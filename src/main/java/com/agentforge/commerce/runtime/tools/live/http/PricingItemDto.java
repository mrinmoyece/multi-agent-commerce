package com.agentforge.commerce.runtime.tools.live.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Strict wire schema for one competitor-pricing record returned by a live pricing
 * intelligence backend. Unknown properties are rejected so schema drift fails
 * closed.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PricingItemDto(
        String sku,
        BigDecimal ourPrice,
        BigDecimal competitorPrice,
        int marginPct) {
}
