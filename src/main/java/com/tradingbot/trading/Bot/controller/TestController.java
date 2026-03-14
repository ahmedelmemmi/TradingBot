package com.tradingbot.trading.Bot.controller;

import com.tradingbot.trading.Bot.backtest.BacktestEngine;
import com.tradingbot.trading.Bot.backtest.BacktestResult;
import com.tradingbot.trading.Bot.backtest.PortfolioBacktestEngine;
import com.tradingbot.trading.Bot.backtest.PortfolioBacktestResult;
import com.tradingbot.trading.Bot.broker.BrokerStateService;
import com.tradingbot.trading.Bot.broker.IBKRPaperBrokerAdapter;
import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.execution.TradeDecision;
import com.tradingbot.trading.Bot.execution.TradeDecisionService;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MockMarketDataService;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.RsiStrategyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@RestController
public class TestController {
    private final MockMarketDataService marketDataService;
    private final RsiStrategyService rsiStrategyService;
    private final TradeDecisionService tradeDecisionService;
    private final PositionManager positionManager;
    private final BacktestEngine backtestEngine;
    private final IBKRPaperBrokerAdapter brokerAdapter;
    private final BrokerStateService brokerStateService;
    private final MarketRegimeService marketRegimeService;
    private final PortfolioBacktestEngine portfolioBacktestEngine;

    public TestController(MockMarketDataService marketDataService,
                          RsiStrategyService rsiStrategyService,
                          TradeDecisionService tradeDecisionService,
                          PositionManager positionManager,
                          BacktestEngine backtestEngine,
                          IBKRPaperBrokerAdapter brokerAdapter,
                          BrokerStateService brokerStateService, MarketRegimeService marketRegimeService, PortfolioBacktestEngine portfolioBacktestEngine) {

        this.marketDataService = marketDataService;
        this.rsiStrategyService = rsiStrategyService;
        this.tradeDecisionService = tradeDecisionService;
        this.positionManager = positionManager;
        this.backtestEngine = backtestEngine;
        this.brokerAdapter = brokerAdapter;
        this.brokerStateService = brokerStateService;
        this.marketRegimeService = marketRegimeService;
        this.portfolioBacktestEngine = portfolioBacktestEngine;
    }

    /*
     -------------------------------------------------------
     SIMPLE STRATEGY TEST
     -------------------------------------------------------
     */
    @GetMapping("/test")
    public Object testStrategy() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        50,
                        MockMarketDataService.MarketScenario.SIDEWAYS_VOLATILE
                );

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

    /*
     -------------------------------------------------------
     NORMAL BACKTEST
     -------------------------------------------------------
     */
    @GetMapping("/backtest/random")
    public BacktestResult backtestRandom() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        1000,
                        MockMarketDataService.MarketScenario.RANDOM
                );

        return backtestEngine.runStrategy(
                "AAPL",
                candles,
                rsiStrategyService
        );
    }

    /*
     -------------------------------------------------------
     UPTREND TEST
     -------------------------------------------------------
     */
    @GetMapping("/backtest/uptrend")
    public BacktestResult backtestUptrend() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        1000,
                        MockMarketDataService.MarketScenario.STRONG_UPTREND
                );

        return backtestEngine.runStrategy(
                "AAPL",
                candles,
                rsiStrategyService
        );
    }

    /*
     -------------------------------------------------------
     CRASH TEST
     -------------------------------------------------------
     */
    @GetMapping("/backtest/crash")
    public BacktestResult backtestCrash() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        1500,
                        MockMarketDataService.MarketScenario.CRASH
                );

        return backtestEngine.runStrategy(
                "AAPL",
                candles,
                rsiStrategyService
        );
    }

    /*
     -------------------------------------------------------
     VOLATILE SIDEWAYS TEST
     -------------------------------------------------------
     */
    @GetMapping("/backtest/volatile")
    public BacktestResult backtestVolatile() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        1500,
                        MockMarketDataService.MarketScenario.SIDEWAYS_VOLATILE
                );

        return backtestEngine.runStrategy(
                "AAPL",
                candles,
                rsiStrategyService
        );
    }

    /*
     -------------------------------------------------------
     PORTFOLIO BACKTEST
     -------------------------------------------------------
     */
    @GetMapping("/backtest/portfolio")
    public Map<String, BacktestResult> backtestPortfolio() {

        List<String> symbols = List.of(
                "AAPL",
                "MSFT",
                "NVDA",
                "TSLA"
        );

        Map<String, BacktestResult> results = new HashMap<>();

        for (String symbol : symbols) {

            List<Candle> candles =
                    marketDataService.generateCandles(
                            symbol,
                            1000,
                            MockMarketDataService.MarketScenario.RANDOM
                    );

            BacktestResult result =
                    backtestEngine.runStrategy(
                            symbol,
                            candles,
                            rsiStrategyService
                    );

            results.put(symbol, result);
        }

        return results;
    }

    /*
     -------------------------------------------------------
     PAPER ORDER TEST
     -------------------------------------------------------
     */
    @GetMapping("/test-order")
    public String testOrder() {

        TradeDecision decision = TradeDecision.buy(
                "AAPL",
                BigDecimal.valueOf(180),
                BigDecimal.ONE,
                BigDecimal.valueOf(175),
                BigDecimal.valueOf(190)
        );

        brokerAdapter.submitOrder(decision);

        return "Paper order submitted";
    }

    /*
     -------------------------------------------------------
     BROKER STATE
     -------------------------------------------------------
     */
    @GetMapping("/broker-state")
    public Object brokerState() {
        return brokerStateService.getPositions();
    }

    @GetMapping("/regime-test")
    public Object regimeTest() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        500,
                        MockMarketDataService.MarketScenario.STRONG_DOWNTREND
                );

        var regime = marketRegimeService.detect(candles);

        BigDecimal lastPrice =
                candles.get(candles.size() - 1).getClose();

        return Map.of(
                "regime", regime,
                "lastPrice", lastPrice,
                "candles", candles.size()
        );
    }

    @GetMapping("/regime-debug")
    public Object regimeDebug() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        500,
                        MockMarketDataService.MarketScenario.CRASH
                );

        var metrics = marketRegimeService.debugMetrics(candles);

        return metrics;
    }

    /*
    ======================================================
    ✅ PROFESSIONAL PORTFOLIO BACKTEST
    ======================================================
     */
    @GetMapping("/backtest/portfolio/pro")
    public PortfolioBacktestResult runPortfolioBacktest() {

        Map<String, List<Candle>> market = new HashMap<>();

        market.put("AAPL",
                marketDataService.generateCandles(
                        "AAPL",
                        1500,
                        MockMarketDataService.MarketScenario.SIDEWAYS_VOLATILE
                ));

        market.put("MSFT",
                marketDataService.generateCandles(
                        "MSFT",
                        1500,
                        MockMarketDataService.MarketScenario.STRONG_UPTREND
                ));

        market.put("NVDA",
                marketDataService.generateCandles(
                        "NVDA",
                        1500,
                        MockMarketDataService.MarketScenario.CRASH
                ));

        market.put("TSLA",
                marketDataService.generateCandles(
                        "TSLA",
                        1500,
                        MockMarketDataService.MarketScenario.RANDOM
                ));

        return portfolioBacktestEngine.runPortfolio(
                market,
                rsiStrategyService
        );
    }

}
