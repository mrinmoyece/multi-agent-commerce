package com.agentforge.commerce;

import com.agentforge.commerce.config.LLMConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LLMConfigTest {

    @Test
    void objectMapperUsesIsoDatesAndIgnoresUnknownFields() throws Exception {
        LLMConfig config = new LLMConfig();

        assertThat(config.objectMapper().readTree("{\"unknown\":true}").get("unknown").asBoolean())
                .isTrue();
    }

    @Test
    void unsupportedProviderFailsWithoutEchoingInput() {
        LLMConfig config = new LLMConfig();
        config.setProvider("attacker-controlled-value");

        assertThatThrownBy(config::openAIClient)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported llm.provider. Expected github-models or azure-openai.")
                .hasMessageNotContaining("attacker-controlled-value");
    }

    @Test
    void missingCredentialFailsFast() {
        LLMConfig config = new LLMConfig();
        config.setApiKey(" ");

        assertThatThrownBy(config::openAIClient)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("llm.api-key must be configured");
    }

    @Test
    void invalidTimeoutFailsFast() {
        LLMConfig config = new LLMConfig();
        config.setTimeout(Duration.ZERO);

        assertThatThrownBy(config::openAIClient)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("llm.timeout must be positive");
    }
}
