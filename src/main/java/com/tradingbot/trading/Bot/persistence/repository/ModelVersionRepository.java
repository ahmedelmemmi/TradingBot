package com.tradingbot.trading.Bot.persistence.repository;

import com.tradingbot.trading.Bot.persistence.entity.ModelVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, Long> {

    Optional<ModelVersionEntity> findByActive(boolean active);

    Optional<ModelVersionEntity> findByVersionTag(String versionTag);
}
