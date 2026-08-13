package com.agentforge.commerce.platform.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Compact run representation used in paginated listings. */
public record RunSummaryResponse(
        UUID id,
        String retailerId,
        LocalDate businessDate,
        String status,
        Instant createdAt,
        Instant completedAt) {
}
