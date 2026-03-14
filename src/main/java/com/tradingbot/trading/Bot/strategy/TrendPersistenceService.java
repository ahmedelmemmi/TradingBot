package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Validates that a trend is genuine by requiring the MA20 > MA50 relationship
 * to hold for a minimum number of consecutive bars before a BUY signal is
 * accepted.  This filters out false signals triggered by single-candle noise.
 *
 * <p>Recommended minimum persistence: 3 bars (configurable per call).</p>
 */
@Service
public class TrendPersistenceService {

    /**
     * Returns true when MA20 > MA50 for every one of the last {@code persistenceBars}
     * candle windows — confirming a genuine uptrend.
     *
     * @param candles          full candle list
     * @param persistenceBars  number of consecutive bars MA20 must remain above MA50
     * @return true if trend is persistent
     */
    public boolean isUptrendPersistent(List<Candle> candles, int persistenceBars) {

        if (candles.size() < 50 + persistenceBars) {
            return false;
        }

        for (int offset = 0; offset < persistenceBars; offset++) {
            int end = candles.size() - offset;
            List<Candle> window = candles.subList(0, end);

            BigDecimal ma20 = movingAverage(window, 20);
            BigDecimal ma50 = movingAverage(window, 50);

            if (ma20.compareTo(ma50) <= 0) {
                System.out.println("[TrendPersistenceService] Uptrend NOT persistent at offset="
                        + offset + " ma20=" + ma20 + " ma50=" + ma50);
                return false;
            }
        }

        System.out.println("[TrendPersistenceService] Uptrend confirmed for " + persistenceBars + " bars");
        return true;
    }

    /**
     * Returns true when MA20 < MA50 for every one of the last {@code persistenceBars}
     * candle windows — confirming a genuine downtrend.
     *
     * @param candles          full candle list
     * @param persistenceBars  number of consecutive bars MA20 must remain below MA50
     * @return true if downtrend is persistent
     */
    public boolean isDowntrendPersistent(List<Candle> candles, int persistenceBars) {

        if (candles.size() < 50 + persistenceBars) {
            return false;
        }

        for (int offset = 0; offset < persistenceBars; offset++) {
            int end = candles.size() - offset;
            List<Candle> window = candles.subList(0, end);

            BigDecimal ma20 = movingAverage(window, 20);
            BigDecimal ma50 = movingAverage(window, 50);

            if (ma20.compareTo(ma50) >= 0) {
                System.out.println("[TrendPersistenceService] Downtrend NOT persistent at offset="
                        + offset + " ma20=" + ma20 + " ma50=" + ma50);
                return false;
            }
        }

        System.out.println("[TrendPersistenceService] Downtrend confirmed for " + persistenceBars + " bars");
        return true;
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {
        if (candles.size() < period) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }
}
