package com.agentforge.commerce.platform.service.exception;

/**
 * Raised when an idempotency key is reused with a different request payload.
 * Maps to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
