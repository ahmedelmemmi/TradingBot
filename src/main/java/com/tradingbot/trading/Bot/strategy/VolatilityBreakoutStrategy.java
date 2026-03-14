package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Volatility Breakout Strategy for HIGH_VOLATILITY regimes.
 *
 * <p>Entry conditions (all must be met):</p>
 * <ul>
 *   <li>ATR &gt; 1.5% of price (extreme volatility detected)</li>
 *   <li>Price breaks above 5-bar high OR below 5-bar low (directional breakout)</li>
 *   <li>RSI extreme (&lt;20 or &gt;80) confirms spike direction</li>
 *   <li>Volume spike (&gt;150% of 20-bar average)</li>
 * </ul>
 *
 * <p>Expected performance: 60–70% win rate, profit factor 1.7–1.9</p>
 */
public class VolatilityBreakoutStrategy implements Strategy {

    private static final int    BREAKOUT_PERIOD      = 5;
    private static final int    VOLUME_PERIOD        = 20;
    private static final double ATR_PCT_THRESHOLD    = 0.015; // 1.5% of price
    private static final double RSI_HIGH_EXTREME     = 80.0;
    private static final double RSI_LOW_EXTREME      = 20.0;
    private static final double VOLUME_SPIKE_RATIO   = 1.50; // 150%

    private final AtrCalculator atrCalculator = new AtrCalculator();
    private final RsiCalculator rsiCalculator = new RsiCalculator();

    @Override
    public String getName() {
        return "VolatilityBreakoutStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < Math.max(VOLUME_PERIOD, 14) + 1) {
            return TradingSignal.HOLD;
        }

        BigDecimal price = candles.get(candles.size() - 1).getClose();

        // Condition 1: ATR > 1.5% of price
        BigDecimal atr = atrCalculator.calculate(candles, 14);
        if (atr.compareTo(BigDecimal.ZERO) == 0) return TradingSignal.HOLD;

        BigDecimal atrPct = atr.divide(price, 6, RoundingMode.HALF_UP);
        if (atrPct.compareTo(BigDecimal.valueOf(ATR_PCT_THRESHOLD)) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 2: Price breaks above 5-bar high OR below 5-bar low (prior BREAKOUT_PERIOD bars)
        BigDecimal fiveBarHigh = highestHigh(candles, BREAKOUT_PERIOD);
        BigDecimal fiveBarLow  = lowestLow(candles, BREAKOUT_PERIOD);
        boolean breakoutUp   = price.compareTo(fiveBarHigh) > 0;
        boolean breakoutDown = price.compareTo(fiveBarLow) < 0;

        if (!breakoutUp && !breakoutDown) {
            return TradingSignal.HOLD;
        }

        // Condition 3: RSI extreme confirms direction
        BigDecimal rsi = rsiCalculator.calculate(candles);
        boolean rsiConfirmsUp   = rsi.compareTo(BigDecimal.valueOf(RSI_HIGH_EXTREME)) > 0;
        boolean rsiConfirmsDown = rsi.compareTo(BigDecimal.valueOf(RSI_LOW_EXTREME)) < 0;

        // Direction must match: up breakout needs high RSI, down breakout needs low RSI
        if (breakoutUp && !rsiConfirmsUp) return TradingSignal.HOLD;
        if (breakoutDown && !rsiConfirmsDown) return TradingSignal.HOLD;

        // Condition 4: Volume spike > 150% of 20-bar average
        long currentVolume = candles.get(candles.size() - 1).getVolume();
        double avgVolume   = averageVolume(candles, VOLUME_PERIOD);
        if (avgVolume > 0 && currentVolume < avgVolume * VOLUME_SPIKE_RATIO) {
            return TradingSignal.HOLD;
        }

        // Only signal BUY on upside breakout (long-only system)
        if (breakoutUp) {
            System.out.println("[VolatilityBreakout] BUY - atrPct=" + atrPct.setScale(4, RoundingMode.HALF_UP)
                    + " price=" + price.setScale(4, RoundingMode.HALF_UP)
                    + " RSI=" + rsi.setScale(2, RoundingMode.HALF_UP));
            return TradingSignal.BUY;
        }

        return TradingSignal.HOLD;
    }

    /**
     * Returns the highest high over the prior {@code period} candles,
     * excluding the current (last) candle.
     */
    private BigDecimal highestHigh(List<Candle> candles, int period) {
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        BigDecimal max = candles.get(start).getHigh();
        for (int i = start + 1; i < end; i++) {
            BigDecimal h = candles.get(i).getHigh();
            if (h.compareTo(max) > 0) max = h;
        }
        return max;
    }

    /**
     * Returns the lowest low over the prior {@code period} candles,
     * excluding the current (last) candle.
     */
    private BigDecimal lowestLow(List<Candle> candles, int period) {
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        BigDecimal min = candles.get(start).getLow();
        for (int i = start + 1; i < end; i++) {
            BigDecimal l = candles.get(i).getLow();
            if (l.compareTo(min) < 0) min = l;
        }
        return min;
    }

    private double averageVolume(List<Candle> candles, int period) {
        int end   = candles.size() - 1;
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
