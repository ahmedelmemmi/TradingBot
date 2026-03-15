package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A configurable, non-singleton variant of {@link SimplifiedBreakoutStrategy} that
 * accepts per-asset parameter overrides at construction time.
 *
 * <p>Designed for per-asset parameter sweeping: instantiate with different thresholds,
 * run through {@link com.tradingbot.trading.Bot.backtest.BacktestEngine#runStrategy}, and
 * compare results to find the minimal relaxation that yields ≥8 trades with quality metrics.</p>
 *
 * <p>Regime detection is performed inline (MA20/MA50 slope + 10-bar momentum) rather than
 * via the shared, stateful {@link com.tradingbot.trading.Bot.market.MarketRegimeService},
 * so each instance uses its own configurable slope threshold without side-effects.</p>
 *
 * <p>Breakout confirmation uses the bar's <em>close</em> price (not the intraday high),
 * consistent with {@link SimplifiedBreakoutStrategy}, to filter false breakouts where price
 * briefly touches the level intraday but reverses before the close.</p>
 */
public class TunedBreakoutStrategy implements Strategy {

    /** Minimum number of candles required before any signal is produced. */
    public static final int MIN_CANDLES = 60;

    /** Look-back window for the average volume calculation (bars). */
    private static final int VOLUME_AVG_PERIOD = 20;

    /** MA periods used for regime slope calculation. */
    private static final int MA_SHORT_PERIOD = 20;
    private static final int MA_LONG_PERIOD  = 50;

    /** Momentum look-back period for trend confirmation. */
    private static final int MOMENTUM_PERIOD = 10;

    // ── Configurable thresholds ───────────────────────────────────────────────

    /** MA20/MA50 slope must exceed this value for STRONG_UPTREND (e.g. 0.02 = 2%). */
    private final double slopeThreshold;

    /** ATR(14) / price must exceed this fraction (e.g. 0.009 = 0.9%). */
    private final double atrThreshold;

    /**
     * Breakout buffer multiplier applied to the 5-bar highest high
     * (e.g. 1.0005 = 0.05% buffer; 1.0 = no buffer).
     */
    private final double breakoutBuffer;

    /** Current bar volume must be at least this fraction of the 20-bar average (e.g. 0.50 = 50%). */
    private final double volumeRatio;

    /** RSI(14) must be above this level (e.g. 50.0). */
    private final double rsiMin;

    private final RsiCalculator rsiCalculator;
    private final AtrCalculator atrCalculator = new AtrCalculator();

    private final String name;

    public TunedBreakoutStrategy(double slopeThreshold,
                                  double atrThreshold,
                                  double breakoutBuffer,
                                  double volumeRatio,
                                  double rsiMin,
                                  RsiCalculator rsiCalculator) {
        this.slopeThreshold = slopeThreshold;
        this.atrThreshold   = atrThreshold;
        this.breakoutBuffer = breakoutBuffer;
        this.volumeRatio    = volumeRatio;
        this.rsiMin         = rsiMin;
        this.rsiCalculator  = rsiCalculator;

        this.name = String.format(
                "TunedBreakout[slope=%.1f%%,atr=%.1f%%,buf=%.3f%%,vol=%.0f%%,rsi=%.0f]",
                slopeThreshold * 100, atrThreshold * 100,
                (breakoutBuffer - 1.0) * 100, volumeRatio * 100, rsiMin);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MIN_CANDLES) {
            return TradingSignal.HOLD;
        }

        Candle current = candles.get(candles.size() - 1);
        BigDecimal price = current.getClose();

        // ── Condition 1: Inline STRONG_UPTREND detection ──────────────────────
        if (!isStrongUptrend(candles)) {
            return TradingSignal.HOLD;
        }

        // ── Condition 2: ATR volatility spike ────────────────────────────────
        BigDecimal atr = atrCalculator.calculate(candles, 14);
        BigDecimal atrPct = price.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : atr.divide(price, 6, RoundingMode.HALF_UP);

        if (atrPct.compareTo(BigDecimal.valueOf(atrThreshold)) < 0) {
            return TradingSignal.HOLD;
        }

        // ── Condition 3: Volume above average ─────────────────────────────────
        long avgVol     = getAverageVolume(candles, VOLUME_AVG_PERIOD);
        long currentVol = current.getVolume();

        BigDecimal volRatio = (avgVol == 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(currentVol)
                        .divide(BigDecimal.valueOf(avgVol), 6, RoundingMode.HALF_UP);

        if (volRatio.compareTo(BigDecimal.valueOf(volumeRatio)) < 0) {
            return TradingSignal.HOLD;
        }

        // ── Condition 4: Price breakout above 5-bar high + buffer ─────────────
        // Uses the bar's CLOSE (not the intraday high) so that only genuine
        // close-confirmed breakouts generate a signal.
        BigDecimal highest5      = getHighestHigh(candles, SimplifiedBreakoutStrategy.BREAKOUT_PERIOD);
        BigDecimal breakoutLevel = highest5.multiply(BigDecimal.valueOf(breakoutBuffer));

        if (price.compareTo(breakoutLevel) <= 0) {
            return TradingSignal.HOLD;
        }

        // ── Condition 5: RSI positive momentum ───────────────────────────────
        try {
            BigDecimal rsi = rsiCalculator.calculate(candles);
            if (rsi.compareTo(BigDecimal.valueOf(rsiMin)) < 0) {
                return TradingSignal.HOLD;
            }
        } catch (Exception e) {
            return TradingSignal.HOLD;
        }

        return TradingSignal.BUY;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Inline STRONG_UPTREND detection: MA20/MA50 slope > {@code slopeThreshold}
     * and 10-bar momentum is positive.
     */
    private boolean isStrongUptrend(List<Candle> candles) {
        if (candles.size() < MA_LONG_PERIOD) return false;

        BigDecimal ma20 = movingAverage(candles, MA_SHORT_PERIOD);
        BigDecimal ma50 = movingAverage(candles, MA_LONG_PERIOD);

        if (ma50.compareTo(BigDecimal.ZERO) == 0) return false;

        BigDecimal slope = ma20.subtract(ma50)
                .divide(ma50, 6, RoundingMode.HALF_UP);

        // Positive 10-bar momentum
        if (candles.size() < MOMENTUM_PERIOD + 1) return false;

        BigDecimal recent = candles.get(candles.size() - 1).getClose();
        BigDecimal past   = candles.get(candles.size() - MOMENTUM_PERIOD).getClose();
        boolean momentumPositive = recent.compareTo(past) > 0;

        return slope.compareTo(BigDecimal.valueOf(slopeThreshold)) > 0 && momentumPositive;
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal getHighestHigh(List<Candle> candles, int period) {
        BigDecimal max = BigDecimal.ZERO;
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        for (int i = start; i < end; i++) {
            if (candles.get(i).getHigh().compareTo(max) > 0) {
                max = candles.get(i).getHigh();
            }
        }
        return max;
    }

    private long getAverageVolume(List<Candle> candles, int period) {
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        long sum  = 0;
        int count = 0;
        for (int i = start; i < end; i++) {
            sum += candles.get(i).getVolume();
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    // ── Accessors for audit reporting ─────────────────────────────────────────

    public double getSlopeThreshold()  { return slopeThreshold; }
    public double getAtrThreshold()    { return atrThreshold; }
    public double getBreakoutBuffer()  { return breakoutBuffer; }
    public double getVolumeRatio()     { return volumeRatio; }
    public double getRsiMin()          { return rsiMin; }
}
