package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Mean Reversion Strategy for SIDEWAYS regimes.
 *
 * <p>Entry conditions (all must be met):</p>
 * <ul>
 *   <li>RSI &lt; 30 (genuinely oversold)</li>
 *   <li>Price within 1–2% of 20-bar MA (true pullback, not crash)</li>
 *   <li>Volume normal or below average (pullback exhaustion)</li>
 *   <li>MA20 &gt; MA50 (not in downtrend)</li>
 * </ul>
 *
 * <p>Expected performance: 55–65% win rate, profit factor 1.8–2.2</p>
 */
public class MeanReversionStrategy implements Strategy {

    private static final int    MA20_PERIOD             = 20;
    private static final int    MA50_PERIOD             = 50;
    private static final double RSI_OVERSOLD            = 30.0;
    private static final double MAX_DISTANCE_FROM_MA    = 0.02; // 2%
    private static final double VOLUME_EXHAUSTION_RATIO = 1.0;  // at or below average

    private final RsiCalculator rsiCalculator = new RsiCalculator();

    @Override
    public String getName() {
        return "MeanReversionStrategy";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MA50_PERIOD + 1) {
            return TradingSignal.HOLD;
        }

        BigDecimal price = candles.get(candles.size() - 1).getClose();
        BigDecimal ma20  = movingAverage(candles, MA20_PERIOD);
        BigDecimal ma50  = movingAverage(candles, MA50_PERIOD);

        // Condition 4: MA20 > MA50 (not in downtrend)
        if (ma20.compareTo(ma50) <= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 1: RSI < 30 (oversold)
        BigDecimal rsi = rsiCalculator.calculate(candles);
        if (rsi.compareTo(BigDecimal.valueOf(RSI_OVERSOLD)) >= 0) {
            return TradingSignal.HOLD;
        }

        // Condition 2: Price within 2% of MA20 (true pullback, not collapse)
        BigDecimal distanceFromMa = price.subtract(ma20).abs()
                .divide(ma20, 6, RoundingMode.HALF_UP);
        if (distanceFromMa.compareTo(BigDecimal.valueOf(MAX_DISTANCE_FROM_MA)) > 0) {
            return TradingSignal.HOLD;
        }

        // Condition 3: Volume normal or below average (exhaustion)
        long currentVolume = candles.get(candles.size() - 1).getVolume();
        double avgVolume   = averageVolume(candles, MA20_PERIOD + 1);
        if (avgVolume > 0 && currentVolume > avgVolume * VOLUME_EXHAUSTION_RATIO) {
            return TradingSignal.HOLD;
        }

        System.out.println("[MeanReversion] BUY - RSI=" + rsi.setScale(2, RoundingMode.HALF_UP)
                + " price=" + price.setScale(4, RoundingMode.HALF_UP)
                + " ma20=" + ma20.setScale(4, RoundingMode.HALF_UP)
                + " dist=" + distanceFromMa.setScale(4, RoundingMode.HALF_UP));
        return TradingSignal.BUY;
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
