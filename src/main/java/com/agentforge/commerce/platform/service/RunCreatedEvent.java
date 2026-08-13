package com.agentforge.commerce.platform.service;

import java.util.UUID;

/**
 * Published (after commit) once a run has been durably created, to trigger
 * exactly-once asynchronous execution.
 */
public record RunCreatedEvent(UUID runId) {
}
