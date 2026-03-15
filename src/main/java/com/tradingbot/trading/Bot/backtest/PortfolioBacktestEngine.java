package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import com.tradingbot.trading.Bot.portfolio.PortfolioRiskService;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.risk.AdaptiveRiskService;
import com.tradingbot.trading.Bot.strategy.AtrCalculator;
import com.tradingbot.trading.Bot.strategy.RegimeAwareStrategyFactory;
import com.tradingbot.trading.Bot.strategy.Strategy;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-symbol portfolio backtesting engine with institutional-grade features:
 * <ul>
 *   <li>Dynamic slippage via {@link SlippageService}</li>
 *   <li>ATR-based adaptive stop loss via {@link AdaptiveRiskService}</li>
 *   <li>Kelly Criterion position sizing (adaptive after 30+ trades)</li>
 *   <li>Trade cooldown enforcement (10 bars default, regime-adjusted)</li>
 *   <li>Capital deducted at entry and returned at exit</li>
 * </ul>
 */
@Service
public class PortfolioBacktestEngine {

    private final PositionManager positionManager;
    private final PortfolioRiskService portfolioRiskService;
    private final MarketRegimeService regimeService;
    private final SlippageService slippageService;
    private final AdaptiveRiskService adaptiveRiskService;
    private final RegimeAwareStrategyFactory strategyFactory;

    private static final BigDecimal STARTING_CAPITAL = BigDecimal.valueOf(10_000);
    private static final BigDecimal COMMISSION        = BigDecimal.valueOf(1.0);
    private static final int TRADE_COOLDOWN_BARS      = 10;

    public PortfolioBacktestEngine(PositionManager positionManager,
                                   PortfolioRiskService portfolioRiskService,
                                   MarketRegimeService regimeService,
                                   SlippageService slippageService,
                                   AdaptiveRiskService adaptiveRiskService,
                                   RegimeAwareStrategyFactory strategyFactory) {
        this.positionManager      = positionManager;
        this.portfolioRiskService = portfolioRiskService;
        this.regimeService        = regimeService;
        this.slippageService      = slippageService;
        this.adaptiveRiskService  = adaptiveRiskService;
        this.strategyFactory      = strategyFactory;
    }

