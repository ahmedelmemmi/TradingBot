package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RsiStrategyService implements Strategy {

    private final RsiCalculator rsiCalculator;

    public RsiStrategyService(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    @Override
    public String getName() {
        return "RSI Mean Reversion";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        System.out.println("RSI evaluate called. Candle size: " + candles.size());

        if (candles.size() < 21) {
            System.out.println("Not enough candles yet.");
            return TradingSignal.HOLD;
        }

        BigDecimal rsi = rsiCalculator.calculate(candles);
        BigDecimal ma20 = calculateMA(candles, 20);

        Candle last = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);

        BigDecimal lastPrice = last.getClose();
        BigDecimal prevPrice = prev.getClose();

        System.out.println("RSI = " + rsi);
        System.out.println("MA20 = " + ma20);
        System.out.println("Last price = " + lastPrice);

        boolean oversold = rsi.compareTo(BigDecimal.valueOf(30)) < 0;
        boolean belowMA = lastPrice.compareTo(ma20) < 0;
        boolean bounce = lastPrice.compareTo(prevPrice) > 0;

        if (oversold && belowMA && bounce) {

            System.out.println("BUY signal:");
            System.out.println("RSI oversold ✔");
            System.out.println("Price below MA ✔");
            System.out.println("Bounce confirmed ✔");

            return TradingSignal.BUY;
        }

        return TradingSignal.HOLD;
    }

    private BigDecimal calculateMA(List<Candle> candles, int period) {

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
}