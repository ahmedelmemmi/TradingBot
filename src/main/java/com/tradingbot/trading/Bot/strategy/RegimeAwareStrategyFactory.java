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
 *   <li>{@link MarketRegime#STRONG_UPTREND} → {@link SimpleTrendFollowingStrategy}</li>
 *   <li>{@link MarketRegime#SIDEWAYS} → {@link StrictMeanReversionStrategy}</li>
 *   <li>{@link MarketRegime#HIGH_VOLATILITY} → {@link NoTradeStrategy} (preserve capital)</li>
 *   <li>{@link MarketRegime#STRONG_DOWNTREND} → {@link NoTradeStrategy} (preserve capital)</li>
 *   <li>{@link MarketRegime#CRASH} → {@link NoTradeStrategy} (emergency halt)</li>
 * </ul>
 */
@Service
public class RegimeAwareStrategyFactory {

    private final SimpleTrendFollowingStrategy simpleTrendFollowing = new SimpleTrendFollowingStrategy();
    private final StrictMeanReversionStrategy  strictMeanReversion  = new StrictMeanReversionStrategy();
    private final NoTradeStrategy              noTrade              = new NoTradeStrategy();

    /**
     * Returns the appropriate strategy for the given regime.
     * Never returns {@code null} — unfavourable regimes return {@link NoTradeStrategy}.
     *
     * @param regime current market regime
     * @return strategy instance (never null)
     */
    public Strategy getStrategy(MarketRegime regime) {
        return switch (regime) {
            case STRONG_UPTREND -> {
                System.out.println("[Factory] Selecting SimpleTrendFollowingStrategy (STRONG_UPTREND)");
                yield simpleTrendFollowing;
            }
            case SIDEWAYS -> {
                System.out.println("[Factory] Selecting StrictMeanReversionStrategy (SIDEWAYS)");
                yield strictMeanReversion;
            }
            case STRONG_DOWNTREND, CRASH, HIGH_VOLATILITY -> {
                System.out.println("[Factory] Selecting NoTradeStrategy (" + regime + ") - preserving capital");
                yield noTrade;
            }
        };
    }

    /**
     * Convenience: evaluate candles for the given regime. Returns HOLD if no
     * strategy is active.
     */
    public TradingSignal evaluate(MarketRegime regime, List<Candle> candles) {
        return getStrategy(regime).evaluate(candles);
    }

    public SimpleTrendFollowingStrategy getSimpleTrendFollowingStrategy() { return simpleTrendFollowing; }
    public StrictMeanReversionStrategy getStrictMeanReversionStrategy()   { return strictMeanReversion; }
    public NoTradeStrategy getNoTradeStrategy()                           { return noTrade; }
}
