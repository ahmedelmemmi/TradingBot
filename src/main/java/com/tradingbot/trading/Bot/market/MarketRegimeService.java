package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketRegimeService {
    public enum MarketRegime {
        STRONG_UPTREND,
        STRONG_DOWNTREND,
        SIDEWAYS,
        HIGH_VOLATILITY,
        CRASH
    }

    private static final int REGIME_PERSISTENCE_COUNT = 3;

    private MarketRegime lastConfirmedRegime = MarketRegime.SIDEWAYS;
    private MarketRegime candidateRegime = MarketRegime.SIDEWAYS;
    private int candidateCount = 0;

    public MarketRegime detect(List<Candle> candles) {

        if (candles.size() < 60) {
            return MarketRegime.SIDEWAYS;
        }

        BigDecimal ma20 = movingAverage(candles, 20);
        BigDecimal ma50 = movingAverage(candles, 50);

        BigDecimal slope =
                ma20.subtract(ma50)
                        .divide(ma50, 6, RoundingMode.HALF_UP);

        BigDecimal volatility = averageRange(candles, 20);
        BigDecimal momentum = momentum(candles, 10);
        BigDecimal drawdownSpeed = drawdownSpeed(candles, 10);

        MarketRegime rawRegime = classifyRegime(
                slope, volatility, momentum, drawdownSpeed);

        return applyPersistence(rawRegime);
    }

    private MarketRegime classifyRegime(BigDecimal slope,
                                         BigDecimal volatility,
                                         BigDecimal momentum,
                                         BigDecimal drawdownSpeed) {

        if (drawdownSpeed.compareTo(BigDecimal.valueOf(0.15)) > 0) {
            return MarketRegime.CRASH;
        }

        if (volatility.compareTo(BigDecimal.valueOf(0.025)) > 0) {
            return MarketRegime.HIGH_VOLATILITY;
        }

        if (slope.compareTo(BigDecimal.valueOf(0.02)) > 0 &&
                momentum.compareTo(BigDecimal.ZERO) > 0) {
            return MarketRegime.STRONG_UPTREND;
        }

        if (slope.compareTo(BigDecimal.valueOf(-0.02)) < 0 &&
                momentum.compareTo(BigDecimal.ZERO) < 0) {
            return MarketRegime.STRONG_DOWNTREND;
        }

        return MarketRegime.SIDEWAYS;
    }

    private MarketRegime applyPersistence(MarketRegime rawRegime) {

        if (rawRegime == MarketRegime.CRASH) {
            lastConfirmedRegime = MarketRegime.CRASH;
            candidateRegime = MarketRegime.CRASH;
            candidateCount = REGIME_PERSISTENCE_COUNT;
            return MarketRegime.CRASH;
        }

        if (rawRegime == candidateRegime) {
            candidateCount++;
        } else {
            candidateRegime = rawRegime;
            candidateCount = 1;
        }

        if (candidateCount >= REGIME_PERSISTENCE_COUNT) {
            lastConfirmedRegime = candidateRegime;
        }

        return lastConfirmedRegime;
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal averageRange(List<Candle> candles, int period) {

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {

            BigDecimal range =
                    candles.get(i).getHigh()
                            .subtract(candles.get(i).getLow())
                            .divide(candles.get(i).getClose(), 6, RoundingMode.HALF_UP);

            sum = sum.add(range);
        }

        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal momentum(List<Candle> candles, int period) {

        BigDecimal last =
                candles.get(candles.size() - 1).getClose();

        BigDecimal past =
                candles.get(candles.size() - period).getClose();

        return last.subtract(past)
                .divide(past, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal drawdownSpeed(List<Candle> candles, int period) {

        BigDecimal peak = candles.get(candles.size() - period).getClose();
        for (int i = candles.size() - period; i < candles.size(); i++) {
            BigDecimal price = candles.get(i).getClose();
            if (price.compareTo(peak) > 0) {
                peak = price;
            }
        }

        BigDecimal last = candles.get(candles.size() - 1).getClose();

        BigDecimal dd = peak.subtract(last)
                .divide(peak, 6, RoundingMode.HALF_UP);

        return dd.max(BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> debugMetrics(List<Candle> candles) {
        return calculateMetrics(candles);
    }

    private Map<String, BigDecimal> calculateMetrics(List<Candle> candles) {

        Map<String, BigDecimal> map = new HashMap<>();

        int n = candles.size();

        BigDecimal first =
                candles.get(n - 50).getClose();

        BigDecimal last =
                candles.get(n - 1).getClose();

        BigDecimal slope =
                last.subtract(first)
                        .divide(first, 6, RoundingMode.HALF_UP);

        BigDecimal volatility = calculateVolatility(candles);

        map.put("slope", slope);
        map.put("volatility", volatility);
        map.put("lastPrice", last);

        return map;
    }

    private BigDecimal calculateVolatility(List<Candle> candles) {

        BigDecimal sum = BigDecimal.ZERO;

        int start = candles.size() - 50;

        for (int i = start + 1; i < candles.size(); i++) {

            BigDecimal prev = candles.get(i - 1).getClose();
            BigDecimal curr = candles.get(i).getClose();

            BigDecimal ret =
                    curr.subtract(prev)
                            .divide(prev, 6, RoundingMode.HALF_UP)
                            .abs();

            sum = sum.add(ret);
        }

        return sum.divide(BigDecimal.valueOf(50), 6, RoundingMode.HALF_UP);
    }
}
