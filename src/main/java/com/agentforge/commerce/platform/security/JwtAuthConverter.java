package com.agentforge.commerce.platform.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Builds the authenticated principal from a validated JWT by combining two
 * authority sources:
 * <ul>
 *   <li>OAuth2 scopes → {@code SCOPE_*} authorities (fine-grained API permissions)</li>
 *   <li>the configured roles claim → {@code ROLE_*} authorities (coarse RBAC)</li>
 * </ul>
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
    private final String rolesClaim;

    public JwtAuthConverter(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
        authorities.addAll(roleAuthorities(jwt));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<GrantedAuthority> roleAuthorities(Jwt jwt) {
        Object claim = jwt.getClaim(rolesClaim);
        if (!(claim instanceof Collection<?> roles)) {
            return List.of();
        }
        List<GrantedAuthority> result = new ArrayList<>();
        for (Object role : roles) {
            if (role != null && !role.toString().isBlank()) {
                result.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        return result;
    }
}
