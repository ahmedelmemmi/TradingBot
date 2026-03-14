package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link MarketDataProvider} backed by the existing {@link MockMarketDataService}.
 * Generates synthetic candles for system-level backtest validation.
 * Use this to verify that the backtest engine, risk management, and strategy
 * infrastructure all work correctly before moving to real data.
 */
@Component
public class MockMarketDataProvider implements MarketDataProvider {

    private final MockMarketDataService mockService;

    public MockMarketDataProvider(MockMarketDataService mockService) {
        this.mockService = mockService;
    }

    @Override
    public String getProviderName() {
        return "MOCK";
    }

    /**
     * Generates synthetic candles using the STRONG_UPTREND scenario by default.
     * The {@code from}/{@code to} parameters are ignored because mock data
     * is generated on-the-fly without real timestamps.
     */
    @Override
    public List<Candle> getCandles(String symbol, int count,
                                   LocalDateTime from, LocalDateTime to) {
        return mockService.generateCandles(
                symbol,
                count,
                MockMarketDataService.MarketScenario.STRONG_UPTREND
        );
    }
}
