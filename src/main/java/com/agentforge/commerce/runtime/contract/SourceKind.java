package com.agentforge.commerce.runtime.contract;

/**
 * Provenance class of a data source. The runtime refuses to present
 * {@link #FIXTURE} or {@link #SIMULATED} data as if it were authoritative, so
 * this enum is the anchor for the "never claim live data when a fixture is
 * active" guarantee.
 */
public enum SourceKind {

    /** A real system of record queried over the network (authoritative). */
    LIVE,

    /** A deterministic, committed fixture used for tests and local development. */
    FIXTURE,

    /** Model-extrapolated or synthetic data with no external system of record. */
    SIMULATED,

    /** Data derived by the platform from other evidence (e.g. an aggregation). */
    DERIVED;

    /** @return {@code true} only for sources that may back decision-grade claims. */
    public boolean isAuthoritative() {
        return this == LIVE;
    }
}
