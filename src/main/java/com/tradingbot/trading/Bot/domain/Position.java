package com.tradingbot.trading.Bot.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;


public class Position {
    @Getter
    private final String symbol;

    @Getter
    private final BigDecimal entryPrice;

    @Getter
    private final BigDecimal quantity;

    @Getter
    private BigDecimal stopLoss;

    @Getter
    private final BigDecimal takeProfit;

    private BigDecimal highestPrice;

    @Getter
    private boolean open = true;

    private BigDecimal exitPrice;

    @Getter
    private BigDecimal pnl;

    public Position(String symbol,
                    BigDecimal entryPrice,
                    BigDecimal quantity,
                    BigDecimal stopLoss,
                    BigDecimal takeProfit) {

        this.symbol = symbol;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.highestPrice = entryPrice;
    }

    public void updateTrailingStop(BigDecimal currentPrice) {

        if (currentPrice.compareTo(highestPrice) > 0) {

            highestPrice = currentPrice;

            BigDecimal trailingStop =
                    highestPrice.multiply(BigDecimal.valueOf(0.98));

            if (trailingStop.compareTo(stopLoss) > 0) {

                stopLoss = trailingStop;

                System.out.println("Trailing stop moved to: " + stopLoss);
            }
        }
    }

    public void close(BigDecimal price) {

        this.exitPrice = price;

        this.pnl = price.subtract(entryPrice)
                .multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);

        this.open = false;

        System.out.println("Position closed. PnL = " + pnl);
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    public BigDecimal getTakeProfit() {
        return takeProfit;
    }

    public BigDecimal getHighestPrice() {
        return highestPrice;
    }

    public void setHighestPrice(BigDecimal highestPrice) {
        this.highestPrice = highestPrice;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }
}
