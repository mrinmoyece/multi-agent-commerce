package com.agentforge.commerce.runtime.contract;

/**
 * Stable identity of the origin of a piece of evidence. Carried end-to-end so
 * that every recommendation can be traced back to a named source and its
 * provenance class.
 *
 * @param id           stable machine identifier (e.g. {@code inventory-wms})
 * @param displayName  human-readable name for operators and reports
 * @param kind         provenance class governing whether the source is authoritative
 * @param systemOfRecord description of the backing system (never a credential/URL secret)
 */
public record SourceIdentity(String id, String displayName, SourceKind kind, String systemOfRecord) {

    public SourceIdentity {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("source id must be provided");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("source displayName must be provided");
        }
        if (kind == null) {
            throw new IllegalArgumentException("source kind must be provided");
        }
        systemOfRecord = systemOfRecord == null ? "" : systemOfRecord;
    }

    /** @return {@code true} when this source may back a decision-grade claim. */
    public boolean isAuthoritative() {
        return kind.isAuthoritative();
    }

    /** Convenience factory for a deterministic committed fixture source. */
    public static SourceIdentity fixture(String id, String displayName) {
        return new SourceIdentity(id, displayName, SourceKind.FIXTURE, "committed deterministic fixture");
    }

    /** Convenience factory for a model-extrapolated simulated source. */
    public static SourceIdentity simulated(String id, String displayName) {
        return new SourceIdentity(id, displayName, SourceKind.SIMULATED, "model-extrapolated (no system of record)");
    }
}
