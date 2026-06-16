package com.tradingbot.trading.Bot.broker;

import com.tradingbot.trading.Bot.execution.TradeDecision;
import org.springframework.stereotype.Service;

/**
 * Stub retained for reference. Requires the TWS API jar — see {@link BaseEWrapper}.
 * All methods are no-ops so the project compiles without the IBKR SDK.
 */
@Service
public class IBKRPaperBrokerAdapter {

    public boolean isConnected() { return false; }

    public void connect() {}

    public void disconnect() {}

    public void requestHistoricalBars() {}

    public void submitOrder(TradeDecision decision) {}
}
