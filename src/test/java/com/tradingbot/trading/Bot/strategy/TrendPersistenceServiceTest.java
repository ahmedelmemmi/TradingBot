package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrendPersistenceServiceTest {

    private TrendPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new TrendPersistenceService();
    }

    @Test
    void isUptrendPersistentReturnsFalseForInsufficientCandles() {
        List<Candle> candles = generateCandles(50, 100.0, 0.5);
        assertFalse(service.isUptrendPersistent(candles, 3),
                "Should return false when fewer than 53 candles");
    }

    @Test
    void isUptrendPersistentReturnsTrueForStrongUptrend() {
        // 80 candles with steady upward trend (MA20 will be > MA50)
        List<Candle> candles = generateCandles(80, 100.0, 1.0);
        assertTrue(service.isUptrendPersistent(candles, 3),
                "Strong steady uptrend should return true");
    }

    @Test
    void isUptrendPersistentReturnsFalseForDowntrend() {
        // 80 candles with steady downward trend (MA20 will be < MA50)
        List<Candle> candles = generateCandles(80, 200.0, -1.0);
        assertFalse(service.isUptrendPersistent(candles, 3),
                "Downtrend should return false for isUptrendPersistent");
    }

    @Test
    void isDowntrendPersistentReturnsTrueForStrongDowntrend() {
        // 80 candles with steady downward trend
        List<Candle> candles = generateCandles(80, 200.0, -1.0);
        assertTrue(service.isDowntrendPersistent(candles, 3),
                "Strong steady downtrend should return true");
    }

    @Test
    void isDowntrendPersistentReturnsFalseForUptrend() {
        List<Candle> candles = generateCandles(80, 100.0, 1.0);
        assertFalse(service.isDowntrendPersistent(candles, 3),
                "Uptrend should return false for isDowntrendPersistent");
    }

    private List<Candle> generateCandles(int count, double startPrice, double trend) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now();
        double price = startPrice;
        for (int i = 0; i < count; i++) {
            price = Math.max(0.01, price + trend);
            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price - 0.5),
                    BigDecimal.valueOf(price + 1.0),
                    BigDecimal.valueOf(price - 1.0),
                    BigDecimal.valueOf(price),
                    100_000));
        }
        return candles;
    }
}
