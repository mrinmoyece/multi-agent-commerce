package com.agentforge.commerce.platform.coordination.redis;

import com.agentforge.commerce.platform.coordination.RunStatusCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed {@link RunStatusCache} storing compact status snapshots. */
public class RedisRunStatusCache implements RunStatusCache {

    private static final String KEY_PREFIX = "runstatus:";

    private final transient StringRedisTemplate redis;

    public RedisRunStatusCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(UUID runId, String snapshotJson, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + runId, snapshotJson, ttl);
    }

    @Override
    public Optional<String> get(UUID runId) {
        return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + runId));
    }

    @Override
    public void evict(UUID runId) {
        redis.delete(KEY_PREFIX + runId);
    }
}
