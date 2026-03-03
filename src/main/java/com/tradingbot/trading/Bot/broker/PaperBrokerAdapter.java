package com.tradingbot.trading.Bot.broker;

import com.tradingbot.trading.Bot.execution.TradeDecision;
import org.springframework.stereotype.Service;

@Service
public class PaperBrokerAdapter {
    // This simulates a real broker, like IBKR paper account
    public boolean submitOrder(TradeDecision decision) {

        if (!decision.isExecute()) return false;

        System.out.println("[BROKER] Submitting order:");
        System.out.println("Symbol: " + decision.getSymbol());
        System.out.println("Quantity: " + decision.getQuantity());
        System.out.println("Entry: " + decision.getEntryPrice());
        System.out.println("SL: " + decision.getStopLoss());
        System.out.println("TP: " + decision.getTakeProfit());

        // In the future, this is where IBKR API call goes
        // e.g., client.placeOrder(...)

        // Simulate immediate paper fill
        simulatePaperFill(decision);

        return true;
    }

    private void simulatePaperFill(TradeDecision decision) {
        // Simple: assume order executes at entry price
        System.out.println("[BROKER] Paper fill executed at " + decision.getEntryPrice());
    }
}
