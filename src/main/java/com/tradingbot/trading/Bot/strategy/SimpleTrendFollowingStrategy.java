package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Simple Trend Following Strategy for STRONG_UPTREND regimes.
 *
 * <p>Entry conditions (ALL must be true):</p>
 * <ul>
 *   <li>Price breaks ABOVE the 20-bar highest high by at least 0.5%</li>
 *   <li>Price is ABOVE MA50 (confirming trend exists)</li>
 *   <li>Price is ABOVE MA20 (confirming pullback is over)</li>
 *   <li>Volume &gt; 80% of 20-bar average (shows conviction)</li>
 *   <li>RSI &gt; 40 (not oversold, momentum positive)</li>
 *   <li>Minimum 60 bars of data required</li>
 * </ul>
 *
 * <p>Stop Loss: MIN(last 5 bars low) - (1.0 × ATR14)</p>
 * <p>Take Profit: Entry + (2.0 × (Entry - StopLoss)) — 2:1 reward:risk ratio</p>
 *
 * <p>Expected performance: 45–55% win rate, profit factor 1.5–1.8</p>
 */
public class SimpleTrendFollowingStrategy implements Strategy {

    private static final int    BREAKOUT_PERIOD      = 20;
    private static final int    MA50_PERIOD          = 50;
    private static final int    MA20_PERIOD          = 20;
    private static final int    MIN_CANDLES          = 60;
    /** Price must be this much above the 20-bar high to confirm breakout */
    private static final double BREAKOUT_CONFIRM_PCT = 0.005; // 0.5%
    private static final double RSI_MIN              = 40.0;
    private static final double VOLUME_MIN_PCT       = 0.80;  // 80% of average

    private final AtrCalculator atrCalculator = new AtrCalculator();
    private final RsiCalculator rsiCalculator = new RsiCalculator();

    @Override
    public String getName() {
        return "SimpleTrendFollowingStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MIN_CANDLES) {
            return TradingSignal.HOLD;
        }

        BigDecimal price = candles.get(candles.size() - 1).getClose();

        // Condition 1: Price is above MA50 (trend confirmation)
        BigDecimal ma50 = movingAverage(candles, MA50_PERIOD);
        if (price.compareTo(ma50) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 2: Price is above MA20 (pullback is over)
        BigDecimal ma20 = movingAverage(candles, MA20_PERIOD);
        if (price.compareTo(ma20) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 3: RSI > 40 (not oversold, momentum positive)
        BigDecimal rsi = rsiCalculator.calculate(candles);
        if (rsi.compareTo(BigDecimal.valueOf(RSI_MIN)) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 4: Price breaks above 20-bar highest high by at least 0.5%
        BigDecimal highest20 = highestHigh(candles, BREAKOUT_PERIOD);
        BigDecimal breakoutLevel = highest20.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(BREAKOUT_CONFIRM_PCT)));
        if (price.compareTo(breakoutLevel) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 5: Volume > 80% of 20-bar average (conviction)
        long currentVolume = candles.get(candles.size() - 1).getVolume();
        double avgVolume = averageVolume(candles, BREAKOUT_PERIOD);
        if (avgVolume > 0 && currentVolume < avgVolume * VOLUME_MIN_PCT) {
            return TradingSignal.HOLD;
        }

        System.out.println("[SimpleTrendFollowing] BUY SIGNAL"
                + " price=" + price.setScale(4, RoundingMode.HALF_UP)
                + " breakoutLevel=" + breakoutLevel.setScale(4, RoundingMode.HALF_UP)
                + " ma50=" + ma50.setScale(4, RoundingMode.HALF_UP)
                + " ma20=" + ma20.setScale(4, RoundingMode.HALF_UP)
                + " RSI=" + rsi.setScale(2, RoundingMode.HALF_UP));
        return TradingSignal.BUY;
    }

    /**
     * Computes the recommended stop loss: MIN(last 5 bars low) - 1.0 × ATR14.
     * Callers can use this for position sizing and SL placement.
     *
     * @param candles full candle list (at least 60 bars)
     * @return stop loss price
     */
    public BigDecimal calculateStopLoss(List<Candle> candles) {
        BigDecimal lowestLow5 = lowestLow(candles, 5);
        BigDecimal atr = atrCalculator.calculate(candles, 14);
        return lowestLow5.subtract(atr).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Computes the recommended take profit using a 2:1 reward:risk ratio.
     *
     * @param entryPrice fill price
     * @param stopLoss   computed stop loss
     * @return take profit price
     */
    public BigDecimal calculateTakeProfit(BigDecimal entryPrice, BigDecimal stopLoss) {
        BigDecimal risk = entryPrice.subtract(stopLoss).abs();
        return entryPrice.add(risk.multiply(BigDecimal.valueOf(2.0)))
                .setScale(4, RoundingMode.HALF_UP);
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
     * including the current (last) candle.
     */
    private BigDecimal lowestLow(List<Candle> candles, int period) {
        int end   = candles.size();
        int start = Math.max(0, end - period);
        BigDecimal min = candles.get(start).getLow();
        for (int i = start + 1; i < end; i++) {
            BigDecimal l = candles.get(i).getLow();
            if (l.compareTo(min) < 0) min = l;
        }
        return min;
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
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
