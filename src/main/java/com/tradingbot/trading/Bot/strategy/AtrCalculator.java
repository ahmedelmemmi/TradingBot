package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class AtrCalculator {
    public BigDecimal calculate(List<Candle> candles, int period) {

        if (candles.size() < period + 1) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {

            BigDecimal high = candles.get(i).getHigh();
            BigDecimal low = candles.get(i).getLow();
            BigDecimal prevClose = candles.get(i - 1).getClose();

            BigDecimal tr1 = high.subtract(low).abs();
            BigDecimal tr2 = high.subtract(prevClose).abs();
            BigDecimal tr3 = low.subtract(prevClose).abs();

            BigDecimal tr =
                    tr1.max(tr2).max(tr3);

            sum = sum.add(tr);
        }

        return sum.divide(
                BigDecimal.valueOf(period),
                6,
                RoundingMode.HALF_UP
        );
    }
}
