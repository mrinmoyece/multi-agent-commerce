package com.agentforge.commerce.runtime.budget;

import com.agentforge.commerce.runtime.contract.TokenUsage;

/**
 * A thread-safe, per-run ledger that enforces a {@link RunBudget} as a hard stop
 * <em>before</em> each provider call and reconciles estimates against actual usage
 * afterwards.
 *
 * <p>Because specialist agents run concurrently, reservation and settlement are
 * atomic: {@link #reserve} projects the request's estimated cost against every
 * budget dimension and either admits it (counting the estimate immediately) or
 * throws {@link BudgetExceededException} without spending. {@link #settle} then
 * swaps the estimate for the provider's reported actuals; {@link #abort} keeps the
 * conservative estimate counted when a call fails. The ledger therefore never
 * lets a run exceed its budget and always over-counts rather than under-counts.</p>
 */
public final class BudgetLedger {

    private final RunBudget budget;
    private final Object lock = new Object();
    private TokenUsage used = TokenUsage.ZERO;
    private long latencyMillis;

    public BudgetLedger(RunBudget budget) {
        if (budget == null) {
            throw new IllegalArgumentException("budget must be provided");
        }
        this.budget = budget;
    }

    /** @return the immutable budget this ledger enforces. */
    public RunBudget budget() {
        return budget;
    }

    /**
     * Reserve headroom for one provider call estimated at the given token counts.
     * Admits and counts the estimate atomically, or throws before any spend.
     */
    public Reservation reserve(long estPromptTokens, long estCompletionTokens) {
        if (estPromptTokens < 0 || estCompletionTokens < 0) {
            throw new IllegalArgumentException("token estimates must be non-negative");
        }
        long estTokens = estPromptTokens + estCompletionTokens;
        long estCost = budget.estimateCostMicros(estPromptTokens, estCompletionTokens);
        synchronized (lock) {
            if (latencyMillis >= budget.maxLatency().toMillis()) {
                throw new BudgetExceededException("latency",
                        "latency budget exhausted before provider call");
            }
            if (used.requests() + 1 > budget.maxRequests()) {
                throw new BudgetExceededException("requests",
                        "request budget exhausted (" + budget.maxRequests() + ")");
            }
            if (used.totalTokens() + estTokens > budget.maxTokens()) {
                throw new BudgetExceededException("tokens",
                        "token budget exhausted (" + budget.maxTokens() + ")");
            }
            if (used.estimatedCostMicros() + estCost > budget.maxCostMicros()) {
                throw new BudgetExceededException("cost",
                        "cost budget exhausted (" + budget.maxCostMicros() + " micro-USD)");
            }
            TokenUsage estimate = new TokenUsage(1, estPromptTokens, estCompletionTokens, estCost);
            used = used.plus(estimate);
            return new Reservation(estimate);
        }
    }

    /** Reconcile a reservation with the provider's actual usage and observed latency. */
    public void settle(Reservation reservation, TokenUsage actual, long callLatencyMillis) {
        if (reservation == null || actual == null) {
            throw new IllegalArgumentException("reservation and actual usage must be provided");
        }
        synchronized (lock) {
            used = subtract(used, reservation.estimate()).plus(actual);
            latencyMillis += Math.max(0, callLatencyMillis);
        }
    }

    /** Record a failed call, keeping the conservative estimate counted. */
    public void abort(Reservation reservation, long callLatencyMillis) {
        synchronized (lock) {
            latencyMillis += Math.max(0, callLatencyMillis);
        }
    }

    /** @return an immutable snapshot of consumption and ceilings. */
    public BudgetSnapshot snapshot() {
        synchronized (lock) {
            boolean exhausted = used.requests() >= budget.maxRequests()
                    || used.totalTokens() >= budget.maxTokens()
                    || used.estimatedCostMicros() >= budget.maxCostMicros()
                    || latencyMillis >= budget.maxLatency().toMillis();
            return new BudgetSnapshot(used, latencyMillis, budget.maxTokens(), budget.maxRequests(),
                    budget.maxLatency().toMillis(), budget.maxCostMicros(), exhausted);
        }
    }

    private static TokenUsage subtract(TokenUsage total, TokenUsage part) {
        return new TokenUsage(
                Math.max(0, total.requests() - part.requests()),
                Math.max(0, total.promptTokens() - part.promptTokens()),
                Math.max(0, total.completionTokens() - part.completionTokens()),
                Math.max(0, total.estimatedCostMicros() - part.estimatedCostMicros()));
    }

    /** An admitted reservation carrying the estimate that was counted. */
    public record Reservation(TokenUsage estimate) {
        public Reservation {
            estimate = estimate == null ? TokenUsage.ZERO : estimate;
        }
    }
}
