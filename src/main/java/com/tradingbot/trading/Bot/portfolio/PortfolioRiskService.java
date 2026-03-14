package com.tradingbot.trading.Bot.portfolio;

import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PortfolioRiskService {

    private static final BigDecimal MAX_PORTFOLIO_RISK = BigDecimal.valueOf(0.05);
    private static final int MAX_POSITIONS = 5;

    private BigDecimal startingCapital = BigDecimal.ZERO;

    public void initialize(BigDecimal capital) {
        this.startingCapital = capital;
    }

    /**
     * ONLY hard blocks:
     * - too many positions
     * - crash regime
     *
     * Drawdown does NOT block trading anymore.
     */
    public boolean canOpenNewPosition(
            List<Position> openPositions,
            BigDecimal currentEquity,
            MarketRegimeService.MarketRegime regime
    ) {

        if (openPositions.size() >= MAX_POSITIONS) {
            System.out.println("RISK BLOCK: max positions reached");
            return false;
        }

        if (regime == MarketRegimeService.MarketRegime.CRASH) {
            System.out.println("RISK BLOCK: crash regime");
            return false;
        }

        return true;
    }

    /**
     * Adaptive risk multiplier based on drawdown.
     * This replaces the old HARD BLOCK.
     */
    public BigDecimal adjustRiskByDrawdown(BigDecimal currentEquity) {

        if (startingCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        BigDecimal drawdown =
                startingCapital.subtract(currentEquity)
                        .divide(startingCapital, 4, RoundingMode.HALF_UP);

        // < 10% DD → full risk
        if (drawdown.compareTo(BigDecimal.valueOf(0.10)) < 0) {
            return BigDecimal.ONE;
        }

        // 10-20% DD → reduce size
        if (drawdown.compareTo(BigDecimal.valueOf(0.20)) < 0) {
            System.out.println("RISK REDUCE: drawdown > 10%");
            return BigDecimal.valueOf(0.5);
        }

        // >20% DD → trade very small
        System.out.println("RISK HEAVY REDUCE: drawdown > 20%");
        return BigDecimal.valueOf(0.25);
    }

    /**
     * Market regime risk multiplier
     */
    public BigDecimal adjustRiskByRegime(MarketRegimeService.MarketRegime regime) {

        return switch (regime) {

            case STRONG_UPTREND -> BigDecimal.ONE;

            case SIDEWAYS -> BigDecimal.valueOf(0.6);

            case HIGH_VOLATILITY -> BigDecimal.valueOf(0.4);

            case STRONG_DOWNTREND -> BigDecimal.valueOf(0.25);

            case CRASH -> BigDecimal.ZERO;
        };
    }

    /**
     * Total open risk in portfolio
     */
    public BigDecimal calculatePortfolioRisk(List<Position> openPositions) {

        BigDecimal totalRisk = BigDecimal.ZERO;

        for (Position p : openPositions) {

            BigDecimal riskPerShare =
                    p.getEntryPrice().subtract(p.getStopLoss()).abs();

            BigDecimal risk =
                    riskPerShare.multiply(p.getQuantity());

            totalRisk = totalRisk.add(risk);
        }

        return totalRisk;
    }

    /**
     * Professional portfolio risk cap
     */
    public boolean isPortfolioRiskAcceptable(
            List<Position> openPositions,
            BigDecimal equity
    ) {

        BigDecimal totalRisk = calculatePortfolioRisk(openPositions);

        BigDecimal maxAllowed =
                equity.multiply(MAX_PORTFOLIO_RISK);

        if (totalRisk.compareTo(maxAllowed) > 0) {
            System.out.println("RISK BLOCK: portfolio risk exceeded");
            return false;
        }

        return true;
    }
}