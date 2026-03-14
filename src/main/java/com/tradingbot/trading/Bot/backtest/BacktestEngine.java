package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.Strategy;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class BacktestEngine {

    private final PositionManager positionManager;

    private static final BigDecimal STARTING_CAPITAL =
            BigDecimal.valueOf(10_000);

    private static final BigDecimal SLIPPAGE =
            BigDecimal.valueOf(0.0002); // 0.02%

    private static final BigDecimal COMMISSION =
            BigDecimal.valueOf(1.0); // $1 per trade

    public BacktestEngine(PositionManager positionManager) {
        this.positionManager = positionManager;
    }

    public BacktestResult runStrategy(String symbol,
                                      List<Candle> candles,
                                      Strategy strategy) {

        BigDecimal capital = STARTING_CAPITAL;

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();

        BigDecimal peakEquity = capital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (int i = 20; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);

            TradingSignal signal = strategy.evaluate(subset);

            BigDecimal marketPrice =
                    candles.get(i).getClose();

            BigDecimal currentPrice =
                    marketPrice.multiply(BigDecimal.ONE.add(SLIPPAGE));

            positionManager.updatePrice(symbol, currentPrice);

            if (signal == TradingSignal.BUY &&
                    positionManager.getOpenPosition(symbol).isEmpty()) {

                BigDecimal stopLoss =
                        currentPrice.multiply(
                                        BigDecimal.ONE.subtract(BigDecimal.valueOf(0.02)))
                                .setScale(4, RoundingMode.HALF_UP);

                BigDecimal takeProfit =
                        currentPrice.multiply(
                                        BigDecimal.ONE.add(BigDecimal.valueOf(0.04)))
                                .setScale(4, RoundingMode.HALF_UP);

                BigDecimal riskPerTrade =
                        capital.multiply(BigDecimal.valueOf(0.01));

                BigDecimal riskPerShare =
                        currentPrice.subtract(stopLoss).abs();

                BigDecimal quantity =
                        riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);

                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal maxAffordable =
                        capital.divide(currentPrice, 0, RoundingMode.DOWN);

                quantity = quantity.min(maxAffordable);

                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                Position position = new Position(
                        symbol,
                        currentPrice,
                        quantity,
                        stopLoss,
                        takeProfit
                );

                positionManager.openPosition(position);

                capital = capital.subtract(COMMISSION);

                System.out.println("BACKTEST BUY: "
                        + symbol
                        + " Price=" + currentPrice
                        + " Qty=" + quantity);
            }

            BigDecimal equity = capital;

            for (Position position : positionManager.getOpenPositions()) {

                BigDecimal unrealized =
                        currentPrice.subtract(position.getEntryPrice())
                                .multiply(position.getQuantity());

                equity = equity.add(unrealized);
            }

            equityCurve.add(equity);

            if (equity.compareTo(peakEquity) > 0) {
                peakEquity = equity;
            }

            BigDecimal drawdown =
                    peakEquity.subtract(equity)
                            .divide(peakEquity, 6, RoundingMode.HALF_UP);

            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        closeRemainingPositions(symbol, candles);

        return calculateResults(capital, maxDrawdown);
    }

    private void closeRemainingPositions(String symbol,
                                         List<Candle> candles) {

        if (positionManager.getOpenPositions().isEmpty())
            return;

        BigDecimal lastPrice =
                candles.get(candles.size() - 1).getClose()
                        .multiply(BigDecimal.ONE.subtract(SLIPPAGE));

        for (Position position : positionManager.getOpenPositions()) {

            position.close(lastPrice);

            positionManager.getClosedPositions().add(position);

            System.out.println("BACKTEST FORCE CLOSE: "
                    + symbol
                    + " Price=" + lastPrice);
        }

        positionManager.getOpenPositions().clear();
    }

    private BacktestResult calculateResults(BigDecimal capital,
                                            BigDecimal maxDrawdown) {

        int totalTrades = positionManager.getClosedPositions().size();
        int winningTrades = 0;
        int losingTrades = 0;

        BigDecimal totalPnL = BigDecimal.ZERO;
        BigDecimal totalWins = BigDecimal.ZERO;
        BigDecimal totalLosses = BigDecimal.ZERO;

        for (Position position : positionManager.getClosedPositions()) {

            BigDecimal pnl = position.getPnl();

            totalPnL = totalPnL.add(pnl);

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                totalWins = totalWins.add(pnl);
            } else {
                losingTrades++;
                totalLosses = totalLosses.add(pnl.abs());
            }
        }

        BigDecimal endingCapital = capital.add(totalPnL);

        BigDecimal winRate =
                totalTrades == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(winningTrades)
                        .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP);

        BigDecimal profitFactor =
                totalLosses.compareTo(BigDecimal.ZERO) == 0
                        ? totalWins
                        : totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP);

        BigDecimal avgWin =
                winningTrades == 0
                        ? BigDecimal.ZERO
                        : totalWins.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP);

        BigDecimal avgLoss =
                losingTrades == 0
                        ? BigDecimal.ZERO
                        : totalLosses.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP);

        BigDecimal expectancy =
                avgWin.multiply(winRate)
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

        return new BacktestResult(
                STARTING_CAPITAL,
                endingCapital,
                totalTrades,
                winningTrades,
                losingTrades,
                totalPnL,
                winRate,
                profitFactor,
                avgWin,
                avgLoss,
                expectancy,
                maxDrawdown
        );
    }
}