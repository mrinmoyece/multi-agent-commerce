package com.agentforge.commerce.runtime.tools.fixture;

import com.agentforge.commerce.runtime.contract.Citation;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Evidence;
import com.agentforge.commerce.runtime.contract.Freshness;
import com.agentforge.commerce.runtime.contract.Hashes;
import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.tools.SentimentObservations;
import com.agentforge.commerce.runtime.tools.SentimentTool;
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
 * Deterministic, committed sentiment fixture. Replaces the previous narrative-only
 * sentiment extrapolation with grounded per-topic aggregates that carry citations
 * and a FIXTURE provenance label, so sentiment themes trace to concrete topics
 * and are never presented as a live social/reviews feed.
 */
@Component
@ConditionalOnProperty(name = "runtime.tools.mode", havingValue = "FIXTURE", matchIfMissing = true)
public class FixtureSentimentTool implements SentimentTool {

    private static final SourceIdentity SOURCE =
            SourceIdentity.fixture("sentiment-fixture", "Committed sentiment aggregates (fixture)");
    private static final Duration TTL = Duration.ofHours(12);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FixtureSentimentTool(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "sentiment.fetch";
    }

    @Override
    public SourceIdentity source() {
        return SOURCE;
    }

    @Override
    public ToolResult<SentimentObservations> fetch(ToolRequest request) {
        Instant observedAt = clock.instant();
        List<SentimentObservations.Signal> signals = signals();
        SentimentObservations payload = new SentimentObservations(request.retailerId(), observedAt, signals);

        List<Citation> citations = new ArrayList<>();
        for (SentimentObservations.Signal s : signals) {
            String snippet = "pos=" + s.positiveCount() + " neg=" + s.negativeCount()
                    + " returns=" + s.returnRatePct() + "% n=" + s.sampleSize();
            citations.add(new Citation(SOURCE.id(), s.topic(), snippet,
                    Hashes.shortSha256(s.topic() + snippet, 16), observedAt));
        }
        Evidence evidence = new Evidence(
                "sentiment-observations",
                SOURCE,
                citations,
                signals.size() + " topics aggregated from reviews and support signals.",
                Freshness.of(observedAt, TTL),
                new Confidence(0.8, "aggregated counts from a committed fixture"));

        int bytes = ToolPayloads.measure(objectMapper, payload, request.maxPayloadBytes());
        return ToolResult.ok(SOURCE, payload, List.of(evidence),
                Freshness.of(observedAt, TTL),
                new Confidence(0.8, "deterministic fixture aggregates"), bytes);
    }

    private static List<SentimentObservations.Signal> signals() {
        List<SentimentObservations.Signal> list = new ArrayList<>();
        list.add(new SentimentObservations.Signal("Delivery", 820, 140, 3, 960));
        list.add(new SentimentObservations.Signal("Produce-Freshness", 240, 410, 9, 650));
        list.add(new SentimentObservations.Signal("Checkout-App", 510, 120, 0, 630));
        list.add(new SentimentObservations.Signal("Packaging", 180, 260, 6, 440));
        list.add(new SentimentObservations.Signal("Value-For-Money", 600, 200, 2, 800));
        list.add(new SentimentObservations.Signal("Customer-Service", 330, 290, 4, 620));
        return list;
    }
}
