package com.agentforge.commerce.runtime.tools;

import java.time.Instant;
import java.util.List;

/**
 * Grounded customer-sentiment observations for a single retailer, aggregated by
 * topic/category. Produced by a {@link SentimentTool}; each signal is citable so
 * themes trace back to concrete review/support aggregates.
 *
 * @param retailerId tenant the observations belong to
 * @param observedAt when the observations were captured
 * @param signals    per-topic sentiment aggregates (defensively copied)
 */
public record SentimentObservations(String retailerId, Instant observedAt, List<Signal> signals) {

    public SentimentObservations {
        if (retailerId == null || retailerId.isBlank()) {
            throw new IllegalArgumentException("retailerId must be provided");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must be provided");
        }
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    /**
     * A single topic-level sentiment aggregate.
     *
     * @param topic         category/topic identifier
     * @param positiveCount count of positive signals
     * @param negativeCount count of negative signals
     * @param returnRatePct returns rate for the topic, as a percentage
     * @param sampleSize    total signals aggregated
     */
    public record Signal(String topic, int positiveCount, int negativeCount, int returnRatePct, int sampleSize) {

        public Signal {
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic must be provided");
            }
        }

        /** @return {@code true} when negative signals outweigh positive ones. */
        public boolean isNegativeLeaning() {
            return negativeCount > positiveCount;
        }
    }
}
