package com.agentforge.commerce.platform.api.dto;

import java.time.Instant;

/** A single specialist agent's persisted contribution to a run. */
public record AgentResultResponse(
        String agentName,
        String status,
        String priority,
        String content,
        Instant createdAt) {
}
