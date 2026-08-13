package com.agentforge.commerce.platform.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Representation of an approval request and its decision, if any. */
public record ApprovalResponse(
        UUID id,
        UUID runId,
        String action,
        String status,
        String requestedBy,
        String decidedBy,
        String decisionReason,
        Instant createdAt,
        Instant decidedAt) {
}
