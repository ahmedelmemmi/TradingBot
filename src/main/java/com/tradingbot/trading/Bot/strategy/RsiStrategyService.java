package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * RSI Pullback Trend strategy with a 4-layer quality filter system.
 *
 * <p>All four filters must pass before a BUY signal is issued:</p>
 * <ol>
 *   <li><b>Trend Filter</b>: MA20 &gt; MA50 must be persistent for 3 consecutive bars</li>
 *   <li><b>Volatility Filter</b>: market must not be too choppy (recent ATR acceptable)</li>
 *   <li><b>Volume Filter</b>: volume must confirm the pullback (80–120% of 20-bar avg)</li>
 *   <li><b>RSI Filter</b>: RSI tightened to 30–40 range (real oversold, not just dip)</li>
 * </ol>
 *
 * <p>Additional pullback validation: price must be within 2% of MA20 to confirm
 * a genuine pullback rather than a trend breakdown.</p>
 */
@Service
public class RsiStrategyService implements Strategy {

    private static final int    TREND_PERSISTENCE_BARS = 3;
    private static final double RSI_LOWER              = 30.0;
    private static final double RSI_UPPER              = 40.0;
    /** Max distance from MA20 for a valid pullback entry */
    private static final BigDecimal MAX_PULLBACK_FROM_MA20 = BigDecimal.valueOf(0.02);

    private final RsiCalculator rsiCalculator;
    private final TrendPersistenceService trendPersistenceService;
    private final VolatilityFilterService volatilityFilterService;

    public RsiStrategyService(RsiCalculator rsiCalculator,
                              TrendPersistenceService trendPersistenceService,
                              VolatilityFilterService volatilityFilterService) {
        this.rsiCalculator          = rsiCalculator;
        this.trendPersistenceService = trendPersistenceService;
        this.volatilityFilterService = volatilityFilterService;
    }

    @Override
    public String getName() {
        return "RSI Pullback Trend";
    }

    @Override
    public TradingSignal evaluate(List<Candle> candles) {

        if (candles.size() < 60) {
            return TradingSignal.HOLD;
        }

        BigDecimal price = candles.get(candles.size() - 1).getClose();
        BigDecimal ma20  = ma(candles, 20);
        BigDecimal ma50  = ma(candles, 50);

        // ── Layer 1: Trend Filter ─────────────────────────────────────────────
        if (!trendPersistenceService.isUptrendPersistent(candles, TREND_PERSISTENCE_BARS)) {
            System.out.println("[RsiStrategy] HOLD - trend not persistent");
            return TradingSignal.HOLD;
        }

        // MA slope must be positive (trend strengthening)
        if (candles.size() >= 70) {
            BigDecimal maSlope = calculateMaSlope(candles, 20, 10);
            if (maSlope.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("[RsiStrategy] HOLD - MA slope <= 0");
                return TradingSignal.HOLD;
            }
        }

        // Price must be above MA50
        if (price.compareTo(ma50) < 0) {
            System.out.println("[RsiStrategy] HOLD - price below MA50");
            return TradingSignal.HOLD;
        }

        // ── Layer 2: Volatility Filter ────────────────────────────────────────
        if (!volatilityFilterService.isVolatilityAcceptable(candles)) {
            System.out.println("[RsiStrategy] HOLD - volatility too high");
            return TradingSignal.HOLD;
        }

        // ── Layer 3: Volume Filter ────────────────────────────────────────────
        if (!volatilityFilterService.isVolumeConfirming(candles)) {
            System.out.println("[RsiStrategy] HOLD - volume not confirming");
            return TradingSignal.HOLD;
        }

        // ── Layer 4: RSI Filter + Pullback Depth ─────────────────────────────
        BigDecimal rsi = rsiCalculator.calculate(candles);

        // Price must be in a pullback below MA20 but within 2% of it
        if (price.compareTo(ma20) >= 0) {
            System.out.println("[RsiStrategy] HOLD - price not below MA20 (no pullback)");
            return TradingSignal.HOLD;
        }

        // Pullback depth: price should not be more than 2% below MA20
        if (ma20.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pullbackDepth = ma20.subtract(price)
                    .divide(ma20, 6, RoundingMode.HALF_UP);
            if (pullbackDepth.compareTo(MAX_PULLBACK_FROM_MA20) > 0) {
                System.out.println("[RsiStrategy] HOLD - pullback too deep="
                        + pullbackDepth.setScale(4, RoundingMode.HALF_UP));
                return TradingSignal.HOLD;
            }
        }

        // RSI must be in 30–40 range (genuine oversold pullback)
        if (rsi.compareTo(BigDecimal.valueOf(RSI_LOWER)) <= 0 ||
                rsi.compareTo(BigDecimal.valueOf(RSI_UPPER)) >= 0) {
            System.out.println("[RsiStrategy] HOLD - RSI=" + rsi.setScale(2, RoundingMode.HALF_UP)
                    + " not in [" + RSI_LOWER + "," + RSI_UPPER + "]");
            return TradingSignal.HOLD;
        }

        System.out.println("[RsiStrategy] BUY signal - all 4 filters PASSED. RSI="
                + rsi.setScale(2, RoundingMode.HALF_UP)
                + " price=" + price + " ma20=" + ma20.setScale(4, RoundingMode.HALF_UP));
        return TradingSignal.BUY;
    }

    private BigDecimal calculateMaSlope(List<Candle> candles, int maPeriod, int lookback) {
        if (candles.size() <= lookback + maPeriod) return BigDecimal.ZERO;
        BigDecimal currentMa = ma(candles, maPeriod);
        List<Candle> pastCandles = candles.subList(0, candles.size() - lookback);
        BigDecimal pastMa = ma(pastCandles, maPeriod);
        if (pastMa.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return currentMa.subtract(pastMa)
                .divide(pastMa, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal ma(List<Candle> c, int p) {
        if (c.size() < p) return BigDecimal.ZERO;
        BigDecimal s = BigDecimal.ZERO;
        for (int i = c.size() - p; i < c.size(); i++) {
            s = s.add(c.get(i).getClose());
        }
        return s.divide(BigDecimal.valueOf(p), 6, RoundingMode.HALF_UP);
    }
}