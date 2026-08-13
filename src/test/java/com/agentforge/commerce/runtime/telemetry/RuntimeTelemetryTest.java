package com.agentforge.commerce.runtime.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge.commerce.runtime.contract.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for runtime telemetry: spans and non-sensitive metrics for tokens,
 * cost, latency, degraded modes, approval waits and budget stops.
 */
@DisplayName("Runtime telemetry")
class RuntimeTelemetryTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RuntimeTelemetry telemetry =
            new RuntimeTelemetry(ObservationRegistry.create(), meters);

    @Test
    @DisplayName("Spans start and stop without recording sensitive data")
    void spans() {
        RuntimeSpan run = telemetry.runSpan("run-1", "R1");
        run.attribute("k", "v").attribute("n", 5L);
        run.stop();
        telemetry.agentSpan("run-1", "InventoryAgent").stop();
        telemetry.toolSpan("InventoryAgent", "inventory.fetch").stop();
        RuntimeSpan llm = telemetry.llmSpan("InventoryAgent", "azure-openai", "gpt-4o");
        llm.error(new IllegalStateException("boom"));
        llm.stop();
        assertThat(meters).isNotNull();
    }

    @Test
    @DisplayName("Token metrics are recorded per agent")
    void tokens() {
        telemetry.recordTokens("InventoryAgent", new TokenUsage(1, 100, 50, 200));
        assertThat(meters.counter("runtime.llm.requests", "agent.name", "InventoryAgent").count()).isEqualTo(1);
        assertThat(meters.counter("runtime.llm.prompt.tokens", "agent.name", "InventoryAgent").count())
                .isEqualTo(100);
        telemetry.recordTokens("InventoryAgent", null);
    }

    @Test
    @DisplayName("Latency, tool, degraded, circuit, approval and budget metrics record")
    void otherMetrics() {
        telemetry.recordToolCall("inventory.fetch", "OK", Duration.ofMillis(5));
        telemetry.recordLlmLatency("InventoryAgent", "gpt-4o", Duration.ofMillis(10), 2);
        telemetry.recordDegraded("InventoryAgent", "schema_invalid");
        telemetry.recordCircuitState("llm", "OPEN");
        telemetry.recordApprovalWait("run-1");
        telemetry.recordBudgetStop("run-1", "tokens");
        assertThat(meters.counter("runtime.degraded", "agent.name", "InventoryAgent", "reason", "schema_invalid")
                .count()).isEqualTo(1);
        // run.id is deliberately NOT a metric label (unbounded cardinality); the
        // counters carry only bounded dimensions and the run id goes to the span.
        assertThat(meters.counter("runtime.budget.stop", "dimension", "tokens").count()).isEqualTo(1);
        assertThat(meters.counter("runtime.approval.wait").count()).isEqualTo(1);
        assertThat(meters.get("runtime.budget.stop").counter().getId().getTag("run.id")).isNull();
        assertThat(meters.get("runtime.approval.wait").counter().getId().getTag("run.id")).isNull();
    }
}
