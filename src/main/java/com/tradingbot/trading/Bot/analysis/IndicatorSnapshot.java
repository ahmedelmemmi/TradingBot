package com.tradingbot.trading.Bot.analysis;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Snapshot of all calculated technical indicators for one candle.
 */
@Getter
@Builder
public class IndicatorSnapshot {

    // Trend EMAs
    private final BigDecimal ema20;
    private final BigDecimal ema50;
    private final BigDecimal ema100;
    private final BigDecimal ema200;

    // Momentum
    private final BigDecimal rsi14;
    private final BigDecimal macdLine;
    private final BigDecimal macdSignal;
    private final BigDecimal macdHistogram;
    private final BigDecimal stochRsiK;
    private final BigDecimal stochRsiD;

    // Volatility
    private final BigDecimal atr14;
    private final BigDecimal bbUpper;
    private final BigDecimal bbMiddle;
    private final BigDecimal bbLower;

    // Trend strength
    private final BigDecimal adx14;
    private final BigDecimal diPlus;
    private final BigDecimal diMinus;

    // Volume
    private final BigDecimal volumeMa20;
    private final BigDecimal relativeVolume;

    // Price action
    private final String candlePattern;
}
