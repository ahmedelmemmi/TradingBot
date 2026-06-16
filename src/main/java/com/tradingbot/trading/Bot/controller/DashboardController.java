package com.tradingbot.trading.Bot.controller;

import com.tradingbot.trading.Bot.persistence.entity.BinaryTradeEntity;
import com.tradingbot.trading.Bot.persistence.repository.BinaryTradeRepository;
import com.tradingbot.trading.Bot.persistence.repository.TradeResultRepository;
import com.tradingbot.trading.Bot.risk.BinaryOptionsRiskEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Dashboard REST endpoints.
 *
 * <p>Provides the data consumed by Grafana dashboards.</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Trading performance and status dashboard")
public class DashboardController {

    private final BinaryTradeRepository tradeRepo;
    private final TradeResultRepository resultRepo;
    private final BinaryOptionsRiskEngine riskEngine;

    public DashboardController(BinaryTradeRepository tradeRepo,
                               TradeResultRepository resultRepo,
                               BinaryOptionsRiskEngine riskEngine) {
        this.tradeRepo  = tradeRepo;
        this.resultRepo = resultRepo;
        this.riskEngine = riskEngine;
    }

    @GetMapping("/status")
    @Operation(summary = "Current system status and risk metrics")
    public ResponseEntity<Map<String, Object>> status() {
        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);

        long dailyWins   = resultRepo.countWinsSince(todayStart);
        long dailyLosses = resultRepo.countLossesSince(todayStart);
        long totalDaily  = dailyWins + dailyLosses;
        double winRate   = totalDaily > 0 ? (double) dailyWins / totalDaily : 0.0;
        BigDecimal dailyPnl = resultRepo.sumProfitLossSince(todayStart);

        return ResponseEntity.ok(Map.of(
                "canTrade",            riskEngine.canTrade(),
                "dailyPnl",            dailyPnl,
                "dailyWins",           dailyWins,
                "dailyLosses",         dailyLosses,
                "winRate",             BigDecimal.valueOf(winRate).setScale(4, RoundingMode.HALF_UP),
                "consecutiveLosses",   riskEngine.getConsecutiveLosses(),
                "startingBalance",     riskEngine.getStartingBalance(),
                "timestamp",           Instant.now()
        ));
    }

    @GetMapping("/trades/open")
    @Operation(summary = "All currently open trades")
    public ResponseEntity<List<BinaryTradeEntity>> openTrades() {
        return ResponseEntity.ok(tradeRepo.findBySymbolAndStatus("EURUSD", "OPEN"));
    }

    @GetMapping("/trades/recent")
    @Operation(summary = "Recent trades for a symbol")
    public ResponseEntity<List<BinaryTradeEntity>> recentTrades(
            @RequestParam(defaultValue = "EURUSD") String symbol,
            @RequestParam(defaultValue = "1") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return ResponseEntity.ok(tradeRepo.findBySymbolSince(symbol, since));
    }
}
