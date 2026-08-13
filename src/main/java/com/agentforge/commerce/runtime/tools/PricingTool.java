package com.agentforge.commerce.runtime.tools;

/**
 * Grounded pricing tool. Returns provenanced {@link PricingObservations} so the
 * pricing specialist reasons only over cited, bounded evidence.
 */
public interface PricingTool extends GroundedTool<PricingObservations> {
}
