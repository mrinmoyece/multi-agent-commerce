package com.agentforge.commerce.platform.api.web;

import com.agentforge.commerce.platform.config.PlatformProperties;
import com.agentforge.commerce.platform.coordination.RateLimiter;
import com.agentforge.commerce.platform.service.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-principal fixed-window rate limiting for the API. Runs after authentication
 * so the limit key is the authenticated subject where available, falling back to
 * the remote address. Backed by the distributed {@link RateLimiter} so limits are
 * enforced across replicas in production.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final PlatformProperties properties;

    public RateLimitInterceptor(RateLimiter rateLimiter, PlatformProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        PlatformProperties.RateLimit config = properties.getRateLimit();
        if (!config.isEnabled()) {
            return true;
        }
        String key = "api:" + resolvePrincipal(request);
        if (!rateLimiter.tryAcquire(key, config.getLimit(), config.getWindow())) {
            throw new RateLimitExceededException("Rate limit exceeded; retry later");
        }
        return true;
    }

    private static String resolvePrincipal(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null) {
            return authentication.getName();
        }
        return request.getRemoteAddr();
    }
}
