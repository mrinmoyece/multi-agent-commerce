package com.agentforge.commerce.platform.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentforge.commerce.platform.coordination.redis.RedisDistributedLock;
import com.agentforge.commerce.platform.coordination.redis.RedisRateLimiter;
import com.agentforge.commerce.platform.coordination.redis.RedisRunStatusCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisCoordinationTest {

    @Test
    @SuppressWarnings("unchecked")
    void lockAcquiresWhenSetIfAbsentSucceeds() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("lock:run:1"), any(), eq(Duration.ofSeconds(30)))).thenReturn(true);

        RedisDistributedLock lock = new RedisDistributedLock(redis);
        Optional<LockHandle> handle = lock.tryAcquire("run:1", Duration.ofSeconds(30));

        assertThat(handle).isPresent();
        handle.get().close();
        verify(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void lockEmptyWhenAlreadyHeld() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        RedisDistributedLock lock = new RedisDistributedLock(redis);
        assertThat(lock.tryAcquire("run:1", Duration.ofSeconds(30))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rateLimiterPermitsUpToLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L, 5L, 6L);

        RedisRateLimiter limiter = new RedisRateLimiter(redis);
        Duration window = Duration.ofMinutes(1);
        assertThat(limiter.tryAcquire("p", 5, window)).isTrue();
        assertThat(limiter.tryAcquire("p", 5, window)).isTrue();
        assertThat(limiter.tryAcquire("p", 5, window)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rateLimiterRejectsOnNullReply() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        RedisRateLimiter limiter = new RedisRateLimiter(redis);
        assertThat(limiter.tryAcquire("p", 5, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusCacheReadsWritesAndEvicts() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        UUID id = UUID.randomUUID();
        when(ops.get("runstatus:" + id)).thenReturn("{\"s\":1}");

        RedisRunStatusCache cache = new RedisRunStatusCache(redis);
        cache.put(id, "{\"s\":1}", Duration.ofSeconds(30));
        verify(ops).set("runstatus:" + id, "{\"s\":1}", Duration.ofSeconds(30));

        assertThat(cache.get(id)).contains("{\"s\":1}");

        cache.evict(id);
        verify(redis).delete("runstatus:" + id);
    }
}
