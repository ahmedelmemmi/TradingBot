package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Perfect Breakout Strategy — Quality Over Quantity (80% Win Rate Target).
 *
 * <p>This strategy only enters when ALL six conditions are simultaneously met:</p>
 * <ol>
 *   <li><b>Consolidation:</b> Price range of the prior {@value #CONSOLIDATION_PERIOD} bars
 *       is ≤ {@value #MAX_CONSOLIDATION_RANGE_PCT}% (tight coil).</li>
 *   <li><b>Volume dry-up:</b> The {@value #VOLUME_DRY_PERIOD} bars before the breakout bar
 *       each have volume below 60% of the 20-bar average (compression).</li>
 *   <li><b>Volume spike:</b> Current bar volume ≥ 1.8× the 20-bar average (explosion).</li>
 *   <li><b>Price breakout:</b> Current close is &gt; consolidation high × 1.002 (confirmed
 *       breakout with 0.2% buffer).</li>
 *   <li><b>RSI momentum flip:</b> RSI of the prior bar was &lt; 50 and current RSI ≥ 55
 *       (momentum shift).</li>
 *   <li><b>Regime:</b> Market regime is {@link MarketRegime#STRONG_UPTREND}.</li>
 * </ol>
 *
 * <p>Exit levels (computed by the backtest engine):</p>
 * <ul>
 *   <li>Stop Loss = consolidation low − 0.5 × ATR14</li>
 *   <li>Take Profit = entry + 3 × consolidation range</li>
 * </ul>
 *
 * <p>Expected performance: 75–85% win rate with 8–15 trades per 1000 candles.</p>
 */
@Service
public class PerfectBreakoutStrategy implements Strategy {

    /** Number of bars forming the consolidation zone (excludes the current/breakout bar). */
    static final int CONSOLIDATION_PERIOD = 8;

    /** Maximum allowed high-to-low range (as fraction of low) for a valid consolidation. */
    static final double MAX_CONSOLIDATION_RANGE_PCT = 0.015; // 1.5%

    /** Number of bars immediately before the breakout bar that must show low volume. */
    static final int VOLUME_DRY_PERIOD = 3;

    /** Volume threshold (fraction of 20-bar average) below which a bar is "dry". */
    static final double VOLUME_DRY_THRESHOLD = 0.6; // 60%

    /** Minimum volume ratio (current / 20-bar avg) required to confirm a spike. */
    static final double VOLUME_SPIKE_MULTIPLE = 1.8; // 80% above average

    /** RSI of the bar BEFORE the breakout must be below this level. */
    static final double RSI_PREV_MAX = 50.0;

    /** RSI of the breakout bar must be at or above this level. */
    static final double RSI_BREAKOUT_LEVEL = 55.0;

    /** Breakout buffer above consolidation high (0.2%). */
    static final double BREAKOUT_BUFFER = 1.002;

    /** Minimum number of candles required before any signal is produced. */
    static final int MIN_CANDLES = 70;

    /** Lookback window for average volume calculation. */
    private static final int VOLUME_AVG_PERIOD = 20;

    private final RsiCalculator rsiCalculator;
    private final MarketRegimeService regimeService;

    public PerfectBreakoutStrategy(RsiCalculator rsiCalculator,
                                    MarketRegimeService regimeService) {
        this.rsiCalculator = rsiCalculator;
        this.regimeService  = regimeService;
    }

    @Override
    public String getName() {
        return "PerfectBreakoutStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MIN_CANDLES) {
            return TradingSignal.HOLD;
        }

        Candle current = candles.get(candles.size() - 1);
        BigDecimal price = current.getClose();

        // ── Condition 1: CONSOLIDATION (prior 8 bars in tight range) ──────────
        List<Candle> consolidationBars = candles.subList(
                candles.size() - 1 - CONSOLIDATION_PERIOD,
                candles.size() - 1);

        BigDecimal consolidationHigh = getHighestHigh(consolidationBars);
        BigDecimal consolidationLow  = getLowestLow(consolidationBars);
        BigDecimal consolidationRange = consolidationHigh
                .subtract(consolidationLow)
                .divide(consolidationLow, 6, RoundingMode.HALF_UP);

        if (consolidationRange.compareTo(BigDecimal.valueOf(MAX_CONSOLIDATION_RANGE_PCT)) > 0) {
            System.out.println("[PerfectBreakout] REJECT - Range too wide: "
                    + consolidationRange.setScale(4, RoundingMode.HALF_UP));
            return TradingSignal.HOLD;
        }

        // ── Condition 2: VOLUME DRY-UP (3 bars before breakout all low-volume) ──
        long avgVolume20 = getAverageVolume(candles, VOLUME_AVG_PERIOD);
        if (avgVolume20 == 0) {
            return TradingSignal.HOLD;
        }

        int dryBars = 0;
        for (int i = candles.size() - 1 - VOLUME_DRY_PERIOD; i < candles.size() - 1; i++) {
            if (candles.get(i).getVolume() < avgVolume20 * VOLUME_DRY_THRESHOLD) {
                dryBars++;
            }
        }

        if (dryBars < VOLUME_DRY_PERIOD) {
            System.out.println("[PerfectBreakout] REJECT - No volume compression: "
                    + dryBars + "/" + VOLUME_DRY_PERIOD + " dry bars");
            return TradingSignal.HOLD;
        }

        // ── Condition 3: VOLUME SPIKE (current bar ≥ 1.8× average) ──────────
        BigDecimal volumeRatio = BigDecimal.valueOf(current.getVolume())
                .divide(BigDecimal.valueOf(avgVolume20), 6, RoundingMode.HALF_UP);

        if (volumeRatio.compareTo(BigDecimal.valueOf(VOLUME_SPIKE_MULTIPLE)) < 0) {
            System.out.println("[PerfectBreakout] REJECT - Volume spike not strong: "
                    + volumeRatio.setScale(2, RoundingMode.HALF_UP) + "x (need "
                    + VOLUME_SPIKE_MULTIPLE + "x)");
            return TradingSignal.HOLD;
        }

        // ── Condition 4: PRICE BREAKOUT (above consolidation high + 0.2% buffer) ──
        BigDecimal breakoutLevel = consolidationHigh.multiply(BigDecimal.valueOf(BREAKOUT_BUFFER));
        if (price.compareTo(breakoutLevel) <= 0) {
            System.out.println("[PerfectBreakout] REJECT - No breakout above "
                    + breakoutLevel.setScale(4, RoundingMode.HALF_UP));
            return TradingSignal.HOLD;
        }

        // ── Condition 5: RSI MOMENTUM FLIP (prev RSI < 50, current RSI ≥ 55) ──
        BigDecimal rsiPrev    = rsiCalculator.calculate(candles.subList(0, candles.size() - 1));
        BigDecimal rsiCurrent = rsiCalculator.calculate(candles);

        if (rsiPrev.compareTo(BigDecimal.valueOf(RSI_PREV_MAX)) >= 0) {
            System.out.println("[PerfectBreakout] REJECT - RSI already elevated before breakout: "
                    + rsiPrev.setScale(2, RoundingMode.HALF_UP));
            return TradingSignal.HOLD;
        }

        if (rsiCurrent.compareTo(BigDecimal.valueOf(RSI_BREAKOUT_LEVEL)) < 0) {
            System.out.println("[PerfectBreakout] REJECT - RSI breakout confirmation weak: "
                    + rsiCurrent.setScale(2, RoundingMode.HALF_UP));
            return TradingSignal.HOLD;
        }

        // ── Condition 6: REGIME (STRONG_UPTREND only) ─────────────────────────
        MarketRegime regime = regimeService.detect(candles);
        if (regime != MarketRegime.STRONG_UPTREND) {
            System.out.println("[PerfectBreakout] REJECT - Wrong regime: " + regime);
            return TradingSignal.HOLD;
        }

        // ── ✅ ALL CONDITIONS MET ──────────────────────────────────────────────
        System.out.println("[PerfectBreakout] ✅ PERFECT BREAKOUT ENTRY");
        System.out.println("  Consolidation range : "
                + consolidationRange.setScale(4, RoundingMode.HALF_UP) + " (≤ "
                + MAX_CONSOLIDATION_RANGE_PCT + ")");
        System.out.println("  Volume dry-up       : " + dryBars + " bars");
        System.out.println("  Volume spike        : "
                + volumeRatio.setScale(2, RoundingMode.HALF_UP) + "x");
        System.out.println("  Price breakout      : "
                + price.setScale(4, RoundingMode.HALF_UP) + " > "
                + breakoutLevel.setScale(4, RoundingMode.HALF_UP));
        System.out.println("  RSI flip            : "
                + rsiPrev.setScale(2, RoundingMode.HALF_UP) + " → "
                + rsiCurrent.setScale(2, RoundingMode.HALF_UP));

        return TradingSignal.BUY;
    }

    // ── Stop / Take-Profit helpers (used by the backtest engine) ─────────────

    /**
     * Computes recommended stop loss: consolidation low − 0.5 × ATR14.
     *
     * @param candles  full candle list (at least {@value #MIN_CANDLES} bars)
     * @return stop loss price
     */
    public BigDecimal calculateStopLoss(List<Candle> candles) {
        List<Candle> consolidationBars = candles.subList(
                candles.size() - 1 - CONSOLIDATION_PERIOD,
                candles.size() - 1);
        BigDecimal consLow = getLowestLow(consolidationBars);
        BigDecimal atr     = new AtrCalculator().calculate(candles, 14);
        return consLow.subtract(atr.multiply(BigDecimal.valueOf(0.5)))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Computes recommended take profit: entry + 3 × consolidation range.
     *
     * @param entryPrice fill price
     * @param candles    full candle list used for the signal
     * @return take profit price
     */
    public BigDecimal calculateTakeProfit(BigDecimal entryPrice, List<Candle> candles) {
        List<Candle> consolidationBars = candles.subList(
                candles.size() - 1 - CONSOLIDATION_PERIOD,
                candles.size() - 1);
        BigDecimal consHigh  = getHighestHigh(consolidationBars);
        BigDecimal consLow   = getLowestLow(consolidationBars);
        BigDecimal consRange = consHigh.subtract(consLow);
        return entryPrice.add(consRange.multiply(BigDecimal.valueOf(3.0)))
                .setScale(4, RoundingMode.HALF_UP);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BigDecimal getHighestHigh(List<Candle> candles) {
        BigDecimal max = candles.get(0).getHigh();
        for (Candle c : candles) {
            if (c.getHigh().compareTo(max) > 0) max = c.getHigh();
        }
        return max;
    }

    private BigDecimal getLowestLow(List<Candle> candles) {
        BigDecimal min = candles.get(0).getLow();
        for (Candle c : candles) {
            if (c.getLow().compareTo(min) < 0) min = c.getLow();
        }
        return min;
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
}
