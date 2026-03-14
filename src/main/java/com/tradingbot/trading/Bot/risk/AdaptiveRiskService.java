package com.tradingbot.trading.Bot.risk;

import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import com.tradingbot.trading.Bot.strategy.AtrCalculator;
import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Adaptive position sizing and stop loss service based on:
 * <ul>
 *   <li>Kelly Criterion (using actual win rate and R:R from recent trades)</li>
 *   <li>ATR-based dynamic stop loss adjusted per market regime</li>
 * </ul>
 *
 * <p>Uses 25% fractional Kelly to avoid over-leveraging (quarter-Kelly).
 * Requires a minimum of 30 closed trades for statistical significance;
 * falls back to 1% risk prior to that threshold.</p>
 */
@Service
public class AdaptiveRiskService {

    /** Minimum trades needed for Kelly to be statistically meaningful */
    private static final int MIN_TRADES_FOR_KELLY = 30;

    /** Maximum risk fraction per trade */
    private static final BigDecimal MAX_RISK_PCT = BigDecimal.valueOf(0.02);  // 2%

    /** Minimum risk fraction per trade */
    private static final BigDecimal MIN_RISK_PCT = BigDecimal.valueOf(0.005); // 0.5%

    /** Fallback risk when insufficient trade history */
    private static final BigDecimal DEFAULT_RISK_PCT = BigDecimal.valueOf(0.01); // 1%

    /** Fraction of Kelly to apply (quarter-Kelly) */
    private static final BigDecimal KELLY_FRACTION = BigDecimal.valueOf(0.25);

    private final AtrCalculator atrCalculator = new AtrCalculator();

    /**
     * Calculates the optimal risk fraction using the Kelly Criterion.
     *
     * <p>Formula: f* = (p × b − q) / b, where:
     * <ul>
     *   <li>p = win probability</li>
     *   <li>q = 1 − p (loss probability)</li>
     *   <li>b = average win / average loss (R:R ratio)</li>
     * </ul>
     * Returns 25% of Kelly, capped between MIN_RISK_PCT and MAX_RISK_PCT.</p>
     *
     * @param closedPositions list of all closed positions from the current backtest
     * @return risk fraction to apply (e.g. 0.012 = 1.2% of capital)
     */
    public BigDecimal calculateKellyRiskPercent(List<Position> closedPositions) {

        if (closedPositions.size() < MIN_TRADES_FOR_KELLY) {
            System.out.println("[AdaptiveRisk] Insufficient trades (" + closedPositions.size()
                    + "). Using default risk=" + DEFAULT_RISK_PCT);
            return DEFAULT_RISK_PCT;
        }

        int wins = 0;
        BigDecimal totalWinAmount  = BigDecimal.ZERO;
        BigDecimal totalLossAmount = BigDecimal.ZERO;

        for (Position p : closedPositions) {
            BigDecimal pnl = p.getPnl();
            if (pnl == null) continue;

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                totalWinAmount = totalWinAmount.add(pnl);
            } else {
                totalLossAmount = totalLossAmount.add(pnl.abs());
            }
        }

        int losses = closedPositions.size() - wins;

        if (wins == 0 || losses == 0 || totalLossAmount.compareTo(BigDecimal.ZERO) == 0) {
            System.out.println("[AdaptiveRisk] Edge case (all wins or all losses). Using default.");
            return DEFAULT_RISK_PCT;
        }

        BigDecimal p = BigDecimal.valueOf(wins)
                .divide(BigDecimal.valueOf(closedPositions.size()), 6, RoundingMode.HALF_UP);

        BigDecimal q = BigDecimal.ONE.subtract(p);

        BigDecimal avgWin  = totalWinAmount.divide(BigDecimal.valueOf(wins), 6, RoundingMode.HALF_UP);
        BigDecimal avgLoss = totalLossAmount.divide(BigDecimal.valueOf(losses), 6, RoundingMode.HALF_UP);

        BigDecimal b = avgWin.divide(avgLoss, 6, RoundingMode.HALF_UP);

        // Kelly: f* = (p*b - q) / b
        BigDecimal kellyFull = p.multiply(b).subtract(q).divide(b, 6, RoundingMode.HALF_UP);

        if (kellyFull.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("[AdaptiveRisk] Negative Kelly (" + kellyFull + "). Strategy has no edge.");
            return MIN_RISK_PCT;
        }

        // Apply quarter-Kelly
        BigDecimal quarterKelly = kellyFull.multiply(KELLY_FRACTION);

        // Clamp between min and max
        BigDecimal result = quarterKelly.max(MIN_RISK_PCT).min(MAX_RISK_PCT);

        System.out.println("[AdaptiveRisk] Kelly p=" + p.setScale(3, RoundingMode.HALF_UP)
                + " b=" + b.setScale(3, RoundingMode.HALF_UP)
                + " fullKelly=" + kellyFull.setScale(4, RoundingMode.HALF_UP)
                + " quarterKelly=" + quarterKelly.setScale(4, RoundingMode.HALF_UP)
                + " finalRisk=" + result.setScale(4, RoundingMode.HALF_UP));

        return result;
    }

    /**
     * Calculates an ATR-based stop loss level adjusted for the current regime.
     *
     * <p>ATR multipliers by regime:
     * <ul>
     *   <li>STRONG_UPTREND: 2.5× ATR</li>
     *   <li>HIGH_VOLATILITY: 3.0× ATR</li>
     *   <li>CRASH: 3.5× ATR</li>
     *   <li>SIDEWAYS / other: 2.0× ATR</li>
     * </ul>
     * </p>
     *
     * @param candles current candle series
     * @param entryPrice entry fill price
     * @param regime current market regime
     * @return stop loss price level
     */
    public BigDecimal calculateAdaptiveStop(List<Candle> candles,
                                            BigDecimal entryPrice,
                                            MarketRegime regime) {

        BigDecimal atr = atrCalculator.calculate(candles, 14);

        if (atr.compareTo(BigDecimal.ZERO) <= 0) {
            // Fallback to fixed 2% stop
            return entryPrice.multiply(BigDecimal.valueOf(0.98)).setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal multiplier = atrMultiplier(regime);

        BigDecimal stopDistance = atr.multiply(multiplier);
        BigDecimal stopLevel = entryPrice.subtract(stopDistance).setScale(4, RoundingMode.HALF_UP);

        System.out.println("[AdaptiveRisk] ATR=" + atr.setScale(4, RoundingMode.HALF_UP)
                + " multiplier=" + multiplier
                + " stop=" + stopLevel
                + " regime=" + regime);

        return stopLevel;
    }

    private BigDecimal atrMultiplier(MarketRegime regime) {
        if (regime == null) return BigDecimal.valueOf(2.5);
        return switch (regime) {
            case STRONG_UPTREND   -> BigDecimal.valueOf(2.5);
            case HIGH_VOLATILITY  -> BigDecimal.valueOf(3.0);
            case CRASH            -> BigDecimal.valueOf(3.5);
            default               -> BigDecimal.valueOf(2.0);
        };
    }
}
