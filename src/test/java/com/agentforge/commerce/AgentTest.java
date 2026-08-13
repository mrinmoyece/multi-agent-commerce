package com.agentforge.commerce;

import com.agentforge.commerce.agent.InventoryAgent;
import com.agentforge.commerce.agent.PricingAgent;
import com.agentforge.commerce.agent.SentimentAgent;
import com.agentforge.commerce.llm.LlmGateway;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.tool.InventoryDataTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AgentTest {

    private LlmGateway llmGateway;
    private DailyAnalysisContext context;

    @BeforeEach
    void setUp() {
        llmGateway = mock(LlmGateway.class);
        context = DailyAnalysisContext.builder()
                .date(LocalDate.of(2026, 8, 12))
                .retailerId("retailer-1")
                .build();
    }

    @Test
    void inventoryAgentMarksCriticalInventoryHighPriority() {
        given(llmGateway.complete(anyString(), anyInt(), anyDouble())).willReturn("{\"summary\":\"ok\"}");
        InventoryAgent agent = new InventoryAgent(new InventoryDataTool(), llmGateway);

        AgentMessage message = agent.analyse(context);

        assertThat(message.getAgentName()).isEqualTo("InventoryAgent");
        assertThat(message.getPriority()).isEqualTo(AgentMessage.AgentMessagePriority.HIGH);
        assertThat(message.getContent()).contains("summary");
    }

    @Test
    void inventoryAgentReturnsExplicitDegradedResponse() {
        given(llmGateway.complete(anyString(), anyInt(), anyDouble()))
                .willThrow(new IllegalStateException("provider included secret text"));
        InventoryAgent agent = new InventoryAgent(new InventoryDataTool(), llmGateway);

        AgentMessage message = agent.analyse(context);

        assertThat(message.getContent())
                .contains("LLM unavailable")
                .doesNotContain("secret text");
    }

    @Test
    void pricingAgentReturnsFindings() {
        given(llmGateway.complete(anyString(), anyInt(), anyDouble())).willReturn("pricing findings");

        AgentMessage message = new PricingAgent(llmGateway).analyse(context);

        assertThat(message.getAgentName()).isEqualTo("PricingAgent");
        assertThat(message.getContent()).isEqualTo("pricing findings");
    }

    @Test
    void sentimentAgentReturnsExplicitDegradedResponse() {
        given(llmGateway.complete(anyString(), anyInt(), anyDouble()))
                .willThrow(new IllegalStateException("provider included secret text"));

        AgentMessage message = new SentimentAgent(llmGateway).analyse(context);

        assertThat(message.getContent())
                .contains("LLM circuit breaker")
                .doesNotContain("secret text");
    }
}
