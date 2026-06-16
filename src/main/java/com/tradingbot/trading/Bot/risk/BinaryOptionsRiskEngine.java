package com.tradingbot.trading.Bot.risk;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Risk engine specifically designed for binary options trading.
 *
 * <p>Enforced protections:</p>
 * <ul>
 *   <li>Daily loss limit (configurable %).</li>
 *   <li>Maximum consecutive losses pause.</li>
 *   <li>Daily profit target (optional stop).</li>
 *   <li>Peak-to-trough drawdown limit.</li>
 *   <li>No Martingale – stake is always fixed.</li>
 * </ul>
 *
 * <p>All state is reset at the start of each new trading day.</p>
 */
@Service
public class BinaryOptionsRiskEngine {

    private static final Logger log = LoggerFactory.getLogger(BinaryOptionsRiskEngine.class);

    private final BinaryOptionsRiskConfig config;

    // Daily state
    private BigDecimal startingBalance    = BigDecimal.ZERO;
    private BigDecimal peakBalance        = BigDecimal.ZERO;
    private BigDecimal dailyPnl           = BigDecimal.ZERO;
    private int consecutiveLosses         = 0;
    private LocalDate currentDay          = LocalDate.now();

    public BinaryOptionsRiskEngine(BinaryOptionsRiskConfig config,
                                   MeterRegistry meterRegistry) {
        this.config = config;

        Gauge.builder("risk.consecutive_losses", this, e -> e.consecutiveLosses)
                .description("Current consecutive loss streak")
                .register(meterRegistry);
        Gauge.builder("risk.daily_pnl", this, e -> e.dailyPnl.doubleValue())
                .description("Daily PnL")
                .register(meterRegistry);
    }

    /** Initialises (or re-initialises) the starting balance for today. */
    public synchronized void setStartingBalance(BigDecimal balance) {
        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) return;
        if (startingBalance.compareTo(BigDecimal.ZERO) == 0) {
            startingBalance = balance;
            peakBalance     = balance;
            log.info("[Risk] Starting balance set: {}", balance);
        }
    }

    /**
     * Returns {@code true} if placing a new trade is allowed.
     * Call this <em>before</em> every trade attempt.
     */
    public synchronized boolean canTrade() {
        rolloverDayIfNeeded();

        if (startingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[Risk] Starting balance not initialised");
            return false;
        }

        // Daily loss limit
        BigDecimal maxLoss = startingBalance.multiply(BigDecimal.valueOf(config.getDailyLossLimitPct()));
        if (dailyPnl.signum() < 0 && dailyPnl.abs().compareTo(maxLoss) >= 0) {
            log.warn("[Risk] DAILY LOSS LIMIT reached. dailyPnl={}", dailyPnl);
            return false;
        }

        // Consecutive losses
        if (consecutiveLosses >= config.getMaxConsecutiveLosses()) {
            log.warn("[Risk] CONSECUTIVE LOSS LIMIT reached: {}", consecutiveLosses);
            return false;
        }

        // Daily profit target (optional stop)
        if (config.getDailyProfitTargetPct() > 0) {
            BigDecimal target = startingBalance.multiply(BigDecimal.valueOf(config.getDailyProfitTargetPct()));
            if (dailyPnl.compareTo(target) >= 0) {
                log.info("[Risk] Daily profit target reached. Stopping. dailyPnl={}", dailyPnl);
                return false;
            }
        }

        // Drawdown from peak
        BigDecimal currentBalance = startingBalance.add(dailyPnl);
        if (peakBalance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal drawdown = peakBalance.subtract(currentBalance)
                    .divide(peakBalance, 6, RoundingMode.HALF_UP);
            if (drawdown.compareTo(BigDecimal.valueOf(config.getMaxDrawdownPct())) >= 0) {
                log.warn("[Risk] MAX DRAWDOWN reached: drawdown={}", drawdown);
                return false;
            }
        }

        return true;
    }

    /**
     * Records the result of a settled trade.
     *
     * @param profitLoss positive = win, negative = loss
     */
    public synchronized void recordResult(BigDecimal profitLoss) {
        rolloverDayIfNeeded();

        dailyPnl = dailyPnl.add(profitLoss);

        // Update peak balance
        BigDecimal currentBalance = startingBalance.add(dailyPnl);
        if (currentBalance.compareTo(peakBalance) > 0) {
            peakBalance = currentBalance;
        }

        if (profitLoss.signum() < 0) {
            consecutiveLosses++;
            log.info("[Risk] Loss recorded. Streak: {}", consecutiveLosses);
        } else {
            consecutiveLosses = 0;
            log.info("[Risk] Win recorded. Streak reset.");
        }

        log.info("[Risk] dailyPnl={} consecutiveLosses={}", dailyPnl, consecutiveLosses);
    }

    // ── Day rollover ─────────────────────────────────────────────────────────

    private void rolloverDayIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            log.info("[Risk] New trading day – resetting state.");
            currentDay        = today;
            startingBalance   = BigDecimal.ZERO;
            peakBalance       = BigDecimal.ZERO;
            dailyPnl          = BigDecimal.ZERO;
            consecutiveLosses = 0;
        }
    }

    // ── Accessors (read-only) ────────────────────────────────────────────────

    public synchronized BigDecimal getDailyPnl()        { return dailyPnl; }
    public synchronized int getConsecutiveLosses()       { return consecutiveLosses; }
    public synchronized BigDecimal getStartingBalance()  { return startingBalance; }
}
