package com.agentforge.commerce.platform.coordination.memory;

import com.agentforge.commerce.platform.coordination.RateLimiter;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-JVM fixed-window {@link RateLimiter} for the {@code local}/{@code test}
 * profiles.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private static final class Window {
        private final long resetAtNanos;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long resetAtNanos) {
            this.resetAtNanos = resetAtNanos;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.nanoTime();
        Window current = windows.compute(key, (k, existing) ->
                (existing == null || existing.resetAtNanos <= now)
                        ? new Window(now + window.toNanos())
                        : existing);
        return current.count.incrementAndGet() <= limit;
    }
}
