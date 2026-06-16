package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.IndicatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IndicatorRepository extends JpaRepository<IndicatorEntity, Long> {

    @Query("""
            SELECT i FROM IndicatorEntity i
            WHERE i.symbol = :symbol AND i.timeFrame = :timeFrame
            ORDER BY i.calcTime DESC
            LIMIT 1
            """)
    Optional<IndicatorEntity> findLatest(@Param("symbol") String symbol,
                                         @Param("timeFrame") String timeFrame);
}
