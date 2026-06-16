package com.tradingbot.trading.Bot.signal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * All rule-engine thresholds are externalised here so they can be tuned
 * via {@code application.properties} without touching code.
 */
@Component
@ConfigurationProperties(prefix = "trading.rules")
@Getter @Setter
public class RuleEngineConfig {

    /** Minimum RSI for BUY signal (default 55). */
    private BigDecimal buyRsiMin = new BigDecimal("55");

    /** Maximum RSI for SELL signal (default 45). */
    private BigDecimal sellRsiMax = new BigDecimal("45");

    /** Minimum ADX to trade (trend must be strong, default 25). */
    private BigDecimal adxMin = new BigDecimal("25");

    /** Minimum ML confidence to execute (default 0.75). */
    private BigDecimal mlConfidenceThreshold = new BigDecimal("0.75");

    /** Binary options expiry in seconds (default 60). */
    private int expirySeconds = 60;

    /** Stake per trade (fixed amount, default 10.00). */
    private BigDecimal stakeAmount = new BigDecimal("10.00");

    /** Payout percent offered by broker (default 80). */
    private BigDecimal payoutPercent = new BigDecimal("80");
}
