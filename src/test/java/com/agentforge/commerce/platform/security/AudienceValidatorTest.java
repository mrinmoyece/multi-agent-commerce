package com.agentforge.commerce.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private static Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user")
                .audience(audience)
                .claim("scope", "runs:read")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void acceptsWhenAudienceMatches() {
        AudienceValidator validator = new AudienceValidator(List.of("api://commerce", "other"));
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of("api://commerce")));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsWhenAudienceMissing() {
        AudienceValidator validator = new AudienceValidator(List.of("api://commerce"));
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of("api://other")));
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsWhenNoAudienceClaim() {
        AudienceValidator validator = new AudienceValidator(List.of("api://commerce"));
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claims(c -> c.putAll(Map.of("sub", "user")))
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
}
