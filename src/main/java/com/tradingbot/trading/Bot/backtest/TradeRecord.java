package com.tradingbot.trading.Bot.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single completed trade in the backtest with full detail for
 * audit, CSV export, and statistical validation.
 */
public class TradeRecord {

    private final String tradeId;
    private final String symbol;

    private final LocalDateTime entryTime;
    private final BigDecimal entryPrice;
    private final BigDecimal quantity;
    private final BigDecimal stopLoss;
    private final BigDecimal takeProfit;

    private LocalDateTime exitTime;
    private BigDecimal exitPrice;
    private String exitReason; // SL / TP / TIMEOUT / FORCE_CLOSE

    private BigDecimal pnl;
    private int barsHeld;

    public TradeRecord(String tradeId,
                       String symbol,
                       LocalDateTime entryTime,
                       BigDecimal entryPrice,
                       BigDecimal quantity,
                       BigDecimal stopLoss,
                       BigDecimal takeProfit) {
        this.tradeId   = tradeId;
        this.symbol    = symbol;
        this.entryTime = entryTime;
        this.entryPrice = entryPrice;
        this.quantity  = quantity;
        this.stopLoss  = stopLoss;
        this.takeProfit = takeProfit;
    }

    public void close(LocalDateTime exitTime,
                      BigDecimal exitPrice,
                      String exitReason,
                      int barsHeld) {
        this.exitTime   = exitTime;
        this.exitPrice  = exitPrice;
        this.exitReason = exitReason;
        this.barsHeld   = barsHeld;
        this.pnl = exitPrice.subtract(entryPrice).multiply(quantity);
    }

    /** Returns a CSV row for this trade. */
    public String toCsvRow() {
        return String.join(",",
                tradeId,
                symbol,
                entryTime  != null ? entryTime.toString()  : "",
                entryPrice != null ? entryPrice.toPlainString()  : "",
                quantity   != null ? quantity.toPlainString()    : "",
                stopLoss   != null ? stopLoss.toPlainString()    : "",
                takeProfit != null ? takeProfit.toPlainString()  : "",
                exitTime   != null ? exitTime.toString()   : "",
                exitPrice  != null ? exitPrice.toPlainString()   : "",
                exitReason != null ? exitReason : "",
                pnl        != null ? pnl.toPlainString()         : "",
                String.valueOf(barsHeld)
        );
    }

    public static String csvHeader() {
        return "tradeId,symbol,entryTime,entryPrice,quantity,stopLoss,takeProfit,"
                + "exitTime,exitPrice,exitReason,pnl,barsHeld";
    }

    // ---- Getters ----
    public String getTradeId()       { return tradeId; }
    public String getSymbol()        { return symbol; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getQuantity()  { return quantity; }
    public BigDecimal getStopLoss()  { return stopLoss; }
    public BigDecimal getTakeProfit() { return takeProfit; }
    public LocalDateTime getExitTime() { return exitTime; }
    public BigDecimal getExitPrice() { return exitPrice; }
    public String getExitReason()    { return exitReason; }
    public BigDecimal getPnl()       { return pnl; }
    public int getBarsHeld()         { return barsHeld; }
}
