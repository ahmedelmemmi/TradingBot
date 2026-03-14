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

        if (candles.size() < 50) {
            return TradingSignal.HOLD;
        }

        MarketRegime regime =
                regimeService.detect(candles);

        BigDecimal rsi = rsiCalculator.calculate(candles);
        BigDecimal ma20 = movingAverage(candles, 20);

        BigDecimal lastPrice =
                candles.get(candles.size() - 1).getClose();

        switch (regime) {

            case STRONG_UPTREND:
                if (rsi.compareTo(BigDecimal.valueOf(45)) < 0 &&
                        lastPrice.compareTo(ma20) > 0)
                    return TradingSignal.BUY;
                break;

            case SIDEWAYS:
                if (rsi.compareTo(BigDecimal.valueOf(30)) < 0)
                    return TradingSignal.BUY;
                break;

            case HIGH_VOLATILITY:
                if (rsi.compareTo(BigDecimal.valueOf(25)) < 0 &&
                        lastPrice.compareTo(ma20) > 0)
                    return TradingSignal.BUY;
                break;

            case CRASH:
                if (rsi.compareTo(BigDecimal.valueOf(20)) < 0 &&
                        lastPrice.compareTo(ma20) > 0)
                    return TradingSignal.BUY;
                break;

            case STRONG_DOWNTREND:
                return TradingSignal.HOLD;
        }

        return TradingSignal.HOLD;
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
}