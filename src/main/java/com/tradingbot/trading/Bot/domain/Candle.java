package com.tradingbot.trading.Bot.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Builder
@NoArgsConstructor
public class Candle {
    private String symbol;
    private LocalDateTime time;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;

    private long volume;

    public Candle(String symbol, LocalDateTime time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
        this.symbol = symbol;
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }
}
