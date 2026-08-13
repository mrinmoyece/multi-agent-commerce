package com.agentforge.commerce.runtime.tools.live.http;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;

/**
 * Builds per-adapter resilience primitives with a single, auditable policy. Each
 * live adapter gets its own {@link CircuitBreaker} and {@link Retry} instance so a
 * failing backend is isolated: its circuit can open without affecting the other
 * adapters. Only transient failures ({@link AdapterException#isRetryable()}) are
 * retried or counted toward opening the circuit — deterministic client-side
 * failures (auth, malformed, oversized, forbidden host, stale) fail fast.
 */
public final class AdapterResilience {

    private AdapterResilience() {
    }

    /** @return a circuit breaker that opens on repeated transient failures only. */
    public static CircuitBreaker circuitBreaker(String name) {
        return circuitBreaker(name, 10, 50.0f, 5, Duration.ofSeconds(10));
    }

    /** @return a circuit breaker with explicit thresholds (used by deterministic tests). */
    public static CircuitBreaker circuitBreaker(String name, int slidingWindow, float failureRatePct,
                                                int minimumCalls, Duration openWait) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(slidingWindow)
                .failureRateThreshold(failureRatePct)
                .minimumNumberOfCalls(minimumCalls)
                .waitDurationInOpenState(openWait)
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .recordException(AdapterResilience::isTransient)
                .build();
        return CircuitBreaker.of(name, config);
    }

    /** @return a retry that attempts transient failures a bounded number of times. */
    public static Retry retry(String name) {
        return retry(name, 3, Duration.ofMillis(200));
    }

    /** @return a retry with explicit attempts/backoff (used by deterministic tests). */
    public static Retry retry(String name, int maxAttempts, Duration wait) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(wait)
                .retryOnException(AdapterResilience::isTransient)
                .build();
        return Retry.of(name, config);
    }

    private static boolean isTransient(Throwable t) {
        return t instanceof AdapterException adapter && adapter.isRetryable();
    }
}
