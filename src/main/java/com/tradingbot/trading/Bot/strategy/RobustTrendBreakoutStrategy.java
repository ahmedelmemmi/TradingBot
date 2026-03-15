package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Robust Trend Breakout Strategy — the single active strategy for this bot.
 *
 * <p>Designed to work on real daily market data (Yahoo Finance, live feeds).
 * Six conditions must ALL be met for a BUY signal:</p>
 *
 * <ol>
 *   <li><b>Uptrend:</b> MA20 &gt; MA50 — price is in a rising trend.</li>
 *   <li><b>Trend strength:</b> MA20 is at least {@value #MIN_MA_RATIO_PCT}% above MA50 —
 *       filters weak/marginal crossovers that quickly reverse.</li>
 *   <li><b>Breakout (current bar):</b> Current bar <em>close</em> &gt; highest high of the prior
 *       {@value #BREAKOUT_PERIOD} bars + {@value #BREAKOUT_BUFFER_PCT}% buffer.
 *       Uses the close (not the intraday high) to reject false breakouts that
 *       spike through the level intraday then reverse.</li>
 *   <li><b>Breakout confirmation (prior bar):</b> The <em>previous</em> bar also closed above
 *       the breakout level — requiring 2 consecutive closes above resistance filters
 *       single-bar false breakouts that quickly reverse.</li>
 *   <li><b>Volume confirmation:</b> Current bar volume &gt; {@value #VOLUME_RATIO_MIN}×
 *       the {@value #VOLUME_AVG_PERIOD}-bar average — breakout must be backed by above-average
 *       participation to filter false breakouts on thin volume.</li>
 *   <li><b>Momentum:</b> RSI(14) &gt; {@value #RSI_MIN} — buying pressure is dominant.</li>
 * </ol>
 *
 * <p>Risk management is handled by the backtesting engine (ATR-based SL/TP),
 * not by this strategy. This separation keeps the strategy logic minimal and
 * data-source agnostic.</p>
 *
 * <p>Target quality gates:</p>
 * <ul>
 *   <li>Trade count ≥ 5</li>
 *   <li>Win rate ≥ 60%</li>
 *   <li>Profit factor ≥ 1.2</li>
 *   <li>Max drawdown ≤ 25%</li>
 * </ul>
 */
@Service
public class RobustTrendBreakoutStrategy implements Strategy {

    /** Minimum number of candles needed for MA50 + RSI(14) + 2-bar confirmation. */
    public static final int MIN_CANDLES = 61;

    /** Lookback period for the breakout high (prior bars, excluding current). */
    public static final int BREAKOUT_PERIOD = 20;

    /**
     * Breakout buffer above the N-bar high (0.1%).
     * Keeps the threshold practical on real daily data without requiring
     * an unreachably large move relative to normal daily noise.
     */
    public static final double BREAKOUT_BUFFER_PCT = 0.001;

    /** RSI must exceed this level to confirm positive momentum. */
    public static final double RSI_MIN = 50.0;

    /**
     * Minimum ratio: (MA20 - MA50) / MA50 must be at least this fraction (0.5%).
     * Increased from 0.3% to 0.5% to filter marginal uptrends that frequently
     * produce false breakouts and revert within a few bars.
     */
    public static final double MIN_MA_RATIO_PCT = 0.005;

    /**
     * Breakout bar volume must be at least this multiple of the N-bar average (1.2×).
     * Increased from 1.1× to 1.2× — stronger volume confirmation reduces false
     * breakouts on thin volume which are a primary source of losing trades.
     */
    public static final double VOLUME_RATIO_MIN = 1.2;

    /** Look-back window for the average volume calculation (bars). */
    public static final int VOLUME_AVG_PERIOD = 20;

    private final RsiCalculator rsiCalculator;

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

        // ── Condition 1: UPTREND — MA20 > MA50 ────────────────────────────────
        BigDecimal ma20 = movingAverage(candles, 20);
        BigDecimal ma50 = movingAverage(candles, 50);

        if (ma20.compareTo(ma50) <= 0) {
            System.out.println("[RobustBreakout] HOLD: MA20(" + fmt(ma20)
                    + ") <= MA50(" + fmt(ma50) + ") — not in uptrend");
            return TradingSignal.HOLD;
        }

        // ── Condition 2: TREND STRENGTH — (MA20-MA50)/MA50 >= MIN_MA_RATIO_PCT ──
        // Marginal crossovers (MA20 barely above MA50) frequently reverse;
        // requiring a minimum ratio filters these weak signals.
        if (ma50.compareTo(BigDecimal.ZERO) == 0) {
            return TradingSignal.HOLD;
        }
        BigDecimal maRatio = ma20.subtract(ma50)
                .divide(ma50, 6, RoundingMode.HALF_UP);

        if (maRatio.compareTo(BigDecimal.valueOf(MIN_MA_RATIO_PCT)) < 0) {
            System.out.println("[RobustBreakout] HOLD: MA ratio(" + fmt(maRatio)
                    + ") < " + MIN_MA_RATIO_PCT + " — trend not strong enough");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Uptrend: MA20(" + fmt(ma20)
                + ") > MA50(" + fmt(ma50) + ") ratio=" + fmt(maRatio));

        // ── Condition 3: BREAKOUT — current bar close > N-bar high + buffer ───
        BigDecimal nBarHigh = highestHigh(candles, BREAKOUT_PERIOD);
        BigDecimal breakoutLevel = nBarHigh.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(BREAKOUT_BUFFER_PCT)));

        System.out.println("[RobustBreakout] Close: " + fmt(price)
                + " vs breakout level: " + fmt(breakoutLevel)
                + " (highest " + BREAKOUT_PERIOD + "-bar high: " + fmt(nBarHigh) + ")");

        if (price.compareTo(breakoutLevel) <= 0) {
            System.out.println("[RobustBreakout] HOLD: Close did not break above " + BREAKOUT_PERIOD + "-bar high");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Breakout confirmed (close above level)");

        // ── Condition 4: PRIOR BAR BREAKOUT CONFIRMATION (2 consecutive closes) ─
        // Require that the previous bar ALSO closed above the breakout level
        // (computed for the prior bar's lookahead). A single-bar spike that closes
        // above resistance is frequently a false breakout that reverses within 1–3
        // bars; requiring two consecutive closes above the level dramatically
        // improves signal quality.
        List<Candle> priorSubset = candles.subList(0, candles.size() - 1);
        BigDecimal priorClose = priorSubset.get(priorSubset.size() - 1).getClose();
        BigDecimal priorNBarHigh = highestHigh(priorSubset, BREAKOUT_PERIOD);
        BigDecimal priorBreakoutLevel = priorNBarHigh.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(BREAKOUT_BUFFER_PCT)));

        if (priorClose.compareTo(priorBreakoutLevel) <= 0) {
            System.out.println("[RobustBreakout] HOLD: Prior bar close (" + fmt(priorClose)
                    + ") did not confirm breakout (" + fmt(priorBreakoutLevel)
                    + ") — single-bar breakouts rejected");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Prior bar confirms breakout (2 consecutive closes)");

        // ── Condition 5: VOLUME CONFIRMATION — above-average volume ───────────
        // False breakouts on thin volume are a primary source of losing trades.
        long avgVolume     = averageVolume(candles, VOLUME_AVG_PERIOD);
        long currentVolume = current.getVolume();

        if (avgVolume > 0) {
            double volRatio = (double) currentVolume / avgVolume;
            if (volRatio < VOLUME_RATIO_MIN) {
                System.out.println("[RobustBreakout] HOLD: Volume ratio " + String.format("%.2f", volRatio)
                        + " < " + VOLUME_RATIO_MIN + " — breakout not backed by volume");
                return TradingSignal.HOLD;
            }
            System.out.println("[RobustBreakout] ✓ Volume confirmed (ratio=" + String.format("%.2f", volRatio) + ")");
        }

        // ── Condition 6: RSI MOMENTUM > 50 ────────────────────────────────────
        BigDecimal rsi = rsiCalculator.calculate(candles);

        System.out.println("[RobustBreakout] RSI: " + fmt(rsi) + " (min: " + RSI_MIN + ")");

        if (rsi.compareTo(BigDecimal.valueOf(RSI_MIN)) <= 0) {
            System.out.println("[RobustBreakout] HOLD: RSI below 50");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Momentum confirmed (RSI > 50)");

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

    private String fmt(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
