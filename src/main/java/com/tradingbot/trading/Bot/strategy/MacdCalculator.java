package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calculates MACD (Moving Average Convergence Divergence).
 *
 * MACD Line   = EMA(12) − EMA(26)
 * Signal Line = EMA(9) of the MACD Line series
 * Histogram   = MACD Line − Signal Line
 *
 * Minimum required candles: SLOW_PERIOD + SIGNAL_PERIOD = 35
 */
@Service
public class MacdCalculator {

    private static final int FAST_PERIOD   = 12;
    private static final int SLOW_PERIOD   = 26;
    private static final int SIGNAL_PERIOD = 9;

    public static class MacdResult {
        private final BigDecimal macdLine;
        private final BigDecimal signalLine;
        private final BigDecimal histogram;

        public MacdResult(BigDecimal macdLine,
                          BigDecimal signalLine,
                          BigDecimal histogram) {
            this.macdLine   = macdLine;
            this.signalLine = signalLine;
            this.histogram  = histogram;
        }

        public BigDecimal getMacdLine()   { return macdLine; }
        public BigDecimal getSignalLine() { return signalLine; }
        public BigDecimal getHistogram()  { return histogram; }
    }

    /**
     * Calculates the current MACD value for the given candle series.
     *
     * @param candles ordered candle list (oldest first), min 35 candles
     */
    public MacdResult calculate(List<Candle> candles) {

        int n = candles.size();
        int required = SLOW_PERIOD + SIGNAL_PERIOD; // 35

        if (n < required) {
            throw new IllegalArgumentException(
                    "Not enough candles for MACD. Need at least "
                    + required + ", got " + n);
        }

        // 1. Compute full EMA(12) and EMA(26) series over all candles
        BigDecimal[] fastEma = computeEma(candles, FAST_PERIOD);
        BigDecimal[] slowEma = computeEma(candles, SLOW_PERIOD);

        // 2. Build MACD-line values (valid from index SLOW_PERIOD-1 onwards)
        int macdStart = SLOW_PERIOD - 1; // 25
        int macdLen   = n - macdStart;   // number of valid MACD values

        BigDecimal[] macdValues = new BigDecimal[macdLen];
        for (int i = 0; i < macdLen; i++) {
            macdValues[i] = fastEma[macdStart + i]
                    .subtract(slowEma[macdStart + i]);
        }

        // 3. Signal line = EMA(9) of the MACD series
        BigDecimal signalLine = emaOfValues(macdValues, SIGNAL_PERIOD);

        BigDecimal macdLine  = macdValues[macdLen - 1];
        BigDecimal histogram = macdLine.subtract(signalLine);

        return new MacdResult(macdLine, signalLine, histogram);
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * Computes an EMA array over the full candle series.
     * result[period-1] is the first valid value (seeded with SMA).
     */
    private BigDecimal[] computeEma(List<Candle> candles, int period) {

        int n = candles.size();
        BigDecimal[] result = new BigDecimal[n];
        BigDecimal multiplier =
                BigDecimal.valueOf(2.0 / (period + 1));

        // Seed: SMA of first `period` closes
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        result[period - 1] =
                sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);

        // Rolling EMA for remaining candles
        for (int i = period; i < n; i++) {
            BigDecimal close = candles.get(i).getClose();
            result[i] = close.multiply(multiplier)
                    .add(result[i - 1].multiply(
                            BigDecimal.ONE.subtract(multiplier)))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        return result;
    }

    /**
     * Computes EMA of an array of BigDecimal values.
     * Seeded with SMA of the first `period` values.
     */
    private BigDecimal emaOfValues(BigDecimal[] values, int period) {

        BigDecimal multiplier =
                BigDecimal.valueOf(2.0 / (period + 1));

        // Seed with SMA of first `period` values
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(values[i]);
        }
        BigDecimal ema =
                sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);

        for (int i = period; i < values.length; i++) {
            ema = values[i].multiply(multiplier)
                    .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        return ema;
    }
}
