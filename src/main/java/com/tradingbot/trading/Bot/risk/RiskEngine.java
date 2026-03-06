package com.tradingbot.trading.Bot.risk;

import com.tradingbot.trading.Bot.position.PositionManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class RiskEngine {

    private static final BigDecimal DAILY_LOSS_LIMIT_PERCENT =
            BigDecimal.valueOf(0.02); // 2%

    private static final BigDecimal RISK_PER_TRADE_PERCENT =
            BigDecimal.valueOf(0.01); // 1%

    private static final int MAX_OPEN_POSITIONS = 3;

    private BigDecimal startingBalance = BigDecimal.ZERO;
    private BigDecimal realizedPnL = BigDecimal.ZERO;

    private LocalDate currentTradingDay = LocalDate.now();

    private final PositionManager positionManager;

    public RiskEngine(PositionManager positionManager) {
        this.positionManager = positionManager;
    }

    public synchronized boolean canTrade() {

        resetIfNewDay();

        if (startingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("⚠ Starting balance not initialized yet.");
            return false;
        }

        if (positionManager.getOpenPositions().size() >= MAX_OPEN_POSITIONS) {
            System.out.println("⚠ Max open positions reached.");
            return false;
        }

        BigDecimal maxLoss =
                startingBalance.multiply(DAILY_LOSS_LIMIT_PERCENT);

        if (realizedPnL.abs().compareTo(maxLoss) >= 0 &&
                realizedPnL.signum() < 0) {

            System.out.println("⚠ DAILY LOSS LIMIT REACHED.");
            System.out.println("Trading disabled for today.");

            return false;
        }

        return true;
    }

    public synchronized void setStartingBalance(BigDecimal balance) {

        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (startingBalance.compareTo(BigDecimal.ZERO) == 0) {

            startingBalance = balance;

            System.out.println("Starting balance set: " + startingBalance);
        }
    }

    public synchronized void recordTradePnL(BigDecimal pnl) {

        realizedPnL = realizedPnL.add(pnl);

        System.out.println("Trade PnL recorded: " + pnl);
        System.out.println("Daily PnL: " + realizedPnL);
    }

    private void resetIfNewDay() {

        LocalDate today = LocalDate.now();

        if (!today.equals(currentTradingDay)) {

            currentTradingDay = today;
            realizedPnL = BigDecimal.ZERO;

            System.out.println("New trading day. PnL reset.");
        }
    }

    public BigDecimal calculatePositionSize(BigDecimal entryPrice,
                                            BigDecimal stopLoss) {

        if (startingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal riskPerShare =
                entryPrice.subtract(stopLoss).abs();

        if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal accountRisk =
                startingBalance.multiply(RISK_PER_TRADE_PERCENT);

        BigDecimal shares =
                accountRisk.divide(riskPerShare, 0, RoundingMode.DOWN);

        if (shares.compareTo(BigDecimal.ONE) < 0) {
            return BigDecimal.ONE;
        }

        return shares;
    }
}