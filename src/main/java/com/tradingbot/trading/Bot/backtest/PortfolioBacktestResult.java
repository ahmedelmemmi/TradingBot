package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioBacktestResult {
    private final BigDecimal startCapital;
    private final BigDecimal endCapital;
    private final BigDecimal totalPnL;
    private final List<BigDecimal> equityCurve;

    public PortfolioBacktestResult(BigDecimal startCapital,
                                   BigDecimal endCapital,
                                   BigDecimal totalPnL,
                                   List<BigDecimal> equityCurve) {
        this.startCapital = startCapital;
        this.endCapital = endCapital;
        this.totalPnL = totalPnL;
        this.equityCurve = equityCurve;
    }

    public BigDecimal getStartCapital() { return startCapital; }
    public BigDecimal getEndCapital() { return endCapital; }
    public BigDecimal getTotalPnL() { return totalPnL; }
    public List<BigDecimal> getEquityCurve() { return equityCurve; }
}
