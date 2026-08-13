package com.agentforge.commerce.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthConverterTest {

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void mapsScopesAndRoles() {
        Jwt jwt = base()
                .claim("scope", "runs:read runs:write")
                .claim("roles", List.of("operator", "auditor"))
                .build();

        AbstractAuthenticationToken token = new JwtAuthConverter("roles").convert(jwt);

        assertThat(AuthorityUtils.authorityListToSet(token.getAuthorities()))
                .contains("SCOPE_runs:read", "SCOPE_runs:write", "ROLE_operator", "ROLE_auditor");
        assertThat(token.getName()).isEqualTo("alice");
    }

    @Test
    void toleratesMissingRolesClaim() {
        Jwt jwt = base().claim("scope", "runs:read").build();
        AbstractAuthenticationToken token = new JwtAuthConverter("roles").convert(jwt);
        assertThat(AuthorityUtils.authorityListToSet(token.getAuthorities()))
                .containsExactly("SCOPE_runs:read");
    }

    @Test
    void ignoresBlankRoles() {
        Jwt jwt = base().claim("roles", List.of("", "operator")).build();
        AbstractAuthenticationToken token = new JwtAuthConverter("roles").convert(jwt);
        assertThat(AuthorityUtils.authorityListToSet(token.getAuthorities()))
                .containsExactly("ROLE_operator");
    }
}