    public PortfolioBacktestResult run(String symbol,
                                       List<Candle> candles,
                                       Strategy strategy) {

        // Reset regime-persistence state so previous runs do not contaminate this one.
        regimeService.reset();

        BigDecimal capital   = STARTING_CAPITAL;
        BigDecimal peakEquity = STARTING_CAPITAL;

        portfolioRiskService.initialize(capital);

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();
        Map<String, Integer> cooldownMap = new HashMap<>();

        AtrCalculator atrCalc = new AtrCalculator();

        for (int i = 60; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);
            MarketRegime regime = regimeService.detect(subset);
            TradingSignal signal = strategy.evaluate(subset);

            BigDecimal price = candles.get(i).getClose();
            BigDecimal atr   = atrCalc.calculate(subset, 14);

            positionManager.updatePrice(symbol, price);

            capital = checkStopsWithCapitalReturn(symbol, price, atr, regime, capital, cooldownMap, i);

            int cooldownUntil = cooldownMap.getOrDefault(symbol, 0);

            if (signal == TradingSignal.BUY &&
                    positionManager.getOpenPosition(symbol).isEmpty() &&
                    i >= cooldownUntil) {

                boolean allowed = portfolioRiskService.canOpenNewPosition(
                        positionManager.getOpenPositions(), capital, regime);

                if (!allowed) continue;

                if (!portfolioRiskService.isPortfolioRiskAcceptable(
                        positionManager.getOpenPositions(), calculateEquity(capital, price))) continue;

                if (atr.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Adaptive stop loss based on ATR + regime
                BigDecimal stop = adaptiveRiskService.calculateAdaptiveStop(subset, price, regime);
                BigDecimal take = price.add(atr.multiply(BigDecimal.valueOf(4.0)));

                // Adaptive risk: Kelly when sufficient trades, else 1%
                BigDecimal riskPct = adaptiveRiskService.calculateKellyRiskPercent(
                        positionManager.getClosedPositions());

                BigDecimal riskPerTrade = capital.multiply(riskPct);
                BigDecimal riskPerShare = price.subtract(stop).abs();

                if (riskPerShare.compareTo(BigDecimal.ZERO) == 0) continue;

                BigDecimal qty = riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);

                BigDecimal regimeMult = portfolioRiskService.adjustRiskByRegime(regime);
                BigDecimal ddMult     = portfolioRiskService.adjustRiskByDrawdown(
                        calculateEquity(capital, price), peakEquity);

                qty = qty.multiply(regimeMult).multiply(ddMult)
                        .setScale(0, RoundingMode.DOWN);

                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Dynamic slippage at entry
                BigDecimal slippage = slippageService.calculateSlippage(atr, price, regime);
                BigDecimal slippageAdjustedPrice = price.multiply(BigDecimal.ONE.add(slippage));

                BigDecimal cost = slippageAdjustedPrice.multiply(qty).add(COMMISSION);

                if (cost.compareTo(capital) > 0) continue;

                // Deduct capital at entry
                capital = capital.subtract(cost);

                Position position = new Position(symbol, slippageAdjustedPrice, qty, stop, take);
                positionManager.openPosition(position);

                System.out.println("[PortfolioBacktest] BUY " + symbol
                        + " regime=" + regime + " qty=" + qty
                        + " price=" + slippageAdjustedPrice.setScale(4, RoundingMode.HALF_UP)
                        + " riskPct=" + riskPct.setScale(4, RoundingMode.HALF_UP)
                        + " capital=" + capital.setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal equity = calculateEquity(capital, price);
            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
            equityCurve.add(equity);
        }

        capital = forceClose(symbol, candles, capital);

        return buildResult(equityCurve, capital, peakEquity);
    }

    private BigDecimal checkStopsWithCapitalReturn(
            String symbol, BigDecimal price, BigDecimal atr, MarketRegime regime,
            BigDecimal capital, Map<String, Integer> cooldownMap, int currentBar) {

        var optional = positionManager.getOpenPosition(symbol);
        if (optional.isEmpty()) return capital;

        Position p = optional.get();

        if (price.compareTo(p.getStopLoss()) <= 0 ||
                price.compareTo(p.getTakeProfit()) >= 0) {

            BigDecimal slippage  = slippageService.calculateSlippage(atr, price, regime);
            BigDecimal exitPrice = price.multiply(BigDecimal.ONE.subtract(slippage));

            p.close(exitPrice);

            BigDecimal proceeds = exitPrice.multiply(p.getQuantity()).subtract(COMMISSION);
            capital = capital.add(proceeds);

            positionManager.getClosedPositions().add(p);
            positionManager.getOpenPositions().remove(p);

            cooldownMap.put(symbol, currentBar + TRADE_COOLDOWN_BARS);

            System.out.println("[PortfolioBacktest] EXIT " + symbol
                    + " bar=" + currentBar
                    + " exitPrice=" + exitPrice.setScale(4, RoundingMode.HALF_UP)
                    + " pnl=" + p.getPnl());
        }

        return capital;
    }

    /**
     * Computes true portfolio equity for a single-symbol run.
     *
     * <p>Previously used {@code p.getPnl()} which is {@code null} for open (not-yet-closed)
     * positions, causing equity to be reported as just {@code capital} (remaining cash after
     * deducting the position cost), artificially creating drawdowns of 40–90% while the
     * position was open. Fixed to use {@code currentPrice × quantity} — the actual market
     * value of the open position.</p>
     *
     * @param capital      current cash balance (position cost already deducted)
     * @param currentPrice close price of the current bar for the symbol being backtested
     * @return true equity = cash + current market value of all open positions
     */
    private BigDecimal calculateEquity(BigDecimal capital, BigDecimal currentPrice) {
        BigDecimal positionValue = BigDecimal.ZERO;
        for (Position p : positionManager.getOpenPositions()) {
            positionValue = positionValue.add(currentPrice.multiply(p.getQuantity()));
        }
        return capital.add(positionValue);
    }

    /**
     * Computes true portfolio equity for a multi-symbol run.
     *
     * @param capital       current cash balance
     * @param currentPrices map of symbol → current close price
     * @return true equity = cash + current market value of all open positions
     */
    private BigDecimal calculateEquity(BigDecimal capital, Map<String, BigDecimal> currentPrices) {
        BigDecimal positionValue = BigDecimal.ZERO;
        for (Position p : positionManager.getOpenPositions()) {
            BigDecimal price = currentPrices.getOrDefault(p.getSymbol(), p.getEntryPrice());
            positionValue = positionValue.add(price.multiply(p.getQuantity()));
        }
        return capital.add(positionValue);
    }

    private BigDecimal forceClose(String symbol,
                                  List<Candle> candles,
                                  BigDecimal capital) {

        if (positionManager.getOpenPositions().isEmpty()) return capital;

        BigDecimal last = candles.get(candles.size() - 1).getClose();

        List<Position> toClose = new ArrayList<>();
        for (Position p : positionManager.getOpenPositions()) {
            if (p.getSymbol().equals(symbol)) toClose.add(p);
        }

        for (Position p : toClose) {
            BigDecimal exitPrice = last.multiply(BigDecimal.valueOf(0.9998));
            p.close(exitPrice);

            BigDecimal proceeds = exitPrice.multiply(p.getQuantity()).subtract(COMMISSION);
            capital = capital.add(proceeds);

            positionManager.getClosedPositions().add(p);
            positionManager.getOpenPositions().remove(p);
        }

        System.out.println("[PortfolioBacktest] FORCE CLOSE " + symbol);
        return capital;
    }

    private PortfolioBacktestResult buildResult(List<BigDecimal> equity,
                                                BigDecimal capital,
                                                BigDecimal peakEquity) {

        BigDecimal end      = capital;
        BigDecimal totalPnL = end.subtract(STARTING_CAPITAL);

        List<Position> closed = positionManager.getClosedPositions();
        int totalTrades   = closed.size();
        int winningTrades = 0;
        int losingTrades  = 0;
        BigDecimal totalWins   = BigDecimal.ZERO;
        BigDecimal totalLosses = BigDecimal.ZERO;

        for (Position p : closed) {
            BigDecimal pnl = p.getPnl();
            if (pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                totalWins = totalWins.add(pnl);
            } else if (pnl != null) {
                losingTrades++;
                totalLosses = totalLosses.add(pnl.abs());
            }
        }

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

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = STARTING_CAPITAL;
        for (BigDecimal eq : equity) {
            if (eq.compareTo(peak) > 0) peak = eq;
            BigDecimal dd = peak.subtract(eq).divide(peak, 6, RoundingMode.HALF_UP);
            if (dd.compareTo(maxDrawdown) > 0) maxDrawdown = dd;
        }

        System.out.println("========== PORTFOLIO BACKTEST RESULT ==========");
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
        System.out.println("Final Capital: " + end);
        System.out.println("===============================================");

        return new PortfolioBacktestResult(
                STARTING_CAPITAL, end, totalPnL, equity,
                totalTrades, winningTrades, losingTrades,
                winRate, profitFactor, expectancy, maxDrawdown
        );
    }

    /**
     * Runs a hybrid regime-aware backtest for a single symbol.
     * The strategy is dynamically selected per bar based on the detected market regime.
     *
     * <p>Regime-to-strategy mapping:</p>
     * <ul>
     *   <li>STRONG_UPTREND → TrendFollowingStrategy</li>
     *   <li>SIDEWAYS → MeanReversionStrategy</li>
     *   <li>HIGH_VOLATILITY → VolatilityBreakoutStrategy</li>
     *   <li>STRONG_DOWNTREND / CRASH → no trading (capital preserved)</li>
     * </ul>
     *
     * @param symbol  ticker symbol
     * @param candles price/volume data
     * @return backtest result including per-strategy trade counts
     */
    public HybridBacktestResult runHybrid(String symbol, List<Candle> candles) {

        // Reset regime-persistence state so previous runs do not contaminate this one.
        regimeService.reset();

        BigDecimal capital   = STARTING_CAPITAL;
        BigDecimal peakEquity = STARTING_CAPITAL;

        portfolioRiskService.initialize(capital);

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();
        Map<String, Integer> cooldownMap = new HashMap<>();
        Map<String, Integer> tradesByStrategy = new HashMap<>();

        AtrCalculator atrCalc = new AtrCalculator();
        String lastLoggedStrategy = null;

        for (int i = 60; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);
            MarketRegime regime = regimeService.detect(subset);

            Strategy strategy = strategyFactory.getStrategy(regime);

            // Log strategy switch
            String strategyName = strategy != null ? strategy.getName() : "NONE (no trading)";
            if (!strategyName.equals(lastLoggedStrategy)) {
                System.out.println("[Portfolio] Using " + strategyName + " (" + regime + ")");
                lastLoggedStrategy = strategyName;
            }

            BigDecimal price = candles.get(i).getClose();
            BigDecimal atr   = atrCalc.calculate(subset, 14);

            positionManager.updatePrice(symbol, price);

            capital = checkStopsWithCapitalReturn(symbol, price, atr, regime, capital, cooldownMap, i);

            if (strategy == null) {
                BigDecimal equity = calculateEquity(capital, price);
                if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
                equityCurve.add(equity);
                continue;
            }

            TradingSignal signal = strategy.evaluate(subset);

            int cooldownUntil = cooldownMap.getOrDefault(symbol, 0);

            if (signal == TradingSignal.BUY &&
                    positionManager.getOpenPosition(symbol).isEmpty() &&
                    i >= cooldownUntil) {

                boolean allowed = portfolioRiskService.canOpenNewPosition(
                        positionManager.getOpenPositions(), capital, regime);

                if (!allowed) {
                    BigDecimal equity = calculateEquity(capital, price);
                    if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
                    equityCurve.add(equity);
                    continue;
                }

                if (!portfolioRiskService.isPortfolioRiskAcceptable(
                        positionManager.getOpenPositions(), calculateEquity(capital, price))) {
                    BigDecimal equity = calculateEquity(capital, price);
                    if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
                    equityCurve.add(equity);
                    continue;
                }

                if (atr.compareTo(BigDecimal.ZERO) <= 0) {
                    BigDecimal equity = calculateEquity(capital, price);
                    if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
                    equityCurve.add(equity);
                    continue;
                }

                BigDecimal stop = adaptiveRiskService.calculateAdaptiveStop(subset, price, regime);
                BigDecimal take = price.add(atr.multiply(BigDecimal.valueOf(4.0)));

                BigDecimal riskPct      = adaptiveRiskService.calculateKellyRiskPercent(
                        positionManager.getClosedPositions());
                BigDecimal riskPerTrade = capital.multiply(riskPct);
                BigDecimal riskPerShare = price.subtract(stop).abs();

                if (riskPerShare.compareTo(BigDecimal.ZERO) == 0) {
                    BigDecimal equity = calculateEquity(capital, price);
                    if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
                    equityCurve.add(equity);
                    continue;
                }

                BigDecimal qty = riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);

                BigDecimal regimeMult = portfolioRiskService.adjustRiskByRegime(regime);
                BigDecimal ddMult     = portfolioRiskService.adjustRiskByDrawdown(
                        calculateEquity(capital, price), peakEquity);

                qty = qty.multiply(regimeMult).multiply(ddMult)
                        .setScale(0, RoundingMode.DOWN);

                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    BigDecimal equity = calculateEquity(capital, price);
                    if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
                    equityCurve.add(equity);
                    continue;
                }

                BigDecimal slippage = slippageService.calculateSlippage(atr, price, regime);
                BigDecimal slippageAdjustedPrice = price.multiply(BigDecimal.ONE.add(slippage));

                BigDecimal cost = slippageAdjustedPrice.multiply(qty).add(COMMISSION);

                if (cost.compareTo(capital) > 0) {
                    BigDecimal equity = calculateEquity(capital, price);
                    if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
                    equityCurve.add(equity);
                    continue;
                }

                capital = capital.subtract(cost);

                Position position = new Position(symbol, slippageAdjustedPrice, qty, stop, take);
                positionManager.openPosition(position);

                tradesByStrategy.merge(strategy.getName(), 1, Integer::sum);

                System.out.println("[Portfolio] HYBRID BUY " + symbol
                        + " strategy=" + strategy.getName()
                        + " regime=" + regime
                        + " qty=" + qty
                        + " price=" + slippageAdjustedPrice.setScale(4, RoundingMode.HALF_UP)
                        + " capital=" + capital.setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal equity = calculateEquity(capital, price);
            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
            equityCurve.add(equity);
        }

        capital = forceClose(symbol, candles, capital);

        PortfolioBacktestResult base = buildResult(equityCurve, capital, peakEquity);
        return new HybridBacktestResult(base, tradesByStrategy);
    }

