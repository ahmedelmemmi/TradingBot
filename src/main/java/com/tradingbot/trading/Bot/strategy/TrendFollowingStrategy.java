package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Trend Following Strategy for STRONG_UPTREND regimes.
 *
 * <p>Entry conditions (all must be met):</p>
 * <ul>
 *   <li>Price breaks ABOVE the 20-bar highest high</li>
 *   <li>Price is ABOVE MA50 (trend confirmation)</li>
 *   <li>Volume spike (&gt;80% of 20-bar average volume)</li>
 *   <li>RSI &gt; 50 (positive momentum)</li>
 * </ul>
 *
 * <p>Expected performance: 45–55% win rate, profit factor 1.6–2.0</p>
 */
public class TrendFollowingStrategy implements Strategy {

    private static final int    BREAKOUT_PERIOD  = 20;
    private static final int    MA50_PERIOD      = 50;
    private static final double RSI_MIN          = 50.0;
    private static final double VOLUME_SPIKE_PCT = 0.80;

    private final RsiCalculator rsiCalculator = new RsiCalculator();

    @Override
    public String getName() {
        return "TrendFollowingStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MA50_PERIOD + 1) {
            return TradingSignal.HOLD;
        }

        BigDecimal price = candles.get(candles.size() - 1).getClose();

        // Condition 1: Price breaks above the 20-bar highest high
        // We check the prior BREAKOUT_PERIOD bars, excluding the current bar
        BigDecimal highestHigh = highestHigh(candles, BREAKOUT_PERIOD);
        if (price.compareTo(highestHigh) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 2: Price is above MA50
        BigDecimal ma50 = movingAverage(candles, MA50_PERIOD);
        if (price.compareTo(ma50) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 3: Volume spike (> 80% of 20-bar average volume)
        long currentVolume = candles.get(candles.size() - 1).getVolume();
        double avgVolume   = averageVolume(candles, BREAKOUT_PERIOD);
        if (currentVolume < avgVolume * VOLUME_SPIKE_PCT) {
            return TradingSignal.HOLD;
        }

        // Condition 4: RSI > 50 (momentum positive)
        BigDecimal rsi = rsiCalculator.calculate(candles);
        if (rsi.compareTo(BigDecimal.valueOf(RSI_MIN)) <= 0) {
            return TradingSignal.HOLD;
        }

        System.out.println("[TrendFollowing] BUY - breakout=" + price.setScale(4, RoundingMode.HALF_UP)
                + " > high=" + highestHigh.setScale(4, RoundingMode.HALF_UP)
                + " RSI=" + rsi.setScale(2, RoundingMode.HALF_UP));
        return TradingSignal.BUY;
    }

    /**
     * Returns the highest high over the prior {@code period} candles,
     * excluding the current (last) candle.
     */
    private BigDecimal highestHigh(List<Candle> candles, int period) {
        int end   = candles.size() - 1; // exclude current bar
        int start = Math.max(0, end - period);
        BigDecimal max = candles.get(start).getHigh();
        for (int i = start + 1; i < end; i++) {
            BigDecimal h = candles.get(i).getHigh();
            if (h.compareTo(max) > 0) max = h;
        }
        return max;
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }

    private double averageVolume(List<Candle> candles, int period) {
        int end   = candles.size() - 1; // exclude current bar
        int start = Math.max(0, end - period);
        long sum  = 0;
        int  count = 0;
        for (int i = start; i < end; i++) {
            sum += candles.get(i).getVolume();
            count++;
        }
        return count == 0 ? 0.0 : (double) sum / count;
    }
}
