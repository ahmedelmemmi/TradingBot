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
    private static final BigDecimal HARD_DRAWDOWN_BLOCK = BigDecimal.valueOf(0.25);

    private BigDecimal startingCapital = BigDecimal.ZERO;

    public void initialize(BigDecimal capital) {
        this.startingCapital = capital;
    }

    /**
     * Hard blocks:
     * - too many positions
     * - crash regime
     * - severe drawdown (>25% from starting capital)
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

        if (regime == MarketRegimeService.MarketRegime.STRONG_DOWNTREND) {
            System.out.println("RISK BLOCK: strong downtrend regime");
            return false;
        }

        if (startingCapital.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ddFromStart = startingCapital.subtract(currentEquity)
                    .divide(startingCapital, 4, RoundingMode.HALF_UP);
            if (ddFromStart.compareTo(HARD_DRAWDOWN_BLOCK) > 0) {
                System.out.println("RISK BLOCK: severe drawdown > 25% from start");
                return false;
            }
        }

        return true;
    }

    /**
     * Adaptive risk multiplier based on drawdown from peak equity.
     */
    public BigDecimal adjustRiskByDrawdown(BigDecimal currentEquity, BigDecimal peakEquity) {

        if (peakEquity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        BigDecimal drawdown =
                peakEquity.subtract(currentEquity)
                        .divide(peakEquity, 4, RoundingMode.HALF_UP);

        if (drawdown.compareTo(BigDecimal.valueOf(0.05)) < 0) {
            return BigDecimal.ONE;
        }

        if (drawdown.compareTo(BigDecimal.valueOf(0.10)) < 0) {
            return BigDecimal.valueOf(0.75);
        }

        if (drawdown.compareTo(BigDecimal.valueOf(0.15)) < 0) {
            System.out.println("RISK REDUCE: drawdown > 10%");
            return BigDecimal.valueOf(0.5);
        }

        if (drawdown.compareTo(BigDecimal.valueOf(0.20)) < 0) {
            System.out.println("RISK HEAVY REDUCE: drawdown > 15%");
            return BigDecimal.valueOf(0.25);
        }

        System.out.println("RISK MINIMAL: drawdown > 20%");
        return BigDecimal.valueOf(0.10);
    }

    /**
     * Legacy overload for backward compatibility.
     */
    public BigDecimal adjustRiskByDrawdown(BigDecimal currentEquity) {
        return adjustRiskByDrawdown(currentEquity, startingCapital);
    }

    /**
     * Market regime risk multiplier
     */
    public BigDecimal adjustRiskByRegime(MarketRegimeService.MarketRegime regime) {

        return switch (regime) {

            case STRONG_UPTREND -> BigDecimal.ONE;

            case SIDEWAYS -> BigDecimal.valueOf(0.5);

            case HIGH_VOLATILITY -> BigDecimal.valueOf(0.3);

            case STRONG_DOWNTREND -> BigDecimal.valueOf(0.15);

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