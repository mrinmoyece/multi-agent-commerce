package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.tools.live.InventoryBackend;
import com.agentforge.commerce.runtime.tools.live.PricingBackend;
import com.agentforge.commerce.runtime.tools.live.SentimentBackend;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.AuthType;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Endpoint;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the concrete live production adapters when {@code runtime.tools.mode=LIVE}.
 *
 * <p>Each backend bean is built only after {@link LiveAdapterValidator} has proven
 * its endpoint configuration is safe (absolute HTTPS, allowlisted host, bounded
 * deadlines/sizes/pagination/freshness, complete auth). Because bean creation runs
 * at startup, an invalid LIVE configuration aborts application boot rather than
 * degrading silently — the "no fake production defaults; fail fast" guarantee. When
 * a backend bean is present the matching {@code Live*Tool} uses it; when validation
 * fails no bean is created and the tool fails closed.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "runtime.tools.mode", havingValue = "LIVE")
@EnableConfigurationProperties(LiveAdapterProperties.class)
public class LiveAdapterConfig {

    @Bean
    public InventoryBackend httpInventoryBackend(LiveAdapterProperties props, ObjectMapper mapper, Clock clock) {
        Endpoint endpoint = props.getInventory();
        LiveAdapterValidator.validateEndpoint(HttpInventoryBackend.ADAPTER, endpoint);
        Paginator<InventoryItemDto> paginator = new Paginator<>(
                HttpInventoryBackend.ADAPTER, client(HttpInventoryBackend.ADAPTER, endpoint, mapper, clock),
                endpoint, strict(mapper), clock, InventoryItemDto.class, HttpInventoryBackend.requiredFields());
        return new HttpInventoryBackend(paginator, systemOfRecord(endpoint, "live inventory system of record"));
    }

    @Bean
    public PricingBackend httpPricingBackend(LiveAdapterProperties props, ObjectMapper mapper, Clock clock) {
        Endpoint endpoint = props.getPricing();
        LiveAdapterValidator.validateEndpoint(HttpPricingBackend.ADAPTER, endpoint);
        Paginator<PricingItemDto> paginator = new Paginator<>(
                HttpPricingBackend.ADAPTER, client(HttpPricingBackend.ADAPTER, endpoint, mapper, clock),
                endpoint, strict(mapper), clock, PricingItemDto.class, HttpPricingBackend.requiredFields());
        return new HttpPricingBackend(paginator, systemOfRecord(endpoint, "live pricing intelligence system"));
    }

    @Bean
    public SentimentBackend httpSentimentBackend(LiveAdapterProperties props, ObjectMapper mapper, Clock clock) {
        Endpoint endpoint = props.getSentiment();
        LiveAdapterValidator.validateEndpoint(HttpSentimentBackend.ADAPTER, endpoint);
        Paginator<SentimentItemDto> paginator = new Paginator<>(
                HttpSentimentBackend.ADAPTER, client(HttpSentimentBackend.ADAPTER, endpoint, mapper, clock),
                endpoint, strict(mapper), clock, SentimentItemDto.class, HttpSentimentBackend.requiredFields());
        return new HttpSentimentBackend(paginator, systemOfRecord(endpoint, "live customer-sentiment system"));
    }

    private static HttpToolClient client(String name, Endpoint endpoint, ObjectMapper mapper, Clock clock) {
        HttpClient http = AdapterHttpClients.build(name, endpoint);
        OAuth2TokenProvider tokenProvider = endpoint.getAuth().getType() == AuthType.OAUTH2_CLIENT_CREDENTIALS
                ? new OAuth2TokenProvider(name, endpoint.getAuth(), http, mapper, clock, endpoint.getReadTimeout())
                : null;
        CircuitBreaker circuitBreaker = AdapterResilience.circuitBreaker(name);
        Retry retry = AdapterResilience.retry(name);
        return new HttpToolClient(name, http, endpoint, tokenProvider, circuitBreaker, retry);
    }

    /** A strict view of the shared mapper: unknown fields and null primitives fail closed. */
    private static ObjectMapper strict(ObjectMapper mapper) {
        return mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    private static String systemOfRecord(Endpoint endpoint, String fallback) {
        String configured = endpoint.getSystemOfRecord();
        return configured == null || configured.isBlank() ? fallback : configured;
    }
}
