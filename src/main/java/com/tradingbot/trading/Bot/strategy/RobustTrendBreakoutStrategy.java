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
 * data. All excessive filters (regime service, volume dry-up, ATR gate) have been
 * removed. Only three universally applicable conditions are checked:</p>
 *
 * <ol>
 *   <li><b>Uptrend confirmation:</b> MA20 &gt; MA50 — price is in a rising trend.</li>
 *   <li><b>Breakout confirmation:</b> Bar <em>close</em> &gt; highest high of the prior
 *       {@value #BREAKOUT_PERIOD} bars + {@value #BREAKOUT_BUFFER_PCT}% buffer.
 *       Uses the close (not the intraday high) to reject false breakouts that
 *       spike through the level intraday then reverse.</li>
 *   <li><b>Momentum confirmation:</b> RSI(14) &gt; 50 — buying pressure is dominant.</li>
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

    /** RSI must exceed this level to confirm positive momentum. */
    public static final double RSI_MIN = 50.0;

    private final RsiCalculator rsiCalculator;
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

        // ── Condition 1: UPTREND — MA20 > MA50 ────────────────────────────────
        BigDecimal ma20 = movingAverage(candles, 20);
        BigDecimal ma50 = movingAverage(candles, 50);

        if (ma20.compareTo(ma50) <= 0) {
            System.out.println("[RobustBreakout] HOLD: MA20(" + fmt(ma20)
                    + ") <= MA50(" + fmt(ma50) + ") — not in uptrend");
            return TradingSignal.HOLD;
        }
        System.out.println("[RobustBreakout] ✓ Uptrend: MA20(" + fmt(ma20)
                + ") > MA50(" + fmt(ma50) + ")");

        // ── Condition 2: BREAKOUT — close > N-bar high + buffer ───────────────
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

        // ── Condition 3: RSI MOMENTUM > 50 ────────────────────────────────────
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

    private String fmt(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
