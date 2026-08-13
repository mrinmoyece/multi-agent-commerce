package com.agentforge.commerce.runtime.tools.live.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strict wire schema for one topic-level sentiment aggregate returned by a live
 * reviews/support/social backend. Unknown properties are rejected so schema drift
 * fails closed.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SentimentItemDto(
        String topic,
        int positiveCount,
        int negativeCount,
        int returnRatePct,
        int sampleSize) {
}
