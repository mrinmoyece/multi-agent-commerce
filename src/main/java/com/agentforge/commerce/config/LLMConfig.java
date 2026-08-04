package com.agentforge.commerce.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Data
public class LLMConfig {

    private String provider = "github-models";
    private String endpoint;
    private String apiKey;
    private String chatDeployment = "gpt-4o";

    private static final String GITHUB_MODELS_ENDPOINT = "https://models.inference.ai.azure.com";

    @Bean
    public OpenAIClient openAIClient() {
        return switch (provider) {
            case "azure-openai" -> new OpenAIClientBuilder()
                    .endpoint(endpoint)
                    .credential(new AzureKeyCredential(apiKey))
                    .buildClient();
            case "github-models" -> {
                String token = System.getenv("GITHUB_TOKEN");
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException(
                            "GITHUB_TOKEN not set. Required for github-models provider.");
                }
                yield new OpenAIClientBuilder()
                        .endpoint(GITHUB_MODELS_ENDPOINT)
                        .credential(new KeyCredential(token))
                        .buildClient();
            }
            default -> throw new IllegalArgumentException("Unknown llm.provider: " + provider);
        };
    }

    /**
     * Shared ObjectMapper bean — thread-safe singleton, injected across all components.
     * Configured with JavaTimeModule for Java 8+ date/time serialisation.
     * Marked @Primary so Spring prefers this over the auto-configured one.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
