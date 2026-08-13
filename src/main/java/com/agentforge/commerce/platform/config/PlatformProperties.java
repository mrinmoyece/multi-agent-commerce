package com.agentforge.commerce.platform.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Externalised tunables for the platform foundations. Bound from the
 * {@code platform.*} namespace so every value can be overridden per environment
 * without code changes and without baking infrastructure defaults into images.
 */
@ConfigurationProperties(prefix = "platform")
@Getter
@Setter
public class PlatformProperties {

    /**
     * When {@code true}, distributed coordination is served by Redis and Redis
     * must be reachable at startup. When {@code false} (permitted only under the
     * {@code local}/{@code test} profiles) in-memory single-JVM stand-ins are used.
     */
    private boolean redisEnabled;

    @NestedConfigurationProperty
    private Lock lock = new Lock();

    @NestedConfigurationProperty
    private StatusCache statusCache = new StatusCache();

    @NestedConfigurationProperty
    private Orchestration orchestration = new Orchestration();

    @NestedConfigurationProperty
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Lock {
        /** Fencing TTL for a run-execution lock; must exceed the orchestration timeout. */
        private Duration ttl = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class StatusCache {
        /** How long a cached run-status snapshot is served before refreshing from the store. */
        private Duration ttl = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Orchestration {
        /** Hard ceiling on a single run's specialist-agent phase. */
        private Duration timeout = Duration.ofMinutes(2);

        /** Bounded worker pool size for durable run execution. */
        private int poolSize = 4;

        /** Bounded queue depth before execution submissions are rejected. */
        private int queueCapacity = 32;
    }

    @Getter
    @Setter
    public static class RateLimit {
        /** Whether API rate limiting is enforced. */
        private boolean enabled = true;

        /** Maximum requests per principal within the window. */
        private int limit = 120;

        /** Rolling window length. */
        private Duration window = Duration.ofMinutes(1);
    }
}
