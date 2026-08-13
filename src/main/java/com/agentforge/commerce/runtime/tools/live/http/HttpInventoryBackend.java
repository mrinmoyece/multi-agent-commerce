package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.InventorySnapshot;
import com.agentforge.commerce.runtime.tools.live.InventoryBackend;
import java.util.List;

/**
 * Production HTTP adapter to a live inventory system of record (WMS/ERP/3PL). It
 * assembles a bounded, schema-validated, freshness-checked snapshot over the shared
 * {@link HttpToolClient} and maps the strict wire DTOs onto the domain
 * {@link InventorySnapshot}. All transport safety (HTTPS, allowlist, deadlines,
 * size ceilings, auth, retry, circuit breaking, secret-safe errors) is enforced by
 * the client and {@link Paginator}; this class only owns the mapping.
 */
public final class HttpInventoryBackend implements InventoryBackend {

    static final String ADAPTER = "inventory";
    private static final List<String> REQUIRED_FIELDS =
            List.of("sku", "name", "category", "currentQty", "daysOfStockRemaining", "status");

    private final Paginator<InventoryItemDto> paginator;
    private final String systemOfRecord;

    public HttpInventoryBackend(Paginator<InventoryItemDto> paginator, String systemOfRecord) {
        this.paginator = paginator;
        this.systemOfRecord = systemOfRecord;
    }

    @Override
    public InventorySnapshot fetchSnapshot(ToolRequest request) {
        Paginator.Result<InventoryItemDto> result = paginator.fetchAll(request);
        List<InventorySnapshot.Item> items = result.items().stream()
                .map(dto -> new InventorySnapshot.Item(
                        dto.sku(), dto.name(), dto.category(),
                        dto.currentQty(), dto.daysOfStockRemaining(), dto.status()))
                .toList();
        return new InventorySnapshot(request.retailerId(), result.observedAt(), items);
    }

    @Override
    public String systemOfRecord() {
        return systemOfRecord;
    }

    static List<String> requiredFields() {
        return REQUIRED_FIELDS;
    }
}
