package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Endpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Bounded, schema-validating pagination driver shared by the live adapters. It
 * walks a backend's paged {@code items} envelope up to a hard page ceiling,
 * strictly validates every page against the adapter schema, enforces the freshness
 * ceiling on the upstream {@code observedAt}, and fails closed — never returns a
 * partially-assembled result as if it were complete.
 *
 * <p>The response envelope contract each backend must satisfy is:
 * {@code {"retailerId":..., "observedAt": <iso-8601>, "hasMore": <bool>, "items": [ ... ]}}.
 * Query parameters are entirely adapter-controlled (retailer id, page, size); no
 * caller- or model-supplied value is ever used to form a URL, so there is no SSRF
 * path.</p>
 *
 * @param <I> the strict item DTO type for one adapter
 */
public final class Paginator<I> {

    private static final String CORRELATION_MDC_KEY = "correlationId";

    private final String adapterName;
    private final HttpToolClient client;
    private final Endpoint endpoint;
    private final ObjectMapper strictMapper;
    private final Clock clock;
    private final Class<I> itemType;
    private final List<String> requiredItemFields;

    public Paginator(String adapterName, HttpToolClient client, Endpoint endpoint,
                     ObjectMapper strictMapper, Clock clock, Class<I> itemType,
                     List<String> requiredItemFields) {
        this.adapterName = adapterName;
        this.client = client;
        this.endpoint = endpoint;
        this.strictMapper = strictMapper;
        this.clock = clock;
        this.itemType = itemType;
        this.requiredItemFields = List.copyOf(requiredItemFields);
    }

    /** Assembled, validated, fresh items plus the oldest observation instant. */
    public record Result<I>(List<I> items, Instant observedAt) {
    }

    /**
     * Fetch and validate every page for the request, honouring the request deadline
     * and the adapter's freshness ceiling.
     *
     * @throws AdapterException (secret-safe) on any failure
     */
    public Result<I> fetchAll(ToolRequest request) {
        String correlationId = correlationId();
        List<I> items = new ArrayList<>();
        Instant oldestObservedAt = null;

        for (int page = 0; page < endpoint.getMaxPages(); page++) {
            if (request.isExpiredAt(clock.instant())) {
                throw new AdapterException(AdapterException.Kind.TIMEOUT, adapterName);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("retailerId", request.retailerId());
            query.put("page", Integer.toString(page));
            query.put("pageSize", Integer.toString(endpoint.getPageSize()));

            String body = client.getJson(query, correlationId);
            JsonNode root = parse(body);
            Instant observedAt = requireFreshObservedAt(root);
            oldestObservedAt = oldestObservedAt == null || observedAt.isBefore(oldestObservedAt)
                    ? observedAt : oldestObservedAt;

            JsonNode itemsNode = root.get("items");
            if (itemsNode == null || !itemsNode.isArray()) {
                throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName);
            }
            for (JsonNode itemNode : itemsNode) {
                items.add(toItem(itemNode));
                if (items.size() > endpoint.getMaxPages() * (long) endpoint.getPageSize()) {
                    throw new AdapterException(AdapterException.Kind.PARTIAL, adapterName);
                }
            }

            boolean hasMore = root.path("hasMore").asBoolean(false);
            if (!hasMore) {
                return new Result<>(List.copyOf(items), oldestObservedAt);
            }
        }
        // Reached the page ceiling while the backend still reports more data:
        // refuse to present an incomplete snapshot as authoritative.
        throw new AdapterException(AdapterException.Kind.PARTIAL, adapterName);
    }

    private JsonNode parse(String body) {
        try {
            return strictMapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName, e);
        }
    }

    private Instant requireFreshObservedAt(JsonNode root) {
        JsonNode observed = root.get("observedAt");
        if (observed == null || !observed.isTextual()) {
            throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName);
        }
        Instant observedAt;
        try {
            observedAt = Instant.parse(observed.asText());
        } catch (DateTimeParseException e) {
            throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName, e);
        }
        Instant now = clock.instant();
        if (observedAt.isAfter(now.plusSeconds(60))) {
            throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName);
        }
        if (observedAt.isBefore(now.minus(endpoint.getMaxStaleness()))) {
            throw new AdapterException(AdapterException.Kind.STALE, adapterName);
        }
        return observedAt;
    }

    private I toItem(JsonNode itemNode) {
        if (itemNode == null || !itemNode.isObject()) {
            throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName);
        }
        for (String field : requiredItemFields) {
            JsonNode value = itemNode.get(field);
            if (value == null || value.isNull()) {
                throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName);
            }
        }
        try {
            return strictMapper.treeToValue(itemNode, itemType);
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName, e);
        }
    }

    private static String correlationId() {
        String existing = MDC.get(CORRELATION_MDC_KEY);
        return existing != null && !existing.isBlank() ? existing : java.util.UUID.randomUUID().toString();
    }
}
