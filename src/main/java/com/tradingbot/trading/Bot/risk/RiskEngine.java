package com.tradingbot.trading.Bot.risk;

import com.tradingbot.trading.Bot.broker.BrokerStateService;
import com.tradingbot.trading.Bot.portfolio.PortfolioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RiskEngine {
    private static final BigDecimal MAX_RISK_PER_TRADE_PERCENT = BigDecimal.valueOf(0.01); // 1%
    private static final BigDecimal MAX_DAILY_LOSS_PERCENT = BigDecimal.valueOf(0.03); // 3%

    private final PortfolioService portfolioService;
    private final BrokerStateService brokerStateService;

    public RiskEngine(PortfolioService portfolioService, BrokerStateService brokerStateService) {
        this.portfolioService = portfolioService;
        this.brokerStateService = brokerStateService;
    }

    public boolean canTrade() {

        BigDecimal maxDailyLoss = portfolioService.getBalance()
                .multiply(MAX_DAILY_LOSS_PERCENT);

        return portfolioService.getDailyLoss()
                .compareTo(maxDailyLoss) < 0;
    }

    public BigDecimal calculatePositionSize(BigDecimal entryPrice, BigDecimal stopLossPrice) {

        BigDecimal riskAmount = portfolioService.getBalance()
                .multiply(MAX_RISK_PER_TRADE_PERCENT);

        BigDecimal riskPerShare = entryPrice.subtract(stopLossPrice).abs();

        if (riskPerShare.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        System.out.println("Entry price: " + entryPrice);
        System.out.println("Stop loss: " + stopLossPrice);
        System.out.println("Risk per share: " + entryPrice.subtract(stopLossPrice));
        System.out.println("Account cash: " + brokerStateService.getCashBalance());
        return riskAmount.divide(riskPerShare, 0, BigDecimal.ROUND_DOWN);
    }
}
