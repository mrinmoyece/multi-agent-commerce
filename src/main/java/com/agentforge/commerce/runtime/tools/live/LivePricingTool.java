package com.agentforge.commerce.runtime.tools.live;

import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Freshness;
import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.SourceKind;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.tools.PricingObservations;
import com.agentforge.commerce.runtime.tools.PricingTool;
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
 * Live pricing tool, active only when {@code runtime.tools.mode=LIVE}. Delegates
 * to a {@link PricingBackend} and fails closed with a LIVE ERROR result when no
 * backend is configured.
 */
@Component
@ConditionalOnProperty(name = "runtime.tools.mode", havingValue = "LIVE")
public class LivePricingTool implements PricingTool {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final ObjectProvider<PricingBackend> backend;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LivePricingTool(ObjectProvider<PricingBackend> backend, ObjectMapper objectMapper, Clock clock) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "pricing.fetch";
    }

    @Override
    public SourceIdentity source() {
        PricingBackend b = backend.getIfAvailable();
        String sor = b == null ? "unconfigured live backend" : b.systemOfRecord();
        return new SourceIdentity("pricing-live", "Live pricing intelligence", SourceKind.LIVE, sor);
    }

    @Override
    public ToolResult<PricingObservations> fetch(ToolRequest request) {
        PricingBackend b = backend.getIfAvailable();
        if (b == null) {
            return ToolResult.error(source(), "live pricing backend is not configured");
        }
        if (request.isExpiredAt(clock.instant())) {
            return ToolResult.error(source(), "deadline exceeded before live pricing call");
        }
        try {
            PricingObservations payload = b.fetchObservations(request);
            Instant observedAt = clock.instant();
            int bytes = ToolPayloads.measure(objectMapper, payload, request.maxPayloadBytes());
            return ToolResult.ok(source(), payload, List.of(),
                    Freshness.of(observedAt, TTL),
                    new Confidence(0.9, "authoritative live pricing observations"), bytes);
        } catch (RuntimeException e) {
            return ToolResult.error(source(), "live pricing backend failed: " + e.getClass().getSimpleName());
        }
    }
}
