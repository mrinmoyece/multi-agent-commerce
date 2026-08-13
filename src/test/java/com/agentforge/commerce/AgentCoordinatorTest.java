package com.agentforge.commerce;

import com.agentforge.commerce.agent.InventoryAgent;
import com.agentforge.commerce.agent.PricingAgent;
import com.agentforge.commerce.agent.SentimentAgent;
import com.agentforge.commerce.coordinator.AgentCoordinator;
import com.agentforge.commerce.coordinator.DailyAnalysisException;
import com.agentforge.commerce.llm.LlmGateway;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.model.DailyBriefing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentCoordinator.
 *
 * Validates:
 * - All 3 specialist agents are invoked in parallel
 * - LLM synthesis call is made after all agents report
 * - A failing agent does NOT abort the whole analysis (safeAnalyse fallback)
 * - DailyBriefing is populated correctly from LLM JSON
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentCoordinator — unit tests")
class AgentCoordinatorTest {

    @Mock InventoryAgent inventoryAgent;
    @Mock PricingAgent   pricingAgent;
    @Mock SentimentAgent sentimentAgent;
    @Mock LlmGateway     llmGateway;
    @Spy  ObjectMapper   objectMapper = new ObjectMapper();
    @Mock Executor applicationTaskExecutor;

    @InjectMocks
    AgentCoordinator coordinator;

    private DailyAnalysisContext context;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(applicationTaskExecutor).execute(any(Runnable.class));
        context = DailyAnalysisContext.builder()
                .date(LocalDate.of(2026, 6, 19))
                .retailerId("RETAILER-UK-001")
                .build();
    }

    @Test
    @DisplayName("All 3 agents report → LLM synthesises → DailyBriefing returned")
    void allAgentsSucceed_briefingReturned() throws ExecutionException, InterruptedException {
        // given — all specialist agents return findings
        given(inventoryAgent.analyse(context)).willReturn(agentMessage("InventoryAgent",
                "{\"risks\":[\"6 critical SKUs\"],\"summary\":\"Restock urgently\"}"));
        given(pricingAgent.analyse(context)).willReturn(agentMessage("PricingAgent",
                "{\"opportunities\":[\"Competitor gap on SKU-009\"],\"summary\":\"3 pricing gaps found\"}"));
        given(sentimentAgent.analyse(context)).willReturn(agentMessage("SentimentAgent",
                "{\"nps\":72,\"topTheme\":\"Delivery speed\",\"summary\":\"Sentiment up 4 pts\"}"));

        stubLlmSynthesis("""
                {
                  "crossInsights": ["Low stock + high NPS on fresh produce = restock before weekend"],
                  "recommendedActions": ["Reorder SKU-0001 immediately", "Adjust pricing on SKU-009"],
                  "urgentAlerts": ["6 SKUs under 7 days stock"],
                  "executiveSummary": "Strong trading day with 6 critical stock alerts needing immediate action."
                }
                """);

        // when
        CompletableFuture<DailyBriefing> future = coordinator.runDailyAnalysis(context);
        DailyBriefing briefing = future.get();

        // then
        assertThat(briefing).isNotNull();
        assertThat(briefing.getRecommendedActions()).hasSize(2);
        assertThat(briefing.getUrgentAlerts()).hasSize(1);
        assertThat(briefing.getExecutiveSummary()).contains("6 critical stock alerts");
        assertThat(briefing.getBriefingId()).isNotBlank();
        assertThat(briefing.getRetailerId()).isEqualTo("RETAILER-UK-001");

        verify(inventoryAgent).analyse(context);
        verify(pricingAgent).analyse(context);
        verify(sentimentAgent).analyse(context);
        verify(llmGateway).complete(anyString(), eq(1500), eq(0.2));
    }

    @Test
    @DisplayName("One agent fails → analysis continues with remaining agents")
    void oneAgentFails_analysisStillCompletes() throws ExecutionException, InterruptedException {
        // given — inventory agent throws; others succeed
        given(inventoryAgent.getName()).willReturn("InventoryAgent");
        given(inventoryAgent.analyse(context))
                .willThrow(new RuntimeException("WMS connection timeout"));
        given(pricingAgent.analyse(context)).willReturn(agentMessage("PricingAgent", "Pricing OK"));
        given(sentimentAgent.analyse(context)).willReturn(agentMessage("SentimentAgent", "Sentiment OK"));

        stubLlmSynthesis("""
                {
                  "crossInsights": [],
                  "recommendedActions": ["Check WMS connectivity"],
                  "urgentAlerts": ["Inventory data unavailable"],
                  "executiveSummary": "Partial analysis — inventory agent unavailable."
                }
                """);

        // when — must NOT throw despite inventory agent failure
        CompletableFuture<DailyBriefing> future = coordinator.runDailyAnalysis(context);
        DailyBriefing briefing = future.get();

        // then
        assertThat(briefing).isNotNull();
        assertThat(briefing.getInventoryFindings()).contains("Agent unavailable");
        assertThat(briefing.getRecommendedActions()).isNotEmpty();
    }

    @Test
    @DisplayName("Malformed synthesis JSON fails closed")
    void malformedSynthesisJson_failsClosed() {
        given(inventoryAgent.analyse(context)).willReturn(agentMessage("InventoryAgent", "OK"));
        given(pricingAgent.analyse(context)).willReturn(agentMessage("PricingAgent", "OK"));
        given(sentimentAgent.analyse(context)).willReturn(agentMessage("SentimentAgent", "OK"));

        stubLlmSynthesis("This is not JSON — the LLM returned prose instead.");

        assertThatThrownBy(() -> coordinator.runDailyAnalysis(context))
                .isInstanceOf(DailyAnalysisException.class)
                .hasMessageContaining("Daily analysis failed");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AgentMessage agentMessage(String agent, String content) {
        return AgentMessage.builder()
                .agentName(agent)
                .messageType(AgentMessage.MessageType.FINDINGS)
                .content(content)
                .timestamp(Instant.now())
                .build();
    }

    private void stubLlmSynthesis(String jsonResponse) {
        given(llmGateway.complete(anyString(), eq(1500), eq(0.2))).willReturn(jsonResponse);
    }
}
