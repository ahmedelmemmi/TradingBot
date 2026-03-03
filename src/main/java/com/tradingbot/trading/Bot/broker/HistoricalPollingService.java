package com.tradingbot.trading.Bot.broker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HistoricalPollingService {
    private final IBKRPaperBrokerAdapter brokerAdapter;

    public HistoricalPollingService(IBKRPaperBrokerAdapter brokerAdapter) {
        this.brokerAdapter = brokerAdapter;
    }

    @Scheduled(fixedRate = 60000) // every 60 seconds
    public void poll() {

        if (!brokerAdapter.isConnected()) {
            return;
        }

        brokerAdapter.requestHistoricalBars();
    }
}
