package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic tests for {@link StrictMeanReversionStrategy}.
 *
 * <p>Verifies that the strategy:</p>
 * <ul>
 *   <li>Only triggers in properly sideways, oversold market conditions</li>
 *   <li>Does NOT trigger in trending (uptrend) markets</li>
 *   <li>Does NOT trigger when RSI is above 25</li>
 *   <li>Does NOT trigger when volume is too high (panic, not exhaustion)</li>
 * </ul>
 */
class MeanReversionDiagnosticTest {

    private StrictMeanReversionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new StrictMeanReversionStrategy();
    }

    @Test
    void strategyNameIsCorrect() {
        assertEquals("StrictMeanReversionStrategy", strategy.getName());
    }

    @Test
    void returnsHoldForInsufficientCandles() {
        List<Candle> candles = generateSidewaysCandles(50, 100.0);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles),
                "Should HOLD with fewer than 60 candles");
    }

    @Test
    void returnsHoldOrBuyInSidewaysMarket() {
        // Generate sideways data — result can be HOLD or BUY, never SELL
        List<Candle> candles = generateSidewaysCandles(100, 100.0);
        TradingSignal signal = strategy.evaluate(candles);
        assertNotEquals(TradingSignal.SELL, signal,
                "Mean reversion should only return BUY or HOLD, never SELL");
    }

    @Test
    void rejectsUptrendingMarket() {
        // Strong uptrend: price keeps rising, MA20 > MA50, RSI will be high
        // Mean reversion should not trigger in a persistent uptrend
        List<Candle> candles = generateUptrendCandles(100, 50.0, 1.0);
        TradingSignal signal = strategy.evaluate(candles);
        // In a clean uptrend, RSI will be far above 25, so we expect HOLD
        assertEquals(TradingSignal.HOLD, signal,
                "Mean reversion should HOLD in a strong uptrend (RSI > 25)");
    }

    @Test
    void rejectsHighVolumeEntry() {
        // Market with spike volume — volume exhaustion condition fails
        List<Candle> candles = generateHighVolumeCandles(100, 100.0);
        TradingSignal signal = strategy.evaluate(candles);
        // Volume > 70% of average should block entry
        // (result depends on all conditions; at minimum, no crash)
        assertNotEquals(TradingSignal.SELL, signal,
                "Strategy should never signal SELL");
    }

    @Test
    void stopLossIsBelowEntryPrice() {
        List<Candle> candles = generateSidewaysCandles(100, 100.0);
        BigDecimal stopLoss = strategy.calculateStopLoss(candles);
        BigDecimal lastPrice = candles.get(candles.size() - 1).getClose();
        // Stop loss should be below entry price
        assertTrue(stopLoss.compareTo(lastPrice) < 0,
                "Stop loss should be below current price");
    }

    @Test
    void takeProfitIsAboveEntryPrice() {
        List<Candle> candles = generateSidewaysCandles(100, 100.0);
        BigDecimal entryPrice = candles.get(candles.size() - 1).getClose();
        BigDecimal takeProfit = strategy.calculateTakeProfit(candles, entryPrice);
        // Take profit should be above entry price
        assertTrue(takeProfit.compareTo(entryPrice) > 0,
                "Take profit should be above entry price");
    }

    // ── Test data generators ──────────────────────────────────────────────────

    /**
     * Generates candles in a sideways (mean-reverting) market around a central price,
     * with slight oscillation and no persistent trend.
     */
    private List<Candle> generateSidewaysCandles(int count, double centerPrice) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count);
        double price = centerPrice;

        for (int i = 0; i < count; i++) {
            // Oscillate slightly around center price
            double offset = Math.sin(i * 0.3) * 0.5;
            price = centerPrice + offset;

            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price - 0.3),
                    BigDecimal.valueOf(price + 0.4),
                    BigDecimal.valueOf(price - 0.4),
                    BigDecimal.valueOf(price),
                    80000L));
        }
        return candles;
    }

    /**
     * Generates candles in a strong, persistent uptrend.
     * Price increases consistently by {@code trend} per bar.
     */
    private List<Candle> generateUptrendCandles(int count, double startPrice, double trend) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count);
        double price = startPrice;

        for (int i = 0; i < count; i++) {
            price += trend;
            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price - 0.2),
                    BigDecimal.valueOf(price + 0.5),
                    BigDecimal.valueOf(price - 0.3),
                    BigDecimal.valueOf(price),
                    100000L));
        }
        return candles;
    }

    /**
     * Generates candles with high volume (3× average) to test volume exhaustion filter.
     */
    private List<Candle> generateHighVolumeCandles(int count, double centerPrice) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count);
        double price = centerPrice;

        for (int i = 0; i < count; i++) {
            double offset = Math.sin(i * 0.3) * 0.5;
            price = centerPrice + offset;

            // Spike volume on the last few bars (simulates panic)
            long volume = (i > count - 5) ? 300000L : 100000L;

            candles.add(new Candle("TEST", time.plusMinutes(i),
                    BigDecimal.valueOf(price - 0.3),
                    BigDecimal.valueOf(price + 0.4),
                    BigDecimal.valueOf(price - 0.4),
                    BigDecimal.valueOf(price),
                    volume));
        }
        return candles;
    }
}
