package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.BacktestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BacktestResultRepository extends JpaRepository<BacktestResultEntity, Long> {

    List<BacktestResultEntity> findByStrategyNameOrderByCreatedAtDesc(String strategyName);
}
