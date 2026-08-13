package com.agentforge.commerce.runtime.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Layer 2 runtime configuration. Binds {@link RuntimeProperties} and
 * exposes a single {@link Clock} bean so every runtime component reads time from
 * one injectable source — production uses UTC, tests can substitute a fixed clock
 * for fully deterministic evidence and freshness behaviour.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RuntimeProperties.class)
public class RuntimeConfig {

    /** The system clock in UTC; overridable in tests for determinism. */
    @Bean
    public Clock runtimeClock() {
        return Clock.systemUTC();
    }
}
