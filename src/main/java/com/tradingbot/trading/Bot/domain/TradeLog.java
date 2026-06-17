package com.tradingbot.trading.Bot.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLog {
    private Long id;

    private String symbol;
    private String action; // BUY / SELL
    private BigDecimal price;
    private BigDecimal quantity;

    private LocalDateTime timestamp;
}
