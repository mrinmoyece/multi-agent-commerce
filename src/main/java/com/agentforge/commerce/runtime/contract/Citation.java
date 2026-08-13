package com.agentforge.commerce.runtime.contract;

import java.time.Instant;

/**
 * A single, verifiable pointer into a source. Citations are what make a
 * recommendation traceable: every claim the runtime surfaces must reference one
 * or more of these, each naming the source, the specific locator (e.g. a SKU or
 * record key), and a content hash of the observed value so drift can be detected.
 *
 * @param sourceId  identifier of the {@link SourceIdentity} this citation belongs to
 * @param locator   the specific record/key within the source (e.g. {@code SKU-0001})
 * @param snippet   a short, bounded, non-sensitive excerpt of the observed value
 * @param valueHash a stable hash of the observed value for drift detection
 * @param observedAt when the cited value was observed
 */
public record Citation(String sourceId, String locator, String snippet, String valueHash, Instant observedAt) {

    public Citation {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("citation sourceId must be provided");
        }
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("citation locator must be provided");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("citation observedAt must be provided");
        }
        snippet = boundSnippet(snippet);
        valueHash = valueHash == null ? "" : valueHash;
    }

    private static String boundSnippet(String snippet) {
        if (snippet == null) {
            return "";
        }
        String trimmed = snippet.strip();
        if (trimmed.length() > RuntimeLimits.MAX_CITATION_SNIPPET_CHARS) {
            return trimmed.substring(0, RuntimeLimits.MAX_CITATION_SNIPPET_CHARS);
        }
        return trimmed;
    }
}
