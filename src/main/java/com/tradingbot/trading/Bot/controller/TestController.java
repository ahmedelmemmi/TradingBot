package com.tradingbot.trading.Bot.controller;

import com.tradingbot.trading.Bot.backtest.BacktestEngine;
import com.tradingbot.trading.Bot.backtest.BacktestResult;
import com.tradingbot.trading.Bot.backtest.TradeRecord;
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
import com.tradingbot.trading.Bot.market.MockMarketDataProvider;
import com.tradingbot.trading.Bot.market.MockMarketDataService;
import com.tradingbot.trading.Bot.market.YahooFinanceMarketDataProvider;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.AtrCalculator;
import com.tradingbot.trading.Bot.strategy.PerfectBreakoutStrategy;
import com.tradingbot.trading.Bot.strategy.RsiCalculator;
import com.tradingbot.trading.Bot.strategy.RsiStrategyService;
import com.tradingbot.trading.Bot.strategy.SimplifiedBreakoutStrategy;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import com.tradingbot.trading.Bot.strategy.RobustTrendBreakoutStrategy;
import com.tradingbot.trading.Bot.strategy.TunedBreakoutStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@Tag(name = "TradingBot", description = "Backtesting and diagnostics endpoints for the TradingBot strategy engine")
public class TestController {

    /** Maximum candle count fetched by real-data diagnostic endpoints. */
    private static final int MAX_CANDLES_FOR_DIAGNOSTICS = 1000;

    /** Maximum candle count fetched per symbol in multi-symbol real data backtests. */
    private static final int MAX_CANDLES_FOR_BACKTEST = 1000;

    private final MockMarketDataService marketDataService;
    private final RsiStrategyService rsiStrategyService;
    private final PerfectBreakoutStrategy perfectBreakoutStrategy;
    private final SimplifiedBreakoutStrategy simplifiedBreakoutStrategy;
    private final RobustTrendBreakoutStrategy robustBreakoutStrategy;
    private final TradeDecisionService tradeDecisionService;
    private final PositionManager positionManager;
    private final BacktestEngine backtestEngine;
    private final IBKRPaperBrokerAdapter brokerAdapter;
    private final BrokerStateService brokerStateService;
    private final MarketRegimeService marketRegimeService;
    private final PortfolioBacktestEngine portfolioBacktestEngine;
    private final BacktestValidationService backtestValidationService;
    private final YahooFinanceMarketDataProvider yahooFinanceMarketDataProvider;
    private final RsiCalculator rsiCalculator;

