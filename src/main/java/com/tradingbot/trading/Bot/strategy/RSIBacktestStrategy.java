package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class RSIBacktestStrategy implements Strategy {

    private final RsiCalculator rsiCalculator;
    private final MarketRegimeService regimeService;
    private final AtrCalculator atrCalculator = new AtrCalculator();

    public RSIBacktestStrategy(RsiCalculator rsiCalculator,
                               MarketRegimeService regimeService) {
        this.rsiCalculator = rsiCalculator;
        this.regimeService = regimeService;
    }

    @Override
    public String getName() {
        return "RSI_ADAPTIVE_BACKTEST";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < 60) {
            return TradingSignal.HOLD;
        }

        MarketRegime regime =
                regimeService.detect(candles);

        BigDecimal rsi = rsiCalculator.calculate(candles);
        BigDecimal ma20 = movingAverage(candles, 20);
        BigDecimal ma50 = movingAverage(candles, 50);

        BigDecimal lastPrice =
                candles.get(candles.size() - 1).getClose();

        if (!isTrendConfirmed(candles, ma20, ma50)) {
            return TradingSignal.HOLD;
        }

        BigDecimal atr = atrCalculator.calculate(candles, 14);
        if (atr.compareTo(BigDecimal.ZERO) <= 0) {
            return TradingSignal.HOLD;
        }

        BigDecimal pullbackDepth = ma20.subtract(lastPrice);

        switch (regime) {

            case STRONG_UPTREND:
                if (rsi.compareTo(BigDecimal.valueOf(35)) < 0 &&
                        rsi.compareTo(BigDecimal.valueOf(20)) > 0 &&
                        lastPrice.compareTo(ma50) > 0 &&
                        pullbackDepth.compareTo(BigDecimal.ZERO) > 0 &&
                        pullbackDepth.compareTo(atr.multiply(BigDecimal.valueOf(2))) < 0)
                    return TradingSignal.BUY;
                break;

            case SIDEWAYS:
                if (rsi.compareTo(BigDecimal.valueOf(25)) < 0 &&
                        lastPrice.compareTo(ma50) > 0)
                    return TradingSignal.BUY;
                break;

            case HIGH_VOLATILITY:
                if (rsi.compareTo(BigDecimal.valueOf(20)) < 0 &&
                        lastPrice.compareTo(ma20) > 0 &&
                        lastPrice.compareTo(ma50) > 0)
                    return TradingSignal.BUY;
                break;

            case CRASH:
            case STRONG_DOWNTREND:
                return TradingSignal.HOLD;
        }

        return TradingSignal.HOLD;
    }

    private boolean isTrendConfirmed(List<Candle> candles,
                                     BigDecimal ma20,
                                     BigDecimal ma50) {

        if (ma20.compareTo(ma50) <= 0) {
            return false;
        }

        if (candles.size() < 70) {
            return true;
        }

        BigDecimal maSlope = calculateMaSlope(candles, 20, 10);
        return maSlope.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal calculateMaSlope(List<Candle> candles, int maPeriod, int lookback) {

        if (candles.size() <= lookback + maPeriod) {
            return BigDecimal.ZERO;
        }

        BigDecimal currentMa = movingAverage(candles, maPeriod);

        List<Candle> pastCandles = candles.subList(0, candles.size() - lookback);
        BigDecimal pastMa = movingAverage(pastCandles, maPeriod);

        if (pastMa.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return currentMa.subtract(pastMa)
                .divide(pastMa, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {

        if (candles.size() < period) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
}