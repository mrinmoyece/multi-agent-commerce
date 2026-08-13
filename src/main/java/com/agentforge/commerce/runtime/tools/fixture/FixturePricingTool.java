package com.agentforge.commerce.runtime.tools.fixture;

import com.agentforge.commerce.runtime.contract.Citation;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Evidence;
import com.agentforge.commerce.runtime.contract.Freshness;
import com.agentforge.commerce.runtime.contract.Hashes;
import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.tools.PricingObservations;
import com.agentforge.commerce.runtime.tools.PricingTool;
import com.agentforge.commerce.runtime.tools.ToolPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, committed pricing fixture. Replaces the previous narrative-only
 * pricing extrapolation with grounded competitor/margin observations that carry
 * citations and a FIXTURE provenance label, so pricing recommendations trace to
 * concrete SKUs and are never presented as live market data.
 */
@Component
@ConditionalOnProperty(name = "runtime.tools.mode", havingValue = "FIXTURE", matchIfMissing = true)
public class FixturePricingTool implements PricingTool {

    private static final SourceIdentity SOURCE =
            SourceIdentity.fixture("pricing-fixture", "Committed competitor/margin observations (fixture)");
    private static final Duration TTL = Duration.ofHours(3);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FixturePricingTool(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "pricing.fetch";
    }

    @Override
    public SourceIdentity source() {
        return SOURCE;
    }

    @Override
    public ToolResult<PricingObservations> fetch(ToolRequest request) {
        Instant observedAt = clock.instant();
        List<PricingObservations.Observation> observations = observations();
        PricingObservations payload = new PricingObservations(request.retailerId(), observedAt, observations);

        List<Citation> citations = new ArrayList<>();
        for (PricingObservations.Observation o : observations) {
            String snippet = "our=" + o.ourPrice() + " comp=" + o.competitorPrice() + " margin=" + o.marginPct() + "%";
            citations.add(new Citation(SOURCE.id(), o.sku(), snippet,
                    Hashes.shortSha256(o.sku() + snippet, 16), observedAt));
        }
        Evidence evidence = new Evidence(
                "pricing-observations",
                SOURCE,
                citations,
                observations.size() + " SKUs with competitor and margin observations.",
                Freshness.of(observedAt, TTL),
                new Confidence(0.85, "exact competitor/margin values from a committed fixture"));

        int bytes = ToolPayloads.measure(objectMapper, payload, request.maxPayloadBytes());
        return ToolResult.ok(SOURCE, payload, List.of(evidence),
                Freshness.of(observedAt, TTL),
                new Confidence(0.85, "deterministic fixture observations"), bytes);
    }

    private static List<PricingObservations.Observation> observations() {
        List<PricingObservations.Observation> list = new ArrayList<>();
        list.add(obs("SKU-0001", "1.40", "1.25", 18));
        list.add(obs("SKU-0006", "6.50", "6.90", 22));
        list.add(obs("SKU-0009", "2.60", "2.20", 12));
        list.add(obs("SKU-0010", "1.10", "0.95", 9));
        list.add(obs("SKU-0011", "5.40", "4.80", 15));
        list.add(obs("SKU-0016", "3.00", "3.40", 28));
        list.add(obs("SKU-0019", "4.80", "4.20", 11));
        list.add(obs("SKU-0020", "1.50", "1.35", 8));
        return list;
    }

    private static PricingObservations.Observation obs(String sku, String our, String comp, int margin) {
        return new PricingObservations.Observation(sku, new BigDecimal(our), new BigDecimal(comp), margin);
    }
}
