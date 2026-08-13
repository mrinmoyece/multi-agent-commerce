package com.agentforge.commerce.platform.persistence.domain;

/**
 * Outcome of a single specialist agent within a run. Terminal by nature — an
 * agent result is recorded once when the agent finishes (or fails closed).
 */
public enum AgentResultStatus {

    /** Agent returned findings from a live model call. */
    SUCCEEDED,
    /** Agent returned an explicit degraded/fallback response. */
    DEGRADED,
    /** Agent threw and the coordinator substituted an unavailable message. */
    FAILED
}
