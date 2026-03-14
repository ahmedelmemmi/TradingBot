package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Simplified Breakout Strategy — trades actual market behaviour, not theoretical ideals.
 *
 * <p>Designed to work with the mock market data by using realistic, achievable thresholds
 * instead of textbook consolidation + volume dry-up patterns that mock data doesn't produce.</p>
 *
 * <p>Entry conditions (ALL must be met):</p>
 * <ol>
 *   <li><b>Regime:</b> {@link MarketRegime#STRONG_UPTREND} only.</li>
 *   <li><b>Volatility spike:</b> ATR(14) &gt; {@value #VOLATILITY_THRESHOLD_PCT}% of price.</li>
 *   <li><b>Volume above average:</b> Current bar volume &gt; {@value #MIN_VOLUME_RATIO_PCT}%
 *       of the 20-bar average (no dry-up requirement).</li>
 *   <li><b>Price breakout:</b> Close &gt; highest high of the prior {@value #BREAKOUT_PERIOD}
 *       bars + 0.05% buffer.</li>
 *   <li><b>RSI positive momentum:</b> RSI(14) &gt; 50.</li>
 * </ol>
 */
@Service
public class SimplifiedBreakoutStrategy implements Strategy {

    /** ATR as a percentage of price must exceed this level (1.2%). */
    public static final double VOLATILITY_THRESHOLD_PCT = 0.012;

    /** Current volume must be at least this fraction of the 20-bar average (70%). */
    public static final double MIN_VOLUME_RATIO_PCT = 0.70;

    /** Look-back period for the breakout high (5 bars). */
    public static final int BREAKOUT_PERIOD = 5;

    /** Tiny buffer above the 5-bar high to confirm the breakout (0.05%). */
    public static final double BREAKOUT_BUFFER = 1.0005;

    /** RSI must be above this level to confirm positive momentum. */
    public static final double RSI_MIN = 50.0;

    /** Minimum number of candles required before any signal is produced. */
    public static final int MIN_CANDLES = 60;

    /** Look-back window for the average volume calculation. */
    private static final int VOLUME_AVG_PERIOD = 20;

    private final RsiCalculator rsiCalculator;
    private final MarketRegimeService regimeService;
    private final AtrCalculator atrCalculator = new AtrCalculator();

    public SimplifiedBreakoutStrategy(RsiCalculator rsiCalculator,
                                       MarketRegimeService regimeService) {
        this.rsiCalculator = rsiCalculator;
        this.regimeService  = regimeService;
    }

    @Override
    public String getName() {
        return "SimplifiedBreakoutStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MIN_CANDLES) {
            return TradingSignal.HOLD;
        }

        Candle current = candles.get(candles.size() - 1);
        BigDecimal price = current.getClose();

        // ── Condition 1: REGIME (STRONG_UPTREND only) ─────────────────────────
        MarketRegime regime = regimeService.detect(candles);
        if (regime != MarketRegime.STRONG_UPTREND) {
            return TradingSignal.HOLD;
        }
        System.out.println("[SimplifiedBreakout] ✓ Regime: STRONG_UPTREND");

        // ── Condition 2: VOLATILITY SPIKE (ATR > 1.2% of price) ──────────────
        BigDecimal atr = atrCalculator.calculate(candles, 14);
        BigDecimal atrPercent = price.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : atr.divide(price, 6, RoundingMode.HALF_UP);

        System.out.println("[SimplifiedBreakout] ATR%: " + fmt(atrPercent)
                + " (threshold: " + VOLATILITY_THRESHOLD_PCT + ")");

        if (atrPercent.compareTo(BigDecimal.valueOf(VOLATILITY_THRESHOLD_PCT)) < 0) {
            System.out.println("  → REJECT: ATR too low");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Volatility sufficient");

        // ── Condition 3: VOLUME ABOVE AVERAGE (≥70% of 20-bar avg) ───────────
        long avgVolume = getAverageVolume(candles, VOLUME_AVG_PERIOD);
        long currentVolume = current.getVolume();

        BigDecimal volumeRatio = (avgVolume == 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(currentVolume)
                        .divide(BigDecimal.valueOf(avgVolume), 6, RoundingMode.HALF_UP);

        System.out.println("[SimplifiedBreakout] Volume ratio: " + fmt(volumeRatio)
                + "x (min: " + MIN_VOLUME_RATIO_PCT + "x)");

        if (volumeRatio.compareTo(BigDecimal.valueOf(MIN_VOLUME_RATIO_PCT)) < 0) {
            System.out.println("  → REJECT: Volume too low");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Volume above average");

        // ── Condition 4: PRICE BREAKOUT (above 5-bar high + 0.05% buffer) ─────
        BigDecimal highest5 = getHighestHigh(candles, BREAKOUT_PERIOD);
        BigDecimal breakoutLevel = highest5.multiply(BigDecimal.valueOf(BREAKOUT_BUFFER));

        System.out.println("[SimplifiedBreakout] Price: " + fmt(price)
                + " vs breakout: " + fmt(breakoutLevel));

        if (price.compareTo(breakoutLevel) <= 0) {
            System.out.println("  → REJECT: No price breakout");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Price breakout confirmed");

        // ── Condition 5: RSI POSITIVE MOMENTUM (>50) ──────────────────────────
        BigDecimal rsi = rsiCalculator.calculate(candles);

        System.out.println("[SimplifiedBreakout] RSI: " + fmt(rsi)
                + " (min: " + RSI_MIN + ")");

        if (rsi.compareTo(BigDecimal.valueOf(RSI_MIN)) < 0) {
            System.out.println("  → REJECT: RSI below 50");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: RSI positive momentum");

        // ── ✅ ALL CONDITIONS MET ──────────────────────────────────────────────
        System.out.println("[SimplifiedBreakout] ✅✅✅ BUY SIGNAL ✅✅✅");
        return TradingSignal.BUY;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the highest high over the last {@code period} bars,
     * excluding the current (last) bar.
     */
    private BigDecimal getHighestHigh(List<Candle> candles, int period) {
        BigDecimal max = BigDecimal.ZERO;
        int end   = candles.size() - 1; // exclude current bar
        int start = Math.max(0, end - period);
        for (int i = start; i < end; i++) {
            if (candles.get(i).getHigh().compareTo(max) > 0) {
                max = candles.get(i).getHigh();
            }
        }
        return max;
    }

    /**
     * Returns the average volume over the last {@code period} bars,
     * excluding the current (last) bar.
     */
    private long getAverageVolume(List<Candle> candles, int period) {
        int end   = candles.size() - 1; // exclude current bar
        int start = Math.max(0, end - period);
        long sum  = 0;
        int count = 0;
        for (int i = start; i < end; i++) {
            sum += candles.get(i).getVolume();
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private String fmt(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
