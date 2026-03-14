package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Backtest result for the hybrid regime-aware strategy.
 * Extends {@link PortfolioBacktestResult} with per-strategy trade count tracking.
 */
public class HybridBacktestResult {

    private final PortfolioBacktestResult base;
    private final Map<String, Integer> tradesByStrategy;

    public HybridBacktestResult(PortfolioBacktestResult base,
                                Map<String, Integer> tradesByStrategy) {
        this.base             = base;
        this.tradesByStrategy = tradesByStrategy;
    }

    // Delegate to base result
    public BigDecimal getStartCapital()  { return base.getStartCapital(); }
    public BigDecimal getEndCapital()    { return base.getEndCapital(); }
    public BigDecimal getTotalPnL()      { return base.getTotalPnL(); }
    public int        getTotalTrades()   { return base.getTotalTrades(); }
    public int        getWinningTrades() { return base.getWinningTrades(); }
    public int        getLosingTrades()  { return base.getLosingTrades(); }
    public BigDecimal getWinRate()       { return base.getWinRate(); }
    public BigDecimal getProfitFactor()  { return base.getProfitFactor(); }
    public BigDecimal getExpectancy()    { return base.getExpectancy(); }
    public BigDecimal getMaxDrawdown()   { return base.getMaxDrawdown(); }

    /**
     * Number of trades executed per strategy name.
     * Keys match {@link com.tradingbot.trading.Bot.strategy.Strategy#getName()}.
     */
    public Map<String, Integer> getTradesByStrategy() { return tradesByStrategy; }
}
