package com.tradingbot.trading.Bot.position;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PositionManager {
    private final List<Position> openPositions = new ArrayList<>();
    private final List<Position> closedPositions = new ArrayList<>();

    public void openPosition(Position position) {
        openPositions.add(position);
    }

    public Optional<Position> getOpenPosition(String symbol) {
        return openPositions.stream()
                .filter(p -> p.getSymbol().equals(symbol) && p.isOpen())
                .findFirst();
    }

    public void updatePrice(String symbol, BigDecimal currentPrice) {

        Optional<Position> optionalPosition = getOpenPosition(symbol);

        if (optionalPosition.isEmpty()) return;

        Position position = optionalPosition.get();

        // Stop Loss hit
        if (currentPrice.compareTo(position.getStopLoss()) <= 0) {
            position.close(position.getStopLoss());
            close(position);
            return;
        }

        // Take Profit hit
        if (currentPrice.compareTo(position.getTakeProfit()) >= 0) {
            position.close(position.getTakeProfit());
            close(position);
        }
    }

    private void close(Position position) {
        openPositions.remove(position);
        closedPositions.add(position);
    }

    public List<Position> getOpenPositions() {
        return openPositions;
    }

    public List<Position> getClosedPositions() {
        return closedPositions;
    }
}
