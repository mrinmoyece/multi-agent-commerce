package com.agentforge.commerce.platform.service;

import java.util.UUID;

/**
 * Published after a human approval decision commits, so a run paused in
 * {@code AWAITING_APPROVAL} can be resumed deterministically once all of its
 * approvals have been decided. Carries only the run identifier — never any
 * sensitive decision content.
 *
 * @param runId the run whose approvals changed
 */
public record ApprovalDecidedEvent(UUID runId) {
}
