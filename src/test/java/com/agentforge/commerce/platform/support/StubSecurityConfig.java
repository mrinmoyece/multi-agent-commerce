package com.agentforge.commerce.platform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Supplies a stub {@link JwtDecoder} so the OAuth2 resource-server filter chain
 * can be configured without a real issuer. Tests authenticate with
 * spring-security-test's {@code jwt()} post-processor, which injects the
 * authentication directly and never invokes this decoder.
 */
@TestConfiguration
public class StubSecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("JWT decoding is not used in tests");
        };
    }
}
