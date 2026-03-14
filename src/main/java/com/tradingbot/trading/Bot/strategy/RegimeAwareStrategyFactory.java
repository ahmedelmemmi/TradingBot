package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Factory that selects the appropriate strategy based on the current market regime.
 *
 * <p>Decision tree:</p>
 * <ul>
 *   <li>{@link MarketRegime#STRONG_UPTREND} → {@link TrendFollowingStrategy}</li>
 *   <li>{@link MarketRegime#SIDEWAYS} → {@link MeanReversionStrategy}</li>
 *   <li>{@link MarketRegime#HIGH_VOLATILITY} → {@link VolatilityBreakoutStrategy}</li>
 *   <li>{@link MarketRegime#STRONG_DOWNTREND} → {@code null} (no trading)</li>
 *   <li>{@link MarketRegime#CRASH} → {@code null} (emergency halt)</li>
 * </ul>
 */
@Service
public class RegimeAwareStrategyFactory {

    private final TrendFollowingStrategy    trendFollowing    = new TrendFollowingStrategy();
    private final MeanReversionStrategy     meanReversion     = new MeanReversionStrategy();
    private final VolatilityBreakoutStrategy volatilityBreakout = new VolatilityBreakoutStrategy();

    /**
     * Returns the appropriate strategy for the given regime, or {@code null} if
     * trading should be halted (DOWNTREND or CRASH).
     *
     * @param regime current market regime
     * @return strategy instance, or {@code null} for no-trade regimes
     */
    public Strategy getStrategy(MarketRegime regime) {
        return switch (regime) {
            case STRONG_UPTREND    -> trendFollowing;
            case SIDEWAYS          -> meanReversion;
            case HIGH_VOLATILITY   -> volatilityBreakout;
            case STRONG_DOWNTREND, CRASH -> null; // capital preservation
        };
    }

    /**
     * Convenience: evaluate candles for the given regime. Returns HOLD if no
     * strategy is active.
     */
    public TradingSignal evaluate(MarketRegime regime, List<Candle> candles) {
        Strategy strategy = getStrategy(regime);
        if (strategy == null) {
            return TradingSignal.HOLD;
        }
        return strategy.evaluate(candles);
    }

    public TrendFollowingStrategy getTrendFollowingStrategy()       { return trendFollowing; }
    public MeanReversionStrategy getMeanReversionStrategy()         { return meanReversion; }
    public VolatilityBreakoutStrategy getVolatilityBreakoutStrategy() { return volatilityBreakout; }
}
