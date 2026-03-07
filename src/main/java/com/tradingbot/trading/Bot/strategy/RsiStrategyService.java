package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RsiStrategyService {
    private final RsiCalculator rsiCalculator;

    public RsiStrategyService(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    public TradingSignal evaluate(List<Candle> candles) {

        System.out.println("RSI evaluate called. Candle size: " + candles.size());

        if (candles.size() < 20) {
            System.out.println("Not enough candles yet.");
            return TradingSignal.HOLD;
        }

        BigDecimal rsi = rsiCalculator.calculate(candles);

        System.out.println("Computed RSI value: " + rsi);

        BigDecimal movingAverage = calculateMA(candles, 20);

        BigDecimal lastPrice = candles.get(candles.size() - 1).getClose();

        System.out.println("MA20: " + movingAverage);
        System.out.println("Last Price: " + lastPrice);

        if (rsi.compareTo(BigDecimal.valueOf(40)) < 0 &&
                lastPrice.compareTo(movingAverage) > 0) {

            System.out.println("Returning signal: BUY");
            return TradingSignal.BUY;
        }

        System.out.println("Returning signal: HOLD");

        return TradingSignal.HOLD;
    }

    private BigDecimal calculateMA(List<Candle> candles, int period) {

        BigDecimal sum = BigDecimal.ZERO;

        int start = candles.size() - period;

        for (int i = start; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
}
