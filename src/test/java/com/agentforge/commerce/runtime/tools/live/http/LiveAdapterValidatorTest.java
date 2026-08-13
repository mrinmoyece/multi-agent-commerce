package com.agentforge.commerce.runtime.tools.live.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Auth;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.AuthType;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Endpoint;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-fast configuration validation: LIVE mode must refuse to run against an
 * unsafe or half-configured backend. Each case proves a specific guard rejects a
 * misconfiguration with a secret-safe {@code CONFIG}/{@code FORBIDDEN_HOST} error.
 */
class LiveAdapterValidatorTest {

    private Endpoint validOauthEndpoint() {
        Endpoint e = new Endpoint();
        e.setBaseUrl("https://wms.internal.example.com");
        e.setPath("/api/v1/inventory");
        e.setAllowedHosts(List.of("wms.internal.example.com"));
        e.setConnectTimeout(Duration.ofSeconds(2));
        e.setReadTimeout(Duration.ofSeconds(5));
        e.setMaxResponseBytes(65536);
        e.setPageSize(100);
        e.setMaxPages(10);
        e.setMaxStaleness(Duration.ofMinutes(15));
        Auth auth = new Auth();
        auth.setType(AuthType.OAUTH2_CLIENT_CREDENTIALS);
        auth.setTokenUrl("https://auth.example.com/oauth2/token");
        auth.setClientId("client");
        auth.setClientSecret("secret");
        e.setAuth(auth);
        return e;
    }

    @Test
    void acceptsFullyConfiguredEndpoint() {
        LiveAdapterValidator.validateEndpoint("inventory", validOauthEndpoint());
    }

    @Test
    void rejectsPlaintextBaseUrl() {
        Endpoint e = validOauthEndpoint();
        e.setBaseUrl("http://wms.internal.example.com");
        assertThatThrownBy(() -> LiveAdapterValidator.validateEndpoint("inventory", e))
                .isInstanceOf(AdapterException.class)
                .extracting(t -> ((AdapterException) t).kind())
                .isEqualTo(AdapterException.Kind.CONFIG);
    }

    @Test
    void rejectsHostNotOnAllowlist() {
        Endpoint e = validOauthEndpoint();
        e.setAllowedHosts(List.of("other.example.com"));
        assertThatThrownBy(() -> LiveAdapterValidator.validateEndpoint("inventory", e))
                .isInstanceOf(AdapterException.class);
    }

    @Test
    void rejectsMissingOauthSecret() {
        Endpoint e = validOauthEndpoint();
        e.getAuth().setClientSecret("");
        assertThatThrownBy(() -> LiveAdapterValidator.validateEndpoint("inventory", e))
                .isInstanceOf(AdapterException.class);
    }

    @Test
    void rejectsPlaintextTokenUrl() {
        Endpoint e = validOauthEndpoint();
        e.getAuth().setTokenUrl("http://auth.example.com/token");
        assertThatThrownBy(() -> LiveAdapterValidator.validateEndpoint("inventory", e))
                .isInstanceOf(AdapterException.class);
    }

    @Test
    void rejectsUnboundedReadTimeout() {
        Endpoint e = validOauthEndpoint();
        e.setReadTimeout(Duration.ofMinutes(5));
        assertThatThrownBy(() -> LiveAdapterValidator.validateEndpoint("inventory", e))
                .isInstanceOf(AdapterException.class);
    }

    @Test
    void rejectsOversizedResponseCeiling() {
        Endpoint e = validOauthEndpoint();
        e.setMaxResponseBytes(64 * 1024 * 1024);
        assertThatThrownBy(() -> LiveAdapterValidator.validateEndpoint("inventory", e))
                .isInstanceOf(AdapterException.class);
    }

    @Test
    void validatesApiKeyAndMtlsAuth() {
        Endpoint apiKey = validOauthEndpoint();
        Auth a = new Auth();
        a.setType(AuthType.API_KEY);
        a.setApiKeyHeader("X-Api-Key");
        a.setApiKeyValue("secret");
        apiKey.setAuth(a);
        LiveAdapterValidator.validateEndpoint("inventory", apiKey);

        Endpoint mtls = validOauthEndpoint();
        Auth m = new Auth();
        m.setType(AuthType.MTLS);
        m.setKeyStorePath("/etc/certs/client.p12");
        m.setKeyStorePassword("pw");
        m.setTrustStorePath("/etc/certs/trust.p12");
        m.setTrustStorePassword("pw");
        mtls.setAuth(m);
        LiveAdapterValidator.validateEndpoint("inventory", mtls);
    }

    @Test
    void requireAllowedRejectsNonHttpsAndForeignHost() {
        assertThatThrownBy(() -> LiveAdapterValidator.requireAllowed(
                "inventory", URI.create("http://wms.internal.example.com/x"), List.of("wms.internal.example.com")))
                .isInstanceOf(AdapterException.class)
                .extracting(t -> ((AdapterException) t).kind())
                .isEqualTo(AdapterException.Kind.FORBIDDEN_HOST);

        assertThatThrownBy(() -> LiveAdapterValidator.requireAllowed(
                "inventory", URI.create("https://evil.example.com/x"), List.of("wms.internal.example.com")))
                .isInstanceOf(AdapterException.class)
                .extracting(t -> ((AdapterException) t).kind())
                .isEqualTo(AdapterException.Kind.FORBIDDEN_HOST);
    }

    @Test
    void requireAllowedAcceptsHttpsAllowlistedHost() {
        LiveAdapterValidator.requireAllowed(
                "inventory", URI.create("https://wms.internal.example.com/api"), List.of("wms.internal.example.com"));
    }

    @Test
    void adapterExceptionMessageCarriesNoSecret() {
        AdapterException e = new AdapterException(AdapterException.Kind.AUTH, "pricing",
                new IllegalStateException("Bearer sk-super-secret-token https://auth.example.com"));
        assertThat(e.getMessage()).doesNotContain("secret").doesNotContain("Bearer").doesNotContain("https://");
        assertThat(e.getCause().getMessage()).isEqualTo("IllegalStateException");
    }
}
