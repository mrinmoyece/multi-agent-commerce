package com.agentforge.commerce.runtime.contract;

import java.time.Instant;
import java.util.List;

/**
 * A grounded unit of support for a claim. Each piece of evidence names its
 * {@link SourceIdentity}, the {@link Citation}s that back it, a bounded
 * human-readable summary, its {@link Freshness}, and a calibrated
 * {@link Confidence}. Recommendations that cannot point at evidence are treated
 * as ungrounded and are not decision-grade.
 *
 * @param id         stable identifier for the evidence item
 * @param source     provenance of the evidence
 * @param citations  verifiable pointers into the source (defensively copied, bounded)
 * @param summary    bounded, non-sensitive natural-language summary
 * @param freshness  currency of the evidence
 * @param confidence calibrated confidence in the evidence
 */
public record Evidence(
        String id,
        SourceIdentity source,
        List<Citation> citations,
        String summary,
        Freshness freshness,
        Confidence confidence) {

    public Evidence {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("evidence id must be provided");
        }
        if (source == null) {
            throw new IllegalArgumentException("evidence source must be provided");
        }
        if (freshness == null) {
            throw new IllegalArgumentException("evidence freshness must be provided");
        }
        if (confidence == null) {
            throw new IllegalArgumentException("evidence confidence must be provided");
        }
        citations = boundCitations(citations);
        summary = boundSummary(summary);
    }

    private static List<Citation> boundCitations(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<Citation> copy = List.copyOf(citations);
        if (copy.size() > RuntimeLimits.MAX_CITATIONS_PER_EVIDENCE) {
            return copy.subList(0, RuntimeLimits.MAX_CITATIONS_PER_EVIDENCE);
        }
        return copy;
    }

    private static String boundSummary(String summary) {
        if (summary == null) {
            return "";
        }
        String trimmed = summary.strip();
        if (trimmed.length() > RuntimeLimits.MAX_EVIDENCE_SUMMARY_CHARS) {
            return trimmed.substring(0, RuntimeLimits.MAX_EVIDENCE_SUMMARY_CHARS);
        }
        return trimmed;
    }

    /** @return {@code true} when this evidence is backed by an authoritative source. */
    public boolean isAuthoritative() {
        return source.isAuthoritative();
    }

    /** @return {@code true} when the evidence has passed its freshness window at {@code now}. */
    public boolean isStaleAt(Instant now) {
        return freshness.isStaleAt(now);
    }

    /** @return {@code true} when the evidence carries at least one verifiable citation. */
    public boolean isCited() {
        return !citations.isEmpty();
    }
}
