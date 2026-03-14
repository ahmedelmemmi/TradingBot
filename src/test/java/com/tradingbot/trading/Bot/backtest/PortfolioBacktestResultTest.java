package com.tradingbot.trading.Bot.backtest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioBacktestResultTest {

    @Test
    void resultContainsAllTradeStatistics() {
        PortfolioBacktestResult result = new PortfolioBacktestResult(
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(11_000),
                BigDecimal.valueOf(1_000),
                List.of(BigDecimal.valueOf(10_000), BigDecimal.valueOf(11_000)),
                20,
                12,
                8,
                BigDecimal.valueOf(0.6),
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(0.05)
        );

        assertEquals(BigDecimal.valueOf(10_000), result.getStartCapital());
        assertEquals(BigDecimal.valueOf(11_000), result.getEndCapital());
        assertEquals(BigDecimal.valueOf(1_000), result.getTotalPnL());
        assertEquals(20, result.getTotalTrades());
        assertEquals(12, result.getWinningTrades());
        assertEquals(8, result.getLosingTrades());
        assertEquals(BigDecimal.valueOf(0.6), result.getWinRate());
        assertEquals(BigDecimal.valueOf(1.5), result.getProfitFactor());
        assertEquals(BigDecimal.valueOf(50), result.getExpectancy());
        assertEquals(BigDecimal.valueOf(0.05), result.getMaxDrawdown());
        assertNotNull(result.getEquityCurve());
        assertEquals(2, result.getEquityCurve().size());
    }
}
