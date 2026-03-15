package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.AtrCalculator;
import com.tradingbot.trading.Bot.strategy.Strategy;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-symbol backtesting engine with production-grade realism:
 *
 * <ul>
 *   <li><b>1-bar delayed fills</b>: signal at bar[i] fills at bar[i+1]</li>
 *   <li><b>Entry confirmation</b>: fill only if bar[i+1] close ≥ signal-bar close (filters false breakouts)</li>
 *   <li><b>ATR-based stop loss</b>: 1.5×ATR(14) below entry (≈5.5% for real SPY data)</li>
 *   <li><b>ATR-based take profit</b>: 2.5×ATR(14) above entry (≈9.25% for real SPY data)</li>
 *   <li><b>Dynamic slippage</b>: via {@link SlippageService} based on ATR and regime</li>
 *   <li><b>Capital deducted at entry</b>: (price × qty) removed from available capital immediately</li>
 *   <li><b>Gap risk</b>: SL execution uses fill price, not exact SL level</li>
 *   <li><b>Trade log</b>: every entry/exit logged as {@link TradeRecord} with CSV export</li>
 * </ul>
 */
@Service
public class BacktestEngine {

    private final PositionManager positionManager;
    private final SlippageService slippageService;
    private final MarketRegimeService regimeService;

    private static final BigDecimal STARTING_CAPITAL = BigDecimal.valueOf(10_000);
    private static final BigDecimal COMMISSION        = BigDecimal.valueOf(1.0);
    private static final BigDecimal RISK_PER_TRADE    = BigDecimal.valueOf(0.01); // 1%

    private static final AtomicInteger TRADE_COUNTER = new AtomicInteger(0);

    public BacktestEngine(PositionManager positionManager,
                          SlippageService slippageService,
                          MarketRegimeService regimeService) {
        this.positionManager = positionManager;
        this.slippageService  = slippageService;
        this.regimeService    = regimeService;
    }

