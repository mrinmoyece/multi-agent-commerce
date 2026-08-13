package com.agentforge.commerce.platform.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentforge.commerce.coordinator.AgentCoordinator;
import com.agentforge.commerce.coordinator.AgentCoordinator.OrchestrationResult;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.platform.support.StubSecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StubSecurityConfig.class)
class PlatformApiIntegrationTest {

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

    private void stubSuccessfulOrchestration() {
        DailyBriefing briefing = DailyBriefing.builder()
                .retailerId("acme")
                .executiveSummary("All nominal")
                .crossInsights(List.of("Insight A"))
                .recommendedActions(List.of("Reprice SKU-1", "Restock SKU-2"))
                .urgentAlerts(List.of())
                .build();
        AgentMessage msg = AgentMessage.builder()
                .agentName("InventoryAgent")
                .messageType(AgentMessage.MessageType.FINDINGS)
                .priority(AgentMessage.AgentMessagePriority.NORMAL)
                .content("Stock healthy")
                .timestamp(Instant.now())
                .build();
        org.mockito.Mockito.when(coordinator.orchestrate(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OrchestrationResult(briefing, List.of(msg)));
    }

    private JsonNode createRun(String retailerId, RequestPostProcessor auth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/analysis-runs")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retailerId\":\"" + retailerId + "\"}"))
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
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/analysis-runs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insufficientScopeIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-runs")
                        .with(reader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retailerId\":\"acme\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationFailureReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-runs")
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retailerId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownRunReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/analysis-runs/" + UUID.randomUUID()).with(reader()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createRunExecutesAndCompletes() throws Exception {
        stubSuccessfulOrchestration();
        JsonNode created = createRun("acme", writer());
        UUID runId = UUID.fromString(created.get("id").asText());

        JsonNode completed = awaitStatus(runId, "SUCCEEDED");
        org.assertj.core.api.Assertions.assertThat(completed.get("executiveSummary").asText())
                .isEqualTo("All nominal");
        org.assertj.core.api.Assertions.assertThat(completed.get("recommendedActions")).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(completed.get("agentResults")).hasSize(1);
    }

    @Test
    void idempotentReplayReturnsSameRun() throws Exception {
        stubSuccessfulOrchestration();
        String key = "it-key-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/api/v1/analysis-runs")
                        .with(writer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retailerId\":\"acme\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult second = mockMvc.perform(post("/api/v1/analysis-runs")
                        .with(writer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retailerId\":\"acme\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("id").asText();

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void listRunsIsPaginated() throws Exception {
        stubSuccessfulOrchestration();
        createRun("pageco", writer());
        createRun("pageco", writer());

        mockMvc.perform(get("/api/v1/analysis-runs")
                        .with(reader())
                        .param("page", "0")
                        .param("size", "1")
                        .param("retailerId", "pageco"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void approvalDecisionLifecycle() throws Exception {
        stubSuccessfulOrchestration();
        JsonNode created = createRun("approveco", writer());
        UUID runId = UUID.fromString(created.get("id").asText());
        awaitStatus(runId, "SUCCEEDED");

        MvcResult approvalsResult = mockMvc.perform(
                        get("/api/v1/analysis-runs/" + runId + "/approvals").with(reader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode approvals = objectMapper.readTree(approvalsResult.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(approvals).isNotEmpty();
        String approvalId = approvals.get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/approvals/" + approvalId + "/approve")
                        .with(approver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("approver"));

        // A second decision on the same approval is a conflict.
        mockMvc.perform(post("/api/v1/approvals/" + approvalId + "/reject")
                        .with(approver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"changed mind\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void approvalRequiresWriteScope() throws Exception {
        mockMvc.perform(post("/api/v1/approvals/" + UUID.randomUUID() + "/approve")
                        .with(reader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void correlationIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/analysis-runs")
                        .with(reader())
                        .header("X-Correlation-Id", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Correlation-Id", "trace-123"));
    }
}
