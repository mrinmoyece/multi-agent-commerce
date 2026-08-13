package com.agentforge.commerce.runtime.tools.live.http;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Externalised configuration for the live production tool adapters, bound from the
 * {@code runtime.tools.live.*} namespace. Nothing here has a "production default":
 * when {@code runtime.tools.mode=LIVE} the {@link LiveAdapterValidator} rejects any
 * endpoint whose URL, allowlist, deadlines, size ceilings, pagination bounds,
 * freshness ceiling, or authentication is missing or unsafe, so a live deployment
 * cannot silently start against an unconfigured or plaintext backend.
 */
@ConfigurationProperties(prefix = "runtime.tools.live")
@Getter
@Setter
public class LiveAdapterProperties {

    @NestedConfigurationProperty
    private Endpoint inventory = new Endpoint();

    @NestedConfigurationProperty
    private Endpoint pricing = new Endpoint();

    @NestedConfigurationProperty
    private Endpoint sentiment = new Endpoint();

    /** How the adapter authenticates to its backend. */
    public enum AuthType {
        /** OAuth2 client-credentials grant (bearer token, cached until near expiry). */
        OAUTH2_CLIENT_CREDENTIALS,
        /** Mutual TLS using a configured client key/trust store. */
        MTLS,
        /** A static API key injected as a request header from a secret. */
        API_KEY
    }

    /** Per-adapter HTTP endpoint, transport bounds, pagination and auth. */
    @Getter
    @Setter
    public static class Endpoint {
        /** Absolute HTTPS base URL of the backend (e.g. {@code https://wms.internal.example.com}). */
        private String baseUrl = "";
        /** Request path appended to {@code baseUrl} (e.g. {@code /api/v1/inventory}). */
        private String path = "";
        /** Hosts the adapter is permitted to talk to; the {@code baseUrl} host must be a member. */
        private List<String> allowedHosts = new ArrayList<>();
        /** Human-readable description of the backing system of record (never a secret/URL). */
        private String systemOfRecord = "";
        /** TCP connect deadline. */
        private Duration connectTimeout = Duration.ofSeconds(2);
        /** Per-request read deadline. */
        private Duration readTimeout = Duration.ofSeconds(5);
        /** Hard ceiling on a single response body, in bytes. */
        private int maxResponseBytes = 64 * 1024;
        /** Page size requested from the backend. */
        private int pageSize = 100;
        /** Maximum number of pages the adapter will assemble before failing closed. */
        private int maxPages = 10;
        /** Maximum age of upstream evidence before it is rejected as stale. */
        private Duration maxStaleness = Duration.ofMinutes(15);

        @NestedConfigurationProperty
        private Auth auth = new Auth();
    }

    /** Authentication configuration for an adapter. No secret has a default value. */
    @Getter
    @Setter
    public static class Auth {
        /** Selected authentication mechanism. */
        private AuthType type = AuthType.OAUTH2_CLIENT_CREDENTIALS;

        // ── OAuth2 client credentials ──
        /** HTTPS token endpoint of the authorization server. */
        private String tokenUrl = "";
        /** OAuth2 client identifier. */
        private String clientId = "";
        /** OAuth2 client secret (supplied from a secret store; never committed). */
        private String clientSecret = "";
        /** Requested scope, if any. */
        private String scope = "";

        // ── API key ──
        /** Header name to carry the API key (e.g. {@code X-Api-Key}). */
        private String apiKeyHeader = "";
        /** API key value (supplied from a secret store; never committed). */
        private String apiKeyValue = "";

        // ── Mutual TLS ──
        /** Filesystem path to the client key store (PKCS12). */
        private String keyStorePath = "";
        /** Client key store password (supplied from a secret store). */
        private String keyStorePassword = "";
        /** Filesystem path to the trust store (PKCS12) validating the backend certificate. */
        private String trustStorePath = "";
        /** Trust store password (supplied from a secret store). */
        private String trustStorePassword = "";
    }
}
