package com.tradingbot.trading.Bot.analysis;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless utility that computes all technical indicators from a list of candles.
 *
 * <p>All indicators are computed using pure-Java arithmetic (no third-party TA library
 * is required) so the module stays lightweight. Calculations follow standard textbook
 * definitions and are intentionally kept readable.</p>
 */
@Component
public class TechnicalAnalysisEngine {

    private static final int SCALE = 6;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final MathContext MC = new MathContext(18, RM);

    // ── EMA ─────────────────────────────────────────────────────────────────

    /**
     * Exponential Moving Average over the last {@code period} closes.
     * Uses SMA of the first {@code period} bars as seed, then EMA from there.
     */
    public BigDecimal ema(List<Candle> candles, int period) {
        if (candles.size() < period) return BigDecimal.ZERO;

        // seed = SMA(period)
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), SCALE, RM);

        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));

        for (int i = period; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).getClose();
            ema = close.subtract(ema).multiply(multiplier).add(ema).setScale(SCALE, RM);
        }
        return ema;
    }

    // ── RSI ─────────────────────────────────────────────────────────────────

    /** Relative Strength Index (Wilder smoothing, period = 14). */
    public BigDecimal rsi(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return BigDecimal.valueOf(50);

        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            BigDecimal diff = candles.get(i).getClose().subtract(candles.get(i - 1).getClose());
            if (diff.compareTo(BigDecimal.ZERO) >= 0) {
                gains.add(diff);
                losses.add(BigDecimal.ZERO);
            } else {
                gains.add(BigDecimal.ZERO);
                losses.add(diff.abs());
            }
        }

        // Initial average gain/loss (SMA over first period)
        BigDecimal avgGain = average(gains.subList(0, period));
        BigDecimal avgLoss = average(losses.subList(0, period));

        // Wilder smoothing
        for (int i = period; i < gains.size(); i++) {
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1))
                    .add(gains.get(i))
                    .divide(BigDecimal.valueOf(period), SCALE, RM);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1))
                    .add(losses.get(i))
                    .divide(BigDecimal.valueOf(period), SCALE, RM);
        }

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(100);

        BigDecimal rs = avgGain.divide(avgLoss, SCALE, RM);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), SCALE, RM))
                .setScale(4, RM);
    }

    // ── MACD ────────────────────────────────────────────────────────────────

    /** MACD line = EMA(12) − EMA(26). */
    public BigDecimal macdLine(List<Candle> candles) {
        return ema(candles, 12).subtract(ema(candles, 26)).setScale(SCALE, RM);
    }

    /**
     * MACD Signal = 9-period EMA of the MACD line values.
     * Builds the MACD line series from a rolling window.
     */
    public BigDecimal macdSignal(List<Candle> candles) {
        if (candles.size() < 35) return BigDecimal.ZERO;  // 26 + 9

        List<BigDecimal> macdSeries = new ArrayList<>();
        for (int i = 26; i <= candles.size(); i++) {
            macdSeries.add(macdLine(candles.subList(0, i)));
        }

        // EMA(9) of the MACD series
        BigDecimal seed = average(macdSeries.subList(0, 9));
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / 10);
        BigDecimal sig = seed;
        for (int i = 9; i < macdSeries.size(); i++) {
            sig = macdSeries.get(i).subtract(sig).multiply(multiplier).add(sig).setScale(SCALE, RM);
        }
        return sig;
    }

    /** MACD Histogram = MACD line − Signal. */
    public BigDecimal macdHistogram(List<Candle> candles) {
        return macdLine(candles).subtract(macdSignal(candles)).setScale(SCALE, RM);
    }

    // ── Stochastic RSI ──────────────────────────────────────────────────────

    /**
     * Stochastic RSI.
     * StochRSI = (RSI − min(RSI, n)) / (max(RSI, n) − min(RSI, n))
     * %K = 3-period SMA of StochRSI, %D = 3-period SMA of %K.
     *
     * @return two-element array: [%K, %D]
     */
    public BigDecimal[] stochRsi(List<Candle> candles, int rsiPeriod, int stochPeriod) {
        if (candles.size() < rsiPeriod + stochPeriod + 3) {
            return new BigDecimal[]{BigDecimal.valueOf(50), BigDecimal.valueOf(50)};
        }

        // Build RSI series
        List<BigDecimal> rsiValues = new ArrayList<>();
        for (int i = rsiPeriod; i <= candles.size(); i++) {
            rsiValues.add(rsi(candles.subList(0, i), rsiPeriod));
        }

        // Stoch RSI raw values
        List<BigDecimal> stochRaw = new ArrayList<>();
        for (int i = stochPeriod - 1; i < rsiValues.size(); i++) {
            List<BigDecimal> window = rsiValues.subList(i - stochPeriod + 1, i + 1);
            BigDecimal minRsi = window.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal maxRsi = window.stream().max(BigDecimal::compareTo).orElse(BigDecimal.valueOf(100));
            BigDecimal range = maxRsi.subtract(minRsi);
            if (range.compareTo(BigDecimal.ZERO) == 0) {
                stochRaw.add(BigDecimal.ZERO);
            } else {
                stochRaw.add(rsiValues.get(i).subtract(minRsi)
                        .divide(range, SCALE, RM)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }

        if (stochRaw.size() < 3) {
            return new BigDecimal[]{BigDecimal.valueOf(50), BigDecimal.valueOf(50)};
        }

        // %K = SMA(3) of stochRaw
        BigDecimal kValue = average(stochRaw.subList(stochRaw.size() - 3, stochRaw.size()));

        // %D = SMA(3) of the last 3 %K values (simplified: use last stoch values)
        BigDecimal dValue = average(stochRaw.subList(Math.max(0, stochRaw.size() - 3), stochRaw.size()));

        return new BigDecimal[]{kValue.setScale(4, RM), dValue.setScale(4, RM)};
    }

    // ── ATR ─────────────────────────────────────────────────────────────────

    /** Average True Range (Wilder, period = 14). */
    public BigDecimal atr(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return BigDecimal.ZERO;

        List<BigDecimal> trValues = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            trValues.add(trueRange(candles.get(i), candles.get(i - 1)));
        }

        BigDecimal avgTr = average(trValues.subList(0, period));
        for (int i = period; i < trValues.size(); i++) {
            avgTr = avgTr.multiply(BigDecimal.valueOf(period - 1))
                    .add(trValues.get(i))
                    .divide(BigDecimal.valueOf(period), SCALE, RM);
        }
        return avgTr;
    }

    private BigDecimal trueRange(Candle curr, Candle prev) {
        BigDecimal hl  = curr.getHigh().subtract(curr.getLow()).abs();
        BigDecimal hpc = curr.getHigh().subtract(prev.getClose()).abs();
        BigDecimal lpc = curr.getLow().subtract(prev.getClose()).abs();
        return hl.max(hpc).max(lpc);
    }

    // ── Bollinger Bands ──────────────────────────────────────────────────────

    /**
     * Bollinger Bands (SMA ± 2σ, 20-period).
     *
     * @return [upper, middle, lower]
     */
    public BigDecimal[] bollingerBands(List<Candle> candles, int period, double multiplier) {
        if (candles.size() < period) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }

        List<Candle> window = candles.subList(candles.size() - period, candles.size());
        BigDecimal sma = average(window.stream().map(Candle::getClose).toList());

        // Standard deviation
        BigDecimal variance = BigDecimal.ZERO;
        for (Candle c : window) {
            BigDecimal diff = c.getClose().subtract(sma);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(period), SCALE, RM);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(SCALE, RM);

        BigDecimal band = stdDev.multiply(BigDecimal.valueOf(multiplier));
        return new BigDecimal[]{
                sma.add(band).setScale(SCALE, RM),
                sma.setScale(SCALE, RM),
                sma.subtract(band).setScale(SCALE, RM)
        };
    }

    // ── ADX / DI ─────────────────────────────────────────────────────────────

    /**
     * ADX, +DI, and –DI (Wilder, period = 14).
     *
     * @return [ADX, +DI, -DI]
     */
    public BigDecimal[] adx(List<Candle> candles, int period) {
        if (candles.size() < period * 2 + 1) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }

        List<BigDecimal> trList  = new ArrayList<>();
        List<BigDecimal> dmPlus  = new ArrayList<>();
        List<BigDecimal> dmMinus = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            BigDecimal tr = trueRange(curr, prev);
            trList.add(tr);

            BigDecimal upMove   = curr.getHigh().subtract(prev.getHigh());
            BigDecimal downMove = prev.getLow().subtract(curr.getLow());

            if (upMove.compareTo(downMove) > 0 && upMove.compareTo(BigDecimal.ZERO) > 0) {
                dmPlus.add(upMove);
            } else {
                dmPlus.add(BigDecimal.ZERO);
            }

            if (downMove.compareTo(upMove) > 0 && downMove.compareTo(BigDecimal.ZERO) > 0) {
                dmMinus.add(downMove);
            } else {
                dmMinus.add(BigDecimal.ZERO);
            }
        }

        // Wilder smoothed TR, +DM, -DM
        BigDecimal smoothTr    = sum(trList.subList(0, period));
        BigDecimal smoothPlus  = sum(dmPlus.subList(0, period));
        BigDecimal smoothMinus = sum(dmMinus.subList(0, period));

        List<BigDecimal> dxList = new ArrayList<>();

        for (int i = period; i < trList.size(); i++) {
            smoothTr    = smoothTr.subtract(smoothTr.divide(BigDecimal.valueOf(period), SCALE, RM))
                    .add(trList.get(i));
            smoothPlus  = smoothPlus.subtract(smoothPlus.divide(BigDecimal.valueOf(period), SCALE, RM))
                    .add(dmPlus.get(i));
            smoothMinus = smoothMinus.subtract(smoothMinus.divide(BigDecimal.valueOf(period), SCALE, RM))
                    .add(dmMinus.get(i));

            if (smoothTr.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal diP = smoothPlus.divide(smoothTr, SCALE, RM).multiply(BigDecimal.valueOf(100));
            BigDecimal diM = smoothMinus.divide(smoothTr, SCALE, RM).multiply(BigDecimal.valueOf(100));

            BigDecimal diSum  = diP.add(diM);
            BigDecimal diDiff = diP.subtract(diM).abs();

            if (diSum.compareTo(BigDecimal.ZERO) == 0) {
                dxList.add(BigDecimal.ZERO);
            } else {
                dxList.add(diDiff.divide(diSum, SCALE, RM).multiply(BigDecimal.valueOf(100)));
            }
        }

        if (dxList.size() < period) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }

        // ADX = Wilder SMA(DX, period)
        BigDecimal adxVal = average(dxList.subList(0, period));
        for (int i = period; i < dxList.size(); i++) {
            adxVal = adxVal.multiply(BigDecimal.valueOf(period - 1))
                    .add(dxList.get(i))
                    .divide(BigDecimal.valueOf(period), SCALE, RM);
        }

        // Final +DI / -DI from the last bar
        BigDecimal finalTr    = trList.get(trList.size() - 1);
        BigDecimal finalDiP   = BigDecimal.ZERO;
        BigDecimal finalDiM   = BigDecimal.ZERO;
        if (smoothTr.compareTo(BigDecimal.ZERO) != 0) {
            finalDiP = smoothPlus.divide(smoothTr, SCALE, RM).multiply(BigDecimal.valueOf(100));
            finalDiM = smoothMinus.divide(smoothTr, SCALE, RM).multiply(BigDecimal.valueOf(100));
        }

        return new BigDecimal[]{
                adxVal.setScale(4, RM),
                finalDiP.setScale(4, RM),
                finalDiM.setScale(4, RM)
        };
    }

    // ── Volume ──────────────────────────────────────────────────────────────

    public BigDecimal volumeMa(List<Candle> candles, int period) {
        if (candles.size() < period) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            total = total.add(BigDecimal.valueOf(candles.get(i).getVolume()));
        }
        return total.divide(BigDecimal.valueOf(period), 2, RM);
    }

    public BigDecimal relativeVolume(List<Candle> candles, int period) {
        BigDecimal vma = volumeMa(candles, period);
        if (vma.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE;
        BigDecimal currentVol = BigDecimal.valueOf(candles.get(candles.size() - 1).getVolume());
        return currentVol.divide(vma, 4, RM);
    }

    // ── Convenience ─────────────────────────────────────────────────────────

    /**
     * Compute the full {@link IndicatorSnapshot} for the given candle list.
     * Requires at least 200+ candles for all indicators to be meaningful.
     */
    public IndicatorSnapshot compute(List<Candle> candles) {
        BigDecimal[] bb  = bollingerBands(candles, 20, 2.0);
        BigDecimal[] stoch = stochRsi(candles, 14, 14);
        BigDecimal[] adxArr = adx(candles, 14);
        BigDecimal[] macdArr = new BigDecimal[]{
                macdLine(candles),
                macdSignal(candles),
                macdHistogram(candles)
        };

        String pattern = PriceActionDetector.detectPattern(candles);

        return IndicatorSnapshot.builder()
                .ema20(ema(candles, 20))
                .ema50(ema(candles, 50))
                .ema100(ema(candles, 100))
                .ema200(ema(candles, 200))
                .rsi14(rsi(candles, 14))
                .macdLine(macdArr[0])
                .macdSignal(macdArr[1])
                .macdHistogram(macdArr[2])
                .stochRsiK(stoch[0])
                .stochRsiD(stoch[1])
                .atr14(atr(candles, 14))
                .bbUpper(bb[0])
                .bbMiddle(bb[1])
                .bbLower(bb[2])
                .adx14(adxArr[0])
                .diPlus(adxArr[1])
                .diMinus(adxArr[2])
                .volumeMa20(volumeMa(candles, 20))
                .relativeVolume(relativeVolume(candles, 20))
                .candlePattern(pattern)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        return sum(values).divide(BigDecimal.valueOf(values.size()), SCALE, RM);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
