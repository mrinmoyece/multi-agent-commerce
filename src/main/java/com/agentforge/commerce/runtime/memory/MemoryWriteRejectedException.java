package com.agentforge.commerce.runtime.memory;

/**
 * Thrown when a caller attempts to write a fact into durable semantic memory that
 * is not backed by validated, cited or authoritative evidence. Enforcing this as a
 * hard failure guarantees that raw, ungrounded model output can never be memorised.
 */
public class MemoryWriteRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MemoryWriteRejectedException(String message) {
        super(message);
    }
}
