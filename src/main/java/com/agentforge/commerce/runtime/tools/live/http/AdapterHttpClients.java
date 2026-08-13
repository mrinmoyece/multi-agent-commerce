package com.agentforge.commerce.runtime.tools.live.http;

import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Auth;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.AuthType;
import com.agentforge.commerce.runtime.tools.live.http.LiveAdapterProperties.Endpoint;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Constructs the JDK {@link HttpClient} for one live adapter. Redirects are never
 * followed (a redirect is a classic SSRF pivot), the connect deadline is applied
 * at the transport layer, and when the adapter uses mutual TLS the client is built
 * with a key/trust store loaded from the configured PKCS12 files so client identity
 * and backend-certificate pinning are enforced by TLS itself.
 */
public final class AdapterHttpClients {

    private AdapterHttpClients() {
    }

    /** @return a bounded, redirect-refusing HTTP client for the endpoint. */
    public static HttpClient build(String adapterName, Endpoint endpoint) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(endpoint.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1);
        if (endpoint.getAuth().getType() == AuthType.MTLS) {
            builder.sslContext(mutualTlsContext(adapterName, endpoint.getAuth()));
        }
        return builder.build();
    }

    private static SSLContext mutualTlsContext(String adapterName, Auth auth) {
        try {
            KeyStore keyStore = load(auth.getKeyStorePath(), auth.getKeyStorePassword());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, auth.getKeyStorePassword().toCharArray());

            KeyStore trustStore = load(auth.getTrustStorePath(), auth.getTrustStorePassword());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return context;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Misconfigured stores must stop LIVE startup, not degrade silently.
            throw new AdapterException(AdapterException.Kind.CONFIG, adapterName, e);
        }
    }

    private static KeyStore load(String path, String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            store.load(in, password.toCharArray());
        }
        return store;
    }
}
