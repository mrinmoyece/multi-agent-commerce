package com.agentforge.commerce.runtime.policy;

import java.util.List;

/**
 * The outcome of evaluating a briefing against the human-in-the-loop risk policy.
 *
 * <p>When {@link #requiresApproval()} is {@code true} the run must pause in
 * {@code AWAITING_APPROVAL} until a human decides, and {@link #reasons()} records
 * the non-sensitive, auditable rule matches (e.g. {@code pricing_change},
 * {@code emergency_order}, {@code low_confidence}) that triggered the hold.</p>
 *
 * @param requiresApproval whether the run must pause for human approval
 * @param reasons          non-sensitive reasons the hold was triggered (copied)
 */
public record RiskDecision(boolean requiresApproval, List<String> reasons) {

    public RiskDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    /** A decision to proceed without a human-in-the-loop hold. */
    public static RiskDecision clear() {
        return new RiskDecision(false, List.of());
    }
}
