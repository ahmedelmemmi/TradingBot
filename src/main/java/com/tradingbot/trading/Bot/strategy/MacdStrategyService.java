package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * MACD + RSI Confluence Strategy.
 *
 * BUY conditions (all must be true):
 *  1. MACD histogram turns positive (bullish crossover)
 *  2. RSI is between 40 and 60 (momentum confirmation, not over-extended)
 *  3. Price is above the 50-period moving average (trend filter)
 */
@Service
public class MacdStrategyService implements Strategy {

    private final MacdCalculator macdCalculator;
    private final RsiCalculator  rsiCalculator;

    private static final int    MIN_CANDLES = 60;
    private static final int    MA_PERIOD   = 50;

    public MacdStrategyService(MacdCalculator macdCalculator,
                               RsiCalculator rsiCalculator) {
        this.macdCalculator = macdCalculator;
        this.rsiCalculator  = rsiCalculator;
    }

    @Override
    public String getName() {
        return "MACD_RSI_CONFLUENCE";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < MIN_CANDLES) {
            return TradingSignal.HOLD;
        }

        MacdCalculator.MacdResult macd = macdCalculator.calculate(candles);
        BigDecimal rsi  = rsiCalculator.calculate(candles);
        BigDecimal ma50 = movingAverage(candles, MA_PERIOD);

        BigDecimal lastPrice =
                candles.get(candles.size() - 1).getClose();

        // 1. MACD histogram positive → bullish momentum
        boolean macdBullish =
                macd.getHistogram().compareTo(BigDecimal.ZERO) > 0;

        // 2. RSI in confirmation zone (not oversold and not overbought)
        boolean rsiConfirmed =
                rsi.compareTo(BigDecimal.valueOf(40)) > 0 &&
                rsi.compareTo(BigDecimal.valueOf(60)) < 0;

        // 3. Price above 50-MA → trend is up
        boolean aboveTrend =
                lastPrice.compareTo(ma50) > 0;

        if (macdBullish && rsiConfirmed && aboveTrend) {
            System.out.println("MACD BUY SIGNAL – histogram=" + macd.getHistogram()
                    + " rsi=" + rsi + " price=" + lastPrice + " ma50=" + ma50);
            return TradingSignal.BUY;
        }

        return TradingSignal.HOLD;
    }

    private BigDecimal movingAverage(List<Candle> candles, int period) {

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }
}
