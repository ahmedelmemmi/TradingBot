package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
@Service
public class RsiStrategyService implements Strategy {

    private final RsiCalculator rsiCalculator;

    public RsiStrategyService(RsiCalculator rsiCalculator) {
        this.rsiCalculator = rsiCalculator;
    }

    @Override
    public String getName() {
        return "RSI Pullback Trend";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < 60) {
            return TradingSignal.HOLD;
        }

        BigDecimal rsi = rsiCalculator.calculate(candles);

        BigDecimal ma20 = ma(candles, 20);
        BigDecimal ma50 = ma(candles, 50);

        BigDecimal price =
                candles.get(candles.size() - 1).getClose();

        if (ma20.compareTo(ma50) < 0) {
            return TradingSignal.HOLD;
        }

        if (candles.size() >= 70) {
            BigDecimal maSlope = calculateMaSlope(candles, 20, 10);
            if (maSlope.compareTo(BigDecimal.ZERO) <= 0) {
                return TradingSignal.HOLD;
            }
        }

        if (price.compareTo(ma50) < 0) {
            return TradingSignal.HOLD;
        }

        if (price.compareTo(ma20) < 0 &&
                rsi.compareTo(BigDecimal.valueOf(30)) > 0 &&
                rsi.compareTo(BigDecimal.valueOf(45)) < 0) {

            return TradingSignal.BUY;
        }

        return TradingSignal.HOLD;
    }

    private BigDecimal calculateMaSlope(List<Candle> candles, int maPeriod, int lookback) {
        if (candles.size() <= lookback + maPeriod) return BigDecimal.ZERO;
        BigDecimal currentMa = ma(candles, maPeriod);
        List<Candle> pastCandles = candles.subList(0, candles.size() - lookback);
        BigDecimal pastMa = ma(pastCandles, maPeriod);
        if (pastMa.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return currentMa.subtract(pastMa)
                .divide(pastMa, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal ma(List<Candle> c, int p) {
        if (c.size() < p) return BigDecimal.ZERO;
        BigDecimal s = BigDecimal.ZERO;
        for (int i = c.size() - p; i < c.size(); i++) {
            s = s.add(c.get(i).getClose());
        }
        return s.divide(BigDecimal.valueOf(p), 6, RoundingMode.HALF_UP);
    }
}