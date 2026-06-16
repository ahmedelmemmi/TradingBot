package com.tradingbot.trading.Bot.signal;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Result emitted by the {@link BinaryOptionsRuleEngine} after evaluating all
 * configurable technical rules against a set of indicators.
 */
@Getter
@Builder
public class RuleEngineResult {

    public enum Direction { BUY, SELL, NONE }

    private final Direction direction;

    /** Fraction of rules that passed [0.0 – 1.0]. */
    private final BigDecimal ruleScore;

    /** Short human-readable explanation of why the signal was / was not generated. */
    private final String reason;

    /** Pattern detected on the last candle (e.g. BULLISH_ENGULFING). */
    private final String candlePattern;

    public static RuleEngineResult none(String reason) {
        return RuleEngineResult.builder()
                .direction(Direction.NONE)
                .ruleScore(BigDecimal.ZERO)
                .reason(reason)
                .candlePattern("NONE")
                .build();
    }
}
