package com.agentforge.commerce.runtime.tools;

import java.time.Instant;
import java.util.List;

/**
 * A grounded, point-in-time inventory snapshot for a single retailer. Produced by
 * an {@link InventoryTool}; every item is individually citable so downstream
 * recommendations can trace back to specific SKUs.
 *
 * @param retailerId tenant the snapshot belongs to
 * @param observedAt when the snapshot was captured
 * @param items      per-SKU stock records (defensively copied)
 */
public record InventorySnapshot(String retailerId, Instant observedAt, List<Item> items) {

    public InventorySnapshot {
        if (retailerId == null || retailerId.isBlank()) {
            throw new IllegalArgumentException("retailerId must be provided");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must be provided");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * A single stock record.
     *
     * @param sku                   stock keeping unit identifier
     * @param name                  product name
     * @param category              merchandising category
     * @param currentQty            units on hand
     * @param daysOfStockRemaining  projected days of cover at current velocity
     * @param status                coarse stock status label
     */
    public record Item(
            String sku,
            String name,
            String category,
            int currentQty,
            int daysOfStockRemaining,
            String status) {

        public Item {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku must be provided");
            }
        }

        /** @return {@code true} when the item has fewer than seven days of cover. */
        public boolean isCritical() {
            return daysOfStockRemaining < 7;
        }

        /** @return {@code true} when the item has more than ninety days of cover. */
        public boolean isOverstocked() {
            return daysOfStockRemaining > 90;
        }
    }
}
