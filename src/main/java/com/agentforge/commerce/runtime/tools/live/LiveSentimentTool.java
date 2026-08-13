package com.agentforge.commerce.runtime.tools.live;

import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Freshness;
import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.SourceKind;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.tools.SentimentObservations;
import com.agentforge.commerce.runtime.tools.SentimentTool;
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
 * Live sentiment tool, active only when {@code runtime.tools.mode=LIVE}. Delegates
 * to a {@link SentimentBackend} and fails closed with a LIVE ERROR result when no
 * backend is configured.
 */
@Component
@ConditionalOnProperty(name = "runtime.tools.mode", havingValue = "LIVE")
public class LiveSentimentTool implements SentimentTool {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final ObjectProvider<SentimentBackend> backend;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LiveSentimentTool(ObjectProvider<SentimentBackend> backend, ObjectMapper objectMapper, Clock clock) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "sentiment.fetch";
    }

    @Override
    public SourceIdentity source() {
        SentimentBackend b = backend.getIfAvailable();
        String sor = b == null ? "unconfigured live backend" : b.systemOfRecord();
        return new SourceIdentity("sentiment-live", "Live sentiment aggregation", SourceKind.LIVE, sor);
    }

    @Override
    public ToolResult<SentimentObservations> fetch(ToolRequest request) {
        SentimentBackend b = backend.getIfAvailable();
        if (b == null) {
            return ToolResult.error(source(), "live sentiment backend is not configured");
        }
        if (request.isExpiredAt(clock.instant())) {
            return ToolResult.error(source(), "deadline exceeded before live sentiment call");
        }
        try {
            SentimentObservations payload = b.fetchObservations(request);
            Instant observedAt = clock.instant();
            int bytes = ToolPayloads.measure(objectMapper, payload, request.maxPayloadBytes());
            return ToolResult.ok(source(), payload, List.of(),
                    Freshness.of(observedAt, TTL),
                    new Confidence(0.9, "authoritative live sentiment observations"), bytes);
        } catch (RuntimeException e) {
            return ToolResult.error(source(), "live sentiment backend failed: " + e.getClass().getSimpleName());
        }
    }
}
