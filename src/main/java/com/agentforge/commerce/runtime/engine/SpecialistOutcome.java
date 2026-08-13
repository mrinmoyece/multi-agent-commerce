package com.agentforge.commerce.runtime.engine;

import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;

/**
 * The result of one specialist execution through the runtime: the fully-traceable
 * {@link AgentResultEnvelope} plus the legacy {@link AgentMessage} projection kept
 * for backward-compatible Layer 1 persistence and coordinator synthesis.
 *
 * @param envelope the provider-neutral, evidence-bearing envelope
 * @param message  the legacy message projection
 */
public record SpecialistOutcome(AgentResultEnvelope envelope, AgentMessage message) {
}
