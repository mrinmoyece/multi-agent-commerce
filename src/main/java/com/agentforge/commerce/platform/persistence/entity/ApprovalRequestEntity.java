package com.agentforge.commerce.platform.persistence.entity;

import com.agentforge.commerce.platform.persistence.domain.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Human approval request raised against a run's recommended action. Optimistic
 * locking prevents two reviewers from racing a decision.
 */
@Entity
@Table(name = "approval_request")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequestEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "action", nullable = false, length = 2000)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalStatus status;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decision_reason", length = 2000)
    private String decisionReason;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
