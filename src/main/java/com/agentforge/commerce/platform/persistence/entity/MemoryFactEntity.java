package com.agentforge.commerce.platform.persistence.entity;

import com.agentforge.commerce.runtime.contract.SourceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable, retailer-scoped semantic-memory fact.
 *
 * <p>Only validated, evidence-backed facts are ever written here (see
 * {@code SemanticMemoryStore}). Tenant isolation is enforced by always querying on
 * {@code retailer_id}; deduplication is enforced by a unique constraint over
 * {@code (retailer_id, fact_key, value_hash)}; and every row carries its provenance
 * ({@code source_id}/{@code source_kind}/{@code provenance_run_id}) and an
 * {@code expires_at} so stale knowledge self-invalidates. Raw free-form model text
 * is never stored.</p>
 */
@Entity
@Table(
        name = "memory_fact",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_memory_fact_dedup",
                columnNames = {"retailer_id", "fact_key", "value_hash"}),
        indexes = {
            @Index(name = "ix_memory_fact_retailer_key", columnList = "retailer_id, fact_key"),
            @Index(name = "ix_memory_fact_expires", columnList = "expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class MemoryFactEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "retailer_id", nullable = false, length = 128)
    private String retailerId;

    @Column(name = "fact_key", nullable = false, length = 128)
    private String factKey;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "value", nullable = false, length = 512)
    private String value;

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 16)
    private SourceKind sourceKind;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "value_hash", nullable = false, length = 64)
    private String valueHash;

    @Column(name = "provenance_run_id", length = 64)
    private String provenanceRunId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
