package com.tradingbot.trading.Bot.analysis;

import com.tradingbot.trading.Bot.domain.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalAnalysisEngineTest {

    private TechnicalAnalysisEngine engine;
    private List<Candle> uptrend;

    @BeforeEach
    void setUp() {
        engine = new TechnicalAnalysisEngine();
        uptrend = generateCandles(300, 0.001);
    }

    @Test
    void ema_returnsIncreasingValueForUptrend() {
        BigDecimal ema20  = engine.ema(uptrend, 20);
        BigDecimal ema200 = engine.ema(uptrend, 200);
        assertThat(ema20).isGreaterThan(ema200);
    }

    @Test
    void rsi_above50ForUptrend() {
        BigDecimal rsi = engine.rsi(uptrend, 14);
        assertThat(rsi.doubleValue()).isGreaterThan(50.0);
    }

    @Test
    void rsi_below50ForDowntrend() {
        List<Candle> downtrend = generateCandles(300, -0.001);
        BigDecimal rsi = engine.rsi(downtrend, 14);
        assertThat(rsi.doubleValue()).isLessThan(50.0);
    }

    @Test
    void macdLine_positiveForUptrend() {
        BigDecimal macd = engine.macdLine(uptrend);
        assertThat(macd.doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void atr_isPositive() {
        BigDecimal atr = engine.atr(uptrend, 14);
        assertThat(atr.doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void bollingerBands_upperAboveMiddleAboveLower() {
        BigDecimal[] bb = engine.bollingerBands(uptrend, 20, 2.0);
        assertThat(bb[0].doubleValue()).isGreaterThan(bb[1].doubleValue());
        assertThat(bb[1].doubleValue()).isGreaterThan(bb[2].doubleValue());
    }

    @Test
    void adx_isNonNegative() {
        BigDecimal[] adxArr = engine.adx(uptrend, 14);
        assertThat(adxArr[0].doubleValue()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void compute_returnsFullSnapshot() {
        IndicatorSnapshot snap = engine.compute(uptrend);
        assertThat(snap.getEma20()).isNotNull();
        assertThat(snap.getEma50()).isNotNull();
        assertThat(snap.getRsi14()).isNotNull();
        assertThat(snap.getMacdLine()).isNotNull();
        assertThat(snap.getBbUpper()).isNotNull();
    }

    private static List<Candle> generateCandles(int n, double dailyMove) {
        List<Candle> candles = new ArrayList<>();
        BigDecimal price = BigDecimal.valueOf(100.0);
        LocalDateTime time = LocalDateTime.now().minusMinutes(n);

        for (int i = 0; i < n; i++) {
            BigDecimal newClose = price.multiply(BigDecimal.valueOf(1 + dailyMove));
            BigDecimal high     = newClose.multiply(BigDecimal.valueOf(1.002));
            BigDecimal low      = price.multiply(BigDecimal.valueOf(0.998));
            candles.add(new Candle("EURUSD", time.plusMinutes(i),
                    price, high, low, newClose, 100_000));
            price = newClose;
        }
        return candles;
    }
}
