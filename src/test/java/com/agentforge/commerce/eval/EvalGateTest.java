package com.agentforge.commerce.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * In-suite guard for the offline evaluation framework: every golden scenario must
 * pass its thresholds, and the harness must be deterministic run-to-run. The
 * committed baseline and CI drift gate are exercised separately by the
 * {@code evalGate} Gradle task.
 */
@DisplayName("Offline evaluation gate")
class EvalGateTest {

    @Test
    @DisplayName("Every golden scenario passes its thresholds")
    void allScenariosPass() {
        EvalReport report = new EvalHarness().runAll();
        assertThat(report.scenarios()).hasSize(8);
        assertThat(report.allPassed())
                .as("scenarios that failed: %s",
                        report.scenarios().stream().filter(s -> !s.passed()).map(ScenarioResult::id).toList())
                .isTrue();
    }

    @Test
    @DisplayName("The harness is deterministic across runs")
    void deterministic() {
        EvalReport first = new EvalHarness().runAll();
        EvalReport second = new EvalHarness().runAll();
        assertThat(second.scenarios()).isEqualTo(first.scenarios());
    }

    @Test
    @DisplayName("Golden scenario set covers the required risk cases")
    void coversRequiredScenarios() {
        assertThat(new EvalHarness().scenarioIds()).containsExactly(
                "normal", "partial-failure", "prompt-injection", "stale-contradictory-evidence",
                "budget-exhaustion", "malformed-output", "approval-required", "tenant-isolation");
    }
}
