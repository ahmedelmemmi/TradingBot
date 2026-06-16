package com.tradingbot.trading.Bot.signal;

import com.tradingbot.trading.Bot.analysis.IndicatorSnapshot;
import com.tradingbot.trading.Bot.analysis.TechnicalAnalysisEngine;
import com.tradingbot.trading.Bot.domain.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryOptionsRuleEngineTest {

    private BinaryOptionsRuleEngine ruleEngine;
    private TechnicalAnalysisEngine taEngine;

    @BeforeEach
    void setUp() {
        RuleEngineConfig config = new RuleEngineConfig();
        ruleEngine = new BinaryOptionsRuleEngine(config);
        taEngine   = new TechnicalAnalysisEngine();
    }

    @Test
    void noSignal_whenCandleListEmpty() {
        RuleEngineResult result = ruleEngine.evaluate(List.of(),
                IndicatorSnapshot.builder().build());
        assertThat(result.getDirection()).isEqualTo(RuleEngineResult.Direction.NONE);
    }

    @Test
    void buySignal_whenAllBuyConditionsMet() {
        IndicatorSnapshot ind = IndicatorSnapshot.builder()
                .ema50(BigDecimal.valueOf(1.2))
                .ema200(BigDecimal.valueOf(1.1))
                .rsi14(BigDecimal.valueOf(60))
                .macdHistogram(BigDecimal.valueOf(0.001))
                .adx14(BigDecimal.valueOf(30))
                .candlePattern("BULLISH_ENGULFING")
                .build();

        List<Candle> candles = bullishCandles(5);
        RuleEngineResult result = ruleEngine.evaluate(candles, ind);

        assertThat(result.getDirection()).isEqualTo(RuleEngineResult.Direction.BUY);
        assertThat(result.getRuleScore()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void sellSignal_whenAllSellConditionsMet() {
        IndicatorSnapshot ind = IndicatorSnapshot.builder()
                .ema50(BigDecimal.valueOf(1.0))
                .ema200(BigDecimal.valueOf(1.1))
                .rsi14(BigDecimal.valueOf(40))
                .macdHistogram(BigDecimal.valueOf(-0.001))
                .adx14(BigDecimal.valueOf(30))
                .candlePattern("BEARISH_ENGULFING")
                .build();

        List<Candle> candles = bearishCandles(5);
        RuleEngineResult result = ruleEngine.evaluate(candles, ind);

        assertThat(result.getDirection()).isEqualTo(RuleEngineResult.Direction.SELL);
    }

    @Test
    void noSignal_whenAdxBelowThreshold() {
        IndicatorSnapshot ind = IndicatorSnapshot.builder()
                .ema50(BigDecimal.valueOf(1.2))
                .ema200(BigDecimal.valueOf(1.1))
                .rsi14(BigDecimal.valueOf(60))
                .macdHistogram(BigDecimal.valueOf(0.001))
                .adx14(BigDecimal.valueOf(10))   // below 25
                .candlePattern("NONE")
                .build();

        List<Candle> candles = bullishCandles(5);
        RuleEngineResult result = ruleEngine.evaluate(candles, ind);

        assertThat(result.getDirection()).isEqualTo(RuleEngineResult.Direction.NONE);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Candle> bullishCandles(int n) {
        List<Candle> c = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            c.add(new Candle("EURUSD", LocalDateTime.now().minusMinutes(n - i),
                    BigDecimal.valueOf(1.0 + i * 0.001),
                    BigDecimal.valueOf(1.002 + i * 0.001),
                    BigDecimal.valueOf(0.999 + i * 0.001),
                    BigDecimal.valueOf(1.001 + i * 0.001),
                    100_000));
        }
        return c;
    }

    private List<Candle> bearishCandles(int n) {
        List<Candle> c = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            c.add(new Candle("EURUSD", LocalDateTime.now().minusMinutes(n - i),
                    BigDecimal.valueOf(1.0 - i * 0.001),
                    BigDecimal.valueOf(1.001 - i * 0.001),
                    BigDecimal.valueOf(0.998 - i * 0.001),
                    BigDecimal.valueOf(0.999 - i * 0.001),
                    100_000));
        }
        return c;
    }
}
