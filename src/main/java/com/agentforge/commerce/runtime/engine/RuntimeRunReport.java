package com.agentforge.commerce.runtime.engine;

import com.agentforge.commerce.runtime.budget.BudgetSnapshot;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import java.util.List;

/**
 * Immutable, non-sensitive summary of a completed run through the runtime.
 *
 * <p>It aggregates each specialist and the coordinator {@link AgentResultEnvelope},
 * the total {@link TokenUsage}, the final {@link BudgetSnapshot}, and the number of
 * tool calls and agent rounds consumed. The service layer persists the derived
 * metadata (prompt refs, tokens, cost, confidence, degraded flags) and telemetry
 * records the metrics — no prompts, payloads or credentials are ever included.</p>
 *
 * @param specialists          per-specialist envelopes
 * @param coordinator          the coordinator synthesis envelope
 * @param totalUsage           aggregate token/cost usage for the run
 * @param budget               final budget snapshot
 * @param toolCalls            number of tool calls consumed
 * @param agentRounds          number of agent rounds consumed
 * @param degraded             whether any part of the run was degraded
 */
public record RuntimeRunReport(
        List<AgentResultEnvelope> specialists,
        AgentResultEnvelope coordinator,
        TokenUsage totalUsage,
        BudgetSnapshot budget,
        int toolCalls,
        int agentRounds,
        boolean degraded) {

    public RuntimeRunReport {
        specialists = specialists == null ? List.of() : List.copyOf(specialists);
        totalUsage = totalUsage == null ? TokenUsage.ZERO : totalUsage;
    }

    /** @return an empty report for backward-compatible construction paths. */
    public static RuntimeRunReport empty() {
        return new RuntimeRunReport(List.of(), null, TokenUsage.ZERO, null, 0, 0, false);
    }

    /** @return the aggregate confidence-bearing envelopes (specialists plus coordinator when present). */
    public List<AgentResultEnvelope> allEnvelopes() {
        if (coordinator == null) {
            return specialists;
        }
        List<AgentResultEnvelope> all = new java.util.ArrayList<>(specialists);
        all.add(coordinator);
        return List.copyOf(all);
    }
}
