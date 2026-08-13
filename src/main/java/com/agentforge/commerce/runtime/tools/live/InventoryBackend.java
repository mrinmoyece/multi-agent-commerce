package com.agentforge.commerce.runtime.tools.live;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.InventorySnapshot;

/**
 * Production boundary to a real inventory system of record (WMS/ERP/3PL). This is
 * the seam a live deployment implements; the runtime supplies the bounded
 * {@link ToolRequest} (tenant, deadline, payload ceiling) and expects a raw,
 * authoritative {@link InventorySnapshot} back. Implementations must enforce the
 * deadline and must never return fabricated data.
 *
 * <p>No implementation ships in this repository: wiring a live backend is a
 * deployment concern. When {@code runtime.tools.mode=LIVE} and no backend bean is
 * present, {@link LiveInventoryTool} fails closed rather than inventing data.</p>
 */
public interface InventoryBackend {

    /** @return an authoritative snapshot for the request, or throws on failure. */
    InventorySnapshot fetchSnapshot(ToolRequest request);

    /** @return a description of the backing system of record (never a secret/URL credential). */
    String systemOfRecord();
}
