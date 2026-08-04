package com.agentforge.commerce;

import com.agentforge.commerce.agent.InventoryAgent;
import com.agentforge.commerce.agent.PricingAgent;
import com.agentforge.commerce.agent.SentimentAgent;
import com.agentforge.commerce.coordinator.AgentCoordinator;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.model.DailyBriefing;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Mock OpenAIClient   openAIClient;
    @Spy  ObjectMapper   objectMapper = new ObjectMapper();
    // Synchronous stand-in for the "commerce-agent-" pool — runs supplyAsync tasks
    // on the calling thread so tests stay deterministic. @Spy (rather than @Mock)
    // so the real execute(Runnable) implementation below actually runs the task.
    @Spy  Executor applicationTaskExecutor = Runnable::run;

    @InjectMocks
    AgentCoordinator coordinator;

    private DailyAnalysisContext context;

    @BeforeEach
    void setUp() {
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
        given(inventoryAgent.getName()).willReturn("InventoryAgent");
        given(pricingAgent.analyse(context)).willReturn(agentMessage("PricingAgent",
                "{\"opportunities\":[\"Competitor gap on SKU-009\"],\"summary\":\"3 pricing gaps found\"}"));
        given(pricingAgent.getName()).willReturn("PricingAgent");
        given(sentimentAgent.analyse(context)).willReturn(agentMessage("SentimentAgent",
                "{\"nps\":72,\"topTheme\":\"Delivery speed\",\"summary\":\"Sentiment up 4 pts\"}"));
        given(sentimentAgent.getName()).willReturn("SentimentAgent");

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
        verify(openAIClient).getChatCompletions(anyString(), any(ChatCompletionsOptions.class));
    }

    @Test
    @DisplayName("One agent fails → analysis continues with remaining agents")
    void oneAgentFails_analysisStillCompletes() throws ExecutionException, InterruptedException {
        // given — inventory agent throws; others succeed
        given(inventoryAgent.getName()).willReturn("InventoryAgent");
        given(inventoryAgent.analyse(context))
                .willThrow(new RuntimeException("WMS connection timeout"));
        given(pricingAgent.analyse(context)).willReturn(agentMessage("PricingAgent", "Pricing OK"));
        given(pricingAgent.getName()).willReturn("PricingAgent");
        given(sentimentAgent.analyse(context)).willReturn(agentMessage("SentimentAgent", "Sentiment OK"));
        given(sentimentAgent.getName()).willReturn("SentimentAgent");

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
    @DisplayName("Malformed synthesis JSON → briefing returned with raw content as summary")
    void malformedSynthesisJson_fallsBackGracefully() throws ExecutionException, InterruptedException {
        given(inventoryAgent.analyse(context)).willReturn(agentMessage("InventoryAgent", "OK"));
        given(inventoryAgent.getName()).willReturn("InventoryAgent");
        given(pricingAgent.analyse(context)).willReturn(agentMessage("PricingAgent", "OK"));
        given(pricingAgent.getName()).willReturn("PricingAgent");
        given(sentimentAgent.analyse(context)).willReturn(agentMessage("SentimentAgent", "OK"));
        given(sentimentAgent.getName()).willReturn("SentimentAgent");

        stubLlmSynthesis("This is not JSON — the LLM returned prose instead.");

        // when — must NOT throw
        CompletableFuture<DailyBriefing> future = coordinator.runDailyAnalysis(context);
        DailyBriefing briefing = future.get();

        assertThat(briefing).isNotNull();
        assertThat(briefing.getExecutiveSummary()).isNotBlank();
        assertThat(briefing.getGeneratedAt()).isNotNull();
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
        ChatResponseMessage msg = mock(ChatResponseMessage.class);
        given(msg.getContent()).willReturn(jsonResponse);
        ChatChoice choice = mock(ChatChoice.class);
        given(choice.getMessage()).willReturn(msg);
        ChatCompletions completions = mock(ChatCompletions.class);
        given(completions.getChoices()).willReturn(List.of(choice));
        given(openAIClient.getChatCompletions(anyString(), any(ChatCompletionsOptions.class)))
                .willReturn(completions);
    }
}
