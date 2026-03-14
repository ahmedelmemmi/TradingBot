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
import java.util.List;
import java.util.Map;

@Service
public class PortfolioBacktestEngine {

    private final PositionManager positionManager;
    private final PortfolioRiskService portfolioRiskService;
    private final MarketRegimeService regimeService;

    private static final BigDecimal STARTING_CAPITAL =
            BigDecimal.valueOf(10_000);

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

        portfolioRiskService.initialize(capital);

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();

        for (int i = 60; i < candles.size(); i++) {

            List<Candle> subset = candles.subList(0, i + 1);

            MarketRegime regime = regimeService.detect(subset);

            TradingSignal signal = strategy.evaluate(subset);

            BigDecimal price = candles.get(i).getClose();

            // ⭐ update floating pnl
            positionManager.updatePrice(symbol, price);

            // ⭐ check SL / TP hit
            checkStops(symbol, price);

            // ⭐ BUY logic
            if (signal == TradingSignal.BUY &&
                    positionManager.getOpenPosition(symbol).isEmpty()) {

                boolean allowed =
                        portfolioRiskService.canOpenNewPosition(
                                positionManager.getOpenPositions(),
                                capital,
                                regime
                        );

                if (!allowed)
                    continue;

                AtrCalculator atrCalc = new AtrCalculator();
                BigDecimal atr = atrCalc.calculate(subset,14);

                BigDecimal stop =
                        price.subtract(atr.multiply(BigDecimal.valueOf(2.5)));

                BigDecimal take =
                        price.multiply(BigDecimal.valueOf(1.05));

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
                        portfolioRiskService.adjustRiskByDrawdown(capital);

                BigDecimal finalMult =
                        regimeMult.multiply(ddMult);

                qty = qty.multiply(finalMult)
                        .setScale(0, RoundingMode.DOWN);

                if (qty.compareTo(BigDecimal.ZERO) <= 0)
                    continue;

                BigDecimal cost = price.multiply(qty);

                // ⭐ REAL CAPITAL DEDUCTION
                if (cost.compareTo(capital) > 0)
                    continue;

                capital = capital.subtract(cost);

                Position position =
                        new Position(symbol, price, qty, stop, take);

                positionManager.openPosition(position);

                System.out.println("BACKTEST BUY "
                        + symbol
                        + " regime=" + regime
                        + " qty=" + qty
                        + " price=" + price
                        + " capital=" + capital);
            }

            BigDecimal equity =
                    calculateEquity(capital);

            equityCurve.add(equity);
        }

        capital = forceClose(symbol, candles, capital);

        return buildResult(equityCurve, capital);
    }

    private void checkStops(String symbol, BigDecimal price) {

        positionManager.getOpenPosition(symbol)
                .ifPresent(p -> {

                    if (price.compareTo(p.getStopLoss()) <= 0 ||
                            price.compareTo(p.getTakeProfit()) >= 0) {

                        p.close(price);
                        positionManager.getClosedPositions().add(p);
                        positionManager.getOpenPositions().remove(p);
                    }
                });
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

        for (Position p : positionManager.getOpenPositions()) {

            p.close(last);

            BigDecimal proceeds =
                    last.multiply(p.getQuantity());

            capital = capital.add(proceeds);

            positionManager.getClosedPositions().add(p);
        }

        positionManager.getOpenPositions().clear();

        System.out.println("BACKTEST FORCE CLOSE " + symbol);

        return capital;
    }

    private PortfolioBacktestResult buildResult(List<BigDecimal> equity,
                                                BigDecimal capital) {

        BigDecimal end = capital;

        BigDecimal totalPnL =
                end.subtract(STARTING_CAPITAL);

        return new PortfolioBacktestResult(
                STARTING_CAPITAL,
                end,
                totalPnL,
                equity
        );
    }

    public PortfolioBacktestResult runPortfolio(
            Map<String, List<Candle>> market,
            Strategy strategy) {

        BigDecimal capital = STARTING_CAPITAL;

        portfolioRiskService.initialize(capital);

        positionManager.getOpenPositions().clear();
        positionManager.getClosedPositions().clear();

        List<BigDecimal> equityCurve = new ArrayList<>();

        int candles = market.values().iterator().next().size();

        for (int i = 60; i < candles; i++) {

            for (String symbol : market.keySet()) {

                List<Candle> series = market.get(symbol);

                List<Candle> subset = series.subList(0, i + 1);

                BigDecimal price =
                        series.get(i).getClose();

                positionManager.updatePrice(symbol, price);

                checkStops(symbol, price);

                MarketRegime regime =
                        regimeService.detect(subset);

                TradingSignal signal =
                        strategy.evaluate(subset);

                if (signal == TradingSignal.BUY &&
                        positionManager.getOpenPosition(symbol).isEmpty()) {

                    boolean allowed =
                            portfolioRiskService.canOpenNewPosition(
                                    positionManager.getOpenPositions(),
                                    capital,
                                    regime
                            );

                    if (!allowed)
                        continue;

                    AtrCalculator atrCalc = new AtrCalculator();
                    BigDecimal atr = atrCalc.calculate(subset,14);

                    BigDecimal stop =
                            price.subtract(atr.multiply(BigDecimal.valueOf(2.5)));

                    BigDecimal take =
                            price.multiply(BigDecimal.valueOf(1.05));

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
                            portfolioRiskService.adjustRiskByDrawdown(capital);

                    BigDecimal finalMult =
                            regimeMult.multiply(ddMult);

                    qty = qty.multiply(finalMult)
                            .setScale(0, RoundingMode.DOWN);

                    BigDecimal cost = price.multiply(qty);

                    if (cost.compareTo(capital) > 0 ||
                            qty.compareTo(BigDecimal.ZERO) <= 0)
                        continue;

                    capital = capital.subtract(cost);

                    Position position =
                            new Position(symbol, price, qty, stop, take);

                    if (price.compareTo(BigDecimal.valueOf(20)) < 0) {
                        continue;
                    }
                    positionManager.openPosition(position);

                    System.out.println("PORTFOLIO BUY "
                            + symbol
                            + " regime=" + regime
                            + " qty=" + qty
                            + " price=" + price
                            + " capital=" + capital);
                }
            }

            BigDecimal equity =
                    calculateEquity(capital);

            equityCurve.add(equity);
        }

        for (String symbol : market.keySet()) {

            List<Candle> series = market.get(symbol);

            BigDecimal last =
                    series.get(series.size() - 1).getClose();

            capital = forceClose(symbol, series, capital);
        }

        return buildResult(equityCurve, capital);
    }
}