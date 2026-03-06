package com.tradingbot.trading.Bot.market;

import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class MarketHoursService {
    public boolean isMarketOpen() {

        ZoneId nyZone = ZoneId.of("America/New_York");

        LocalTime now =
                ZonedDateTime.now(nyZone).toLocalTime();

        LocalTime open = LocalTime.of(9,30);
        LocalTime close = LocalTime.of(16,0);

        return now.isAfter(open) && now.isBefore(close);
    }
}
