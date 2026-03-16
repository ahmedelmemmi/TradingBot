package com.tradingbot.trading.Bot.broker;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single IBKR paper-account order execution record.
 *
 * <p>Fields mirror the fields surfaced by the Interactive Brokers
 * {@code execDetails} callback ({@code Execution} object) so that
 * the in-process paper-trading simulation produces a log that is
 * structurally identical to what a live IBKR connection would emit.</p>
 */
public class PaperOrderExecution {

    public enum Action { BUY, SELL }
    public enum Status { FILLED, REJECTED, PENDING }

    private final int            orderId;
    private final String         symbol;
    private final Action         action;
    private final String         orderType;   // MKT, LMT, …
    private final BigDecimal     requestedQty;
    private final BigDecimal     filledQty;
    private final BigDecimal     fillPrice;
    private final BigDecimal     commission;  // simulated flat $1/order
    private final LocalDateTime  execTime;
    private final Status         status;

    /** SL/TP set at the time the BUY was placed (informational). */
    private final BigDecimal stopLoss;
    private final BigDecimal takeProfit;

    /** Realised PnL is populated lazily when the closing SELL order is built
     *  (i.e. after the trade record is closed). It cannot be set at construction
     *  time because the BUY and SELL executions are created from the same
     *  {@link com.tradingbot.trading.Bot.backtest.TradeRecord} but the PnL
     *  belongs only to the SELL side. */
    private BigDecimal realisedPnl;

    public PaperOrderExecution(int orderId,
                               String symbol,
                               Action action,
                               BigDecimal requestedQty,
                               BigDecimal fillPrice,
                               BigDecimal commission,
                               LocalDateTime execTime,
                               Status status,
                               BigDecimal stopLoss,
                               BigDecimal takeProfit) {
        this.orderId      = orderId;
        this.symbol       = symbol;
        this.action       = action;
        this.orderType    = "MKT";
        this.requestedQty = requestedQty;
        this.filledQty    = status == Status.FILLED ? requestedQty : BigDecimal.ZERO;
        this.fillPrice    = fillPrice;
        this.commission   = commission;
        this.execTime     = execTime;
        this.status       = status;
        this.stopLoss     = stopLoss;
        this.takeProfit   = takeProfit;
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setRealisedPnl(BigDecimal realisedPnl) {
        this.realisedPnl = realisedPnl;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int            getOrderId()      { return orderId; }
    public String         getSymbol()       { return symbol; }
    public Action         getAction()       { return action; }
    public String         getOrderType()    { return orderType; }
    public BigDecimal     getRequestedQty() { return requestedQty; }
    public BigDecimal     getFilledQty()    { return filledQty; }
    public BigDecimal     getFillPrice()    { return fillPrice; }
    public BigDecimal     getCommission()   { return commission; }
    public LocalDateTime  getExecTime()     { return execTime; }
    public Status         getStatus()       { return status; }
    public BigDecimal     getStopLoss()     { return stopLoss; }
    public BigDecimal     getTakeProfit()   { return takeProfit; }
    public BigDecimal     getRealisedPnl()  { return realisedPnl; }
}
