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
public class MockMarketDataService  implements MarketDataService{
    private static final int SCALE = 4;
    private static final BigDecimal VOLATILITY = BigDecimal.valueOf(0.02); // 2% max move per candle
    private final Random random = new Random();

    public List<Candle> generateCandles(String symbol, int numberOfCandles) {

        List<Candle> candles = new ArrayList<>();

        BigDecimal lastClose = BigDecimal.valueOf(10_000); // starting price

        LocalDateTime time = LocalDateTime.now().minusDays(numberOfCandles);

        for (int i = 0; i < numberOfCandles; i++) {

            BigDecimal percentageMove = randomPercentageMove();

            BigDecimal newClose = lastClose.add(
                    lastClose.multiply(percentageMove)
            ).setScale(SCALE, RoundingMode.HALF_UP);

            BigDecimal high = newClose.max(lastClose)
                    .add(randomSmallNoise());

            BigDecimal low = newClose.min(lastClose)
                    .subtract(randomSmallNoise());

            Candle candle = new Candle(
                    symbol,
                    time.plusDays(i),
                    lastClose,
                    high,
                    low,
                    newClose,
                    random.nextInt(1_000_000)
            );

            candles.add(candle);

            lastClose = newClose;
        }

        return candles;
    }

    private BigDecimal randomPercentageMove() {
        double move = (random.nextDouble() * 2 - 1) * VOLATILITY.doubleValue();
        return BigDecimal.valueOf(move);
    }

    private BigDecimal randomSmallNoise() {
        return BigDecimal.valueOf(random.nextDouble() * 0.5)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public List<Candle> getHistoricalCandles(String symbol, int numberOfCandles) {
        return generateCandles(symbol, numberOfCandles);
    }

    @Override
    public Candle getLatestCandle(String symbol) {
        return generateCandles(symbol, 1).get(0);
    }
}
