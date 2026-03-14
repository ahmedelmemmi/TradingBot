package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link MarketDataProvider} implementation backed by {@link MockMarketDataService}.
 *
 * <p>Generates synthetic candles for a given market scenario. The default Spring-managed
 * bean uses {@link MockMarketDataService.MarketScenario#STRONG_UPTREND}. Use the static
 * factory method {@link #forScenario} to create transient provider instances for specific
 * scenarios in endpoint handlers.</p>
 */
@Component
public class MockMarketDataProvider implements MarketDataProvider {

    private final MockMarketDataService mockMarketDataService;
    private final MockMarketDataService.MarketScenario scenario;

    /**
     * Primary Spring-managed constructor — uses {@link MockMarketDataService.MarketScenario#STRONG_UPTREND}.
     */
    @Autowired
    public MockMarketDataProvider(MockMarketDataService mockMarketDataService) {
        this.mockMarketDataService = mockMarketDataService;
        this.scenario              = MockMarketDataService.MarketScenario.STRONG_UPTREND;
    }

    /** Internal constructor for scenario-specific instances. */
    MockMarketDataProvider(MockMarketDataService mockMarketDataService,
                            MockMarketDataService.MarketScenario scenario) {
        this.mockMarketDataService = mockMarketDataService;
        this.scenario              = scenario;
    }

    /**
     * Creates a provider that generates candles for the specified scenario.
     * The returned instance is <em>not</em> Spring-managed.
     *
     * @param service  the underlying mock service
     * @param scenario the market scenario to simulate
     * @return a new provider instance
     */
    public static MockMarketDataProvider forScenario(MockMarketDataService service,
                                                      MockMarketDataService.MarketScenario scenario) {
        return new MockMarketDataProvider(service, scenario);
    }

    @Override
    public List<Candle> getCandles(String symbol, int count) {
        return mockMarketDataService.generateCandles(symbol, count, scenario);
    }

    @Override
    public String getProviderName() {
        return "MockMarketDataProvider[" + scenario.name() + "]";
    }

    /** Returns the scenario this provider simulates. */
    public MockMarketDataService.MarketScenario getScenario() {
        return scenario;
    }
}
