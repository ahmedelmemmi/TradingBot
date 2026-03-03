package com.tradingbot.trading.Bot.controller;

import com.tradingbot.trading.Bot.backtest.BacktestEngine;
import com.tradingbot.trading.Bot.backtest.BacktestResult;
import com.tradingbot.trading.Bot.broker.BrokerStateService;
import com.tradingbot.trading.Bot.broker.IBKRPaperBrokerAdapter;
import com.tradingbot.trading.Bot.execution.TradeDecision;
import com.tradingbot.trading.Bot.execution.TradeDecisionService;
import com.tradingbot.trading.Bot.market.MockMarketDataService;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.RsiStrategyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
public class TestController {
    private final MockMarketDataService marketDataService;
    private final RsiStrategyService rsiStrategyService;
    private final TradeDecisionService tradeDecisionService;
    private final PositionManager positionManager;
    private final BacktestEngine backtestEngine;
    private final IBKRPaperBrokerAdapter ibkrPaperBrokerAdapter;
    private final BrokerStateService brokerStateService;

    public TestController(MockMarketDataService marketDataService,
                          RsiStrategyService rsiStrategyService, TradeDecisionService tradeDecisionService, PositionManager positionManager, BacktestEngine backtestEngine, IBKRPaperBrokerAdapter ibkrPaperBrokerAdapter, BrokerStateService brokerStateService) {
        this.marketDataService = marketDataService;
        this.rsiStrategyService = rsiStrategyService;
        this.tradeDecisionService = tradeDecisionService;
        this.positionManager = positionManager;
        this.backtestEngine = backtestEngine;
        this.ibkrPaperBrokerAdapter = ibkrPaperBrokerAdapter;
        this.brokerStateService = brokerStateService;
    }



    @GetMapping("/test")
    public Object test() {

        var candles = marketDataService.generateCandles("AAPL", 20);

        TradeDecision decision =
                tradeDecisionService.evaluate("AAPL", candles);

        BigDecimal currentPrice =
                candles.get(candles.size() - 1).getClose();

        positionManager.updatePrice("AAPL", currentPrice);

        return Map.of(
                "decision", decision,
                "openPositions", positionManager.getOpenPositions(),
                "closedPositions", positionManager.getClosedPositions()
        );
    }

    @GetMapping("/backtest")
    public BacktestResult backtest() {

        var candles = marketDataService.generateCandles("AAPL", 200);

        return backtestEngine.run("AAPL", candles);
    }

    @GetMapping("/test-order")
    public String testOrder() {

        TradeDecision decision = TradeDecision.buy(
                "AAPL",
                BigDecimal.valueOf(180),
                BigDecimal.ONE,
                BigDecimal.valueOf(175),
                BigDecimal.valueOf(190)
        );

        ibkrPaperBrokerAdapter.submitOrder(decision);

        return "Order submitted";
    }

    @GetMapping("/broker-state")
    public Object brokerState() {
        return brokerStateService.getPositions();
    }
}
