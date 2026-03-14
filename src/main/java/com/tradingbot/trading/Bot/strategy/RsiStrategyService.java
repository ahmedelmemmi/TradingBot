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

        BigDecimal ma20 = ma(candles,20);
        BigDecimal ma50 = ma(candles,50);

        BigDecimal price =
                candles.get(candles.size()-1).getClose();

        // only trade strong structure
        if (ma20.compareTo(ma50) < 0) {
            return TradingSignal.HOLD;
        }

        // only buy pullback
        if (price.compareTo(ma20) < 0 &&
                rsi.compareTo(BigDecimal.valueOf(35)) > 0 &&
                rsi.compareTo(BigDecimal.valueOf(50)) < 0) {

            System.out.println("BUY TREND PULLBACK");
            return TradingSignal.BUY;
        }

        return TradingSignal.HOLD;
    }

    private BigDecimal ma(List<Candle> c,int p){
        BigDecimal s = BigDecimal.ZERO;
        for(int i=c.size()-p;i<c.size();i++){
            s=s.add(c.get(i).getClose());
        }
        return s.divide(BigDecimal.valueOf(p),6, RoundingMode.HALF_UP);
    }
}