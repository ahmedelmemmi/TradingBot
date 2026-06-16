package com.tradingbot.trading.Bot.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TradeResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false, unique = true)
    private BinaryTradeEntity trade;

    @Column(name = "exit_price", precision = 18, scale = 6)
    private BigDecimal exitPrice;

    @Column(name = "exit_time")
    private Instant exitTime;

    /** WIN | LOSS | EXPIRED */
    @Column(nullable = false, length = 10)
    private String outcome;

    @Column(name = "profit_loss", nullable = false, precision = 18, scale = 2)
    private BigDecimal profitLoss;

    @Column(name = "return_pct", precision = 8, scale = 4)
    private BigDecimal returnPct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
