package com.tradingbot.trading.Bot.trading;

import com.tradingbot.trading.Bot.audit.AuditService;
import com.tradingbot.trading.Bot.persistence.entity.BinaryTradeEntity;
import com.tradingbot.trading.Bot.persistence.entity.TradeResultEntity;
import com.tradingbot.trading.Bot.persistence.repository.BinaryTradeRepository;
import com.tradingbot.trading.Bot.persistence.repository.TradeResultRepository;
import com.tradingbot.trading.Bot.risk.BinaryOptionsRiskEngine;
import com.tradingbot.trading.Bot.signal.FusionResult;
import com.tradingbot.trading.Bot.signal.RuleEngineConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Executes binary option trades (PAPER or LIVE mode).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Validates risk before placing a trade.</li>
 *   <li>Persists the trade record.</li>
 *   <li>Settles expired trades and records results.</li>
 *   <li>Updates the risk engine after settlement.</li>
 * </ul>
 *
 * <p>Martingale is explicitly prohibited: stake is always fixed.</p>
 */
@Service
public class BinaryTradeExecutor {

    private static final Logger log = LoggerFactory.getLogger(BinaryTradeExecutor.class);

    private final BinaryTradeRepository tradeRepo;
    private final TradeResultRepository resultRepo;
    private final BinaryOptionsRiskEngine riskEngine;
    private final RuleEngineConfig ruleConfig;
    private final AuditService auditService;

    private final Counter tradesPlacedCounter;
    private final Counter tradesWonCounter;
    private final Counter tradesLostCounter;

    public BinaryTradeExecutor(BinaryTradeRepository tradeRepo,
                               TradeResultRepository resultRepo,
                               BinaryOptionsRiskEngine riskEngine,
                               RuleEngineConfig ruleConfig,
                               AuditService auditService,
                               MeterRegistry meterRegistry) {
        this.tradeRepo    = tradeRepo;
        this.resultRepo   = resultRepo;
        this.riskEngine   = riskEngine;
        this.ruleConfig   = ruleConfig;
        this.auditService = auditService;

        tradesPlacedCounter = Counter.builder("trades.placed")
                .description("Total trades placed").register(meterRegistry);
        tradesWonCounter    = Counter.builder("trades.won")
                .description("Total trades won").register(meterRegistry);
        tradesLostCounter   = Counter.builder("trades.lost")
                .description("Total trades lost").register(meterRegistry);
    }

    /**
     * Places a binary options trade if risk conditions allow.
     *
     * @param symbol  trading symbol
     * @param mode    "PAPER" or "LIVE"
     * @param fusion  approved fusion result from the signal engine
     * @return the persisted trade entity, or {@code null} if risk blocked the trade
     */
    @Transactional
    public BinaryTradeEntity placeTrade(String symbol, String mode, FusionResult fusion) {
        if (!fusion.isApproved()) {
            log.debug("[Executor] Trade not approved: {}", fusion.getBlockReason());
            return null;
        }

        if (!riskEngine.canTrade()) {
            log.warn("[Executor] Risk engine blocked trade for {}", symbol);
            return null;
        }

        // Prevent duplicate orders for the same symbol
        boolean alreadyOpen = !tradeRepo.findBySymbolAndStatus(symbol, "OPEN").isEmpty();
        if (alreadyOpen) {
            log.info("[Executor] Duplicate prevention: open trade already exists for {}", symbol);
            return null;
        }

        BinaryTradeEntity trade = BinaryTradeEntity.builder()
                .signal(fusion.getSignal())
                .prediction(fusion.getPrediction())
                .symbol(symbol)
                .direction(fusion.getDirection())
                .entryPrice(fusion.getEntryPrice())
                .entryTime(Instant.now())
                .expirySeconds(ruleConfig.getExpirySeconds())
                .stakeAmount(ruleConfig.getStakeAmount())
                .payoutPercent(ruleConfig.getPayoutPercent())
                .mode(mode)
                .status("OPEN")
                .build();

        trade = tradeRepo.save(trade);
        tradesPlacedCounter.increment();

        auditService.log("TRADE_PLACED", "BinaryTrade", trade.getId(),
                "Placed " + mode + " " + fusion.getDirection() + " trade for " + symbol,
                Map.of("direction", fusion.getDirection(), "stake", ruleConfig.getStakeAmount()));

        log.info("[Executor] Trade {} placed: {} {} @ {} (expiry={}s)",
                trade.getId(), mode, fusion.getDirection(), fusion.getEntryPrice(),
                ruleConfig.getExpirySeconds());

        return trade;
    }

    /**
     * Settles a trade by comparing the exit price to the entry price.
     *
     * @param tradeId   ID of the trade to settle
     * @param exitPrice observed price at expiry
     */
    @Transactional
    public TradeResultEntity settleTrade(Long tradeId, BigDecimal exitPrice) {
        BinaryTradeEntity trade = tradeRepo.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + tradeId));

        if (!"OPEN".equals(trade.getStatus())) {
            log.warn("[Executor] Trade {} already settled", tradeId);
            return resultRepo.findByTradeId(tradeId).orElse(null);
        }

        boolean win = isWin(trade.getDirection(), trade.getEntryPrice(), exitPrice);
        BigDecimal payout = trade.getPayoutPercent() != null ? trade.getPayoutPercent() : BigDecimal.valueOf(80);

        BigDecimal profitLoss;
        String outcome;
        if (win) {
            profitLoss = trade.getStakeAmount().multiply(payout).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            outcome    = "WIN";
            tradesWonCounter.increment();
        } else {
            profitLoss = trade.getStakeAmount().negate();
            outcome    = "LOSS";
            tradesLostCounter.increment();
        }

        BigDecimal returnPct = profitLoss.divide(trade.getStakeAmount(), 4, RoundingMode.HALF_UP);

        // Update trade status
        trade.setStatus(outcome);
        tradeRepo.save(trade);

        // Persist result
        TradeResultEntity result = TradeResultEntity.builder()
                .trade(trade)
                .exitPrice(exitPrice)
                .exitTime(Instant.now())
                .outcome(outcome)
                .profitLoss(profitLoss)
                .returnPct(returnPct)
                .build();
        result = resultRepo.save(result);

        // Update risk engine
        riskEngine.recordResult(profitLoss);

        auditService.log("TRADE_SETTLED", "BinaryTrade", tradeId,
                "Trade settled: " + outcome + " P/L=" + profitLoss,
                Map.of("outcome", outcome, "profitLoss", profitLoss));

        log.info("[Executor] Trade {} settled: {} P/L={}", tradeId, outcome, profitLoss);
        return result;
    }

    private boolean isWin(String direction, BigDecimal entry, BigDecimal exit) {
        int cmp = exit.compareTo(entry);
        return "BUY".equals(direction) ? cmp > 0 : cmp < 0;
    }
}
