package com.tradingbot.trading.Bot.execution;

import com.tradingbot.trading.Bot.broker.BrokerStateService;
import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.domain.Position;
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
    private final BrokerStateService brokerStateService;
    private static final BigDecimal STOP_LOSS_PERCENT = BigDecimal.valueOf(0.02);
    private static final BigDecimal TAKE_PROFIT_PERCENT = BigDecimal.valueOf(0.04);

    public TradeDecisionService(RsiStrategyService strategyService,
                                RiskEngine riskEngine, PositionManager positionManager, BrokerStateService brokerStateService) {
        this.strategyService = strategyService;
        this.riskEngine = riskEngine;
        this.positionManager = positionManager;
        this.brokerStateService = brokerStateService;
    }

    public TradeDecision evaluate(String symbol, List<Candle> candles) {
        System.out.println(">>> TradeDecisionService.evaluate() entered");
        System.out.println("RiskEngine canTrade(): " + riskEngine.canTrade());
        System.out.println("Evaluating strategy...");
        System.out.println("Broker has open position: " + brokerStateService.hasOpenPosition(symbol));
        System.out.println("Local has open position: " + positionManager.getOpenPosition(symbol).isPresent());
        if (!riskEngine.canTrade()) {
            return TradeDecision.noTrade("Daily loss limit reached");
        }
        // 🔐 BROKER DUPLICATE CHECK (REAL ACCOUNT)
        if (brokerStateService.hasOpenPosition(symbol)) {
            return TradeDecision.noTrade("Already holding position at broker");
        }

        // 🔐 LOCAL POSITION CHECK (extra safety)
        if (positionManager.getOpenPosition(symbol).isPresent()) {
            return TradeDecision.noTrade("Already holding position locally");
        }

        TradingSignal signal = strategyService.evaluate(candles);

        System.out.println("RSI signal: " + signal);

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
        System.out.println("Calculated position size: " + quantity);

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

        return TradeDecision.buy(symbol, entryPrice, quantity, stopLoss, takeProfit);
    }
}
