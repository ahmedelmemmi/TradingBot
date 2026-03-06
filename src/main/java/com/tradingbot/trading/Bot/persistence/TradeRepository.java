package com.tradingbot.trading.Bot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    Optional<TradeEntity> findFirstBySymbolAndOpenTrue(String symbol);

    List<TradeEntity> findByOpenTrue();
}
