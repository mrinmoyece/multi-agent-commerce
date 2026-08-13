package com.agentforge.commerce.runtime.contract;

/**
 * Accounting for a single provider interaction (or an aggregate of several).
 * Costs are held in micro-USD (millionths of a dollar) as a {@code long} to avoid
 * floating-point drift when budgets are enforced and audited.
 *
 * @param requests          number of provider requests represented
 * @param promptTokens      input tokens consumed
 * @param completionTokens  output tokens produced
 * @param estimatedCostMicros estimated cost in micro-USD
 */
public record TokenUsage(long requests, long promptTokens, long completionTokens, long estimatedCostMicros) {

    /** A zero-usage ledger entry. */
    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0);

    public TokenUsage {
        if (requests < 0 || promptTokens < 0 || completionTokens < 0 || estimatedCostMicros < 0) {
            throw new IllegalArgumentException("usage counters must be non-negative");
        }
    }

    /** @return the sum of prompt and completion tokens. */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    /** @return a new usage that is the element-wise sum of this and {@code other}. */
    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                requests + other.requests,
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                estimatedCostMicros + other.estimatedCostMicros);
    }
}
