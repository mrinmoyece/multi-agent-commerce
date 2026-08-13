package com.agentforge.commerce.platform.security;

import com.agentforge.commerce.platform.config.PlatformProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2/OIDC resource-server security.
 *
 * <p>Secure by default: the API is stateless, CSRF is disabled (bearer tokens,
 * no cookies), sessions are never created, and every {@code /api/**} route
 * requires an authenticated principal with fine-grained authority enforced by
 * method security. Only unauthenticated liveness/readiness and scrape endpoints
 * are public. There is no static credential or bypass — production tokens are
 * validated against the configured issuer and audience.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({PlatformSecurityProperties.class, PlatformProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            PlatformSecurityProperties securityProperties,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {

        JwtAuthConverter jwtAuthConverter = new JwtAuthConverter(securityProperties.getRolesClaim());

        http
                // CSRF protection is intentionally disabled: this is a stateless
                // resource server authenticated solely via `Authorization: Bearer`
                // JWTs (no cookies, no HTTP session — see STATELESS below). Browsers
                // never auto-attach bearer tokens cross-site, so there is no CSRF
                // attack surface; enabling CSRF here would only break legitimate API
                // clients. (CodeQL java/spring-disabled-csrf-protection is a false
                // positive for token-only APIs.)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    /**
     * Production JWT decoder — created only when an issuer is configured. Validates
     * signature, standard claims (including expiry and issuer) and the required
     * audience. In the {@code test} profile no issuer is set, so a stub decoder is
     * supplied by the test context and real tokens are never decoded.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.issuer-uri")
    public JwtDecoder jwtDecoder(
            @org.springframework.beans.factory.annotation.Value(
                    "${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            PlatformSecurityProperties securityProperties) {

        if (securityProperties.getAudiences().isEmpty()) {
            throw new IllegalStateException(
                    "platform.security.audiences must be configured when validating JWTs");
        }

        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(securityProperties.getAudiences()));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
