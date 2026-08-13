package com.agentforge.commerce.platform.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor dedicated to durable run execution. Separate from Spring's
 * {@code applicationTaskExecutor} (which fans out the specialist agents) so that
 * run-level orchestration has an explicit, back-pressured pool with a caller-runs
 * rejection policy rather than unbounded queueing.
 */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfig {

    @Bean(name = "platformOrchestrationExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor platformOrchestrationExecutor(PlatformProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = Math.max(1, properties.getOrchestration().getPoolSize());
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Math.max(1, properties.getOrchestration().getQueueCapacity()));
        executor.setThreadNamePrefix("run-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
