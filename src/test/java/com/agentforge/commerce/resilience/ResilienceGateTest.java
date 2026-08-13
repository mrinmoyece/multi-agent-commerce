package com.agentforge.commerce.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Enforces the resilience/chaos scenarios on every build so fault-handling
 * regressions are caught independently of the standalone {@code resilienceGate}
 * Gradle task.
 */
class ResilienceGateTest {

    private final ResilienceHarness harness = new ResilienceHarness();

    @Test
    void throttlingIsRetriedAndRecovers() {
        assertThat(harness.providerThrottlingRecovers().passed()).isTrue();
    }

    @Test
    void partialOutageOpensCircuit() {
        assertThat(harness.partialToolOutageOpensCircuit().passed()).isTrue();
    }

    @Test
    void deterministicFaultsFailFast() {
        assertThat(harness.deterministicFaultFailsFast().passed()).isTrue();
    }

    @Test
    void coordinationOutageFailsClosed() {
        assertThat(harness.coordinationOutageFailsClosed().passed()).isTrue();
    }

    @Test
    void coordinationRecoversAfterTransientContention() {
        assertThat(harness.coordinationTransientThenRecovers().passed()).isTrue();
    }

    @Test
    void deadlinesAreHonoured() {
        assertThat(harness.deadlineIsHonoured().passed()).isTrue();
    }

    @Test
    void gracefulShutdownDrainsInflightWork() {
        assertThat(harness.gracefulShutdownDrains().passed()).isTrue();
    }
}
