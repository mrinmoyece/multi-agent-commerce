package com.agentforge.commerce.platform.persistence.repo;

import com.agentforge.commerce.platform.persistence.entity.ApprovalRequestEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for human approval requests. */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, UUID> {

    List<ApprovalRequestEntity> findByRunIdOrderByCreatedAt(UUID runId);
}
