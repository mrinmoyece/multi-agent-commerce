package com.agentforge.commerce.runtime.config;

import com.agentforge.commerce.runtime.memory.FactCache;
import com.agentforge.commerce.runtime.memory.cache.InMemoryFactCache;
import com.agentforge.commerce.runtime.memory.cache.RedisFactCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Selects the semantic-memory hot-cache backend. When
 * {@code platform.redis-enabled=true} the Redis cache is wired (production);
 * otherwise the in-memory single-JVM stand-in is used for the
 * {@code local}/{@code test} profiles. The Redis bean is declared first so the
 * {@link ConditionalOnMissingBean} fallback is suppressed when enabled.
 */
@Configuration(proxyBeanMethods = false)
public class MemoryConfig {

    @Bean
    @ConditionalOnProperty(name = "platform.redis-enabled", havingValue = "true")
    public FactCache redisFactCache(StringRedisTemplate redis) {
        return new RedisFactCache(redis);
    }

    @Bean
    @ConditionalOnMissingBean(FactCache.class)
    public FactCache inMemoryFactCache() {
        return new InMemoryFactCache();
    }
}
