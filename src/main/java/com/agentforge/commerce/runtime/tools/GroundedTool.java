package com.agentforge.commerce.runtime.tools;

import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;

/**
 * Provider-neutral contract for a grounded tool. Implementations may be
 * deterministic fixtures or live production adapters; either way they return a
 * fully-provenanced {@link ToolResult} that names its {@link SourceIdentity}, so
 * the runtime can always tell live data from fixture or simulated data.
 *
 * @param <T> the payload type the tool returns
 */
public interface GroundedTool<T> {

    /** @return the logical tool name used for telemetry and allowlisting. */
    String name();

    /** @return the identity/provenance of this tool's data source. */
    SourceIdentity source();

    /**
     * Fetch data for {@code request}. Must honour the request deadline and payload
     * ceiling, must never execute arbitrary code or fetch arbitrary URLs, and must
     * fail closed (an ERROR/EMPTY result) rather than fabricate data.
     */
    ToolResult<T> fetch(ToolRequest request);
}
