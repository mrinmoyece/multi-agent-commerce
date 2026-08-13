package com.agentforge.commerce.platform.service.exception;

import java.util.UUID;

/** Raised when a referenced approval request does not exist. Maps to HTTP 404. */
public class ApprovalNotFoundException extends RuntimeException {

    public ApprovalNotFoundException(UUID id) {
        super("Approval request not found: " + id);
    }
}
