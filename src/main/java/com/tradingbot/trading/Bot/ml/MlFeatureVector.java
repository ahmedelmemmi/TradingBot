package com.tradingbot.trading.Bot.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Feature vector sent to the ML service for prediction.
 * All fields are derived from the latest {@link com.tradingbot.trading.Bot.analysis.IndicatorSnapshot}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlFeatureVector {

    private BigDecimal rsi14;
    private BigDecimal macdLine;
    private BigDecimal macdHistogram;
    private BigDecimal atr14;
    private BigDecimal adx14;

    /** Price distance from EMA20 as a ratio: (price − EMA20) / EMA20 */
    @JsonProperty("ema20_dist")
    private BigDecimal ema20Distance;

    /** Price distance from EMA50 as a ratio */
    @JsonProperty("ema50_dist")
    private BigDecimal ema50Distance;

    /** Price distance from EMA200 as a ratio */
    @JsonProperty("ema200_dist")
    private BigDecimal ema200Distance;

    /** Bollinger Band %B: (price − lower) / (upper − lower) */
    @JsonProperty("bb_pct_b")
    private BigDecimal bbPctB;

    private BigDecimal relativeVolume;

    @JsonProperty("hour_of_day")
    private int hourOfDay;

    @JsonProperty("day_of_week")
    private int dayOfWeek;

    /** Last candle: close − open as a ratio */
    @JsonProperty("prev_candle_body_ratio")
    private BigDecimal prevCandleBodyRatio;

    /** Last candle upper-wick ratio */
    @JsonProperty("prev_candle_upper_wick")
    private BigDecimal prevCandleUpperWick;

    /** Last candle lower-wick ratio */
    @JsonProperty("prev_candle_lower_wick")
    private BigDecimal prevCandleLowerWick;

    @JsonProperty("stoch_rsi_k")
    private BigDecimal stochRsiK;

    @JsonProperty("stoch_rsi_d")
    private BigDecimal stochRsiD;

    @JsonProperty("ema50_above_ema200")
    private int ema50AboveEma200;

    @JsonProperty("macd_bullish")
    private int macdBullish;

    @JsonProperty("adx_strong")
    private int adxStrong;
}
