package com.agentforge.commerce.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.platform.persistence.domain.AgentResultStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentResultClassifierTest {

    private static AgentMessage message(AgentMessage.MessageType type, String content) {
        return AgentMessage.builder()
                .agentName("InventoryAgent")
                .messageType(type)
                .priority(AgentMessage.AgentMessagePriority.NORMAL)
                .content(content)
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void completeMessageIsFailure() {
        assertThat(AgentResultClassifier.classify(
                message(AgentMessage.MessageType.COMPLETE, "coordinator substitute")))
                .isEqualTo(AgentResultStatus.FAILED);
    }

    @Test
    void degradedMarkersAreDegraded() {
        assertThat(AgentResultClassifier.classify(
                message(AgentMessage.MessageType.FINDINGS, "Pricing data UNAVAILABLE — manual review")))
                .isEqualTo(AgentResultStatus.DEGRADED);
        assertThat(AgentResultClassifier.classify(
                message(AgentMessage.MessageType.FINDINGS, "Please do a manual pricing review")))
                .isEqualTo(AgentResultStatus.DEGRADED);
    }

    @Test
    void ordinaryFindingsSucceed() {
        assertThat(AgentResultClassifier.classify(
                message(AgentMessage.MessageType.FINDINGS, "All SKUs healthy")))
                .isEqualTo(AgentResultStatus.SUCCEEDED);
    }

    @Test
    void nullContentSucceeds() {
        assertThat(AgentResultClassifier.classify(
                message(AgentMessage.MessageType.FINDINGS, null)))
                .isEqualTo(AgentResultStatus.SUCCEEDED);
    }
}
