package com.tradingbot.trading.Bot.position;

import com.tradingbot.trading.Bot.persistence.TradeService;
import lombok.Getter;
import org.springframework.stereotype.Service;
import com.tradingbot.trading.Bot.domain.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Service
public class PositionManager {
    private final List<Position> openPositions = new ArrayList<>();
    private final List<Position> closedPositions = new ArrayList<>();
    private final TradeService tradeService;

    public PositionManager(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    public void openPosition(Position position) {

        openPositions.add(position);
        tradeService.recordEntry(
                position.getSymbol(),
                position.getEntryPrice(),
                position.getQuantity()
        );

        System.out.println("Position registered: " + position.getSymbol());
    }

    public Optional<Position> getOpenPosition(String symbol) {

        return openPositions.stream()
                .filter(p -> p.getSymbol().equals(symbol) && p.isOpen())
                .findFirst();
    }

    /**
     * Used by controllers / backtests / monitoring
     * to update trailing stop logic.
     */
    public void updatePrice(String symbol, BigDecimal currentPrice) {

        Optional<Position> optionalPosition = getOpenPosition(symbol);

        if (optionalPosition.isEmpty()) {
            return;
        }

        Position position = optionalPosition.get();

        position.updateTrailingStop(currentPrice);
    }

    public void updateTrailingStop(String symbol, BigDecimal currentPrice) {

        Optional<Position> optionalPosition = getOpenPosition(symbol);

        if (optionalPosition.isEmpty()) {
            return;
        }

        Position position = optionalPosition.get();

        position.updateTrailingStop(currentPrice);
    }

    public void closePosition(String symbol, BigDecimal exitPrice) {

        Optional<Position> optionalPosition = getOpenPosition(symbol);

        if (optionalPosition.isEmpty()) {

            System.out.println("No open position found to close for: " + symbol);

            return;
        }

        Position position = optionalPosition.get();

        position.close(exitPrice);

        openPositions.remove(position);

        closedPositions.add(position);
        tradeService.recordExit(
                symbol,
                exitPrice,
                position.getPnl()
        );

        System.out.println("Position closed and moved to history: " + symbol);
    }

    /**
     * Used by backtesting engine
     */
    public List<Position> getOpenPositions() {
        return openPositions;
    }

    /**
     * Used by backtesting engine
     */
    public List<Position> getClosedPositions() {
        return closedPositions;
    }

}
