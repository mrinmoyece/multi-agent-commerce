package com.agentforge.commerce.platform.persistence.entity;

import com.agentforge.commerce.platform.persistence.domain.AgentResultStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable record of one specialist agent's contribution to a run. Stored flat
 * (referenced by {@code runId}) to keep the aggregate simple and avoid
 * accidental lazy-loading across transaction boundaries.
 */
@Entity
@Table(name = "agent_result")
@Getter
@Setter
@NoArgsConstructor
public class AgentResultEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "agent_name", nullable = false, length = 64)
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentResultStatus status;

    @Column(name = "priority", nullable = false, length = 16)
    private String priority;

    @Column(name = "content", length = 8000)
    private String content;

    @Column(name = "prompt_id", length = 128)
    private String promptId;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "prompt_tokens")
    private long promptTokens;

    @Column(name = "completion_tokens")
    private long completionTokens;

    @Column(name = "cost_micros")
    private long costMicros;

    @Column(name = "confidence")
    private double confidence;

    @Column(name = "schema_valid", nullable = false)
    private boolean schemaValid;

    @Column(name = "cited_evidence_count")
    private long citedEvidenceCount;

    @Column(name = "degraded_reasons", length = 1000)
    private String degradedReasons;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
