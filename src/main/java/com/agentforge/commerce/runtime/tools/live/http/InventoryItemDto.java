package com.agentforge.commerce.runtime.tools.live.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strict wire schema for one inventory record returned by a live WMS/ERP backend.
 * Deserialised with unknown-property rejection so a drifted upstream schema fails
 * closed rather than silently dropping fields.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record InventoryItemDto(
        String sku,
        String name,
        String category,
        int currentQty,
        int daysOfStockRemaining,
        String status) {
}
