package com.tradingbot.trading.Bot.broker;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class BrokerStateService {
    private BigDecimal cashBalance = BigDecimal.ZERO;
    private BigDecimal netLiquidation = BigDecimal.ZERO;

    private final Map<String, BigDecimal> positions = new ConcurrentHashMap<>();

    public void updateCash(String value) {
        this.cashBalance = new BigDecimal(value);
    }

    public void updateNetLiquidation(String value) {
        this.netLiquidation = new BigDecimal(value);
    }

    public void updatePosition(String symbol, BigDecimal quantity) {

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            positions.remove(symbol);
        } else {
            positions.put(symbol, quantity);
        }
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public BigDecimal getNetLiquidation() {
        return netLiquidation;
    }

    public Map<String, BigDecimal> getPositions() {
        return positions;
    }

    public boolean hasOpenPosition(String symbol) {
        return positions.containsKey(symbol);
    }
}
