package com.agentforge.commerce.runtime.brakes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the runtime brakes: tool-call and agent-round ceilings,
 * deadline propagation and cooperative cancellation.
 */
@DisplayName("Run brakes")
class RunBrakesTest {

    private static final Instant NOW = Instant.parse("2026-06-19T09:00:00Z");

    private RunBrakes brakes() {
        return new RunBrakes(2, 2, NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("Tool-call ceiling trips on the third call")
    void toolCallCeiling() {
        RunBrakes brakes = brakes();
        brakes.onToolCall(NOW);
        brakes.onToolCall(NOW);
        assertThat(brakes.toolCallCount()).isEqualTo(2);
        assertThatThrownBy(() -> brakes.onToolCall(NOW))
                .isInstanceOf(BrakeTrippedException.class)
                .satisfies(e -> assertThat(((BrakeTrippedException) e).brake()).isEqualTo("tool-calls"));
    }

    @Test
    @DisplayName("Agent-round ceiling trips on the third round")
    void agentRoundCeiling() {
        RunBrakes brakes = brakes();
        brakes.onAgentRound(NOW);
        brakes.onAgentRound(NOW);
        assertThat(brakes.agentRoundCount()).isEqualTo(2);
        assertThatThrownBy(() -> brakes.onAgentRound(NOW)).isInstanceOf(BrakeTrippedException.class);
    }

    @Test
    @DisplayName("Deadline trips once passed")
    void deadline() {
        RunBrakes brakes = brakes();
        assertThatThrownBy(() -> brakes.onToolCall(NOW.plusSeconds(120)))
                .isInstanceOf(BrakeTrippedException.class)
                .satisfies(e -> assertThat(((BrakeTrippedException) e).brake()).isEqualTo("deadline"));
        assertThat(brakes.remaining(NOW.plusSeconds(120))).isEqualTo(Duration.ZERO);
        assertThat(brakes.remaining(NOW)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("Cancellation trips subsequent work")
    void cancellation() {
        RunBrakes brakes = brakes();
        brakes.cancel();
        assertThat(brakes.isCancelled()).isTrue();
        assertThatThrownBy(() -> brakes.onToolCall(NOW))
                .isInstanceOf(BrakeTrippedException.class)
                .satisfies(e -> assertThat(((BrakeTrippedException) e).brake()).isEqualTo("cancelled"));
    }

    @Test
    @DisplayName("Constructor rejects invalid ceilings and deadline")
    void invalidConstruction() {
        assertThatThrownBy(() -> new RunBrakes(0, 1, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunBrakes(1, 1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(brakes().deadline()).isEqualTo(NOW.plusSeconds(60));
    }
}
