package com.agentforge.commerce.runtime.policy;

import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.runtime.config.RuntimeProperties;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.engine.RuntimeRunReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Configurable human-in-the-loop risk policy.
 *
 * <p>Before a run is finalised, its recommended actions and the confidence of its
 * agent envelopes are evaluated against externally-tunable rules: high-impact
 * pricing changes, emergency orders, and low-confidence recommendations must be
 * paused for human approval. The policy is deterministic and reason-carrying so
 * every hold can be audited, and it can be disabled per environment via
 * {@code runtime.risk.enabled}.</p>
 */
@Component
public class RiskPolicy {

    private final RuntimeProperties properties;

    public RiskPolicy(RuntimeProperties properties) {
        this.properties = properties;
    }

    /** @return {@code true} when risk gating is enforced in this environment. */
    public boolean isEnabled() {
        return properties.getRisk().isEnabled();
    }

    /**
     * Evaluate a briefing and its run report against the risk rules.
     *
     * @param briefing the synthesised briefing whose actions are assessed
     * @param report   the run report supplying per-agent confidence
     * @return a decision naming every rule that requires a human hold
     */
    public RiskDecision evaluate(DailyBriefing briefing, RuntimeRunReport report) {
        if (!isEnabled()) {
            return RiskDecision.clear();
        }
        RuntimeProperties.Risk risk = properties.getRisk();
        List<String> reasons = new ArrayList<>();

        List<String> actions = briefing == null ? null : briefing.getRecommendedActions();
        if (actions != null) {
            for (String action : actions) {
                if (action == null) {
                    continue;
                }
                String normalised = action.toLowerCase(Locale.ROOT);
                if (matchesAny(normalised, risk.getPricingKeywords())) {
                    reasons.add("pricing_change");
                }
                if (matchesAny(normalised, risk.getEmergencyKeywords())) {
                    reasons.add("emergency_order");
                }
            }
        }

        if (report != null) {
            for (AgentResultEnvelope envelope : report.allEnvelopes()) {
                if (envelope.confidence().value() <= risk.getLowConfidenceThreshold()) {
                    reasons.add("low_confidence:" + envelope.agentName());
                }
            }
        }

        List<String> deduped = reasons.stream().distinct().toList();
        return new RiskDecision(!deduped.isEmpty(), deduped);
    }

    private static boolean matchesAny(String text, List<String> keywords) {
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()
                    && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
