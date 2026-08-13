package com.agentforge.commerce.platform.persistence.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a human approval request raised against a run's recommended actions.
 *
 * <pre>
 *   PENDING ──▶ APPROVED
 *      └───────▶ REJECTED
 * </pre>
 */
public enum ApprovalStatus {

    /** Awaiting a human decision. */
    PENDING,
    /** A reviewer accepted the recommended action. */
    APPROVED,
    /** A reviewer declined the recommended action. */
    REJECTED;

    private static final Map<ApprovalStatus, Set<ApprovalStatus>> TRANSITIONS =
            new EnumMap<>(ApprovalStatus.class);

    static {
        TRANSITIONS.put(PENDING, Set.of(APPROVED, REJECTED));
        TRANSITIONS.put(APPROVED, Set.of());
        TRANSITIONS.put(REJECTED, Set.of());
    }

    /** @return {@code true} when moving from this status to {@code target} is permitted. */
    public boolean canTransitionTo(ApprovalStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** @return {@code true} when a decision has already been recorded. */
    public boolean isDecided() {
        return this != PENDING;
    }
}
