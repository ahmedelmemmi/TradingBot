package com.tradingbot.trading.Bot.risk;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * External configuration for the Binary Options risk engine.
 */
@Component
@ConfigurationProperties(prefix = "trading.risk")
@Getter @Setter
public class BinaryOptionsRiskConfig {

    /** Maximum loss as fraction of starting balance (default 2%). */
    private double dailyLossLimitPct = 0.02;

    /** Stop trading after this many consecutive losses. */
    private int maxConsecutiveLosses = 3;

    /** Optional: stop trading after daily profit reaches this fraction. */
    private double dailyProfitTargetPct = 0.05;

    /** Maximum drawdown from peak balance before halting (default 10%). */
    private double maxDrawdownPct = 0.10;
}
