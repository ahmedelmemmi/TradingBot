package com.tradingbot.trading.Bot.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BinaryOptionsRiskEngineTest {

    private BinaryOptionsRiskEngine engine;

    @BeforeEach
    void setUp() {
        BinaryOptionsRiskConfig config = new BinaryOptionsRiskConfig();
        config.setDailyLossLimitPct(0.02);
        config.setMaxConsecutiveLosses(3);
        config.setDailyProfitTargetPct(0.05);
        config.setMaxDrawdownPct(0.10);

        engine = new BinaryOptionsRiskEngine(config, new SimpleMeterRegistry());
        engine.setStartingBalance(BigDecimal.valueOf(1000));
    }

    @Test
    void canTrade_initiallyTrue() {
        assertThat(engine.canTrade()).isTrue();
    }

    @Test
    void canTrade_falseAfterDailyLossLimit() {
        // Lose 2% of 1000 = 20
        engine.recordResult(BigDecimal.valueOf(-20));
        assertThat(engine.canTrade()).isFalse();
    }

    @Test
    void canTrade_falseAfterMaxConsecutiveLosses() {
        engine.recordResult(BigDecimal.valueOf(-5));
        engine.recordResult(BigDecimal.valueOf(-5));
        engine.recordResult(BigDecimal.valueOf(-5));
        assertThat(engine.canTrade()).isFalse();
    }

    @Test
    void consecutiveLossesResetAfterWin() {
        engine.recordResult(BigDecimal.valueOf(-5));
        engine.recordResult(BigDecimal.valueOf(-5));
        engine.recordResult(BigDecimal.valueOf(8));   // win resets streak
        assertThat(engine.getConsecutiveLosses()).isZero();
        assertThat(engine.canTrade()).isTrue();
    }

    @Test
    void canTrade_falseAfterDailyProfitTarget() {
        engine.recordResult(BigDecimal.valueOf(50));  // 5% of 1000
        assertThat(engine.canTrade()).isFalse();
    }

    @Test
    void noMartingale_engineHasNoStakeMultiplierMethod() throws Exception {
        // Verify the risk engine exposes no Martingale "next stake" logic
        long martingaleMethods = java.util.Arrays.stream(engine.getClass().getMethods())
                .filter(m -> m.getName().toLowerCase().contains("nextstake")
                          || m.getName().toLowerCase().contains("martingale"))
                .count();
        assertThat(martingaleMethods).isZero();
    }
}
