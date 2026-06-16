package com.tradingbot.trading.Bot.analysis;

import com.tradingbot.trading.Bot.domain.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Detects candlestick and price-action patterns from a candle series.
 *
 * <p>Patterns detected:</p>
 * <ul>
 *   <li>BULLISH_ENGULFING / BEARISH_ENGULFING</li>
 *   <li>PIN_BAR_BULLISH / PIN_BAR_BEARISH</li>
 *   <li>DOJI</li>
 *   <li>NONE</li>
 * </ul>
 */
public final class PriceActionDetector {

    private static final BigDecimal DOJI_RATIO   = new BigDecimal("0.10");
    private static final BigDecimal PIN_RATIO     = new BigDecimal("0.33");

    private PriceActionDetector() {}

    public static String detectPattern(List<Candle> candles) {
        if (candles.size() < 2) return "NONE";

        Candle curr = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);

        BigDecimal range = curr.getHigh().subtract(curr.getLow());
        if (range.compareTo(BigDecimal.ZERO) == 0) return "NONE";

        BigDecimal body = curr.getClose().subtract(curr.getOpen()).abs();

        // ── DOJI ──────────────────────────────────────────────────────────────
        BigDecimal bodyRatio = body.divide(range, 6, RoundingMode.HALF_UP);
        if (bodyRatio.compareTo(DOJI_RATIO) <= 0) {
            return "DOJI";
        }

        // ── PIN BAR ───────────────────────────────────────────────────────────
        BigDecimal upperWick = curr.getHigh().subtract(curr.getClose().max(curr.getOpen()));
        BigDecimal lowerWick = curr.getClose().min(curr.getOpen()).subtract(curr.getLow());

        if (lowerWick.divide(range, 6, RoundingMode.HALF_UP).compareTo(PIN_RATIO) >= 0
                && bodyRatio.compareTo(new BigDecimal("0.30")) <= 0) {
            return "PIN_BAR_BULLISH";
        }
        if (upperWick.divide(range, 6, RoundingMode.HALF_UP).compareTo(PIN_RATIO) >= 0
                && bodyRatio.compareTo(new BigDecimal("0.30")) <= 0) {
            return "PIN_BAR_BEARISH";
        }

        // ── ENGULFING ─────────────────────────────────────────────────────────
        boolean prevBearish = prev.getClose().compareTo(prev.getOpen()) < 0;
        boolean currBullish = curr.getClose().compareTo(curr.getOpen()) > 0;
        if (prevBearish && currBullish
                && curr.getOpen().compareTo(prev.getClose()) <= 0
                && curr.getClose().compareTo(prev.getOpen()) >= 0) {
            return "BULLISH_ENGULFING";
        }

        boolean prevBullish = prev.getClose().compareTo(prev.getOpen()) > 0;
        boolean currBearish = curr.getClose().compareTo(curr.getOpen()) < 0;
        if (prevBullish && currBearish
                && curr.getOpen().compareTo(prev.getClose()) >= 0
                && curr.getClose().compareTo(prev.getOpen()) <= 0) {
            return "BEARISH_ENGULFING";
        }

        return "NONE";
    }

    /**
     * Returns {@code true} when there is bullish candle confirmation on the last bar.
     * Used as the final gate in the BUY rule engine.
     */
    public static boolean isBullishConfirmation(String pattern, Candle lastCandle) {
        if ("BULLISH_ENGULFING".equals(pattern) || "PIN_BAR_BULLISH".equals(pattern)) {
            return true;
        }
        // Plain bullish close (close > open)
        return lastCandle.getClose().compareTo(lastCandle.getOpen()) > 0;
    }

    /** Returns {@code true} when there is bearish candle confirmation on the last bar. */
    public static boolean isBearishConfirmation(String pattern, Candle lastCandle) {
        if ("BEARISH_ENGULFING".equals(pattern) || "PIN_BAR_BEARISH".equals(pattern)) {
            return true;
        }
        return lastCandle.getClose().compareTo(lastCandle.getOpen()) < 0;
    }
}
