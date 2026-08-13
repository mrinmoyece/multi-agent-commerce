package com.agentforge.commerce.llm;

import com.agentforge.commerce.config.LLMConfig;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.CompletionsUsage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmGateway {

    private final OpenAIClient openAIClient;
    private final LLMConfig config;

    @Value("${llm.chat-deployment:gpt-4o}")
    private String chatDeployment;

    @Retry(name = "llm")
    @CircuitBreaker(name = "llm")
    public String complete(String prompt, int maxTokens, double temperature) {
        return completeOn(chatDeployment, prompt, maxTokens, temperature).content();
    }

    /**
     * Complete against an explicit model/deployment and return the validated
     * content together with provider-reported token usage. Used by the agent
     * runtime for per-agent model selection and budget accounting. Retries and
     * circuit-breaking are owned by the same {@code llm} policies as {@link #complete}.
     */
    @Retry(name = "llm")
    @CircuitBreaker(name = "llm")
    public LlmCompletion completeWithUsage(String model, String prompt, int maxTokens, double temperature) {
        String deployment = (model == null || model.isBlank()) ? chatDeployment : model;
        return completeOn(deployment, prompt, maxTokens, temperature);
    }

    private LlmCompletion completeOn(String deployment, String prompt, int maxTokens, double temperature) {
        var options = new ChatCompletionsOptions(List.of(new ChatRequestUserMessage(prompt)));
        options.setMaxTokens(maxTokens);
        options.setTemperature(temperature);

        ChatCompletions response = openAIClient.getChatCompletions(deployment, options);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            throw new IllegalStateException("LLM returned no completion");
        }
        String content = validateContent(response.getChoices().get(0).getMessage().getContent());
        return new LlmCompletion(content, usageOf(response));
    }

    private static TokenUsage usageOf(ChatCompletions response) {
        CompletionsUsage usage = response.getUsage();
        if (usage == null) {
            return new TokenUsage(1, 0, 0, 0);
        }
        return new TokenUsage(1,
                Math.max(0, usage.getPromptTokens()),
                Math.max(0, usage.getCompletionTokens()),
                0);
    }

    String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM returned an empty completion");
        }
        if (content.length() > config.getMaxResponseCharacters()) {
            throw new IllegalStateException("LLM completion exceeded the configured response limit");
        }
        return content;
    }
}
