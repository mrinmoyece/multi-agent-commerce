package com.agentforge.commerce.llm;

import com.agentforge.commerce.config.LLMConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmGatewayTest {

    private LLMConfig config;
    private LlmGateway gateway;

    @BeforeEach
    void setUp() {
        config = new LLMConfig();
        gateway = new LlmGateway(null, config);
    }

    @Test
    void acceptsBoundedContent() {
        assertThat(gateway.validateContent("valid response")).isEqualTo("valid response");
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> gateway.validateContent(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LLM returned an empty completion");
    }

    @Test
    void rejectsOversizedContent() {
        config.setMaxResponseCharacters(4);

        assertThatThrownBy(() -> gateway.validateContent("too long"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response limit");
    }
}
