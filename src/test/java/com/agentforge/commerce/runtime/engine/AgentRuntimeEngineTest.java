package com.agentforge.commerce.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.agentforge.commerce.config.LLMConfig;
import com.agentforge.commerce.llm.LlmCompletion;
import com.agentforge.commerce.llm.LlmGateway;
import com.agentforge.commerce.runtime.budget.ModelPolicy;
import com.agentforge.commerce.runtime.budget.RuntimeBudgets;
import com.agentforge.commerce.runtime.config.RuntimeProperties;
import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.memory.SemanticMemoryStore;
import com.agentforge.commerce.runtime.prompt.PromptRegistry;
import com.agentforge.commerce.runtime.telemetry.RuntimeTelemetry;
import com.agentforge.commerce.runtime.tools.fixture.FixtureInventoryTool;
import com.agentforge.commerce.runtime.tools.fixture.FixturePricingTool;
import com.agentforge.commerce.runtime.tools.fixture.FixtureSentimentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@link AgentRuntimeEngine}: end-to-end grounded specialist
 * runs, evidence-backed memory recording, coordinator synthesis and the
 * deterministic degraded fallback.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("Agent runtime engine")
class AgentRuntimeEngineTest {

    private static final Instant NOW = Instant.parse("2026-06-19T09:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 19);

    private static final String INVENTORY_JSON = "{\"risks\":[\"SKU-0001 critical\"],\"reorders\":[\"SKU-0006\"],"
            + "\"clearance\":[],\"citedSkus\":[\"SKU-0001\"],\"summary\":\"Inventory reviewed.\"}";
    private static final String PRICING_JSON = "{\"repricingOpportunities\":[\"SKU-0009\"],\"marginRisks\":[],"
            + "\"citedSkus\":[\"SKU-0009\"],\"summary\":\"Pricing reviewed.\"}";
    private static final String SENTIMENT_JSON = "{\"positiveThemes\":[\"Dairy\"],\"negativeThemes\":[],"
            + "\"citedTopics\":[\"Dairy\"],\"summary\":\"Sentiment reviewed.\"}";
    private static final String COORDINATOR_JSON = "{\"crossInsights\":[\"i\"],"
            + "\"recommendedActions\":[\"Reorder SKU-0001\"],\"urgentAlerts\":[\"SKU-0006 low\"],"
            + "\"citedReferences\":[\"SKU-0001\"],\"executiveSummary\":\"Daily briefing ready.\"}";

    @Mock
    LlmGateway llm;
    @Mock
    SemanticMemoryStore memory;

    private final RuntimeProperties properties = new RuntimeProperties();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private AgentRuntimeEngine engine;

    @BeforeEach
    void setUp() {
        LLMConfig llmConfig = new LLMConfig();
        RuntimeTelemetry telemetry = new RuntimeTelemetry(ObservationRegistry.create(), new SimpleMeterRegistry());
        engine = new AgentRuntimeEngine(new PromptRegistry(), llm, new ModelPolicy(properties),
                new RuntimeBudgets(properties), properties, llmConfig, mapper, memory, telemetry, clock,
                new FixtureInventoryTool(mapper, clock), new FixturePricingTool(mapper, clock),
                new FixtureSentimentTool(mapper, clock));
        given(llm.completeWithUsage(anyString(), anyString(), anyInt(), anyDouble()))
                .willAnswer(invocation -> {
                    String prompt = invocation.getArgument(1);
                    return new LlmCompletion(responseFor(prompt), new TokenUsage(1, 120, 80, 0));
                });
    }

    private static String responseFor(String prompt) {
        if (prompt.contains("INVENTORY_SNAPSHOT")) {
            return INVENTORY_JSON;
        }
        if (prompt.contains("PRICING_OBSERVATIONS")) {
            return PRICING_JSON;
        }
        if (prompt.contains("SENTIMENT_OBSERVATIONS")) {
            return SENTIMENT_JSON;
        }
        return COORDINATOR_JSON;
    }

    private RuntimeRunContext context() {
        return engine.newContext("run-1", "RETAILER-UK-001", DATE);
    }

    @Test
    @DisplayName("newContext validates the provider allowlist and builds a bounded context")
    void newContext() {
        RuntimeRunContext ctx = context();
        assertThat(ctx.runId()).isEqualTo("run-1");
        assertThat(ctx.provider()).isEqualTo("github-models");
        assertThat(ctx.deadline()).isAfter(NOW);
        assertThat(ctx.ledger()).isNotNull();
    }

    @Test
    @DisplayName("Inventory specialist grounds output and records an evidence-backed fact")
    void runInventory() {
        RuntimeRunContext ctx = context();
        SpecialistOutcome outcome = engine.runInventory(ctx);
        assertThat(outcome.envelope().agentName()).isEqualTo(AgentRuntimeEngine.INVENTORY_AGENT);
        assertThat(outcome.envelope().schemaValid()).isTrue();
        assertThat(outcome.envelope().promptId()).isEqualTo("inventory.analysis@v1");
        assertThat(outcome.envelope().provider()).isEqualTo("github-models");
        assertThat(outcome.envelope().isGrounded()).isTrue();
        assertThat(outcome.envelope().status()).isEqualTo(ResultStatus.DEGRADED);
        verify(memory, atLeastOnce()).write(any(), any());
    }

    @Test
    @DisplayName("Pricing and sentiment specialists produce schema-valid grounded envelopes")
    void runPricingAndSentiment() {
        RuntimeRunContext ctx = context();
        assertThat(engine.runPricing(ctx).envelope().schemaValid()).isTrue();
        assertThat(engine.runSentiment(ctx).envelope().isGrounded()).isTrue();
    }

    @Test
    @DisplayName("Coordinator synthesis assembles a briefing from validated specialist payloads")
    void synthesise() {
        RuntimeRunContext ctx = context();
        SpecialistOutcome inventory = engine.runInventory(ctx);
        SpecialistOutcome pricing = engine.runPricing(ctx);
        SpecialistOutcome sentiment = engine.runSentiment(ctx);
        AgentRuntimeEngine.SynthesisOutcome synthesis = engine.synthesise(ctx, inventory, pricing, sentiment);
        assertThat(synthesis.briefing().getExecutiveSummary()).isEqualTo("Daily briefing ready.");
        assertThat(synthesis.briefing().getRecommendedActions()).contains("Reorder SKU-0001");
        assertThat(synthesis.briefing().getUrgentAlerts()).contains("SKU-0006 low");
        assertThat(synthesis.envelope().agentName()).isEqualTo(AgentRuntimeEngine.COORDINATOR_AGENT);
        assertThat(synthesis.envelope().evidence()).isNotEmpty();
    }

    @Test
    @DisplayName("degradedOutcome returns a deterministic schema-valid fallback")
    void degradedOutcome() {
        SpecialistOutcome outcome = engine.degradedOutcome(AgentRuntimeEngine.PRICING_AGENT, "specialist_error");
        assertThat(outcome.envelope().status()).isEqualTo(ResultStatus.DEGRADED);
        assertThat(outcome.envelope().degradedReasons()).contains("specialist_error");
        assertThat(outcome.envelope().payloadJson()).contains("manual review required");
        assertThat(outcome.message().getContent()).contains("manual review required");
    }
}
