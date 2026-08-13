package com.agentforge.commerce.runtime.tools;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Grounded competitor-and-margin observations for a single retailer. Produced by
 * a {@link PricingTool}; each observation names a SKU so repricing
 * recommendations remain traceable.
 *
 * @param retailerId  tenant the observations belong to
 * @param observedAt  when the observations were captured
 * @param observations per-SKU price/margin records (defensively copied)
 */
public record PricingObservations(String retailerId, Instant observedAt, List<Observation> observations) {

    public PricingObservations {
        if (retailerId == null || retailerId.isBlank()) {
            throw new IllegalArgumentException("retailerId must be provided");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must be provided");
        }
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    /**
     * A single price/margin observation.
     *
     * @param sku             stock keeping unit identifier
     * @param ourPrice        our current shelf price
     * @param competitorPrice observed competitor price
     * @param marginPct       current gross margin percentage
     */
    public record Observation(String sku, BigDecimal ourPrice, BigDecimal competitorPrice, int marginPct) {

        public Observation {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku must be provided");
            }
            if (ourPrice == null || competitorPrice == null) {
                throw new IllegalArgumentException("prices must be provided");
            }
        }

        /** @return {@code true} when a competitor is undercutting our price. */
        public boolean isUndercut() {
            return competitorPrice.compareTo(ourPrice) < 0;
        }
    }
}
