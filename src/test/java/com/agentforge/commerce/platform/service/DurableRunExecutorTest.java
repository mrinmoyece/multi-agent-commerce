package com.agentforge.commerce.platform.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentforge.commerce.coordinator.AgentCoordinator;
import com.agentforge.commerce.coordinator.AgentCoordinator.OrchestrationResult;
import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.platform.config.PlatformProperties;
import com.agentforge.commerce.platform.coordination.DistributedLock;
import com.agentforge.commerce.platform.coordination.memory.InMemoryDistributedLock;
import com.agentforge.commerce.platform.persistence.domain.RunStatus;
import com.agentforge.commerce.platform.persistence.entity.AnalysisRunEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class DurableRunExecutorTest {

    private AnalysisRunService runService;
    private AgentCoordinator coordinator;
    private DurableRunExecutor executor;

    @BeforeEach
    void setUp() {
        runService = mock(AnalysisRunService.class);
        coordinator = mock(AgentCoordinator.class);
        DistributedLock lock = new InMemoryDistributedLock();
        PlatformProperties properties = new PlatformProperties();
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.initialize();
        executor = new DurableRunExecutor(runService, coordinator, lock, properties, pool);
    }

    private AnalysisRunEntity run(UUID id) {
        AnalysisRunEntity entity = new AnalysisRunEntity();
        entity.setId(id);
        entity.setRetailerId("acme");
        entity.setBusinessDate(LocalDate.of(2024, 1, 1));
        entity.setStatus(RunStatus.RUNNING);
        return entity;
    }

    @Test
    void executesAndCompletesRun() {
        UUID id = UUID.randomUUID();
        when(runService.beginExecution(id)).thenReturn(Optional.of(run(id)));
        DailyBriefing briefing = DailyBriefing.builder()
                .retailerId("acme")
                .recommendedActions(List.of("Reprice SKU-1"))
                .build();
        OrchestrationResult result = new OrchestrationResult(briefing, List.of());
        when(coordinator.orchestrate(any(), any())).thenReturn(result);

        executor.execute(id);

        verify(runService).completeRun(eq(id), eq(result));
        verify(runService, never()).failRun(any(), any());
    }

    @Test
    void skipsWhenNotClaimable() {
        UUID id = UUID.randomUUID();
        when(runService.beginExecution(id)).thenReturn(Optional.empty());

        executor.execute(id);

        verify(coordinator, never()).orchestrate(any(), any());
        verify(runService, never()).completeRun(any(), any());
    }

    @Test
    void failsRunWhenOrchestrationThrows() {
        UUID id = UUID.randomUUID();
        when(runService.beginExecution(id)).thenReturn(Optional.of(run(id)));
        when(coordinator.orchestrate(any(), any())).thenThrow(new IllegalStateException("boom"));

        executor.execute(id);

        verify(runService).failRun(eq(id), any());
        verify(runService, never()).completeRun(any(), any());
    }

    @Test
    void secondReplicaSkipsWhileLockHeld() {
        UUID id = UUID.randomUUID();
        DistributedLock lock = new InMemoryDistributedLock();
        PlatformProperties properties = new PlatformProperties();
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.initialize();
        DurableRunExecutor local = new DurableRunExecutor(runService, coordinator, lock, properties, pool);
        // Hold the run lock so the executor cannot claim it.
        lock.tryAcquire("run:" + id, properties.getLock().getTtl()).orElseThrow();

        local.execute(id);

        verify(runService, never()).beginExecution(id);
    }
}
