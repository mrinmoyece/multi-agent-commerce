package com.agentforge.commerce.platform.config;

import com.agentforge.commerce.platform.coordination.DistributedLock;
import com.agentforge.commerce.platform.coordination.RateLimiter;
import com.agentforge.commerce.platform.coordination.RunStatusCache;
import com.agentforge.commerce.platform.coordination.memory.InMemoryDistributedLock;
import com.agentforge.commerce.platform.coordination.memory.InMemoryRateLimiter;
import com.agentforge.commerce.platform.coordination.memory.InMemoryRunStatusCache;
import com.agentforge.commerce.platform.coordination.redis.RedisDistributedLock;
import com.agentforge.commerce.platform.coordination.redis.RedisRateLimiter;
import com.agentforge.commerce.platform.coordination.redis.RedisRunStatusCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Selects the coordination backend. When {@code platform.redis-enabled=true} the
 * Redis-backed implementations are wired (production); otherwise the in-memory
 * single-JVM stand-ins are used for the {@code local}/{@code test} profiles.
 *
 * <p>The Redis beans are declared first so that, when enabled, the
 * {@link ConditionalOnMissingBean} fallbacks are suppressed.</p>
 */
@Configuration(proxyBeanMethods = false)
public class CoordinationConfig {

    @Bean
    @ConditionalOnProperty(name = "platform.redis-enabled", havingValue = "true")
    public DistributedLock redisDistributedLock(StringRedisTemplate redis) {
        return new RedisDistributedLock(redis);
    }

    @Bean
    @ConditionalOnProperty(name = "platform.redis-enabled", havingValue = "true")
    public RateLimiter redisRateLimiter(StringRedisTemplate redis) {
        return new RedisRateLimiter(redis);
    }

    @Bean
    @ConditionalOnProperty(name = "platform.redis-enabled", havingValue = "true")
    public RunStatusCache redisRunStatusCache(StringRedisTemplate redis) {
        return new RedisRunStatusCache(redis);
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock inMemoryDistributedLock() {
        return new InMemoryDistributedLock();
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter inMemoryRateLimiter() {
        return new InMemoryRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean(RunStatusCache.class)
    public RunStatusCache inMemoryRunStatusCache() {
        return new InMemoryRunStatusCache();
    }
}
