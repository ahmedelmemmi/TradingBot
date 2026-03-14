package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioBacktestResult {
    private final BigDecimal startCapital;
    private final BigDecimal endCapital;
    private final BigDecimal totalPnL;
    private final List<BigDecimal> equityCurve;
    private final int totalTrades;
    private final int winningTrades;
    private final int losingTrades;
    private final BigDecimal winRate;
    private final BigDecimal profitFactor;
    private final BigDecimal expectancy;
    private final BigDecimal maxDrawdown;

    public PortfolioBacktestResult(BigDecimal startCapital,
                                   BigDecimal endCapital,
                                   BigDecimal totalPnL,
                                   List<BigDecimal> equityCurve,
                                   int totalTrades,
                                   int winningTrades,
                                   int losingTrades,
                                   BigDecimal winRate,
                                   BigDecimal profitFactor,
                                   BigDecimal expectancy,
                                   BigDecimal maxDrawdown) {
        this.startCapital = startCapital;
        this.endCapital = endCapital;
        this.totalPnL = totalPnL;
        this.equityCurve = equityCurve;
        this.totalTrades = totalTrades;
        this.winningTrades = winningTrades;
        this.losingTrades = losingTrades;
        this.winRate = winRate;
        this.profitFactor = profitFactor;
        this.expectancy = expectancy;
        this.maxDrawdown = maxDrawdown;
    }

    public BigDecimal getStartCapital() { return startCapital; }
    public BigDecimal getEndCapital() { return endCapital; }
    public BigDecimal getTotalPnL() { return totalPnL; }
    public List<BigDecimal> getEquityCurve() { return equityCurve; }
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public BigDecimal getWinRate() { return winRate; }
    public BigDecimal getProfitFactor() { return profitFactor; }
    public BigDecimal getExpectancy() { return expectancy; }
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
}
