package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.PricingObservations;
import com.agentforge.commerce.runtime.tools.live.PricingBackend;
import java.util.List;

/**
 * Production HTTP adapter to a live competitor-pricing / margin intelligence
 * backend. Assembles bounded, schema-validated, fresh observations over the shared
 * {@link HttpToolClient} and maps the strict wire DTOs onto the domain
 * {@link PricingObservations}.
 */
public final class HttpPricingBackend implements PricingBackend {

    static final String ADAPTER = "pricing";
    private static final List<String> REQUIRED_FIELDS =
            List.of("sku", "ourPrice", "competitorPrice", "marginPct");

    private final Paginator<PricingItemDto> paginator;
    private final String systemOfRecord;

    public HttpPricingBackend(Paginator<PricingItemDto> paginator, String systemOfRecord) {
        this.paginator = paginator;
        this.systemOfRecord = systemOfRecord;
    }

    @Override
    public PricingObservations fetchObservations(ToolRequest request) {
        Paginator.Result<PricingItemDto> result = paginator.fetchAll(request);
        List<PricingObservations.Observation> observations = result.items().stream()
                .map(dto -> new PricingObservations.Observation(
                        dto.sku(), dto.ourPrice(), dto.competitorPrice(), dto.marginPct()))
                .toList();
        return new PricingObservations(request.retailerId(), result.observedAt(), observations);
    }

    @Override
    public String systemOfRecord() {
        return systemOfRecord;
    }

    static List<String> requiredFields() {
        return REQUIRED_FIELDS;
    }
}
