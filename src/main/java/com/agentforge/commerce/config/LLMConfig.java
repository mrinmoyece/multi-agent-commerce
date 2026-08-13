package com.agentforge.commerce.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Locale;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LLMConfig {

    private String provider = "github-models";
    private String endpoint;
    private String apiKey;
    private String chatDeployment = "gpt-4o";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxResponseCharacters = 32_768;

    private static final String GITHUB_MODELS_ENDPOINT = "https://models.inference.ai.azure.com";

    @Bean
    public OpenAIClient openAIClient() {
        String normalizedProvider = requireText(provider, "llm.provider")
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("llm.timeout must be positive");
        }
        if (maxResponseCharacters < 1) {
            throw new IllegalStateException("llm.max-response-characters must be positive");
        }

        return switch (normalizedProvider) {
            case "azure-openai" -> new OpenAIClientBuilder()
                    .endpoint(requireText(endpoint, "llm.endpoint"))
                    .credential(new AzureKeyCredential(requireText(apiKey, "llm.api-key")))
                    .httpClient(httpClient())
                    .buildClient();
            case "github-models" -> new OpenAIClientBuilder()
                    .endpoint(GITHUB_MODELS_ENDPOINT)
                    .credential(new KeyCredential(requireText(apiKey, "llm.api-key")))
                    .httpClient(httpClient())
                    .buildClient();
            default -> throw new IllegalArgumentException(
                    "Unsupported llm.provider. Expected github-models or azure-openai.");
        };
    }

    private com.azure.core.http.HttpClient httpClient() {
        return new NettyAsyncHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .responseTimeout(timeout)
                .writeTimeout(timeout)
                .build();
    }

    private static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
        return value;
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
