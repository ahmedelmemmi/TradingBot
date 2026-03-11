package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class MockMarketDataService implements MarketDataService {

    private static final int SCALE = 4;
    private static final BigDecimal PRICE_FLOOR = BigDecimal.valueOf(5);

    private final Random random = new Random();

    public enum MarketScenario {
        RANDOM,
        STRONG_UPTREND,
        STRONG_DOWNTREND,
        CRASH,
        SIDEWAYS_VOLATILE
    }

    public List<Candle> generateCandles(String symbol,
                                        int numberOfCandles,
                                        MarketScenario scenario) {

        List<Candle> candles = new ArrayList<>();

        BigDecimal lastClose = BigDecimal.valueOf(100);

        LocalDateTime time = LocalDateTime.now()
                .minusMinutes(numberOfCandles);

        for (int i = 0; i < numberOfCandles; i++) {

            BigDecimal move = switch (scenario) {

                case STRONG_UPTREND ->
                        BigDecimal.valueOf(0.0015 + random.nextDouble() * 0.003);

                case STRONG_DOWNTREND ->
                        BigDecimal.valueOf(-0.0015 - random.nextDouble() * 0.003);

                case CRASH ->
                        crashMove(i, numberOfCandles);

                case SIDEWAYS_VOLATILE ->
                        BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.04);

                default ->
                        BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.015);
            };

            BigDecimal newClose =
                    lastClose.add(lastClose.multiply(move))
                            .setScale(SCALE, RoundingMode.HALF_UP);

            // 🔥 VERY IMPORTANT — price floor protection
            if (newClose.compareTo(PRICE_FLOOR) < 0) {
                newClose = PRICE_FLOOR.add(
                        BigDecimal.valueOf(random.nextDouble() * 3)
                );
            }

            BigDecimal high = newClose.max(lastClose)
                    .add(BigDecimal.valueOf(random.nextDouble() * 1.5));

            BigDecimal low = newClose.min(lastClose)
                    .subtract(BigDecimal.valueOf(random.nextDouble() * 1.5));

            long volume = generateVolume(scenario);

            Candle candle = new Candle(
                    symbol,
                    time.plusMinutes(i),
                    lastClose,
                    high,
                    low,
                    newClose,
                    volume
            );

            candles.add(candle);

            lastClose = newClose;
        }

        return candles;
    }

    // =========================
    // REALISTIC CRASH PHASE MODEL
    // =========================
    private BigDecimal crashMove(int index, int total) {

        double progress = (double) index / total;

        // Phase 1 — slow distribution
        if (progress < 0.25) {
            return BigDecimal.valueOf(-random.nextDouble() * 0.005);
        }

        // Phase 2 — panic selloff
        if (progress < 0.55) {
            return BigDecimal.valueOf(-0.01 - random.nextDouble() * 0.04);
        }

        // Phase 3 — capitulation volatility
        if (progress < 0.8) {
            double shock = (random.nextDouble() * 2 - 1) * 0.05;
            return BigDecimal.valueOf(shock);
        }

        // Phase 4 — stabilization / dead cat bounce
        double recovery = (random.nextDouble() * 2 - 1) * 0.015;
        return BigDecimal.valueOf(recovery);
    }

    private long generateVolume(MarketScenario scenario) {

        switch (scenario) {

            case CRASH:
                return 300_000 + random.nextInt(900_000);

            case SIDEWAYS_VOLATILE:
                return 200_000 + random.nextInt(600_000);

            case STRONG_UPTREND:
            case STRONG_DOWNTREND:
                return 150_000 + random.nextInt(400_000);

            default:
                return 100_000 + random.nextInt(300_000);
        }
    }

    @Override
    public List<Candle> getHistoricalCandles(String symbol, int numberOfCandles) {
        return generateCandles(symbol, numberOfCandles, MarketScenario.RANDOM);
    }

    @Override
    public Candle getLatestCandle(String symbol) {
        return generateCandles(symbol, 1, MarketScenario.RANDOM).get(0);
    }
}