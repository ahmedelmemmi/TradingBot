package com.tradingbot.trading.Bot.signal;

import com.tradingbot.trading.Bot.analysis.IndicatorSnapshot;
import com.tradingbot.trading.Bot.analysis.PriceActionDetector;
import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configurable rule-based engine that checks predefined BUY / SELL conditions
 * for binary options trading.
 *
 * <p><b>BUY rules</b></p>
 * <ol>
 *   <li>EMA50 &gt; EMA200 (uptrend)</li>
 *   <li>RSI &gt; {@code trading.rules.buy-rsi-min} (default 55)</li>
 *   <li>MACD histogram positive (bullish momentum)</li>
 *   <li>ADX &gt; {@code trading.rules.adx-min} (trend strength)</li>
 *   <li>Bullish candle confirmation</li>
 * </ol>
 *
 * <p><b>SELL rules</b></p>
 * <ol>
 *   <li>EMA50 &lt; EMA200 (downtrend)</li>
 *   <li>RSI &lt; {@code trading.rules.sell-rsi-max} (default 45)</li>
 *   <li>MACD histogram negative (bearish momentum)</li>
 *   <li>ADX &gt; {@code trading.rules.adx-min}</li>
 *   <li>Bearish candle confirmation</li>
 * </ol>
 */
@Component
public class BinaryOptionsRuleEngine {

    private final RuleEngineConfig config;

    public BinaryOptionsRuleEngine(RuleEngineConfig config) {
        this.config = config;
    }

    public RuleEngineResult evaluate(List<Candle> candles, IndicatorSnapshot ind) {

        if (candles.isEmpty()) {
            return RuleEngineResult.none("No candle data");
        }

        Candle last = candles.get(candles.size() - 1);
        String pattern = ind.getCandlePattern();

        // ── BUY rules ──────────────────────────────────────────────────────
        int buyPassed = 0;
        if (ind.getEma50() != null && ind.getEma200() != null
                && ind.getEma50().compareTo(ind.getEma200()) > 0) buyPassed++;

        if (ind.getRsi14() != null
                && ind.getRsi14().compareTo(config.getBuyRsiMin()) > 0) buyPassed++;

        if (ind.getMacdHistogram() != null
                && ind.getMacdHistogram().compareTo(BigDecimal.ZERO) > 0) buyPassed++;

        if (ind.getAdx14() != null
                && ind.getAdx14().compareTo(config.getAdxMin()) > 0) buyPassed++;

        if (PriceActionDetector.isBullishConfirmation(pattern, last)) buyPassed++;

        // ── SELL rules ─────────────────────────────────────────────────────
        int sellPassed = 0;
        if (ind.getEma50() != null && ind.getEma200() != null
                && ind.getEma50().compareTo(ind.getEma200()) < 0) sellPassed++;

        if (ind.getRsi14() != null
                && ind.getRsi14().compareTo(config.getSellRsiMax()) < 0) sellPassed++;

        if (ind.getMacdHistogram() != null
                && ind.getMacdHistogram().compareTo(BigDecimal.ZERO) < 0) sellPassed++;

        if (ind.getAdx14() != null
                && ind.getAdx14().compareTo(config.getAdxMin()) > 0) sellPassed++;

        if (PriceActionDetector.isBearishConfirmation(pattern, last)) sellPassed++;

        // ── Require ALL 5 rules to pass ────────────────────────────────────
        if (buyPassed == 5) {
            return RuleEngineResult.builder()
                    .direction(RuleEngineResult.Direction.BUY)
                    .ruleScore(BigDecimal.ONE)
                    .reason("All 5 BUY rules passed")
                    .candlePattern(pattern)
                    .build();
        }

        if (sellPassed == 5) {
            return RuleEngineResult.builder()
                    .direction(RuleEngineResult.Direction.SELL)
                    .ruleScore(BigDecimal.ONE)
                    .reason("All 5 SELL rules passed")
                    .candlePattern(pattern)
                    .build();
        }

        String reason = String.format("BUY rules passed: %d/5, SELL rules passed: %d/5",
                buyPassed, sellPassed);
        return RuleEngineResult.none(reason);
    }
}
