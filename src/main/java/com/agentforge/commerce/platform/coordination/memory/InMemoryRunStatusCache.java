package com.agentforge.commerce.platform.coordination.memory;

import com.agentforge.commerce.platform.coordination.RunStatusCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-JVM {@link RunStatusCache} for the {@code local}/{@code test} profiles.
 */
public class InMemoryRunStatusCache implements RunStatusCache {

    private record Entry(String value, long expiresAtNanos) {
    }

    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public void put(UUID runId, String snapshotJson, Duration ttl) {
        cache.put(runId, new Entry(snapshotJson, System.nanoTime() + ttl.toNanos()));
    }

    @Override
    public Optional<String> get(UUID runId) {
        Entry entry = cache.get(runId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtNanos() <= System.nanoTime()) {
            cache.remove(runId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void evict(UUID runId) {
        cache.remove(runId);
    }
}
