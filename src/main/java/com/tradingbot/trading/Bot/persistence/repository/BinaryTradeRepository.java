package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.BinaryTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BinaryTradeRepository extends JpaRepository<BinaryTradeEntity, Long> {

    List<BinaryTradeEntity> findBySymbolAndStatus(String symbol, String status);

    @Query("""
            SELECT t FROM BinaryTradeEntity t
            WHERE t.status = 'OPEN' AND t.entryTime <= :expiryBefore
            """)
    List<BinaryTradeEntity> findExpiredTrades(@Param("expiryBefore") Instant expiryBefore);

    @Query("""
            SELECT COUNT(t) FROM BinaryTradeEntity t
            WHERE t.entryTime >= :since AND t.status <> 'OPEN'
            """)
    long countSettledSince(@Param("since") Instant since);

    @Query("""
            SELECT t FROM BinaryTradeEntity t
            WHERE t.symbol = :symbol
              AND t.entryTime >= :from
            ORDER BY t.entryTime DESC
            """)
    List<BinaryTradeEntity> findBySymbolSince(@Param("symbol") String symbol,
                                               @Param("from") Instant from);
}
