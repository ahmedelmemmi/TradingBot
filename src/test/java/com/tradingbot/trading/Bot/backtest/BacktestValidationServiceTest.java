package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BacktestValidationServiceTest {

    private final BacktestValidationService service = new BacktestValidationService();

    @Test
    void validationPassesWithSufficientPositiveData() {
        List<Position> closed = buildPositions(10, 50.0, 30.0, 0.6);
        List<BigDecimal> curve = buildEquityCurve(10_000, 11_500);
        BigDecimal start = BigDecimal.valueOf(10_000);

        BacktestValidationResult result = service.validate(closed, curve, start);

        assertTrue(result.isTradeCountPass(), "Trade count should pass");
        assertTrue(result.isWinRatePass(),    "Win rate should pass");
        assertTrue(result.isExpectancyPass(), "Expectancy should pass");
    }

    @Test
    void validationFailsWithInsufficientTrades() {
        List<Position> closed = buildPositions(3, 50.0, 30.0, 0.6);
        List<BigDecimal> curve = buildEquityCurve(10_000, 10_500);
        BigDecimal start = BigDecimal.valueOf(10_000);

        BacktestValidationResult result = service.validate(closed, curve, start);

        assertFalse(result.isTradeCountPass(), "Should fail with only 3 trades (need >= 5)");
        assertFalse(result.isValid(),          "Overall validation should fail");
        assertFalse(result.getFailureReasons().isEmpty(), "Should have failure reasons");
    }

    @Test
    void validationFailsWithHighDrawdown() {
        List<Position> closed = buildPositions(30, 50.0, 30.0, 0.6);
        // Equity curve that drops 25%
        List<BigDecimal> curve = new ArrayList<>();
        curve.add(BigDecimal.valueOf(10_000));
        curve.add(BigDecimal.valueOf(7_500)); // 25% drawdown
        curve.add(BigDecimal.valueOf(8_000));
        BigDecimal start = BigDecimal.valueOf(10_000);

        BacktestValidationResult result = service.validate(closed, curve, start);

        assertFalse(result.isDrawdownPass(), "Should fail with 25% drawdown");
    }

    @Test
    void validationResultMetricsAreCorrect() {
        // 40 trades: 24 wins @ $100, 16 losses @ $60
        List<Position> closed = buildPositions(40, 100.0, 60.0, 0.6);
        List<BigDecimal> curve = buildEquityCurve(10_000, 11_200);
        BigDecimal start = BigDecimal.valueOf(10_000);

        BacktestValidationResult result = service.validate(closed, curve, start);

        assertEquals(40, result.getTotalTrades());
        // Win rate should be approximately 0.6 (60%)
        assertTrue(result.getWinRate().compareTo(BigDecimal.valueOf(0.55)) >= 0
                && result.getWinRate().compareTo(BigDecimal.valueOf(0.65)) <= 0,
                "Win rate should be approximately 60%");
        assertTrue(result.getAvgWin().compareTo(BigDecimal.ZERO) > 0, "Avg win should be positive");
    }

    /** Builds a list of mock closed positions with given win rate and avg amounts. */
    private List<Position> buildPositions(int count, double avgWin, double avgLoss, double winRate) {
        List<Position> positions = new ArrayList<>();
        int wins = (int) (count * winRate);
        for (int i = 0; i < count; i++) {
            Position p = new Position("TEST",
                    BigDecimal.valueOf(100),
                    BigDecimal.TEN,
                    BigDecimal.valueOf(98),
                    BigDecimal.valueOf(104));
            if (i < wins) {
                p.close(BigDecimal.valueOf(100 + avgWin / 10.0)); // creates positive PnL
            } else {
                p.close(BigDecimal.valueOf(100 - avgLoss / 10.0)); // creates negative PnL
            }
            positions.add(p);
        }
        return positions;
    }

    private List<BigDecimal> buildEquityCurve(double start, double end) {
        List<BigDecimal> curve = new ArrayList<>();
        curve.add(BigDecimal.valueOf(start));
        curve.add(BigDecimal.valueOf((start + end) / 2));
        curve.add(BigDecimal.valueOf(end));
        return curve;
    }
}
