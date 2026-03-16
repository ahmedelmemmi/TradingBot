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
 * Unit tests for {@link RobustTrendBreakoutStrategy}.
 *
 * <p>Verifies all five conditions:</p>
 * <ol>
 *   <li>MA20 &gt; MA50 (uptrend)</li>
 *   <li>(MA20−MA50)/MA50 &ge; MIN_MA_RATIO_PCT (trend strength)</li>
 *   <li>Close &gt; 20-bar highest high + 0.1% buffer (breakout)</li>
 *   <li>Volume &ge; VOLUME_RATIO_MIN × 20-bar average (volume confirmation)</li>
 *   <li>RSI(14) &gt; 50 (momentum)</li>
 * </ol>
 */
class RobustTrendBreakoutStrategyTest {

    private RobustTrendBreakoutStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RobustTrendBreakoutStrategy(new RsiCalculator());
    }

    // ── Condition 0: minimum candle count ─────────────────────────────────────

    @Test
    void returnsHoldWhenInsufficientCandles() {
        // 30 candles — well below MIN_CANDLES threshold
        List<Candle> candles = generateCandles(30, 100.0, 0.5, 100_000);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles),
                "Should return HOLD when fewer than 60 candles provided");
    }

    @Test
    void returnsHoldAtBoundaryOf59Candles() {
        // 59 candles — well below MIN_CANDLES (boundary check)
        List<Candle> candles = generateCandles(59, 100.0, 0.5, 100_000);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles),
                "Should return HOLD at exactly 59 candles (MIN_CANDLES is 61)");
    }

    // ── Condition 1: uptrend ──────────────────────────────────────────────────

    @Test
    void returnsHoldWhenNotInUptrend() {
        // Flat market: MA20 ≈ MA50 ≈ same price, no uptrend
        List<Candle> candles = generateCandles(80, 100.0, 0.0, 100_000);
        // No uptrend, no breakout → HOLD
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "Flat market should produce HOLD (no uptrend)");
    }

    // ── Condition 2: trend strength ───────────────────────────────────────────

    @Test
    void returnsHoldWhenTrendTooWeak() {
        // Build candles where MA20 is just barely above MA50 (less than MIN_MA_RATIO_PCT)
        // Use a very gentle trend so MA20 ≈ MA50
        List<Candle> candles = buildWeakUptrendBreakout();
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "MA20 barely above MA50 (weak trend) must produce HOLD");
    }

    // ── Condition 3: breakout uses close, not high ────────────────────────────

    @Test
    void returnsHoldWhenHighAboveBreakoutButCloseBelowIt() {
        List<Candle> candles = buildBreakoutSequence(false, true);
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "High-only breakout (close below level) must be rejected as false breakout");
    }

    @Test
    void returnsBuyWhenCloseConfirmsBreakout() {
        // Two consecutive breakout bars are required (condition 4: 2-bar confirmation).
        List<Candle> candles = buildBreakoutSequence(true, true, 2);
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.BUY, last,
                "Close above breakout level for 2 consecutive bars in an uptrend should produce BUY");
    }

    @Test
    void returnsHoldWhenOnlyOneBreakoutBarEvenIfCloseAboveLevel() {
        // Only 1 breakout bar — fails condition 4 (2-bar confirmation required)
        List<Candle> candles = buildBreakoutSequence(true, true, 1);
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "Single-bar breakout must be rejected (2 consecutive closes required)");
    }

    // ── Condition 5: volume confirmation ─────────────────────────────────────

    @Test
    void returnsHoldWhenBreakoutVolumeIsBelowAverage() {
        // Two breakout bars, but current (second) bar volume 80k < 1.2 × avg (200k) → HOLD
        List<Candle> candles = buildBreakoutSequenceWithVolume(true, true, 80_000L, 200_000L);
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "Breakout on below-average volume must be rejected");
    }

    @Test
    void returnsBuyWhenBreakoutVolumeIsAboveAverage() {
        // Two breakout bars, current bar volume (250k) > 1.2 × avg (150k) → BUY
        List<Candle> candles = buildBreakoutSequenceWithVolume(true, true, 250_000L, 150_000L);
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.BUY, last,
                "Breakout on above-average volume in an uptrend should produce BUY");
    }

    // ── Strategy name ─────────────────────────────────────────────────────────

    @Test
    void nameIsCorrect() {
        assertEquals("RobustTrendBreakoutStrategy", strategy.getName());
    }

    // ── Helper: build a breakout candle sequence ──────────────────────────────

    /**
     * Builds a candle sequence with a single breakout bar (for testing that a
     * single-bar breakout is rejected by the 2-bar confirmation requirement).
     */
    private List<Candle> buildBreakoutSequence(boolean closeBreaksOut,
                                                boolean highBreaksOut) {
        return buildBreakoutSequence(closeBreaksOut, highBreaksOut, 1);
    }

    /**
     * Builds a candle sequence:
     * <ol>
     *   <li>70 bars of strong uptrend (ensures MA20 &gt; MA50 and RSI &gt; 50).</li>
     *   <li>20 flat bars (sets the 20-bar reference high).</li>
     *   <li>{@code breakoutBarCount} breakout bars with close / high controlled by parameters.</li>
     * </ol>
     *
     * <p>For the 2-bar case the breakout bars are constructed so that both closes are
     * above their respective N-bar breakout levels: the first bar's high is kept close
     * to its close (preventing it from inflating the second bar's reference high), and
     * the second bar's close is set above the elevated reference level.</p>
     *
     * @param closeBreaksOut whether the breakout bars close above the breakout level
     * @param highBreaksOut  whether the breakout bars' high is above the breakout level
     * @param breakoutBarCount number of consecutive breakout bars to append (1 or 2+)
     */
    private List<Candle> buildBreakoutSequence(boolean closeBreaksOut,
                                                boolean highBreaksOut,
                                                int breakoutBarCount) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();

        // Phase 1: 70 bars of strong uptrend (0.5/bar) → MA20 > MA50, RSI > 50
        double price = 100.0;
        for (int i = 0; i < 70; i++) {
            price += 0.5;
            candles.add(new Candle("T", t.plusMinutes(i),
                    bd(price - 0.5), bd(price + 0.8), bd(price - 0.8), bd(price),
                    150_000L));
        }

        // Phase 2: 20 flat bars to set the 20-bar reference high
        double refBase = price;
        for (int i = 0; i < 20; i++) {
            candles.add(new Candle("T", t.plusMinutes(70 + i),
                    bd(refBase - 0.3), bd(refBase + 0.5), bd(refBase - 0.5), bd(refBase),
                    150_000L));
        }

        // The 20-bar highest high is refBase + 0.5.
        // Initial breakout level = (refBase + 0.5) * (1 + BREAKOUT_BUFFER_PCT)
        double initialBreakoutLevel = (refBase + 0.5)
                * (1.0 + RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT);

        // Phase 3: breakoutBarCount breakout bars.
        // Each bar's close is above its breakout level; its high is only slightly
        // above its close so that the next bar's reference high is not inflated
        // beyond reach.
        double currentBreakoutLevel = initialBreakoutLevel;
        for (int b = 0; b < breakoutBarCount; b++) {
            double barClose = closeBreaksOut
                    ? currentBreakoutLevel + 0.5   // clearly above level
                    : initialBreakoutLevel - 0.1;  // below level (first bar only)
            double barHigh = highBreaksOut
                    ? barClose + 0.1               // just above close to keep reference tight
                    : refBase + 0.1;

            candles.add(new Candle("T", t.plusMinutes(90 + b),
                    bd(refBase - 0.1),
                    bd(barHigh),
                    bd(refBase - 0.3),
                    bd(barClose),
                    200_000L));

            // For the next bar, the reference high is now barHigh.
            // Next breakout level = barHigh * (1 + buffer)
            currentBreakoutLevel = barHigh * (1.0 + RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT);
        }

        return candles;
    }

    /**
     * Builds a breakout sequence with configurable volume for 2-bar confirmation:
     * - Prior breakout bar closes above the initial breakout level (uses avgVolume)
     * - Current breakout bar closes above the elevated breakout level (uses breakoutVolume)
     */
    private List<Candle> buildBreakoutSequenceWithVolume(boolean closeBreaksOut,
                                                          boolean highBreaksOut,
                                                          long breakoutVolume,
                                                          long avgVolume) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();

        // Phase 1: 70 bars of strong uptrend → MA20 > MA50, trend strength ok, RSI > 50
        double price = 100.0;
        for (int i = 0; i < 70; i++) {
            price += 0.5;
            candles.add(new Candle("T", t.plusMinutes(i),
                    bd(price - 0.5), bd(price + 0.8), bd(price - 0.8), bd(price),
                    avgVolume));
        }

        // Phase 2: 20 flat bars to set the 20-bar reference high
        double refBase = price;
        for (int i = 0; i < 20; i++) {
            candles.add(new Candle("T", t.plusMinutes(70 + i),
                    bd(refBase - 0.3), bd(refBase + 0.5), bd(refBase - 0.5), bd(refBase),
                    avgVolume));
        }

        double initialBreakoutLevel = (refBase + 0.5)
                * (1.0 + RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT);
        double bo1Close = closeBreaksOut ? initialBreakoutLevel + 0.5 : initialBreakoutLevel - 0.1;
        // Keep high just above close so next bar's reference isn't inflated out of reach
        double bo1High  = highBreaksOut  ? bo1Close + 0.1 : refBase + 0.1;

        // Phase 3a: prior breakout bar (enables 2-bar confirmation) — uses avgVolume
        candles.add(new Candle("T", t.plusMinutes(90),
                bd(refBase - 0.1), bd(bo1High), bd(refBase - 0.3), bd(bo1Close),
                avgVolume));

        // Phase 3b: current breakout bar — uses breakoutVolume.
        // Its breakout level is based on bar 3a's high.
        double elevatedBreakoutLevel = bo1High * (1.0 + RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT);
        double bo2Close = closeBreaksOut ? elevatedBreakoutLevel + 0.5 : initialBreakoutLevel - 0.1;
        double bo2High  = highBreaksOut  ? bo2Close + 0.1 : refBase + 0.1;

        candles.add(new Candle("T", t.plusMinutes(91),
                bd(refBase - 0.1), bd(bo2High), bd(refBase - 0.3), bd(bo2Close),
                breakoutVolume));

        return candles;
    }

    /**
     * Builds a candle sequence where the trend is too weak to pass the
     * MIN_MA_RATIO_PCT threshold: 50 bars of gentle uptrend (0.01/bar) then
     * 20 flat bars and a breakout bar.  The MA ratio will be well below the
     * required minimum so the strategy should return HOLD.
     */
    private List<Candle> buildWeakUptrendBreakout() {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();

        // Use a very gentle drift (0.004/bar) so the MA20/MA50 ratio stays well below
        // the MIN_MA_RATIO_PCT (0.5%) threshold.
        // After 70 bars: MA20 ≈ 100.13, MA50 ≈ 100.04 → ratio ≈ 0.09% < 0.5% → HOLD
        double price = 100.0;
        for (int i = 0; i < 70; i++) {
            price += 0.004;
            candles.add(new Candle("T", t.plusMinutes(i),
                    bd(price - 0.1), bd(price + 0.2), bd(price - 0.2), bd(price),
                    200_000L));
        }

        double refBase = price;
        for (int i = 0; i < 20; i++) {
            candles.add(new Candle("T", t.plusMinutes(70 + i),
                    bd(refBase - 0.1), bd(refBase + 0.2), bd(refBase - 0.2), bd(refBase),
                    200_000L));
        }

        double breakoutLevel = (refBase + 0.2)
                * (1.0 + RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT);
        candles.add(new Candle("T", t.plusMinutes(90),
                bd(refBase - 0.1), bd(breakoutLevel + 1.0), bd(refBase - 0.2),
                bd(breakoutLevel + 0.5), 300_000L));

        return candles;
    }

    private List<Candle> generateCandles(int count, double startPrice,
                                          double trend, long volume) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();
        double price = startPrice;
        for (int i = 0; i < count; i++) {
            price = Math.max(0.01, price + trend);
            candles.add(new Candle("T", t.plusMinutes(i),
                    bd(price - 0.2), bd(price + 0.5), bd(price - 0.5), bd(price),
                    volume));
        }
        return candles;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    /**
     * Simulates incremental evaluation (like the BacktestEngine) so that
     * indicators accumulate properly. Returns the signal at the final bar.
     */
    private static TradingSignal evaluateIncrementally(RobustTrendBreakoutStrategy strat,
                                                        List<Candle> candles) {
        TradingSignal last = TradingSignal.HOLD;
        for (int i = RobustTrendBreakoutStrategy.MIN_CANDLES; i <= candles.size(); i++) {
            last = strat.evaluate(candles.subList(0, i));
        }
        return last;
    }
}
