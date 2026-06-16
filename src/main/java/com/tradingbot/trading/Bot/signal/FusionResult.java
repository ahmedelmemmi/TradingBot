package com.tradingbot.trading.Bot.signal;

import com.tradingbot.trading.Bot.persistence.entity.PredictionEntity;
import com.tradingbot.trading.Bot.persistence.entity.SignalEntity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Final decision output of the {@link SignalFusionEngine}.
 */
@Getter
@Builder
public class FusionResult {

    private final boolean approved;
    private final String direction;    // BUY | SELL | null
    private final BigDecimal entryPrice;
    private final SignalEntity signal;
    private final PredictionEntity prediction;
    private final BigDecimal mlConfidence;
    private final String blockReason;

    public static FusionResult approved(String direction,
                                        BigDecimal entryPrice,
                                        SignalEntity signal,
                                        PredictionEntity prediction,
                                        BigDecimal mlConfidence) {
        return FusionResult.builder()
                .approved(true)
                .direction(direction)
                .entryPrice(entryPrice)
                .signal(signal)
                .prediction(prediction)
                .mlConfidence(mlConfidence)
                .build();
    }

    public static FusionResult blocked(String reason) {
        return FusionResult.builder()
                .approved(false)
                .blockReason(reason)
                .build();
    }
}
