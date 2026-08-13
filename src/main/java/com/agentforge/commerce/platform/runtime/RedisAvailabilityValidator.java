package com.agentforge.commerce.platform.runtime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Fails application startup fast when Redis is required (production) but
 * unreachable. Coordination correctness depends on Redis, so booting without it
 * would silently degrade cross-replica guarantees — we refuse to start instead.
 */
@Component
@ConditionalOnProperty(name = "platform.redis-enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RedisAvailabilityValidator implements ApplicationRunner {

    private final transient RedisConnectionFactory connectionFactory;

    public RedisAvailabilityValidator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String reply = connection.ping();
            if (reply == null || !"PONG".equalsIgnoreCase(reply)) {
                throw new IllegalStateException("Redis PING returned an unexpected reply: " + reply);
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Redis is required (platform.redis-enabled=true) but not reachable at startup", ex);
        }
    }
}