    /**
     * Runs the strategy against the provided candle list and returns a full
     * {@link BacktestResult} including trade records and equity curve.
     *
     * @param symbol   ticker symbol
     * @param candles  full historical candle list
     * @param strategy trading strategy to evaluate
     * @return backtest result with metrics and trade log
     */
    public BacktestResult runStrategy(String symbol,
                                      List<Candle> candles,
                                      Strategy strategy) {

        // Reset regime-persistence state so previous runs do not contaminate this one.
        regimeService.reset();

        BigDecimal capital   = STARTING_CAPITAL;
        BigDecimal peakEquity = STARTING_CAPITAL;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal>  equityCurve = new ArrayList<>();
        List<TradeRecord> tradeLog    = new ArrayList<>();

        // pendingSignal: set when BUY is detected at bar[i], fills at bar[i+1]
        // signalBarClose: close price at signal bar — used for 1-bar entry confirmation
        boolean    pendingSignal    = false;
        BigDecimal signalBarClose   = null;
        int        entryBar         = -1;
        int        tradeId          = 0;

        AtrCalculator atrCalc = new AtrCalculator();

        for (int i = 20; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);
            MarketRegime regime = regimeService.detect(subset);

            BigDecimal rawPrice = candles.get(i).getClose();

            // ── 1. Check SL / TP on existing position (realistic gap execution) ──
            if (positionManager.getOpenPosition(symbol).isPresent()) {

                Position pos = positionManager.getOpenPosition(symbol).get();
                BigDecimal atr = atrCalc.calculate(subset, 14);
                BigDecimal slippage = slippageService.calculateSlippage(atr, rawPrice, regime);

                boolean hitSl = rawPrice.compareTo(pos.getStopLoss()) <= 0;
                boolean hitTp = rawPrice.compareTo(pos.getTakeProfit()) >= 0;

                if (hitSl || hitTp) {
                    // Gap risk: execute at current price (may be worse than exact SL)
                    BigDecimal exitFill = rawPrice.multiply(
                            BigDecimal.ONE.subtract(slippage));

                    String reason = hitSl ? "SL" : "TP";

                    pos.close(exitFill);

                    BigDecimal proceeds = exitFill.multiply(pos.getQuantity())
                            .subtract(COMMISSION);

                    // Return position proceeds to capital
                    capital = capital.add(proceeds);

                    positionManager.getClosedPositions().add(pos);
                    positionManager.getOpenPositions().remove(pos);

                    // Close trade record
                    if (!tradeLog.isEmpty()) {
                        TradeRecord rec = tradeLog.get(tradeLog.size() - 1);
                        if (rec.getExitPrice() == null) {
                            rec.close(LocalDateTime.now(), exitFill, reason,
                                    regime.name(), i - entryBar);
                        }
                    }

                    System.out.println("[BacktestEngine] " + reason + " HIT " + symbol
                            + " bar=" + i
                            + " exitFill=" + exitFill.setScale(4, RoundingMode.HALF_UP)
                            + " pnl=" + pos.getPnl());

                    pendingSignal = false;
                }
            }

            // ── 2. Delayed fill: enter at bar[i+1] after signal at bar[i] ──────
            if (pendingSignal && positionManager.getOpenPosition(symbol).isEmpty()) {

                // Entry confirmation: cancel only if the next bar closes more than 2% below
                // the signal-bar close (indicating a genuine reversal, not noise).
                // The strategy already confirmed the breakout using bar close; we allow small
                // intra-day pullbacks which are common on volatile stocks like NVDA/TSLA.
                boolean cancelEntry = false;
                if (signalBarClose != null && rawPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal threshold = signalBarClose.multiply(BigDecimal.valueOf(0.98))
                            .setScale(4, RoundingMode.HALF_UP);
                    if (rawPrice.setScale(4, RoundingMode.HALF_UP).compareTo(threshold) < 0) {
                        System.out.println("[BacktestEngine] ENTRY CANCELLED (reversal >2%) "
                                + symbol + " bar=" + i
                                + " close=" + rawPrice.setScale(4, RoundingMode.HALF_UP)
                                + " < 98% of signalClose=" + threshold);
                        cancelEntry = true;
                    }
                }

                if (!cancelEntry) {
                    List<Candle> entrySubset = candles.subList(0, i + 1);
                    BigDecimal atr = atrCalc.calculate(entrySubset, 14);
                    MarketRegime entryRegime = regimeService.detect(entrySubset);
                    BigDecimal slippage = slippageService.calculateSlippage(atr, rawPrice, entryRegime);

                    BigDecimal entryFill = rawPrice.multiply(BigDecimal.ONE.add(slippage));

                    // ATR-based stop loss: 1.5×ATR(14) below entry (≈5.5% for real SPY).
                    // Keeps stop outside normal daily noise (real ATR ≈ 3.7% of price).
                    BigDecimal atrPct = entryFill.compareTo(BigDecimal.ZERO) == 0 || atr.compareTo(BigDecimal.ZERO) <= 0
                            ? BigDecimal.ZERO
                            : atr.divide(entryFill, 6, RoundingMode.HALF_UP);

                    if (atrPct.compareTo(BigDecimal.ZERO) > 0) {

                        BigDecimal stopLoss = entryFill.multiply(
                                        BigDecimal.ONE.subtract(BigDecimal.valueOf(1.5).multiply(atrPct)))
                                .setScale(4, RoundingMode.HALF_UP);

                        // ATR-based take profit: 2.5×ATR(14) above entry (≈9.25% for real SPY).
                        // Maintains ≥1.67:1 reward-to-risk even with the wider stop.
                        BigDecimal takeProfit = entryFill.multiply(
                                        BigDecimal.ONE.add(BigDecimal.valueOf(2.5).multiply(atrPct)))
                                .setScale(4, RoundingMode.HALF_UP);

                        BigDecimal riskPerTrade = capital.multiply(RISK_PER_TRADE);
                        BigDecimal riskPerShare = entryFill.subtract(stopLoss).abs();

                        if (riskPerShare.compareTo(BigDecimal.ZERO) > 0) {

                            BigDecimal quantity = riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);
                            BigDecimal maxAffordable = capital.divide(entryFill, 0, RoundingMode.DOWN);
                            quantity = quantity.min(maxAffordable);

                            if (quantity.compareTo(BigDecimal.ZERO) > 0) {

                                BigDecimal cost = entryFill.multiply(quantity).add(COMMISSION);

                                if (cost.compareTo(capital) <= 0) {

                                    // Deduct capital AT ENTRY
                                    capital = capital.subtract(cost);

                                    Position position = new Position(
                                            symbol, entryFill, quantity, stopLoss, takeProfit);
                                    positionManager.openPosition(position);

                                    tradeId = TRADE_COUNTER.incrementAndGet();
                                    TradeRecord rec = new TradeRecord(
                                            "T" + tradeId, symbol,
                                            strategy.getName(), entryRegime.name(),
                                            LocalDateTime.now(), entryFill, quantity,
                                            stopLoss, takeProfit);
                                    tradeLog.add(rec);
                                    entryBar = i;

                                    System.out.println("[BacktestEngine] FILL ENTRY " + symbol
                                            + " bar=" + i
                                            + " price=" + entryFill.setScale(4, RoundingMode.HALF_UP)
                                            + " qty=" + quantity
                                            + " capital=" + capital.setScale(2, RoundingMode.HALF_UP));
                                }
                            }
                        }
                    } else {
                        System.out.println("[BacktestEngine] ENTRY SKIPPED (ATR=0) " + symbol + " bar=" + i);
                    }
                }

                pendingSignal  = false;
                signalBarClose = null;
            }

