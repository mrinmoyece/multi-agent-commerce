package com.agentforge.commerce.runtime.contract;

import java.time.Instant;
import java.util.List;

/**
 * The universal, provider-neutral envelope returned by every grounded tool.
 *
 * <p>A result is never just a payload: it carries its {@link SourceIdentity} (so
 * fixture/simulated data can never masquerade as live), the {@link Evidence} that
 * supports it, its {@link Freshness} and {@link Confidence}, an explicit
 * {@link ResultStatus}, and a bounded payload-size accounting. Callers must
 * inspect {@link #status()} and {@link #source()} before treating a payload as
 * decision-grade.</p>
 *
 * @param <T>         the payload type
 * @param status      explicit outcome status
 * @param source      provenance of the data
 * @param payload     the (possibly {@code null}) typed payload
 * @param evidence    grounded support for the payload (defensively copied, bounded)
 * @param freshness   currency of the data
 * @param confidence  calibrated confidence in the data
 * @param message     bounded, non-sensitive human-readable note
 * @param payloadBytes measured serialized size of the payload in bytes
 */
public record ToolResult<T>(
        ResultStatus status,
        SourceIdentity source,
        T payload,
        List<Evidence> evidence,
        Freshness freshness,
        Confidence confidence,
        String message,
        int payloadBytes) {

    public ToolResult {
        if (status == null) {
            throw new IllegalArgumentException("tool result status must be provided");
        }
        if (source == null) {
            throw new IllegalArgumentException("tool result source must be provided");
        }
        if (payloadBytes < 0 || payloadBytes > RuntimeLimits.MAX_TOOL_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payloadBytes must be within [0, " + RuntimeLimits.MAX_TOOL_PAYLOAD_BYTES + "]");
        }
        evidence = boundEvidence(evidence);
        confidence = confidence == null ? Confidence.none("no confidence supplied") : confidence;
        message = message == null ? "" : message;
    }

    private static List<Evidence> boundEvidence(List<Evidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        List<Evidence> copy = List.copyOf(evidence);
        if (copy.size() > RuntimeLimits.MAX_EVIDENCE_ITEMS) {
            return copy.subList(0, RuntimeLimits.MAX_EVIDENCE_ITEMS);
        }
        return copy;
    }

    /** @return {@code true} when the payload originates from a non-authoritative source. */
    public boolean isSimulatedOrFixture() {
        return !source.isAuthoritative();
    }

    /** @return {@code true} when the payload carries at least one cited evidence item. */
    public boolean isGrounded() {
        return evidence.stream().anyMatch(Evidence::isCited);
    }

    /** @return {@code true} when any supporting evidence is stale at {@code now}. */
    public boolean hasStaleEvidenceAt(Instant now) {
        return evidence.stream().anyMatch(e -> e.isStaleAt(now));
    }

    /** Build a complete, authoritative result. */
    public static <T> ToolResult<T> ok(SourceIdentity source, T payload, List<Evidence> evidence,
                                       Freshness freshness, Confidence confidence, int payloadBytes) {
        ResultStatus status = source.isAuthoritative() ? ResultStatus.OK : ResultStatus.DEGRADED;
        return new ToolResult<>(status, source, payload, evidence, freshness, confidence, "", payloadBytes);
    }

    /** Build a degraded result (fallback/cache/fixture/simulated) that must not be presented as live. */
    public static <T> ToolResult<T> degraded(SourceIdentity source, T payload, List<Evidence> evidence,
                                             Freshness freshness, Confidence confidence, String message, int payloadBytes) {
        return new ToolResult<>(ResultStatus.DEGRADED, source, payload, evidence, freshness, confidence, message, payloadBytes);
    }

    /** Build an empty result (operation succeeded, no data). */
    public static <T> ToolResult<T> empty(SourceIdentity source, String message) {
        return new ToolResult<>(ResultStatus.EMPTY, source, null, List.of(),
                Freshness.of(Instant.EPOCH, java.time.Duration.ZERO), Confidence.none(message), message, 0);
    }

    /** Build an error result (operation failed). */
    public static <T> ToolResult<T> error(SourceIdentity source, String message) {
        return new ToolResult<>(ResultStatus.ERROR, source, null, List.of(),
                Freshness.of(Instant.EPOCH, java.time.Duration.ZERO), Confidence.none(message), message, 0);
    }
}
