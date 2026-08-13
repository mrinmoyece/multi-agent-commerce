package com.agentforge.commerce.runtime.budget;

import java.time.Duration;

/**
 * The immutable per-run resource budget. Every dimension is a hard ceiling the
 * {@link BudgetLedger} enforces before each provider call.
 *
 * @param maxTokens      maximum total tokens (prompt + completion) per run
 * @param maxRequests    maximum provider requests per run
 * @param maxLatency     maximum cumulative provider latency budget per run
 * @param maxCostMicros  maximum estimated spend per run, in micro-USD
 * @param promptMicrosPerThousand     estimated cost per 1000 prompt tokens (micro-USD)
 * @param completionMicrosPerThousand estimated cost per 1000 completion tokens (micro-USD)
 */
public record RunBudget(
        long maxTokens,
        long maxRequests,
        Duration maxLatency,
        long maxCostMicros,
        long promptMicrosPerThousand,
        long completionMicrosPerThousand) {

    public RunBudget {
        if (maxTokens <= 0 || maxRequests <= 0 || maxCostMicros <= 0) {
            throw new IllegalArgumentException("budget ceilings must be positive");
        }
        if (maxLatency == null || maxLatency.isNegative() || maxLatency.isZero()) {
            throw new IllegalArgumentException("maxLatency must be positive");
        }
        if (promptMicrosPerThousand < 0 || completionMicrosPerThousand < 0) {
            throw new IllegalArgumentException("cost rates must be non-negative");
        }
    }

    /** @return the estimated cost in micro-USD for the given token counts. */
    public long estimateCostMicros(long promptTokens, long completionTokens) {
        long prompt = (promptTokens * promptMicrosPerThousand) / 1000;
        long completion = (completionTokens * completionMicrosPerThousand) / 1000;
        return prompt + completion;
    }
}
