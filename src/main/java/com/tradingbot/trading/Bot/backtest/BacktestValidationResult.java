package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a completed backtest against minimum quality criteria.
 *
 * <p>All six criteria must pass for the backtest to be considered production-ready:</p>
 * <ol>
 *   <li>Trade count ≥ 5 (minimum statistical sample)</li>
 *   <li>Win rate ≥ 60%</li>
 *   <li>Expectancy &gt; 0 (positive expected value per trade)</li>
 *   <li>Profit factor ≥ 1.2</li>
 *   <li>Max drawdown ≤ 25% of starting capital</li>
 *   <li>Average win &gt; average loss (positive R:R)</li>
 * </ol>
 */
public class BacktestValidationResult {

    private static final int    MIN_TRADES             = 5;
    private static final double MIN_WIN_RATE           = 0.60;
    private static final double MIN_PROFIT_FACTOR      = 1.20;
    private static final double MAX_DRAWDOWN_THRESHOLD = 0.25; // 25%

    private final int      totalTrades;
    private final BigDecimal winRate;
    private final BigDecimal expectancy;
    private final BigDecimal profitFactor;
    private final BigDecimal maxDrawdown;
    private final BigDecimal avgWin;
    private final BigDecimal avgLoss;

    // Pass / fail per criterion
    private final boolean tradeCountPass;
    private final boolean winRatePass;
    private final boolean expectancyPass;
    private final boolean profitFactorPass;
    private final boolean drawdownPass;
    private final boolean rrRatioPass;

    public BacktestValidationResult(int totalTrades,
                                    BigDecimal winRate,
                                    BigDecimal expectancy,
                                    BigDecimal profitFactor,
                                    BigDecimal maxDrawdown,
                                    BigDecimal avgWin,
                                    BigDecimal avgLoss) {

        this.totalTrades  = totalTrades;
        this.winRate      = winRate;
        this.expectancy   = expectancy;
        this.profitFactor = profitFactor;
        this.maxDrawdown  = maxDrawdown;
        this.avgWin       = avgWin;
        this.avgLoss      = avgLoss;

        this.tradeCountPass   = totalTrades >= MIN_TRADES;
        this.winRatePass      = winRate.compareTo(BigDecimal.valueOf(MIN_WIN_RATE)) >= 0;
        this.expectancyPass   = expectancy.compareTo(BigDecimal.ZERO) > 0;
        this.profitFactorPass = profitFactor.compareTo(BigDecimal.valueOf(MIN_PROFIT_FACTOR)) >= 0;
        this.drawdownPass     = maxDrawdown.compareTo(BigDecimal.valueOf(MAX_DRAWDOWN_THRESHOLD)) < 0;
        this.rrRatioPass      = avgLoss.compareTo(BigDecimal.ZERO) == 0
                || avgWin.compareTo(avgLoss) > 0;
    }

    /** Returns true only when ALL six criteria pass. */
    public boolean isValid() {
        return tradeCountPass && winRatePass && expectancyPass
                && profitFactorPass && drawdownPass && rrRatioPass;
    }

    /** Prints a formatted human-readable report to stdout. */
    public void printReport() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║         BACKTEST VALIDATION REPORT                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        printLine("Trade Count >= 5",         tradeCountPass,   String.valueOf(totalTrades));
        printLine("Win Rate >= 60%",           winRatePass,      pct(winRate));
        printLine("Expectancy > 0",            expectancyPass,   "$" + fmt(expectancy));
        printLine("Profit Factor >= 1.2",      profitFactorPass, fmt(profitFactor));
        printLine("Max Drawdown <= 25%",       drawdownPass,     pct(maxDrawdown));
        printLine("Avg Win > Avg Loss",       rrRatioPass,
                "$" + fmt(avgWin) + " vs $" + fmt(avgLoss));
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        String status = isValid() ? "✅  PASSED - Strategy is VALID" : "❌  FAILED - Strategy NEEDS WORK";
        System.out.printf("║  %-56s ║%n", status);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printLine(String criterion, boolean passed, String value) {
        String icon  = passed ? "✅" : "❌";
        System.out.printf("║  %s  %-36s %-14s ║%n", icon, criterion, value);
    }

    private String pct(BigDecimal v) {
        return v.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Returns a list of human-readable failure reasons (empty if all pass). */
    public List<String> getFailureReasons() {
        List<String> reasons = new ArrayList<>();
        if (!tradeCountPass)   reasons.add("Insufficient trades: " + totalTrades + " (need >= " + MIN_TRADES + ")");
        if (!winRatePass)      reasons.add("Win rate too low: " + pct(winRate) + " (need >= 60%)");
        if (!expectancyPass)   reasons.add("Negative expectancy: " + fmt(expectancy));
        if (!profitFactorPass) reasons.add("Low profit factor: " + fmt(profitFactor) + " (need >= 1.2)");
        if (!drawdownPass)     reasons.add("Max drawdown too high: " + pct(maxDrawdown) + " (need <= 25%)");
        if (!rrRatioPass)      reasons.add("Avg win <= avg loss: $" + fmt(avgWin) + " vs $" + fmt(avgLoss));
        return reasons;
    }

    // ---- Getters ----
    public int      getTotalTrades()  { return totalTrades; }
    public BigDecimal getWinRate()    { return winRate; }
    public BigDecimal getExpectancy() { return expectancy; }
    public BigDecimal getProfitFactor() { return profitFactor; }
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public BigDecimal getAvgWin()     { return avgWin; }
    public BigDecimal getAvgLoss()    { return avgLoss; }
    public boolean isTradeCountPass() { return tradeCountPass; }
    public boolean isWinRatePass()    { return winRatePass; }
    public boolean isExpectancyPass() { return expectancyPass; }
    public boolean isProfitFactorPass(){ return profitFactorPass; }
    public boolean isDrawdownPass()   { return drawdownPass; }
    public boolean isRrRatioPass()    { return rrRatioPass; }
}
