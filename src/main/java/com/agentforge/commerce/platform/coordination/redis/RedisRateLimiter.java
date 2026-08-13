package com.agentforge.commerce.platform.coordination.redis;

import com.agentforge.commerce.platform.coordination.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis-backed fixed-window {@link RateLimiter}. A single atomic Lua script
 * increments the window counter and sets its expiry on first use, so the
 * check-and-increment cannot race across replicas.
 */
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('incr', KEYS[1]) "
                    + "if tonumber(current) == 1 then "
                    + "redis.call('pexpire', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private final transient StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        Long count = redis.execute(
                INCREMENT_SCRIPT,
                List.of(KEY_PREFIX + key),
                Long.toString(window.toMillis()));
        return count != null && count <= limit;
    }
}
