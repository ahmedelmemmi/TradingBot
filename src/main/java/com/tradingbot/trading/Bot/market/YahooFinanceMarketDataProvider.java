package com.tradingbot.trading.Bot.market;

import com.tradingbot.trading.Bot.domain.Candle;
import org.springframework.stereotype.Component;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * {@link MarketDataProvider} that fetches real historical daily candles from
 * Yahoo Finance.  This is Phase-2 of the data strategy: use actual market data
 * to validate that the strategy has a real edge before going live.
 *
 * <p>Usage notes:
 * <ul>
 *   <li>Yahoo Finance provides <b>daily</b> (EOD) data only via this API.</li>
 *   <li>Data is limited to roughly 10–15 years of history per symbol.</li>
 *   <li>No API key is required; rate-limits may apply for frequent calls.</li>
 *   <li>If Yahoo Finance is unavailable (network error) the provider returns an
 *       empty list and logs the failure so callers can handle it gracefully.</li>
 * </ul>
 * </p>
 */
@Component
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger log =
            Logger.getLogger(YahooFinanceMarketDataProvider.class.getName());

    @Override
    public String getProviderName() {
        return "YAHOO_FINANCE";
    }

    /**
     * Downloads daily historical candles for {@code symbol} from Yahoo Finance.
     *
     * @param symbol ticker symbol, e.g. {@code "SPY"} or {@code "AAPL"}
     * @param count  maximum number of most-recent candles to return; use 0 for all
     * @param from   start date (inclusive); defaults to 2 years ago when null
     * @param to     end date (inclusive); defaults to today when null
     * @return list of candles ordered oldest-first, or empty list on error
     */
    @Override
    public List<Candle> getCandles(String symbol, int count,
                                   LocalDateTime from, LocalDateTime to) {

        try {
            Calendar calFrom = toCalendar(
                    from != null ? from : LocalDateTime.now().minusYears(2));
            Calendar calTo = toCalendar(
                    to   != null ? to   : LocalDateTime.now());

            Stock stock = YahooFinance.get(symbol, calFrom, calTo, Interval.DAILY);

            if (stock == null) {
                log.warning("Yahoo Finance returned null for symbol: " + symbol);
                return Collections.emptyList();
            }

            List<HistoricalQuote> history = stock.getHistory();

            if (history == null || history.isEmpty()) {
                log.warning("No historical data returned for symbol: " + symbol);
                return Collections.emptyList();
            }

            List<Candle> candles = new ArrayList<>(history.size());

            for (HistoricalQuote quote : history) {

                if (quote.getOpen()  == null || quote.getHigh()  == null
                 || quote.getLow()   == null || quote.getClose() == null) {
                    continue;
                }

                LocalDateTime time = toLocalDateTime(quote.getDate());
                long volume = quote.getVolume() != null ? quote.getVolume() : 0L;

                candles.add(new Candle(
                        symbol,
                        time,
                        quote.getOpen(),
                        quote.getHigh(),
                        quote.getLow(),
                        // Use adjusted close (accounts for splits/dividends) for accurate
                        // backtesting. Falls back to raw close if adjusted is unavailable.
                        quote.getAdjClose() != null ? quote.getAdjClose() : quote.getClose(),
                        volume
                ));
            }

            // Yahoo Finance returns data newest-first; reverse to oldest-first
            Collections.reverse(candles);

            // Honour the count limit: keep the most recent 'count' candles
            if (count > 0 && candles.size() > count) {
                candles = candles.subList(candles.size() - count, candles.size());
            }

            log.info("Yahoo Finance: loaded " + candles.size()
                    + " daily candles for " + symbol);

            return candles;

        } catch (Exception e) {
            log.severe("Yahoo Finance data fetch failed for " + symbol
                    + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Calendar toCalendar(LocalDateTime ldt) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(
                ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return cal;
    }

    private static LocalDateTime toLocalDateTime(Calendar cal) {
        if (cal == null) return LocalDateTime.now();
        return LocalDateTime.ofInstant(cal.toInstant(), ZoneId.systemDefault());
    }
}
