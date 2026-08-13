package com.agentforge.commerce.platform.persistence.entity;

import com.agentforge.commerce.platform.persistence.domain.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable record of a single daily analysis run.
 *
 * <p>Optimistic locking via {@link Version} guarantees that competing replicas
 * cannot both drive the same run through its state machine — the loser of a
 * concurrent update receives an {@code OptimisticLockingFailureException} and
 * backs off. Raw provider payloads and credentials are never persisted; only
 * synthesised, non-sensitive briefing fields are stored.</p>
 */
@Entity
@Table(name = "analysis_run")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisRunEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "retailer_id", nullable = false)
    private String retailerId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "trigger_source", nullable = false, length = 32)
    private String triggerSource;

    @Column(name = "executive_summary", length = 8000)
    private String executiveSummary;

    @Column(name = "cross_insights", length = 8000)
    private String crossInsights;

    @Column(name = "recommended_actions", length = 8000)
    private String recommendedActions;

    @Column(name = "urgent_alerts", length = 8000)
    private String urgentAlerts;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

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

    @Column(name = "tool_calls")
    private int toolCalls;

    @Column(name = "agent_rounds")
    private int agentRounds;

    @Column(name = "min_confidence")
    private double minConfidence;

    @Column(name = "degraded", nullable = false)
    private boolean degraded;

    @Column(name = "risk_reasons", length = 2000)
    private String riskReasons;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
