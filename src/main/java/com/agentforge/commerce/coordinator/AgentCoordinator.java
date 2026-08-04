package com.agentforge.commerce.coordinator;

import com.agentforge.commerce.agent.CommerceAgent;
import com.agentforge.commerce.agent.InventoryAgent;
import com.agentforge.commerce.agent.PricingAgent;
import com.agentforge.commerce.agent.SentimentAgent;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.model.DailyBriefing;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Coordinator Agent — the "manager" of the multi-agent system.
 *
 * Responsibilities:
 * 1. Dispatch all specialist agents (Inventory, Pricing, Sentiment) IN PARALLEL
 * 2. Collect their individual findings
 * 3. Synthesise a unified daily executive briefing via a second LLM call
 * 4. Surface cross-agent insights no single specialist could see alone
 *
 * The coordinator does NOT implement domain logic — only orchestrates and synthesises.
 * Domain logic lives in each specialist agent.
 *
 * Scheduling: runs at 06:00 daily (configurable via briefing.cron).
 * There is currently no on-demand HTTP trigger — see README "Simulated Data & Current Limitations".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentCoordinator {

    private final InventoryAgent inventoryAgent;
    private final PricingAgent   pricingAgent;
    private final SentimentAgent sentimentAgent;
    private final OpenAIClient   openAIClient;
    private final ObjectMapper   objectMapper;   // injected bean — thread-safe, not re-created inline

    /**
     * Spring Boot's auto-configured task executor, built from the
     * {@code spring.task.execution.pool} properties in application.yml
     * (tuned size, "commerce-agent-" thread name prefix). Passed explicitly to
     * every {@code supplyAsync} call below so agent dispatch actually uses the
     * tuned pool instead of silently falling back to the JVM's default common pool.
     */
    @Qualifier("applicationTaskExecutor")
    private final Executor applicationTaskExecutor;

    @Value("${llm.chat-deployment:gpt-4o}")
    private String chatDeployment;

    @Value("${retailer.id:default-retailer}")
    private String retailerId;

    // ── Scheduled entry point ────────────────────────────────────────────────

    /** Runs at 06:00 daily. Override via {@code briefing.cron} in application.yml. */
    @Scheduled(cron = "${briefing.cron:0 0 6 * * *}")
    public void scheduledDailyAnalysis() {
        runDailyAnalysis(DailyAnalysisContext.builder()
                .date(LocalDate.now())
                .retailerId(retailerId)
                .build());
    }

    // ── Core orchestration ───────────────────────────────────────────────────

    @Async
    public CompletableFuture<DailyBriefing> runDailyAnalysis(DailyAnalysisContext context) {
        log.info("Coordinator: starting daily analysis — retailer={} date={}",
                context.getRetailerId(), context.getDate());

        // PHASE 1: All specialist agents run in PARALLEL, dispatched on the tuned
        // "commerce-agent-" pool (application.yml: spring.task.execution) rather than
        // the JVM's default common pool.
        CompletableFuture<AgentMessage> inventoryFuture = CompletableFuture
                .supplyAsync(() -> safeAnalyse(inventoryAgent, context), applicationTaskExecutor);
        CompletableFuture<AgentMessage> pricingFuture   = CompletableFuture
                .supplyAsync(() -> safeAnalyse(pricingAgent, context), applicationTaskExecutor);
        CompletableFuture<AgentMessage> sentimentFuture = CompletableFuture
                .supplyAsync(() -> safeAnalyse(sentimentAgent, context), applicationTaskExecutor);

        try {
            // allOf(...).join() already blocks until every future has completed (or
            // throws on the first failure), so the .get() calls below are reading
            // already-completed futures — they are not a second wait.
            CompletableFuture.allOf(inventoryFuture, pricingFuture, sentimentFuture).join();

            AgentMessage inventory = inventoryFuture.get();
            AgentMessage pricing   = pricingFuture.get();
            AgentMessage sentiment = sentimentFuture.get();

            log.info("All agents reported — synthesising briefing");

            // PHASE 2: LLM synthesises cross-agent insights
            DailyBriefing briefing = synthesise(context, inventory, pricing, sentiment);

            log.info("Daily briefing ready — {} actions, {} alerts",
                    briefing.getRecommendedActions() != null ? briefing.getRecommendedActions().size() : 0,
                    briefing.getUrgentAlerts()       != null ? briefing.getUrgentAlerts().size()       : 0);

            return CompletableFuture.completedFuture(briefing);

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Coordinator daily analysis failed: {}", e.getMessage(), e);
            throw new DailyAnalysisException("Daily analysis failed for retailer "
                    + context.getRetailerId() + " on " + context.getDate(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Wraps agent.analyse() so a single failing agent does not abort the whole run.
     * Returns an empty COMPLETE message if the agent throws.
     */
    private AgentMessage safeAnalyse(CommerceAgent agent, DailyAnalysisContext ctx) {
        try {
            return agent.analyse(ctx);
        } catch (Exception e) {
            log.warn("[Coordinator] {} failed: {} — proceeding without it",
                    agent.getName(), e.getMessage());
            return AgentMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .agentName(agent.getName())
                    .messageType(AgentMessage.MessageType.COMPLETE)
                    .priority(AgentMessage.AgentMessagePriority.NORMAL)
                    .content("Agent unavailable: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }

    private DailyBriefing synthesise(DailyAnalysisContext ctx,
                                      AgentMessage inventory,
                                      AgentMessage pricing,
                                      AgentMessage sentiment) {
        String prompt = """
                You are the Chief of Staff for a major UK retail operation.
                Three specialist AI agents have submitted their daily analysis for %s:

                INVENTORY AGENT:
                %s

                PRICING AGENT:
                %s

                CUSTOMER SENTIMENT AGENT:
                %s

                Your job:
                1. Identify cross-cutting insights (e.g. low stock + high demand + good sentiment = restock urgently)
                2. List the top 5 recommended actions for today, in priority order
                3. Flag any urgent issues requiring immediate attention
                4. Write a 3-sentence executive summary for the daily leadership standup

                Return JSON only:
                {"crossInsights":[],"recommendedActions":[],"urgentAlerts":[],"executiveSummary":""}
                """.formatted(ctx.getDate(),
                inventory.getContent(), pricing.getContent(), sentiment.getContent());

        var options = new ChatCompletionsOptions(List.of(new ChatRequestUserMessage(prompt)));
        options.setMaxTokens(1500);
        options.setTemperature(0.2);

        String raw = openAIClient.getChatCompletions(chatDeployment, options)
                .getChoices().get(0).getMessage().getContent()
                .replaceAll("(?s)```json\\s*|```\\s*$", "")
                .trim();

        return parseBriefing(ctx, inventory, pricing, sentiment, raw);
    }

    private DailyBriefing parseBriefing(DailyAnalysisContext ctx,
                                         AgentMessage inventory,
                                         AgentMessage pricing,
                                         AgentMessage sentiment,
                                         String rawSynthesis) {
        List<String> actions      = List.of();
        List<String> alerts       = List.of();
        List<String> crossInsights = List.of();
        String summary = rawSynthesis;

        try {
            // Single injected bean — no new ObjectMapper() inline
            var node = objectMapper.readTree(rawSynthesis);
            if (node.has("recommendedActions")) {
                actions = List.of(objectMapper.treeToValue(node.get("recommendedActions"), String[].class));
            }
            if (node.has("urgentAlerts")) {
                alerts = List.of(objectMapper.treeToValue(node.get("urgentAlerts"), String[].class));
            }
            if (node.has("crossInsights")) {
                crossInsights = List.of(objectMapper.treeToValue(node.get("crossInsights"), String[].class));
            }
            if (node.has("executiveSummary")) {
                summary = node.get("executiveSummary").asText();
            }
        } catch (Exception e) {
            log.warn("Could not parse coordinator synthesis JSON: {}", e.getMessage());
        }

        return DailyBriefing.builder()
                .briefingId(UUID.randomUUID().toString())
                .date(ctx.getDate())
                .retailerId(ctx.getRetailerId())
                .inventoryFindings(inventory.getContent())
                .pricingFindings(pricing.getContent())
                .sentimentFindings(sentiment.getContent())
                .crossInsights(crossInsights)
                .recommendedActions(actions)
                .urgentAlerts(alerts)
                .executiveSummary(summary)
                .rawSynthesis(rawSynthesis)
                .generatedAt(Instant.now())
                .build();
    }
}
