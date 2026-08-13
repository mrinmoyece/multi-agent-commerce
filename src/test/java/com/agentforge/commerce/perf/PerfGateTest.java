package com.agentforge.commerce.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Runs the perf/concurrency harness under the normal test suite so its correctness
 * invariants (exactly-once claiming, no starvation) are enforced on every build,
 * independent of the standalone {@code perfGate} Gradle task.
 */
class PerfGateTest {

    @Test
    void concurrentClaimYieldsExactlyOneWinnerPerRun() {
        PerfHarness.Scenario scenario = new PerfHarness().concurrentClaimNoDuplicates();
        assertThat(scenario.metrics()).containsEntry("duplicateClaims", 0);
        assertThat(scenario.metrics()).containsEntry("unclaimed", 0);
        assertThat(scenario.passed()).isTrue();
    }

    @Test
    void coordinationThroughputMeetsFloor() {
        PerfHarness.Scenario scenario = new PerfHarness().coordinationThroughput();
        assertThat(scenario.passed())
                .as("coordination throughput %s", scenario.metrics())
                .isTrue();
    }
}
