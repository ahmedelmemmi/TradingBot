package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;

public class BacktestResult {
    private BigDecimal startingCapital;
    private BigDecimal endingCapital;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal totalPnL;
    private BigDecimal winRate;
    private BigDecimal profitFactor;
    private BigDecimal avgWin;
    private BigDecimal avgLoss;
    private BigDecimal expectancy;
    private BigDecimal maxDrawdown;

    public BacktestResult(BigDecimal startingCapital,
                          BigDecimal endingCapital,
                          int totalTrades,
                          int winningTrades,
                          int losingTrades,
                          BigDecimal totalPnL,
                          BigDecimal winRate,
                          BigDecimal profitFactor,
                          BigDecimal avgWin,
                          BigDecimal avgLoss,
                          BigDecimal expectancy,
                          BigDecimal maxDrawdown) {
        this.startingCapital = startingCapital;
        this.endingCapital = endingCapital;
        this.totalTrades = totalTrades;
        this.winningTrades = winningTrades;
        this.losingTrades = losingTrades;
        this.totalPnL = totalPnL;
        this.winRate = winRate;
        this.profitFactor = profitFactor;
        this.avgWin = avgWin;
        this.avgLoss = avgLoss;
        this.expectancy = expectancy;
        this.maxDrawdown = maxDrawdown;
    }

    public BigDecimal getStartingCapital() { return startingCapital; }
    public BigDecimal getEndingCapital() { return endingCapital; }
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public BigDecimal getTotalPnL() { return totalPnL; }
    public BigDecimal getWinRate() { return winRate; }
    public BigDecimal getProfitFactor() { return profitFactor; }
    public BigDecimal getAvgWin() { return avgWin; }
    public BigDecimal getAvgLoss() { return avgLoss; }
    public BigDecimal getExpectancy() { return expectancy; }
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
}
