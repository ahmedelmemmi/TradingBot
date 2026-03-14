package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RsiCalculator {
    private static final int PERIOD = 14;

    public BigDecimal calculate(List<Candle> candles) {

        for (Candle candle : candles) {
            if (candle.getClose() == null) {
                throw new IllegalStateException("Candle close is null");
            }
        }

        if (candles.size() <= PERIOD) {
            throw new IllegalArgumentException("Not enough candles to calculate RSI");
        }

        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;

        int end   = candles.size() - 1;
        int start = end - PERIOD;

        for (int i = start + 1; i <= end; i++) {
            BigDecimal change = candles.get(i).getClose()
                    .subtract(candles.get(i - 1).getClose());

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gain = gain.add(change);
            } else {
                loss = loss.add(change.abs());
            }
        }

        BigDecimal avgGain = gain.divide(BigDecimal.valueOf(PERIOD), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = loss.divide(BigDecimal.valueOf(PERIOD), 4, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);

        return BigDecimal.valueOf(100)
                .subtract(
                        BigDecimal.valueOf(100)
                                .divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP)
                );
    }

}
