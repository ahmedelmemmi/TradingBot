package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.util.List;

/**
 * No-Trade Strategy for capital preservation in unfavourable regimes.
 *
 * <p>Used for STRONG_DOWNTREND, CRASH, and HIGH_VOLATILITY regimes where
 * trading is halted to preserve capital. Always returns HOLD.</p>
 */
public class NoTradeStrategy implements Strategy {

    @Override
    public String getName() {
        return "NoTradeStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {
        return TradingSignal.HOLD;
    }
}
