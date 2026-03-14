package com.tradingbot.trading.Bot.execution;

import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks trade cooldowns per symbol to prevent immediate re-entry after exit.
 * Professional trading systems require a minimum waiting period after a stop-out
 * to avoid whipsaw loops in choppy markets.
 *
 * <ul>
 *   <li>Default cooldown: 5 bars after exit</li>
 *   <li>Sideways regime: 10 bars (market is choppy)</li>
 * </ul>
 */
@Service
public class TradeTimingService {

    private static final int DEFAULT_COOLDOWN_BARS = 5;
    private static final int SIDEWAYS_COOLDOWN_BARS = 10;

    /** Maps symbol -> bar index at which cooldown expires. */
    private final Map<String, Integer> cooldownMap = new HashMap<>();

    /**
     * Records the bar index at which the position was exited, setting a cooldown
     * that prevents re-entry until sufficient bars have elapsed.
     *
     * @param symbol     ticker symbol
     * @param currentBar bar index of the exit
     * @param regime     current market regime (affects cooldown length)
     */
    public void recordExit(String symbol, int currentBar, MarketRegime regime) {
        int cooldownBars = (regime == MarketRegime.SIDEWAYS)
                ? SIDEWAYS_COOLDOWN_BARS
                : DEFAULT_COOLDOWN_BARS;

        int cooldownUntil = currentBar + cooldownBars;
        cooldownMap.put(symbol, cooldownUntil);

        System.out.println("[TradeTimingService] Cooldown set for " + symbol
                + " until bar=" + cooldownUntil
                + " (regime=" + regime + ", bars=" + cooldownBars + ")");
    }

    /**
     * Returns true if the cooldown has expired and a new entry is permitted.
     *
     * @param symbol     ticker symbol
     * @param currentBar current bar index
     * @return true if entry is allowed
     */
    public boolean canEnter(String symbol, int currentBar) {
        int cooldownUntil = cooldownMap.getOrDefault(symbol, 0);
        boolean allowed = currentBar >= cooldownUntil;

        if (!allowed) {
            System.out.println("[TradeTimingService] Entry BLOCKED for " + symbol
                    + " currentBar=" + currentBar
                    + " cooldownUntil=" + cooldownUntil);
        }

        return allowed;
    }

    /** Resets all cooldowns (call before each new backtest run). */
    public void reset() {
        cooldownMap.clear();
    }
}
