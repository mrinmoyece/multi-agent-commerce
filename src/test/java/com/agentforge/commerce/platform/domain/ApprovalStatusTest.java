package com.agentforge.commerce.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge.commerce.platform.persistence.domain.ApprovalStatus;
import org.junit.jupiter.api.Test;

class ApprovalStatusTest {

    @Test
    void pendingCanBeDecided() {
        assertThat(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.APPROVED)).isTrue();
        assertThat(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.REJECTED)).isTrue();
        assertThat(ApprovalStatus.PENDING.isDecided()).isFalse();
    }

    @Test
    void decidedIsTerminal() {
        assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.REJECTED)).isFalse();
        assertThat(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.APPROVED)).isFalse();
        assertThat(ApprovalStatus.APPROVED.isDecided()).isTrue();
        assertThat(ApprovalStatus.REJECTED.isDecided()).isTrue();
    }
}
