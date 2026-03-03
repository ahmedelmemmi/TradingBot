package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.util.List;

public interface Strategy {
    String getName();

    TradingSignal evaluate(List<Candle> candles);
}