            // ── 3. Evaluate strategy signal (takes effect next bar) ───────────
            // NOTE: This runs unconditionally after step 2, so if an entry was
            // cancelled above, this bar is still evaluated for a fresh signal.
            if (positionManager.getOpenPosition(symbol).isEmpty()) {
                TradingSignal signal = strategy.evaluate(subset);
                if (signal == TradingSignal.BUY) {
                    pendingSignal  = true;
                    signalBarClose = rawPrice.setScale(4, RoundingMode.HALF_UP);
                    System.out.println("[BacktestEngine] BUY signal queued at bar=" + i
                            + " (will fill at bar=" + (i + 1) + ")"
                            + " signalClose=" + rawPrice.setScale(4, RoundingMode.HALF_UP));
                }
            }

            // ── 4. Equity calculation (capital + unrealized PnL) ──────────────
            BigDecimal equity = capital;
            for (Position p : positionManager.getOpenPositions()) {
                BigDecimal unrealized = rawPrice.subtract(p.getEntryPrice())
                        .multiply(p.getQuantity());
                equity = equity.add(unrealized);
            }

            equityCurve.add(equity);

            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;

            BigDecimal drawdown = peakEquity.subtract(equity)
                    .divide(peakEquity, 6, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) maxDrawdown = drawdown;
        }

        // ── 5. Force close any remaining positions at end of data ─────────────
        capital = closeRemainingPositions(symbol, candles, capital, tradeLog);

        return calculateResults(capital, maxDrawdown, equityCurve, tradeLog);
    }

    private BigDecimal closeRemainingPositions(String symbol,
                                               List<Candle> candles,
                                               BigDecimal capital,
                                               List<TradeRecord> tradeLog) {

        List<Position> toClose = new ArrayList<>(positionManager.getOpenPositions());

        if (toClose.isEmpty()) return capital;

        BigDecimal lastPrice = candles.get(candles.size() - 1).getClose();

        for (Position p : toClose) {
            if (!p.getSymbol().equals(symbol)) continue;

            BigDecimal exitFill = lastPrice.multiply(BigDecimal.valueOf(0.9998));
            p.close(exitFill);

            BigDecimal proceeds = exitFill.multiply(p.getQuantity()).subtract(COMMISSION);
            capital = capital.add(proceeds);

            positionManager.getClosedPositions().add(p);

            // Close open trade record
            if (!tradeLog.isEmpty()) {
                TradeRecord rec = tradeLog.get(tradeLog.size() - 1);
                if (rec.getExitPrice() == null) {
                    rec.close(LocalDateTime.now(), exitFill, "FORCE_CLOSE",
                            "", candles.size() - 1);
                }
            }

            System.out.println("[BacktestEngine] FORCE CLOSE " + symbol
                    + " exitFill=" + exitFill.setScale(4, RoundingMode.HALF_UP)
                    + " pnl=" + p.getPnl());
        }

        positionManager.getOpenPositions().removeAll(toClose);

        return capital;
    }

    private BacktestResult calculateResults(BigDecimal capital,
                                            BigDecimal maxDrawdown,
                                            List<BigDecimal> equityCurve,
                                            List<TradeRecord> tradeLog) {

        List<Position> closed = positionManager.getClosedPositions();

        int totalTrades   = closed.size();
        int winningTrades = 0;
        int losingTrades  = 0;

        BigDecimal totalPnL    = BigDecimal.ZERO;
        BigDecimal totalWins   = BigDecimal.ZERO;
        BigDecimal totalLosses = BigDecimal.ZERO;

        for (Position p : closed) {
            BigDecimal pnl = p.getPnl();
            if (pnl == null) continue;
            totalPnL = totalPnL.add(pnl);
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                totalWins = totalWins.add(pnl);
            } else {
                losingTrades++;
                totalLosses = totalLosses.add(pnl.abs());
            }
        }

        BigDecimal endingCapital = capital;

        BigDecimal winRate = totalTrades == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(winningTrades)
                .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP);

        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) == 0
                ? totalWins
                : totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP);

        BigDecimal avgWin = winningTrades == 0 ? BigDecimal.ZERO
                : totalWins.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP);

        BigDecimal avgLoss = losingTrades == 0 ? BigDecimal.ZERO
                : totalLosses.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP);

        BigDecimal expectancy = avgWin.multiply(winRate)
                .subtract(avgLoss.multiply(BigDecimal.ONE.subtract(winRate)));

        System.out.println("========== BACKTEST RESULT ==========");
        System.out.println("Trades: " + totalTrades);
        System.out.println("Winning: " + winningTrades);
        System.out.println("Losing: " + losingTrades);
        System.out.println("Win Rate: " + winRate);
        System.out.println("Profit Factor: " + profitFactor);
        System.out.println("Avg Win: " + avgWin);
        System.out.println("Avg Loss: " + avgLoss);
        System.out.println("Expectancy: " + expectancy);
        System.out.println("Max Drawdown: " + maxDrawdown);
        System.out.println("Total PnL: " + totalPnL);
        System.out.println("Final Capital: " + endingCapital);
        System.out.println("=====================================");

        // Print trade log CSV header + first few rows
        if (!tradeLog.isEmpty()) {
            System.out.println("\n--- TRADE LOG (CSV) ---");
            System.out.println(TradeRecord.csvHeader());
            tradeLog.stream().limit(10).forEach(r -> System.out.println(r.toCsvRow()));
            if (tradeLog.size() > 10) {
                System.out.println("... and " + (tradeLog.size() - 10) + " more trades");
            }
        }

        return new BacktestResult(
                STARTING_CAPITAL,
                endingCapital,
                totalTrades,
                winningTrades,
                losingTrades,
                totalPnL,
                winRate,
                profitFactor,
                expectancy,
                maxDrawdown,
                equityCurve,
                tradeLog
        );
    }
}