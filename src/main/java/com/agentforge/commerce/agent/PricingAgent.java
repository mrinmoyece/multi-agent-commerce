package com.agentforge.commerce.agent;

import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pricing Specialist Agent.
 *
 * Analyses competitor pricing, current margin data, and price elasticity signals
 * to surface repricing opportunities and margin risks.
 *
 * Findings are returned as an AgentMessage consumed by the AgentCoordinator
 * for cross-agent synthesis.
 */
@Component("pricingAgent")
@RequiredArgsConstructor
@Slf4j
public class PricingAgent implements CommerceAgent {

    private final OpenAIClient openAIClient;

    @Value("${llm.chat-deployment:gpt-4o}")
    private String chatDeployment;

    @Override
    public String getName() {
        return "PricingAgent";
    }

    @Override
    public AgentMessage analyse(DailyAnalysisContext ctx) {
        log.info("[PricingAgent] Analysing pricing data for retailer={} date={}",
                ctx.getRetailerId(), ctx.getDate());

        // NOTE: there is no live competitor-pricing or ERP-margin feed wired up here.
        // The LLM is asked to extrapolate a plausible pricing analysis from just the
        // date and retailer ID. This is demo/portfolio behaviour, not a real data feed —
        // see "Simulated Data & Current Limitations" in the README.
        String findings = callLLM(ctx);

        log.info("[PricingAgent] Analysis complete — {} chars", findings.length());

        return AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .agentName(getName())
                .messageType(AgentMessage.MessageType.FINDINGS)
                .priority(AgentMessage.AgentMessagePriority.NORMAL)
                .content(findings)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Calls the LLM to extrapolate a pricing analysis from the given context.
     * Protected by the "llm" circuit breaker; {@link #callLLMFallback} returns a safe
     * degraded response when the LLM is unavailable or the circuit is open.
     *
     * Package-private (not {@code private}) so the Resilience4j AOP proxy can
     * intercept the call — Spring's proxy-based AOP cannot advise private methods.
     */
    @CircuitBreaker(name = "llm", fallbackMethod = "callLLMFallback")
    String callLLM(DailyAnalysisContext ctx) {
        String prompt = """
                You are a pricing analyst for a major UK retailer.
                Date: %s | Retailer: %s

                Analyse the current pricing situation:
                1. Identify 3–5 products where price adjustment would improve margin or volume
                2. Flag any products where competitor undercutting is a risk
                3. Note any seasonal pricing opportunities for this time of year
                4. Calculate estimated margin impact of recommended changes

                Return a concise analysis (200–300 words) for the daily coordinator briefing.
                """.formatted(ctx.getDate(), ctx.getRetailerId());

        var options = new ChatCompletionsOptions(List.of(new ChatRequestUserMessage(prompt)));
        options.setMaxTokens(400);
        options.setTemperature(0.4);

        return openAIClient.getChatCompletions(chatDeployment, options)
                .getChoices().get(0).getMessage().getContent();
    }

    /** Fallback when the "llm" circuit breaker is open or the LLM call fails. */
    String callLLMFallback(DailyAnalysisContext ctx, Throwable t) {
        log.warn("[PricingAgent] LLM unavailable ({}), returning degraded summary", t.getMessage());
        return "Pricing analysis unavailable — LLM circuit breaker open or call failed. "
                + "Manual pricing review recommended for retailer " + ctx.getRetailerId() + ".";
    }
}
