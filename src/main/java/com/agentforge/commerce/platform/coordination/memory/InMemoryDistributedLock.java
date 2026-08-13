package com.agentforge.commerce.platform.coordination.memory;

import com.agentforge.commerce.platform.coordination.DistributedLock;
import com.agentforge.commerce.platform.coordination.LockHandle;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-JVM {@link DistributedLock} for the {@code local}/{@code test}
 * profiles. Provides the same mutual-exclusion and self-expiry semantics as the
 * Redis implementation within one process, but offers no cross-replica
 * guarantee — it must never be active in production.
 */
public class InMemoryDistributedLock implements DistributedLock {

    private record Entry(String token, long expiresAtNanos) {
    }

    private final ConcurrentHashMap<String, Entry> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<LockHandle> tryAcquire(String key, Duration ttl) {
        long now = System.nanoTime();
        String token = UUID.randomUUID().toString();
        long expiry = now + ttl.toNanos();

        Entry result = locks.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAtNanos() <= now) {
                return new Entry(token, expiry);
            }
            return existing;
        });

        if (result.token().equals(token)) {
            return Optional.of(new LockHandle(key, token, this::release));
        }
        return Optional.empty();
    }

    private void release(LockHandle handle) {
        locks.computeIfPresent(handle.getKey(), (k, existing) ->
                existing.token().equals(handle.getFencingToken()) ? null : existing);
    }
}
