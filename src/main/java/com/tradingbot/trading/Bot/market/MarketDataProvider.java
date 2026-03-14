package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Abstraction layer for market data sources.
 * Implementations can provide mock data (for backtesting system validation),
 * real historical data (for strategy validation), or live data (for live trading).
 *
 * <p>Use this interface to switch between data sources without changing strategy code.</p>
 */
public interface MarketDataProvider {

    /**
     * Returns the name of this data provider (e.g., "MOCK", "YAHOO_FINANCE").
     */
    String getProviderName();

    /**
     * Fetches historical candles for the given symbol.
     *
     * @param symbol ticker symbol (e.g., "SPY", "AAPL")
     * @param count  maximum number of candles to return
     * @param from   start of the date range (inclusive); may be null for providers that ignore it
     * @param to     end of the date range (inclusive); may be null for providers that ignore it
     * @return list of candles ordered oldest-first
     */
    List<Candle> getCandles(String symbol, int count, LocalDateTime from, LocalDateTime to);
}
