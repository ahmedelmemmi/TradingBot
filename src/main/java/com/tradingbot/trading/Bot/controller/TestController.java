package com.tradingbot.trading.Bot.controller;

import com.tradingbot.trading.Bot.backtest.BacktestEngine;
import com.tradingbot.trading.Bot.backtest.BacktestResult;
import com.tradingbot.trading.Bot.backtest.BacktestValidationResult;
import com.tradingbot.trading.Bot.backtest.BacktestValidationService;
import com.tradingbot.trading.Bot.backtest.HybridBacktestResult;
import com.tradingbot.trading.Bot.backtest.PortfolioBacktestEngine;
import com.tradingbot.trading.Bot.backtest.PortfolioBacktestResult;
import com.tradingbot.trading.Bot.broker.BrokerStateService;
import com.tradingbot.trading.Bot.broker.IBKRPaperBrokerAdapter;
import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.execution.TradeDecision;
import com.tradingbot.trading.Bot.execution.TradeDecisionService;
import com.tradingbot.trading.Bot.market.MarketDataProvider;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MockMarketDataService;
import com.tradingbot.trading.Bot.market.YahooFinanceMarketDataProvider;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.PerfectBreakoutStrategy;
import com.tradingbot.trading.Bot.strategy.RsiStrategyService;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class TestController {
    private final MockMarketDataService marketDataService;
    private final RsiStrategyService rsiStrategyService;
    private final PerfectBreakoutStrategy perfectBreakoutStrategy;
    private final TradeDecisionService tradeDecisionService;
    private final PositionManager positionManager;
    private final BacktestEngine backtestEngine;
    private final IBKRPaperBrokerAdapter brokerAdapter;
    private final BrokerStateService brokerStateService;
    private final MarketRegimeService marketRegimeService;
    private final PortfolioBacktestEngine portfolioBacktestEngine;
    private final BacktestValidationService backtestValidationService;
    private final YahooFinanceMarketDataProvider yahooFinanceProvider;

    public TestController(MockMarketDataService marketDataService,
                          RsiStrategyService rsiStrategyService,
                          PerfectBreakoutStrategy perfectBreakoutStrategy,
                          TradeDecisionService tradeDecisionService,
                          PositionManager positionManager,
                          BacktestEngine backtestEngine,
                          IBKRPaperBrokerAdapter brokerAdapter,
                          BrokerStateService brokerStateService,
                          MarketRegimeService marketRegimeService,
                          PortfolioBacktestEngine portfolioBacktestEngine,
                          BacktestValidationService backtestValidationService,
                          YahooFinanceMarketDataProvider yahooFinanceProvider) {

        this.marketDataService         = marketDataService;
        this.rsiStrategyService        = rsiStrategyService;
        this.perfectBreakoutStrategy   = perfectBreakoutStrategy;
        this.tradeDecisionService      = tradeDecisionService;
        this.positionManager           = positionManager;
        this.backtestEngine            = backtestEngine;
        this.brokerAdapter             = brokerAdapter;
        this.brokerStateService        = brokerStateService;
        this.marketRegimeService       = marketRegimeService;
        this.portfolioBacktestEngine   = portfolioBacktestEngine;
        this.backtestValidationService = backtestValidationService;
        this.yahooFinanceProvider      = yahooFinanceProvider;
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
     NORMAL BACKTEST  (PerfectBreakoutStrategy)
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
                perfectBreakoutStrategy
        );
    }

    /*
     -------------------------------------------------------
     UPTREND TEST  (PerfectBreakoutStrategy)
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
                perfectBreakoutStrategy
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
                perfectBreakoutStrategy
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
                perfectBreakoutStrategy
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
                            perfectBreakoutStrategy
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
                perfectBreakoutStrategy
        );
    }

    /*
    ======================================================
    ✅ BACKTEST VALIDATION ENDPOINT
    Runs uptrend backtest, validates all criteria, returns
    pass/fail report. Blocks live trading if validation fails.
    ======================================================
     */
    @GetMapping("/backtest/validate")
    public Map<String, Object> validateBacktest() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        1000,
                        MockMarketDataService.MarketScenario.STRONG_UPTREND
                );

        BacktestResult result = backtestEngine.runStrategy(
                "AAPL", candles, perfectBreakoutStrategy);

        BacktestValidationResult validation =
                backtestValidationService.validate(
                        positionManager.getClosedPositions(),
                        result.getEquityCurve(),
                        result.getStartingCapital()
                );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid",              validation.isValid());
        response.put("totalTrades",        validation.getTotalTrades());
        response.put("winRate",            validation.getWinRate());
        response.put("expectancy",         validation.getExpectancy());
        response.put("profitFactor",       validation.getProfitFactor());
        response.put("maxDrawdown",        validation.getMaxDrawdown());
        response.put("avgWin",             validation.getAvgWin());
        response.put("avgLoss",            validation.getAvgLoss());
        response.put("tradeCountPass",     validation.isTradeCountPass());
        response.put("winRatePass",        validation.isWinRatePass());
        response.put("expectancyPass",     validation.isExpectancyPass());
        response.put("profitFactorPass",   validation.isProfitFactorPass());
        response.put("drawdownPass",       validation.isDrawdownPass());
        response.put("rrRatioPass",        validation.isRrRatioPass());
        response.put("failureReasons",     validation.getFailureReasons());
        response.put("liveTradingAllowed", validation.isValid());

        return response;
    }

    /*
    ======================================================
    ✅ TRADE LOG CSV EXPORT
    Returns the full trade log for the last uptrend backtest
    in CSV format for external analysis.
    ======================================================
     */
    @GetMapping(value = "/backtest/tradelog", produces = MediaType.TEXT_PLAIN_VALUE)
    public String tradeLogCsv() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        1000,
                        MockMarketDataService.MarketScenario.STRONG_UPTREND
                );

        BacktestResult result = backtestEngine.runStrategy(
                "AAPL", candles, perfectBreakoutStrategy);

        return result.getTradeLogCsv();
    }

    /*
    ======================================================
    ✅ HYBRID REGIME-BASED STRATEGY BACKTEST
    Runs a full backtest using the RegimeAwareStrategyFactory.
    PerfectBreakoutStrategy is used in all active regimes:
      STRONG_UPTREND   → PerfectBreakoutStrategy
      SIDEWAYS         → PerfectBreakoutStrategy (watching for breakout)
      HIGH_VOLATILITY  → No trading (capital preserved)
      DOWNTREND/CRASH  → No trading (capital preserved)
    Returns per-strategy trade counts and aggregate metrics.
    ======================================================
     */
    @GetMapping("/backtest/hybrid")
    public Map<String, Object> backtestHybrid() {

        List<Candle> candles =
                marketDataService.generateCandles(
                        "AAPL",
                        1000,
                        MockMarketDataService.MarketScenario.RANDOM
                );

        HybridBacktestResult result = portfolioBacktestEngine.runHybrid("AAPL", candles);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("startCapital",    result.getStartCapital());
        response.put("endCapital",      result.getEndCapital());
        response.put("totalPnL",        result.getTotalPnL());
        response.put("totalTrades",     result.getTotalTrades());
        response.put("winningTrades",   result.getWinningTrades());
        response.put("losingTrades",    result.getLosingTrades());
        response.put("winRate",         result.getWinRate());
        response.put("profitFactor",    result.getProfitFactor());
        response.put("expectancy",      result.getExpectancy());
        response.put("maxDrawdown",     result.getMaxDrawdown());
        response.put("tradesByStrategy", result.getTradesByStrategy());

        return response;
    }

    /*
    ======================================================
    ✅ DEBUG: PerfectBreakoutStrategy Diagnostic
    Generates 500 STRONG_UPTREND candles and evaluates the
    strategy at each bar, counting BUY signals and logging
    which condition rejects each bar. Use this to verify
    that the strategy is generating signals and to diagnose
    why it might not be trading.
    ======================================================
     */
    @GetMapping("/backtest/debug-perfect-breakout")
    public Map<String, Object> debugPerfectBreakout() {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEBUG: PerfectBreakoutStrategy Diagnostic");
        System.out.println("=".repeat(80));

        List<Candle> candles = marketDataService.generateCandles(
                "AAPL",
                500,
                MockMarketDataService.MarketScenario.STRONG_UPTREND
        );

        System.out.println("Generated 500 UPTREND candles. Running strategy evaluation...\n");

        int signalCount = 0;
        List<String> signals = new ArrayList<>();

        for (int i = 60; i < candles.size(); i++) {
            List<Candle> subset = candles.subList(0, i + 1);
            TradingSignal signal = perfectBreakoutStrategy.evaluate(subset);

            if (signal == TradingSignal.BUY) {
                signalCount++;
                signals.add("Bar " + i + ": BUY SIGNAL");
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("DIAGNOSTIC RESULT: " + signalCount + " BUY signals found");
        System.out.println("=".repeat(80) + "\n");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCandles", candles.size());
        result.put("buySignalsFound", signalCount);
        result.put("signals", signals);

        if (signalCount == 0) {
            result.put("recommendation", "ADJUST THRESHOLDS - No signals generated");
            result.put("checkThese", Arrays.asList(
                    "1. Consolidation range threshold (currently 2.5%)",
                    "2. Volume dry-up period (currently 2 bars)",
                    "3. Volume spike multiplier (currently 1.3x)",
                    "4. RSI momentum threshold (prev<55, current>=60)",
                    "5. Regime detection (check if STRONG_UPTREND ever detected)"
            ));
        } else {
            result.put("recommendation", "STRATEGY IS WORKING - Signals generated successfully");
        }

        return result;
    }

    /*
    ======================================================
    ✅ REAL DATA BACKTEST – Yahoo Finance SPY 2024
    Downloads 2024 SPY daily candles from Yahoo Finance and
    runs the PerfectBreakoutStrategy on real market data.
    Use this to validate the strategy edge on actual history
    before switching to live trading.

    ⚠ Requires internet access to Yahoo Finance.
    ======================================================
     */
    @GetMapping("/backtest/real/spy-2024")
    public Map<String, Object> backtestRealSpy2024() {

        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2024, 12, 31, 23, 59);

        List<Candle> candles = yahooFinanceProvider.getCandles("SPY", 1000, from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dataSource",    yahooFinanceProvider.getProviderName());
        response.put("symbol",        "SPY");
        response.put("from",          from.toLocalDate().toString());
        response.put("to",            to.toLocalDate().toString());
        response.put("candlesLoaded", candles.size());

        if (candles.isEmpty()) {
            response.put("error",
                    "No data loaded – check internet connectivity or try again later");
            return response;
        }

        BacktestResult result = backtestEngine.runStrategy(
                "SPY", candles, perfectBreakoutStrategy);

        response.put("startCapital",  result.getStartingCapital());
        response.put("endCapital",    result.getEndingCapital());
        response.put("totalPnL",      result.getTotalPnL());
        response.put("totalTrades",   result.getTotalTrades());
        response.put("winningTrades", result.getWinningTrades());
        response.put("losingTrades",  result.getLosingTrades());
        response.put("winRate",       result.getWinRate());
        response.put("profitFactor",  result.getProfitFactor());
        response.put("expectancy",    result.getExpectancy());
        response.put("maxDrawdown",   result.getMaxDrawdown());

        return response;
    }

    /*
    ======================================================
    ✅ MOCK vs REAL COMPARISON – SPY 2024
    Runs the PerfectBreakoutStrategy on both mock STRONG_UPTREND
    data and real Yahoo Finance SPY 2024 data side-by-side.
    Use this to confirm whether the mock results reflect real
    market behaviour before going live.

    ⚠ Requires internet access to Yahoo Finance.
    ======================================================
     */
    @GetMapping("/backtest/compare/spy-2024")
    public Map<String, Object> compareBacktestSpy2024() {

        // ── Mock run ──────────────────────────────────────────────────────────
        List<Candle> mockCandles = marketDataService.generateCandles(
                "SPY", 250, MockMarketDataService.MarketScenario.STRONG_UPTREND);

        BacktestResult mockResult = backtestEngine.runStrategy(
                "SPY", mockCandles, perfectBreakoutStrategy);

        Map<String, Object> mockSummary = new LinkedHashMap<>();
        mockSummary.put("dataSource",    "MOCK (STRONG_UPTREND, 250 bars)");
        mockSummary.put("totalTrades",   mockResult.getTotalTrades());
        mockSummary.put("winRate",       mockResult.getWinRate());
        mockSummary.put("profitFactor",  mockResult.getProfitFactor());
        mockSummary.put("totalPnL",      mockResult.getTotalPnL());
        mockSummary.put("maxDrawdown",   mockResult.getMaxDrawdown());

        // ── Real run ──────────────────────────────────────────────────────────
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2024, 12, 31, 23, 59);

        List<Candle> realCandles = yahooFinanceProvider.getCandles("SPY", 1000, from, to);

        Map<String, Object> realSummary = new LinkedHashMap<>();
        realSummary.put("dataSource",    "YAHOO_FINANCE (SPY, 2024-01-01 to 2024-12-31)");
        realSummary.put("candlesLoaded", realCandles.size());

        if (realCandles.isEmpty()) {
            realSummary.put("error",
                    "No data loaded – check internet connectivity or try again later");
        } else {
            BacktestResult realResult = backtestEngine.runStrategy(
                    "SPY", realCandles, perfectBreakoutStrategy);

            realSummary.put("totalTrades",  realResult.getTotalTrades());
            realSummary.put("winRate",      realResult.getWinRate());
            realSummary.put("profitFactor", realResult.getProfitFactor());
            realSummary.put("totalPnL",     realResult.getTotalPnL());
            realSummary.put("maxDrawdown",  realResult.getMaxDrawdown());
        }

        // ── Side-by-side comparison ───────────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mock", mockSummary);
        response.put("real", realSummary);
        response.put("note",
                "If mock and real results are similar, the mock is a good proxy. "
                + "If they differ, adjust the strategy parameters for real market conditions.");

        return response;
    }

}
