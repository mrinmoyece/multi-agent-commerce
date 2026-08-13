package com.agentforge.commerce.runtime.contract;

/**
 * Explicit outcome status for tool and agent results. The runtime never collapses
 * a partial or degraded result into an apparent success — callers must branch on
 * this to decide whether a result is decision-grade.
 */
public enum ResultStatus {

    /** Complete, authoritative result. */
    OK,

    /** Some requested data was returned, but coverage is incomplete. */
    PARTIAL,

    /** Result was produced from a fallback, cache, fixture, or simulated source. */
    DEGRADED,

    /** The operation succeeded but yielded no data. */
    EMPTY,

    /** The operation failed; no trustworthy data was produced. */
    ERROR;

    /** @return {@code true} when the result carries data a caller may use (with care). */
    public boolean hasData() {
        return this == OK || this == PARTIAL || this == DEGRADED;
    }

    /** @return {@code true} for a complete, authoritative result only. */
    public boolean isComplete() {
        return this == OK;
    }

    /** @return {@code true} when the result must not be presented as authoritative. */
    public boolean isDegraded() {
        return this == PARTIAL || this == DEGRADED;
    }
}
