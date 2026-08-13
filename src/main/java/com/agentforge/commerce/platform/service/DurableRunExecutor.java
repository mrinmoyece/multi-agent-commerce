package com.agentforge.commerce.platform.service;

import com.agentforge.commerce.coordinator.AgentCoordinator;
import com.agentforge.commerce.coordinator.AgentCoordinator.OrchestrationResult;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.platform.config.PlatformProperties;
import com.agentforge.commerce.platform.coordination.DistributedLock;
import com.agentforge.commerce.platform.coordination.LockHandle;
import com.agentforge.commerce.platform.persistence.entity.AnalysisRunEntity;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Executes a durable run exactly once across replicas.
 *
 * <p>Coordination is layered: a distributed lock ensures a single replica claims
 * the run, the PENDING→RUNNING transition is guarded by optimistic locking as a
 * backstop, and orchestration is bounded by a timeout on a dedicated pool.
 * Execution is triggered only after the creating transaction commits, so the run
 * is always durable before any work starts. Existing agent behaviour and
 * fail-closed synthesis are unchanged.</p>
 */
@Component
public class DurableRunExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DurableRunExecutor.class);

    private final AnalysisRunService runService;
    private final AgentCoordinator coordinator;
    private final DistributedLock distributedLock;
    private final PlatformProperties properties;
    private final ThreadPoolTaskExecutor orchestrationExecutor;

    public DurableRunExecutor(
            AnalysisRunService runService,
            AgentCoordinator coordinator,
            DistributedLock distributedLock,
            PlatformProperties properties,
            @org.springframework.beans.factory.annotation.Qualifier("platformOrchestrationExecutor")
            ThreadPoolTaskExecutor orchestrationExecutor) {
        this.runService = runService;
        this.coordinator = coordinator;
        this.distributedLock = distributedLock;
        this.properties = properties;
        this.orchestrationExecutor = orchestrationExecutor;
    }

    /** React to a durably-created run by scheduling its asynchronous execution. */
    @TransactionalEventListener
    public void onRunCreated(RunCreatedEvent event) {
        submit(event.runId());
    }

    /** Submit a run for bounded, back-pressured asynchronous execution. */
    public void submit(UUID runId) {
        orchestrationExecutor.execute(() -> execute(runId));
    }

    /**
     * Execute a run under a cluster-wide lock. Safe to call from any replica: at
     * most one will win the lock and drive the state machine; the rest return.
     */
    public void execute(UUID runId) {
        LockHandle handle = distributedLock.tryAcquire(
                "run:" + runId, properties.getLock().getTtl()).orElse(null);
        if (handle == null) {
            LOG.debug("Run {} is already being executed by another replica — skipping", runId);
            return;
        }
        try (handle) {
            runOnce(runId);
        }
    }

    private void runOnce(UUID runId) {
        Optional<AnalysisRunEntity> claimed;
        try {
            claimed = runService.beginExecution(runId);
        } catch (OptimisticLockingFailureException e) {
            LOG.debug("Run {} claimed concurrently — skipping", runId);
            return;
        }
        if (claimed.isEmpty()) {
            LOG.debug("Run {} is not in a startable state — skipping", runId);
            return;
        }

        AnalysisRunEntity run = claimed.get();
        try {
            DailyAnalysisContext context = DailyAnalysisContext.builder()
                    .runId(runId.toString())
                    .date(run.getBusinessDate())
                    .retailerId(run.getRetailerId())
                    .build();
            OrchestrationResult result =
                    coordinator.orchestrate(context, properties.getOrchestration().getTimeout());
            runService.completeRun(runId, result);
            LOG.info("Run {} completed successfully", runId);
        } catch (RuntimeException e) {
            LOG.error("Run {} failed ({})", runId, e.getClass().getSimpleName());
            runService.failRun(runId, e.getClass().getSimpleName());
        }
    }
}
