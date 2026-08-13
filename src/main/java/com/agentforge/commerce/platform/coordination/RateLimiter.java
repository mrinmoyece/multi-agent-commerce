package com.agentforge.commerce.platform.coordination;

import java.time.Duration;

/**
 * Fixed-window rate limiter. Backed by Redis counters in production so limits
 * are enforced across replicas; an in-memory implementation backs the
 * {@code local}/{@code test} profiles.
 */
public interface RateLimiter {

    /**
     * Consume one unit against {@code key}.
     *
     * @param key    caller/route identity to meter
     * @param limit  maximum units permitted within the window
     * @param window rolling window length
     * @return {@code true} if the call is permitted, {@code false} if throttled
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
