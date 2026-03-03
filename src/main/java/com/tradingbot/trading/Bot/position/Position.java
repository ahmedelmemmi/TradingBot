package com.tradingbot.trading.Bot.position;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Position {
    private final String symbol;
    private final BigDecimal entryPrice;
    private final BigDecimal quantity;
    private final BigDecimal stopLoss;
    private final BigDecimal takeProfit;

    private boolean open = true;
    private BigDecimal exitPrice;
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
    }

    public void close(BigDecimal price) {
        this.exitPrice = price;
        this.pnl = price.subtract(entryPrice)
                .multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);
        this.open = false;
    }

    public boolean isOpen() { return open; }

    public String getSymbol() { return symbol; }

    public BigDecimal getEntryPrice() { return entryPrice; }

    public BigDecimal getQuantity() { return quantity; }

    public BigDecimal getStopLoss() { return stopLoss; }

    public BigDecimal getTakeProfit() { return takeProfit; }

    public BigDecimal getPnl() { return pnl; }

    public BigDecimal getExitPrice() { return exitPrice; }
}
