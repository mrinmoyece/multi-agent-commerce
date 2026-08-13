package com.agentforge.commerce.platform.service;

import java.time.LocalDate;

/**
 * Immutable inputs for creating an analysis run. {@code requestFingerprint} is a
 * stable hash of the business-meaningful request fields, used to detect misuse
 * of an idempotency key with a different payload.
 */
public record CreateRunCommand(
        String retailerId,
        LocalDate businessDate,
        String idempotencyKey,
        String requestFingerprint,
        String requestedBy,
        String triggerSource,
        String correlationId) {
}
