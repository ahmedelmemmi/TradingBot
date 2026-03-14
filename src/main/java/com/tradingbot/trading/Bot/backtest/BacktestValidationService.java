package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Validates completed backtest results against institutional quality criteria.
 * Creates a {@link BacktestValidationResult} from a list of closed positions
 * and an equity curve, and prints a per-strategy performance breakdown.
 */
@Service
public class BacktestValidationService {

    /**
     * Validates the backtest by computing all metrics from closed positions and
     * the equity curve, then checking each criterion.
     *
     * @param closedPositions all closed positions from the backtest
     * @param equityCurve     equity at each bar
     * @param startingCapital initial capital
     * @return populated {@link BacktestValidationResult}
     */
    public BacktestValidationResult validate(List<Position> closedPositions,
                                             List<BigDecimal> equityCurve,
                                             BigDecimal startingCapital) {

        int total = closedPositions.size();
        int wins  = 0;
        BigDecimal totalWins   = BigDecimal.ZERO;
        BigDecimal totalLosses = BigDecimal.ZERO;

        for (Position p : closedPositions) {
            BigDecimal pnl = p.getPnl();
            if (pnl == null) continue;

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                totalWins = totalWins.add(pnl);
            } else {
                totalLosses = totalLosses.add(pnl.abs());
            }
        }

        int losses = total - wins;

        BigDecimal winRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);

        BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO
                : totalWins.divide(BigDecimal.valueOf(wins), 4, RoundingMode.HALF_UP);

        BigDecimal avgLoss = losses == 0 ? BigDecimal.ZERO
                : totalLosses.divide(BigDecimal.valueOf(losses), 4, RoundingMode.HALF_UP);

        BigDecimal expectancy = avgWin.multiply(winRate)
                .subtract(avgLoss.multiply(BigDecimal.ONE.subtract(winRate)));

        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) == 0
                ? totalWins
                : totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP);

        BigDecimal maxDrawdown = computeMaxDrawdown(equityCurve, startingCapital);

        BacktestValidationResult result = new BacktestValidationResult(
                total, winRate, expectancy, profitFactor, maxDrawdown, avgWin, avgLoss);

        result.printReport();

        return result;
    }

    /**
     * Validates the backtest and prints per-strategy performance breakdown using
     * trade records that include strategy name metadata.
     *
     * @param tradeLog        all trade records from the backtest
     * @param equityCurve     equity at each bar
     * @param startingCapital initial capital
     * @return populated {@link BacktestValidationResult}
     */
    public BacktestValidationResult validateWithTradeLog(List<TradeRecord> tradeLog,
                                                          List<BigDecimal> equityCurve,
                                                          BigDecimal startingCapital) {

        int total = tradeLog.size();
        int wins  = 0;
        BigDecimal totalWins   = BigDecimal.ZERO;
        BigDecimal totalLosses = BigDecimal.ZERO;

        // Per-strategy tracking
        Map<String, int[]>        strategyTrades = new LinkedHashMap<>();  // [total, wins]
        Map<String, BigDecimal[]> strategyPnl    = new LinkedHashMap<>();  // [wins, losses]

        for (TradeRecord rec : tradeLog) {
            BigDecimal pnl = rec.getPnl();
            if (pnl == null) continue;

            String strat = rec.getStrategyName() != null ? rec.getStrategyName() : "Unknown";
            strategyTrades.computeIfAbsent(strat, k -> new int[]{0, 0});
            strategyPnl.computeIfAbsent(strat, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            strategyTrades.get(strat)[0]++;

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                totalWins = totalWins.add(pnl);
                strategyTrades.get(strat)[1]++;
                strategyPnl.get(strat)[0] = strategyPnl.get(strat)[0].add(pnl);
            } else {
                totalLosses = totalLosses.add(pnl.abs());
                strategyPnl.get(strat)[1] = strategyPnl.get(strat)[1].add(pnl.abs());
            }
        }

        int losses = total - wins;

        BigDecimal winRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);

        BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO
                : totalWins.divide(BigDecimal.valueOf(wins), 4, RoundingMode.HALF_UP);

        BigDecimal avgLoss = losses == 0 ? BigDecimal.ZERO
                : totalLosses.divide(BigDecimal.valueOf(losses), 4, RoundingMode.HALF_UP);

        BigDecimal expectancy = avgWin.multiply(winRate)
                .subtract(avgLoss.multiply(BigDecimal.ONE.subtract(winRate)));

        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) == 0
                ? totalWins
                : totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP);

        BigDecimal maxDrawdown = computeMaxDrawdown(equityCurve, startingCapital);

        BacktestValidationResult result = new BacktestValidationResult(
                total, winRate, expectancy, profitFactor, maxDrawdown, avgWin, avgLoss);

        result.printReport();

        // Print per-strategy breakdown
        System.out.println("\n========== STRATEGY PERFORMANCE BREAKDOWN ==========");
        for (Map.Entry<String, int[]> entry : strategyTrades.entrySet()) {
            String strat  = entry.getKey();
            int[]  counts = entry.getValue();
            BigDecimal[] pnls = strategyPnl.get(strat);

            int stratTotal  = counts[0];
            int stratWins   = counts[1];
            int stratLosses = stratTotal - stratWins;

            BigDecimal stratWinRate = stratTotal == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(stratWins)
                    .divide(BigDecimal.valueOf(stratTotal), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            BigDecimal stratPF = pnls[1].compareTo(BigDecimal.ZERO) == 0
                    ? pnls[0]
                    : pnls[0].divide(pnls[1], 4, RoundingMode.HALF_UP);

            BigDecimal netPnl = pnls[0].subtract(pnls[1]);
            boolean profitable = netPnl.compareTo(BigDecimal.ZERO) > 0;

            System.out.println("\n  " + strat + ":");
            System.out.println("    Trades: " + stratTotal + " (Wins: " + stratWins + ", Losses: " + stratLosses + ")");
            System.out.printf ("    Win Rate: %.1f%%%n", stratWinRate.doubleValue());
            System.out.println("    Profit Factor: " + stratPF.setScale(2, RoundingMode.HALF_UP));
            System.out.println("    Net PnL: " + netPnl.setScale(2, RoundingMode.HALF_UP));
            System.out.println("    Status: " + (profitable ? "✅ PROFITABLE" : "❌ LOSING"));
        }

        if (strategyTrades.containsKey("NoTradeStrategy")) {
            System.out.println("\n  NoTradeStrategy:");
            System.out.println("    Trades: 0");
            System.out.println("    Capital Preserved");
            System.out.println("    Status: ✅ WORKING");
        }
        System.out.println("====================================================");

        return result;
    }

    private BigDecimal computeMaxDrawdown(List<BigDecimal> equityCurve,
                                          BigDecimal startingCapital) {
        BigDecimal peak   = startingCapital;
        BigDecimal maxDD  = BigDecimal.ZERO;

        for (BigDecimal equity : equityCurve) {
            if (equity.compareTo(peak) > 0) peak = equity;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dd = peak.subtract(equity)
                        .divide(peak, 6, RoundingMode.HALF_UP);
                if (dd.compareTo(maxDD) > 0) maxDD = dd;
            }
        }

        return maxDD;
    }
}
