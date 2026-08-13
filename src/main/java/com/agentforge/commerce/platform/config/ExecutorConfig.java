package com.agentforge.commerce.platform.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor dedicated to durable run execution. Separate from Spring's
 * {@code applicationTaskExecutor} (which fans out the specialist agents) so that
 * run-level orchestration has an explicit, back-pressured pool with a caller-runs
 * rejection policy rather than unbounded queueing.
 */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfig {

    /**
     * Agent fan-out executor consumed by {@code AgentCoordinator}.
     *
     * <p>Declaring the bounded {@code platformOrchestrationExecutor} above makes
     * Spring Boot's {@code TaskExecutionAutoConfiguration} back off (its
     * {@code applicationTaskExecutor} is {@code @ConditionalOnMissingBean(Executor.class)}),
     * which would otherwise leave the coordinator with no {@code applicationTaskExecutor}
     * to wire and fail context startup in every runtime profile. We therefore supply
     * it explicitly, mirroring Boot's virtual-thread executor (the platform runs with
     * {@code spring.threads.virtual.enabled=true}). The {@code @ConditionalOnMissingBean}
     * guard keeps any context that already provides one (e.g. a test stub) authoritative.</p>
     */
    @Bean(name = "applicationTaskExecutor")
    @ConditionalOnMissingBean(name = "applicationTaskExecutor")
    public SimpleAsyncTaskExecutor applicationTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("agent-");
        executor.setVirtualThreads(true);
        return executor;
    }

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
