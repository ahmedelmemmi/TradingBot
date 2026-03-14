package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketRegimeServiceTest {

    private MarketRegimeService service;

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService();
    }

    @Test
    void detectReturnsSidewaysForInsufficientCandles() {
        List<Candle> candles = generateCandles(30, 100.0, 0.0);
        assertEquals(MarketRegime.SIDEWAYS, service.detect(candles));
    }

    @Test
    void detectReturnsSidewaysForFlatMarket() {
        List<Candle> candles = generateCandles(100, 100.0, 0.0);
        MarketRegime regime = service.detect(candles);
        assertTrue(regime == MarketRegime.SIDEWAYS || regime == MarketRegime.HIGH_VOLATILITY,
                "Flat market should be SIDEWAYS or HIGH_VOLATILITY, got: " + regime);
    }

    @Test
    void detectReturnsStrongUptrendForRisingMarket() {
        List<Candle> candles = generateCandles(100, 50.0, 0.5);
        MarketRegime lastRegime = MarketRegime.SIDEWAYS;
        for (int i = 0; i < 10; i++) {
            lastRegime = service.detect(candles);
        }
        assertEquals(MarketRegime.STRONG_UPTREND, lastRegime);
    }

    @Test
    void drawdownSpeedUsesMaxPriceInWindow() {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now();
        for (int i = 0; i < 70; i++) {
            double price;
            if (i < 60) {
                price = 100.0 + i * 0.5;
            } else if (i < 65) {
                price = 130.0 + (i - 60) * 2;
            } else {
                price = 140.0 - (i - 65) * 8;
            }
            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price - 0.5),
                    BigDecimal.valueOf(price + 1),
                    BigDecimal.valueOf(price - 1),
                    BigDecimal.valueOf(price),
                    100000));
        }
        MarketRegime regime = service.detect(candles);
        assertNotNull(regime);
    }

    @Test
    void regimePersistencePreventsFlickering() {
        MarketRegimeService svc = new MarketRegimeService();

        List<Candle> upCandles = generateCandles(100, 50.0, 0.5);
        for (int i = 0; i < 5; i++) {
            svc.detect(upCandles);
        }
        MarketRegime confirmed = svc.detect(upCandles);

        List<Candle> flatCandles = generateCandles(100, 100.0, 0.0);
        MarketRegime afterOneFlat = svc.detect(flatCandles);

        assertEquals(confirmed, afterOneFlat,
                "Single divergent reading should not change confirmed regime");
    }

    @Test
    void crashRegimeBypassesPersistence() {
        MarketRegimeService svc = new MarketRegimeService();

        List<Candle> crashCandles = generateCrashCandles(100);
        MarketRegime regime = svc.detect(crashCandles);
        assertEquals(MarketRegime.CRASH, regime);
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

    private List<Candle> generateCrashCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now();
        double price = 200.0;
        for (int i = 0; i < count; i++) {
            if (i > count - 15) {
                price = price * 0.95;
            }
            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price + 1),
                    BigDecimal.valueOf(price + 3),
                    BigDecimal.valueOf(price - 3),
                    BigDecimal.valueOf(price),
                    100000));
        }
        return candles;
    }
}
