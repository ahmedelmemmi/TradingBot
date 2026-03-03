package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.execution.TradeDecision;
import com.tradingbot.trading.Bot.execution.TradeDecisionService;
import com.tradingbot.trading.Bot.position.Position;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.Strategy;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class BacktestEngine {
    private final TradeDecisionService decisionService;
    private final PositionManager positionManager;

    private static final BigDecimal STARTING_CAPITAL = BigDecimal.valueOf(10_000);

    public BacktestEngine(TradeDecisionService decisionService,
                          PositionManager positionManager) {
        this.decisionService = decisionService;
        this.positionManager = positionManager;
    }

    public BacktestResult run(String symbol, List<Candle> candles) {

        BigDecimal capital = STARTING_CAPITAL;

        for (int i = 14; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);

            TradeDecision decision =
                    decisionService.evaluate(symbol, subset);

            BigDecimal currentPrice =
                    candles.get(i).getClose();

            positionManager.updatePrice(symbol, currentPrice);
        }

        int totalTrades = positionManager.getClosedPositions().size();
        int winningTrades = 0;
        int losingTrades = 0;
        BigDecimal totalPnL = BigDecimal.ZERO;

        for (Position position : positionManager.getClosedPositions()) {

            BigDecimal pnl = position.getPnl();
            totalPnL = totalPnL.add(pnl);

            if (pnl.compareTo(BigDecimal.ZERO) > 0)
                winningTrades++;
            else
                losingTrades++;
        }

        BigDecimal endingCapital = capital.add(totalPnL);

        return new BacktestResult(
                STARTING_CAPITAL,
                endingCapital,
                totalTrades,
                winningTrades,
                losingTrades,
                totalPnL
        );
    }

    public List<BacktestResult> runMultiple(String symbol,
                                            List<Candle> candles,
                                            List<Strategy> strategies) {
        List<BacktestResult> results = new ArrayList<>();

        for (Strategy strategy : strategies) {
            // Reset positions for each strategy
            positionManager.getOpenPositions().clear();
            positionManager.getClosedPositions().clear();

            BacktestResult result = runStrategy(symbol, candles, strategy);
            results.add(result);
        }

        return results;
    }

    private BacktestResult runStrategy(String symbol,
                                       List<Candle> candles,
                                       Strategy strategy) {

        // Start with initial capital
        BigDecimal capital = STARTING_CAPITAL;

        // Clear positions for this strategy run
        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        // Iterate over candles (start after enough for indicator calculation)
        for (int i = 14; i < candles.size(); i++) {

            // Use subset of candles up to current for evaluation
            List<Candle> subset = candles.subList(0, i + 1);

            // Evaluate strategy
            TradingSignal signal = strategy.evaluate(subset);

            // Current market price
            BigDecimal currentPrice = candles.get(i).getClose();

            // Update any open positions for SL/TP
            positionManager.updatePrice(symbol, currentPrice);

            // If strategy signals BUY and no open position exists, open new position
            if (signal == TradingSignal.BUY &&
                    positionManager.getOpenPosition(symbol).isEmpty()) {

                // Simple fixed stop-loss and take-profit
                BigDecimal stopLoss = currentPrice
                        .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.02)))
                        .setScale(4, RoundingMode.HALF_UP);

                BigDecimal takeProfit = currentPrice
                        .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.04)))
                        .setScale(4, RoundingMode.HALF_UP);

                BigDecimal quantity = new BigDecimal(10); // simple fixed size for backtest

                positionManager.openPosition(
                        new com.tradingbot.trading.Bot.position.Position(
                                symbol,
                                currentPrice,
                                quantity,
                                stopLoss,
                                takeProfit
                        )
                );
            }
        }

        // Calculate results
        int totalTrades = positionManager.getClosedPositions().size();
        int winningTrades = 0;
        int losingTrades = 0;
        BigDecimal totalPnL = BigDecimal.ZERO;

        for (com.tradingbot.trading.Bot.position.Position position : positionManager.getClosedPositions()) {
            BigDecimal pnl = position.getPnl();
            totalPnL = totalPnL.add(pnl);
            if (pnl.compareTo(BigDecimal.ZERO) > 0) winningTrades++;
            else losingTrades++;
        }

        BigDecimal endingCapital = capital.add(totalPnL);

        return new BacktestResult(
                STARTING_CAPITAL,
                endingCapital,
                totalTrades,
                winningTrades,
                losingTrades,
                totalPnL
        );
    }
}
