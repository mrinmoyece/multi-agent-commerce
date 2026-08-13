package com.agentforge.commerce.llm;

import com.agentforge.commerce.config.LLMConfig;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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
        var options = new ChatCompletionsOptions(List.of(new ChatRequestUserMessage(prompt)));
        options.setMaxTokens(maxTokens);
        options.setTemperature(temperature);

        var response = openAIClient.getChatCompletions(chatDeployment, options);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            throw new IllegalStateException("LLM returned no completion");
        }

        return validateContent(response.getChoices().get(0).getMessage().getContent());
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
