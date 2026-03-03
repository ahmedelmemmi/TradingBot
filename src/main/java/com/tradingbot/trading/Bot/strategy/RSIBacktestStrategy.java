package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.util.List;

public class RSIBacktestStrategy implements Strategy{
    private final RsiCalculator rsiCalculator;

    public RSIBacktestStrategy(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    @Override
    public String getName() {
        return "RSI Oversold";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {
        return new RsiStrategyService(rsiCalculator).evaluate(candles);
    }
}
