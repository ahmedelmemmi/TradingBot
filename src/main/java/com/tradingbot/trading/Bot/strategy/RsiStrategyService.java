package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RsiStrategyService {
    private final RsiCalculator rsiCalculator;
    private BigDecimal lastRsi = BigDecimal.ZERO;

    public RsiStrategyService(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    public TradingSignal evaluate(List<Candle> candles) {
        System.out.println("RSI evaluate called. Candle size: " + candles.size());
        if (candles.size() < 15) {
            System.out.println("Not enough candles for RSI: " + candles.size());
            return TradingSignal.HOLD;
        }

        List<Candle> lastCandles =
                candles.subList(candles.size() - 15, candles.size());

        BigDecimal rsi = rsiCalculator.calculate(lastCandles);

        lastRsi = rsi;

        System.out.println("Computed RSI value: " + rsi);
        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            System.out.println("Returning signal: " + TradingSignal.BUY);
            return TradingSignal.BUY;
        }

        if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            System.out.println("Returning signal: " + TradingSignal.SELL);
            return TradingSignal.SELL;
        }
        System.out.println("Returning signal: " + TradingSignal.HOLD);
        return TradingSignal.HOLD;
    }
}
