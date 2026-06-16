package com.tradingbot.trading.Bot.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "predictions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_id")
    private SignalEntity signal;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "predicted_at", nullable = false)
    private Instant predictedAt;

    @Column(name = "buy_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal buyProbability;

    @Column(name = "sell_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal sellProbability;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features_json", columnDefinition = "jsonb")
    private Map<String, Object> featuresJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
