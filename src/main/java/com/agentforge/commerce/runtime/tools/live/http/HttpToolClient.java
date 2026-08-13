package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.AuthType;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Endpoint;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared, bounded HTTP executor for one live adapter. Every outbound call passes
 * through the same safety controls so an individual backend implementation cannot
 * accidentally weaken them:
 *
 * <ul>
 *   <li>HTTPS-only, host-allowlisted URLs (re-checked per request — no SSRF);</li>
 *   <li>connect and read deadlines enforced by the JDK client;</li>
 *   <li>bounded response reads that abort before an oversized body is buffered;</li>
 *   <li>OAuth2 bearer / API-key / mTLS auth applied centrally;</li>
 *   <li>a correlation id propagated on every request;</li>
 *   <li>per-adapter retry (transient only) wrapped in a per-adapter circuit
 *       breaker, so one failing backend is isolated from the others;</li>
 *   <li>secret-safe {@link AdapterException}s — never a URL, token or body.</li>
 * </ul>
 */
public final class HttpToolClient {

    private static final Logger LOG = LoggerFactory.getLogger(HttpToolClient.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final String adapterName;
    private final HttpClient httpClient;
    private final Endpoint endpoint;
    private final OAuth2TokenProvider tokenProvider;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public HttpToolClient(String adapterName, HttpClient httpClient, Endpoint endpoint,
                          OAuth2TokenProvider tokenProvider, CircuitBreaker circuitBreaker, Retry retry) {
        this.adapterName = adapterName;
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.tokenProvider = tokenProvider;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    /**
     * Perform a bounded GET for the given query parameters and return the raw JSON
     * body as a string. Retries transient failures and trips the circuit breaker
     * on repeated transient failures.
     *
     * @throws AdapterException (secret-safe) on any failure
     */
    public String getJson(Map<String, String> query, String correlationId) {
        Supplier<String> call = () -> doGet(query, correlationId);
        Supplier<String> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, call));
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            throw new AdapterException(AdapterException.Kind.CIRCUIT_OPEN, adapterName, e);
        }
    }

    private String doGet(Map<String, String> query, String correlationId) {
        URI uri = buildUri(query);
        LiveAdapterValidator.requireAllowed(adapterName, uri, endpoint.getAllowedHosts());

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(endpoint.getReadTimeout())
                .header("Accept", "application/json")
                .header(CORRELATION_HEADER, correlationId)
                .GET();
        applyAuth(builder);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AdapterException(AdapterException.Kind.TIMEOUT, adapterName, e);
        } catch (java.io.InterruptedIOException e) {
            Thread.currentThread().interrupt();
            throw new AdapterException(AdapterException.Kind.TIMEOUT, adapterName, e);
        } catch (IOException e) {
            throw new AdapterException(AdapterException.Kind.TRANSPORT, adapterName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdapterException(AdapterException.Kind.TRANSPORT, adapterName, e);
        }

        int status = response.statusCode();
        String body = readBounded(response);
        return mapStatus(status, body);
    }

    private String mapStatus(int status, String body) {
        if (status == 200) {
            return body;
        }
        if (status == 401 || status == 403) {
            throw new AdapterException(AdapterException.Kind.AUTH, adapterName);
        }
        if (status == 429 || status >= 500) {
            LOG.debug("adapter {} transient upstream status {}", adapterName, status);
            throw new AdapterException(AdapterException.Kind.TRANSPORT, adapterName);
        }
        throw new AdapterException(AdapterException.Kind.MALFORMED, adapterName);
    }

    private String readBounded(HttpResponse<InputStream> response) {
        int max = endpoint.getMaxResponseBytes();
        try (InputStream in = response.body()) {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > max) {
                    throw new AdapterException(AdapterException.Kind.OVERSIZED, adapterName);
                }
                sink.write(buffer, 0, read);
            }
            return sink.toString(StandardCharsets.UTF_8);
        } catch (AdapterException e) {
            throw e;
        } catch (IOException e) {
            throw new AdapterException(AdapterException.Kind.TRANSPORT, adapterName, e);
        }
    }

    private URI buildUri(Map<String, String> query) {
        StringBuilder sb = new StringBuilder(endpoint.getBaseUrl());
        if (endpoint.getBaseUrl().endsWith("/") && endpoint.getPath().startsWith("/")) {
            sb.setLength(sb.length() - 1);
        }
        sb.append(endpoint.getPath());
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            sb.append(first ? '?' : '&');
            first = false;
            sb.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        try {
            return new URI(sb.toString());
        } catch (java.net.URISyntaxException e) {
            throw new AdapterException(AdapterException.Kind.FORBIDDEN_HOST, adapterName, e);
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        AuthType type = endpoint.getAuth().getType();
        if (type == AuthType.OAUTH2_CLIENT_CREDENTIALS && tokenProvider != null) {
            builder.header("Authorization", "Bearer " + tokenProvider.bearerToken());
        } else if (type == AuthType.API_KEY) {
            builder.header(endpoint.getAuth().getApiKeyHeader(), endpoint.getAuth().getApiKeyValue());
        }
        // MTLS carries identity at the TLS layer (client key store), so no header is added here.
    }
}
