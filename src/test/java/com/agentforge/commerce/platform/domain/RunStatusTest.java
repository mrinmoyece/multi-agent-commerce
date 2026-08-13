package com.agentforge.commerce.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge.commerce.platform.persistence.domain.RunStatus;
import org.junit.jupiter.api.Test;

class RunStatusTest {

    @Test
    void pendingCanStartOrFail() {
        assertThat(RunStatus.PENDING.canTransitionTo(RunStatus.RUNNING)).isTrue();
        assertThat(RunStatus.PENDING.canTransitionTo(RunStatus.FAILED)).isTrue();
        assertThat(RunStatus.PENDING.canTransitionTo(RunStatus.SUCCEEDED)).isFalse();
    }

    @Test
    void runningCanCompleteOrFail() {
        assertThat(RunStatus.RUNNING.canTransitionTo(RunStatus.SUCCEEDED)).isTrue();
        assertThat(RunStatus.RUNNING.canTransitionTo(RunStatus.FAILED)).isTrue();
        assertThat(RunStatus.RUNNING.canTransitionTo(RunStatus.PENDING)).isFalse();
    }

    @Test
    void terminalStatesAllowNoTransition() {
        assertThat(RunStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(RunStatus.FAILED.isTerminal()).isTrue();
        assertThat(RunStatus.PENDING.isTerminal()).isFalse();
        assertThat(RunStatus.RUNNING.isTerminal()).isFalse();
        assertThat(RunStatus.SUCCEEDED.canTransitionTo(RunStatus.FAILED)).isFalse();
    }
}
