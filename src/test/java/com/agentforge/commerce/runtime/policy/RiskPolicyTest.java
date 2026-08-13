package com.agentforge.commerce.runtime.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.runtime.config.RuntimeProperties;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.engine.RuntimeRunReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the human-in-the-loop risk policy: pricing/emergency keyword
 * rules and the low-confidence escalation rule, gated by configuration.
 */
@DisplayName("Risk policy")
class RiskPolicyTest {

    private RuntimeProperties enabledProperties() {
        RuntimeProperties properties = new RuntimeProperties();
        properties.getRisk().setEnabled(true);
        return properties;
    }

    private DailyBriefing briefing(List<String> actions) {
        return DailyBriefing.builder()
                .briefingId("b")
                .date(LocalDate.of(2026, 6, 19))
                .retailerId("R1")
                .recommendedActions(actions)
                .urgentAlerts(List.of())
                .executiveSummary("s")
                .generatedAt(Instant.now())
                .build();
    }

    private RuntimeRunReport report(double confidence) {
        AgentResultEnvelope envelope = new AgentResultEnvelope("InventoryAgent", ResultStatus.OK, "id", "h",
                "azure-openai", "gpt-4o", "{}", List.of(), new Confidence(confidence, "r"),
                TokenUsage.ZERO, 1, true, List.of());
        return new RuntimeRunReport(List.of(envelope), null, TokenUsage.ZERO, null, 1, 1, false);
    }

    @Test
    @DisplayName("Disabled policy clears every run")
    void disabled() {
        RiskPolicy policy = new RiskPolicy(new RuntimeProperties() {
            {
                getRisk().setEnabled(false);
            }
        });
        assertThat(policy.isEnabled()).isFalse();
        assertThat(policy.evaluate(briefing(List.of("Reprice SKU-1")), report(0.9)).requiresApproval()).isFalse();
    }

    @Test
    @DisplayName("Pricing keyword triggers an approval hold")
    void pricingKeyword() {
        RiskPolicy policy = new RiskPolicy(enabledProperties());
        var decision = policy.evaluate(briefing(List.of("Reprice SKU-1 down 10%")), report(0.9));
        assertThat(decision.requiresApproval()).isTrue();
        assertThat(decision.reasons()).contains("pricing_change");
    }

    @Test
    @DisplayName("Emergency keyword triggers an approval hold")
    void emergencyKeyword() {
        RiskPolicy policy = new RiskPolicy(enabledProperties());
        var decision = policy.evaluate(briefing(List.of("Place emergency order for SKU-6")), report(0.9));
        assertThat(decision.reasons()).contains("emergency_order");
    }

    @Test
    @DisplayName("Low confidence triggers an approval hold naming the agent")
    void lowConfidence() {
        RiskPolicy policy = new RiskPolicy(enabledProperties());
        var decision = policy.evaluate(briefing(List.of("Monitor SKU-9")), report(0.3));
        assertThat(decision.requiresApproval()).isTrue();
        assertThat(decision.reasons()).contains("low_confidence:InventoryAgent");
    }

    @Test
    @DisplayName("Clean run with high confidence requires no approval")
    void clean() {
        RiskPolicy policy = new RiskPolicy(enabledProperties());
        var decision = policy.evaluate(briefing(List.of("Monitor SKU-9")), report(0.9));
        assertThat(decision.requiresApproval()).isFalse();
        assertThat(decision.reasons()).isEmpty();
        assertThat(RiskDecision.clear().requiresApproval()).isFalse();
    }
}
