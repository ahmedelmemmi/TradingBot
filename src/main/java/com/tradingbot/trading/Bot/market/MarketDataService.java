package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;

import java.util.List;

public interface MarketDataService {
    List<Candle> getHistoricalCandles(String symbol, int numberOfCandles);

    Candle getLatestCandle(String symbol);
}
