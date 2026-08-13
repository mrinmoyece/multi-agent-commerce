package com.agentforge.commerce.runtime.tools.fixture;

import com.agentforge.commerce.runtime.contract.Citation;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Evidence;
import com.agentforge.commerce.runtime.contract.Freshness;
import com.agentforge.commerce.runtime.contract.Hashes;
import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.tools.InventorySnapshot;
import com.agentforge.commerce.runtime.tools.InventoryTool;
import com.agentforge.commerce.runtime.tools.ToolPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, committed inventory fixture. Active by default (or whenever
 * {@code runtime.tools.mode=FIXTURE}). It returns a fixed 20-SKU snapshot with
 * fully-formed evidence and citations, and — critically — labels its source as
 * {@link com.agentforge.commerce.runtime.contract.SourceKind#FIXTURE}, so nothing
 * downstream can mistake this deterministic data for a live feed.
 */
@Component
@ConditionalOnProperty(name = "runtime.tools.mode", havingValue = "FIXTURE", matchIfMissing = true)
public class FixtureInventoryTool implements InventoryTool {

    private static final SourceIdentity SOURCE =
            SourceIdentity.fixture("inventory-fixture", "Committed inventory snapshot (WMS fixture)");
    private static final Duration TTL = Duration.ofHours(6);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FixtureInventoryTool(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "inventory.fetch";
    }

    @Override
    public SourceIdentity source() {
        return SOURCE;
    }

    @Override
    public ToolResult<InventorySnapshot> fetch(ToolRequest request) {
        Instant observedAt = clock.instant();
        List<InventorySnapshot.Item> items = items();
        InventorySnapshot snapshot = new InventorySnapshot(request.retailerId(), observedAt, items);

        List<Citation> citations = new ArrayList<>();
        for (InventorySnapshot.Item item : items) {
            if (item.isCritical() || item.isOverstocked()) {
                String snippet = "qty=" + item.currentQty() + " daysCover=" + item.daysOfStockRemaining()
                        + " status=" + item.status();
                citations.add(new Citation(SOURCE.id(), item.sku(), snippet,
                        Hashes.shortSha256(item.sku() + snippet, 16), observedAt));
            }
        }
        Evidence evidence = new Evidence(
                "inventory-snapshot",
                SOURCE,
                citations,
                items.size() + " SKUs observed; " + citations.size() + " critical/overstocked.",
                Freshness.of(observedAt, TTL),
                new Confidence(0.9, "exact values from a committed deterministic fixture"));

        int bytes = ToolPayloads.measure(objectMapper, snapshot, request.maxPayloadBytes());
        return ToolResult.ok(SOURCE, snapshot, List.of(evidence),
                Freshness.of(observedAt, TTL),
                new Confidence(0.9, "deterministic fixture snapshot"), bytes);
    }

    private static List<InventorySnapshot.Item> items() {
        List<InventorySnapshot.Item> items = new ArrayList<>();
        items.add(item("SKU-0001", "Organic Whole Milk 2L", "Dairy", 220, 3, "CRITICAL"));
        items.add(item("SKU-0002", "Free Range Eggs 12pk", "Dairy", 180, 6, "LOW"));
        items.add(item("SKU-0003", "Sourdough Loaf 800g", "Bakery", 95, 4, "CRITICAL"));
        items.add(item("SKU-0004", "Cheddar Mature 400g", "Dairy", 430, 18, "OK"));
        items.add(item("SKU-0005", "Greek Yoghurt 500g", "Dairy", 670, 22, "OK"));
        items.add(item("SKU-0006", "Salmon Fillet 2pk", "Fish", 45, 2, "CRITICAL"));
        items.add(item("SKU-0007", "Chicken Breast 500g", "Meat", 120, 5, "LOW"));
        items.add(item("SKU-0008", "British Beef Mince 500g", "Meat", 88, 3, "CRITICAL"));
        items.add(item("SKU-0009", "Basmati Rice 2kg", "Ambient", 2100, 105, "OVERSTOCK"));
        items.add(item("SKU-0010", "Pasta Fusilli 500g", "Ambient", 3400, 120, "OVERSTOCK"));
        items.add(item("SKU-0011", "Olive Oil Extra Virgin 500ml", "Ambient", 1800, 95, "OVERSTOCK"));
        items.add(item("SKU-0012", "Orange Juice 1L", "Drinks", 310, 14, "OK"));
        items.add(item("SKU-0013", "Sparkling Water 6pk", "Drinks", 890, 40, "OK"));
        items.add(item("SKU-0014", "Baby Spinach 200g", "Produce", 60, 2, "CRITICAL"));
        items.add(item("SKU-0015", "Cherry Tomatoes 400g", "Produce", 140, 6, "LOW"));
        items.add(item("SKU-0016", "Avocado Pack 4", "Produce", 30, 1, "CRITICAL"));
        items.add(item("SKU-0017", "Butter Unsalted 250g", "Dairy", 200, 9, "OK"));
        items.add(item("SKU-0018", "Oat Milk 1L", "Dairy-Alt", 780, 35, "OK"));
        items.add(item("SKU-0019", "Toilet Roll 9pk", "Household", 4200, 110, "OVERSTOCK"));
        items.add(item("SKU-0020", "Washing Up Liquid 500ml", "Household", 3100, 98, "OVERSTOCK"));
        return items;
    }

    private static InventorySnapshot.Item item(String sku, String name, String category, int qty, int days, String status) {
        return new InventorySnapshot.Item(sku, name, category, qty, days, status);
    }
}