    public PortfolioBacktestResult runPortfolio(
            Map<String, List<Candle>> market,
            Strategy strategy) {

        BigDecimal capital   = STARTING_CAPITAL;
        BigDecimal peakEquity = STARTING_CAPITAL;

        portfolioRiskService.initialize(capital);

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();
        Map<String, Integer> cooldownMap = new HashMap<>();

        int candles = market.values().iterator().next().size();
        AtrCalculator atrCalc = new AtrCalculator();

        for (int i = 60; i < candles; i++) {

            Map<String, BigDecimal> currentPrices = new HashMap<>();

            for (String symbol : market.keySet()) {

                List<Candle> series = market.get(symbol);
                List<Candle> subset = series.subList(0, i + 1);

                BigDecimal price = series.get(i).getClose();
                BigDecimal atr   = atrCalc.calculate(subset, 14);

                currentPrices.put(symbol, price);

                positionManager.updatePrice(symbol, price);

                MarketRegime regime = regimeService.detect(subset);

                capital = checkStopsWithCapitalReturn(symbol, price, atr, regime, capital, cooldownMap, i);

                TradingSignal signal = strategy.evaluate(subset);

                int cooldownUntil = cooldownMap.getOrDefault(symbol, 0);

                if (signal == TradingSignal.BUY &&
                        positionManager.getOpenPosition(symbol).isEmpty() &&
                        i >= cooldownUntil) {

                    boolean allowed = portfolioRiskService.canOpenNewPosition(
                            positionManager.getOpenPositions(), capital, regime);

                    if (!allowed) continue;

                    if (!portfolioRiskService.isPortfolioRiskAcceptable(
                            positionManager.getOpenPositions(), calculateEquity(capital, currentPrices))) continue;

                    if (atr.compareTo(BigDecimal.ZERO) <= 0) continue;

                    // Adaptive stop loss
                    BigDecimal stop = adaptiveRiskService.calculateAdaptiveStop(subset, price, regime);
                    BigDecimal take = price.add(atr.multiply(BigDecimal.valueOf(4.0)));

                    // Adaptive Kelly risk sizing
                    BigDecimal riskPct = adaptiveRiskService.calculateKellyRiskPercent(
                            positionManager.getClosedPositions());

                    BigDecimal riskPerTrade = capital.multiply(riskPct);
                    BigDecimal riskPerShare = price.subtract(stop).abs();

                    if (riskPerShare.compareTo(BigDecimal.ZERO) == 0) continue;

                    BigDecimal qty = riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);

                    BigDecimal regimeMult = portfolioRiskService.adjustRiskByRegime(regime);
                    BigDecimal ddMult     = portfolioRiskService.adjustRiskByDrawdown(
                            calculateEquity(capital, currentPrices), peakEquity);

                    qty = qty.multiply(regimeMult).multiply(ddMult)
                            .setScale(0, RoundingMode.DOWN);

                    if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

                    if (price.compareTo(BigDecimal.valueOf(20)) < 0) continue;

                    BigDecimal slippage = slippageService.calculateSlippage(atr, price, regime);
                    BigDecimal slippageAdjustedPrice = price.multiply(BigDecimal.ONE.add(slippage));

                    BigDecimal cost = slippageAdjustedPrice.multiply(qty).add(COMMISSION);

                    if (cost.compareTo(capital) > 0) continue;

                    capital = capital.subtract(cost);

                    Position position = new Position(symbol, slippageAdjustedPrice, qty, stop, take);
                    positionManager.openPosition(position);

                    System.out.println("[PortfolioBacktest] PORTFOLIO BUY " + symbol
                            + " regime=" + regime + " qty=" + qty
                            + " price=" + slippageAdjustedPrice.setScale(4, RoundingMode.HALF_UP)
                            + " capital=" + capital.setScale(2, RoundingMode.HALF_UP));
                }
            }

            BigDecimal equity = calculateEquity(capital, currentPrices);
            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
            equityCurve.add(equity);
        }

        for (String symbol : market.keySet()) {
            capital = forceClose(symbol, market.get(symbol), capital);
        }

        return buildResult(equityCurve, capital, peakEquity);
    }
}