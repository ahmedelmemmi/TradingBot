package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.SignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SignalRepository extends JpaRepository<SignalEntity, Long> {

    List<SignalEntity> findBySymbolOrderBySignalTimeDesc(String symbol);

    @Query("""
            SELECT s FROM SignalEntity s
            WHERE s.symbol = :symbol AND s.signalTime >= :since
            ORDER BY s.signalTime DESC
            """)
    List<SignalEntity> findRecent(@Param("symbol") String symbol,
                                  @Param("since") Instant since);
}
