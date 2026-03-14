package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;

import java.time.LocalDateTime;
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
     * Returns a list of candles for the given symbol within a date range.
     * Implementations that do not support date-range queries should override
     * this method; the default falls back to {@link #getCandles(String, int)}.
     *
     * @param symbol the ticker symbol (e.g. "SPY")
     * @param count  approximate number of candles (hint; implementations may ignore)
     * @param from   start of the date range (inclusive)
     * @param to     end of the date range (inclusive)
     * @return list of candles, ordered oldest-first
     */
    default List<Candle> getCandles(String symbol, int count, LocalDateTime from, LocalDateTime to) {
        return getCandles(symbol, count);
    }

    /**
     * Returns a human-readable name for this provider (used in API responses).
     */
    String getProviderName();
}
