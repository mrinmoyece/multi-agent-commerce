package com.agentforge.commerce.platform.service;

import com.agentforge.commerce.platform.persistence.entity.AuditEventEntity;
import com.agentforge.commerce.platform.persistence.repo.AuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records append-only audit events. Runs in the caller's transaction so an audit
 * entry is committed atomically with the state change it describes.
 */
@Service
public class AuditService {

    static final String CORRELATION_ID = "correlationId";

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    public void record(String eventType, UUID runId, String principal, String detail) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setRunId(runId);
        event.setPrincipal(principal);
        event.setCorrelationId(MDC.get(CORRELATION_ID));
        event.setDetail(truncate(detail));
        event.setCreatedAt(Instant.now());
        repository.save(event);
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() > 4000 ? detail.substring(0, 4000) : detail;
    }
}