    public TestController(MockMarketDataService marketDataService,
                          RsiStrategyService rsiStrategyService,
                          PerfectBreakoutStrategy perfectBreakoutStrategy,
                          SimplifiedBreakoutStrategy simplifiedBreakoutStrategy,
                          RobustTrendBreakoutStrategy robustBreakoutStrategy,
                          TradeDecisionService tradeDecisionService,
                          PositionManager positionManager,
                          BacktestEngine backtestEngine,
                          IBKRPaperBrokerAdapter brokerAdapter,
                          BrokerStateService brokerStateService,
                          MarketRegimeService marketRegimeService,
                          PortfolioBacktestEngine portfolioBacktestEngine,
                          BacktestValidationService backtestValidationService,
                          YahooFinanceMarketDataProvider yahooFinanceMarketDataProvider,
                          RsiCalculator rsiCalculator) {

        this.marketDataService              = marketDataService;
        this.rsiStrategyService             = rsiStrategyService;
        this.perfectBreakoutStrategy        = perfectBreakoutStrategy;
        this.simplifiedBreakoutStrategy     = simplifiedBreakoutStrategy;
        this.robustBreakoutStrategy         = robustBreakoutStrategy;
        this.tradeDecisionService           = tradeDecisionService;
        this.positionManager                = positionManager;
        this.backtestEngine                 = backtestEngine;
        this.brokerAdapter                  = brokerAdapter;
        this.brokerStateService             = brokerStateService;
        this.marketRegimeService            = marketRegimeService;
        this.portfolioBacktestEngine        = portfolioBacktestEngine;
        this.backtestValidationService      = backtestValidationService;
        this.yahooFinanceMarketDataProvider = yahooFinanceMarketDataProvider;
        this.rsiCalculator                  = rsiCalculator;
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
    @Operation(summary = "Broker state", description = "Returns current IBKR paper broker positions.")
    @GetMapping("/broker-state")
    public Object brokerState() {
        return brokerStateService.getPositions();
    }

    @Operation(summary = "Regime detection test", description = "Tests MarketRegimeService regime detection on mock STRONG_UPTREND data.")
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
    @Operation(summary = "Backtest validation",
               description = "Runs PerfectBreakoutStrategy on mock uptrend data and validates against quality gates. " +
                             "Pass/fail report for all criteria.")
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
    ✅ DEBUG: SimplifiedBreakoutStrategy Diagnostic
    Generates 500 STRONG_UPTREND candles and evaluates the
    strategy at each bar, counting BUY signals. Use this to
    verify the strategy generates signals on actual data.
    Expected: 20-50 signals for 500 candles (4-10%).
    ======================================================
     */
    @GetMapping("/backtest/debug-simplified-breakout")
    public Map<String, Object> debugSimplifiedBreakout() {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEBUG: SimplifiedBreakoutStrategy Diagnostic");
        System.out.println("=".repeat(80));

        List<Candle> candles = marketDataService.generateCandles(
                "AAPL",
                500,
                MockMarketDataService.MarketScenario.STRONG_UPTREND
        );

        System.out.println("Generated 500 STRONG_UPTREND candles. Running strategy evaluation...\n");

        int signalCount = 0;
        List<String> signals = new ArrayList<>();

        for (int i = 60; i < candles.size(); i++) {
            List<Candle> subset = candles.subList(0, i + 1);
            TradingSignal signal = simplifiedBreakoutStrategy.evaluate(subset);

            if (signal == TradingSignal.BUY) {
                signalCount++;
                signals.add("Bar " + i);
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESULT: " + signalCount + " signals found");
        System.out.println("=".repeat(80) + "\n");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCandles", candles.size());
        result.put("signalsFound", signalCount);
        result.put("signals", signals);
        result.put("expected", "20-50 signals for 500 candles (4-10%)");
        result.put("status", signalCount > 10 ? "✅ WORKING" : "❌ NEEDS ADJUSTMENT");

        return result;
    }

    /*
    ======================================================
    ✅ MOCK DATA: Backtest using MarketDataProvider abstraction
    Uses MockMarketDataProvider with STRONG_UPTREND scenario
    and SimplifiedBreakoutStrategy.
    Expected: 70%+ win rate on uptrend data.
    ======================================================
     */
    @Operation(summary = "Mock uptrend backtest (SimplifiedBreakoutStrategy)",
               description = "Runs SimplifiedBreakoutStrategy on 1000 bars of mock STRONG_UPTREND data. " +
                             "Sanity check — expected 70%+ win rate.")
    @GetMapping("/backtest/mock/uptrend")
    public Map<String, Object> backtestMockUptrend() {
        MarketDataProvider provider = MockMarketDataProvider.forScenario(
                marketDataService,
                MockMarketDataService.MarketScenario.STRONG_UPTREND
        );

        List<Candle> candles = provider.getCandles("AAPL", 1000);
        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, simplifiedBreakoutStrategy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",      provider.getProviderName());
        response.put("strategy",      simplifiedBreakoutStrategy.getName());
        response.put("totalCandles",  candles.size());
        response.put("totalTrades",   result.getTotalTrades());
        response.put("winRate",       result.getWinRate());
        response.put("totalPnL",      result.getTotalPnL());
        response.put("startCapital",  result.getStartingCapital());
        response.put("endCapital",    result.getEndingCapital());
        response.put("maxDrawdown",   result.getMaxDrawdown());
        return response;
    }

    /*
    ======================================================
    ✅ MOCK DATA: Backtest using MarketDataProvider abstraction
    Uses MockMarketDataProvider with RANDOM scenario and
    SimplifiedBreakoutStrategy.
    ======================================================
     */
    @Operation(summary = "Mock random market backtest",
               description = "Runs SimplifiedBreakoutStrategy on 1000 bars of mixed (RANDOM) mock market data.")
    @GetMapping("/backtest/mock/random")
    public Map<String, Object> backtestMockRandom() {
        MarketDataProvider provider = MockMarketDataProvider.forScenario(
                marketDataService,
                MockMarketDataService.MarketScenario.RANDOM
        );

        List<Candle> candles = provider.getCandles("AAPL", 1000);
        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, simplifiedBreakoutStrategy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",      provider.getProviderName());
        response.put("strategy",      simplifiedBreakoutStrategy.getName());
        response.put("totalCandles",  candles.size());
        response.put("totalTrades",   result.getTotalTrades());
        response.put("winRate",       result.getWinRate());
        response.put("totalPnL",      result.getTotalPnL());
        response.put("startCapital",  result.getStartingCapital());
        response.put("endCapital",    result.getEndingCapital());
        response.put("maxDrawdown",   result.getMaxDrawdown());
        return response;
    }

    /*
    ======================================================
    ✅ UPTREND-ONLY BACKTEST
    Generates pure STRONG_UPTREND mock data and runs
    SimplifiedBreakoutStrategy. Only trades when regime is
    confirmed STRONG_UPTREND.

    Diagnostic logging (server console):
      [UptrendOnly] ✅ UPTREND: Trading with SimplifiedBreakoutStrategy
      [UptrendOnly] ⏸️ SIDEWAYS: NO TRADING - Waiting for uptrend

    Expected: high win rate on pure uptrend data.
    ======================================================
     */
    @Operation(summary = "Uptrend-only mock backtest",
               description = "Runs RobustTrendBreakoutStrategy on 1000 bars of STRONG_UPTREND mock data. " +
                             "Quality gates: win rate ≥60%, profit factor ≥1.2, drawdown ≤25%.")
    @GetMapping("/backtest/uptrend-only")
    public Map<String, Object> backtestUptrendOnly() {

        System.out.println("\n[UptrendOnly] ========== UPTREND-ONLY BACKTEST ==========");

        List<Candle> candles = marketDataService.generateCandles(
                "AAPL",
                1000,
                MockMarketDataService.MarketScenario.STRONG_UPTREND
        );

        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, robustBreakoutStrategy);

        System.out.println("[UptrendOnly] ========== DONE ==========\n");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode",         "uptrend-only");
        response.put("dataScenario", "STRONG_UPTREND");
        response.put("strategy",     robustBreakoutStrategy.getName());
        response.put("totalCandles", candles.size());
        response.put("totalTrades",  result.getTotalTrades());
        response.put("winRate",      result.getWinRate());
        response.put("profitFactor", result.getProfitFactor());
        response.put("totalPnL",     result.getTotalPnL());
        response.put("startCapital", result.getStartingCapital());
        response.put("endCapital",   result.getEndingCapital());
        response.put("maxDrawdown",  result.getMaxDrawdown());
        response.put("note",         "RobustTrendBreakoutStrategy: MA20>MA50 + 20-bar breakout + RSI>50");
        return response;
    }

    /*
    ======================================================
    ✅ ROBUST BACKTEST (mock data)
    Runs RobustTrendBreakoutStrategy on 1000 bars of
    STRONG_UPTREND mock data. Target quality gates:
      trade count ≥5, win rate ≥60%, profit factor ≥1.2,
      max drawdown ≤25%
    ======================================================
     */
    @Operation(summary = "Robust mock backtest with quality gates",
               description = "Runs RobustTrendBreakoutStrategy on 1000 bars of mock STRONG_UPTREND data. " +
                             "Reports pass/fail for: trade count ≥5, win rate ≥60%, profit factor ≥1.2, drawdown ≤25%.")
    @GetMapping("/backtest/robust")
    public Map<String, Object> backtestRobust() {

        System.out.println("\n[Robust] ========== ROBUST STRATEGY BACKTEST (mock) ==========");

        List<Candle> candles = marketDataService.generateCandles(
                "AAPL",
                1000,
                MockMarketDataService.MarketScenario.STRONG_UPTREND
        );

        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, robustBreakoutStrategy);

        System.out.println("[Robust] ========== DONE ==========\n");

        boolean tradeCountPass  = result.getTotalTrades() >= 5;
        boolean winRatePass     = result.getWinRate().compareTo(BigDecimal.valueOf(0.60)) >= 0;
        boolean profitFactorPass= result.getProfitFactor().compareTo(BigDecimal.valueOf(1.2)) >= 0;
        boolean drawdownPass    = result.getMaxDrawdown().compareTo(BigDecimal.valueOf(0.25)) <= 0;
        boolean allPass = tradeCountPass && winRatePass && profitFactorPass && drawdownPass;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("strategy",       robustBreakoutStrategy.getName());
        response.put("dataSource",     "mock-STRONG_UPTREND");
        response.put("totalCandles",   candles.size());
        response.put("totalTrades",    result.getTotalTrades());
        response.put("winRate",        result.getWinRate());
        response.put("profitFactor",   result.getProfitFactor());
        response.put("totalPnL",       result.getTotalPnL());
        response.put("startCapital",   result.getStartingCapital());
        response.put("endCapital",     result.getEndingCapital());
        response.put("maxDrawdown",    result.getMaxDrawdown());
        Map<String, Object> gates = new LinkedHashMap<>();
        gates.put("tradeCount_pass",   tradeCountPass   + " (≥5, got " + result.getTotalTrades() + ")");
        gates.put("winRate_pass",      winRatePass     + " (≥60%)");
        gates.put("profitFactor_pass", profitFactorPass + " (≥1.2)");
        gates.put("drawdown_pass",     drawdownPass    + " (≤25%)");
        gates.put("overall",           allPass ? "✅ ALL QUALITY GATES PASS" : "⚠️ SOME QUALITY GATES FAIL");
        response.put("qualityGates",   gates);
        return response;
    }

    /*
    ======================================================
    ✅ HYBRID UPTREND-ONLY BACKTEST
    Generates mixed (RANDOM) mock data covering multiple
    regimes, but the RegimeAwareStrategyFactory only allows
    trades when STRONG_UPTREND is detected. All sideways /
    volatile / downtrend / crash bars are skipped.

    Diagnostic logging (server console):
      [UptrendOnly] ✅ UPTREND: Trading with RobustTrendBreakoutStrategy
      [UptrendOnly] ⏸️ SIDEWAYS: NO TRADING - Waiting for uptrend
      [UptrendOnly] ⏸️ CRASH: NO TRADING - Waiting for uptrend

    Expected: fewer total trades than uptrend-only, but only
    trades taken during confirmed uptrend windows.
    ======================================================
     */
    @GetMapping("/backtest/hybrid-uptrend-only")
    public Map<String, Object> backtestHybridUptrendOnly() {

        System.out.println("\n[UptrendOnly] ========== HYBRID UPTREND-ONLY BACKTEST ==========");

        List<Candle> candles = marketDataService.generateCandles(
                "AAPL",
                1000,
                MockMarketDataService.MarketScenario.RANDOM
        );

        HybridBacktestResult result = portfolioBacktestEngine.runHybrid("AAPL", candles);

        System.out.println("[UptrendOnly] ========== DONE ==========\n");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode",             "hybrid-uptrend-only");
        response.put("dataScenario",     "RANDOM (mixed regimes)");
        response.put("strategy",         "SimplifiedBreakoutStrategy (STRONG_UPTREND only)");
        response.put("startCapital",     result.getStartCapital());
        response.put("endCapital",       result.getEndCapital());
        response.put("totalPnL",         result.getTotalPnL());
        response.put("totalTrades",      result.getTotalTrades());
        response.put("winningTrades",    result.getWinningTrades());
        response.put("losingTrades",     result.getLosingTrades());
        response.put("winRate",          result.getWinRate());
        response.put("profitFactor",     result.getProfitFactor());
        response.put("expectancy",       result.getExpectancy());
        response.put("maxDrawdown",      result.getMaxDrawdown());
        response.put("tradesByStrategy", result.getTradesByStrategy());
        response.put("note",             "Mixed data — only uptrend portions traded, capital preserved elsewhere");
        return response;
    }

    /*
    ======================================================
    ✅ REAL DATA BACKTEST: SPY 2-Year History (Uptrend-Only)
    Downloads 2 years of real SPY daily candles from Yahoo Finance
    and runs SimplifiedBreakoutStrategy in STRONG_UPTREND-only mode.

    Validates that the strategy edge holds on real market data
    before committing to live or paper trading.

    Date range: 2022-01-01 → 2024-03-14
    Strategy:   SimplifiedBreakoutStrategy (STRONG_UPTREND only)
    Capital:    $10,000 starting

    Expected results (real market):
      Total trades:  35-50
      Win rate:      ≥ 90%
      Profit factor: ≥ 1.6
      Max drawdown:  ≤ 15%
    ======================================================
     */
    @Operation(summary = "SPY 2-year real data backtest",
               description = "Downloads real SPY daily candles (Yahoo Finance, 2022-01-01 to 2024-03-14) and " +
                             "runs SimplifiedBreakoutStrategy in STRONG_UPTREND-only mode.")
    @GetMapping("/backtest/real/spy-2years-uptrend-only")
    public Map<String, Object> backtestRealSPYUptrendOnly() {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("REAL DATA BACKTEST: SPY 2-Year History (Uptrend-Only Strategy)");
        System.out.println("=".repeat(80));

        LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                "SPY", 500, from, to);

        System.out.println("Downloaded " + candles.size() + " real SPY candles");
        System.out.println("Date range: " + from + " → " + to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",   yahooFinanceMarketDataProvider.getProviderName());
        response.put("strategy",   simplifiedBreakoutStrategy.getName());
        response.put("dateFrom",   from.toString());
        response.put("dateTo",     to.toString());
        response.put("totalCandles", candles.size());

        if (candles.isEmpty()) {
            System.err.println("[RealDataBacktest] No candles downloaded — check network/Yahoo Finance");
            response.put("error", "No data downloaded. Check server logs. " +
                    "Yahoo Finance may be temporarily unavailable.");
            return response;
        }

        BacktestResult result = backtestEngine.runStrategy("SPY", candles, simplifiedBreakoutStrategy);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("REAL DATA RESULTS:");
        System.out.println("Total Trades:   " + result.getTotalTrades());
        System.out.println("Win Rate:       " + result.getWinRate()
                + " (target ≥ 90%)");
        System.out.println("Profit Factor:  " + result.getProfitFactor()
                + " (target ≥ 1.6)");
        System.out.println("Total PnL:      $" + result.getTotalPnL());
        System.out.println("Max Drawdown:   " + result.getMaxDrawdown()
                + " (target ≤ 15%)");
        System.out.println("=".repeat(80) + "\n");

        // Pass/fail assessment
        boolean winRatePass     = result.getWinRate()
                .compareTo(BigDecimal.valueOf(0.90)) >= 0;
        boolean profitFactorPass = result.getProfitFactor()
                .compareTo(BigDecimal.valueOf(1.6))  >= 0;
        boolean drawdownPass    = result.getMaxDrawdown()
                .compareTo(BigDecimal.valueOf(0.15)) <= 0;
        boolean tradeCountPass  = result.getTotalTrades() >= 10;
        boolean allPass = winRatePass && profitFactorPass && drawdownPass && tradeCountPass;

        response.put("totalTrades",      result.getTotalTrades());
        response.put("winningTrades",    result.getWinningTrades());
        response.put("losingTrades",     result.getLosingTrades());
        response.put("winRate",          result.getWinRate());
        response.put("profitFactor",     result.getProfitFactor());
        response.put("totalPnL",         result.getTotalPnL());
        response.put("startCapital",     result.getStartingCapital());
        response.put("endCapital",       result.getEndingCapital());
        response.put("maxDrawdown",      result.getMaxDrawdown());
        response.put("expectancy",       result.getExpectancy());

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("winRate_pass",     winRatePass     + " (≥90%)");
        validation.put("profitFactor_pass", profitFactorPass + " (≥1.6)");
        validation.put("drawdown_pass",    drawdownPass    + " (≤15%)");
        validation.put("tradeCount_pass",  tradeCountPass  + " (≥10 trades)");
        validation.put("overall",          allPass
                ? "✅ PASS — Strategy validated on real data"
                : "⚠️ FAIL — Needs adjustment before live trading");
        response.put("validation", validation);

        return response;
    }

    /*
    ======================================================
    ✅ REAL DATA DIAGNOSTIC: Root Cause Analysis for Zero Trades
    Downloads real SPY data and performs a bar-by-bar analysis
    of every condition in SimplifiedBreakoutStrategy to identify
    EXACTLY which condition blocks signal generation.

    Run this FIRST when real-data backtests return 0 trades.

    What it reports:
      - totalCandles: how many bars were downloaded
      - regimeDistribution: how many bars each regime is detected
      - conditionBreakdown: pass/fail counts for each of the 5
        strategy conditions (regime → ATR → volume → breakout → RSI)
      - sampleMetrics: average ATR%, slope thresholds for reference
      - diagnosis: plain-language root cause

    Date range: 2022-01-01 → 2024-03-14 (~2.25-year SPY history)
    ======================================================
     */
    @GetMapping("/backtest/debug/real-data")
    public Map<String, Object> debugRealData() {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("REAL DATA DIAGNOSTIC — Root Cause Analysis for Zero Trades");
        System.out.println("=".repeat(80));

        LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        List<Candle> candles = yahooFinanceMarketDataProvider.getCandles("SPY", MAX_CANDLES_FOR_DIAGNOSTICS, from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol",      "SPY");
        response.put("dateFrom",    from.toString());
        response.put("dateTo",      to.toString());
        response.put("totalCandles", candles.size());

        if (candles.isEmpty()) {
            response.put("diagnosis",
                    "❌ DATA LAYER: Yahoo Finance returned 0 candles. " +
                    "Check network connectivity. " +
                    "Run /backtest/real/spy-2years-uptrend-only for full details.");
            return response;
        }

        // ── Reset regime service so this run is clean ────────────────────────
        marketRegimeService.reset();

        AtrCalculator atrCalc = new AtrCalculator();

        // Regime distribution counters
        Map<String, Integer> regimeCounts = new LinkedHashMap<>();
        for (MarketRegimeService.MarketRegime r : MarketRegimeService.MarketRegime.values()) {
            regimeCounts.put(r.name(), 0);
        }

        // Condition pass counters (cumulative funnel)
        int barsAnalyzed  = 0;
        int regimePass    = 0;
        int atrPass       = 0;
        int volumePass    = 0;
        int breakoutPass  = 0;
        int rsiPass       = 0;

        // Metrics accumulators
        BigDecimal sumAtrPct   = BigDecimal.ZERO;
        int        atrSamples  = 0;
        BigDecimal sumSlope    = BigDecimal.ZERO;
        int        slopeSamples = 0;

        for (int i = SimplifiedBreakoutStrategy.MIN_CANDLES; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);
            MarketRegimeService.MarketRegime regime = marketRegimeService.detect(subset);

            regimeCounts.merge(regime.name(), 1, Integer::sum);
            barsAnalyzed++;

            // ── Condition 1: STRONG_UPTREND ───────────────────────────────────
            if (regime != MarketRegimeService.MarketRegime.STRONG_UPTREND) continue;
            regimePass++;

            Candle  current = subset.get(subset.size() - 1);
            BigDecimal price   = current.getClose();
            BigDecimal barHigh = current.getHigh();

            // ── Condition 2: ATR > 1.2% of price ─────────────────────────────
            BigDecimal atr    = atrCalc.calculate(subset, 14);
            BigDecimal atrPct = price.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : atr.divide(price, 6, RoundingMode.HALF_UP);

            sumAtrPct = sumAtrPct.add(atrPct);
            atrSamples++;

            if (atrPct.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT)) < 0) continue;
            atrPass++;

            // ── Condition 3: Volume ≥ 70% of 20-bar average ───────────────────
            long currentVol = current.getVolume();
            long avgVol     = diagAvgVolume(subset, 20);

            BigDecimal volumeRatio = (avgVol == 0)
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(currentVol)
                            .divide(BigDecimal.valueOf(avgVol), 6, RoundingMode.HALF_UP);

            if (volumeRatio.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT)) < 0) continue;
            volumePass++;

            // ── Condition 4: Price breakout above 5-bar high + buffer ───────────
            BigDecimal highest5      = diagHighestHigh(subset, SimplifiedBreakoutStrategy.BREAKOUT_PERIOD);
            BigDecimal breakoutLevel = highest5.multiply(BigDecimal.valueOf(SimplifiedBreakoutStrategy.BREAKOUT_BUFFER));

            if (barHigh.compareTo(breakoutLevel) <= 0) continue;
            breakoutPass++;

            // ── Condition 5: RSI > 50 ─────────────────────────────────────────
            try {
                BigDecimal rsi = rsiCalculator.calculate(subset);
                if (rsi.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.RSI_MIN)) < 0) continue;
                rsiPass++;
            } catch (Exception e) {
                // Not enough bars for RSI — skip
                continue;
            }
        }

        // ── Slope metrics (sampled from last 60 bars of data) ─────────────────
        if (candles.size() >= 60) {
            List<Candle> tail = candles.subList(Math.max(0, candles.size() - 60), candles.size());
            BigDecimal ma20 = diagMovingAverage(tail, 20);
            BigDecimal ma50 = diagMovingAverage(tail, 50);
            if (ma50.compareTo(BigDecimal.ZERO) > 0) {
                sumSlope = ma20.subtract(ma50).divide(ma50, 6, RoundingMode.HALF_UP);
                slopeSamples = 1;
            }
        }

        // ── Build response ────────────────────────────────────────────────────
        response.put("barsAnalyzed",       barsAnalyzed);
        response.put("regimeDistribution", regimeCounts);

        Map<String, String> conditions = new LinkedHashMap<>();
        conditions.put("c1_regime_STRONG_UPTREND",
                regimePass + "/" + barsAnalyzed + " bars");
        conditions.put("c2_atr_above_" + (int)(SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT * 100) + "pct",
                (regimePass > 0 ? atrPass + "/" + regimePass : "n/a") + " bars");
        conditions.put("c3_volume_above_" + (int)(SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT * 100) + "pct",
                (atrPass > 0 ? volumePass + "/" + atrPass : "n/a") + " bars");
        conditions.put("c4_high_breakout_" + SimplifiedBreakoutStrategy.BREAKOUT_PERIOD + "bar",
                (volumePass > 0 ? breakoutPass + "/" + volumePass : "n/a") + " bars");
        conditions.put("c5_rsi_above_" + (int) SimplifiedBreakoutStrategy.RSI_MIN,
                (breakoutPass > 0 ? rsiPass + "/" + breakoutPass : "n/a") + " bars");
        conditions.put("all_conditions_met", rsiPass + " bars");
        response.put("conditionBreakdown", conditions);

        Map<String, String> metrics = new LinkedHashMap<>();
        if (atrSamples > 0) {
            BigDecimal avgAtrPct = sumAtrPct
                    .divide(BigDecimal.valueOf(atrSamples), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            metrics.put("avgAtrPct_in_uptrend_bars",
                    avgAtrPct.setScale(3, RoundingMode.HALF_UP) + "% (threshold: "
                    + (SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT * 100) + "%)");
        }
        if (slopeSamples > 0) {
            metrics.put("currentMa20Ma50Slope",
                    sumSlope.multiply(BigDecimal.valueOf(100)).setScale(3, RoundingMode.HALF_UP)
                    + "% (STRONG_UPTREND needs >"
                    + (MarketRegimeService.STRONG_UPTREND_SLOPE_THRESHOLD * 100) + "%)");
        }
        response.put("sampleMetrics", metrics);

        // ── Plain-language root cause ──────────────────────────────────────────
        String diagnosis;
        if (regimePass == 0) {
            diagnosis = "❌ ROOT CAUSE — REGIME: STRONG_UPTREND was never detected across "
                    + barsAnalyzed + " bars. The regime thresholds (slope>"
                    + (MarketRegimeService.STRONG_UPTREND_SLOPE_THRESHOLD * 100) + "%, volatility<"
                    + (MarketRegimeService.HIGH_VOLATILITY_RANGE_THRESHOLD * 100) + "%) "
                    + "may not match real SPY daily data. Consider lowering the slope threshold "
                    + "in MarketRegimeService.classifyRegime().";
        } else if (atrPass == 0) {
            diagnosis = "❌ ROOT CAUSE — ATR: ATR% is below the 1.2% threshold on all "
                    + regimePass + " STRONG_UPTREND bars. Real daily SPY data has lower "
                    + "volatility than mock data. Lower VOLATILITY_THRESHOLD_PCT in "
                    + "SimplifiedBreakoutStrategy (e.g. 0.005 = 0.5%).";
        } else if (volumePass == 0) {
            diagnosis = "❌ ROOT CAUSE — VOLUME: Volume ratio falls below 70% of the 20-bar "
                    + "average on all " + atrPass + " ATR-passing bars. Real SPY volume "
                    + "can be erratic around earnings/holidays. Lower MIN_VOLUME_RATIO_PCT "
                    + "in SimplifiedBreakoutStrategy.";
        } else if (breakoutPass == 0) {
            double bufferPct = (SimplifiedBreakoutStrategy.BREAKOUT_BUFFER - 1.0) * 100.0;
            diagnosis = "❌ ROOT CAUSE — BREAKOUT: Bar high never breaks above the "
                    + SimplifiedBreakoutStrategy.BREAKOUT_PERIOD + "-bar highest high + "
                    + String.format("%.2f", bufferPct) + "% buffer "
                    + "while the other conditions hold. "
                    + "Run /backtest/debug/real-data-breakout-test to find the first buffer level that generates trades.";
        } else if (rsiPass == 0) {
            diagnosis = "❌ ROOT CAUSE — RSI: RSI is below " + (int) SimplifiedBreakoutStrategy.RSI_MIN
                    + " on all " + breakoutPass + " breakout bars. Lower RSI_MIN in "
                    + "SimplifiedBreakoutStrategy (e.g. 45).";
        } else {
            diagnosis = "✅ " + rsiPass + " bars pass ALL conditions. "
                    + "Run /backtest/real/spy-2years-uptrend-only to execute the full backtest.";
        }
        response.put("diagnosis", diagnosis);

        System.out.println("DIAGNOSTIC COMPLETE — " + diagnosis);
        System.out.println("=".repeat(80) + "\n");

        return response;
    }

    // ── Private diagnostic helpers ────────────────────────────────────────────

    /*
    ======================================================
    ✅ BREAKOUT BUFFER SWEEP: Find the minimum buffer that generates trades
    Downloads real SPY data and counts how many bars produce a price breakout
    at each of six candidate BREAKOUT_BUFFER multipliers.

    Use this endpoint when /backtest/debug/real-data reports
    "Price never breaks above the 5-bar high" to identify the
    first buffer level that actually generates signals.

    The response also indicates which buffer is currently active in
    SimplifiedBreakoutStrategy and recommends the tightest buffer
    (smallest % above the 5-bar high) that yields at least 10 breakouts.
    ======================================================
     */
    @GetMapping("/backtest/debug/real-data-breakout-test")
    public Map<String, Object> testBreakoutLevels() {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BREAKOUT BUFFER SWEEP — Find minimum buffer that generates trades");
        System.out.println("=".repeat(80));

        LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        List<Candle> candles = yahooFinanceMarketDataProvider.getCandles("SPY", MAX_CANDLES_FOR_DIAGNOSTICS, from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol",       "SPY");
        response.put("dateFrom",     from.toString());
        response.put("dateTo",       to.toString());
        response.put("totalCandles", candles.size());
        response.put("activeBuffer", SimplifiedBreakoutStrategy.BREAKOUT_BUFFER
                + " (" + String.format("%.3f", (SimplifiedBreakoutStrategy.BREAKOUT_BUFFER - 1.0) * 100) + "% above 5-bar high)");

        if (candles.isEmpty()) {
            response.put("error", "Yahoo Finance returned 0 candles. Check network connectivity.");
            return response;
        }

        // Candidate multipliers to sweep (1 + buffer fraction)
        double[] buffers = {
            1.00001,  // 0.001%
            1.0001,   // 0.01%
            1.0005,   // 0.05%
            1.0020,   // 0.20%
            1.0050,   // 0.50%
            1.0100,   // 1.00%
        };

        // Pre-compute which bars pass conditions 1-3 (regime + ATR + volume) so
        // we re-use this funnel for every buffer without re-running the full loop.
        marketRegimeService.reset();
        AtrCalculator atrCalc = new AtrCalculator();
        List<Integer> funnelIndices = new ArrayList<>();

        for (int i = SimplifiedBreakoutStrategy.MIN_CANDLES; i < candles.size(); i++) {
            List<Candle> subset = candles.subList(0, i + 1);
            MarketRegimeService.MarketRegime regime = marketRegimeService.detect(subset);
            if (regime != MarketRegimeService.MarketRegime.STRONG_UPTREND) continue;

            Candle current = subset.get(subset.size() - 1);
            BigDecimal price = current.getClose();

            BigDecimal atr    = atrCalc.calculate(subset, 14);
            BigDecimal atrPct = price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : atr.divide(price, 6, RoundingMode.HALF_UP);
            if (atrPct.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT)) < 0) continue;

            long avgVol     = diagAvgVolume(subset, 20);
            long currentVol = current.getVolume();
            BigDecimal volumeRatio = (avgVol == 0) ? BigDecimal.ZERO
                    : BigDecimal.valueOf(currentVol).divide(BigDecimal.valueOf(avgVol), 6, RoundingMode.HALF_UP);
            if (volumeRatio.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT)) < 0) continue;

            funnelIndices.add(i);
        }

        int funnelSize = funnelIndices.size();
        response.put("barsPassingConditions1to3", funnelSize);

        // Now sweep each buffer
        Map<String, Object> bufferResults = new LinkedHashMap<>();
        String recommendation = null;

        for (double buffer : buffers) {
            int breakouts = 0;
            for (int idx : funnelIndices) {
                List<Candle> subset = candles.subList(0, idx + 1);
                BigDecimal barHigh  = subset.get(subset.size() - 1).getHigh();
                BigDecimal highest5 = diagHighestHigh(subset, SimplifiedBreakoutStrategy.BREAKOUT_PERIOD);
                BigDecimal level    = highest5.multiply(BigDecimal.valueOf(buffer));
                if (barHigh.compareTo(level) > 0) breakouts++;
            }

            double pct = (buffer - 1.0) * 100.0;
            String key = String.format("buffer_%.3f_pct", pct);
            String label = String.format("%.3f%% (multiplier %.5f): %d/%d breakouts",
                    pct, buffer, breakouts, funnelSize);
            bufferResults.put(key, label);

            System.out.printf("  Buffer %.3f%% → %d breakouts%n", pct, breakouts);

            if (recommendation == null && breakouts >= 10) {
                recommendation = String.format(
                        "✅ First buffer with ≥10 breakouts: %.3f%% (multiplier=%.5f, %d trades). "
                        + "Set BREAKOUT_BUFFER=%.5f in SimplifiedBreakoutStrategy.java.",
                        pct, buffer, breakouts, buffer);
            }
        }

        response.put("bufferSweep", bufferResults);
        response.put("recommendation", recommendation != null ? recommendation
                : "❌ No buffer level (up to 1.00%) produced ≥10 breakouts. "
                  + "The breakout condition is not the bottleneck — investigate regime or data quality.");

        System.out.println("SWEEP COMPLETE — " + (recommendation != null ? recommendation
                : "No buffer reached 10 breakouts"));
        System.out.println("=".repeat(80) + "\n");

        return response;
    }

    /*
    ======================================================
    ✅ CANDLE ANATOMY REPORT: Bar-by-bar inspection for the first 50 uptrend bars
    ======================================================
    Prints every bar that passes conditions 1–3 (regime + ATR + volume) and shows:
      - bar index and date
      - unadjusted close and high for the current bar
      - highest high of the prior 5 bars (unadjusted)
      - breakout level (5-bar high × buffer)
      - whether the breakout condition is met
    Use this to confirm that open/high/low/close are all on the same price scale
    after the adjusted-close fix.
    ======================================================
     */
    @GetMapping("/backtest/debug/real-data-candle-anatomy")
    public Map<String, Object> candleAnatomy() {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("CANDLE ANATOMY REPORT — first 50 STRONG_UPTREND bars");
        System.out.println("=".repeat(80));

        LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                "SPY", MAX_CANDLES_FOR_DIAGNOSTICS, from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol",       "SPY");
        response.put("dateFrom",     from.toString());
        response.put("dateTo",       to.toString());
        response.put("totalCandles", candles.size());

        if (candles.isEmpty()) {
            response.put("error", "Yahoo Finance returned 0 candles. Check network connectivity.");
            return response;
        }

        // Log first 5 raw candles so we can verify open ≤ close ≤ high etc.
        List<Map<String, String>> rawSample = new ArrayList<>();
        int rawLimit = Math.min(5, candles.size());
        for (int i = 0; i < rawLimit; i++) {
            Candle c = candles.get(i);
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("index", String.valueOf(i));
            entry.put("date",  c.getTime().toLocalDate().toString());
            entry.put("open",  c.getOpen().setScale(4, RoundingMode.HALF_UP).toPlainString());
            entry.put("high",  c.getHigh().setScale(4, RoundingMode.HALF_UP).toPlainString());
            entry.put("low",   c.getLow().setScale(4, RoundingMode.HALF_UP).toPlainString());
            entry.put("close", c.getClose().setScale(4, RoundingMode.HALF_UP).toPlainString());
            entry.put("volume", String.valueOf(c.getVolume()));
            entry.put("closeNotAboveHigh", c.getClose().compareTo(c.getHigh()) <= 0 ? "✅ ok" : "❌ DATA BUG");
            rawSample.add(entry);
        }
        response.put("rawCandleSample_first5", rawSample);

        marketRegimeService.reset();
        AtrCalculator atrCalc = new AtrCalculator();

        List<Map<String, String>> anatomyRows = new ArrayList<>();
        int uptrendBarsFound = 0;
        int breakoutsFound   = 0;

        for (int i = SimplifiedBreakoutStrategy.MIN_CANDLES; i < candles.size()
                && uptrendBarsFound < 50; i++) {

            List<Candle> subset = candles.subList(0, i + 1);
            MarketRegimeService.MarketRegime regime = marketRegimeService.detect(subset);
            if (regime != MarketRegimeService.MarketRegime.STRONG_UPTREND) continue;

            Candle cur = subset.get(subset.size() - 1);
            BigDecimal price = cur.getClose();
            BigDecimal high  = cur.getHigh();

            // ATR check
            BigDecimal atr     = atrCalc.calculate(subset, 14);
            BigDecimal atrPct  = price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : atr.divide(price, 6, RoundingMode.HALF_UP);
            if (atrPct.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT)) < 0)
                continue;

            // Volume check
            long avgVol = diagAvgVolume(subset, 20);
            long curVol = cur.getVolume();
            BigDecimal volRatio = (avgVol == 0) ? BigDecimal.ZERO
                    : BigDecimal.valueOf(curVol).divide(BigDecimal.valueOf(avgVol), 6, RoundingMode.HALF_UP);
            if (volRatio.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT)) < 0)
                continue;

            // Breakout check
            BigDecimal highest5      = diagHighestHigh(subset, SimplifiedBreakoutStrategy.BREAKOUT_PERIOD);
            BigDecimal breakoutLevel = highest5.multiply(BigDecimal.valueOf(SimplifiedBreakoutStrategy.BREAKOUT_BUFFER));
            boolean    breakout      = high.compareTo(breakoutLevel) > 0;
            if (breakout) breakoutsFound++;

            uptrendBarsFound++;

            Map<String, String> row = new LinkedHashMap<>();
            row.put("barIndex",      String.valueOf(i));
            row.put("date",          cur.getTime().toLocalDate().toString());
            row.put("close",         price.setScale(4, RoundingMode.HALF_UP).toPlainString());
            row.put("barHigh",       high.setScale(4, RoundingMode.HALF_UP).toPlainString());
            row.put("highest5High",  highest5.setScale(4, RoundingMode.HALF_UP).toPlainString());
            row.put("breakoutLevel", breakoutLevel.setScale(4, RoundingMode.HALF_UP).toPlainString());
            row.put("breakout",      breakout ? "✅ YES" : "❌ no  (high=" + high.setScale(2, RoundingMode.HALF_UP)
                    + " vs level=" + breakoutLevel.setScale(2, RoundingMode.HALF_UP) + ")");
            row.put("closeVsHigh",   price.compareTo(high) <= 0 ? "close≤high ✅" : "close>high ❌ DATA BUG");
            anatomyRows.add(row);
        }

        response.put("uptrendBarsAnalyzed", uptrendBarsFound);
        response.put("breakoutsFound",      breakoutsFound);
        response.put("anatomy",             anatomyRows);

        String diagnosis;
        if (breakoutsFound > 0) {
            diagnosis = "✅ Breakouts detected (" + breakoutsFound + "/" + uptrendBarsFound
                    + "). Data is healthy — strategy should generate trades.";
        } else if (uptrendBarsFound == 0) {
            diagnosis = "⚠️ No bars passed regime+ATR+volume filter. Check regime detection.";
        } else {
            // Check if close > high ever (data bug indicator)
            long badBars = anatomyRows.stream()
                    .filter(r -> r.get("closeVsHigh").contains("BUG")).count();
            if (badBars > 0) {
                diagnosis = "❌ DATA BUG: close > high on " + badBars + " bars. "
                        + "Adjusted-close is still being mixed with unadjusted high — check the fix.";
            } else {
                diagnosis = "❌ Bar high ≤ breakout level on all bars (data consistent), but high never exceeds "
                        + "the 5-bar highest high × buffer. The breakout threshold may still be too strict "
                        + "for current market conditions, or regime windows are too narrow.";
            }
        }
        response.put("diagnosis", diagnosis);

        System.out.println("ANATOMY COMPLETE — uptrend bars: " + uptrendBarsFound
                + ", breakouts: " + breakoutsFound);
        System.out.println("=".repeat(80) + "\n");

        return response;
    }

    /*
    ======================================================
    ✅ REAL TRADE ANATOMY: Bar-by-bar dissection of every trade on real SPY data
    Replays SimplifiedBreakoutStrategy against 2022-2024 SPY daily bars and, for
    each trade entered, reports:
      - Entry date, price, SL, TP, R:R ratio
      - Regime at entry + all 5 entry conditions
      - Bar-by-bar close price from entry to exit with % from entry
      - Max Adverse Excursion (MAE) and Max Favorable Excursion (MFE)
      - Plain-language failure analysis
      - Comparison note: why mock data wins but real data loses
      - Actionable fix recommendations

    Slippage is excluded from this diagnostic so every run is deterministic.
    Date range: 2022-01-01 → 2024-03-14 (same as other real-data endpoints)
    ======================================================
    */
    @GetMapping("/backtest/debug/real-trade-anatomy")
    public Map<String, Object> realTradeAnatomy() {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("REAL TRADE ANATOMY — Bar-by-bar dissection of every trade on real SPY data");
        System.out.println("=".repeat(80));

        LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                "SPY", MAX_CANDLES_FOR_DIAGNOSTICS, from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol",       "SPY");
        response.put("dateFrom",     from.toString());
        response.put("dateTo",       to.toString());
        response.put("totalCandles", candles.size());
        response.put("strategy",     "SimplifiedBreakoutStrategy");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("startCapital",   "$10,000");
        params.put("riskPerTrade",   "1% of capital");
        params.put("slDistance",     "2% below entry fill");
        params.put("tpDistance",     "4% above entry fill");
        params.put("slippageNote",   "Excluded (deterministic anatomy — no random gap risk)");
        response.put("backtestParams", params);

        if (candles.isEmpty()) {
            response.put("error",
                    "Yahoo Finance returned 0 candles. Check network connectivity.");
            return response;
        }

        // ── Replay the strategy (same logic as BacktestEngine, no slippage) ──
        marketRegimeService.reset();
        AtrCalculator atrCalc = new AtrCalculator();

        final BigDecimal START_CAPITAL   = BigDecimal.valueOf(10_000);
        final BigDecimal COMMISSION      = BigDecimal.ONE;
        final BigDecimal RISK_PER_TRADE  = BigDecimal.valueOf(0.01);
        final BigDecimal SL_PCT          = BigDecimal.valueOf(0.02);
        final BigDecimal TP_PCT          = BigDecimal.valueOf(0.04);

        BigDecimal capital = START_CAPITAL;

        // Open-position state
        boolean  pendingSignal    = false;
        int      signalBar        = -1;
        int      entryBar         = -1;
        BigDecimal entryFill      = null;
        BigDecimal stopLoss       = null;
        BigDecimal takeProfit     = null;
        BigDecimal tradeQty       = null;
        BigDecimal capitalAtEntry = null;
        MarketRegimeService.MarketRegime regimeAtEntry = null;
        List<Map<String, Object>> barsInTrade         = null;

        List<Map<String, Object>> trades = new ArrayList<>();

        for (int i = 20; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);
            MarketRegimeService.MarketRegime regime = marketRegimeService.detect(subset);
            BigDecimal closePrice = candles.get(i).getClose();

            // ── 1. Check SL / TP on open position ──────────────────────────
            if (entryFill != null) {

                boolean hitSl = closePrice.compareTo(stopLoss)   <= 0;
                boolean hitTp = closePrice.compareTo(takeProfit)  >= 0;

                BigDecimal pctVsEntry = entryFill.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : closePrice.subtract(entryFill)
                                .divide(entryFill, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));

                Map<String, Object> barRow = new LinkedHashMap<>();
                barRow.put("barIndex",    i);
                barRow.put("date",        candles.get(i).getTime().toLocalDate().toString());
                barRow.put("close",       closePrice.setScale(2, RoundingMode.HALF_UP).toPlainString());
                barRow.put("pctFromEntry",pctVsEntry.setScale(2, RoundingMode.HALF_UP) + "%");
                barRow.put("regime",      regime.name());
                barRow.put("slHit",       hitSl ? ("❌ YES (SL=" + stopLoss.setScale(2, RoundingMode.HALF_UP) + ")") : "no");
                barRow.put("tpHit",       hitTp ? ("✅ YES (TP=" + takeProfit.setScale(2, RoundingMode.HALF_UP) + ")") : "no");
                barsInTrade.add(barRow);

                if (hitSl || hitTp) {
                    String exitReason = hitSl ? "SL" : "TP";
                    int    barsHeld   = i - entryBar;

                    // Build the trade record
                    Map<String, Object> trade = anatomyBuildTrade(
                            trades.size() + 1,
                            candles.get(entryBar), entryBar, entryFill,
                            stopLoss, takeProfit, tradeQty, capitalAtEntry,
                            candles.get(i), i, closePrice, exitReason,
                            regimeAtEntry, regime, barsHeld,
                            barsInTrade,
                            candles.subList(0, entryBar + 1), atrCalc);
                    trades.add(trade);

                    // Return proceeds to capital
                    capital = capital.add(closePrice.multiply(tradeQty).subtract(COMMISSION));

                    // Reset trade state
                    entryFill      = null;
                    stopLoss       = null;
                    takeProfit     = null;
                    tradeQty       = null;
                    capitalAtEntry = null;
                    regimeAtEntry  = null;
                    barsInTrade    = null;
                    pendingSignal  = false;
                }
            }

            // ── 2. Delayed fill: enter on bar after the signal ───────────
            if (pendingSignal && entryFill == null) {

                BigDecimal fillPrice = closePrice; // no slippage for deterministic output

                BigDecimal sl = fillPrice.multiply(BigDecimal.ONE.subtract(SL_PCT))
                        .setScale(4, RoundingMode.HALF_UP);
                BigDecimal tp = fillPrice.multiply(BigDecimal.ONE.add(TP_PCT))
                        .setScale(4, RoundingMode.HALF_UP);

                BigDecimal riskPerTrade  = capital.multiply(RISK_PER_TRADE);
                BigDecimal riskPerShare  = fillPrice.subtract(sl).abs();

                if (riskPerShare.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal qty         = riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);
                    BigDecimal maxAfford   = capital.divide(fillPrice, 0, RoundingMode.DOWN);
                    qty = qty.min(maxAfford);

                    if (qty.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal cost = fillPrice.multiply(qty).add(COMMISSION);
                        if (cost.compareTo(capital) <= 0) {
                            capitalAtEntry = capital;
                            capital        = capital.subtract(cost);
                            entryFill      = fillPrice;
                            entryBar       = i;
                            stopLoss       = sl;
                            takeProfit     = tp;
                            tradeQty       = qty;
                            regimeAtEntry  = regime;
                            barsInTrade    = new ArrayList<>();
                        }
                    }
                }
                pendingSignal = false;
            }

            // ── 3. Evaluate strategy (only when flat) ────────────────────
            if (entryFill == null) {
                TradingSignal signal = simplifiedBreakoutStrategy.evaluate(subset);
                if (signal == TradingSignal.BUY) {
                    pendingSignal = true;
                    signalBar     = i;
                }
            }
        }

        // Force-close any remaining position at end of data
        if (entryFill != null && !candles.isEmpty()) {
            Candle last   = candles.get(candles.size() - 1);
            BigDecimal lp = last.getClose();
            int barsHeld  = (candles.size() - 1) - entryBar;
            MarketRegimeService.MarketRegime lastRegime =
                    marketRegimeService.detect(candles);
            Map<String, Object> trade = anatomyBuildTrade(
                    trades.size() + 1,
                    candles.get(entryBar), entryBar, entryFill,
                    stopLoss, takeProfit, tradeQty, capitalAtEntry,
                    last, candles.size() - 1, lp, "FORCE_CLOSE",
                    regimeAtEntry, lastRegime, barsHeld,
                    barsInTrade,
                    candles.subList(0, entryBar + 1), atrCalc);
            trades.add(trade);
            capital = capital.add(lp.multiply(tradeQty).subtract(BigDecimal.ONE));
        }

        // ── Summary ───────────────────────────────────────────────────────────
        long winning = trades.stream()
                .filter(t -> "WIN".equals(t.get("outcome"))).count();
        long losing  = trades.stream()
                .filter(t -> "LOSS".equals(t.get("outcome"))).count();

        response.put("totalTrades",   trades.size());
        response.put("winningTrades", (int) winning);
        response.put("losingTrades",  (int) losing);
        response.put("finalCapital",  capital.setScale(2, RoundingMode.HALF_UP).toPlainString());
        response.put("trades",        trades);

        // ── Mock vs Real comparison note ─────────────────────────────────────
        Map<String, String> comparison = new LinkedHashMap<>();
        comparison.put("mockData_winRate",
                "100% — MockMarketDataService generates perfectly monotone STRONG_UPTREND bars "
                + "with no intra-trend pullbacks, so the 2% SL is never hit.");
        comparison.put("realSPY_winRate",
                "0% — Real SPY daily bars have ATR ≈ 3.7% of price, meaning a 2% SL sits "
                + "inside the normal daily noise. Almost every entry triggers the SL within a "
                + "few bars before the trend continues.");
        comparison.put("coreGap",
                "Mock bars have near-zero bar-to-bar volatility relative to ATR; "
                + "real bars have full daily range. SL and TP must be calibrated to ATR, "
                + "not fixed percentages.");
        response.put("mockVsRealComparison", comparison);

        // ── Recommendations ───────────────────────────────────────────────────
        response.put("recommendations", anatomyRecommendations(trades));

        System.out.println("ANATOMY COMPLETE — " + trades.size() + " trades ("
                + winning + " wins, " + losing + " losses)");
        System.out.println("=".repeat(80) + "\n");

        return response;
    }

    /** Builds a detailed trade record map for the anatomy endpoint. */
    private Map<String, Object> anatomyBuildTrade(
            int tradeNum,
            Candle entryCandle, int entryBarIdx, BigDecimal entryPrice,
            BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal qty, BigDecimal capitalAtEntry,
            Candle exitCandle,  int exitBarIdx,  BigDecimal exitPrice,
            String exitReason,
            MarketRegimeService.MarketRegime entryRegime,
            MarketRegimeService.MarketRegime exitRegime,
            int barsHeld,
            List<Map<String, Object>> barsDuringTrade,
            List<Candle> entrySubset,
            AtrCalculator atrCalc) {

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("tradeNum",    tradeNum);
        t.put("entryBar",    entryBarIdx);
        t.put("entryDate",   entryCandle.getTime().toLocalDate().toString());
        t.put("entryPrice",  entryPrice.setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.put("stopLoss",    stopLoss.setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.put("takeProfit",  takeProfit.setScale(2, RoundingMode.HALF_UP).toPlainString());

        BigDecimal slPct = entryPrice.subtract(stopLoss)
                .divide(entryPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal tpPct = takeProfit.subtract(entryPrice)
                .divide(entryPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal rr = slPct.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : tpPct.divide(slPct, 2, RoundingMode.HALF_UP);

        t.put("slDistancePct",    slPct.setScale(2, RoundingMode.HALF_UP) + "%");
        t.put("tpDistancePct",    tpPct.setScale(2, RoundingMode.HALF_UP) + "%");
        t.put("riskRewardRatio",  rr.toPlainString() + ":1");
        t.put("quantity",         qty.toPlainString());
        t.put("positionSizeUSD",  entryPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.put("capitalAtEntry",   capitalAtEntry != null
                ? capitalAtEntry.setScale(2, RoundingMode.HALF_UP).toPlainString() : "n/a");
        t.put("regimeAtEntry",    entryRegime != null ? entryRegime.name() : "UNKNOWN");

        // Entry conditions at the signal bar
        t.put("entryConditions",  anatomyEntryConditions(entrySubset, atrCalc));

        t.put("exitBar",          exitBarIdx);
        t.put("exitDate",         exitCandle.getTime().toLocalDate().toString());
        t.put("exitPrice",        exitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.put("exitReason",       exitReason);
        t.put("exitRegime",       exitRegime != null ? exitRegime.name() : "UNKNOWN");
        t.put("barsHeld",         barsHeld);

        BigDecimal pnl    = exitPrice.subtract(entryPrice).multiply(qty);
        BigDecimal pnlPct = entryPrice.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : exitPrice.subtract(entryPrice)
                        .divide(entryPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        t.put("pnl",     pnl.setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.put("pnlPct",  pnlPct.setScale(2, RoundingMode.HALF_UP) + "%");
        t.put("outcome", pnl.compareTo(BigDecimal.ZERO) > 0 ? "WIN" : "LOSS");

        // MAE / MFE from the bar-by-bar list
        BigDecimal mae    = BigDecimal.ZERO;
        BigDecimal mfe    = BigDecimal.ZERO;
        int        maeBar = entryBarIdx;
        int        mfeBar = entryBarIdx;

        if (barsDuringTrade != null) {
            for (Map<String, Object> bar : barsDuringTrade) {
                String pctStr = bar.get("pctFromEntry").toString().replace("%", "").trim();
                try {
                    BigDecimal pct = new BigDecimal(pctStr);
                    if (pct.negate().compareTo(mae) > 0) {
                        mae    = pct.negate();
                        maeBar = (int) bar.get("barIndex");
                    }
                    if (pct.compareTo(mfe) > 0) {
                        mfe    = pct;
                        mfeBar = (int) bar.get("barIndex");
                    }
                } catch (NumberFormatException ignored) { /* skip unparseable */ }
            }
        }

        t.put("maxAdverseExcursion",
                mae.setScale(2, RoundingMode.HALF_UP) + "% below entry (bar " + maeBar + ")");
        t.put("maxFavorableExcursion",
                mfe.setScale(2, RoundingMode.HALF_UP) + "% above entry (bar " + mfeBar + ")");

        // ATR at entry (for calibration context)
        if (!entrySubset.isEmpty()) {
            BigDecimal atr     = atrCalc.calculate(entrySubset, 14);
            BigDecimal entryPx = entrySubset.get(entrySubset.size() - 1).getClose();
            BigDecimal atrPct  = entryPx.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : atr.divide(entryPx, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
            t.put("atrAtEntry",       atr.setScale(2, RoundingMode.HALF_UP).toPlainString());
            t.put("atrPctAtEntry",    atrPct.setScale(2, RoundingMode.HALF_UP) + "%");
            t.put("slVsAtrRatio",
                    "SL=" + slPct.setScale(1, RoundingMode.HALF_UP) + "% vs ATR="
                            + atrPct.setScale(1, RoundingMode.HALF_UP) + "% — "
                            + (slPct.compareTo(atrPct) < 0
                            ? "⚠️ SL is INSIDE daily ATR noise (will be hit by normal volatility)"
                            : "✅ SL wider than ATR"));
        }

        // Failure analysis
        t.put("failureAnalysis", anatomyFailureAnalysis(
                entryPrice, stopLoss, takeProfit, exitPrice,
                exitReason, mae, mfe, barsHeld, entryRegime, exitRegime));

        t.put("barsDuringTrade", barsDuringTrade != null ? barsDuringTrade : List.of());

        return t;
    }

    /** Returns the 5 entry conditions evaluated at the signal bar. */
    private Map<String, String> anatomyEntryConditions(List<Candle> subset,
                                                        AtrCalculator atrCalc) {
        Map<String, String> c = new LinkedHashMap<>();
        if (subset == null || subset.isEmpty()) return c;

        Candle     cur   = subset.get(subset.size() - 1);
        BigDecimal price = cur.getClose();
        BigDecimal high  = cur.getHigh();

        // C1 – regime already confirmed at call site
        c.put("c1_regime", "✅ STRONG_UPTREND (condition to reach this point)");

        // C2 – ATR
        BigDecimal atr    = atrCalc.calculate(subset, 14);
        BigDecimal atrPct = price.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : atr.divide(price, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
        c.put("c2_atr",
                (atrPct.compareTo(BigDecimal.valueOf(
                        SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT * 100)) >= 0
                        ? "✅ " : "❌ ")
                        + atrPct.setScale(2, RoundingMode.HALF_UP) + "% (min "
                        + (SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT * 100) + "%)");

        // C3 – Volume
        long       avgVol   = diagAvgVolume(subset, 20);
        long       curVol   = cur.getVolume();
        BigDecimal volRatio = (avgVol == 0) ? BigDecimal.ZERO
                : BigDecimal.valueOf(curVol)
                        .divide(BigDecimal.valueOf(avgVol), 2, RoundingMode.HALF_UP);
        c.put("c3_volume",
                (volRatio.compareTo(BigDecimal.valueOf(
                        SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT)) >= 0
                        ? "✅ " : "❌ ")
                        + volRatio.setScale(2, RoundingMode.HALF_UP) + "x avg (min "
                        + SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT + "x)");

        // C4 – Breakout
        BigDecimal highest5 = diagHighestHigh(subset,
                SimplifiedBreakoutStrategy.BREAKOUT_PERIOD);
        BigDecimal breakoutLevel = highest5.multiply(
                BigDecimal.valueOf(SimplifiedBreakoutStrategy.BREAKOUT_BUFFER));
        c.put("c4_breakout",
                (high.compareTo(breakoutLevel) > 0 ? "✅ " : "❌ ")
                        + "high=" + high.setScale(2, RoundingMode.HALF_UP)
                        + " vs level=" + breakoutLevel.setScale(2, RoundingMode.HALF_UP));

        // C5 – RSI
        try {
            BigDecimal rsi = rsiCalculator.calculate(subset);
            c.put("c5_rsi",
                    (rsi.compareTo(BigDecimal.valueOf(SimplifiedBreakoutStrategy.RSI_MIN)) >= 0
                            ? "✅ " : "❌ ")
                            + rsi.setScale(1, RoundingMode.HALF_UP)
                            + " (min " + (int) SimplifiedBreakoutStrategy.RSI_MIN + ")");
        } catch (Exception e) {
            c.put("c5_rsi", "⚠️ RSI calculation error: " + e.getMessage());
        }

        return c;
    }

    /** Produces a plain-language explanation of why a trade failed. */
    private String anatomyFailureAnalysis(
            BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal takeProfit,
            BigDecimal exitPrice,  String exitReason,
            BigDecimal mae, BigDecimal mfe, int barsHeld,
            MarketRegimeService.MarketRegime entryRegime,
            MarketRegimeService.MarketRegime exitRegime) {

        StringBuilder sb = new StringBuilder();

        BigDecimal slPct = entryPrice.subtract(stopLoss)
                .divide(entryPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if ("SL".equals(exitReason)) {
            sb.append("STOP-LOSS HIT after ").append(barsHeld).append(" bar(s). ");
            if (barsHeld <= 2) {
                sb.append("Price reversed almost immediately — entry was likely at a short-term "
                        + "local high with no follow-through. ");
            }
            if (mfe.compareTo(BigDecimal.valueOf(0.5)) < 0) {
                sb.append("Price never moved favorably (MFE=")
                        .append(mfe.setScale(1, RoundingMode.HALF_UP))
                        .append("%) — the breakout was a false breakout with immediate rejection. ");
            }
            sb.append("SL at ").append(slPct.setScale(1, RoundingMode.HALF_UP))
              .append("% below entry sits inside the daily ATR noise of real SPY bars, "
                    + "so normal intraday/daily fluctuation triggers the exit before the trend resumes. ");
        } else if ("TP".equals(exitReason)) {
            sb.append("TAKE-PROFIT HIT — trade won. ");
        } else {
            sb.append("Position closed at end of data (FORCE_CLOSE). ");
        }

        if (exitRegime != null && entryRegime != null && !exitRegime.equals(entryRegime)) {
            sb.append("Regime shifted from ").append(entryRegime.name())
              .append(" → ").append(exitRegime.name()).append(" during the trade, "
                    + "indicating the uptrend ended while the position was open. ");
        }

        sb.append("| ROOT CAUSES: ")
          .append("(1) Fixed 2% SL is inside the average daily ATR (~3.7%) — "
                + "use ATR-based SL (e.g. 1.5×ATR ≈ 5.5%) to give the trade room to breathe. ")
          .append("(2) Breakout entries on real data often retest the breakout level before "
                + "continuing — a 1-bar confirmation or ATR-based entry filter would reduce "
                + "false entries. ")
          .append("(3) 4% TP with 2% SL gives 2:1 R:R on paper, but with a near-100% SL hit "
                + "rate the expectancy is deeply negative — "
                + "widen SL and TP together to maintain R:R above 1.5:1.");

        return sb.toString();
    }

    /** Generates overall fix recommendations based on the full trade list. */
    private List<String> anatomyRecommendations(List<Map<String, Object>> trades) {
        List<String> recs = new ArrayList<>();

        if (trades.isEmpty()) {
            recs.add("❌ No trades generated — strategy did not produce any BUY signals. "
                    + "Run /backtest/debug/real-data for a condition-by-condition breakdown.");
            return recs;
        }

        long lossCount = trades.stream()
                .filter(t -> "LOSS".equals(t.get("outcome"))).count();

        if (lossCount == trades.size()) {
            recs.add("❌ ALL " + trades.size() + " real-data trades lost (0% win rate). "
                    + "Strategy is calibrated for mock data and needs the following fixes:");
        }

        recs.add("🔧 FIX 1 — ATR-based stop-loss: Replace fixed 2% SL with 1.5×ATR(14). "
                + "Real SPY ATR ≈ 3.7% of price → SL ≈ 5.5%. "
                + "This keeps the stop outside normal daily noise. "
                + "Change in BacktestEngine: stopLoss = entryFill × (1 − 1.5 × atrPct).");
        recs.add("🔧 FIX 2 — ATR-based take-profit: Replace fixed 4% TP with 2.5×ATR(14) ≈ 9.25%. "
                + "Maintain ≥1.67:1 R:R even with wider stops. "
                + "Change: takeProfit = entryFill × (1 + 2.5 × atrPct).");
        recs.add("🔧 FIX 3 — Entry confirmation (1-bar delay): After the BUY signal fires, "
                + "only enter if the NEXT bar's CLOSE is still above the breakout level. "
                + "This filters false breakouts that reverse on the same day.");
        recs.add("🔧 FIX 4 — Tighten regime filter: Require MA20/MA50 slope > 3% "
                + "(vs current 2%) to reduce 'borderline uptrend' entries that quickly "
                + "revert to SIDEWAYS.");
        recs.add("🔧 FIX 5 — Reduce position size: With SL widened to ~5.5%, risk per trade "
                + "stays at 1% of capital but quantity drops proportionally, "
                + "reducing drawdown impact when SL is hit.");
        recs.add("📊 MOCK vs REAL ROOT CAUSE: Mock STRONG_UPTREND bars advance ~0.3% each bar "
                + "with near-zero intra-bar range. Real SPY daily bars move 1-4% intraday. "
                + "A 2% SL that is never touched in mock data is hit on nearly every real trade "
                + "by normal daily volatility. All risk parameters must be calibrated against "
                + "real ATR, not mock bar size.");

        return recs;
    }

    /** Highest high of the last {@code period} bars, excluding the current bar. */
    private BigDecimal diagHighestHigh(List<Candle> candles, int period) {
        BigDecimal max = BigDecimal.ZERO;
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        for (int i = start; i < end; i++) {
            if (candles.get(i).getHigh().compareTo(max) > 0) {
                max = candles.get(i).getHigh();
            }
        }
        return max;
    }

    /** Average volume of the last {@code period} bars, excluding the current bar. */
    private long diagAvgVolume(List<Candle> candles, int period) {
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        long sum  = 0;
        int count = 0;
        for (int i = start; i < end; i++) {
            sum += candles.get(i).getVolume();
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    /** Simple moving average of the last {@code period} bars. */
    private BigDecimal diagMovingAverage(List<Candle> candles, int period) {
        if (candles.size() < period) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }

    /*
    ======================================================
    ✅ MULTI-SYMBOL REAL DATA BACKTEST
    Runs SimplifiedBreakoutStrategy against real Yahoo Finance
    daily data for all 13 quality-backtest symbols.

    Symbols: SPY, QQQ, AAPL, MSFT, NVDA, GOOG, AMZN, META,
             TSLA, IWM, XLF, XLE, XLV

    Date range: 2022-01-01 → 2024-03-14 (~2.25 years)

    Returns per-symbol metrics and an aggregated summary.

    Validation thresholds (per symbol):
      Win rate:      ≥ 50%
      Profit factor: ≥ 1.0
      Max drawdown:  ≤ 30%
    ======================================================
     */
    @Operation(summary = "Multi-symbol real-data backtest (primary)",
               description = "Downloads 2 years of real daily candles (Yahoo Finance) for 13 symbols and runs " +
                             "RobustTrendBreakoutStrategy (MA20>MA50 + 20-bar close breakout + RSI>50). " +
                             "Quality gates: win rate ≥60%, profit factor ≥1.2, drawdown ≤25%, trades ≥5.")
    @GetMapping("/backtest/real/multi")
    public Map<String, Object> backtestRealMultiSymbol() {

        final List<String> SYMBOLS = List.of(
                "SPY", "QQQ", "AAPL", "MSFT", "NVDA",
                "GOOG", "AMZN", "META", "TSLA",
                "IWM", "XLF", "XLE", "XLV"
        );

        final LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        final LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("MULTI-SYMBOL REAL DATA BACKTEST (" + SYMBOLS.size() + " symbols)");
        System.out.println("Date range: " + from + " → " + to);
        System.out.println("Strategy:   " + robustBreakoutStrategy.getName());
        System.out.println("=".repeat(80));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",   yahooFinanceMarketDataProvider.getProviderName());
        response.put("strategy",   robustBreakoutStrategy.getName());
        response.put("dateFrom",   from.toString());
        response.put("dateTo",     to.toString());
        response.put("symbols",    SYMBOLS);

        List<Map<String, Object>> symbolResults = new ArrayList<>();

        int passCount  = 0;
        int totalCount = 0;

        for (String symbol : SYMBOLS) {
            System.out.println("\n--- Backtesting " + symbol + " ---");

            List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                    symbol, MAX_CANDLES_FOR_BACKTEST, from, to);

            Map<String, Object> symbolResult = new LinkedHashMap<>();
            symbolResult.put("symbol",       symbol);
            symbolResult.put("totalCandles", candles.size());

            if (candles.isEmpty()) {
                symbolResult.put("status", "ERROR");
                symbolResult.put("error",  "No data downloaded from Yahoo Finance");
                symbolResults.add(symbolResult);
                totalCount++;
                continue;
            }

            BacktestResult result = backtestEngine.runStrategy(
                    symbol, candles, robustBreakoutStrategy);

            boolean winRatePass     = result.getWinRate()
                    .compareTo(BigDecimal.valueOf(0.60)) >= 0;
            boolean profitFactorPass = result.getProfitFactor()
                    .compareTo(BigDecimal.valueOf(1.2)) >= 0;
            boolean drawdownPass    = result.getMaxDrawdown()
                    .compareTo(BigDecimal.valueOf(0.25)) <= 0;
            boolean tradeCountPass  = result.getTotalTrades() >= 5;
            boolean allPass = winRatePass && profitFactorPass && drawdownPass && tradeCountPass;

            symbolResult.put("totalTrades",   result.getTotalTrades());
            symbolResult.put("winningTrades", result.getWinningTrades());
            symbolResult.put("losingTrades",  result.getLosingTrades());
            symbolResult.put("winRate",       result.getWinRate());
            symbolResult.put("profitFactor",  result.getProfitFactor());
            symbolResult.put("totalPnL",      result.getTotalPnL());
            symbolResult.put("startCapital",  result.getStartingCapital());
            symbolResult.put("endCapital",    result.getEndingCapital());
            symbolResult.put("maxDrawdown",   result.getMaxDrawdown());
            symbolResult.put("expectancy",    result.getExpectancy());

            Map<String, Object> validation = new LinkedHashMap<>();
            validation.put("winRate_pass",      winRatePass     + " (≥60%)");
            validation.put("profitFactor_pass", profitFactorPass + " (≥1.2)");
            validation.put("drawdown_pass",     drawdownPass    + " (≤25%)");
            validation.put("tradeCount_pass",   tradeCountPass  + " (≥5 trades)");
            validation.put("overall",           allPass ? "✅ PASS" : "⚠️ FAIL");
            symbolResult.put("validation", validation);

            symbolResult.put("status", allPass ? "PASS" : "FAIL");

            if (allPass) passCount++;
            totalCount++;

            symbolResults.add(symbolResult);

            System.out.println("[MultiBacktest] " + symbol
                    + "  trades=" + result.getTotalTrades()
                    + "  winRate=" + result.getWinRate()
                    + "  pf=" + result.getProfitFactor()
                    + "  dd=" + result.getMaxDrawdown()
                    + "  status=" + (allPass ? "PASS" : "FAIL"));
        }

        response.put("results", symbolResults);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("symbolsTested",  totalCount);
        summary.put("symbolsPassed",  passCount);
        summary.put("symbolsFailed",  totalCount - passCount);
        summary.put("passRate",       totalCount == 0 ? "N/A"
                : BigDecimal.valueOf(passCount)
                .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                .toPlainString());
        summary.put("overall", passCount == totalCount
                ? "✅ ALL SYMBOLS PASSED"
                : "⚠️ " + (totalCount - passCount) + " SYMBOL(S) FAILED");
        response.put("summary", summary);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("MULTI-SYMBOL BACKTEST COMPLETE");
        System.out.println("Passed: " + passCount + " / " + totalCount);
        System.out.println("=".repeat(80) + "\n");

        return response;
    }

    /*
    ======================================================
    ✅ PER-ASSET FILTER DIAGNOSTICS
    For each of the 13 quality-backtest symbols, runs a
    bar-by-bar filter funnel using the current (default)
    SimplifiedBreakoutStrategy thresholds and reports how
    many bars pass each successive filter gate:

      c1: regime = STRONG_UPTREND
      c2: ATR > threshold
      c3: volume above average ratio
      c4: price breakout above 5-bar high + buffer
      c5: RSI > minimum

    Also identifies the "bottleneck" — the single filter
    that drops the most bars — so callers know which
    parameter to loosen first.

    Endpoint: GET /backtest/real/multi/diagnostics
    ======================================================
     */
    @Operation(summary = "Per-symbol filter diagnostics",
               description = "Bar-by-bar filter funnel analysis for 13 symbols. Shows how many bars pass each " +
                             "condition (regime, ATR, volume, breakout, RSI) and identifies the bottleneck.")
    @GetMapping("/backtest/real/multi/diagnostics")
    public Map<String, Object> multiAssetFilterDiagnostics() {

        final List<String> SYMBOLS = List.of(
                "SPY", "QQQ", "AAPL", "MSFT", "NVDA",
                "GOOG", "AMZN", "META", "TSLA",
                "IWM", "XLF", "XLE", "XLV"
        );

        final LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        final LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PER-ASSET FILTER DIAGNOSTICS (" + SYMBOLS.size() + " symbols)");
        System.out.println("=".repeat(80));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description",
                "Filter funnel counts per symbol. 'bottleneck' is the first filter that drops " +
                "the most trades. Loosen that parameter first.");
        response.put("defaultParams", buildDefaultParamsDoc());
        response.put("dateFrom", from.toString());
        response.put("dateTo",   to.toString());

        List<Map<String, Object>> symbolDiags = new ArrayList<>();

        for (String symbol : SYMBOLS) {
            System.out.println("\n--- Diagnosing " + symbol + " ---");

            List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                    symbol, MAX_CANDLES_FOR_BACKTEST, from, to);

            Map<String, Object> diag = new LinkedHashMap<>();
            diag.put("symbol", symbol);
            diag.put("totalCandles", candles.size());

            if (candles.isEmpty()) {
                diag.put("error", "No data from Yahoo Finance");
                symbolDiags.add(diag);
                continue;
            }

            int[] counts = runFilterFunnel(candles,
                    MarketRegimeService.STRONG_UPTREND_SLOPE_THRESHOLD,
                    SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT,
                    SimplifiedBreakoutStrategy.BREAKOUT_BUFFER,
                    SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT,
                    SimplifiedBreakoutStrategy.RSI_MIN);

            // counts: [barsAnalyzed, regimePass, atrPass, volumePass, breakoutPass, rsiPass]
            int barsAnalyzed = counts[0];
            int regimePass   = counts[1];
            int atrPass      = counts[2];
            int volumePass   = counts[3];
            int breakoutPass = counts[4];
            int rsiPass      = counts[5];

            Map<String, String> funnel = new LinkedHashMap<>();
            funnel.put("c1_regime_STRONG_UPTREND",
                    regimePass + "/" + barsAnalyzed + " bars");
            funnel.put("c2_atr_above_" + pct(SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT),
                    (regimePass > 0 ? atrPass + "/" + regimePass : "n/a") + " bars");
            funnel.put("c3_volume_above_" + pct(SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT),
                    (atrPass > 0 ? volumePass + "/" + atrPass : "n/a") + " bars");
            funnel.put("c4_breakout_buf_" + pct(SimplifiedBreakoutStrategy.BREAKOUT_BUFFER - 1.0),
                    (volumePass > 0 ? breakoutPass + "/" + volumePass : "n/a") + " bars");
            funnel.put("c5_rsi_above_" + (int) SimplifiedBreakoutStrategy.RSI_MIN,
                    (breakoutPass > 0 ? rsiPass + "/" + breakoutPass : "n/a") + " bars");
            funnel.put("all_conditions_met", rsiPass + " bars");
            diag.put("filterFunnel", funnel);

            diag.put("bottleneck", identifyBottleneck(
                    barsAnalyzed, regimePass, atrPass, volumePass, breakoutPass, rsiPass));

            symbolDiags.add(diag);

            System.out.println("[Diagnostics] " + symbol
                    + " regime=" + regimePass + " atr=" + atrPass
                    + " vol=" + volumePass + " bo=" + breakoutPass
                    + " rsi=" + rsiPass + " total=" + rsiPass);
        }

        response.put("results", symbolDiags);

        System.out.println("=".repeat(80) + "\n");
        return response;
    }

    /*
    ======================================================
    ✅ PER-ASSET PARAMETER TUNING BACKTEST
    For each of the 13 symbols, systematically relaxes ONE
    filter at a time (in order: regime slope → ATR → breakout
    buffer → volume) until the asset achieves ≥8 trades with
    quality metrics:
      Win rate    ≥ 50%
      Profit fac  ≥ 1.0
      Max drawdown ≤ 30%

    Reports per-asset tuned settings, metrics, and a final
    audit table. Assets that never reach ≥8 trades are
    flagged as INSUFFICIENT_TRADES. Assets that reach
    ≥8 trades but fail quality checks are flagged POOR_QUALITY.

    STOP condition: ≥ 8 assets achieve PASS status.

    Endpoint: GET /backtest/real/multi/tuned
    ======================================================
     */
    @GetMapping("/backtest/real/multi/tuned")
    public Map<String, Object> multiAssetTunedBacktest() {

        final List<String> SYMBOLS = List.of(
                "SPY", "QQQ", "AAPL", "MSFT", "NVDA",
                "GOOG", "AMZN", "META", "TSLA",
                "IWM", "XLF", "XLE", "XLV"
        );

        final LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        final LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        // Ordered parameter sweep: relax one dimension at a time.
        // Each row: { slopeThreshold, atrThreshold, breakoutBuffer, volumeRatio }
        // Start from strictest (defaults) and progressively loosen.
        final double[][] PARAM_SWEEP = {
            // slope%, atr%,  buffer(mult), volume%
            { 0.030, 0.009, 1.0005, 0.50 },  // 0: defaults
            { 0.020, 0.009, 1.0005, 0.50 },  // 1: loosen regime slope 3%→2%
            { 0.020, 0.007, 1.0005, 0.50 },  // 2: loosen ATR 0.9%→0.7%
            { 0.020, 0.005, 1.0005, 0.50 },  // 3: loosen ATR 0.9%→0.5%
            { 0.020, 0.005, 1.0001, 0.50 },  // 4: loosen buffer →0.01%
            { 0.020, 0.005, 1.0000, 0.50 },  // 5: remove buffer entirely
            { 0.015, 0.005, 1.0000, 0.30 },  // 6: loosen slope+volume further
            { 0.015, 0.003, 1.0000, 0.20 },  // 7: maximum relaxation
        };

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PER-ASSET TUNED BACKTEST (" + SYMBOLS.size() + " symbols)");
        System.out.println("=".repeat(80));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description",
                "Per-asset parameter sweep: relaxes ONE filter at a time until ≥8 trades " +
                "AND quality metrics (winRate≥50%, PF≥1.0, DD≤30%) are achieved.");
        response.put("qualityGates",
                Map.of("minTrades", 8, "minWinRate", "50%",
                       "minProfitFactor", "1.0", "maxDrawdown", "30%"));
        response.put("dateFrom", from.toString());
        response.put("dateTo",   to.toString());

        List<Map<String, Object>> symbolResults = new ArrayList<>();
        int passCount  = 0;
        int totalCount = 0;

        for (String symbol : SYMBOLS) {
            System.out.println("\n--- Tuning " + symbol + " ---");

            List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                    symbol, MAX_CANDLES_FOR_BACKTEST, from, to);

            Map<String, Object> symbolResult = new LinkedHashMap<>();
            symbolResult.put("symbol",       symbol);
            symbolResult.put("totalCandles", candles.size());

            if (candles.isEmpty()) {
                symbolResult.put("status", "ERROR");
                symbolResult.put("error",  "No data from Yahoo Finance");
                symbolResults.add(symbolResult);
                totalCount++;
                continue;
            }

            // Try each parameter combo in order, stop at first PASS
            String   bestStatus    = "INSUFFICIENT_TRADES";
            int      bestParamIdx  = -1;
            BacktestResult bestResult = null;
            double[] bestParams    = null;

            for (int pi = 0; pi < PARAM_SWEEP.length; pi++) {
                double[] p = PARAM_SWEEP[pi];

                TunedBreakoutStrategy tunedStrategy = new TunedBreakoutStrategy(
                        p[0], p[1], p[2], p[3], SimplifiedBreakoutStrategy.RSI_MIN,
                        rsiCalculator);

                BacktestResult result = backtestEngine.runStrategy(
                        symbol, candles, tunedStrategy);

                boolean tradeCountOk  = result.getTotalTrades() >= 8;
                boolean winRateOk     = result.getWinRate()
                        .compareTo(BigDecimal.valueOf(0.50)) >= 0;
                boolean profitFactOk  = result.getProfitFactor()
                        .compareTo(BigDecimal.ONE) >= 0;
                boolean drawdownOk    = result.getMaxDrawdown()
                        .compareTo(BigDecimal.valueOf(0.30)) <= 0;

                System.out.println("[Tuning] " + symbol
                        + " combo=" + pi
                        + " trades=" + result.getTotalTrades()
                        + " winRate=" + result.getWinRate()
                        + " pf=" + result.getProfitFactor()
                        + " dd=" + result.getMaxDrawdown());

                if (tradeCountOk) {
                    bestResult   = result;
                    bestParamIdx = pi;
                    bestParams   = p;

                    if (winRateOk && profitFactOk && drawdownOk) {
                        bestStatus = "PASS";
                        break; // stop at first combo that fully passes
                    } else {
                        bestStatus = "POOR_QUALITY"; // trades ok but quality fails
                        // keep searching — a looser combo might yield better quality
                    }
                }
            }

            // If we only found POOR_QUALITY, keep it; if nothing found, report
            if (bestResult == null) {
                // No combo gave ≥8 trades — report the most-trades combo we found
                int maxTrades = 0;
                for (int pi = 0; pi < PARAM_SWEEP.length; pi++) {
                    double[] p = PARAM_SWEEP[pi];
                    TunedBreakoutStrategy tunedStrategy = new TunedBreakoutStrategy(
                            p[0], p[1], p[2], p[3], SimplifiedBreakoutStrategy.RSI_MIN,
                            rsiCalculator);
                    BacktestResult result = backtestEngine.runStrategy(
                            symbol, candles, tunedStrategy);
                    if (result.getTotalTrades() > maxTrades) {
                        maxTrades = result.getTotalTrades();
                        bestResult   = result;
                        bestParamIdx = pi;
                        bestParams   = p;
                    }
                }
                bestStatus = "INSUFFICIENT_TRADES";
            }

            if (bestResult != null) {
                boolean winRateOk    = bestResult.getWinRate()
                        .compareTo(BigDecimal.valueOf(0.50)) >= 0;
                boolean profitFactOk = bestResult.getProfitFactor()
                        .compareTo(BigDecimal.ONE) >= 0;
                boolean drawdownOk   = bestResult.getMaxDrawdown()
                        .compareTo(BigDecimal.valueOf(0.30)) <= 0;
                boolean tradeCountOk = bestResult.getTotalTrades() >= 8;

                symbolResult.put("totalTrades",   bestResult.getTotalTrades());
                symbolResult.put("winningTrades", bestResult.getWinningTrades());
                symbolResult.put("losingTrades",  bestResult.getLosingTrades());
                symbolResult.put("winRate",       bestResult.getWinRate());
                symbolResult.put("profitFactor",  bestResult.getProfitFactor());
                symbolResult.put("maxDrawdown",   bestResult.getMaxDrawdown());
                symbolResult.put("totalPnL",      bestResult.getTotalPnL());
                symbolResult.put("endCapital",    bestResult.getEndingCapital());

                Map<String, Object> tunedParams = new LinkedHashMap<>();
                tunedParams.put("paramComboIndex",  bestParamIdx);
                tunedParams.put("slopeThreshold",   pct(bestParams[0]) + " (default: 3.0%)");
                tunedParams.put("atrThreshold",     pct(bestParams[1]) + " (default: 0.9%)");
                tunedParams.put("breakoutBuffer",   pct(bestParams[2] - 1.0) + " (default: 0.05%)");
                tunedParams.put("volumeRatio",      pct(bestParams[3]) + " (default: 50.0%)");
                symbolResult.put("tunedParams", tunedParams);

                Map<String, Object> validation = new LinkedHashMap<>();
                validation.put("tradeCount_pass", tradeCountOk + " (" + bestResult.getTotalTrades() + " trades, need ≥8)");
                validation.put("winRate_pass",    winRateOk    + " (" + bestResult.getWinRate() + ", need ≥50%)");
                validation.put("profitFact_pass", profitFactOk + " (" + bestResult.getProfitFactor() + ", need ≥1.0)");
                validation.put("drawdown_pass",   drawdownOk   + " (" + bestResult.getMaxDrawdown() + ", need ≤30%)");
                validation.put("overall", bestStatus.equals("PASS") ? "✅ PASS" : "⚠️ " + bestStatus);
                symbolResult.put("validation", validation);
            }

            symbolResult.put("status", bestStatus);
            if (bestStatus.equals("PASS")) passCount++;
            totalCount++;
            symbolResults.add(symbolResult);

            System.out.println("[Tuning] " + symbol + " → " + bestStatus
                    + " (paramCombo=" + bestParamIdx + ")");
        }

        response.put("results", symbolResults);

        // ── Audit summary ─────────────────────────────────────────────────────
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("symbolsTested",  totalCount);
        audit.put("symbolsPassed",  passCount);
        audit.put("symbolsFailed",  totalCount - passCount);
        audit.put("passRate",       totalCount == 0 ? "N/A"
                : BigDecimal.valueOf(passCount)
                .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                .toPlainString());

        boolean readyForPaperTrading = passCount >= 8;
        audit.put("readyForPaperTrading", readyForPaperTrading);
        audit.put("paperTradingVerdict",
                readyForPaperTrading
                        ? "✅ SYSTEM READY — ≥8 assets pass. Proceed to paper trading with documented per-asset settings."
                        : "⚠️ NOT READY — Only " + passCount + "/13 assets pass. "
                        + "Continue loosening filters or review strategy robustness.");
        audit.put("overall", passCount == totalCount
                ? "✅ ALL SYMBOLS PASSED"
                : "⚠️ " + (totalCount - passCount) + " SYMBOL(S) NOT PASSING");

        response.put("auditSummary", audit);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PER-ASSET TUNED BACKTEST COMPLETE");
        System.out.println("Passed: " + passCount + " / " + totalCount);
        System.out.println("Paper trading ready: " + (passCount >= 8));
        System.out.println("=".repeat(80) + "\n");

        return response;
    }

    // ── Private helpers for multi-asset diagnostics ───────────────────────────

    /**
     * Runs a bar-by-bar filter funnel for the given parameter values.
     *
     * @return int[6]: [barsAnalyzed, regimePass, atrPass, volumePass, breakoutPass, rsiPass]
     */
    private int[] runFilterFunnel(List<Candle> candles,
                                   double slopeThresh,
                                   double atrThresh,
                                   double bufferMult,
                                   double volRatio,
                                   double rsiMin) {
        AtrCalculator atrCalc = new AtrCalculator();
        marketRegimeService.reset();

        int barsAnalyzed = 0;
        int regimePass   = 0;
        int atrPass      = 0;
        int volumePass   = 0;
        int breakoutPass = 0;
        int rsiPass      = 0;

        for (int i = SimplifiedBreakoutStrategy.MIN_CANDLES; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);
            barsAnalyzed++;

            // ── C1: Inline slope-based STRONG_UPTREND ─────────────────────────
            if (!inlineIsStrongUptrend(subset, slopeThresh)) continue;
            regimePass++;

            Candle current = subset.get(subset.size() - 1);
            BigDecimal price   = current.getClose();
            BigDecimal barHigh = current.getHigh();

            // ── C2: ATR threshold ─────────────────────────────────────────────
            BigDecimal atr    = atrCalc.calculate(subset, 14);
            BigDecimal atrPct = price.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : atr.divide(price, 6, RoundingMode.HALF_UP);

            if (atrPct.compareTo(BigDecimal.valueOf(atrThresh)) < 0) continue;
            atrPass++;

            // ── C3: Volume ratio ──────────────────────────────────────────────
            long currentVol = current.getVolume();
            long avgVol     = diagAvgVolume(subset, 20);

            BigDecimal volumeRatioBd = (avgVol == 0)
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(currentVol)
                            .divide(BigDecimal.valueOf(avgVol), 6, RoundingMode.HALF_UP);

            if (volumeRatioBd.compareTo(BigDecimal.valueOf(volRatio)) < 0) continue;
            volumePass++;

            // ── C4: Breakout above 5-bar high + buffer ────────────────────────
            BigDecimal highest5      = diagHighestHigh(subset, SimplifiedBreakoutStrategy.BREAKOUT_PERIOD);
            BigDecimal breakoutLevel = highest5.multiply(BigDecimal.valueOf(bufferMult));

            if (barHigh.compareTo(breakoutLevel) <= 0) continue;
            breakoutPass++;

            // ── C5: RSI ───────────────────────────────────────────────────────
            try {
                BigDecimal rsi = rsiCalculator.calculate(subset);
                if (rsi.compareTo(BigDecimal.valueOf(rsiMin)) < 0) continue;
                rsiPass++;
            } catch (Exception e) {
                // insufficient data
            }
        }

        return new int[]{ barsAnalyzed, regimePass, atrPass, volumePass, breakoutPass, rsiPass };
    }

    /**
     * Inline STRONG_UPTREND check: MA20/MA50 slope > slopeThresh AND 10-bar momentum positive.
     */
    private boolean inlineIsStrongUptrend(List<Candle> candles, double slopeThresh) {
        if (candles.size() < 50) return false;

        BigDecimal ma20 = diagMovingAverage(candles, 20);
        BigDecimal ma50 = diagMovingAverage(candles, 50);

        if (ma50.compareTo(BigDecimal.ZERO) == 0) return false;

        BigDecimal slope = ma20.subtract(ma50)
                .divide(ma50, 6, RoundingMode.HALF_UP);

        if (slope.compareTo(BigDecimal.valueOf(slopeThresh)) <= 0) return false;

        // 10-bar momentum
        if (candles.size() < 11) return false;
        BigDecimal recent = candles.get(candles.size() - 1).getClose();
        BigDecimal past   = candles.get(candles.size() - 10).getClose();
        return recent.compareTo(past) > 0;
    }

    /** Identifies which filter gate is the biggest bottleneck. */
    private String identifyBottleneck(int analyzed, int regime, int atr,
                                       int volume, int breakout, int rsi) {
        if (analyzed == 0) return "NO_DATA";
        if (regime  == 0) return "REGIME_SLOPE — no STRONG_UPTREND bars. "
                + "Try lowering slopeThreshold from 3% to 2%.";
        if (atr     == 0) return "ATR_THRESHOLD — ATR% too low on all uptrend bars. "
                + "Try lowering atrThreshold from 0.9% to 0.7%.";
        if (volume  == 0) return "VOLUME_RATIO — volume below average on all ATR-passing bars. "
                + "Try lowering volumeRatio from 50% to 30%.";
        if (breakout == 0) return "BREAKOUT_BUFFER — bar high never exceeds 5-bar high + buffer. "
                + "Try lowering breakoutBuffer from 0.05% to 0.01%.";
        if (rsi     == 0) return "RSI — RSI below " + (int) SimplifiedBreakoutStrategy.RSI_MIN
                + " on all breakout bars. Try lowering rsiMin to 45.";
        if (rsi     < 8)  return "LOW_SIGNAL_COUNT (" + rsi + " bars pass all filters). "
                + "Need ≥8 signals; combine regime + ATR loosening.";
        return "NO_BOTTLENECK — " + rsi + " bars pass all filters with default settings.";
    }

    /** Formats a decimal fraction as a percentage string (e.g. 0.009 → "0.9%"). */
    private String pct(double fraction) {
        return BigDecimal.valueOf(fraction * 100)
                .setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** Builds a documentation map for the default strategy parameters. */
    private Map<String, String> buildDefaultParamsDoc() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("regimeSlopeThreshold",
                pct(MarketRegimeService.STRONG_UPTREND_SLOPE_THRESHOLD) + " (MA20/MA50 slope)");
        params.put("atrThreshold",
                pct(SimplifiedBreakoutStrategy.VOLATILITY_THRESHOLD_PCT) + " of price");
        params.put("breakoutBuffer",
                pct(SimplifiedBreakoutStrategy.BREAKOUT_BUFFER - 1.0) + " above 5-bar high");
        params.put("volumeRatioMin",
                pct(SimplifiedBreakoutStrategy.MIN_VOLUME_RATIO_PCT) + " of 20-bar avg volume");
        params.put("rsiMin", (int) SimplifiedBreakoutStrategy.RSI_MIN + "");
        return params;
    }

    /*
    ======================================================
    ✅ TRADE AUDIT — ROOT CAUSE ANALYSIS
    Runs the RobustTrendBreakoutStrategy backtest on key
    failing assets (SPY, QQQ, AAPL, MSFT) and returns a
    detailed per-trade breakdown showing:
      - Entry price, SL, TP, regime, ATR% at entry
      - Exit price, exit reason (SL/TP/FORCE_CLOSE), bars held
      - PnL and win/loss classification
      - Root-cause tag for each losing trade

    Use this endpoint to investigate why individual trades
    lose and find patterns (e.g., most losses = SL hit on
    day 2 = noise-induced false breakout).

    Endpoint: GET /backtest/real/audit
    ======================================================
     */
    @Operation(summary = "Trade audit — root cause analysis for key failing assets",
               description = "Runs RobustTrendBreakoutStrategy on SPY, QQQ, AAPL, MSFT and returns " +
                             "per-trade detail with exit reasons and loss root-cause categorisation.")
    @GetMapping("/backtest/real/audit")
    public Map<String, Object> tradeAudit() {

        final List<String> AUDIT_SYMBOLS = List.of("SPY", "QQQ", "AAPL", "MSFT");
        final LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        final LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("strategy",  robustBreakoutStrategy.getName());
        response.put("dateFrom",  from.toString());
        response.put("dateTo",    to.toString());
        response.put("purpose",
                "Root-cause audit: per-trade breakdown to identify why signals fail quality gates. " +
                "Loss root causes: QUICK_REVERSAL (SL hit ≤2 bars), SLOW_GRIND_DOWN (SL hit 3-10 bars), " +
                "TIMEOUT (force-close after long hold), MARGINAL_EXIT (close to SL/TP).");

        List<Map<String, Object>> auditResults = new ArrayList<>();
        AtrCalculator atrCalc = new AtrCalculator();

        for (String symbol : AUDIT_SYMBOLS) {
            System.out.println("\n--- Auditing " + symbol + " ---");

            List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                    symbol, MAX_CANDLES_FOR_BACKTEST, from, to);

            Map<String, Object> symbolAudit = new LinkedHashMap<>();
            symbolAudit.put("symbol",       symbol);
            symbolAudit.put("totalCandles", candles.size());

            if (candles.isEmpty()) {
                symbolAudit.put("error", "No data from Yahoo Finance");
                auditResults.add(symbolAudit);
                continue;
            }

            BacktestResult result = backtestEngine.runStrategy(
                    symbol, candles, robustBreakoutStrategy);

            // ── Per-trade detail ─────────────────────────────────────────────
            List<Map<String, Object>> tradeDetails = new ArrayList<>();
            int quickReversals  = 0;
            int slowGrindDowns  = 0;
            int timeouts        = 0;
            int marginalExits   = 0;
            int tpHits          = 0;

            for (TradeRecord rec : result.getTradeLog()) {
                Map<String, Object> td = new LinkedHashMap<>();
                td.put("tradeId",     rec.getTradeId());
                td.put("entryPrice",  rec.getEntryPrice());
                td.put("stopLoss",    rec.getStopLoss());
                td.put("takeProfit",  rec.getTakeProfit());
                td.put("entryRegime", rec.getEntryRegime());
                td.put("exitPrice",   rec.getExitPrice());
                td.put("exitReason",  rec.getExitReason());
                td.put("exitRegime",  rec.getExitRegime());
                td.put("barsHeld",    rec.getBarsHeld());

                if (rec.getPnl() != null) {
                    td.put("pnl",    rec.getPnl().setScale(2, RoundingMode.HALF_UP));
                    td.put("result", rec.getPnl().compareTo(BigDecimal.ZERO) > 0 ? "WIN" : "LOSS");
                } else {
                    td.put("pnl",    null);
                    td.put("result", "OPEN");
                }

                // ATR% at entry (approximated from SL distance / entry price × 0.5,
                // since SL = entry × (1 - 2×atrPct), so atrPct = (entry-SL)/(entry×2))
                if (rec.getEntryPrice() != null && rec.getStopLoss() != null
                        && rec.getEntryPrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal slDistance = rec.getEntryPrice().subtract(rec.getStopLoss());
                    BigDecimal atrPct = slDistance
                            .divide(rec.getEntryPrice().multiply(BigDecimal.valueOf(2.0)),
                                    4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    td.put("impliedAtrPct", atrPct.toPlainString() + "%");
                }

                // Root-cause categorisation for losses
                if (rec.getExitPrice() != null && rec.getPnl() != null
                        && rec.getPnl().compareTo(BigDecimal.ZERO) <= 0) {
                    String rootCause;
                    if ("SL".equals(rec.getExitReason()) && rec.getBarsHeld() <= 2) {
                        rootCause = "QUICK_REVERSAL";
                        quickReversals++;
                    } else if ("SL".equals(rec.getExitReason()) && rec.getBarsHeld() <= 10) {
                        rootCause = "SLOW_GRIND_DOWN";
                        slowGrindDowns++;
                    } else if ("FORCE_CLOSE".equals(rec.getExitReason())) {
                        rootCause = "TIMEOUT";
                        timeouts++;
                    } else {
                        rootCause = "MARGINAL_EXIT";
                        marginalExits++;
                    }
                    td.put("rootCause", rootCause);
                } else if ("TP".equals(rec.getExitReason())) {
                    td.put("rootCause", "TP_HIT");
                    tpHits++;
                }

                tradeDetails.add(td);
            }

            // ── Symbol-level audit summary ────────────────────────────────────
            Map<String, Object> lossCauses = new LinkedHashMap<>();
            lossCauses.put("QUICK_REVERSAL (SL ≤2 bars)",   quickReversals);
            lossCauses.put("SLOW_GRIND_DOWN (SL 3-10 bars)", slowGrindDowns);
            lossCauses.put("TIMEOUT (force-close)",          timeouts);
            lossCauses.put("MARGINAL_EXIT (other SL)",       marginalExits);
            lossCauses.put("TP_HIT (winners)",               tpHits);

            symbolAudit.put("totalTrades",   result.getTotalTrades());
            symbolAudit.put("winRate",       result.getWinRate());
            symbolAudit.put("profitFactor",  result.getProfitFactor());
            symbolAudit.put("maxDrawdown",   result.getMaxDrawdown());
            symbolAudit.put("totalPnL",      result.getTotalPnL());
            symbolAudit.put("lossCauses",    lossCauses);
            symbolAudit.put("trades",        tradeDetails);

            String primaryCause = "UNKNOWN";
            int maxCount = 0;
            if (quickReversals > maxCount)  { maxCount = quickReversals;  primaryCause = "QUICK_REVERSAL — entry right before reversal; tighten entry confirmation"; }
            if (slowGrindDowns > maxCount)  { maxCount = slowGrindDowns;  primaryCause = "SLOW_GRIND_DOWN — trend resumes against position; consider wider SL"; }
            if (timeouts > maxCount)        { maxCount = timeouts;         primaryCause = "TIMEOUT — position held too long without direction; check TP distance"; }
            symbolAudit.put("primaryLossCause", primaryCause);

            auditResults.add(symbolAudit);

            System.out.println("[Audit] " + symbol
                    + " trades=" + result.getTotalTrades()
                    + " winRate=" + result.getWinRate()
                    + " quickRev=" + quickReversals
                    + " slowGrind=" + slowGrindDowns
                    + " timeouts=" + timeouts);
        }

        response.put("results", auditResults);
        return response;
    }

}
