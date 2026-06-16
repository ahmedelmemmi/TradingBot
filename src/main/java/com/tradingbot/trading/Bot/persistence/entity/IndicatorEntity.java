package com.tradingbot.trading.Bot.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "indicators",
        indexes = @Index(name = "idx_indicators_symbol_time", columnList = "symbol, calc_time DESC"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IndicatorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candle_id")
    private CandleEntity candle;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "time_frame", nullable = false, length = 10)
    private String timeFrame;

    @Column(name = "calc_time", nullable = false)
    private Instant calcTime;

    @Column(name = "ema_20", precision = 18, scale = 6)
    private BigDecimal ema20;

    @Column(name = "ema_50", precision = 18, scale = 6)
    private BigDecimal ema50;

    @Column(name = "ema_100", precision = 18, scale = 6)
    private BigDecimal ema100;

    @Column(name = "ema_200", precision = 18, scale = 6)
    private BigDecimal ema200;

    @Column(name = "rsi_14", precision = 8, scale = 4)
    private BigDecimal rsi14;

    @Column(name = "macd_line", precision = 18, scale = 6)
    private BigDecimal macdLine;

    @Column(name = "macd_signal", precision = 18, scale = 6)
    private BigDecimal macdSignal;

    @Column(name = "macd_histogram", precision = 18, scale = 6)
    private BigDecimal macdHistogram;

    @Column(name = "stoch_rsi_k", precision = 8, scale = 4)
    private BigDecimal stochRsiK;

    @Column(name = "stoch_rsi_d", precision = 8, scale = 4)
    private BigDecimal stochRsiD;

    @Column(name = "atr_14", precision = 18, scale = 6)
    private BigDecimal atr14;

    @Column(name = "bb_upper", precision = 18, scale = 6)
    private BigDecimal bbUpper;

    @Column(name = "bb_middle", precision = 18, scale = 6)
    private BigDecimal bbMiddle;

    @Column(name = "bb_lower", precision = 18, scale = 6)
    private BigDecimal bbLower;

    @Column(name = "adx_14", precision = 8, scale = 4)
    private BigDecimal adx14;

    @Column(name = "di_plus", precision = 8, scale = 4)
    private BigDecimal diPlus;

    @Column(name = "di_minus", precision = 8, scale = 4)
    private BigDecimal diMinus;

    @Column(name = "volume_ma_20", precision = 18, scale = 2)
    private BigDecimal volumeMa20;

    @Column(name = "relative_volume", precision = 8, scale = 4)
    private BigDecimal relativeVolume;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
