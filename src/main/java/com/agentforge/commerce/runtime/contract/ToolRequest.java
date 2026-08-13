package com.agentforge.commerce.runtime.contract;

import java.time.Instant;
import java.util.Map;

/**
 * An immutable, bounded request to a grounded tool. Carries the tenant
 * ({@code retailerId}) for isolation, an absolute {@code deadline} for deadline
 * propagation, and a payload-size ceiling. Parameters are simple strings — tools
 * never receive nor execute arbitrary code or URLs.
 *
 * @param toolName        logical tool name (for telemetry and allowlisting)
 * @param retailerId      tenant identifier used for scoping and isolation
 * @param parameters      bounded string parameters (defensively copied)
 * @param deadline        absolute instant by which the tool must return
 * @param maxPayloadBytes ceiling on the returned payload size in bytes
 */
public record ToolRequest(
        String toolName,
        String retailerId,
        Map<String, String> parameters,
        Instant deadline,
        int maxPayloadBytes) {

    public ToolRequest {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must be provided");
        }
        if (retailerId == null || retailerId.isBlank()) {
            throw new IllegalArgumentException("retailerId must be provided");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("deadline must be provided");
        }
        if (maxPayloadBytes <= 0 || maxPayloadBytes > RuntimeLimits.MAX_TOOL_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("maxPayloadBytes must be within (0, " + RuntimeLimits.MAX_TOOL_PAYLOAD_BYTES + "]");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /** @return {@code true} when the request deadline has passed at {@code now}. */
    public boolean isExpiredAt(Instant now) {
        return now.isAfter(deadline);
    }

    /** Factory using the runtime's default payload ceiling. */
    public static ToolRequest of(String toolName, String retailerId, Instant deadline) {
        return new ToolRequest(toolName, retailerId, Map.of(), deadline, RuntimeLimits.MAX_TOOL_PAYLOAD_BYTES);
    }
}
