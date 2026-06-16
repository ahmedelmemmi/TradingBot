package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.CandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    Optional<CandleEntity> findBySymbolAndTimeFrameAndOpenTime(String symbol, String timeFrame, Instant openTime);

    List<CandleEntity> findBySymbolAndTimeFrameOrderByOpenTimeDesc(String symbol, String timeFrame);

    @Query("""
            SELECT c FROM CandleEntity c
            WHERE c.symbol = :symbol AND c.timeFrame = :timeFrame
            ORDER BY c.openTime DESC
            LIMIT :limit
            """)
    List<CandleEntity> findLatest(@Param("symbol") String symbol,
                                  @Param("timeFrame") String timeFrame,
                                  @Param("limit") int limit);

    @Query("""
            SELECT c FROM CandleEntity c
            WHERE c.symbol = :symbol AND c.timeFrame = :timeFrame
              AND c.openTime >= :from AND c.openTime <= :to
            ORDER BY c.openTime ASC
            """)
    List<CandleEntity> findByRange(@Param("symbol") String symbol,
                                   @Param("timeFrame") String timeFrame,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);
}
