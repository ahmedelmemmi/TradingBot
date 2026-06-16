package com.tradingbot.trading.Bot.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response body returned by the Python ML service's {@code /predict} endpoint.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlPredictionResponse {

    @JsonProperty("buyProbability")
    private BigDecimal buyProbability;

    @JsonProperty("sellProbability")
    private BigDecimal sellProbability;

    @JsonProperty("confidence")
    private BigDecimal confidence;

    @JsonProperty("modelVersion")
    private String modelVersion;
}
