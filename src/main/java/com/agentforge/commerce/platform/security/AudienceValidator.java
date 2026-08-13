package com.agentforge.commerce.platform.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects any JWT whose {@code aud} claim does not include one of the configured
 * audiences. Guards against tokens minted for a different relying party being
 * replayed against this API.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> acceptedAudiences;

    public AudienceValidator(List<String> acceptedAudiences) {
        this.acceptedAudiences = List.copyOf(acceptedAudiences);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> tokenAudiences = token.getAudience();
        if (tokenAudiences != null && tokenAudiences.stream().anyMatch(acceptedAudiences::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "The required audience is missing from the token",
                null));
    }
}
