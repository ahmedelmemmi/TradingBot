package com.tradingbot.trading.Bot.execution;

import java.math.BigDecimal;

public class TradeDecision {
    private String symbol;
    private BigDecimal entryPrice;
    private BigDecimal quantity;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private String reason;
    private boolean execute;

    private TradeDecision() {}

    public static TradeDecision buy(String symbol,
                                    BigDecimal entryPrice,
                                    BigDecimal quantity,
                                    BigDecimal stopLoss,
                                    BigDecimal takeProfit) {

        TradeDecision decision = new TradeDecision();
        decision.symbol = symbol;
        decision.entryPrice = entryPrice;
        decision.quantity = quantity;
        decision.stopLoss = stopLoss;
        decision.takeProfit = takeProfit;
        decision.execute = true;
        decision.reason = "BUY signal validated";
        return decision;
    }

    public static TradeDecision noTrade(String reason) {
        TradeDecision decision = new TradeDecision();
        decision.execute = false;
        decision.reason = reason;
        return decision;
    }

    public boolean isExecute() { return execute; }
    public String getReason() { return reason; }
    public String getSymbol() { return symbol; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getStopLoss() { return stopLoss; }
    public BigDecimal getTakeProfit() { return takeProfit; }
}
