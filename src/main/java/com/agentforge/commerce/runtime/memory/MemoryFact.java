package com.agentforge.commerce.runtime.memory;

import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.SourceKind;
import java.time.Instant;

/**
 * An immutable, retailer-scoped fact eligible for durable semantic memory.
 *
 * <p>A fact is a small, structured, derived assertion — never raw free-form model
 * output. It always names its provenance ({@link #sourceId()} + {@link #sourceKind()}
 * and the run that produced it), carries a calibrated {@link #confidence()}, a
 * stable {@link #valueHash()} for deduplication and drift detection, and an
 * explicit {@link #expiresAt()} so stale knowledge self-invalidates.</p>
 *
 * @param retailerId  owning tenant (isolation boundary)
 * @param key         normalised fact key (e.g. {@code inventory.critical_sku_count})
 * @param category    coarse grouping for retrieval (e.g. {@code inventory})
 * @param value       bounded, structured value text (never free-form prose)
 * @param sourceId    identifier of the evidence source that grounds the fact
 * @param sourceKind  provenance kind of that source
 * @param confidence  calibrated confidence in the fact
 * @param valueHash   stable hash of the value for dedup/drift detection
 * @param provenanceRunId run that produced the fact
 * @param observedAt  when the underlying evidence was observed
 * @param expiresAt   when the fact must be treated as invalid
 */
public record MemoryFact(
        String retailerId,
        String key,
        String category,
        String value,
        String sourceId,
        SourceKind sourceKind,
        Confidence confidence,
        String valueHash,
        String provenanceRunId,
        Instant observedAt,
        Instant expiresAt) {

    /** Maximum length of a stored fact value; longer values are rejected, never truncated silently. */
    public static final int MAX_VALUE_CHARS = 512;

    public MemoryFact {
        if (retailerId == null || retailerId.isBlank()) {
            throw new IllegalArgumentException("retailerId must be provided");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("fact key must be provided");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("fact value must be provided");
        }
        if (value.length() > MAX_VALUE_CHARS) {
            throw new IllegalArgumentException("fact value exceeds " + MAX_VALUE_CHARS + " chars");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("fact sourceId must be provided");
        }
        if (sourceKind == null) {
            throw new IllegalArgumentException("fact sourceKind must be provided");
        }
        if (observedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("fact observedAt and expiresAt must be provided");
        }
        category = category == null || category.isBlank() ? "general" : category;
        confidence = confidence == null ? Confidence.none("no confidence supplied") : confidence;
        valueHash = valueHash == null ? "" : valueHash;
    }

    /** @return {@code true} when the fact has passed its validity window at {@code now}. */
    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }
}
