package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RsiStrategyService implements Strategy{
    private final RsiCalculator rsiCalculator;

    public RsiStrategyService(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    @Override
    public String getName() {
        return null;
    }

    public TradingSignal evaluate(List<Candle> candles) {

        System.out.println("RSI evaluate called. Candle size: " + candles.size());

        if (candles.size() < 20) {
            System.out.println("Not enough candles yet.");
            return TradingSignal.HOLD;
        }

        BigDecimal rsi = rsiCalculator.calculate(candles);
        BigDecimal movingAverage = calculateMA(candles, 20);
        BigDecimal avgVolume = calculateAverageVolume(candles, 20);

        Candle lastCandle = candles.get(candles.size() - 1);

        BigDecimal lastPrice = lastCandle.getClose();
        BigDecimal lastVolume = BigDecimal.valueOf(lastCandle.getVolume());

        System.out.println("Computed RSI value: " + rsi);
        System.out.println("MA20: " + movingAverage);
        System.out.println("Last Price: " + lastPrice);
        System.out.println("Last Volume: " + lastVolume);
        System.out.println("Average Volume: " + avgVolume);

        boolean rsiCondition = rsi.compareTo(BigDecimal.valueOf(40)) < 0;
        boolean trendCondition = lastPrice.compareTo(movingAverage) > 0;

        BigDecimal volumeThreshold =
                avgVolume.multiply(BigDecimal.valueOf(1.5));

        boolean volumeCondition =
                lastVolume.compareTo(volumeThreshold) > 0;

        if (rsiCondition && trendCondition && volumeCondition) {

            System.out.println("BUY conditions satisfied:");
            System.out.println("RSI < 40 ✔");
            System.out.println("Price > MA20 ✔");
            System.out.println("Volume spike ✔");

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

    private BigDecimal calculateAverageVolume(List<Candle> candles, int period) {

        BigDecimal sum = BigDecimal.ZERO;

        int start = candles.size() - period;

        for (int i = start; i < candles.size(); i++) {
            sum = sum.add(BigDecimal.valueOf(candles.get(i).getVolume()));
        }

        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
}
