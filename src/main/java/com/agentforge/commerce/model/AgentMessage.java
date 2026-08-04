package com.agentforge.commerce.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Message passed between agents in the multi-agent system.
 * Each specialist agent emits FINDINGS messages collected by the coordinator.
 */
@Data
@Builder
public class AgentMessage {
    private String messageId;
    /** Name of the agent that produced this message */
    private String agentName;
    private MessageType messageType;
    private AgentMessagePriority priority;
    /** Findings content — natural language or JSON string */
    private String content;
    private Instant timestamp;

    public enum MessageType {
        FINDINGS,   // Agent sharing what it discovered
        REQUEST,    // Agent requesting data from another agent
        ALERT,      // Urgent finding requiring immediate attention
        COMPLETE    // Agent signalling it has finished
    }

    public enum AgentMessagePriority { HIGH, NORMAL, LOW }
}
