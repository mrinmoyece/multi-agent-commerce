package com.agentforge.commerce.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only audit trail entry. Written inside the same transaction as the
 * state change it records so the trail can never diverge from the domain state.
 * Details are deliberately non-sensitive (no credentials, no raw model output).
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEventEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "principal", length = 200)
    private String principal;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "detail", length = 4000)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
