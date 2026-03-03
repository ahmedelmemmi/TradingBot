package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

@Service
public class LiveCandleBuffer {
    private final Deque<Candle> candles = new LinkedList<>();

    private static final int MAX_SIZE = 100;

    public synchronized void add(Candle candle) {

        if (candles.size() >= MAX_SIZE) {
            candles.removeFirst();
        }

        candles.addLast(candle);
    }

    public synchronized List<Candle> getCandles() {
        return new ArrayList<>(candles);
    }

    public synchronized void clear() {
        candles.clear();
    }

    public synchronized boolean isReady() {
        return candles.size() >= 15; // RSI 14 requires 15 candles minimum
    }

    public synchronized int size() {
        return candles.size();
    }
}
