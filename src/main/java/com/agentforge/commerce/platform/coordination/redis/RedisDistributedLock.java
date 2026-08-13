package com.agentforge.commerce.platform.coordination.redis;

import com.agentforge.commerce.platform.coordination.DistributedLock;
import com.agentforge.commerce.platform.coordination.LockHandle;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis-backed {@link DistributedLock} using {@code SET key token NX PX ttl} for
 * acquisition and a compare-and-delete Lua script for release, so a lock can
 * only be released by the exact acquisition that took it (fencing token).
 */
public class RedisDistributedLock implements DistributedLock {

    private static final String KEY_PREFIX = "lock:";

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final transient StringRedisTemplate redis;

    public RedisDistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<LockHandle> tryAcquire(String key, Duration ttl) {
        String redisKey = KEY_PREFIX + key;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.of(new LockHandle(key, token, this::release));
        }
        return Optional.empty();
    }

    private void release(LockHandle handle) {
        redis.execute(RELEASE_SCRIPT, List.of(KEY_PREFIX + handle.getKey()), handle.getFencingToken());
    }
}
