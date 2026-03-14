package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Filters trades based on market volatility and volume conditions.
 *
 * <p>Two key checks are provided:</p>
 * <ol>
 *   <li><b>isVolatilityAcceptable</b>: rejects entries when recent ATR
 *       significantly exceeds historical ATR (volatility is spiking) or when
 *       ATR exceeds 1.5% of current price (market is too noisy).</li>
 *   <li><b>isVolumeConfirming</b>: confirms volume is in the 80–120% range of
 *       the 20-bar average — showing measured conviction without a breakout
 *       spike that would invalidate a pullback entry.</li>
 * </ol>
 */
@Service
public class VolatilityFilterService {

    /** Recent ATR period (bars) */
    private static final int RECENT_ATR_PERIOD   = 14;
    /** Historical ATR period (bars) used for comparison */
    private static final int HIST_ATR_PERIOD     = 20;
    /** Threshold ratio: reject if recentATR > historicalATR × this factor */
    private static final BigDecimal ATR_SPIKE_MULTIPLIER = BigDecimal.valueOf(1.2);
    /** Maximum ATR as a fraction of price before rejecting */
    private static final BigDecimal MAX_ATR_PCT  = BigDecimal.valueOf(0.015); // 1.5%

    /** Volume ratio lower bound for pullback confirmation */
    private static final BigDecimal VOLUME_LOWER = BigDecimal.valueOf(0.80);
    /** Volume ratio upper bound (breakout spike rejection) */
    private static final BigDecimal VOLUME_UPPER = BigDecimal.valueOf(1.20);
    /** Volume average period (bars) */
    private static final int VOLUME_AVG_PERIOD   = 20;

    private final AtrCalculator atrCalculator = new AtrCalculator();

    /**
     * Returns true when market volatility is within an acceptable range for
     * strategy entries.
     *
     * @param candles full candle list (at least 35 bars recommended)
     * @return true if volatility is acceptable
     */
    public boolean isVolatilityAcceptable(List<Candle> candles) {

        if (candles.size() < HIST_ATR_PERIOD + RECENT_ATR_PERIOD + 1) {
            System.out.println("[VolatilityFilter] Insufficient candles for volatility check");
            return false;
        }

        BigDecimal recentAtr = atrCalculator.calculate(candles, RECENT_ATR_PERIOD);
        BigDecimal historicalAtr = atrCalculator.calculate(candles, HIST_ATR_PERIOD);
        BigDecimal currentPrice = candles.get(candles.size() - 1).getClose();

        // Check 1: recent ATR spike (> 1.2× historical)
        if (historicalAtr.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = recentAtr.divide(historicalAtr, 6, RoundingMode.HALF_UP);
            if (ratio.compareTo(ATR_SPIKE_MULTIPLIER) > 0) {
                System.out.println("[VolatilityFilter] REJECTED - ATR spiking ratio="
                        + ratio.setScale(3, RoundingMode.HALF_UP));
                return false;
            }
        }

        // Check 2: absolute ATR > 1.5% of price
        if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal atrPct = recentAtr.divide(currentPrice, 6, RoundingMode.HALF_UP);
            if (atrPct.compareTo(MAX_ATR_PCT) > 0) {
                System.out.println("[VolatilityFilter] REJECTED - ATR% too high="
                        + atrPct.setScale(4, RoundingMode.HALF_UP));
                return false;
            }
        }

        System.out.println("[VolatilityFilter] ACCEPTED recentATR=" + recentAtr.setScale(4, RoundingMode.HALF_UP)
                + " histATR=" + historicalAtr.setScale(4, RoundingMode.HALF_UP));
        return true;
    }

    /**
     * Returns true when the current bar's volume is within 80–120% of the
     * 20-bar average volume.  This confirms a measured pullback rather than
     * a panic sell or breakout spike.
     *
     * @param candles full candle list (at least 21 bars required)
     * @return true if volume is confirming
     */
    public boolean isVolumeConfirming(List<Candle> candles) {

        if (candles.size() < VOLUME_AVG_PERIOD + 1) {
            System.out.println("[VolatilityFilter] Insufficient candles for volume check");
            return false;
        }

        long currentVolume = candles.get(candles.size() - 1).getVolume();

        // Calculate average volume over the last VOLUME_AVG_PERIOD bars (excluding current)
        long volumeSum = 0;
        int start = candles.size() - 1 - VOLUME_AVG_PERIOD;
        for (int i = start; i < candles.size() - 1; i++) {
            volumeSum += candles.get(i).getVolume();
        }

        if (volumeSum == 0) {
            System.out.println("[VolatilityFilter] Volume data missing - skipping check");
            return true; // gracefully pass if no volume data
        }

        BigDecimal avgVolume = BigDecimal.valueOf(volumeSum)
                .divide(BigDecimal.valueOf(VOLUME_AVG_PERIOD), 6, RoundingMode.HALF_UP);

        BigDecimal volumeRatio = BigDecimal.valueOf(currentVolume)
                .divide(avgVolume, 6, RoundingMode.HALF_UP);

        boolean confirming = volumeRatio.compareTo(VOLUME_LOWER) >= 0
                && volumeRatio.compareTo(VOLUME_UPPER) <= 0;

        System.out.println("[VolatilityFilter] Volume ratio=" + volumeRatio.setScale(3, RoundingMode.HALF_UP)
                + (confirming ? " CONFIRMING" : " NOT confirming"));

        return confirming;
    }
}
