package com.agentforge.commerce.runtime.memory.cache;

import com.agentforge.commerce.runtime.memory.FactCache;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-JVM {@link FactCache} for the {@code local}/{@code test} profiles. Keys
 * are tenant-qualified to preserve isolation and each entry carries an absolute
 * expiry so stale facts self-heal without a background sweeper.
 */
public class InMemoryFactCache implements FactCache {

    private record Entry(String value, long expiresAtNanos) {
    }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public void put(String retailerId, String key, String snapshotJson, Duration ttl) {
        cache.put(compositeKey(retailerId, key), new Entry(snapshotJson, System.nanoTime() + ttl.toNanos()));
    }

    @Override
    public Optional<String> get(String retailerId, String key) {
        String composite = compositeKey(retailerId, key);
        Entry entry = cache.get(composite);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtNanos() <= System.nanoTime()) {
            cache.remove(composite, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void evict(String retailerId, String key) {
        cache.remove(compositeKey(retailerId, key));
    }

    private static String compositeKey(String retailerId, String key) {
        return retailerId + '\u0000' + key;
    }
}
