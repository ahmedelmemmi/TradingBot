package com.tradingbot.trading.Bot.persistence;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TradeService {
    private final TradeRepository tradeRepository;

    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public void recordEntry(String symbol,
                            BigDecimal entryPrice,
                            BigDecimal quantity) {

        TradeEntity trade = new TradeEntity(
                symbol,
                entryPrice,
                quantity,
                LocalDateTime.now()
        );

        tradeRepository.save(trade);

        System.out.println("Trade entry saved for " + symbol);
    }

    public void recordExit(String symbol,
                           BigDecimal exitPrice,
                           BigDecimal pnl) {

        Optional<TradeEntity> optional =
                tradeRepository.findFirstBySymbolAndOpenTrue(symbol);

        if (optional.isEmpty()) {
            return;
        }

        TradeEntity trade = optional.get();

        trade.close(exitPrice, pnl);

        tradeRepository.save(trade);

        System.out.println("Trade exit saved for " + symbol);
    }
}
