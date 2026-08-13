package com.agentforge.commerce.platform.api.dto;

import jakarta.validation.constraints.Size;

/** Optional reason supplied with an approve/reject decision. */
public record ApprovalDecisionRequest(
        @Size(max = 2000, message = "reason must be at most 2000 characters")
        String reason) {
}
