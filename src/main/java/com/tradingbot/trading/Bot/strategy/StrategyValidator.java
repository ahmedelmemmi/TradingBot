package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates per-strategy performance against strategy-specific thresholds.
 *
 * <p>Thresholds:</p>
 * <ul>
 *   <li>Trend Following: min 45% win rate, PF &gt; 1.6</li>
 *   <li>Mean Reversion: min 55% win rate, PF &gt; 1.8</li>
 *   <li>Volatility Breakout: min 60% win rate, PF &gt; 1.7</li>
 * </ul>
 */
@Service
public class StrategyValidator {

    public enum StrategyType {
        TREND_FOLLOWING,
        MEAN_REVERSION,
        VOLATILITY_BREAKOUT
    }

    public static class ValidationResult {
        private final StrategyType strategyType;
        private final int    totalTrades;
        private final BigDecimal winRate;
        private final BigDecimal profitFactor;
        private final BigDecimal maxDrawdown;
        private final BigDecimal avgWin;
        private final BigDecimal avgLoss;
        private final boolean winRatePass;
        private final boolean profitFactorPass;
        private final boolean drawdownPass;
        private final boolean tradeCountPass;
        private final List<String> failureReasons;

        public ValidationResult(StrategyType strategyType, int totalTrades,
                                BigDecimal winRate, BigDecimal profitFactor,
                                BigDecimal maxDrawdown, BigDecimal avgWin,
                                BigDecimal avgLoss, double minWinRate,
                                double minProfitFactor) {
            this.strategyType   = strategyType;
            this.totalTrades    = totalTrades;
            this.winRate        = winRate;
            this.profitFactor   = profitFactor;
            this.maxDrawdown    = maxDrawdown;
            this.avgWin         = avgWin;
            this.avgLoss        = avgLoss;

            this.tradeCountPass   = totalTrades >= 10;
            this.winRatePass      = winRate.compareTo(BigDecimal.valueOf(minWinRate)) >= 0;
            this.profitFactorPass = profitFactor.compareTo(BigDecimal.valueOf(minProfitFactor)) >= 0;
            this.drawdownPass     = maxDrawdown.compareTo(BigDecimal.valueOf(0.20)) < 0;

            this.failureReasons = new ArrayList<>();
            if (!tradeCountPass)   failureReasons.add("Insufficient trades: " + totalTrades + " (need >= 10)");
            if (!winRatePass)      failureReasons.add("Win rate too low: " + pct(winRate)
                    + " (need >= " + pct(BigDecimal.valueOf(minWinRate)) + ")");
            if (!profitFactorPass) failureReasons.add("Profit factor too low: " + fmt(profitFactor)
                    + " (need >= " + minProfitFactor + ")");
            if (!drawdownPass)     failureReasons.add("Max drawdown too high: " + pct(maxDrawdown) + " (need < 20%)");
        }

        public boolean isValid() {
            return tradeCountPass && winRatePass && profitFactorPass && drawdownPass;
        }

        public void printReport() {
            String name = strategyType.name().replace('_', ' ');
            System.out.println();
            System.out.println("┌─ " + name + " ─────────────────────────────────────────────┐");
            System.out.printf("│ Trades: %-44d │%n", totalTrades);
            System.out.printf("│ Win Rate: %-42s │%n", pct(winRate));
            System.out.printf("│ Profit Factor: %-37s │%n", fmt(profitFactor));
            System.out.printf("│ Avg Win: $%-41s │%n", fmt(avgWin));
            System.out.printf("│ Avg Loss: $%-40s │%n", fmt(avgLoss));
            System.out.printf("│ Max Drawdown: %-38s │%n", pct(maxDrawdown));
            System.out.println("├─ Validation: " + (isValid() ? "✅ PASSED" : "❌ FAILED") + " ──────────────────────────────────┤");
            for (String reason : failureReasons) {
                System.out.printf("│ ✗ %-49s │%n", reason);
            }
            System.out.println("└─────────────────────────────────────────────────────────────┘");
        }

        private String pct(BigDecimal v) {
            return v.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
        }

        private String fmt(BigDecimal v) {
            return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        public StrategyType getStrategyType()  { return strategyType; }
        public int    getTotalTrades()          { return totalTrades; }
        public BigDecimal getWinRate()          { return winRate; }
        public BigDecimal getProfitFactor()     { return profitFactor; }
        public BigDecimal getMaxDrawdown()      { return maxDrawdown; }
        public BigDecimal getAvgWin()           { return avgWin; }
        public BigDecimal getAvgLoss()          { return avgLoss; }
        public boolean isWinRatePass()          { return winRatePass; }
        public boolean isProfitFactorPass()     { return profitFactorPass; }
        public boolean isDrawdownPass()         { return drawdownPass; }
        public boolean isTradeCountPass()       { return tradeCountPass; }
        public List<String> getFailureReasons() { return failureReasons; }
    }

    /**
     * Validates a list of closed positions for the given strategy type.
     *
     * @param strategyType   which strategy these trades belong to
     * @param closedPositions closed position records
     * @param equityCurve     equity at each bar for drawdown calculation
     * @param startingCapital initial capital
     * @return validation result with pass/fail per criterion
     */
    public ValidationResult validate(StrategyType strategyType,
                                     List<Position> closedPositions,
                                     List<BigDecimal> equityCurve,
                                     BigDecimal startingCapital) {

        double minWinRate;
        double minProfitFactor;

        switch (strategyType) {
            case TREND_FOLLOWING    -> { minWinRate = 0.45; minProfitFactor = 1.6; }
            case MEAN_REVERSION     -> { minWinRate = 0.55; minProfitFactor = 1.8; }
            case VOLATILITY_BREAKOUT -> { minWinRate = 0.60; minProfitFactor = 1.7; }
            default                 -> { minWinRate = 0.50; minProfitFactor = 1.5; }
        }

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

        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) == 0
                ? (totalWins.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(999) : BigDecimal.ZERO)
                : totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP);

        BigDecimal maxDrawdown = computeMaxDrawdown(equityCurve, startingCapital);

        ValidationResult result = new ValidationResult(
                strategyType, total, winRate, profitFactor,
                maxDrawdown, avgWin, avgLoss, minWinRate, minProfitFactor);

        result.printReport();
        return result;
    }

    private BigDecimal computeMaxDrawdown(List<BigDecimal> equityCurve, BigDecimal startingCapital) {
        BigDecimal peak  = startingCapital;
        BigDecimal maxDD = BigDecimal.ZERO;
        for (BigDecimal equity : equityCurve) {
            if (equity.compareTo(peak) > 0) peak = equity;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dd = peak.subtract(equity).divide(peak, 6, RoundingMode.HALF_UP);
                if (dd.compareTo(maxDD) > 0) maxDD = dd;
            }
        }
        return maxDD;
    }
}
