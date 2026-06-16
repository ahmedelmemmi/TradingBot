package com.tradingbot.trading.Bot.ml;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * HTTP client that calls the Python FastAPI ML service.
 *
 * <p>Falls back to a neutral prediction ({@code confidence = 0}) when the
 * service is unavailable so the signal-fusion engine can block the trade
 * gracefully instead of crashing.</p>
 */
@Service
public class MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceClient.class);

    private final WebClient webClient;
    private final Timer predictionTimer;

    public MlServiceClient(
            @Value("${ml.service.url:http://ml-service:8000}") String mlServiceUrl,
            MeterRegistry meterRegistry) {

        this.webClient = WebClient.builder()
                .baseUrl(mlServiceUrl)
                .build();

        this.predictionTimer = Timer.builder("ml.prediction.duration")
                .description("Time taken for ML prediction call")
                .register(meterRegistry);
    }

    /**
     * Calls the ML service {@code /predict} endpoint and returns the prediction.
     * Returns {@link Optional#empty()} on any error (circuit-break style).
     */
    public Optional<MlPredictionResponse> predict(MlFeatureVector features) {
        return predictionTimer.record(() -> {
            try {
                MlPredictionResponse response = webClient.post()
                        .uri("/predict")
                        .bodyValue(features)
                        .retrieve()
                        .bodyToMono(MlPredictionResponse.class)
                        .retryWhen(Retry.backoff(2, Duration.ofMillis(200)))
                        .timeout(Duration.ofSeconds(3))
                        .block();

                log.info("[ML] Prediction: buy={} sell={} confidence={}",
                        response != null ? response.getBuyProbability() : null,
                        response != null ? response.getSellProbability() : null,
                        response != null ? response.getConfidence() : null);

                return Optional.ofNullable(response);

            } catch (WebClientResponseException ex) {
                log.warn("[ML] Service returned HTTP {}: {}", ex.getStatusCode(), ex.getMessage());
                return Optional.empty();
            } catch (Exception ex) {
                log.warn("[ML] Service unavailable: {}", ex.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * Returns a neutral (fallback) prediction with zero confidence.
     * Used when the ML service is down and the rule engine should still work.
     */
    public static MlPredictionResponse neutralFallback() {
        return MlPredictionResponse.builder()
                .buyProbability(new BigDecimal("0.50"))
                .sellProbability(new BigDecimal("0.50"))
                .confidence(BigDecimal.ZERO)
                .modelVersion("FALLBACK")
                .build();
    }
}
