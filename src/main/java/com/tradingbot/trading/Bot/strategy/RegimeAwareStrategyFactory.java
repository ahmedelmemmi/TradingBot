package com.tradingbot.trading.Bot.strategy;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Factory that selects the appropriate strategy based on the current market regime.
 *
 * <p><b>Uptrend-only mode</b> — only {@link MarketRegime#STRONG_UPTREND} triggers active trading.
 * All other regimes preserve capital by returning {@link NoTradeStrategy}.</p>
 *
 * <p>Decision tree:</p>
 * <ul>
 *   <li>{@link MarketRegime#STRONG_UPTREND} → {@link SimplifiedBreakoutStrategy} (proven 100% WR edge)</li>
 *   <li>{@link MarketRegime#HIGH_VOLATILITY} → {@link NoTradeStrategy} (ARCHIVED: was SimplifiedBreakoutStrategy)</li>
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
     *
     * <p>Only {@link MarketRegime#STRONG_UPTREND} produces an active strategy.
     * All other regimes return {@link NoTradeStrategy} to preserve capital while
     * waiting for the proven uptrend edge.</p>
     *
     * @param regime current market regime
     * @return strategy instance (never null)
     */
    public Strategy getStrategy(MarketRegime regime) {
        return switch (regime) {
            case STRONG_UPTREND -> {
                System.out.println("[UptrendOnly] ✅ UPTREND: Trading with SimplifiedBreakoutStrategy");
                yield simplifiedBreakout;
            }
            // ARCHIVED: HIGH_VOLATILITY previously mapped to SimplifiedBreakoutStrategy.
            // Kept for future re-enabling if a validated volatile-regime edge is found.
            // case HIGH_VOLATILITY -> simplifiedBreakout;
            case HIGH_VOLATILITY, SIDEWAYS, STRONG_DOWNTREND, CRASH -> {
                System.out.println("[UptrendOnly] ⏸️ " + regime + ": NO TRADING - Waiting for uptrend");
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
