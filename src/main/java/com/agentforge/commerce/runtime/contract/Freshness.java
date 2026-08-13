package com.agentforge.commerce.runtime.contract;

import java.time.Duration;
import java.time.Instant;

/**
 * How current a piece of evidence is. Combines the moment the data was observed
 * with a validity window so the runtime can flag stale or contradictory evidence
 * before it reaches a recommendation.
 *
 * @param observedAt when the underlying data was captured
 * @param ttl        how long the observation remains valid
 */
public record Freshness(Instant observedAt, Duration ttl) {

    public Freshness {
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must be provided");
        }
        if (ttl == null || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be non-negative");
        }
    }

    /** @return the age of the observation relative to {@code now}. */
    public Duration ageAt(Instant now) {
        return Duration.between(observedAt, now);
    }

    /** @return {@code true} when the observation has exceeded its validity window at {@code now}. */
    public boolean isStaleAt(Instant now) {
        return now.isAfter(observedAt.plus(ttl));
    }

    /** Factory for evidence considered valid for {@code ttl} from {@code observedAt}. */
    public static Freshness of(Instant observedAt, Duration ttl) {
        return new Freshness(observedAt, ttl);
    }
}
