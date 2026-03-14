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
 *   <li>{@link MarketRegime#STRONG_UPTREND} → {@link SimplifiedBreakoutStrategy} (primary setup)</li>
 *   <li>{@link MarketRegime#HIGH_VOLATILITY} → {@link SimplifiedBreakoutStrategy} (volatile breakouts)</li>
 *   <li>{@link MarketRegime#SIDEWAYS} → {@link NoTradeStrategy} (no edge in sideways)</li>
 *   <li>{@link MarketRegime#STRONG_DOWNTREND} → {@link NoTradeStrategy} (preserve capital)</li>
 *   <li>{@link MarketRegime#CRASH} → {@link NoTradeStrategy} (emergency halt)</li>
 * </ul>
 */
@Service
public class RegimeAwareStrategyFactory {

    private final SimplifiedBreakoutStrategy simplifiedBreakout;
    private final PerfectBreakoutStrategy    perfectBreakout;
    private final NoTradeStrategy            noTrade = new NoTradeStrategy();

    public RegimeAwareStrategyFactory(SimplifiedBreakoutStrategy simplifiedBreakout,
                                      PerfectBreakoutStrategy perfectBreakout) {
        this.simplifiedBreakout = simplifiedBreakout;
        this.perfectBreakout    = perfectBreakout;
    }

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
                System.out.println("[Factory] Using SimplifiedBreakoutStrategy (STRONG_UPTREND)");
                yield simplifiedBreakout;
            }
            case HIGH_VOLATILITY -> {
                System.out.println("[Factory] Using SimplifiedBreakoutStrategy (HIGH_VOLATILITY)");
                yield simplifiedBreakout;
            }
            case SIDEWAYS, STRONG_DOWNTREND, CRASH -> {
                System.out.println("[Factory] NO TRADES - regime: " + regime);
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

    public SimplifiedBreakoutStrategy getSimplifiedBreakoutStrategy() { return simplifiedBreakout; }
    public PerfectBreakoutStrategy getPerfectBreakoutStrategy()       { return perfectBreakout; }
    public NoTradeStrategy getNoTradeStrategy()                       { return noTrade; }
}
