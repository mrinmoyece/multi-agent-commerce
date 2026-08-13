package com.agentforge.commerce.runtime.tools.live;

import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Freshness;
import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.SourceKind;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.tools.InventorySnapshot;
import com.agentforge.commerce.runtime.tools.InventoryTool;
import com.agentforge.commerce.runtime.tools.ToolPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Live inventory tool, active only when {@code runtime.tools.mode=LIVE}. It
 * delegates to an {@link InventoryBackend} implementation supplied by the
 * deployment. When no backend bean is wired it fails closed — returning a LIVE
 * ERROR result — rather than inventing data or silently degrading to a fixture.
 */
@Component
@ConditionalOnProperty(name = "runtime.tools.mode", havingValue = "LIVE")
public class LiveInventoryTool implements InventoryTool {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final ObjectProvider<InventoryBackend> backend;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LiveInventoryTool(ObjectProvider<InventoryBackend> backend, ObjectMapper objectMapper, Clock clock) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "inventory.fetch";
    }

    @Override
    public SourceIdentity source() {
        InventoryBackend b = backend.getIfAvailable();
        String sor = b == null ? "unconfigured live backend" : b.systemOfRecord();
        return new SourceIdentity("inventory-live", "Live inventory system of record", SourceKind.LIVE, sor);
    }

    @Override
    public ToolResult<InventorySnapshot> fetch(ToolRequest request) {
        InventoryBackend b = backend.getIfAvailable();
        if (b == null) {
            return ToolResult.error(source(), "live inventory backend is not configured");
        }
        if (request.isExpiredAt(clock.instant())) {
            return ToolResult.error(source(), "deadline exceeded before live inventory call");
        }
        try {
            InventorySnapshot snapshot = b.fetchSnapshot(request);
            Instant observedAt = clock.instant();
            int bytes = ToolPayloads.measure(objectMapper, snapshot, request.maxPayloadBytes());
            return ToolResult.ok(source(), snapshot, List.of(),
                    Freshness.of(observedAt, TTL),
                    new Confidence(0.95, "authoritative live inventory snapshot"), bytes);
        } catch (RuntimeException e) {
            return ToolResult.error(source(), "live inventory backend failed: " + e.getClass().getSimpleName());
        }
    }
}
