package com.agentforge.commerce.platform.service;

import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.platform.persistence.domain.AgentResultStatus;
import java.util.Locale;

/**
 * Classifies an {@link AgentMessage} produced by the coordinator into a durable
 * {@link AgentResultStatus}. The coordinator emits a {@code COMPLETE} message
 * when it substituted for a throwing agent, and specialist agents emit
 * {@code FINDINGS} with a recognisable degraded marker when their LLM call fails
 * closed. Everything else is a genuine success.
 */
final class AgentResultClassifier {

    private AgentResultClassifier() {
    }

    static AgentResultStatus classify(AgentMessage message) {
        if (message.getMessageType() == AgentMessage.MessageType.COMPLETE) {
            return AgentResultStatus.FAILED;
        }
        String content = message.getContent();
        if (content != null) {
            String normalised = content.toLowerCase(Locale.ROOT);
            if (normalised.contains("unavailable") || normalised.contains("manual review")
                    || normalised.contains("manual pricing review")) {
                return AgentResultStatus.DEGRADED;
            }
        }
        return AgentResultStatus.SUCCEEDED;
    }
}
