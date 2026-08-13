package com.agentforge.commerce.platform.service.exception;

/** Raised when a caller exceeds the configured request rate. Maps to HTTP 429. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
