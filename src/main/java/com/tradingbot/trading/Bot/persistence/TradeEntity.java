package com.tradingbot.trading.Bot.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
public class TradeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    private BigDecimal entryPrice;

    private BigDecimal exitPrice;

    private BigDecimal quantity;

    private BigDecimal pnl;

    private boolean open;

    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    public TradeEntity() {}

    public TradeEntity(String symbol,
                       BigDecimal entryPrice,
                       BigDecimal quantity,
                       LocalDateTime entryTime) {

        this.symbol = symbol;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.entryTime = entryTime;
        this.open = true;
    }

    public void close(BigDecimal exitPrice, BigDecimal pnl) {

        this.exitPrice = exitPrice;
        this.pnl = pnl;
        this.exitTime = LocalDateTime.now();
        this.open = false;
    }

    public Long getId() { return id; }

    public String getSymbol() { return symbol; }

    public BigDecimal getEntryPrice() { return entryPrice; }

    public BigDecimal getExitPrice() { return exitPrice; }

    public BigDecimal getQuantity() { return quantity; }

    public BigDecimal getPnl() { return pnl; }

    public boolean isOpen() { return open; }

    public LocalDateTime getEntryTime() { return entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
}
