package com.tradingbot.trading.Bot.controller;

import com.tradingbot.trading.Bot.backtest.BacktestEngine;
import com.tradingbot.trading.Bot.backtest.BacktestResult;
import com.tradingbot.trading.Bot.broker.BrokerStateService;
import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MockMarketDataService;
import com.tradingbot.trading.Bot.market.YahooFinanceMarketDataProvider;
import com.tradingbot.trading.Bot.strategy.AtrCalculator;
import com.tradingbot.trading.Bot.strategy.RobustTrendBreakoutStrategy;
import com.tradingbot.trading.Bot.strategy.RsiCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@Tag(name = "TradingBot", description = "Backtesting and diagnostics for the RobustTrendBreakoutStrategy")
public class TestController {

    private static final int MAX_CANDLES_FOR_BACKTEST     = 1000;
    private static final int MAX_CANDLES_FOR_DIAGNOSTICS  = 1000;

    private final MockMarketDataService         marketDataService;
    private final BacktestEngine                backtestEngine;
    private final BrokerStateService            brokerStateService;
    private final RobustTrendBreakoutStrategy   robustBreakoutStrategy;
    private final YahooFinanceMarketDataProvider yahooFinanceMarketDataProvider;
    private final RsiCalculator                 rsiCalculator;

    public TestController(MockMarketDataService marketDataService,
                          BacktestEngine backtestEngine,
                          BrokerStateService brokerStateService,
                          RobustTrendBreakoutStrategy robustBreakoutStrategy,
                          YahooFinanceMarketDataProvider yahooFinanceMarketDataProvider,
                          RsiCalculator rsiCalculator) {
        this.marketDataService              = marketDataService;
        this.backtestEngine                 = backtestEngine;
        this.brokerStateService             = brokerStateService;
        this.robustBreakoutStrategy         = robustBreakoutStrategy;
        this.yahooFinanceMarketDataProvider = yahooFinanceMarketDataProvider;
        this.rsiCalculator                  = rsiCalculator;
    }

    /*
     * ── Broker state ──────────────────────────────────────────────────────────
     */
    @Operation(summary = "Broker state", description = "Returns current IBKR paper broker positions.")
    @GetMapping("/broker-state")
    public Object brokerState() {
        return brokerStateService.getPositions();
    }

    /*
     * ── Mock sanity-check backtest ────────────────────────────────────────────
     * Runs RobustTrendBreakoutStrategy on 1000 bars of mock STRONG_UPTREND data
     * and reports pass/fail for the quality gates. Use this to verify strategy
     * logic hasn't regressed.  NOT for strategy validation — use real-data endpoints.
     */
    @Operation(summary = "Sanity-check backtest (mock data)",
               description = "Runs RobustTrendBreakoutStrategy on 1000 bars of mock STRONG_UPTREND data. " +
                             "Quality gates: win rate ≥60%, profit factor ≥1.2, drawdown ≤25%. " +
                             "For strategy validation use /backtest/real/multi.")
    @GetMapping("/backtest/robust")
    public Map<String, Object> backtestRobust() {

        System.out.println("\n[Robust] ========== SANITY-CHECK BACKTEST (mock data) ==========");

        List<Candle> candles = marketDataService.generateCandles(
                "AAPL", 1000, MockMarketDataService.MarketScenario.STRONG_UPTREND);

        BacktestResult result = backtestEngine.runStrategy("AAPL", candles, robustBreakoutStrategy);

        System.out.println("[Robust] ========== DONE ==========\n");

        boolean tradeCountPass   = result.getTotalTrades() >= 5;
        boolean winRatePass      = result.getWinRate().compareTo(BigDecimal.valueOf(0.60)) >= 0;
        boolean profitFactorPass = result.getProfitFactor().compareTo(BigDecimal.valueOf(1.2)) >= 0;
        boolean drawdownPass     = result.getMaxDrawdown().compareTo(BigDecimal.valueOf(0.25)) <= 0;
        boolean allPass = tradeCountPass && winRatePass && profitFactorPass && drawdownPass;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("strategy",     robustBreakoutStrategy.getName());
        response.put("dataSource",   "mock-STRONG_UPTREND");
        response.put("totalCandles", candles.size());
        response.put("totalTrades",  result.getTotalTrades());
        response.put("winRate",      result.getWinRate());
        response.put("profitFactor", result.getProfitFactor());
        response.put("totalPnL",     result.getTotalPnL());
        response.put("startCapital", result.getStartingCapital());
        response.put("endCapital",   result.getEndingCapital());
        response.put("maxDrawdown",  result.getMaxDrawdown());

        Map<String, Object> gates = new LinkedHashMap<>();
        gates.put("tradeCount_pass",   tradeCountPass   + " (≥5, got " + result.getTotalTrades() + ")");
        gates.put("winRate_pass",      winRatePass     + " (≥60%)");
        gates.put("profitFactor_pass", profitFactorPass + " (≥1.2)");
        gates.put("drawdown_pass",     drawdownPass    + " (≤25%)");
        gates.put("overall", allPass ? "✅ ALL QUALITY GATES PASS" : "⚠️ SOME QUALITY GATES FAIL");
        response.put("qualityGates",   gates);
        return response;
    }

