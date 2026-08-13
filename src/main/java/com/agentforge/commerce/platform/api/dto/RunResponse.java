package com.agentforge.commerce.platform.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Full representation of a run including synthesis output and agent results. */
public record RunResponse(
        UUID id,
        String retailerId,
        LocalDate businessDate,
        String status,
        String triggerSource,
        String correlationId,
        String executiveSummary,
        List<String> crossInsights,
        List<String> recommendedActions,
        List<String> urgentAlerts,
        String failureReason,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<AgentResultResponse> agentResults) {
}
