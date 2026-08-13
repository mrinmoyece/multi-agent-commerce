package com.agentforge.commerce.platform.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge.commerce.platform.coordination.memory.InMemoryDistributedLock;
import com.agentforge.commerce.platform.coordination.memory.InMemoryRateLimiter;
import com.agentforge.commerce.platform.coordination.memory.InMemoryRunStatusCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryCoordinationTest {

    @Test
    void lockIsMutuallyExclusiveUntilReleased() {
        InMemoryDistributedLock lock = new InMemoryDistributedLock();
        Optional<LockHandle> first = lock.tryAcquire("run:1", Duration.ofMinutes(1));
        assertThat(first).isPresent();

        assertThat(lock.tryAcquire("run:1", Duration.ofMinutes(1))).isEmpty();

        first.get().close();
        assertThat(lock.tryAcquire("run:1", Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void lockSelfExpires() throws InterruptedException {
        InMemoryDistributedLock lock = new InMemoryDistributedLock();
        assertThat(lock.tryAcquire("k", Duration.ofMillis(20))).isPresent();
        Thread.sleep(40);
        assertThat(lock.tryAcquire("k", Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void closeIsIdempotent() {
        InMemoryDistributedLock lock = new InMemoryDistributedLock();
        LockHandle handle = lock.tryAcquire("k", Duration.ofMinutes(1)).orElseThrow();
        handle.close();
        handle.close();
        assertThat(lock.tryAcquire("k", Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void rateLimiterThrottlesBeyondLimit() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        Duration window = Duration.ofMinutes(1);
        assertThat(limiter.tryAcquire("p", 2, window)).isTrue();
        assertThat(limiter.tryAcquire("p", 2, window)).isTrue();
        assertThat(limiter.tryAcquire("p", 2, window)).isFalse();
        assertThat(limiter.tryAcquire("other", 2, window)).isTrue();
    }

    @Test
    void rateLimiterWindowResets() throws InterruptedException {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        Duration window = Duration.ofMillis(20);
        assertThat(limiter.tryAcquire("p", 1, window)).isTrue();
        assertThat(limiter.tryAcquire("p", 1, window)).isFalse();
        Thread.sleep(40);
        assertThat(limiter.tryAcquire("p", 1, window)).isTrue();
    }

    @Test
    void statusCacheStoresAndExpires() throws InterruptedException {
        InMemoryRunStatusCache cache = new InMemoryRunStatusCache();
        UUID id = UUID.randomUUID();
        cache.put(id, "{\"status\":\"PENDING\"}", Duration.ofMinutes(1));
        assertThat(cache.get(id)).contains("{\"status\":\"PENDING\"}");

        cache.evict(id);
        assertThat(cache.get(id)).isEmpty();

        cache.put(id, "x", Duration.ofMillis(20));
        Thread.sleep(40);
        assertThat(cache.get(id)).isEmpty();
    }
}