    /*
     * ── Multi-symbol real data backtest ───────────────────────────────────────
     * Primary validation endpoint. Runs RobustTrendBreakoutStrategy on 2+ years
     * of real daily data for 13 major symbols.  Results here determine readiness
     * for paper trading.
     */
    @Operation(summary = "Multi-symbol real data backtest",
               description = "Runs RobustTrendBreakoutStrategy on real Yahoo Finance daily data " +
                             "(2022-01-01 to 2024-03-14) for 13 symbols. " +
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
        System.out.println("Strategy: " + robustBreakoutStrategy.getName());
        System.out.println("=".repeat(80));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider",   yahooFinanceMarketDataProvider.getProviderName());
        response.put("strategy",   robustBreakoutStrategy.getName());
        response.put("dateFrom",   from.toString());
        response.put("dateTo",     to.toString());
        response.put("qualityGates", Map.of(
                "minTrades", 5, "minWinRate", "60%",
                "minProfitFactor", "1.2", "maxDrawdown", "25%"));

        List<Map<String, Object>> symbolResults = new ArrayList<>();
        int passCount  = 0;

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
                continue;
            }

            BacktestResult result = backtestEngine.runStrategy(symbol, candles, robustBreakoutStrategy);

            boolean winRatePass      = result.getWinRate().compareTo(BigDecimal.valueOf(0.60)) >= 0;
            boolean profitFactorPass = result.getProfitFactor().compareTo(BigDecimal.valueOf(1.2)) >= 0;
            boolean drawdownPass     = result.getMaxDrawdown().compareTo(BigDecimal.valueOf(0.25)) <= 0;
            boolean tradeCountPass   = result.getTotalTrades() >= 5;
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

            Map<String, Object> validation = new LinkedHashMap<>();
            validation.put("tradeCount_pass",   tradeCountPass   + " (≥5, got " + result.getTotalTrades() + ")");
            validation.put("winRate_pass",       winRatePass      + " (" + result.getWinRate() + ", need ≥60%)");
            validation.put("profitFactor_pass",  profitFactorPass + " (" + result.getProfitFactor() + ", need ≥1.2)");
            validation.put("drawdown_pass",      drawdownPass     + " (" + result.getMaxDrawdown() + ", need ≤25%)");
            validation.put("overall",            allPass ? "✅ PASS" : "⚠️ FAIL");
            symbolResult.put("validation", validation);
            symbolResult.put("status", allPass ? "PASS" : "FAIL");

