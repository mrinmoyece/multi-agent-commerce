package com.agentforge.commerce.runtime.tools.live.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.InventorySnapshot;
import com.agentforge.commerce.runtime.tools.SentimentObservations;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Auth;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.AuthType;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Endpoint;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deterministic contract tests for the live HTTP adapters against a WireMock
 * server. They exercise the full transport path (bounded reads, deadlines, auth
 * headers, correlation id, retry, circuit breaking, schema validation, freshness,
 * pagination) and prove each failure mode maps to the right secret-safe
 * {@link AdapterException.Kind}. No unrestricted URL fetching is possible: every URL
 * is built from the allowlisted endpoint configuration.
 */
class LiveAdapterContractTest {

    private static final Instant NOW = Instant.parse("2024-06-01T00:00:00Z");
    private static final String INVENTORY_PATH = "/api/v1/inventory";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private WireMockServer server;
    private int port;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicHttpsPort().httpDisabled(true));
        server.start();
        port = server.httpsPort();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    // ── configuration & helpers ──────────────────────────────────────────────

    private Endpoint endpoint(String path) {
        Endpoint e = new Endpoint();
        e.setBaseUrl("https://localhost:" + port);
        e.setPath(path);
        e.setAllowedHosts(List.of("localhost"));
        e.setConnectTimeout(Duration.ofSeconds(2));
        // Generous default so slow/loaded CI runners never spuriously time out on a
        // localhost round trip; the timeout path is exercised explicitly by
        // readTimeoutIsMapped() with its own tight budget against a fixed delay.
        e.setReadTimeout(Duration.ofSeconds(5));
        e.setMaxResponseBytes(64 * 1024);
        e.setPageSize(2);
        e.setMaxPages(5);
        e.setMaxStaleness(Duration.ofMinutes(15));
        Auth auth = new Auth();
        auth.setType(AuthType.API_KEY);
        auth.setApiKeyHeader("X-Api-Key");
        auth.setApiKeyValue("secret-key");
        e.setAuth(auth);
        return e;
    }

    private static ObjectMapper strictMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    private HttpClient trustAllClient(Endpoint endpoint) {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                    .connectTimeout(endpoint.getConnectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .version(HttpClient.Version.HTTP_1_1)
                    .sslContext(ctx)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpInventoryBackend inventoryBackend(Endpoint endpoint, Retry retry, CircuitBreaker breaker) {
        HttpToolClient client = new HttpToolClient(
                "inventory", trustAllClient(endpoint), endpoint, null, breaker, retry);
        Paginator<InventoryItemDto> paginator = new Paginator<>(
                "inventory", client, endpoint, strictMapper(), clock,
                InventoryItemDto.class, HttpInventoryBackend.requiredFields());
        return new HttpInventoryBackend(paginator, "test WMS");
    }

    private HttpInventoryBackend inventoryBackend(Endpoint endpoint) {
        return inventoryBackend(endpoint, AdapterResilience.retry("inventory", 1, Duration.ofMillis(1)),
                AdapterResilience.circuitBreaker("inventory"));
    }

    private ToolRequest request() {
        return ToolRequest.of("inventory.fetch", "retailer-1", NOW.plusSeconds(30));
    }

    private static String inventoryPage(String observedAt, boolean hasMore, String... skus) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < skus.length; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    {"sku":"%s","name":"Item %s","category":"Cat","currentQty":10,"daysOfStockRemaining":3,"status":"CRITICAL"}
                    """.formatted(skus[i], skus[i]));
        }
        return """
                {"retailerId":"retailer-1","observedAt":"%s","hasMore":%s,"items":[%s]}
                """.formatted(observedAt, hasMore, items);
    }

    // ── success & security controls ──────────────────────────────────────────

    @Test
    void successReturnsMappedSnapshotAndSendsAuthAndCorrelationHeaders() {
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(inventoryPage(NOW.toString(), false, "SKU-1", "SKU-2"))));

        InventorySnapshot snapshot = inventoryBackend(endpoint(INVENTORY_PATH)).fetchSnapshot(request());

        assertThat(snapshot.items()).hasSize(2);
        assertThat(snapshot.items().get(0).sku()).isEqualTo("SKU-1");
        assertThat(snapshot.observedAt()).isEqualTo(NOW);
        server.verify(getRequestedFor(urlPathEqualTo(INVENTORY_PATH))
                .withHeader("X-Api-Key", equalTo("secret-key"))
                .withHeader("X-Correlation-Id", matching(".+"))
                .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void assemblesMultiplePagesInOrder() {
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH)).withQueryParam("page", equalTo("0"))
                .willReturn(aResponse().withBody(inventoryPage(NOW.toString(), true, "SKU-1", "SKU-2"))));
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH)).withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withBody(inventoryPage(NOW.toString(), false, "SKU-3"))));

        InventorySnapshot snapshot = inventoryBackend(endpoint(INVENTORY_PATH)).fetchSnapshot(request());

        assertThat(snapshot.items()).extracting(InventorySnapshot.Item::sku)
                .containsExactly("SKU-1", "SKU-2", "SKU-3");
    }

    // ── failure modes ────────────────────────────────────────────────────────

    @Test
    void authFailureIsMappedAndNotRetried() {
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"nope\"}")));

        assertKind(AdapterException.Kind.AUTH, endpoint(INVENTORY_PATH));
        server.verify(1, getRequestedFor(urlPathEqualTo(INVENTORY_PATH)));
    }

    @Test
    void readTimeoutIsMapped() {
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withFixedDelay(1500)
                        .withBody(inventoryPage(NOW.toString(), false, "SKU-1"))));

        Endpoint endpoint = endpoint(INVENTORY_PATH);
        // Tight per-request budget well below the server's fixed delay so the
        // timeout fires deterministically regardless of runner speed.
        endpoint.setReadTimeout(Duration.ofMillis(300));
        assertKind(AdapterException.Kind.TIMEOUT, endpoint);
    }

    @Test
    void oversizedBodyIsRejectedBeforeBuffering() {
        Endpoint endpoint = endpoint(INVENTORY_PATH);
        endpoint.setMaxResponseBytes(40);
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withBody(inventoryPage(NOW.toString(), false, "SKU-1", "SKU-2"))));

        assertKind(AdapterException.Kind.OVERSIZED, endpoint);
    }

    @Test
    void unknownSchemaFieldIsRejected() {
        String body = """
                {"retailerId":"retailer-1","observedAt":"%s","hasMore":false,
                 "items":[{"sku":"SKU-1","name":"n","category":"c","currentQty":1,
                           "daysOfStockRemaining":2,"status":"OK","rogueField":"x"}]}
                """.formatted(NOW.toString());
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH)).willReturn(aResponse().withBody(body)));

        assertKind(AdapterException.Kind.MALFORMED, endpoint(INVENTORY_PATH));
    }

    @Test
    void missingRequiredFieldIsRejected() {
        String body = """
                {"retailerId":"retailer-1","observedAt":"%s","hasMore":false,
                 "items":[{"sku":"SKU-1","name":"n","category":"c","currentQty":1,"daysOfStockRemaining":2}]}
                """.formatted(NOW.toString());
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH)).willReturn(aResponse().withBody(body)));

        assertKind(AdapterException.Kind.MALFORMED, endpoint(INVENTORY_PATH));
    }

    @Test
    void malformedJsonIsRejected() {
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withBody("{not json")));

        assertKind(AdapterException.Kind.MALFORMED, endpoint(INVENTORY_PATH));
    }

    @Test
    void staleEvidenceIsRejected() {
        String stale = NOW.minus(Duration.ofHours(1)).toString();
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withBody(inventoryPage(stale, false, "SKU-1"))));

        assertKind(AdapterException.Kind.STALE, endpoint(INVENTORY_PATH));
    }

    @Test
    void pagingBeyondCeilingFailsClosed() {
        Endpoint endpoint = endpoint(INVENTORY_PATH);
        endpoint.setMaxPages(2);
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withBody(inventoryPage(NOW.toString(), true, "SKU-1", "SKU-2"))));

        assertKind(AdapterException.Kind.PARTIAL, endpoint);
    }

    @Test
    void transientFailureIsRetriedThenSucceeds() {
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH)).inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH)).inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withBody(inventoryPage(NOW.toString(), false, "SKU-1"))));

        HttpInventoryBackend backend = inventoryBackend(endpoint(INVENTORY_PATH),
                AdapterResilience.retry("inventory", 3, Duration.ofMillis(5)),
                AdapterResilience.circuitBreaker("inventory"));
        InventorySnapshot snapshot = backend.fetchSnapshot(request());

        assertThat(snapshot.items()).hasSize(1);
        server.verify(2, getRequestedFor(urlPathEqualTo(INVENTORY_PATH)));
    }

    @Test
    void circuitOpensAfterRepeatedTransientFailures() {
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withStatus(503)));

        CircuitBreaker breaker = AdapterResilience.circuitBreaker(
                "inventory", 2, 50.0f, 2, Duration.ofSeconds(10));
        HttpInventoryBackend backend = inventoryBackend(endpoint(INVENTORY_PATH),
                AdapterResilience.retry("inventory", 1, Duration.ofMillis(1)), breaker);

        assertThatThrownBy(() -> backend.fetchSnapshot(request())).isInstanceOf(AdapterException.class);
        assertThatThrownBy(() -> backend.fetchSnapshot(request())).isInstanceOf(AdapterException.class);
        assertThatThrownBy(() -> backend.fetchSnapshot(request()))
                .isInstanceOf(AdapterException.class)
                .extracting(t -> ((AdapterException) t).kind())
                .isEqualTo(AdapterException.Kind.CIRCUIT_OPEN);
    }

    @Test
    void forbiddenHostIsBlockedAtRequestTime() {
        Endpoint endpoint = endpoint(INVENTORY_PATH);
        endpoint.setAllowedHosts(List.of("other.internal.example.com"));
        server.stubFor(get(urlPathEqualTo(INVENTORY_PATH))
                .willReturn(aResponse().withBody(inventoryPage(NOW.toString(), false, "SKU-1"))));

        assertKind(AdapterException.Kind.FORBIDDEN_HOST, endpoint);
        server.verify(0, getRequestedFor(urlPathEqualTo(INVENTORY_PATH)));
    }

    @Test
    void sentimentAdapterMapsAggregates() {
        String path = "/api/v1/sentiment";
        String body = """
                {"retailerId":"retailer-1","observedAt":"%s","hasMore":false,
                 "items":[{"topic":"delivery","positiveCount":10,"negativeCount":4,
                           "returnRatePct":3,"sampleSize":14}]}
                """.formatted(NOW.toString());
        server.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withBody(body)));

        Endpoint endpoint = endpoint(path);
        HttpToolClient client = new HttpToolClient(
                "sentiment", trustAllClient(endpoint), endpoint, null,
                AdapterResilience.circuitBreaker("sentiment"),
                AdapterResilience.retry("sentiment", 1, Duration.ofMillis(1)));
        Paginator<SentimentItemDto> paginator = new Paginator<>(
                "sentiment", client, endpoint, strictMapper(), clock,
                SentimentItemDto.class, HttpSentimentBackend.requiredFields());
        SentimentObservations observations =
                new HttpSentimentBackend(paginator, "test sentiment").fetchObservations(
                        ToolRequest.of("sentiment.fetch", "retailer-1", NOW.plusSeconds(30)));

        assertThat(observations.signals()).hasSize(1);
        assertThat(observations.signals().get(0).topic()).isEqualTo("delivery");
    }

    private void assertKind(AdapterException.Kind expected, Endpoint endpoint) {
        assertThatThrownBy(() -> inventoryBackend(endpoint).fetchSnapshot(request()))
                .isInstanceOf(AdapterException.class)
                .extracting(t -> ((AdapterException) t).kind())
                .isEqualTo(expected);
    }
}
