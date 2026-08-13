package com.agentforge.commerce.platform.runtime;

import com.agentforge.commerce.platform.persistence.domain.RunStatus;
import com.agentforge.commerce.platform.persistence.repo.AnalysisRunRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Publishes the durable-run queue age as a low-cardinality gauge.
 *
 * <p>{@code runtime.run.queue.age.seconds} reports how long the oldest PENDING run
 * has been waiting to be claimed. A rising value means the pool cannot keep up (or
 * every replica is blocked), which is the earliest actionable signal that scheduled
 * work is stalling. The gauge carries no run/tenant identifier, so it stays bounded.</p>
 */
@Component
public class RunQueueMetrics {

    private final AnalysisRunRepository runRepository;
    private final Clock clock;

    public RunQueueMetrics(MeterRegistry registry, AnalysisRunRepository runRepository, Clock clock) {
        this.runRepository = runRepository;
        this.clock = clock;
        Gauge.builder("runtime.run.queue.age.seconds", this, RunQueueMetrics::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest unclaimed (PENDING) durable run")
                .register(registry);
    }

    double oldestPendingAgeSeconds() {
        Instant oldest = runRepository.findOldestCreatedAtByStatus(RunStatus.PENDING);
        if (oldest == null) {
            return 0.0d;
        }
        long seconds = Duration.between(oldest, clock.instant()).getSeconds();
        return Math.max(0L, seconds);
    }
}
