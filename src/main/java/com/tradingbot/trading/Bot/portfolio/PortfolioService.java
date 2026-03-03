package com.tradingbot.trading.Bot.portfolio;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PortfolioService {
    private BigDecimal balance = BigDecimal.valueOf(10_000); // Paper starting capital
    private BigDecimal dailyLoss = BigDecimal.ZERO;

    public BigDecimal getBalance() {
        return balance;
    }

    public void updateBalance(BigDecimal amountChange) {
        balance = balance.add(amountChange);
    }

    public BigDecimal getDailyLoss() {
        return dailyLoss;
    }

    public void addDailyLoss(BigDecimal loss) {
        dailyLoss = dailyLoss.add(loss);
    }

    public void resetDailyLoss() {
        dailyLoss = BigDecimal.ZERO;
    }
}
