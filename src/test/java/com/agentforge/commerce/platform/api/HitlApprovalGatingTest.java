package com.agentforge.commerce.platform.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentforge.commerce.coordinator.AgentCoordinator;
import com.agentforge.commerce.coordinator.AgentCoordinator.OrchestrationResult;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.platform.support.StubSecurityConfig;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.engine.RuntimeRunReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * End-to-end human-in-the-loop test with risk gating enabled: a high-impact
 * pricing recommendation must pause the run in {@code AWAITING_APPROVAL} and
 * resume deterministically to {@code SUCCEEDED} once every approval is decided.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "runtime.risk.enabled=true")
@Import(StubSecurityConfig.class)
class HitlApprovalGatingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AgentCoordinator coordinator;

    private static RequestPostProcessor writer() {
        return jwt().jwt(b -> b.subject("writer")).authorities(
                new SimpleGrantedAuthority("SCOPE_runs:write"),
                new SimpleGrantedAuthority("SCOPE_runs:read"));
    }

    private static RequestPostProcessor reader() {
        return jwt().jwt(b -> b.subject("reader"))
                .authorities(new SimpleGrantedAuthority("SCOPE_runs:read"));
    }

    private static RequestPostProcessor approver() {
        return jwt().jwt(b -> b.subject("approver")).authorities(
                new SimpleGrantedAuthority("SCOPE_approvals:write"),
                new SimpleGrantedAuthority("SCOPE_runs:read"));
    }

    private void stubPricingOrchestration() {
        DailyBriefing briefing = DailyBriefing.builder()
                .retailerId("riskco")
                .executiveSummary("Pricing action recommended")
                .crossInsights(List.of("Competitor undercut on SKU-1"))
                .recommendedActions(List.of("Reprice SKU-1 down 8%"))
                .urgentAlerts(List.of())
                .build();
        AgentMessage msg = AgentMessage.builder()
                .agentName("PricingAgent")
                .messageType(AgentMessage.MessageType.FINDINGS)
                .priority(AgentMessage.AgentMessagePriority.HIGH)
                .content("{}")
                .timestamp(Instant.now())
                .build();
        AgentResultEnvelope envelope = new AgentResultEnvelope("PricingAgent", ResultStatus.OK, "pricing.analysis@v1",
                "hash", "github-models", "gpt-4o", "{}", List.of(), new Confidence(0.9, "r"),
                TokenUsage.ZERO, 5, true, List.of());
        RuntimeRunReport report = new RuntimeRunReport(List.of(envelope), envelope, TokenUsage.ZERO, null, 3, 4, false);
        OrchestrationResult result = new OrchestrationResult(briefing, List.of(msg), List.of(envelope), report);
        org.mockito.Mockito.when(coordinator.orchestrate(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(result);
    }

    private JsonNode createRun() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/analysis-runs")
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retailerId\":\"riskco\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode awaitStatus(UUID runId, String target) throws Exception {
        for (int i = 0; i < 100; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/analysis-runs/" + runId).with(reader()))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            if (target.equals(body.get("status").asText())) {
                return body;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Run " + runId + " did not reach " + target);
    }

    @Test
    void pricingActionPausesForApprovalThenResumes() throws Exception {
        stubPricingOrchestration();
        JsonNode created = createRun();
        UUID runId = UUID.fromString(created.get("id").asText());

        JsonNode paused = awaitStatus(runId, "AWAITING_APPROVAL");
        Assertions.assertThat(paused.get("status").asText()).isEqualTo("AWAITING_APPROVAL");

        MvcResult approvalsResult = mockMvc.perform(
                        get("/api/v1/analysis-runs/" + runId + "/approvals").with(reader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode approvals = objectMapper.readTree(approvalsResult.getResponse().getContentAsString());
        Assertions.assertThat(approvals).isNotEmpty();

        for (JsonNode approval : approvals) {
            String approvalId = approval.get("id").asText();
            mockMvc.perform(post("/api/v1/approvals/" + approvalId + "/approve")
                            .with(approver())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"approved\"}"))
                    .andExpect(status().isOk());
        }

        JsonNode resumed = awaitStatus(runId, "SUCCEEDED");
        Assertions.assertThat(resumed.get("status").asText()).isEqualTo("SUCCEEDED");
    }
}
