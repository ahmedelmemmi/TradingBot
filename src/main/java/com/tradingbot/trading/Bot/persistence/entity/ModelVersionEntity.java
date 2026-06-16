package com.tradingbot.trading.Bot.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "model_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_tag", nullable = false, unique = true, length = 50)
    private String versionTag;

    @Column(nullable = false, length = 50)
    private String algorithm;

    @Column(name = "trained_at", nullable = false)
    private Instant trainedAt;

    @Column(name = "training_rows")
    private Integer trainingRows;

    @Column(name = "val_accuracy", precision = 6, scale = 4)
    private BigDecimal valAccuracy;

    @Column(name = "val_auc", precision = 6, scale = 4)
    private BigDecimal valAuc;

    @Column(name = "val_log_loss", precision = 10, scale = 6)
    private BigDecimal valLogLoss;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_importance_json", columnDefinition = "jsonb")
    private Map<String, Double> featureImportanceJson;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
