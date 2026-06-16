package com.tradingbot.trading.Bot.signal;

import com.tradingbot.trading.Bot.analysis.IndicatorSnapshot;
import com.tradingbot.trading.Bot.analysis.TechnicalAnalysisEngine;
import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.ml.MlFeatureVector;
import com.tradingbot.trading.Bot.ml.MlPredictionResponse;
import com.tradingbot.trading.Bot.ml.MlServiceClient;
import com.tradingbot.trading.Bot.persistence.entity.PredictionEntity;
import com.tradingbot.trading.Bot.persistence.entity.SignalEntity;
import com.tradingbot.trading.Bot.persistence.repository.PredictionRepository;
import com.tradingbot.trading.Bot.persistence.repository.SignalRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Signal Fusion Engine.
 *
 * <p>A trade is only approved when:</p>
 * <ol>
 *   <li>Rule engine returns BUY or SELL (all 5 rules passed).</li>
 *   <li>ML confidence &gt; {@code trading.rules.ml-confidence-threshold}.</li>
 *   <li>ML direction agrees with the rule engine direction.</li>
 * </ol>
 *
 * <p>When the ML service is unavailable the engine blocks the trade
 * (returns {@link FusionResult#blocked}).</p>
 */
@Service
public class SignalFusionEngine {

    private static final Logger log = LoggerFactory.getLogger(SignalFusionEngine.class);

    private final TechnicalAnalysisEngine taEngine;
    private final BinaryOptionsRuleEngine ruleEngine;
    private final MlServiceClient mlClient;
    private final RuleEngineConfig config;
    private final SignalRepository signalRepository;
    private final PredictionRepository predictionRepository;

    private final Counter signalsApprovedCounter;
    private final Counter signalsBlockedCounter;

    public SignalFusionEngine(TechnicalAnalysisEngine taEngine,
                              BinaryOptionsRuleEngine ruleEngine,
                              MlServiceClient mlClient,
                              RuleEngineConfig config,
                              SignalRepository signalRepository,
                              PredictionRepository predictionRepository,
                              MeterRegistry meterRegistry) {
        this.taEngine            = taEngine;
        this.ruleEngine          = ruleEngine;
        this.mlClient            = mlClient;
        this.config              = config;
        this.signalRepository    = signalRepository;
        this.predictionRepository = predictionRepository;

        this.signalsApprovedCounter = Counter.builder("signals.approved")
                .description("Number of signals approved by the fusion engine")
                .register(meterRegistry);
        this.signalsBlockedCounter = Counter.builder("signals.blocked")
                .description("Number of signals blocked by the fusion engine")
                .register(meterRegistry);
    }

    /**
     * Evaluates the full pipeline for a given symbol and candle series.
     *
     * @param symbol    trading symbol
     * @param timeFrame candle time-frame (e.g. "M1")
     * @param candles   recent candles (at least 200 recommended)
     * @return {@link FusionResult} – approved trade or reason for blocking
     */
    public FusionResult evaluate(String symbol, String timeFrame, List<Candle> candles) {

        // ── Step 1: Compute indicators ────────────────────────────────────
        IndicatorSnapshot ind = taEngine.compute(candles);

        // ── Step 2: Rule engine ───────────────────────────────────────────
        RuleEngineResult ruleResult = ruleEngine.evaluate(candles, ind);

        if (ruleResult.getDirection() == RuleEngineResult.Direction.NONE) {
            signalsBlockedCounter.increment();
            log.debug("[Fusion] {} – blocked by rules: {}", symbol, ruleResult.getReason());
            return FusionResult.blocked(ruleResult.getReason());
        }

        // ── Step 3: Persist the raw signal ────────────────────────────────
        Candle last = candles.get(candles.size() - 1);
        SignalEntity signal = signalRepository.save(SignalEntity.builder()
                .symbol(symbol)
                .timeFrame(timeFrame)
                .signalTime(Instant.now())
                .direction(ruleResult.getDirection().name())
                .strategyName("BinaryOptions-RuleEngine")
                .ruleScore(ruleResult.getRuleScore())
                .priceAtSignal(last.getClose())
                .candlePattern(ruleResult.getCandlePattern())
                .notes(ruleResult.getReason())
                .build());

        // ── Step 4: Build ML feature vector ──────────────────────────────
        MlFeatureVector features = buildFeatures(candles, ind, last);

        // ── Step 5: Call ML service ───────────────────────────────────────
        Optional<MlPredictionResponse> mlOpt = mlClient.predict(features);
        if (mlOpt.isEmpty()) {
            signalsBlockedCounter.increment();
            log.warn("[Fusion] {} – ML service unavailable; blocking trade", symbol);
            return FusionResult.blocked("ML service unavailable");
        }

        MlPredictionResponse ml = mlOpt.get();

        // ── Step 6: Persist prediction ────────────────────────────────────
        PredictionEntity prediction = predictionRepository.save(PredictionEntity.builder()
                .signal(signal)
                .modelVersion(ml.getModelVersion() != null ? ml.getModelVersion() : "unknown")
                .predictedAt(Instant.now())
                .buyProbability(ml.getBuyProbability())
                .sellProbability(ml.getSellProbability())
                .confidence(ml.getConfidence())
                .build());

        // ── Step 7: Confidence gate ───────────────────────────────────────
        if (ml.getConfidence() == null
                || ml.getConfidence().compareTo(config.getMlConfidenceThreshold()) < 0) {
            signalsBlockedCounter.increment();
            log.info("[Fusion] {} – ML confidence {} below threshold {}",
                    symbol, ml.getConfidence(), config.getMlConfidenceThreshold());
            return FusionResult.blocked("ML confidence below threshold: " + ml.getConfidence());
        }

        // ── Step 8: Direction agreement ───────────────────────────────────
        boolean directionAgrees = directionAgreement(ruleResult.getDirection(), ml);
        if (!directionAgrees) {
            signalsBlockedCounter.increment();
            log.info("[Fusion] {} – ML direction disagrees with rule engine", symbol);
            return FusionResult.blocked("ML direction disagrees with rule engine");
        }

        // ── Step 9: Approve ───────────────────────────────────────────────
        signalsApprovedCounter.increment();
        log.info("[Fusion] {} – APPROVED {} signal (confidence={})",
                symbol, ruleResult.getDirection(), ml.getConfidence());

        return FusionResult.approved(
                ruleResult.getDirection().name(),
                last.getClose(),
                signal,
                prediction,
                ml.getConfidence());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean directionAgreement(RuleEngineResult.Direction direction, MlPredictionResponse ml) {
        if (direction == RuleEngineResult.Direction.BUY) {
            return ml.getBuyProbability() != null
                    && ml.getBuyProbability().compareTo(new BigDecimal("0.50")) > 0;
        }
        return ml.getSellProbability() != null
                && ml.getSellProbability().compareTo(new BigDecimal("0.50")) > 0;
    }

    private MlFeatureVector buildFeatures(List<Candle> candles, IndicatorSnapshot ind, Candle last) {
        LocalDateTime ldt = last.getTime();

        BigDecimal price = last.getClose();
        BigDecimal range = last.getHigh().subtract(last.getLow());
        BigDecimal body  = last.getClose().subtract(last.getOpen()).abs();
        BigDecimal bodyRatio  = range.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : body.divide(range, 4, RoundingMode.HALF_UP);
        BigDecimal upperWick  = last.getHigh().subtract(last.getClose().max(last.getOpen()));
        BigDecimal lowerWick  = last.getClose().min(last.getOpen()).subtract(last.getLow());
        BigDecimal upperRatio = range.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : upperWick.divide(range, 4, RoundingMode.HALF_UP);
        BigDecimal lowerRatio = range.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : lowerWick.divide(range, 4, RoundingMode.HALF_UP);

        BigDecimal ema20Dist  = ratio(price, ind.getEma20());
        BigDecimal ema50Dist  = ratio(price, ind.getEma50());
        BigDecimal ema200Dist = ratio(price, ind.getEma200());

        BigDecimal bbPctB = BigDecimal.ZERO;
        if (ind.getBbUpper() != null && ind.getBbLower() != null) {
            BigDecimal bbRange = ind.getBbUpper().subtract(ind.getBbLower());
            if (bbRange.compareTo(BigDecimal.ZERO) != 0) {
                bbPctB = price.subtract(ind.getBbLower()).divide(bbRange, 4, RoundingMode.HALF_UP);
            }
        }

        return MlFeatureVector.builder()
                .rsi14(ind.getRsi14())
                .macdLine(ind.getMacdLine())
                .macdHistogram(ind.getMacdHistogram())
                .atr14(ind.getAtr14())
                .adx14(ind.getAdx14())
                .ema20Distance(ema20Dist)
                .ema50Distance(ema50Dist)
                .ema200Distance(ema200Dist)
                .bbPctB(bbPctB)
                .relativeVolume(ind.getRelativeVolume())
                .hourOfDay(ldt.getHour())
                .dayOfWeek(ldt.getDayOfWeek().getValue())
                .prevCandleBodyRatio(bodyRatio)
                .prevCandleUpperWick(upperRatio)
                .prevCandleLowerWick(lowerRatio)
                .stochRsiK(ind.getStochRsiK())
                .stochRsiD(ind.getStochRsiD())
                .ema50AboveEma200(ind.getEma50() != null && ind.getEma200() != null
                        && ind.getEma50().compareTo(ind.getEma200()) > 0 ? 1 : 0)
                .macdBullish(ind.getMacdHistogram() != null
                        && ind.getMacdHistogram().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0)
                .adxStrong(ind.getAdx14() != null
                        && ind.getAdx14().compareTo(config.getAdxMin()) > 0 ? 1 : 0)
                .build();
    }

    private BigDecimal ratio(BigDecimal price, BigDecimal ema) {
        if (ema == null || ema.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return price.subtract(ema).divide(ema, 6, RoundingMode.HALF_UP);
    }
}
