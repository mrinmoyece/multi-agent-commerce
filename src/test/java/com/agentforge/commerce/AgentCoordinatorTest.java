package com.agentforge.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.agentforge.commerce.coordinator.AgentCoordinator;
import com.agentforge.commerce.coordinator.AgentCoordinator.OrchestrationResult;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.runtime.brakes.RunBrakes;
import com.agentforge.commerce.runtime.budget.BudgetLedger;
import com.agentforge.commerce.runtime.budget.RunBudget;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.engine.AgentRuntimeEngine;
import com.agentforge.commerce.runtime.engine.AgentRuntimeEngine.SynthesisOutcome;
import com.agentforge.commerce.runtime.engine.RuntimeRunContext;
import com.agentforge.commerce.runtime.engine.SpecialistOutcome;
import com.agentforge.commerce.runtime.memory.WorkingMemory;
import com.agentforge.commerce.runtime.telemetry.RuntimeTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the engine-backed {@link AgentCoordinator}.
 *
 * <p>Validates that the coordinator dispatches the three grounded specialists
 * through the runtime engine, synthesises a briefing from their envelopes,
 * assembles a full run report, and keeps running (degraded) when a single
 * specialist fails.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentCoordinator — engine orchestration")
class AgentCoordinatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 19);
    private static final String RETAILER = "RETAILER-UK-001";

    @Mock
    AgentRuntimeEngine engine;

    private final RuntimeTelemetry telemetry =
            new RuntimeTelemetry(ObservationRegistry.create(), new SimpleMeterRegistry());
    private final Executor synchronousExecutor = Runnable::run;

    private AgentCoordinator coordinator;
    private RuntimeRunContext ctx;
    private DailyAnalysisContext context;

    @BeforeEach
    void setUp() {
        coordinator = new AgentCoordinator(engine, telemetry, synchronousExecutor);
        ctx = new RuntimeRunContext("run-1", RETAILER, DATE, "github-models",
                new BudgetLedger(new RunBudget(120_000, 24, Duration.ofMinutes(3), 500_000, 2_500, 10_000)),
                new RunBrakes(32, 4, Instant.now().plusSeconds(120)), new WorkingMemory());
        context = DailyAnalysisContext.builder().runId("run-1").date(DATE).retailerId(RETAILER).build();
        given(engine.newContext(any(), any(), any())).willReturn(ctx);
    }

    @Test
    @DisplayName("All specialists report → synthesis → full orchestration result")
    void allSpecialistsSucceed() {
        given(engine.runInventory(ctx)).willReturn(outcome(AgentRuntimeEngine.INVENTORY_AGENT, 0.9));
        given(engine.runPricing(ctx)).willReturn(outcome(AgentRuntimeEngine.PRICING_AGENT, 0.8));
        given(engine.runSentiment(ctx)).willReturn(outcome(AgentRuntimeEngine.SENTIMENT_AGENT, 0.7));
        given(engine.synthesise(any(), any(), any(), any())).willReturn(synthesis());

        OrchestrationResult result = coordinator.orchestrate(context, Duration.ofMinutes(2));

        assertThat(result.briefing().getExecutiveSummary()).isEqualTo("All nominal");
        assertThat(result.agentMessages()).hasSize(3);
        assertThat(result.envelopes()).hasSize(4);
        assertThat(result.report().specialists()).hasSize(3);
        assertThat(result.report().coordinator()).isNotNull();
        verify(engine).runInventory(ctx);
        verify(engine).runPricing(ctx);
        verify(engine).runSentiment(ctx);
        verify(engine).synthesise(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A failing specialist is replaced by a degraded outcome, run still completes")
    void oneSpecialistFails() {
        given(engine.runInventory(ctx)).willThrow(new IllegalStateException("WMS timeout"));
        given(engine.runPricing(ctx)).willReturn(outcome(AgentRuntimeEngine.PRICING_AGENT, 0.8));
        given(engine.runSentiment(ctx)).willReturn(outcome(AgentRuntimeEngine.SENTIMENT_AGENT, 0.7));
        given(engine.degradedOutcome(eq(AgentRuntimeEngine.INVENTORY_AGENT), any()))
                .willReturn(outcome(AgentRuntimeEngine.INVENTORY_AGENT, ResultStatus.DEGRADED, 0.0));
        given(engine.synthesise(any(), any(), any(), any())).willReturn(synthesis());

        OrchestrationResult result = coordinator.orchestrate(context, Duration.ofMinutes(2));

        assertThat(result.briefing()).isNotNull();
        assertThat(result.report().specialists()).hasSize(3);
        verify(engine).degradedOutcome(eq(AgentRuntimeEngine.INVENTORY_AGENT), any());
    }

    @Test
    @DisplayName("Legacy runDailyAnalysis path returns the synthesised briefing")
    void runDailyAnalysisReturnsBriefing() throws Exception {
        given(engine.runInventory(ctx)).willReturn(outcome(AgentRuntimeEngine.INVENTORY_AGENT, 0.9));
        given(engine.runPricing(ctx)).willReturn(outcome(AgentRuntimeEngine.PRICING_AGENT, 0.8));
        given(engine.runSentiment(ctx)).willReturn(outcome(AgentRuntimeEngine.SENTIMENT_AGENT, 0.7));
        given(engine.synthesise(any(), any(), any(), any())).willReturn(synthesis());

        DailyBriefing briefing = coordinator.runDailyAnalysis(context).get();

        assertThat(briefing.getRetailerId()).isEqualTo(RETAILER);
        assertThat(briefing.getRecommendedActions()).containsExactly("Reorder SKU-1");
    }

    private SpecialistOutcome outcome(String agent, double confidence) {
        return outcome(agent, ResultStatus.OK, confidence);
    }

    private SpecialistOutcome outcome(String agent, ResultStatus status, double confidence) {
        AgentResultEnvelope envelope = new AgentResultEnvelope(agent, status, "id", "hash",
                "github-models", "gpt-4o", "{}", List.of(), new Confidence(confidence, "r"),
                TokenUsage.ZERO, 5, true, List.of());
        AgentMessage message = AgentMessage.builder()
                .agentName(agent)
                .messageType(AgentMessage.MessageType.FINDINGS)
                .priority(AgentMessage.AgentMessagePriority.NORMAL)
                .content("{}")
                .timestamp(Instant.now())
                .build();
        return new SpecialistOutcome(envelope, message);
    }

    private SynthesisOutcome synthesis() {
        DailyBriefing briefing = DailyBriefing.builder()
                .briefingId("b-1")
                .date(DATE)
                .retailerId(RETAILER)
                .crossInsights(List.of("Insight"))
                .recommendedActions(List.of("Reorder SKU-1"))
                .urgentAlerts(List.of())
                .executiveSummary("All nominal")
                .rawSynthesis("{}")
                .generatedAt(Instant.now())
                .build();
        AgentResultEnvelope envelope = new AgentResultEnvelope(AgentRuntimeEngine.COORDINATOR_AGENT,
                ResultStatus.OK, "id", "hash", "github-models", "gpt-4o", "{}", List.of(),
                new Confidence(0.7, "r"), TokenUsage.ZERO, 5, true, List.of());
        return new SynthesisOutcome(briefing, envelope, ResultStatus.OK);
    }
}
