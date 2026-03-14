package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Factory that selects the appropriate strategy based on the current market regime.
 *
 * <p>Decision tree (quality-over-quantity, targeting 80% win rate):</p>
 * <ul>
 *   <li>{@link MarketRegime#STRONG_UPTREND} → {@link PerfectBreakoutStrategy} (primary setup)</li>
 *   <li>{@link MarketRegime#SIDEWAYS} → {@link PerfectBreakoutStrategy} (consolidation = sideways)</li>
 *   <li>{@link MarketRegime#HIGH_VOLATILITY} → {@link NoTradeStrategy} (preserve capital)</li>
 *   <li>{@link MarketRegime#STRONG_DOWNTREND} → {@link NoTradeStrategy} (preserve capital)</li>
 *   <li>{@link MarketRegime#CRASH} → {@link NoTradeStrategy} (emergency halt)</li>
 * </ul>
 *
 * <p>Note: {@link PerfectBreakoutStrategy} performs its own internal regime check and will only
 * emit a BUY signal when the regime is {@link MarketRegime#STRONG_UPTREND}. Routing SIDEWAYS
 * regimes here allows the strategy to evaluate whether a consolidation breakout is forming while
 * still applying its strict internal filters.</p>
 */
@Service
public class RegimeAwareStrategyFactory {

    private final PerfectBreakoutStrategy perfectBreakout;
    private final NoTradeStrategy         noTrade = new NoTradeStrategy();

    public RegimeAwareStrategyFactory(PerfectBreakoutStrategy perfectBreakout) {
        this.perfectBreakout = perfectBreakout;
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
                System.out.println("[Factory] Selecting PerfectBreakoutStrategy (STRONG_UPTREND)");
                yield perfectBreakout;
            }
            case SIDEWAYS -> {
                System.out.println("[Factory] Selecting PerfectBreakoutStrategy (SIDEWAYS - watching for breakout)");
                yield perfectBreakout;
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

    public PerfectBreakoutStrategy getPerfectBreakoutStrategy() { return perfectBreakout; }
    public NoTradeStrategy getNoTradeStrategy()                 { return noTrade; }
}
