package com.tradingbot.trading.Bot.risk;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveRiskServiceTest {

    private AdaptiveRiskService service;

    @BeforeEach
    void setUp() {
        service = new AdaptiveRiskService();
    }

    @Test
    void kellyReturnsDefaultWhenInsufficientTrades() {
        List<Position> positions = new ArrayList<>(); // empty
        BigDecimal risk = service.calculateKellyRiskPercent(positions);
        // Should return 1% default
        assertEquals(0, BigDecimal.valueOf(0.01).compareTo(risk),
                "Should return default 1% with no trades");
    }

    @Test
    void kellyReturnsMinRiskWhenAllLosses() {
        List<Position> positions = buildPositions(30, -50.0); // all losing
        BigDecimal risk = service.calculateKellyRiskPercent(positions);
        // Should return min risk 0.5%
        assertTrue(risk.compareTo(BigDecimal.valueOf(0.005)) >= 0,
                "Risk should be at least minimum 0.5%");
        assertTrue(risk.compareTo(BigDecimal.valueOf(0.02)) <= 0,
                "Risk should not exceed maximum 2%");
    }

    @Test
    void kellyReturnsCappedRiskWhenAllWins() {
        List<Position> positions = buildPositions(30, 100.0); // all winning
        BigDecimal risk = service.calculateKellyRiskPercent(positions);
        // Max risk is 2%
        assertTrue(risk.compareTo(BigDecimal.valueOf(0.02)) <= 0,
                "Risk should not exceed maximum 2%");
    }

    @Test
    void adaptiveStopIsLowerThanEntryPrice() {
        List<Candle> candles = generateCandles(30, 100.0, 0.5);
        BigDecimal entryPrice = BigDecimal.valueOf(120.0);

        BigDecimal stop = service.calculateAdaptiveStop(candles, entryPrice, MarketRegime.STRONG_UPTREND);

        assertTrue(stop.compareTo(entryPrice) < 0,
                "Stop loss should be below entry price");
    }

    @Test
    void adaptiveStopIsWiderInCrashRegime() {
        List<Candle> candles = generateCandles(30, 100.0, -2.0);
        BigDecimal entryPrice = BigDecimal.valueOf(50.0);

        BigDecimal stopUptrend = service.calculateAdaptiveStop(candles, entryPrice, MarketRegime.STRONG_UPTREND);
        BigDecimal stopCrash   = service.calculateAdaptiveStop(candles, entryPrice, MarketRegime.CRASH);

        assertTrue(stopCrash.compareTo(stopUptrend) <= 0,
                "CRASH stop should be equal or wider than UPTREND stop");
    }

    private List<Position> buildPositions(int count, double pnlPerTrade) {
        List<Position> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Position p = new Position("TEST",
                    BigDecimal.valueOf(100),
                    BigDecimal.TEN,
                    BigDecimal.valueOf(98),
                    BigDecimal.valueOf(104));
            double exitPrice = pnlPerTrade > 0
                    ? 100 + Math.abs(pnlPerTrade) / 10.0
                    : 100 - Math.abs(pnlPerTrade) / 10.0;
            p.close(BigDecimal.valueOf(exitPrice));
            positions.add(p);
        }
        return positions;
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
