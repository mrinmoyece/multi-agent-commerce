package com.agentforge.commerce.platform.service.exception;

import java.util.UUID;

/** Raised when a referenced analysis run does not exist. Maps to HTTP 404. */
public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(UUID id) {
        super("Analysis run not found: " + id);
    }
}
