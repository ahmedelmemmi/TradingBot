//package com.tradingbot.trading.Bot.scheduler;
//
//import com.tradingbot.trading.Bot.broker.PaperBrokerAdapter;
//import com.tradingbot.trading.Bot.execution.TradeDecisionService;
//import com.tradingbot.trading.Bot.market.MarketDataService;
//import com.tradingbot.trading.Bot.position.PositionManager;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//@Component
//public class TradingScheduler {
//    private final MarketDataService marketDataService;
//    private final TradeDecisionService tradeDecisionService;
//    private final PaperBrokerAdapter brokerAdapter;
//    private final PositionManager positionManager;
//
//    public TradingScheduler(MarketDataService marketDataService,
//                            TradeDecisionService tradeDecisionService,
//                            PaperBrokerAdapter brokerAdapter,
//                            PositionManager positionManager) {
//        this.marketDataService = marketDataService;
//        this.tradeDecisionService = tradeDecisionService;
//        this.brokerAdapter = brokerAdapter;
//        this.positionManager = positionManager;
//    }
//
//    // Every 1 minute
//    @Scheduled(fixedRate = 60_000)
//    public void runTradingCycle() {
//
//        String symbol = "AAPL"; // or configurable
//
//        var latestCandle = marketDataService.getLatestCandle(symbol);
//
//        // Update open positions with latest price
//        positionManager.updatePrice(symbol, latestCandle.getClose());
//
//        // Evaluate strategy
//        var decision = tradeDecisionService.evaluate(symbol,
//                marketDataService.getHistoricalCandles(symbol, 20));
//
//        // Submit to broker
//        brokerAdapter.submitOrder(decision);
//    }
//}
