package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Complete result of a single-symbol backtest including detailed metrics,
 * equity curve, and full trade log for external analysis.
 */
public class BacktestResult {
    private BigDecimal startingCapital;
    private BigDecimal endingCapital;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal totalPnL;
    private BigDecimal winRate;
    private BigDecimal profitFactor;
    private BigDecimal expectancy;
    private BigDecimal maxDrawdown;
    private List<BigDecimal> equityCurve;
    private List<TradeRecord> tradeLog;

    public BacktestResult(BigDecimal startingCapital,
                          BigDecimal endingCapital,
                          int totalTrades,
                          int winningTrades,
                          int losingTrades,
                          BigDecimal totalPnL,
                          BigDecimal winRate,
                          BigDecimal profitFactor,
                          BigDecimal expectancy,
                          BigDecimal maxDrawdown,
                          List<BigDecimal> equityCurve,
                          List<TradeRecord> tradeLog) {
        this.startingCapital = startingCapital;
        this.endingCapital   = endingCapital;
        this.totalTrades     = totalTrades;
        this.winningTrades   = winningTrades;
        this.losingTrades    = losingTrades;
        this.totalPnL        = totalPnL;
        this.winRate         = winRate;
        this.profitFactor    = profitFactor;
        this.expectancy      = expectancy;
        this.maxDrawdown     = maxDrawdown;
        this.equityCurve     = equityCurve;
        this.tradeLog        = tradeLog;
    }

    public BigDecimal getStartingCapital() { return startingCapital; }
    public BigDecimal getEndingCapital()   { return endingCapital; }
    public int getTotalTrades()            { return totalTrades; }
    public int getWinningTrades()          { return winningTrades; }
    public int getLosingTrades()           { return losingTrades; }
    public BigDecimal getTotalPnL()        { return totalPnL; }
    public BigDecimal getWinRate()         { return winRate; }
    public BigDecimal getProfitFactor()    { return profitFactor; }
    public BigDecimal getExpectancy()      { return expectancy; }
    public BigDecimal getMaxDrawdown()     { return maxDrawdown; }
    public List<BigDecimal> getEquityCurve() { return equityCurve; }
    public List<TradeRecord> getTradeLog() { return tradeLog; }

    /** Returns the trade log as a CSV string. */
    public String getTradeLogCsv() {
        if (tradeLog == null || tradeLog.isEmpty()) return TradeRecord.csvHeader();
        StringBuilder sb = new StringBuilder(TradeRecord.csvHeader()).append("\n");
        tradeLog.forEach(r -> sb.append(r.toCsvRow()).append("\n"));
        return sb.toString();
    }
}
