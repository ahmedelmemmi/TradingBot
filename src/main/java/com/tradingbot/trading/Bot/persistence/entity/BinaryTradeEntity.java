package com.tradingbot.trading.Bot.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades",
        indexes = {
            @Index(name = "idx_trades_symbol_time", columnList = "symbol, entry_time DESC"),
            @Index(name = "idx_trades_status",      columnList = "status")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BinaryTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_id")
    private SignalEntity signal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id")
    private PredictionEntity prediction;

    @Column(nullable = false, length = 20)
    private String symbol;

    /** BUY or SELL */
    @Column(nullable = false, length = 4)
    private String direction;

    @Column(name = "entry_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal entryPrice;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "expiry_seconds", nullable = false)
    private int expirySeconds;

    @Column(name = "stake_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal stakeAmount;

    @Column(name = "payout_percent", precision = 5, scale = 2)
    private BigDecimal payoutPercent;

    /** LIVE or PAPER */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String mode = "PAPER";

    @Column(name = "broker_order_id", length = 100)
    private String brokerOrderId;

    /** OPEN | WIN | LOSS | EXPIRED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
