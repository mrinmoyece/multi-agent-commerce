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
 * Customer Sentiment Specialist Agent.
 *
 * Analyses customer reviews, support tickets, returns data, and social signals
 * to surface satisfaction trends, emerging issues, and NPS drivers.
 *
 * Cross-agent value: coordinator can spot correlations like
 * "high returns on product X + negative sentiment = potential quality issue"
 * that neither agent would surface alone.
 */
@Component("sentimentAgent")
@RequiredArgsConstructor
@Slf4j
public class SentimentAgent implements CommerceAgent {

    private final OpenAIClient openAIClient;

    @Value("${llm.chat-deployment:gpt-4o}")
    private String chatDeployment;

    @Override
    public String getName() {
        return "SentimentAgent";
    }

    @Override
    public AgentMessage analyse(DailyAnalysisContext ctx) {
        log.info("[SentimentAgent] Analysing customer sentiment for retailer={} date={}",
                ctx.getRetailerId(), ctx.getDate());

        // NOTE: there is no live reviews/support-ticket/social feed wired up here.
        // The LLM is asked to generate plausible sentiment themes from just the date
        // and retailer ID. This is demo/portfolio behaviour, not a real data feed —
        // see "Simulated Data & Current Limitations" in the README.
        String findings = callLLM(ctx);

        log.info("[SentimentAgent] Analysis complete — {} chars", findings.length());

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
     * Calls the LLM to extrapolate a sentiment analysis from the given context.
     * Protected by the "llm" circuit breaker; {@link #callLLMFallback} returns a safe
     * degraded response when the LLM is unavailable or the circuit is open.
     *
     * Package-private (not {@code private}) so the Resilience4j AOP proxy can
     * intercept the call — Spring's proxy-based AOP cannot advise private methods.
     */
    @CircuitBreaker(name = "llm", fallbackMethod = "callLLMFallback")
    String callLLM(DailyAnalysisContext ctx) {
        String prompt = """
                You are a customer experience analyst for a major UK retailer.
                Date: %s | Retailer: %s

                Analyse current customer sentiment:
                1. Identify the top 3 positive sentiment themes driving customer satisfaction
                2. Identify the top 3 negative themes requiring urgent attention
                3. Flag any trending complaints that could escalate (social media risk)
                4. Note any product categories with unusually high return rates
                5. Recommend one customer communication action for today

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
        log.warn("[SentimentAgent] LLM unavailable ({}), returning degraded summary", t.getMessage());
        return "Sentiment analysis unavailable — LLM circuit breaker open or call failed. "
                + "Manual review of reviews/support tickets recommended for retailer "
                + ctx.getRetailerId() + ".";
    }
}
