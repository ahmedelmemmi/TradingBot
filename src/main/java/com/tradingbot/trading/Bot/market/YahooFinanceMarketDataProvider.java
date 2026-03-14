package com.tradingbot.trading.Bot.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link MarketDataProvider} that fetches real daily OHLCV data from the
 * Yahoo Finance v8 chart API.
 *
 * <p>No API key is required. Data is returned as daily (1d) candles for the
 * requested symbol and date range.</p>
 *
 * <p>URL pattern used:
 * {@code https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 *         ?period1={epochSec}&period2={epochSec}&interval=1d}</p>
 */
@Component
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

    private static final String BASE_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s" +
            "?period1=%d&period2=%d&interval=1d&events=history";

    private final HttpClient  httpClient;
    private final ObjectMapper objectMapper;

    public YahooFinanceMarketDataProvider() {
        this.httpClient   = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    // ── MarketDataProvider ────────────────────────────────────────────────────

    /**
     * Falls back to the date-range variant using "2 years ago → now".
     */
    @Override
    public List<Candle> getCandles(String symbol, int count) {
        LocalDateTime to   = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = to.minusYears(2);
        return getCandles(symbol, count, from, to);
    }

    /**
     * Downloads real daily candles for {@code symbol} between {@code from} and
     * {@code to} from the Yahoo Finance API.
     *
     * @param symbol ticker (e.g. "SPY")
     * @param count  ignored for this provider (all available bars are returned)
     * @param from   start date
     * @param to     end date
     * @return list of candles ordered oldest-first; empty list on any error
     */
    @Override
    public List<Candle> getCandles(String symbol, int count, LocalDateTime from, LocalDateTime to) {
        long period1 = from.toEpochSecond(ZoneOffset.UTC);
        long period2 = to.toEpochSecond(ZoneOffset.UTC);

        String url = String.format(BASE_URL, symbol, period1, period2);
        System.out.println("[YahooFinance] Fetching: " + url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent",
                            "Mozilla/5.0 (compatible; TradingBot/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[YahooFinance] HTTP " + response.statusCode()
                        + " for " + symbol);
                return List.of();
            }

            return parseResponse(symbol, response.body());

        } catch (Exception e) {
            System.err.println("[YahooFinance] Error fetching " + symbol + ": " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public String getProviderName() {
        return "YahooFinanceMarketDataProvider";
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private List<Candle> parseResponse(String symbol, String json) throws Exception {
        List<Candle> candles = new ArrayList<>();

        JsonNode root   = objectMapper.readTree(json);
        JsonNode result = root.path("chart").path("result");

        if (!result.isArray() || result.isEmpty()) {
            System.err.println("[YahooFinance] Empty result for " + symbol);
            return candles;
        }

        JsonNode series    = result.get(0);
        JsonNode timestamps = series.path("timestamp");
        JsonNode quote      = series.path("indicators").path("quote").get(0);
        JsonNode adjClose   = series.path("indicators").path("adjclose");

        JsonNode opens   = quote.path("open");
        JsonNode highs   = quote.path("high");
        JsonNode lows    = quote.path("low");
        JsonNode closes;
        // Prefer adjusted close if available
        if (adjClose.isArray() && !adjClose.isEmpty()) {
            closes = adjClose.get(0).path("adjclose");
        } else {
            closes = quote.path("close");
        }
        JsonNode volumes = quote.path("volume");

        for (int i = 0; i < timestamps.size(); i++) {
            JsonNode openNode   = opens.get(i);
            JsonNode highNode   = highs.get(i);
            JsonNode lowNode    = lows.get(i);
            JsonNode closeNode  = closes.get(i);
            JsonNode volNode    = volumes.get(i);

            // Skip bars with null/missing OHLCV (e.g. market-closed days)
            if (openNode  == null || openNode.isNull()  ||
                highNode  == null || highNode.isNull()  ||
                lowNode   == null || lowNode.isNull()   ||
                closeNode == null || closeNode.isNull()) {
                continue;
            }

            long     epochSec = timestamps.get(i).asLong();
            LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(epochSec), ZoneOffset.UTC);

            BigDecimal open   = BigDecimal.valueOf(openNode.asDouble());
            BigDecimal high   = BigDecimal.valueOf(highNode.asDouble());
            BigDecimal low    = BigDecimal.valueOf(lowNode.asDouble());
            BigDecimal close  = BigDecimal.valueOf(closeNode.asDouble());
            long volume = (volNode == null || volNode.isNull()) ? 0L : volNode.asLong();

            candles.add(new Candle(symbol, time, open, high, low, close, volume));
        }

        System.out.println("[YahooFinance] Parsed " + candles.size()
                + " candles for " + symbol);
        return candles;
    }
}