            if (allPass) passCount++;
            symbolResults.add(symbolResult);
        }

        response.put("results", symbolResults);

        boolean readyForPaperTrading = passCount >= 8;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("symbolsTested",         SYMBOLS.size());
        summary.put("symbolsPassed",         passCount);
        summary.put("symbolsFailed",         SYMBOLS.size() - passCount);
        summary.put("passRate",              BigDecimal.valueOf(passCount)
                .divide(BigDecimal.valueOf(SYMBOLS.size()), 4, RoundingMode.HALF_UP).toPlainString());
        summary.put("readyForPaperTrading",  readyForPaperTrading);
        summary.put("paperTradingVerdict",
                readyForPaperTrading
                        ? "✅ READY — ≥8/13 symbols pass all quality gates."
                        : "⚠️ NOT READY — Only " + passCount + "/13 symbols pass. Review strategy conditions.");
        summary.put("overall", passCount == SYMBOLS.size()
                ? "✅ ALL SYMBOLS PASSED"
                : "⚠️ " + (SYMBOLS.size() - passCount) + " SYMBOL(S) FAILED");
        response.put("summary", summary);

        System.out.println("\nMULTI-SYMBOL BACKTEST COMPLETE — Passed: " + passCount + " / " + SYMBOLS.size());
        System.out.println("=".repeat(80) + "\n");

        return response;
    }

    /*
     * ── Per-symbol filter diagnostics ─────────────────────────────────────────
     * Bar-by-bar filter funnel report showing how many bars pass each condition
     * of RobustTrendBreakoutStrategy.  Use this to understand why some symbols
     * generate fewer trade signals.
     */
    @Operation(summary = "Per-symbol filter diagnostics",
               description = "Bar-by-bar filter funnel for 13 symbols using RobustTrendBreakoutStrategy conditions " +
                             "(uptrend, trend-strength, breakout, volume, RSI). Shows bottleneck for each symbol.")
    @GetMapping("/backtest/real/multi/diagnostics")
    public Map<String, Object> multiAssetFilterDiagnostics() {

        final List<String> SYMBOLS = List.of(
                "SPY", "QQQ", "AAPL", "MSFT", "NVDA",
                "GOOG", "AMZN", "META", "TSLA",
                "IWM", "XLF", "XLE", "XLV"
        );

        final LocalDateTime from = LocalDateTime.of(2022, 1, 1, 0, 0);
        final LocalDateTime to   = LocalDateTime.of(2024, 3, 14, 23, 59);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description",
                "Filter funnel counts per symbol using RobustTrendBreakoutStrategy conditions. " +
                "'bottleneck' is the first filter that drops the most bars.");
        response.put("defaultParams", Map.of(
                "uptrend",         "MA20 > MA50",
                "trendStrength",   "(MA20-MA50)/MA50 ≥ " + (RobustTrendBreakoutStrategy.MIN_MA_RATIO_PCT * 100) + "%",
                "breakout",        "close > " + RobustTrendBreakoutStrategy.BREAKOUT_PERIOD + "-bar high + " +
                                   (RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT * 100) + "% buffer",
                "volumeConfirm",   "volume ≥ " + RobustTrendBreakoutStrategy.VOLUME_RATIO_MIN + "× " +
                                   RobustTrendBreakoutStrategy.VOLUME_AVG_PERIOD + "-bar average",
                "rsiMin",          RobustTrendBreakoutStrategy.RSI_MIN + ""
        ));
        response.put("dateFrom", from.toString());
        response.put("dateTo",   to.toString());

        AtrCalculator atrCalc = new AtrCalculator();
        List<Map<String, Object>> results = new ArrayList<>();

        for (String symbol : SYMBOLS) {
            List<Candle> candles = yahooFinanceMarketDataProvider.getCandles(
                    symbol, MAX_CANDLES_FOR_DIAGNOSTICS, from, to);

            Map<String, Object> symbolResult = new LinkedHashMap<>();
            symbolResult.put("symbol",       symbol);
            symbolResult.put("totalCandles", candles.size());

            if (candles.isEmpty()) {
                symbolResult.put("bottleneck", "ERROR: No data from Yahoo Finance");
                results.add(symbolResult);
                continue;
            }

            int barsAnalyzed  = 0;
            int uptrendPass   = 0;
            int strengthPass  = 0;
            int breakoutPass  = 0;
            int volumePass    = 0;
            int rsiPass       = 0;

            for (int i = RobustTrendBreakoutStrategy.MIN_CANDLES; i < candles.size(); i++) {
                List<Candle> subset = candles.subList(0, i + 1);
                barsAnalyzed++;

                Candle current = subset.get(subset.size() - 1);
                BigDecimal price = current.getClose();

                // C1: MA20 > MA50
                BigDecimal ma20 = movingAverage(subset, 20);
                BigDecimal ma50 = movingAverage(subset, 50);
                if (ma20.compareTo(ma50) <= 0) continue;
                uptrendPass++;

                // C2: trend strength
                BigDecimal maRatio = ma20.subtract(ma50).divide(ma50, 6, RoundingMode.HALF_UP);
                if (maRatio.compareTo(BigDecimal.valueOf(RobustTrendBreakoutStrategy.MIN_MA_RATIO_PCT)) < 0) continue;
                strengthPass++;

                // C3: breakout
                BigDecimal nBarHigh = highestHigh(subset, RobustTrendBreakoutStrategy.BREAKOUT_PERIOD);
                BigDecimal breakoutLevel = nBarHigh.multiply(
                        BigDecimal.ONE.add(BigDecimal.valueOf(RobustTrendBreakoutStrategy.BREAKOUT_BUFFER_PCT)));
                if (price.compareTo(breakoutLevel) <= 0) continue;
                breakoutPass++;

                // C4: volume
                long avgVol     = averageVolume(subset, RobustTrendBreakoutStrategy.VOLUME_AVG_PERIOD);
                long currentVol = current.getVolume();
                if (avgVol > 0) {
                    double volRatio = (double) currentVol / avgVol;
                    if (volRatio < RobustTrendBreakoutStrategy.VOLUME_RATIO_MIN) continue;
                }
                volumePass++;

                // C5: RSI
                try {
                    BigDecimal rsi = rsiCalculator.calculate(subset);
                    if (rsi.compareTo(BigDecimal.valueOf(RobustTrendBreakoutStrategy.RSI_MIN)) <= 0) continue;
                    rsiPass++;
                } catch (Exception e) {
                    // insufficient data for RSI
                }
            }

            Map<String, String> funnel = new LinkedHashMap<>();
            funnel.put("c1_ma20_above_ma50",         uptrendPass  + "/" + barsAnalyzed + " bars");
            funnel.put("c2_trend_strength_" + (int)(RobustTrendBreakoutStrategy.MIN_MA_RATIO_PCT*100) + "pct",
                       strengthPass + "/" + uptrendPass + " bars");
            funnel.put("c3_breakout_" + RobustTrendBreakoutStrategy.BREAKOUT_PERIOD + "bar",
                       breakoutPass + "/" + strengthPass + " bars");
            funnel.put("c4_volume_" + RobustTrendBreakoutStrategy.VOLUME_RATIO_MIN + "x",
                       volumePass   + "/" + breakoutPass + " bars");
            funnel.put("c5_rsi_above_" + (int) RobustTrendBreakoutStrategy.RSI_MIN,
                       rsiPass      + "/" + volumePass + " bars");
            funnel.put("all_conditions_met", rsiPass + " bars");
            symbolResult.put("filterFunnel", funnel);

            // Identify bottleneck
            String bottleneck;
            if (uptrendPass  == 0) bottleneck = "UPTREND — MA20 ≤ MA50 on most bars";
            else if (strengthPass == 0) bottleneck = "TREND_STRENGTH — (MA20-MA50)/MA50 < " +
                    (RobustTrendBreakoutStrategy.MIN_MA_RATIO_PCT*100) + "%";
            else if (breakoutPass == 0) bottleneck = "BREAKOUT — close never exceeds " +
                    RobustTrendBreakoutStrategy.BREAKOUT_PERIOD + "-bar high + buffer";
            else if (volumePass  == 0) bottleneck = "VOLUME — volume < " +
                    RobustTrendBreakoutStrategy.VOLUME_RATIO_MIN + "× average on breakout bars";
            else if (rsiPass     == 0) bottleneck = "RSI — RSI ≤ " + (int)RobustTrendBreakoutStrategy.RSI_MIN;
            else if (rsiPass     < 5)  bottleneck = "LOW_SIGNAL_COUNT (" + rsiPass +
                    " bars pass all filters — need ≥5 for quality assessment)";
            else bottleneck = "NO_BOTTLENECK — " + rsiPass + " bars pass all filters.";

            symbolResult.put("bottleneck", bottleneck);
            results.add(symbolResult);
        }

        response.put("results", results);
        return response;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BigDecimal movingAverage(List<Candle> candles, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        int end   = candles.size();
        int start = Math.max(0, end - period);
        int count = 0;
        for (int i = start; i < end; i++) {
            sum = sum.add(candles.get(i).getClose());
            count++;
        }
        return count == 0 ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal highestHigh(List<Candle> candles, int period) {
        BigDecimal max = BigDecimal.ZERO;
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        for (int i = start; i < end; i++) {
            if (candles.get(i).getHigh().compareTo(max) > 0) max = candles.get(i).getHigh();
        }
        return max;
    }

    private long averageVolume(List<Candle> candles, int period) {
        int end   = candles.size() - 1;
        int start = Math.max(0, end - period);
        long sum  = 0; int count = 0;
        for (int i = start; i < end; i++) { sum += candles.get(i).getVolume(); count++; }
        return count == 0 ? 0 : sum / count;
    }
}
