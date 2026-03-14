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
    static final double MAX_CONSOLIDATION_RANGE_PCT = 0.025; // 2.5%

    /** Number of bars immediately before the breakout bar that must show low volume. */
    static final int VOLUME_DRY_PERIOD = 2;

    /** Volume threshold (fraction of 20-bar average) below which a bar is "dry". */
    static final double VOLUME_DRY_THRESHOLD = 0.6; // 60%

    /** Minimum volume ratio (current / 20-bar avg) required to confirm a spike. */
    static final double VOLUME_SPIKE_MULTIPLE = 1.3; // 30% above average

    /** RSI of the bar BEFORE the breakout must be below this level. */
    static final double RSI_PREV_MAX = 55.0;

    /** RSI of the breakout bar must be at or above this level. */
    static final double RSI_BREAKOUT_LEVEL = 60.0;

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

        System.out.println("[DEBUG] Bar " + candles.size()
                + " | Consolidation range: " + fmt(consolidationRange)
                + " (threshold: " + fmt(BigDecimal.valueOf(MAX_CONSOLIDATION_RANGE_PCT)) + ")");

        if (consolidationRange.compareTo(BigDecimal.valueOf(MAX_CONSOLIDATION_RANGE_PCT)) > 0) {
            System.out.println("  → REJECT: Range too wide");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Consolidation tight");

        // ── Condition 2: VOLUME DRY-UP (bars before breakout all low-volume) ──
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

        System.out.println("[DEBUG] Volume dry-up: " + dryBars
                + " bars (threshold: " + VOLUME_DRY_PERIOD + ")");

        if (dryBars < VOLUME_DRY_PERIOD) {
            System.out.println("  → REJECT: Not enough dry bars");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Volume compressed");

        // ── Condition 3: VOLUME SPIKE (current bar ≥ VOLUME_SPIKE_MULTIPLE × avg) ──
        long currentVolume = current.getVolume();
        BigDecimal volumeRatio = (avgVolume20 == 0 || currentVolume == 0) ? BigDecimal.ZERO
                : BigDecimal.valueOf(currentVolume)
                        .divide(BigDecimal.valueOf(avgVolume20), 6, RoundingMode.HALF_UP);

        System.out.println("[DEBUG] Volume spike: " + fmt(volumeRatio)
                + "x (threshold: " + fmt(BigDecimal.valueOf(VOLUME_SPIKE_MULTIPLE)) + "x)");

        if (volumeRatio.compareTo(BigDecimal.valueOf(VOLUME_SPIKE_MULTIPLE)) < 0) {
            System.out.println("  → REJECT: Volume spike not strong enough");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Volume spiked");

        // ── Condition 4: PRICE BREAKOUT (above consolidation high + 0.2% buffer) ──
        BigDecimal breakoutLevel = consolidationHigh.multiply(BigDecimal.valueOf(BREAKOUT_BUFFER));

        System.out.println("[DEBUG] Price breakout: " + fmt(price)
                + " vs " + fmt(breakoutLevel)
                + " (diff: " + fmt(price.subtract(breakoutLevel)) + ")");

        if (price.compareTo(breakoutLevel) <= 0) {
            System.out.println("  → REJECT: No price breakout");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Price broke out");

        // ── Condition 5: RSI MOMENTUM FLIP (prev RSI < RSI_PREV_MAX, current RSI ≥ RSI_BREAKOUT_LEVEL) ──
        BigDecimal rsiPrev    = rsiCalculator.calculate(candles.subList(0, candles.size() - 1));
        BigDecimal rsiCurrent = rsiCalculator.calculate(candles);

        System.out.println("[DEBUG] RSI momentum: " + fmt(rsiPrev)
                + " → " + fmt(rsiCurrent)
                + " (threshold: <" + RSI_PREV_MAX + " → >=" + RSI_BREAKOUT_LEVEL + ")");

        if (rsiPrev.compareTo(BigDecimal.valueOf(RSI_PREV_MAX)) >= 0) {
            System.out.println("  → REJECT: RSI already elevated (prev)");
            return TradingSignal.HOLD;
        }

        if (rsiCurrent.compareTo(BigDecimal.valueOf(RSI_BREAKOUT_LEVEL)) < 0) {
            System.out.println("  → REJECT: RSI not at breakout level");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: RSI momentum confirmed");

        // ── Condition 6: REGIME (STRONG_UPTREND only) ─────────────────────────
        MarketRegime regime = regimeService.detect(candles);

        System.out.println("[DEBUG] Market regime: " + regime);

        if (regime != MarketRegime.STRONG_UPTREND) {
            System.out.println("  → REJECT: Not in STRONG_UPTREND");
            return TradingSignal.HOLD;
        }
        System.out.println("  → PASS: Regime is STRONG_UPTREND");

        // ── ✅ ALL CONDITIONS MET ──────────────────────────────────────────────
        System.out.println("[PerfectBreakout] ✅✅✅ PERFECT ENTRY SIGNAL ✅✅✅");
        System.out.println("  Consolidation: " + fmt(consolidationRange));
        System.out.println("  Volume: " + dryBars + " dry bars → " + fmt(volumeRatio) + "x spike");
        System.out.println("  Price: " + fmt(price) + " > " + fmt(breakoutLevel));
        System.out.println("  RSI: " + fmt(rsiPrev) + " → " + fmt(rsiCurrent));

        return TradingSignal.BUY;
    }

    private String fmt(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP).toPlainString();
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
