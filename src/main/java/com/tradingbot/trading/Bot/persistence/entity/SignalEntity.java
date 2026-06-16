package com.tradingbot.trading.Bot.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "signals",
        indexes = @Index(name = "idx_signals_symbol_time", columnList = "symbol, signal_time DESC"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "time_frame", nullable = false, length = 10)
    private String timeFrame;

    @Column(name = "signal_time", nullable = false)
    private Instant signalTime;

    /** BUY or SELL */
    @Column(nullable = false, length = 4)
    private String direction;

    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;

    @Column(name = "rule_score", precision = 5, scale = 2)
    private BigDecimal ruleScore;

    @Column(name = "price_at_signal", precision = 18, scale = 6)
    private BigDecimal priceAtSignal;

    @Column(name = "candle_pattern", length = 50)
    private String candlePattern;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
