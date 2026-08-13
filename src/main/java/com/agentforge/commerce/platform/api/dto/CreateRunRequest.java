package com.agentforge.commerce.platform.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for creating an analysis run. {@code businessDate} is optional and
 * defaults to the current date server-side when omitted.
 */
public record CreateRunRequest(
        @NotBlank(message = "retailerId is required")
        @Size(max = 100, message = "retailerId must be at most 100 characters")
        String retailerId,
        LocalDate businessDate) {
}
