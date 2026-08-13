package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and caches an OAuth2 client-credentials bearer token for one adapter.
 *
 * <p>The token is requested from the configured HTTPS token endpoint with an
 * HTTP Basic-authenticated {@code client_credentials} grant, parsed strictly, and
 * cached in memory until a safety margin before its expiry. Failures are surfaced
 * as a secret-safe {@link AdapterException} of kind {@code AUTH}; neither the
 * client secret nor the token is ever logged or placed in an exception message.</p>
 */
public final class OAuth2TokenProvider {

    /** Refresh the token this long before its stated expiry to avoid edge races. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);
    /** Fallback lifetime when the authorization server omits {@code expires_in}. */
    private static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(5);
    private static final int MAX_TOKEN_RESPONSE_BYTES = 16 * 1024;

    private final String adapterName;
    private final Auth auth;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration requestTimeout;
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public OAuth2TokenProvider(String adapterName, Auth auth, HttpClient httpClient,
                               ObjectMapper objectMapper, Clock clock, Duration requestTimeout) {
        this.adapterName = adapterName;
        this.auth = auth;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.requestTimeout = requestTimeout;
    }

    /** @return a currently-valid bearer token, fetching a fresh one when needed. */
    public String bearerToken() {
        CachedToken current = cache.get();
        Instant now = clock.instant();
        if (current != null && now.isBefore(current.expiresAt().minus(EXPIRY_MARGIN))) {
            return current.token();
        }
        CachedToken fresh = requestToken();
        cache.set(fresh);
        return fresh.token();
    }

    private CachedToken requestToken() {
        String form = "grant_type=client_credentials";
        if (auth.getScope() != null && !auth.getScope().isBlank()) {
            form += "&scope=" + java.net.URLEncoder.encode(auth.getScope(), StandardCharsets.UTF_8);
        }
        String basic = java.util.Base64.getEncoder().encodeToString(
                (auth.getClientId() + ":" + auth.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(auth.getTokenUrl()))
                .timeout(requestTimeout)
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new AdapterException(AdapterException.Kind.AUTH, adapterName);
            }
            byte[] body = response.body();
            if (body.length > MAX_TOKEN_RESPONSE_BYTES) {
                throw new AdapterException(AdapterException.Kind.AUTH, adapterName);
            }
            JsonNode json = objectMapper.readTree(body);
            JsonNode token = json.get("access_token");
            if (token == null || !token.isTextual() || token.asText().isBlank()) {
                throw new AdapterException(AdapterException.Kind.AUTH, adapterName);
            }
            long expiresIn = json.has("expires_in") && json.get("expires_in").isNumber()
                    ? json.get("expires_in").asLong() : DEFAULT_LIFETIME.toSeconds();
            Instant expiresAt = clock.instant().plusSeconds(Math.max(expiresIn, EXPIRY_MARGIN.toSeconds() + 1));
            return new CachedToken(token.asText(), expiresAt);
        } catch (AdapterException e) {
            throw e;
        } catch (java.io.InterruptedIOException e) {
            Thread.currentThread().interrupt();
            throw new AdapterException(AdapterException.Kind.TIMEOUT, adapterName, e);
        } catch (java.io.IOException e) {
            throw new AdapterException(AdapterException.Kind.AUTH, adapterName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdapterException(AdapterException.Kind.AUTH, adapterName, e);
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
