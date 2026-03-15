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
 * <p>Verifies the five conditions:</p>
 * <ol>
 *   <li>MA20/MA50 slope ≥ 1% (established uptrend, not just marginal crossover)</li>
 *   <li>ATR(14) ≥ 0.5% of price (sufficient volatility for TP to be reachable)</li>
 *   <li>Close &gt; 20-bar highest high + 0.1% buffer (breakout confirmation)</li>
 *   <li>Current volume ≥ 1.2× 20-bar average (volume spike confirmation)</li>
 *   <li>RSI(14) &gt; 50 (momentum confirmation)</li>
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
        // 59 candles — one below MIN_CANDLES (boundary check)
        List<Candle> candles = generateCandles(59, 100.0, 0.5, 100_000);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles),
                "Should return HOLD at exactly 59 candles (MIN_CANDLES is 60)");
    }

    // ── Condition 1: uptrend slope ────────────────────────────────────────────

    @Test
    void returnsHoldWhenNotInUptrend() {
        // Flat market: MA20 ≈ MA50 ≈ same price, slope = 0
        List<Candle> candles = generateCandles(80, 100.0, 0.0, 100_000);
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "Flat market should produce HOLD (no uptrend)");
    }

    @Test
    void returnsHoldWhenUptrendSlopeTooWeak() {
        // Very gentle uptrend (0.05/bar) produces MA slope well below 1%:
        // MA20 ≈ 103.5, MA50 ≈ 103.15 → slope ≈ 0.34% < 1%
        List<Candle> phase1 = generateCandles(70, 100.0, 0.05, 150_000L);
        List<Candle> phase2 = generateCandles(20, phase1.get(phase1.size()-1).getClose().doubleValue(),
                0.0, 150_000L);
        // Breakout bar: close above the 20-bar high
        double refBase = phase2.get(phase2.size()-1).getClose().doubleValue();
        double nBarHigh = phase2.stream()
                .mapToDouble(c -> c.getHigh().doubleValue())
                .max().orElse(refBase + 0.5);
        double breakoutClose = nBarHigh * (1.0 + RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT) + 0.3;

        List<Candle> candles = new ArrayList<>(phase1);
        candles.addAll(phase2);
        candles.add(new Candle("T", LocalDateTime.now().plusDays(91),
                bd(refBase - 0.1), bd(breakoutClose + 0.5), bd(refBase - 0.5), bd(breakoutClose),
                200_000L)); // volume spike present

        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "Weak MA slope (< 1%) should produce HOLD even with a valid breakout");
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
        List<Candle> candles = buildBreakoutSequence(true, true);
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.BUY, last,
                "Close above breakout level in an uptrend should produce BUY");
    }

    // ── Condition 4: volume spike confirmation ────────────────────────────────

    @Test
    void returnsHoldWhenBreakoutHasLowVolume() {
        // Full breakout sequence but the breakout bar has volume BELOW 1.2× average
        List<Candle> candles = buildBreakoutSequence(true, true);
        // Replace the last candle with an identical one but low volume (below threshold)
        Candle lastBar = candles.remove(candles.size() - 1);
        long lowVolume = 100_000L; // well below 1.2 × 150_000 = 180_000
        candles.add(new Candle("T", LocalDateTime.now(),
                lastBar.getOpen(), lastBar.getHigh(), lastBar.getLow(), lastBar.getClose(),
                lowVolume));

        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "Breakout on low volume (< 1.2× average) should produce HOLD");
    }

    // ── Strategy name ─────────────────────────────────────────────────────────

    @Test
    void nameIsCorrect() {
        assertEquals("RobustTrendBreakoutStrategy", strategy.getName());
    }

    // ── Helper: build a breakout candle sequence ──────────────────────────────

    /**
     * Builds a candle sequence that satisfies all five strategy conditions:
     * <ol>
     *   <li>70 bars of strong uptrend (0.5/bar) → MA slope ≥ 1%, ATR ≥ 0.5%.</li>
     *   <li>20 flat consolidation bars → sets the 20-bar reference high.</li>
     *   <li>1 breakout bar with close / high controlled by parameters and
     *       a volume spike (200k vs 150k avg = 1.33×) to pass the volume filter.</li>
     * </ol>
     */
    private List<Candle> buildBreakoutSequence(boolean closeBreaksOut,
                                                boolean highBreaksOut) {
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
        // Breakout level = (refBase + 0.5) * (1 + BREAKOUT_BUFFER_PCT)
        double breakoutLevel = (refBase + 0.5)
                * (1.0 + RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT);

        double closePrice = closeBreaksOut
                ? breakoutLevel + 0.5   // close clearly above breakout level
                : breakoutLevel - 0.1;  // close just below breakout level

        double highPrice = highBreaksOut
                ? breakoutLevel + 1.0   // high well above breakout level
                : refBase + 0.1;        // high does not reach the level

        // Phase 3: breakout bar
        candles.add(new Candle("T", t.plusMinutes(90),
                bd(refBase - 0.1),
                bd(highPrice),
                bd(refBase - 0.3),
                bd(closePrice),
                200_000L));

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
