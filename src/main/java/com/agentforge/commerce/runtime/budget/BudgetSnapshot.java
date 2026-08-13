package com.agentforge.commerce.runtime.budget;

import com.agentforge.commerce.runtime.contract.TokenUsage;

/**
 * An immutable point-in-time view of a run's budget consumption, suitable for
 * telemetry, audit records, and API responses. Carries both the spent totals and
 * the ceilings so consumers need no other context to reason about headroom.
 *
 * @param used       accumulated usage so far
 * @param latencyMillis accumulated provider latency so far
 * @param maxTokens  token ceiling
 * @param maxRequests request ceiling
 * @param maxLatencyMillis latency ceiling in millis
 * @param maxCostMicros cost ceiling in micro-USD
 * @param exhausted  whether any dimension has been exhausted
 */
public record BudgetSnapshot(
        TokenUsage used,
        long latencyMillis,
        long maxTokens,
        long maxRequests,
        long maxLatencyMillis,
        long maxCostMicros,
        boolean exhausted) {

    public BudgetSnapshot {
        used = used == null ? TokenUsage.ZERO : used;
    }

    /** @return remaining token headroom (never negative). */
    public long remainingTokens() {
        return Math.max(0, maxTokens - used.totalTokens());
    }

    /** @return remaining request headroom (never negative). */
    public long remainingRequests() {
        return Math.max(0, maxRequests - used.requests());
    }

    /** @return remaining cost headroom in micro-USD (never negative). */
    public long remainingCostMicros() {
        return Math.max(0, maxCostMicros - used.estimatedCostMicros());
    }
}
