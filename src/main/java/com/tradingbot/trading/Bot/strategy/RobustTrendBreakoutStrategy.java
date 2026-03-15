package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Robust Trend Breakout Strategy — minimal conditions, works on real and mock data.
 *
 * <p>This strategy was designed to replace over-tuned multi-condition strategies that
 * failed on real daily data (Yahoo Finance, live feeds) while succeeding only on mock
 * data. It implements five universally applicable conditions with tighter quality gates
 * than a pure 3-condition approach to improve win rate on noisy real-market data.</p>
 *
 * <ol>
 *   <li><b>Uptrend slope:</b> MA20/MA50 slope ≥ {@value #MIN_MA_SLOPE_PCT}% — requires an
 *       <em>established</em> uptrend, not just a recent MA crossover where the gap between
 *       MA20 and MA50 can be negligibly small.</li>
 *   <li><b>ATR minimum:</b> ATR(14) ≥ {@value #MIN_ATR_PCT}% of price — only enter when
 *       there is enough daily range for the take-profit to be realistically reached within
 *       a normal holding period.</li>
 *   <li><b>Breakout confirmation:</b> Bar <em>close</em> &gt; highest high of the prior
 *       {@value #BREAKOUT_PERIOD} bars + {@value #BREAKOUT_BUFFER_PCT}% buffer.
 *       Uses the close (not the intraday high) to reject false breakouts that
 *       spike through the level intraday then reverse.</li>
 *   <li><b>Volume spike:</b> Current bar volume ≥ {@value #MIN_VOLUME_RATIO}× the
 *       20-bar average — genuine breakouts are accompanied by above-average participation;
 *       low-volume breakouts fail at a significantly higher rate.</li>
 *   <li><b>Momentum confirmation:</b> RSI(14) &gt; {@value #RSI_MIN} — buying pressure is dominant.</li>
 * </ol>
 *
 * <p>Risk management is handled by the backtesting engine (ATR-based SL/TP),
 * not by this strategy. This separation keeps the strategy logic minimal and
 * data-source agnostic.</p>
 *
 * <p>Target quality gates (per problem statement):</p>
 * <ul>
 *   <li>Trade count ≥ 5</li>
 *   <li>Win rate ≥ 60%</li>
 *   <li>Profit factor ≥ 1.2</li>
 *   <li>Max drawdown ≤ 25%</li>
 * </ul>
 */
@Service
public class RobustTrendBreakoutStrategy implements Strategy {

    /** Minimum number of candles needed for MA50 + RSI(14). */
    public static final int MIN_CANDLES = 60;

    /** Lookback period for the breakout high (prior bars, excluding current). */
    public static final int BREAKOUT_PERIOD = 20;

    /**
     * Breakout buffer above the N-bar high (0.1%).
     * Keeps the threshold practical on real daily data without requiring
     * an unreachably large move relative to normal daily noise.
     */
    public static final double BREAKOUT_BUFFER_PCT = 0.001;

    /**
     * Minimum MA20/MA50 slope required to confirm an established uptrend.
     * A simple MA20 &gt; MA50 crossover can occur with negligible difference;
     * requiring 1% slope ensures a meaningful, sustained trend is underway.
     */
    public static final double MIN_MA_SLOPE_PCT = 0.01; // 1%

    /**
     * Minimum ATR(14) as a fraction of price.
     * Entries in low-volatility compression periods rarely reach the TP;
     * requiring 0.5% ATR ensures the daily range is wide enough for the
     * take-profit target (placed at 4.5×ATR) to be reachable.
     */
    public static final double MIN_ATR_PCT = 0.005; // 0.5%

    /**
     * Current bar volume must be at least this multiple of the 20-bar average.
     * Breakouts on above-average volume are far more likely to follow through
     * than those on thin, low-conviction volume.
     */
    public static final double MIN_VOLUME_RATIO = 1.2; // 120% of avg

    /** Lookback window for the volume average (bars). */
    private static final int VOLUME_AVG_PERIOD = 20;

    /** RSI must exceed this level to confirm positive momentum. */
    public static final double RSI_MIN = 50.0;

    private final RsiCalculator rsiCalculator;
    // AtrCalculator is a stateless utility with no Spring dependencies;
    // inline instantiation is the established codebase convention (used in 10+ classes).
    private final AtrCalculator atrCalculator = new AtrCalculator();

    public RobustTrendBreakoutStrategy(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    @Override
    public String getName() {
        return "RobustTrendBreakoutStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MIN_CANDLES) {
            return TradingSignal.HOLD;
        }

        Candle current = candles.get(candles.size() - 1);
        BigDecimal price = current.getClose();

        // ── Condition 1: UPTREND SLOPE — MA20/MA50 slope ≥ 1% ────────────────
        // Requires an established uptrend, not just a marginal MA crossover.
        BigDecimal ma20 = movingAverage(candles, 20);
        BigDecimal ma50 = movingAverage(candles, 50);

        if (ma50.compareTo(BigDecimal.ZERO) == 0) {
            return TradingSignal.HOLD;
        }

        BigDecimal slope = ma20.subtract(ma50).divide(ma50, 6, RoundingMode.HALF_UP);

        if (slope.compareTo(BigDecimal.valueOf(MIN_MA_SLOPE_PCT)) < 0) {
            System.out.println("[RobustBreakout] HOLD: MA slope(" + fmt(slope)
                    + ") < " + MIN_MA_SLOPE_PCT + " — uptrend not established");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Uptrend slope: MA20/MA50=" + fmt(slope)
                + " ≥ " + MIN_MA_SLOPE_PCT);

        // ── Condition 2: ATR MINIMUM — ATR(14) ≥ 0.5% of price ──────────────
        // Ensures enough daily range for the TP to be reachable.
        BigDecimal atr = atrCalculator.calculate(candles, 14);
        BigDecimal atrPct = price.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : atr.divide(price, 6, RoundingMode.HALF_UP);

        if (atrPct.compareTo(BigDecimal.valueOf(MIN_ATR_PCT)) < 0) {
            System.out.println("[RobustBreakout] HOLD: ATR%=" + fmt(atrPct)
                    + " < " + MIN_ATR_PCT + " — volatility too low");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ ATR%=" + fmt(atrPct)
                + " ≥ " + MIN_ATR_PCT);

        // ── Condition 3: BREAKOUT — close > N-bar high + buffer ───────────────
        BigDecimal nBarHigh = highestHigh(candles, BREAKOUT_PERIOD);
        BigDecimal breakoutLevel = nBarHigh.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(BREAKOUT_BUFFER_PCT)));

        System.out.println("[RobustBreakout] Close: " + fmt(price)
                + " vs breakout level: " + fmt(breakoutLevel)
                + " (highest " + BREAKOUT_PERIOD + "-bar high: " + fmt(nBarHigh) + ")");

        if (price.compareTo(breakoutLevel) <= 0) {
            System.out.println("[RobustBreakout] HOLD: Close did not break above "
                    + BREAKOUT_PERIOD + "-bar high");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Breakout confirmed (close above level)");

        // ── Condition 4: VOLUME SPIKE — volume ≥ 1.2× 20-bar average ─────────
        // Genuine breakouts are accompanied by above-average participation.
        long avgVolume     = averageVolume(candles, VOLUME_AVG_PERIOD);
        long currentVolume = current.getVolume();

        if (avgVolume > 0) {
            BigDecimal volRatio = BigDecimal.valueOf(currentVolume)
                    .divide(BigDecimal.valueOf(avgVolume), 6, RoundingMode.HALF_UP);
            System.out.println("[RobustBreakout] Volume ratio: " + fmt(volRatio)
                    + "x (min: " + MIN_VOLUME_RATIO + "x)");
            if (volRatio.compareTo(BigDecimal.valueOf(MIN_VOLUME_RATIO)) < 0) {
                System.out.println("[RobustBreakout] HOLD: Volume too low for reliable breakout");
                return TradingSignal.HOLD;
            }
            System.out.println("[RobustBreakout] ✓ Volume spike confirmed");
        }

        // ── Condition 5: RSI MOMENTUM > 50 ────────────────────────────────────
        BigDecimal rsi = rsiCalculator.calculate(candles);

        System.out.println("[RobustBreakout] RSI: " + fmt(rsi) + " (min: " + RSI_MIN + ")");

        if (rsi.compareTo(BigDecimal.valueOf(RSI_MIN)) <= 0) {
            System.out.println("[RobustBreakout] HOLD: RSI below " + RSI_MIN);
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Momentum confirmed (RSI > " + RSI_MIN + ")");

        System.out.println("[RobustBreakout] ✅ BUY SIGNAL");
        return TradingSignal.BUY;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns the highest high over the last {@code period} bars, excluding the current bar. */
    private BigDecimal highestHigh(List<Candle> candles, int period) {
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

    /** Returns the average volume over the last {@code period} bars, excluding the current bar. */
    private long averageVolume(List<Candle> candles, int period) {
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

    /** Returns the simple moving average of the last {@code period} closes. */
    private BigDecimal movingAverage(List<Candle> candles, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        int end   = candles.size();
        int start = Math.max(0, end - period);
        int count = 0;
        for (int i = start; i < end; i++) {
            sum = sum.add(candles.get(i).getClose());
            count++;
        }
        return count == 0 ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
    }

    private String fmt(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
