package com.tradingbot.trading.Bot.backtest;

import com.tradingbot.trading.Bot.domain.Candle;
import com.tradingbot.trading.Bot.domain.Position;
import com.tradingbot.trading.Bot.market.MarketRegimeService;
import com.tradingbot.trading.Bot.market.MarketRegimeService.MarketRegime;
import com.tradingbot.trading.Bot.portfolio.PortfolioRiskService;
import com.tradingbot.trading.Bot.position.PositionManager;
import com.tradingbot.trading.Bot.strategy.AtrCalculator;
import com.tradingbot.trading.Bot.strategy.Strategy;
import com.tradingbot.trading.Bot.strategy.TradingSignal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioBacktestEngine {

    private final PositionManager positionManager;
    private final PortfolioRiskService portfolioRiskService;
    private final MarketRegimeService regimeService;

    private static final BigDecimal STARTING_CAPITAL =
            BigDecimal.valueOf(10_000);

    private static final BigDecimal SLIPPAGE =
            BigDecimal.valueOf(0.0005);

    private static final BigDecimal COMMISSION =
            BigDecimal.valueOf(1.0);

    private static final int TRADE_COOLDOWN_BARS = 10;

    public PortfolioBacktestEngine(PositionManager positionManager,
                                   PortfolioRiskService portfolioRiskService,
                                   MarketRegimeService regimeService) {
        this.positionManager = positionManager;
        this.portfolioRiskService = portfolioRiskService;
        this.regimeService = regimeService;
    }

    public PortfolioBacktestResult run(String symbol,
                                       List<Candle> candles,
                                       Strategy strategy) {

        BigDecimal capital = STARTING_CAPITAL;
        BigDecimal peakEquity = STARTING_CAPITAL;

        portfolioRiskService.initialize(capital);

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();
        Map<String, Integer> cooldownMap = new HashMap<>();

        for (int i = 60; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);

            MarketRegime regime = regimeService.detect(subset);

            TradingSignal signal = strategy.evaluate(subset);

            BigDecimal price = candles.get(i).getClose();

            positionManager.updatePrice(symbol, price);

            capital = checkStopsWithCapitalReturn(symbol, price, capital, cooldownMap, i);

            int cooldownUntil = cooldownMap.getOrDefault(symbol, 0);

            if (signal == TradingSignal.BUY &&
                    positionManager.getOpenPosition(symbol).isEmpty() &&
                    i >= cooldownUntil) {

                boolean allowed =
                        portfolioRiskService.canOpenNewPosition(
                                positionManager.getOpenPositions(),
                                capital,
                                regime
                        );

                if (!allowed)
                    continue;

                if (!portfolioRiskService.isPortfolioRiskAcceptable(
                        positionManager.getOpenPositions(), calculateEquity(capital)))
                    continue;

                AtrCalculator atrCalc = new AtrCalculator();
                BigDecimal atr = atrCalc.calculate(subset, 14);

                if (atr.compareTo(BigDecimal.ZERO) <= 0)
                    continue;

                BigDecimal stop =
                        price.subtract(atr.multiply(BigDecimal.valueOf(2.5)));

                BigDecimal take =
                        price.add(atr.multiply(BigDecimal.valueOf(4.0)));

                BigDecimal riskPerTrade =
                        capital.multiply(BigDecimal.valueOf(0.01));

                BigDecimal riskPerShare =
                        price.subtract(stop).abs();

                if (riskPerShare.compareTo(BigDecimal.ZERO) == 0)
                    continue;

                BigDecimal qty =
                        riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);

                BigDecimal regimeMult =
                        portfolioRiskService.adjustRiskByRegime(regime);

                BigDecimal ddMult =
                        portfolioRiskService.adjustRiskByDrawdown(
                                calculateEquity(capital), peakEquity);

                BigDecimal finalMult =
                        regimeMult.multiply(ddMult);

                qty = qty.multiply(finalMult)
                        .setScale(0, RoundingMode.DOWN);

                if (qty.compareTo(BigDecimal.ZERO) <= 0)
                    continue;

                BigDecimal slippageAdjustedPrice =
                        price.multiply(BigDecimal.ONE.add(SLIPPAGE));

                BigDecimal cost = slippageAdjustedPrice.multiply(qty)
                        .add(COMMISSION);

                if (cost.compareTo(capital) > 0)
                    continue;

                capital = capital.subtract(cost);

                Position position =
                        new Position(symbol, slippageAdjustedPrice, qty, stop, take);

                positionManager.openPosition(position);

                System.out.println("BACKTEST BUY "
                        + symbol
                        + " regime=" + regime
                        + " qty=" + qty
                        + " price=" + slippageAdjustedPrice
                        + " capital=" + capital);
            }

            BigDecimal equity =
                    calculateEquity(capital);

            if (equity.compareTo(peakEquity) > 0) {
                peakEquity = equity;
            }

            equityCurve.add(equity);
        }

        capital = forceClose(symbol, candles, capital);

        return buildResult(equityCurve, capital, peakEquity);
    }

    private BigDecimal checkStopsWithCapitalReturn(
            String symbol, BigDecimal price, BigDecimal capital,
            Map<String, Integer> cooldownMap, int currentBar) {

        var optional = positionManager.getOpenPosition(symbol);

        if (optional.isEmpty())
            return capital;

        Position p = optional.get();

        if (price.compareTo(p.getStopLoss()) <= 0 ||
                price.compareTo(p.getTakeProfit()) >= 0) {

            BigDecimal exitPrice =
                    price.multiply(BigDecimal.ONE.subtract(SLIPPAGE));

            p.close(exitPrice);

            BigDecimal proceeds =
                    exitPrice.multiply(p.getQuantity())
                            .subtract(COMMISSION);

            capital = capital.add(proceeds);

            positionManager.getClosedPositions().add(p);
            positionManager.getOpenPositions().remove(p);

            cooldownMap.put(symbol, currentBar + TRADE_COOLDOWN_BARS);
        }

        return capital;
    }

    private BigDecimal calculateEquity(BigDecimal capital) {

        BigDecimal floating = BigDecimal.ZERO;

        for (Position p : positionManager.getOpenPositions()) {
            if (p.getPnl() != null)
                floating = floating.add(p.getPnl());
        }

        return capital.add(floating);
    }

    private BigDecimal forceClose(String symbol,
                                  List<Candle> candles,
                                  BigDecimal capital) {

        if (positionManager.getOpenPositions().isEmpty())
            return capital;

        BigDecimal last =
                candles.get(candles.size() - 1).getClose();

        List<Position> toClose = new ArrayList<>();
        for (Position p : positionManager.getOpenPositions()) {
            if (p.getSymbol().equals(symbol)) {
                toClose.add(p);
            }
        }

        for (Position p : toClose) {
            BigDecimal exitPrice =
                    last.multiply(BigDecimal.ONE.subtract(SLIPPAGE));

            p.close(exitPrice);

            BigDecimal proceeds =
                    exitPrice.multiply(p.getQuantity())
                            .subtract(COMMISSION);

            capital = capital.add(proceeds);

            positionManager.getClosedPositions().add(p);
            positionManager.getOpenPositions().remove(p);
        }

        System.out.println("BACKTEST FORCE CLOSE " + symbol);

        return capital;
    }

    private PortfolioBacktestResult buildResult(List<BigDecimal> equity,
                                                BigDecimal capital,
                                                BigDecimal peakEquity) {

        BigDecimal end = capital;

        BigDecimal totalPnL =
                end.subtract(STARTING_CAPITAL);

        List<Position> closed = positionManager.getClosedPositions();
        int totalTrades = closed.size();
        int winningTrades = 0;
        int losingTrades = 0;
        BigDecimal totalWins = BigDecimal.ZERO;
        BigDecimal totalLosses = BigDecimal.ZERO;

        for (Position p : closed) {
            BigDecimal pnl = p.getPnl();
            if (pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                totalWins = totalWins.add(pnl);
            } else if (pnl != null) {
                losingTrades++;
                totalLosses = totalLosses.add(pnl.abs());
            }
        }

        BigDecimal winRate = totalTrades == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(winningTrades)
                        .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP);

        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) == 0
                ? totalWins
                : totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP);

        BigDecimal avgWin = winningTrades == 0 ? BigDecimal.ZERO :
                totalWins.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP);

        BigDecimal avgLoss = losingTrades == 0 ? BigDecimal.ZERO :
                totalLosses.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP);

        BigDecimal expectancy = avgWin.multiply(winRate)
                .subtract(avgLoss.multiply(BigDecimal.ONE.subtract(winRate)));

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = STARTING_CAPITAL;
        for (BigDecimal eq : equity) {
            if (eq.compareTo(peak) > 0) peak = eq;
            BigDecimal dd = peak.subtract(eq)
                    .divide(peak, 6, RoundingMode.HALF_UP);
            if (dd.compareTo(maxDrawdown) > 0) maxDrawdown = dd;
        }

        System.out.println("========== PORTFOLIO BACKTEST RESULT ==========");
        System.out.println("Trades: " + totalTrades);
        System.out.println("Winning: " + winningTrades);
        System.out.println("Losing: " + losingTrades);
        System.out.println("Win Rate: " + winRate);
        System.out.println("Profit Factor: " + profitFactor);
        System.out.println("Avg Win: " + avgWin);
        System.out.println("Avg Loss: " + avgLoss);
        System.out.println("Expectancy: " + expectancy);
        System.out.println("Max Drawdown: " + maxDrawdown);
        System.out.println("Total PnL: " + totalPnL);
        System.out.println("Final Capital: " + end);
        System.out.println("===============================================");

        return new PortfolioBacktestResult(
                STARTING_CAPITAL,
                end,
                totalPnL,
                equity,
                totalTrades,
                winningTrades,
                losingTrades,
                winRate,
                profitFactor,
                expectancy,
                maxDrawdown
        );
    }

    public PortfolioBacktestResult runPortfolio(
            Map<String, List<Candle>> market,
            Strategy strategy) {

        BigDecimal capital = STARTING_CAPITAL;
        BigDecimal peakEquity = STARTING_CAPITAL;

        portfolioRiskService.initialize(capital);

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();
        Map<String, Integer> cooldownMap = new HashMap<>();

        int candles = market.values().iterator().next().size();

        for (int i = 60; i < candles; i++) {

            for (String symbol : market.keySet()) {

                List<Candle> series = market.get(symbol);

                List<Candle> subset = series.subList(0, i + 1);

                BigDecimal price =
                        series.get(i).getClose();

                positionManager.updatePrice(symbol, price);

                capital = checkStopsWithCapitalReturn(
                        symbol, price, capital, cooldownMap, i);

                MarketRegime regime =
                        regimeService.detect(subset);

                TradingSignal signal =
                        strategy.evaluate(subset);

                int cooldownUntil = cooldownMap.getOrDefault(symbol, 0);

                if (signal == TradingSignal.BUY &&
                        positionManager.getOpenPosition(symbol).isEmpty() &&
                        i >= cooldownUntil) {

                    boolean allowed =
                            portfolioRiskService.canOpenNewPosition(
                                    positionManager.getOpenPositions(),
                                    capital,
                                    regime
                            );

                    if (!allowed)
                        continue;

                    if (!portfolioRiskService.isPortfolioRiskAcceptable(
                            positionManager.getOpenPositions(), calculateEquity(capital)))
                        continue;

                    AtrCalculator atrCalc = new AtrCalculator();
                    BigDecimal atr = atrCalc.calculate(subset, 14);

                    if (atr.compareTo(BigDecimal.ZERO) <= 0)
                        continue;

                    BigDecimal stop =
                            price.subtract(atr.multiply(BigDecimal.valueOf(2.5)));

                    BigDecimal take =
                            price.add(atr.multiply(BigDecimal.valueOf(4.0)));

                    BigDecimal riskPerTrade =
                            capital.multiply(BigDecimal.valueOf(0.01));

                    BigDecimal riskPerShare =
                            price.subtract(stop).abs();

                    if (riskPerShare.compareTo(BigDecimal.ZERO) == 0)
                        continue;

                    BigDecimal qty =
                            riskPerTrade.divide(riskPerShare, 0, RoundingMode.DOWN);

                    BigDecimal regimeMult =
                            portfolioRiskService.adjustRiskByRegime(regime);

                    BigDecimal ddMult =
                            portfolioRiskService.adjustRiskByDrawdown(
                                    calculateEquity(capital), peakEquity);

                    BigDecimal finalMult =
                            regimeMult.multiply(ddMult);

                    qty = qty.multiply(finalMult)
                            .setScale(0, RoundingMode.DOWN);

                    if (qty.compareTo(BigDecimal.ZERO) <= 0)
                        continue;

                    if (price.compareTo(BigDecimal.valueOf(20)) < 0)
                        continue;

                    BigDecimal slippageAdjustedPrice =
                            price.multiply(BigDecimal.ONE.add(SLIPPAGE));

                    BigDecimal cost = slippageAdjustedPrice.multiply(qty)
                            .add(COMMISSION);

                    if (cost.compareTo(capital) > 0)
                        continue;

                    capital = capital.subtract(cost);

                    Position position =
                            new Position(symbol, slippageAdjustedPrice, qty, stop, take);

                    positionManager.openPosition(position);

                    System.out.println("PORTFOLIO BUY "
                            + symbol
                            + " regime=" + regime
                            + " qty=" + qty
                            + " price=" + slippageAdjustedPrice
                            + " capital=" + capital);
                }
            }

            BigDecimal equity =
                    calculateEquity(capital);

            if (equity.compareTo(peakEquity) > 0) {
                peakEquity = equity;
            }

            equityCurve.add(equity);
        }

        for (String symbol : market.keySet()) {
            capital = forceClose(symbol, market.get(symbol), capital);
        }

        return buildResult(equityCurve, capital, peakEquity);
    }
}