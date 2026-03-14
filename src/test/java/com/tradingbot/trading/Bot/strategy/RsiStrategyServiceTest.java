package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RsiStrategyServiceTest {

    private RsiStrategyService strategy;

    @BeforeEach
    void setUp() {
        strategy = new RsiStrategyService(new RsiCalculator());
    }

    @Test
    void evaluateReturnsHoldForInsufficientCandles() {
        List<Candle> candles = generateCandles(30, 100.0, 0.5);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles));
    }

    @Test
    void evaluateReturnsHoldWhenMa20BelowMa50() {
        List<Candle> candles = generateCandles(100, 200.0, -0.5);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles));
    }

    @Test
    void evaluateReturnsHoldWhenPriceBelowMa50() {
        List<Candle> candles = generateUpThenPullback(100, 50.0, 0.5, 30);
        BigDecimal lastPrice = candles.get(candles.size() - 1).getClose();
        BigDecimal ma50 = computeMa(candles, 50);

        if (lastPrice.compareTo(ma50) < 0) {
            assertEquals(TradingSignal.HOLD, strategy.evaluate(candles));
        }
    }

    @Test
    void evaluateDoesNotBuyInFlatMarket() {
        List<Candle> candles = generateCandles(100, 100.0, 0.0);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles),
                "Flat market with no pullback should not trigger BUY");
    }

    @Test
    void nameIsCorrect() {
        assertEquals("RSI Pullback Trend", strategy.getName());
    }

    private List<Candle> generateCandles(int count, double startPrice, double trend) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now();
        double price = startPrice;
        for (int i = 0; i < count; i++) {
            price = price + trend;
            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price - 0.2),
                    BigDecimal.valueOf(price + 0.5),
                    BigDecimal.valueOf(price - 0.5),
                    BigDecimal.valueOf(price),
                    100000));
        }
        return candles;
    }

    private List<Candle> generateUpThenPullback(int total, double startPrice,
                                                 double trend, int pullbackStart) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now();
        double price = startPrice;
        for (int i = 0; i < total; i++) {
            if (i < pullbackStart) {
                price = price + trend;
            } else {
                price = price - trend * 2;
            }
            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price - 0.2),
                    BigDecimal.valueOf(price + 0.5),
                    BigDecimal.valueOf(price - 0.5),
                    BigDecimal.valueOf(price),
                    100000));
        }
        return candles;
    }

    private BigDecimal computeMa(List<Candle> candles, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 6, java.math.RoundingMode.HALF_UP);
    }
}
