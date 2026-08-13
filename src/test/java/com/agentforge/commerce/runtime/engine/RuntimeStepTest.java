package com.agentforge.commerce.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.agentforge.commerce.llm.LlmCompletion;
import com.agentforge.commerce.llm.LlmGateway;
import com.agentforge.commerce.runtime.brakes.BrakeTrippedException;
import com.agentforge.commerce.runtime.brakes.RunBrakes;
import com.agentforge.commerce.runtime.budget.BudgetLedger;
import com.agentforge.commerce.runtime.budget.RunBudget;
import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.memory.WorkingMemory;
import com.agentforge.commerce.runtime.prompt.PromptRegistry;
import com.agentforge.commerce.runtime.telemetry.RuntimeTelemetry;
import com.agentforge.commerce.runtime.tools.InventorySnapshot;
import com.agentforge.commerce.runtime.tools.fixture.FixtureInventoryTool;
import com.agentforge.commerce.runtime.validation.AgentSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the shared {@link RuntimeStep}: injection fencing, budget hard
 * stops before provider calls, schema validation, and fail-closed degradation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Runtime step")
class RuntimeStepTest {

    private static final Instant NOW = Instant.parse("2026-06-19T09:00:00Z");
    private static final String VALID = "{\"risks\":[],\"reorders\":[],\"clearance\":[],"
            + "\"citedSkus\":[],\"summary\":\"ok\"}";
    private static final String FALLBACK = "{\"risks\":[],\"reorders\":[],\"clearance\":[],"
            + "\"citedSkus\":[],\"summary\":\"fallback\"}";

    @Mock
    LlmGateway llm;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RuntimeTelemetry telemetry =
            new RuntimeTelemetry(ObservationRegistry.create(), new SimpleMeterRegistry());
    private RuntimeStep step;

    @BeforeEach
    void setUp() {
        step = new RuntimeStep(new PromptRegistry(), llm, mapper, telemetry, clock);
    }

    private RuntimeRunContext context(RunBudget budget) {
        return new RuntimeRunContext("run-1", "R1", LocalDate.of(2026, 6, 19), "azure-openai",
                new BudgetLedger(budget), new RunBrakes(4, 4, NOW.plusSeconds(120)), new WorkingMemory());
    }

    private RunBudget generousBudget() {
        return new RunBudget(120_000, 24, Duration.ofMinutes(3), 500_000, 2_500, 10_000);
    }

    private RuntimeStep.Structured complete(RuntimeRunContext ctx, boolean sourceDegraded) {
        return step.complete(ctx, AgentRuntimeEngine.INVENTORY_AGENT, "gpt-4o",
                PromptRegistry.INVENTORY_ANALYSIS, AgentSchemas.INVENTORY, Map.of(), "snapshot",
                "{\"data\":true}", FALLBACK, sourceDegraded, 600, 0.3);
    }

    @Test
    @DisplayName("Happy path returns a schema-valid OK payload and settles the budget")
    void happyPath() {
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willReturn(new LlmCompletion(VALID, new TokenUsage(1, 100, 60, 0)));
        RuntimeRunContext ctx = context(generousBudget());
        RuntimeStep.Structured result = complete(ctx, false);
        assertThat(result.schemaValid()).isTrue();
        assertThat(result.status()).isEqualTo(ResultStatus.OK);
        assertThat(ctx.ledger().snapshot().used().requests()).isEqualTo(1);
    }

    @Test
    @DisplayName("Non-authoritative source is surfaced as DEGRADED even when valid")
    void degradedSource() {
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willReturn(new LlmCompletion(VALID, new TokenUsage(1, 100, 60, 0)));
        RuntimeStep.Structured result = complete(context(generousBudget()), true);
        assertThat(result.status()).isEqualTo(ResultStatus.DEGRADED);
        assertThat(result.degradedReasons()).contains("non_authoritative_source");
    }

    @Test
    @DisplayName("Schema-invalid output fails closed to the deterministic fallback")
    void schemaInvalid() {
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willReturn(new LlmCompletion("{}", new TokenUsage(1, 10, 5, 0)));
        RuntimeStep.Structured result = complete(context(generousBudget()), false);
        assertThat(result.schemaValid()).isFalse();
        assertThat(result.status()).isEqualTo(ResultStatus.DEGRADED);
        assertThat(result.degradedReasons()).contains("schema_invalid");
        assertThat(result.payloadJson()).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("Provider failure degrades to the fallback without throwing")
    void llmFailure() {
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willThrow(new IllegalStateException("provider down"));
        RuntimeStep.Structured result = complete(context(generousBudget()), false);
        assertThat(result.degradedReasons()).contains("llm_unavailable");
        assertThat(result.payloadJson()).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("Budget hard stop occurs before any provider call")
    void budgetHardStop() {
        RunBudget tiny = new RunBudget(100, 24, Duration.ofMinutes(3), 500_000, 2_500, 10_000);
        RuntimeStep.Structured result = complete(context(tiny), false);
        assertThat(result.status()).isEqualTo(ResultStatus.DEGRADED);
        assertThat(result.degradedReasons()).anyMatch(r -> r.startsWith("budget_"));
        assertThat(result.usage()).isEqualTo(TokenUsage.ZERO);
    }

    @Test
    @DisplayName("callTool counts against the brakes and strips fenced JSON")
    void callToolAndFences() {
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willReturn(new LlmCompletion("```json\n" + VALID + "\n```", new TokenUsage(1, 10, 5, 0)));
        RuntimeRunContext ctx = context(generousBudget());
        ToolResult<InventorySnapshot> tr = step.callTool(ctx, AgentRuntimeEngine.INVENTORY_AGENT,
                new FixtureInventoryTool(mapper, clock));
        assertThat(tr.status().hasData()).isTrue();
        assertThat(ctx.brakes().toolCallCount()).isEqualTo(1);
        RuntimeStep.Structured result = complete(ctx, false);
        assertThat(result.schemaValid()).isTrue();
    }

    @Test
    @DisplayName("Agent-round brake trips after the ceiling is exceeded")
    void agentRoundBrake() {
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willReturn(new LlmCompletion(VALID, new TokenUsage(1, 10, 5, 0)));
        RuntimeRunContext ctx = new RuntimeRunContext("run-1", "R1", LocalDate.of(2026, 6, 19), "azure-openai",
                new BudgetLedger(generousBudget()), new RunBrakes(4, 1, NOW.plusSeconds(120)), new WorkingMemory());
        complete(ctx, false);
        assertThatThrownBy(() -> complete(ctx, false)).isInstanceOf(BrakeTrippedException.class);
    }

    @Test
    @DisplayName("envelope and message projections carry prompt refs and status")
    void projections() {
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willReturn(new LlmCompletion(VALID, new TokenUsage(1, 10, 5, 0)));
        RuntimeStep.Structured result = complete(context(generousBudget()), false);
        var envelope = step.envelope(AgentRuntimeEngine.INVENTORY_AGENT, PromptRegistry.INVENTORY_ANALYSIS,
                "azure-openai", "gpt-4o", result, java.util.List.of(), null);
        assertThat(envelope.promptId()).isEqualTo("inventory.analysis@v1");
        assertThat(envelope.promptHash()).isNotBlank();
        assertThat(step.message(AgentRuntimeEngine.INVENTORY_AGENT, result).getAgentName())
                .isEqualTo(AgentRuntimeEngine.INVENTORY_AGENT);
    }
}
