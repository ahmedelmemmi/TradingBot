package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.TradeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface TradeResultRepository extends JpaRepository<TradeResultEntity, Long> {

    Optional<TradeResultEntity> findByTradeId(Long tradeId);

    @Query("""
            SELECT COALESCE(SUM(r.profitLoss), 0)
            FROM TradeResultEntity r
            JOIN r.trade t
            WHERE t.entryTime >= :since
            """)
    BigDecimal sumProfitLossSince(@Param("since") Instant since);

    @Query("""
            SELECT COUNT(r) FROM TradeResultEntity r
            WHERE r.outcome = 'WIN' AND r.trade.entryTime >= :since
            """)
    long countWinsSince(@Param("since") Instant since);

    @Query("""
            SELECT COUNT(r) FROM TradeResultEntity r
            WHERE r.outcome = 'LOSS' AND r.trade.entryTime >= :since
            """)
    long countLossesSince(@Param("since") Instant since);
}
