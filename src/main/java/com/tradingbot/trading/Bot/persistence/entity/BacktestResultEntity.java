package com.tradingbot.trading.Bot.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "backtest_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BacktestResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true, length = 100)
    private String runId;

    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "time_frame", nullable = false, length = 10)
    private String timeFrame;

    @Column(name = "from_date", nullable = false)
    private Instant fromDate;

    @Column(name = "to_date", nullable = false)
    private Instant toDate;

    @Column(name = "total_trades", nullable = false)
    @Builder.Default
    private int totalTrades = 0;

    @Column(name = "winning_trades", nullable = false)
    @Builder.Default
    private int winningTrades = 0;

    @Column(name = "losing_trades", nullable = false)
    @Builder.Default
    private int losingTrades = 0;

    @Column(name = "win_rate", precision = 6, scale = 4)
    private BigDecimal winRate;

    @Column(name = "profit_factor", precision = 10, scale = 4)
    private BigDecimal profitFactor;

    @Column(name = "max_drawdown", precision = 8, scale = 4)
    private BigDecimal maxDrawdown;

    @Column(name = "sharpe_ratio", precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    @Column(name = "total_pnl", precision = 18, scale = 2)
    private BigDecimal totalPnl;

    @Column(name = "max_consecutive_wins")
    private Integer maxConsecutiveWins;

    @Column(name = "max_consecutive_loss")
    private Integer maxConsecutiveLoss;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", columnDefinition = "jsonb")
    private Map<String, Object> parametersJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
