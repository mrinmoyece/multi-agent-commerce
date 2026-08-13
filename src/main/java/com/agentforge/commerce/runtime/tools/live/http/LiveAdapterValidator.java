package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Auth;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Endpoint;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;

/**
 * Validates live-adapter configuration and outbound URLs. Two responsibilities:
 *
 * <ul>
 *   <li><b>Fail-fast startup</b>: {@link #validateEndpoint} rejects any endpoint
 *       whose URL is not absolute HTTPS, whose host is not on its own allowlist,
 *       whose deadlines/sizes/pagination are out of bounds, or whose auth block is
 *       incomplete for the selected mechanism. LIVE mode therefore cannot start on
 *       a plaintext, unauthenticated, or half-configured backend.</li>
 *   <li><b>Per-request egress guard</b>: {@link #requireAllowed} re-checks every
 *       resolved URL against the HTTPS + host-allowlist invariant, closing off SSRF
 *       even if a path or query were ever influenced by data.</li>
 * </ul>
 */
public final class LiveAdapterValidator {

    /** Lowest sane per-request read deadline; anything shorter is a misconfiguration. */
    private static final Duration MIN_TIMEOUT = Duration.ofMillis(50);
    /** Highest read deadline we allow so a backend cannot pin a worker indefinitely. */
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);
    /** Absolute ceiling on a single response body across all adapters. */
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    /** Absolute ceiling on assembled pages so pagination cannot loop unbounded. */
    private static final int MAX_PAGES = 100;

    private LiveAdapterValidator() {
    }

    /**
     * Validate a single adapter endpoint for LIVE operation.
     *
     * @throws AdapterException with {@link AdapterException.Kind#CONFIG} if invalid
     */
    public static void validateEndpoint(String adapterName, Endpoint endpoint) {
        if (endpoint == null) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        URI base = parseHttps(adapterName, endpoint.getBaseUrl());
        if (endpoint.getAllowedHosts() == null || endpoint.getAllowedHosts().isEmpty()) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        if (!hostAllowed(base.getHost(), endpoint.getAllowedHosts())) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        if (endpoint.getPath() == null || endpoint.getPath().isBlank() || !endpoint.getPath().startsWith("/")) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        requireBoundedTimeout(adapterName, endpoint.getConnectTimeout());
        requireBoundedTimeout(adapterName, endpoint.getReadTimeout());
        if (endpoint.getMaxResponseBytes() <= 0 || endpoint.getMaxResponseBytes() > MAX_RESPONSE_BYTES) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        if (endpoint.getPageSize() <= 0 || endpoint.getPageSize() > 10_000) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        if (endpoint.getMaxPages() <= 0 || endpoint.getMaxPages() > MAX_PAGES) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        if (endpoint.getMaxStaleness() == null || endpoint.getMaxStaleness().isNegative()
                || endpoint.getMaxStaleness().isZero()) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        validateAuth(adapterName, endpoint.getAuth());
    }

    private static void validateAuth(String adapterName, Auth auth) {
        if (auth == null || auth.getType() == null) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        switch (auth.getType()) {
            case OAUTH2_CLIENT_CREDENTIALS -> {
                parseHttps(adapterName, auth.getTokenUrl());
                requireSecret(adapterName, auth.getClientId());
                requireSecret(adapterName, auth.getClientSecret());
            }
            case API_KEY -> {
                requireSecret(adapterName, auth.getApiKeyHeader());
                requireSecret(adapterName, auth.getApiKeyValue());
            }
            case MTLS -> {
                requireSecret(adapterName, auth.getKeyStorePath());
                requireSecret(adapterName, auth.getKeyStorePassword());
                requireSecret(adapterName, auth.getTrustStorePath());
                requireSecret(adapterName, auth.getTrustStorePassword());
            }
            default -> throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
    }

    /**
     * Re-assert the HTTPS + allowlist invariant for a fully-resolved request URL.
     *
     * @throws AdapterException with {@link AdapterException.Kind#FORBIDDEN_HOST} if disallowed
     */
    public static void requireAllowed(String adapterName, URI uri, java.util.List<String> allowedHosts) {
        if (uri == null || uri.getScheme() == null
                || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))
                || uri.getHost() == null) {
            throw new AdapterException(AdapterException.Kind.FORBIDDEN_HOST, adapterName);
        }
        if (!hostAllowed(uri.getHost(), allowedHosts)) {
            throw new AdapterException(AdapterException.Kind.FORBIDDEN_HOST, adapterName);
        }
    }

    private static URI parseHttps(String adapterName, String value) {
        if (value == null || value.isBlank()) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        if (uri.getScheme() == null || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))
                || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
        return uri;
    }

    private static boolean hostAllowed(String host, java.util.List<String> allowedHosts) {
        if (host == null) {
            return false;
        }
        String candidate = host.toLowerCase(Locale.ROOT);
        return allowedHosts.stream()
                .filter(h -> h != null && !h.isBlank())
                .map(h -> h.toLowerCase(Locale.ROOT))
                .anyMatch(candidate::equals);
    }

    private static void requireBoundedTimeout(String adapterName, Duration timeout) {
        if (timeout == null || timeout.compareTo(MIN_TIMEOUT) < 0 || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
    }

    private static void requireSecret(String adapterName, String value) {
        if (value == null || value.isBlank()) {
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName);
        }
    }
}
