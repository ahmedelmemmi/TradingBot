package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;

import java.util.List;

/**
 * Abstraction layer for market data sources.
 *
 * <p>Implementations can be swapped to support mock data (for backtesting),
 * historical real data (e.g. Yahoo Finance), or live data (e.g. IBKR)
 * without changing any strategy or backtest engine code.</p>
 */
public interface MarketDataProvider {

    /**
     * Returns a list of candles for the given symbol and scenario/configuration.
     *
     * @param symbol the ticker symbol (e.g. "AAPL")
     * @param count  number of candles to generate or fetch
     * @return list of candles, ordered oldest-first
     */
    List<Candle> getCandles(String symbol, int count);

    /**
     * Returns a human-readable name for this provider (used in API responses).
     */
    String getProviderName();
}
