package com.agentforge.commerce.runtime.contract;

import java.util.List;

/**
 * The provider-neutral result of one specialist agent invocation.
 *
 * <p>Unlike a raw model string, an envelope is fully traceable: it records the
 * exact prompt template ({@link #promptId()} + {@link #promptHash()}) and the
 * {@link #provider()}/{@link #model()} that produced it, whether the structured
 * payload passed schema validation, the {@link Evidence} the recommendation
 * traces to, calibrated {@link Confidence}, token/cost {@link TokenUsage}, and an
 * explicit list of degraded-mode reasons. The coordinator and persistence layer
 * consume envelopes, never bare model text.</p>
 *
 * @param agentName       producing agent
 * @param status          explicit outcome status
 * @param promptId        identifier of the versioned prompt template used
 * @param promptHash      content hash of the prompt template used
 * @param provider        LLM provider identifier (never a credential)
 * @param model           model/deployment identifier
 * @param payloadJson     bounded, schema-validated structured output as JSON text
 * @param evidence        grounded support (defensively copied, bounded)
 * @param confidence      calibrated confidence
 * @param usage           token/cost accounting for this agent
 * @param latencyMillis   wall-clock latency of the agent invocation
 * @param schemaValid     whether the structured payload passed schema validation
 * @param degradedReasons non-sensitive reasons the result is degraded (copied)
 */
public record AgentResultEnvelope(
        String agentName,
        ResultStatus status,
        String promptId,
        String promptHash,
        String provider,
        String model,
        String payloadJson,
        List<Evidence> evidence,
        Confidence confidence,
        TokenUsage usage,
        long latencyMillis,
        boolean schemaValid,
        List<String> degradedReasons) {

    public AgentResultEnvelope {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName must be provided");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must be provided");
        }
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("latencyMillis must be non-negative");
        }
        promptId = promptId == null ? "" : promptId;
        promptHash = promptHash == null ? "" : promptHash;
        provider = provider == null ? "" : provider;
        model = model == null ? "" : model;
        payloadJson = boundPayload(payloadJson);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        confidence = confidence == null ? Confidence.none("no confidence supplied") : confidence;
        usage = usage == null ? TokenUsage.ZERO : usage;
        degradedReasons = degradedReasons == null ? List.of() : List.copyOf(degradedReasons);
    }

    private static String boundPayload(String payloadJson) {
        if (payloadJson == null) {
            return "";
        }
        if (payloadJson.length() > RuntimeLimits.MAX_AGENT_PAYLOAD_CHARS) {
            return payloadJson.substring(0, RuntimeLimits.MAX_AGENT_PAYLOAD_CHARS);
        }
        return payloadJson;
    }

    /** @return {@code true} when the recommendation traces to at least one cited evidence item. */
    public boolean isGrounded() {
        return evidence.stream().anyMatch(Evidence::isCited);
    }

    /** @return count of cited evidence items backing this result. */
    public long citedEvidenceCount() {
        return evidence.stream().filter(Evidence::isCited).count();
    }

    /** @return {@code true} when the result was produced without full authoritative grounding. */
    public boolean isDegraded() {
        return status.isDegraded() || !degradedReasons.isEmpty();
    }
}
