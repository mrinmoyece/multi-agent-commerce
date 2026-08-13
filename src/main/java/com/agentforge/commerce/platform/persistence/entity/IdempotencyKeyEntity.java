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
 * Persisted idempotency key. A unique constraint on the natural key guarantees
 * at-most-once creation of a run for a given client-supplied key, even under
 * concurrent requests hitting different replicas.
 */
@Entity
@Table(name = "idempotency_key")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
    private String key;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
