package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.PredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<PredictionEntity, Long> {
}
