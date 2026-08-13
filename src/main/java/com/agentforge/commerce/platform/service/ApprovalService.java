package com.agentforge.commerce.platform.service;

import com.agentforge.commerce.platform.persistence.domain.ApprovalStatus;
import com.agentforge.commerce.platform.persistence.entity.ApprovalRequestEntity;
import com.agentforge.commerce.platform.persistence.repo.ApprovalRequestRepository;
import com.agentforge.commerce.platform.service.exception.ApprovalNotFoundException;
import com.agentforge.commerce.platform.service.exception.InvalidStateTransitionException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the approval-request lifecycle with an explicit state machine and
 * optimistic locking. Approvals are raised automatically for each recommended
 * action of a successful run and decided by a human reviewer via the API.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final AuditService auditService;

    public ApprovalService(ApprovalRequestRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /** Raise a new PENDING approval request. Invoked within a run's transaction. */
    @Transactional
    public ApprovalRequestEntity raise(UUID runId, String action, String requestedBy) {
        ApprovalRequestEntity approval = new ApprovalRequestEntity();
        approval.setId(UUID.randomUUID());
        approval.setRunId(runId);
        approval.setAction(truncate(action));
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setRequestedBy(requestedBy);
        approval.setCreatedAt(Instant.now());
        ApprovalRequestEntity saved = repository.save(approval);
        auditService.record("APPROVAL_REQUESTED", runId, requestedBy, "action=" + saved.getAction());
        return saved;
    }

    @Transactional(readOnly = true)
    public ApprovalRequestEntity find(UUID approvalId) {
        return repository.findById(approvalId).orElseThrow(() -> new ApprovalNotFoundException(approvalId));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestEntity> listForRun(UUID runId) {
        return repository.findByRunIdOrderByCreatedAt(runId);
    }

    /**
     * Record a human decision. Enforces the {@code PENDING → APPROVED|REJECTED}
     * transition; a second decision on an already-decided request is rejected as a
     * conflict. Optimistic locking guards concurrent reviewers.
     */
    @Transactional
    public ApprovalRequestEntity decide(UUID approvalId, ApprovalStatus decision,
                                        String decidedBy, String reason) {
        if (decision != ApprovalStatus.APPROVED && decision != ApprovalStatus.REJECTED) {
            throw new InvalidStateTransitionException("Decision must be APPROVED or REJECTED");
        }
        ApprovalRequestEntity approval = find(approvalId);
        if (!approval.getStatus().canTransitionTo(decision)) {
            throw new InvalidStateTransitionException(
                    "Approval " + approvalId + " is already " + approval.getStatus());
        }
        approval.setStatus(decision);
        approval.setDecidedBy(decidedBy);
        approval.setDecisionReason(truncate(reason));
        approval.setDecidedAt(Instant.now());
        ApprovalRequestEntity saved = repository.save(approval);
        auditService.record("APPROVAL_" + decision, approval.getRunId(), decidedBy,
                "approvalId=" + approvalId);
        return saved;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
