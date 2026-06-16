package com.tradingbot.trading.Bot.trading;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketDataService;
import com.tradingbot.trading.Bot.persistence.entity.BinaryTradeEntity;
import com.tradingbot.trading.Bot.persistence.repository.BinaryTradeRepository;
import com.tradingbot.trading.Bot.risk.BinaryOptionsRiskEngine;
import com.tradingbot.trading.Bot.signal.FusionResult;
import com.tradingbot.trading.Bot.signal.SignalFusionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Main trading loop.
 *
 * <p>Every minute:</p>
 * <ol>
 *   <li>Fetches the latest candles.</li>
 *   <li>Runs the signal fusion engine.</li>
 *   <li>Places a trade if approved by rules + ML + risk.</li>
 *   <li>Settles expired open trades.</li>
 * </ol>
 */
@Component
public class TradingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TradingOrchestrator.class);

    private final MarketDataService marketDataService;
    private final SignalFusionEngine fusionEngine;
    private final BinaryTradeExecutor executor;
    private final BinaryOptionsRiskEngine riskEngine;
    private final BinaryTradeRepository tradeRepo;

    @Value("${trading.symbol:EURUSD}")
    private String symbol;

    @Value("${trading.timeframe:M1}")
    private String timeFrame;

    @Value("${trading.mode:PAPER}")
    private String mode;

    @Value("${trading.candles.lookback:250}")
    private int lookbackCandles;

    @Value("${trading.balance.initial:1000.00}")
    private BigDecimal initialBalance;

    public TradingOrchestrator(MarketDataService marketDataService,
                               SignalFusionEngine fusionEngine,
                               BinaryTradeExecutor executor,
                               BinaryOptionsRiskEngine riskEngine,
                               BinaryTradeRepository tradeRepo) {
        this.marketDataService = marketDataService;
        this.fusionEngine      = fusionEngine;
        this.executor          = executor;
        this.riskEngine        = riskEngine;
        this.tradeRepo         = tradeRepo;
    }

    /** Main trading tick – runs every minute. */
    @Scheduled(fixedDelayString = "${trading.interval.ms:60000}")
    public void tradingTick() {
        log.debug("[Orchestrator] Trading tick for {} [{}]", symbol, mode);

        // Init balance on first run
        riskEngine.setStartingBalance(initialBalance);

        // ── 1. Fetch market data ──────────────────────────────────────────
        List<Candle> candles;
        try {
            candles = marketDataService.getHistoricalCandles(symbol, lookbackCandles);
        } catch (Exception ex) {
            log.error("[Orchestrator] Failed to fetch market data: {}", ex.getMessage());
            return;
        }

        if (candles.size() < 50) {
            log.warn("[Orchestrator] Insufficient candle data: {}", candles.size());
            return;
        }

        // ── 2. Evaluate signal ────────────────────────────────────────────
        FusionResult fusion;
        try {
            fusion = fusionEngine.evaluate(symbol, timeFrame, candles);
        } catch (Exception ex) {
            log.error("[Orchestrator] Signal fusion error: {}", ex.getMessage(), ex);
            return;
        }

        // ── 3. Execute trade ──────────────────────────────────────────────
        if (fusion.isApproved()) {
            try {
                executor.placeTrade(symbol, mode, fusion);
            } catch (Exception ex) {
                log.error("[Orchestrator] Trade execution error: {}", ex.getMessage(), ex);
            }
        }

        // ── 4. Settle expired trades ──────────────────────────────────────
        settleExpiredTrades(candles);
    }

    private void settleExpiredTrades(List<Candle> candles) {
        Candle latestCandle = candles.get(candles.size() - 1);
        BigDecimal currentPrice = latestCandle.getClose();

        List<BinaryTradeEntity> expired = tradeRepo.findExpiredTrades(
                Instant.now().minusSeconds(60));  // trades older than 60s

        for (BinaryTradeEntity trade : expired) {
            try {
                executor.settleTrade(trade.getId(), currentPrice);
            } catch (Exception ex) {
                log.error("[Orchestrator] Settlement error for trade {}: {}",
                        trade.getId(), ex.getMessage());
            }
        }
    }
}
