package com.tradingbot.trading.Bot.audit;

import com.tradingbot.trading.Bot.persistence.entity.AuditLogEntity;
import com.tradingbot.trading.Bot.persistence.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Writes structured audit events to the {@code audit_logs} table.
 * All writes are asynchronous to avoid blocking the trading path.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void log(String eventType,
                    String entityType,
                    Long entityId,
                    String description,
                    Map<String, Object> payload) {
        try {
            auditLogRepository.save(AuditLogEntity.builder()
                    .eventType(eventType)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .payload(payload)
                    .build());
        } catch (Exception ex) {
            log.error("[Audit] Failed to write audit log: {}", ex.getMessage(), ex);
        }
    }
}
