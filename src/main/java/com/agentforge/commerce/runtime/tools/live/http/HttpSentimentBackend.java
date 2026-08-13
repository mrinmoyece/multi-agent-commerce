package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.SentimentObservations;
import com.agentforge.commerce.runtime.tools.live.SentimentBackend;
import java.util.List;

/**
 * Production HTTP adapter to a live customer-sentiment backend (reviews, support,
 * social, returns). Assembles bounded, schema-validated, fresh topic aggregates
 * over the shared {@link HttpToolClient} and maps the strict wire DTOs onto the
 * domain {@link SentimentObservations}.
 */
public final class HttpSentimentBackend implements SentimentBackend {

    static final String ADAPTER = "sentiment";
    private static final List<String> REQUIRED_FIELDS =
            List.of("topic", "positiveCount", "negativeCount", "returnRatePct", "sampleSize");

    private final Paginator<SentimentItemDto> paginator;
    private final String systemOfRecord;

    public HttpSentimentBackend(Paginator<SentimentItemDto> paginator, String systemOfRecord) {
        this.paginator = paginator;
        this.systemOfRecord = systemOfRecord;
    }

    @Override
    public SentimentObservations fetchObservations(ToolRequest request) {
        Paginator.Result<SentimentItemDto> result = paginator.fetchAll(request);
        List<SentimentObservations.Signal> signals = result.items().stream()
                .map(dto -> new SentimentObservations.Signal(
                        dto.topic(), dto.positiveCount(), dto.negativeCount(),
                        dto.returnRatePct(), dto.sampleSize()))
                .toList();
        return new SentimentObservations(request.retailerId(), result.observedAt(), signals);
    }

    @Override
    public String systemOfRecord() {
        return systemOfRecord;
    }

    static List<String> requiredFields() {
        return REQUIRED_FIELDS;
    }
}
