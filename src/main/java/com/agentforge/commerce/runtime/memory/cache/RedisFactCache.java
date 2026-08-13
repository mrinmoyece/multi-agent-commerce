package com.agentforge.commerce.runtime.memory.cache;

import com.agentforge.commerce.runtime.memory.FactCache;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed {@link FactCache} for production. Keys are tenant-qualified under a
 * dedicated prefix and every entry is written with its TTL so the hot cache never
 * outlives a fact's validity window.
 */
public class RedisFactCache implements FactCache {

    private static final String KEY_PREFIX = "memfact:";

    private final transient StringRedisTemplate redis;

    public RedisFactCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String retailerId, String key, String snapshotJson, Duration ttl) {
        redis.opsForValue().set(compositeKey(retailerId, key), snapshotJson, ttl);
    }

    @Override
    public Optional<String> get(String retailerId, String key) {
        return Optional.ofNullable(redis.opsForValue().get(compositeKey(retailerId, key)));
    }

    @Override
    public void evict(String retailerId, String key) {
        redis.delete(compositeKey(retailerId, key));
    }

    private static String compositeKey(String retailerId, String key) {
        return KEY_PREFIX + retailerId + ':' + key;
    }
}
