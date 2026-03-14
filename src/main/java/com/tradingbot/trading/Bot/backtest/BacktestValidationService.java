package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Validates completed backtest results against institutional quality criteria.
 * Creates a {@link BacktestValidationResult} from a list of closed positions
 * and an equity curve.
 */
@Service
public class BacktestValidationService {

    /**
     * Validates the backtest by computing all metrics from closed positions and
     * the equity curve, then checking each criterion.
     *
     * @param closedPositions all closed positions from the backtest
     * @param equityCurve     equity at each bar
     * @param startingCapital initial capital
     * @return populated {@link BacktestValidationResult}
     */
    public BacktestValidationResult validate(List<Position> closedPositions,
                                             List<BigDecimal> equityCurve,
                                             BigDecimal startingCapital) {

        int total = closedPositions.size();
        int wins  = 0;
        BigDecimal totalWins   = BigDecimal.ZERO;
        BigDecimal totalLosses = BigDecimal.ZERO;

        for (Position p : closedPositions) {
            BigDecimal pnl = p.getPnl();
            if (pnl == null) continue;

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                totalWins = totalWins.add(pnl);
            } else {
                totalLosses = totalLosses.add(pnl.abs());
            }
        }

        int losses = total - wins;

        BigDecimal winRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);

        BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO
                : totalWins.divide(BigDecimal.valueOf(wins), 4, RoundingMode.HALF_UP);

        BigDecimal avgLoss = losses == 0 ? BigDecimal.ZERO
                : totalLosses.divide(BigDecimal.valueOf(losses), 4, RoundingMode.HALF_UP);

        BigDecimal expectancy = avgWin.multiply(winRate)
                .subtract(avgLoss.multiply(BigDecimal.ONE.subtract(winRate)));

        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) == 0
                ? totalWins
                : totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP);

        BigDecimal maxDrawdown = computeMaxDrawdown(equityCurve, startingCapital);

        BacktestValidationResult result = new BacktestValidationResult(
                total, winRate, expectancy, profitFactor, maxDrawdown, avgWin, avgLoss);

        result.printReport();

        return result;
    }

    private BigDecimal computeMaxDrawdown(List<BigDecimal> equityCurve,
                                          BigDecimal startingCapital) {
        BigDecimal peak   = startingCapital;
        BigDecimal maxDD  = BigDecimal.ZERO;

        for (BigDecimal equity : equityCurve) {
            if (equity.compareTo(peak) > 0) peak = equity;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dd = peak.subtract(equity)
                        .divide(peak, 6, RoundingMode.HALF_UP);
                if (dd.compareTo(maxDD) > 0) maxDD = dd;
            }
        }

        return maxDD;
    }
}
