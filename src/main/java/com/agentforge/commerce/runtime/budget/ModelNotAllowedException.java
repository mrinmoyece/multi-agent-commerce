package com.agentforge.commerce.runtime.budget;

/**
 * Thrown when a run attempts to use a provider or model that is not on the
 * configured allowlist. Enforcing an allowlist prevents an agent (or a
 * misconfiguration) from silently routing traffic to an unapproved model.
 */
public class ModelNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelNotAllowedException(String message) {
        super(message);
    }
}
