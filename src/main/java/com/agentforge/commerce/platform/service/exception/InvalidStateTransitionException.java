package com.agentforge.commerce.platform.service.exception;

/**
 * Raised when a state-machine transition is not permitted (for example approving
 * an already-decided request). Maps to HTTP 409 Conflict.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
