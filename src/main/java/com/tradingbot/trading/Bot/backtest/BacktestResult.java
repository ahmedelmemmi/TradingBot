package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;

public class BacktestResult {
    private BigDecimal startingCapital;
    private BigDecimal endingCapital;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal totalPnL;

    public BacktestResult(BigDecimal startingCapital,
                          BigDecimal endingCapital,
                          int totalTrades,
                          int winningTrades,
                          int losingTrades,
                          BigDecimal totalPnL) {
        this.startingCapital = startingCapital;
        this.endingCapital = endingCapital;
        this.totalTrades = totalTrades;
        this.winningTrades = winningTrades;
        this.losingTrades = losingTrades;
        this.totalPnL = totalPnL;
    }

    public BigDecimal getStartingCapital() { return startingCapital; }
    public BigDecimal getEndingCapital() { return endingCapital; }
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public BigDecimal getTotalPnL() { return totalPnL; }
}
