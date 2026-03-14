package com.tradingbot.trading.Bot.portfolio;

import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioRiskServiceTest {

    private PortfolioRiskService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioRiskService();
        service.initialize(BigDecimal.valueOf(10_000));
    }

    @Test
    void canOpenNewPositionBlocksCrash() {
        assertFalse(service.canOpenNewPosition(
                new ArrayList<>(),
                BigDecimal.valueOf(10_000),
                MarketRegime.CRASH));
    }

    @Test
    void canOpenNewPositionBlocksDowntrend() {
        assertFalse(service.canOpenNewPosition(
                new ArrayList<>(),
                BigDecimal.valueOf(10_000),
                MarketRegime.STRONG_DOWNTREND));
    }

    @Test
    void canOpenNewPositionAllowsUptrend() {
        assertTrue(service.canOpenNewPosition(
                new ArrayList<>(),
                BigDecimal.valueOf(10_000),
                MarketRegime.STRONG_UPTREND));
    }

    @Test
    void canOpenNewPositionBlocksMaxPositions() {
        List<Position> positions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            positions.add(new Position("SYM" + i,
                    BigDecimal.valueOf(100),
                    BigDecimal.ONE,
                    BigDecimal.valueOf(95),
                    BigDecimal.valueOf(110)));
        }
        assertFalse(service.canOpenNewPosition(
                positions,
                BigDecimal.valueOf(10_000),
                MarketRegime.STRONG_UPTREND));
    }

    @Test
    void canOpenNewPositionBlocksSevereDrawdown() {
        assertFalse(service.canOpenNewPosition(
                new ArrayList<>(),
                BigDecimal.valueOf(7_000),
                MarketRegime.STRONG_UPTREND),
                "Should block when drawdown > 25% from starting capital");
    }

    @Test
    void adjustRiskByDrawdownReturnsFullAtSmallDD() {
        BigDecimal mult = service.adjustRiskByDrawdown(
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000));
        assertEquals(0, BigDecimal.ONE.compareTo(mult));
    }

    @Test
    void adjustRiskByDrawdownReducesAtModerateDD() {
        BigDecimal mult = service.adjustRiskByDrawdown(
                BigDecimal.valueOf(8_500), BigDecimal.valueOf(10_000));
        assertTrue(mult.compareTo(BigDecimal.ONE) < 0,
                "15% DD should reduce risk multiplier");
    }

    @Test
    void adjustRiskByDrawdownMinimalAtSevereDD() {
        BigDecimal mult = service.adjustRiskByDrawdown(
                BigDecimal.valueOf(7_500), BigDecimal.valueOf(10_000));
        assertTrue(mult.compareTo(BigDecimal.valueOf(0.25)) <= 0,
                "25% DD should be very conservative");
    }

    @Test
    void adjustRiskByRegimeFullForUptrend() {
        assertEquals(0,
                BigDecimal.ONE.compareTo(
                        service.adjustRiskByRegime(MarketRegime.STRONG_UPTREND)));
    }

    @Test
    void adjustRiskByRegimeZeroForCrash() {
        assertEquals(0,
                BigDecimal.ZERO.compareTo(
                        service.adjustRiskByRegime(MarketRegime.CRASH)));
    }

    @Test
    void adjustRiskByRegimeReducedForSideways() {
        BigDecimal mult = service.adjustRiskByRegime(MarketRegime.SIDEWAYS);
        assertTrue(mult.compareTo(BigDecimal.ONE) < 0 &&
                mult.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void portfolioRiskCapBlocksExcessiveRisk() {
        List<Position> positions = new ArrayList<>();
        positions.add(new Position("AAPL",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(150)));

        assertFalse(service.isPortfolioRiskAcceptable(
                positions, BigDecimal.valueOf(10_000)),
                "5000 risk on 10000 equity should exceed 5% cap");
    }

    @Test
    void portfolioRiskCapAllowsSmallRisk() {
        List<Position> positions = new ArrayList<>();
        positions.add(new Position("AAPL",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(98),
                BigDecimal.valueOf(110)));

        assertTrue(service.isPortfolioRiskAcceptable(
                positions, BigDecimal.valueOf(10_000)),
                "2 risk on 10000 equity should pass");
    }
}
