package com.tradingbot.trading.Bot.execution;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.position.Position;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.risk.RiskEngine;
import com.tradingbot.trading.Bot.strategy.RsiStrategyService;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TradeDecisionService {
    private final RsiStrategyService strategyService;
    private final RiskEngine riskEngine;
    private final PositionManager positionManager;

    private static final BigDecimal STOP_LOSS_PERCENT = BigDecimal.valueOf(0.02);
    private static final BigDecimal TAKE_PROFIT_PERCENT = BigDecimal.valueOf(0.04);

    public TradeDecisionService(RsiStrategyService strategyService,
                                RiskEngine riskEngine, PositionManager positionManager) {
        this.strategyService = strategyService;
        this.riskEngine = riskEngine;
        this.positionManager = positionManager;
    }

    public TradeDecision evaluate(String symbol, List<Candle> candles) {

        if (!riskEngine.canTrade()) {
            return TradeDecision.noTrade("Daily loss limit reached");
        }

        TradingSignal signal = strategyService.evaluate(candles);

        if (signal != TradingSignal.BUY) {
            return TradeDecision.noTrade("No BUY signal");
        }

        BigDecimal entryPrice = candles.get(candles.size() - 1).getClose();

        BigDecimal stopLoss = entryPrice
                .multiply(BigDecimal.ONE.subtract(STOP_LOSS_PERCENT))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal takeProfit = entryPrice
                .multiply(BigDecimal.ONE.add(TAKE_PROFIT_PERCENT))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal quantity = riskEngine.calculatePositionSize(entryPrice, stopLoss);

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return TradeDecision.noTrade("Position size too small");
        }

        Position position = new Position(
                symbol,
                entryPrice,
                quantity,
                stopLoss,
                takeProfit
        );

        positionManager.openPosition(position);

        return TradeDecision.buy(symbol, entryPrice, quantity, stopLoss, takeProfit);
    }
}
