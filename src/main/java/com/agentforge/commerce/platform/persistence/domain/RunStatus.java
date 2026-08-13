package com.agentforge.commerce.platform.persistence.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a durable analysis run.
 *
 * <p>The permitted transitions form an explicit state machine so that concurrent
 * replicas can never move a run backwards or into an inconsistent state. All
 * transitions are validated in the service layer before persistence.</p>
 *
 * <pre>
 *   PENDING ──▶ RUNNING ──▶ SUCCEEDED
 *      │           │  │
 *      │           │  └──▶ AWAITING_APPROVAL ──▶ SUCCEEDED
 *      │           │              │
 *      │           └──────────────┴──────▶ FAILED
 *      └──────────────────────────────────▶ FAILED
 * </pre>
 */
public enum RunStatus {

    /** Durably recorded but not yet picked up for execution. */
    PENDING,
    /** Claimed by exactly one replica and executing. */
    RUNNING,
    /** Paused pending human approval of a high-impact or low-confidence action. */
    AWAITING_APPROVAL,
    /** Completed and a briefing was produced (possibly with degraded agents). */
    SUCCEEDED,
    /** Terminated without a usable briefing. */
    FAILED;

    private static final Map<RunStatus, Set<RunStatus>> TRANSITIONS = new EnumMap<>(RunStatus.class);

    static {
        TRANSITIONS.put(PENDING, Set.of(RUNNING, FAILED));
        TRANSITIONS.put(RUNNING, Set.of(SUCCEEDED, FAILED, AWAITING_APPROVAL));
        TRANSITIONS.put(AWAITING_APPROVAL, Set.of(SUCCEEDED, FAILED));
        TRANSITIONS.put(SUCCEEDED, Set.of());
        TRANSITIONS.put(FAILED, Set.of());
    }

    /** @return {@code true} when moving from this status to {@code target} is permitted. */
    public boolean canTransitionTo(RunStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** @return {@code true} when no further transitions are possible. */
    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }
}
