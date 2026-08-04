package com.agentforge.commerce.tool;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inventory Data Tool — abstracts the connection to inventory management systems.
 *
 * In production this wraps calls to:
 *   - Warehouse Management System (WMS) REST API
 *   - ERP stock tables (SAP / Oracle)
 *   - 3PL fulfillment APIs
 *
 * All external calls are protected with Resilience4j circuit breaker + retry.
 * When the upstream is unavailable, the fallback returns the last-known snapshot
 * (safe-read degradation — the agent can still report with stale data rather than fail).
 */
@Component
@Slf4j
public class InventoryDataTool {

    /**
     * Fetch current stock levels for every active SKU.
     * Protected: circuit breaker "inventoryData" + 3-attempt retry.
     */
    @Retry(name = "inventoryData", fallbackMethod = "stockLevelsFallback")
    @CircuitBreaker(name = "inventoryData", fallbackMethod = "stockLevelsFallback")
    public List<StockLevel> getCurrentStockLevels() {
        log.debug("[InventoryDataTool] Fetching live stock levels from WMS");
        /*
         * Production: inject a WMS RestTemplate / WebClient and call the API here.
         * During portfolio demo, return a deterministic synthetic dataset that covers
         * all inventory categories a UK retailer tracks.
         */
        return buildSyntheticInventory();
    }

    /** Fallback — returns a safe cached snapshot when the WMS is unreachable. */
    public List<StockLevel> stockLevelsFallback(Throwable t) {
        log.warn("[InventoryDataTool] WMS unavailable ({}), returning cached snapshot", t.getMessage());
        return buildSyntheticInventory();
    }

    // ── Data model ──────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class StockLevel {
        private String sku;
        private String productName;
        private String category;
        private int currentQty;
        private int reorderPoint;
        private int reorderQty;
        private int daysOfStockRemaining;
        private BigDecimal unitCost;
        private String warehouseLocation;
        private StockStatus status;

        public enum StockStatus { OK, LOW, CRITICAL, OVERSTOCK, OUT_OF_STOCK }
    }

    // ── Synthetic data (realistic UK retail SKU set) ─────────────────────

    private List<StockLevel> buildSyntheticInventory() {
        // Deterministic, hardcoded snapshot — repeatable across calls and across test runs.
        return List.of(
            sku("SKU-0001", "Organic Whole Milk 2L",  "Dairy",       220,  300, 500,  3,  1.20, "AMBIENT-A1", StockLevel.StockStatus.CRITICAL),
            sku("SKU-0002", "Free Range Eggs 12pk",   "Dairy",       180,  200, 400,  6,  2.40, "CHILL-B2",   StockLevel.StockStatus.LOW),
            sku("SKU-0003", "Sourdough Loaf 800g",    "Bakery",       95,  150, 200,  4,  1.80, "BAKERY-C1",  StockLevel.StockStatus.CRITICAL),
            sku("SKU-0004", "Cheddar Mature 400g",    "Dairy",       430,  100, 300, 18,  2.10, "CHILL-B3",   StockLevel.StockStatus.OK),
            sku("SKU-0005", "Greek Yoghurt 500g",     "Dairy",       670,  200, 400, 22,  1.50, "CHILL-B4",   StockLevel.StockStatus.OK),
            sku("SKU-0006", "Salmon Fillet 2pk",      "Fish",         45,  100, 150,  2,  5.40, "CHILL-D1",   StockLevel.StockStatus.CRITICAL),
            sku("SKU-0007", "Chicken Breast 500g",    "Meat",        120,  200, 300,  5,  3.20, "CHILL-D2",   StockLevel.StockStatus.LOW),
            sku("SKU-0008", "British Beef Mince 500g","Meat",         88,  150, 250,  3,  3.80, "CHILL-D3",   StockLevel.StockStatus.CRITICAL),
            sku("SKU-0009", "Basmati Rice 2kg",       "Ambient",    2100,  300, 600,105,  2.20, "AMBIENT-E1", StockLevel.StockStatus.OVERSTOCK),
            sku("SKU-0010", "Pasta Fusilli 500g",     "Ambient",    3400,  400, 800,120,  0.90, "AMBIENT-E2", StockLevel.StockStatus.OVERSTOCK),
            sku("SKU-0011", "Olive Oil Extra Virgin 500ml","Ambient",1800, 300, 500, 95,  4.50, "AMBIENT-E3", StockLevel.StockStatus.OVERSTOCK),
            sku("SKU-0012", "Orange Juice 1L",        "Drinks",      310,  250, 500, 14,  1.40, "AMBIENT-F1", StockLevel.StockStatus.OK),
            sku("SKU-0013", "Sparkling Water 6pk",    "Drinks",      890,  300, 600, 40,  2.80, "AMBIENT-F2", StockLevel.StockStatus.OK),
            sku("SKU-0014", "Baby Spinach 200g",      "Produce",      60,  120, 200,  2,  1.20, "PRODUCE-G1", StockLevel.StockStatus.CRITICAL),
            sku("SKU-0015", "Cherry Tomatoes 400g",   "Produce",     140,  150, 250,  6,  1.10, "PRODUCE-G2", StockLevel.StockStatus.LOW),
            sku("SKU-0016", "Avocado Pack 4",         "Produce",      30,  100, 150,  1,  2.50, "PRODUCE-G3", StockLevel.StockStatus.CRITICAL),
            sku("SKU-0017", "Butter Unsalted 250g",   "Dairy",       200,  200, 400,  9,  1.70, "CHILL-B5",   StockLevel.StockStatus.OK),
            sku("SKU-0018", "Oat Milk 1L",            "Dairy-Alt",   780,  250, 500, 35,  1.60, "AMBIENT-A2", StockLevel.StockStatus.OK),
            sku("SKU-0019", "Toilet Roll 9pk",        "Household",  4200,  500, 1000,110,  4.00, "DRY-H1",    StockLevel.StockStatus.OVERSTOCK),
            sku("SKU-0020", "Washing Up Liquid 500ml","Household",  3100,  400, 800,  98,  1.20, "DRY-H2",    StockLevel.StockStatus.OVERSTOCK)
        );
    }

    private StockLevel sku(String sku, String name, String cat, int qty,
                            int reorderPt, int reorderQty, int days,
                            double cost, String loc, StockLevel.StockStatus status) {
        return StockLevel.builder()
                .sku(sku).productName(name).category(cat)
                .currentQty(qty).reorderPoint(reorderPt).reorderQty(reorderQty)
                .daysOfStockRemaining(days)
                .unitCost(BigDecimal.valueOf(cost))
                .warehouseLocation(loc).status(status)
                .build();
    }
}
