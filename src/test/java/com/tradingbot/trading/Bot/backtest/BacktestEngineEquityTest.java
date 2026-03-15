package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.persistence.TradeService;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.RsiCalculator;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * Tests that the equity curve and max-drawdown calculation in {@link BacktestEngine}
 * are correct when a position is open.
 *
 * <p>Root-cause history: the original implementation computed equity as
 * {@code capital + (currentPrice − entryPrice) × qty} instead of
 * {@code capital + currentPrice × qty}.  Because the position cost
 * ({@code entryPrice × qty + commission}) was already deducted from {@code capital}
 * at entry, using only unrealized PnL left equity ≈ remaining-cash, which was a
 * tiny fraction of $10,000.  This created artificial drawdowns of 40–70% even when
 * all trades were profitable.
 *
 * <p>This test verifies the fix: a single winning trade that fills at bar 21 and
 * closes at TP at bar 22 must produce a max-drawdown well below 10%.</p>
 */
class BacktestEngineEquityTest {

    private BacktestEngine engine;

    @BeforeEach
    void setUp() {
        TradeService tradeService = mock(TradeService.class);
        doNothing().when(tradeService).recordEntry(any(), any(), any());
        doNothing().when(tradeService).recordExit(any(), any(), any());
        PositionManager positionManager = new PositionManager(tradeService);
        SlippageService slippageService = new SlippageService();
        MarketRegimeService regimeService = new MarketRegimeService();
        engine = new BacktestEngine(positionManager, slippageService, regimeService);
    }

    /**
     * Builds a simple strong-uptrend candle list of {@code n} bars.
     * Prices increase linearly from {@code startPrice} by {@code step} each bar.
     * High = close + step, Low = close - step/2.
     */
    private List<Candle> buildUptrendCandles(int n, double startPrice, double step) {
        List<Candle> candles = new ArrayList<>();
        double price = startPrice;
        for (int i = 0; i < n; i++) {
            BigDecimal close = BigDecimal.valueOf(price);
            BigDecimal open  = BigDecimal.valueOf(price - step * 0.5);
            BigDecimal high  = BigDecimal.valueOf(price + step);
            BigDecimal low   = BigDecimal.valueOf(price - step * 0.5);
            candles.add(new Candle("TEST", LocalDateTime.now().minusDays(n - i),
                    open, high, low, close, 1_000_000L));
            price += step;
        }
        return candles;
    }

    /**
     * A constant-price strategy that fires a BUY signal once at a designated bar
     * index, then never again.  Used to drive exactly one trade in the engine.
     */
    private com.tradingbot.trading.Bot.strategy.Strategy oneShotBuyStrategy(int signalBar) {
        return new com.tradingbot.trading.Bot.strategy.Strategy() {
            @Override
            public String getName() { return "OneShotBuy"; }

            @Override
            public TradingSignal evaluate(List<Candle> candles) {
                // Signal fires when the subset length equals signalBar + 1
                if (candles.size() == signalBar + 1) {
                    return TradingSignal.BUY;
                }
                return TradingSignal.HOLD;
            }
        };
    }

    /**
     * Verifies that max-drawdown is realistic (< 10%) when a single winning trade
     * is executed in a steady uptrend.
     *
     * <p>With the old bug: equity at entry was ≈ remaining-cash (e.g. $400 on $10k),
     * producing a reported drawdown of ~96% even when the trade was a winner.
     * With the fix: equity at entry is ≈ $10k (cash + position value), giving a
     * drawdown of only the commission + slippage (< 5%).</p>
     */
    @Test
    void maxDrawdownIsRealisticForSingleWinningTrade() {
        // 120 bars of strong uptrend: price rises from 100 to ~224
        List<Candle> candles = buildUptrendCandles(120, 100.0, 1.04);

        // Signal fires at bar index 60 (fills at bar 61)
        BacktestResult result = engine.runStrategy("TEST", candles, oneShotBuyStrategy(60));

        // With the equity bug fixed, drawdown during an open winning trade should be
        // far less than 10% (it will be approximately the commission cost / capital).
        assertTrue(result.getMaxDrawdown().compareTo(BigDecimal.valueOf(0.10)) < 0,
                "Max drawdown should be < 10% for a single winning trade in uptrend, "
                        + "but was " + result.getMaxDrawdown());

        // Sanity check: at least one trade should have been executed
        assertTrue(result.getTotalTrades() >= 1,
                "Expected at least 1 trade but got " + result.getTotalTrades());
    }

    /**
     * Verifies that total PnL is positive when all trades are winners
     * (uptrend candles, no stop-outs).
     */
    @Test
    void totalPnlIsPositiveInSteadyUptrend() {
        List<Candle> candles = buildUptrendCandles(120, 100.0, 1.04);
        BacktestResult result = engine.runStrategy("TEST", candles, oneShotBuyStrategy(60));

        // With a winning trade the ending capital should exceed starting capital
        assertTrue(result.getEndingCapital().compareTo(result.getStartingCapital()) > 0,
                "Ending capital should exceed starting capital in a steady uptrend trade. "
                        + "Start=" + result.getStartingCapital()
                        + " End=" + result.getEndingCapital());
    }
}
