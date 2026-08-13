package com.agentforge.commerce.platform.security;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-server security tunables bound from {@code platform.security.*}.
 */
@ConfigurationProperties(prefix = "platform.security")
@Getter
@Setter
public class PlatformSecurityProperties {

    /**
     * Accepted token audiences. At least one must appear in the JWT {@code aud}
     * claim. Required in every profile that validates real tokens.
     */
    private List<String> audiences = List.of();

    /**
     * JWT claim carrying coarse-grained roles (in addition to OAuth2 scopes).
     * Mapped to {@code ROLE_*} authorities for method security.
     */
    private String rolesClaim = "roles";
}
