package com.agentforge.commerce.runtime.tools;

/**
 * Grounded sentiment tool. Returns provenanced {@link SentimentObservations} so
 * the sentiment specialist reasons only over cited, bounded evidence.
 */
public interface SentimentTool extends GroundedTool<SentimentObservations> {
}
