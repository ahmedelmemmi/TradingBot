package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimplifiedBreakoutStrategy}.
 *
 * <p>Key focus: condition 4 (price breakout) must be confirmed by the bar's
 * <em>close</em> price, not just the intraday high. A bar that spikes above
 * the breakout level intraday but closes below it is a false breakout.</p>
 */
class SimplifiedBreakoutStrategyTest {

    private SimplifiedBreakoutStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SimplifiedBreakoutStrategy(
                new RsiCalculator(),
                new MarketRegimeService()
        );
    }

    // ── Condition 0: Minimum candle count ─────────────────────────────────────

    @Test
    void returnsHoldWhenInsufficientCandles() {
        List<Candle> candles = generateUptrendCandles(40, 100.0, 0.5, 100_000);
        assertEquals(TradingSignal.HOLD, strategy.evaluate(candles),
                "Should return HOLD when fewer than 60 candles are provided");
    }

    // ── Condition 4: Price breakout uses close, not high ──────────────────────

    /**
     * Core regression test: the bar's high exceeds the breakout level but the
     * close is below it. Before the fix this would have returned BUY; after
     * the fix it must return HOLD (false breakout, not confirmed by the close).
     */
    @Test
    void returnsHoldWhenHighAboveBreakoutButCloseBelowIt() {
        List<Candle> candles = buildBreakoutSequence(
                /* closeBreaksOut */ false,  // close stays below breakout level
                /* highBreaksOut  */ true    // only the intraday high spikes above
        );
        // Even with regime pre-warmed, the close does not confirm the breakout.
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.HOLD, last,
                "High-only breakout (close below level) must be rejected as false breakout");
    }

    @Test
    void returnsBuyWhenCloseConfirmsBreakout() {
        List<Candle> candles = buildBreakoutSequence(
                /* closeBreaksOut */ true,   // close is above breakout level
                /* highBreaksOut  */ true    // high is also above (expected)
        );
        // Evaluate incrementally so the regime service accumulates enough confirmations.
        TradingSignal last = evaluateIncrementally(strategy, candles);
        assertEquals(TradingSignal.BUY, last,
                "Close above breakout level should produce a BUY signal");
    }

    // ── Strategy name ─────────────────────────────────────────────────────────

    @Test
    void nameIsCorrect() {
        assertEquals("SimplifiedBreakoutStrategy", strategy.getName());
    }

    // ── Helper: build a candle sequence ───────────────────────────────────────

    /**
     * Builds a candle sequence that satisfies all conditions except the one under
     * test:
     * <ol>
     *   <li>60 strong-uptrend bars (establishes STRONG_UPTREND regime and ATR).</li>
     *   <li>5 normal bars (establishes the 5-bar high reference).</li>
     *   <li>1 breakout bar — high/close controlled by the parameters.</li>
     * </ol>
     *
     * @param closeBreaksOut when {@code true} the final bar's close is above the
     *                       breakout level; when {@code false} it stays below
     * @param highBreaksOut  when {@code true} the final bar's high is above the
     *                       breakout level (always {@code true} in practice; only
     *                       {@code false} is used to verify the strategy handles
     *                       partial conditions correctly)
     */
    private List<Candle> buildBreakoutSequence(boolean closeBreaksOut,
                                               boolean highBreaksOut) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();

        // Phase 1: 60-bar strong uptrend — builds STRONG_UPTREND regime
        // Use a wide high-low spread so ATR(14) / price exceeds the 0.9% threshold.
        double price = 100.0;
        for (int i = 0; i < 60; i++) {
            price += 0.5;
            candles.add(new Candle("T", t.plusMinutes(i),
                    bd(price - 0.5), bd(price + 0.8), bd(price - 0.8), bd(price),
                    150_000L));
        }

        // Phase 2: 5 bars forming the reference high (highest5).
        // Use the same wide range as the uptrend bars so ATR stays above the 0.9% threshold.
        double refBase = price;
        for (int i = 0; i < 5; i++) {
            candles.add(new Candle("T", t.plusMinutes(60 + i),
                    bd(refBase - 0.5), bd(refBase + 0.8), bd(refBase - 0.8), bd(refBase),
                    150_000L));
        }

        // The 5-bar highest high is refBase + 0.8.
        // Breakout level = (refBase + 0.8) * BREAKOUT_BUFFER
        double breakoutLevel = (refBase + 0.8) * SimplifiedBreakoutStrategy.BREAKOUT_BUFFER;

        // Phase 3: breakout bar
        double closePrice = closeBreaksOut
                ? breakoutLevel + 0.5   // close clearly above breakout level
                : breakoutLevel - 0.1;  // close just below breakout level (false breakout)

        double highPrice = highBreaksOut
                ? breakoutLevel + 1.0   // high is well above the breakout level
                : refBase + 0.05;       // high does not even reach the level

        candles.add(new Candle("T", t.plusMinutes(65),
                bd(refBase - 0.1),
                bd(highPrice),
                bd(refBase - 0.3),
                bd(closePrice),
                300_000L)); // volume spike above 50% of average

        return candles;
    }

    private List<Candle> generateUptrendCandles(int count, double startPrice,
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
     * Simulates the BacktestEngine loop: evaluates the strategy on each growing
     * prefix of {@code candles} so that the regime service accumulates enough
     * consecutive STRONG_UPTREND detections to confirm the regime.  Returns the
     * signal produced on the very last bar.
     */
    private static TradingSignal evaluateIncrementally(SimplifiedBreakoutStrategy strat,
                                                        List<Candle> candles) {
        TradingSignal last = TradingSignal.HOLD;
        for (int i = SimplifiedBreakoutStrategy.MIN_CANDLES; i <= candles.size(); i++) {
            last = strat.evaluate(candles.subList(0, i));
        }
        return last;
    }
}
