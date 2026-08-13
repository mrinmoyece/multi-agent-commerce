package com.agentforge.commerce.platform.persistence.repo;

import com.agentforge.commerce.platform.persistence.entity.AuditEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Append-only data access for audit events. */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    long countByRunId(UUID runId);
}
