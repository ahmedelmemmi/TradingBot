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
import com.tradingbot.trading.Bot.market.MockMarketDataProvider;
import com.tradingbot.trading.Bot.market.MockMarketDataService;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.MeanReversionStrategy;
import com.tradingbot.trading.Bot.strategy.NoTradeStrategy;
import com.tradingbot.trading.Bot.strategy.PerfectBreakoutStrategy;
import com.tradingbot.trading.Bot.strategy.RsiStrategyService;
import com.tradingbot.trading.Bot.strategy.SimplifiedBreakoutStrategy;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import com.tradingbot.trading.Bot.strategy.VolatilityBreakoutStrategy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@RestController
public class TestController {
    private final MockMarketDataService marketDataService;
    private final RsiStrategyService rsiStrategyService;
    private final PerfectBreakoutStrategy perfectBreakoutStrategy;
    private final SimplifiedBreakoutStrategy simplifiedBreakoutStrategy;
    private final MeanReversionStrategy meanReversionStrategy;
    private final VolatilityBreakoutStrategy volatilityBreakoutStrategy;
    private final TradeDecisionService tradeDecisionService;
    private final PositionManager positionManager;
    private final BacktestEngine backtestEngine;
    private final IBKRPaperBrokerAdapter brokerAdapter;
    private final BrokerStateService brokerStateService;
    private final MarketRegimeService marketRegimeService;
    private final PortfolioBacktestEngine portfolioBacktestEngine;
    private final BacktestValidationService backtestValidationService;

    public TestController(MockMarketDataService marketDataService,
                          RsiStrategyService rsiStrategyService,
                          PerfectBreakoutStrategy perfectBreakoutStrategy,
                          SimplifiedBreakoutStrategy simplifiedBreakoutStrategy,
                          MeanReversionStrategy meanReversionStrategy,
                          VolatilityBreakoutStrategy volatilityBreakoutStrategy,
                          TradeDecisionService tradeDecisionService,
                          PositionManager positionManager,
                          BacktestEngine backtestEngine,
                          IBKRPaperBrokerAdapter brokerAdapter,
                          BrokerStateService brokerStateService,
                          MarketRegimeService marketRegimeService,
                          PortfolioBacktestEngine portfolioBacktestEngine,
                          BacktestValidationService backtestValidationService) {

        this.marketDataService           = marketDataService;
        this.rsiStrategyService          = rsiStrategyService;
        this.perfectBreakoutStrategy     = perfectBreakoutStrategy;
        this.simplifiedBreakoutStrategy  = simplifiedBreakoutStrategy;
        this.meanReversionStrategy       = meanReversionStrategy;
        this.volatilityBreakoutStrategy  = volatilityBreakoutStrategy;
        this.tradeDecisionService        = tradeDecisionService;
        this.positionManager             = positionManager;
        this.backtestEngine              = backtestEngine;
        this.brokerAdapter               = brokerAdapter;
        this.brokerStateService          = brokerStateService;
        this.marketRegimeService         = marketRegimeService;
        this.portfolioBacktestEngine     = portfolioBacktestEngine;
        this.backtestValidationService   = backtestValidationService;
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
    ✅ MOCK DATA: Backtest using SIDEWAYS_VOLATILE scenario
    Uses MeanReversionStrategy — the correct strategy for
    ranging/sideways markets. Expected: 55-65% WR.
    ======================================================
     */
    @GetMapping("/backtest/mock/sideways")
    public Map<String, Object> backtestMockSideways() {
        MarketDataProvider provider = MockMarketDataProvider.forScenario(
                marketDataService,
                MockMarketDataService.MarketScenario.SIDEWAYS_VOLATILE
        );

        List<Candle> candles = provider.getCandles("AAPL", 1000);
        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, meanReversionStrategy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",      provider.getProviderName());
        response.put("strategy",      meanReversionStrategy.getName());
        response.put("regime",        "SIDEWAYS");
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
    ✅ MOCK DATA: Backtest using SIDEWAYS_VOLATILE scenario
    Uses VolatilityBreakoutStrategy — the correct strategy
    for high-volatility directional spikes. Expected: 60-70% WR.
    ======================================================
     */
    @GetMapping("/backtest/mock/volatile")
    public Map<String, Object> backtestMockVolatile() {
        MarketDataProvider provider = MockMarketDataProvider.forScenario(
                marketDataService,
                MockMarketDataService.MarketScenario.SIDEWAYS_VOLATILE
        );

        List<Candle> candles = provider.getCandles("AAPL", 1000);
        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, volatilityBreakoutStrategy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",      provider.getProviderName());
        response.put("strategy",      volatilityBreakoutStrategy.getName());
        response.put("regime",        "HIGH_VOLATILITY");
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
    ✅ MOCK DATA: Backtest on STRONG_DOWNTREND data.
    Uses NoTradeStrategy — no trades expected (preserve capital).
    Expected: 0 trades, 0 loss.
    ======================================================
     */
    @GetMapping("/backtest/mock/downtrend")
    public Map<String, Object> backtestMockDowntrend() {
        MarketDataProvider provider = MockMarketDataProvider.forScenario(
                marketDataService,
                MockMarketDataService.MarketScenario.STRONG_DOWNTREND
        );

        List<Candle> candles = provider.getCandles("AAPL", 1000);
        NoTradeStrategy noTradeStrategy = new NoTradeStrategy();
        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, noTradeStrategy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",      provider.getProviderName());
        response.put("strategy",      noTradeStrategy.getName());
        response.put("regime",        "STRONG_DOWNTREND");
        response.put("totalCandles",  candles.size());
        response.put("totalTrades",   result.getTotalTrades());
        response.put("winRate",       result.getWinRate());
        response.put("totalPnL",      result.getTotalPnL());
        response.put("startCapital",  result.getStartingCapital());
        response.put("endCapital",    result.getEndingCapital());
        response.put("maxDrawdown",   result.getMaxDrawdown());
        response.put("note",          "Capital preserved — no trades in downtrend");
        return response;
    }

    /*
    ======================================================
    ✅ MOCK DATA: Backtest on CRASH data.
    Uses NoTradeStrategy — no trades expected (emergency halt).
    Expected: 0 trades, 0 loss.
    ======================================================
     */
    @GetMapping("/backtest/mock/crash")
    public Map<String, Object> backtestMockCrash() {
        MarketDataProvider provider = MockMarketDataProvider.forScenario(
                marketDataService,
                MockMarketDataService.MarketScenario.CRASH
        );

        List<Candle> candles = provider.getCandles("AAPL", 1000);
        NoTradeStrategy noTradeStrategy = new NoTradeStrategy();
        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, noTradeStrategy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",      provider.getProviderName());
        response.put("strategy",      noTradeStrategy.getName());
        response.put("regime",        "CRASH");
        response.put("totalCandles",  candles.size());
        response.put("totalTrades",   result.getTotalTrades());
        response.put("winRate",       result.getWinRate());
        response.put("totalPnL",      result.getTotalPnL());
        response.put("startCapital",  result.getStartingCapital());
        response.put("endCapital",    result.getEndingCapital());
        response.put("maxDrawdown",   result.getMaxDrawdown());
        response.put("note",          "Capital preserved — no trades in crash");
        return response;
    }

}
