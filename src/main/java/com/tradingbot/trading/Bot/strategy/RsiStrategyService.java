package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RsiStrategyService {
    private final RsiCalculator rsiCalculator;

    public RsiStrategyService(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    public TradingSignal evaluate(List<Candle> candles) {

        BigDecimal rsi = rsiCalculator.calculate(candles);

        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            return TradingSignal.BUY;
        }

        if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            return TradingSignal.SELL;
        }

        return TradingSignal.HOLD;
    }
}
