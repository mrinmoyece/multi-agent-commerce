package com.agentforge.commerce.runtime.telemetry;

import com.agentforge.commerce.runtime.contract.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Central telemetry facade for the agent runtime. Emits OpenTelemetry-compatible
 * spans through the Micrometer {@link ObservationRegistry} bridge and numeric
 * metrics through the {@link MeterRegistry}.
 *
 * <p>Only non-sensitive, low-cardinality attributes are recorded: agent names,
 * tool names, model identifiers, status codes, token counts, cost, latency,
 * retries, circuit state, degraded flags and approval waits. Prompts, credentials
 * and tool payloads are never recorded.</p>
 */
@Component
public class RuntimeTelemetry {

    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    public RuntimeTelemetry(ObservationRegistry observations, MeterRegistry meters) {
        this.observations = observations;
        this.meters = meters;
    }

    /**
     * Opens a span for the run lifecycle. Callers must {@code stop()} the returned span.
     */
    public RuntimeSpan runSpan(String runId, String retailerId) {
        Observation obs = Observation.createNotStarted("agent.run", observations)
                .highCardinalityKeyValue("run.id", safe(runId))
                .highCardinalityKeyValue("retailer.id", safe(retailerId));
        return new RuntimeSpan(obs.start());
    }

    /**
     * Opens a span for a single specialist agent execution.
     */
    public RuntimeSpan agentSpan(String runId, String agentName) {
        Observation obs = Observation.createNotStarted("agent.execute", observations)
                .highCardinalityKeyValue("run.id", safe(runId))
                .lowCardinalityKeyValue("agent.name", safe(agentName));
        return new RuntimeSpan(obs.start());
    }

    /**
     * Opens a span for a grounded tool call.
     */
    public RuntimeSpan toolSpan(String agentName, String toolName) {
        Observation obs = Observation.createNotStarted("agent.tool", observations)
                .lowCardinalityKeyValue("agent.name", safe(agentName))
                .lowCardinalityKeyValue("tool.name", safe(toolName));
        return new RuntimeSpan(obs.start());
    }

    /**
     * Opens a span for an LLM provider call.
     */
    public RuntimeSpan llmSpan(String agentName, String provider, String model) {
        Observation obs = Observation.createNotStarted("agent.llm", observations)
                .lowCardinalityKeyValue("agent.name", safe(agentName))
                .lowCardinalityKeyValue("llm.provider", safe(provider))
                .lowCardinalityKeyValue("llm.model", safe(model));
        return new RuntimeSpan(obs.start());
    }

    public void recordTokens(String agentName, TokenUsage usage) {
        if (usage == null) {
            return;
        }
        Tags tags = Tags.of("agent.name", safe(agentName));
        meters.counter("runtime.llm.requests", tags).increment(usage.requests());
        meters.counter("runtime.llm.prompt.tokens", tags).increment(usage.promptTokens());
        meters.counter("runtime.llm.completion.tokens", tags).increment(usage.completionTokens());
        meters.counter("runtime.llm.cost.micros", tags).increment(usage.estimatedCostMicros());
    }

    public void recordToolCall(String toolName, String status, Duration latency) {
        Tags tags = Tags.of("tool.name", safe(toolName), "status", safe(status));
        meters.timer("runtime.tool.latency", tags).record(latency);
    }

    public void recordLlmLatency(String agentName, String model, Duration latency, int retries) {
        Tags tags = Tags.of("agent.name", safe(agentName), "llm.model", safe(model));
        meters.timer("runtime.llm.latency", tags).record(latency);
        if (retries > 0) {
            meters.counter("runtime.llm.retries", tags).increment(retries);
        }
    }

    public void recordDegraded(String agentName, String reason) {
        meters.counter("runtime.degraded", Tags.of("agent.name", safe(agentName), "reason", safe(reason))).increment();
    }

    public void recordCircuitState(String circuit, String state) {
        meters.counter("runtime.circuit.state", Tags.of("circuit", safe(circuit), "state", safe(state))).increment();
    }

    /**
     * Terminal outcome of a run. {@code outcome} is bounded (success, failed,
     * awaiting_approval) so it is safe as a metric label and drives the run-success SLI.
     */
    public void recordRunOutcome(String outcome) {
        meters.counter("runtime.run.completed", Tags.of("outcome", safe(outcome))).increment();
    }

    /**
     * A grounded tool returned evidence that is stale at read time. {@code toolName}
     * is bounded (a fixed set of tools) so it is safe as a metric label.
     */
    public void recordStaleEvidence(String toolName) {
        meters.counter("runtime.evidence.stale", Tags.of("tool.name", safe(toolName))).increment();
    }

    /**
     * Records that a run is blocked awaiting human approval. The run identifier is
     * deliberately NOT a metric label (it is unbounded); it is emitted on the active
     * trace span instead so the counter stays low-cardinality.
     */
    public void recordApprovalWait(String runId) {
        markCurrentSpan("run.id", runId);
        meters.counter("runtime.approval.wait").increment();
    }

    /**
     * Records a budget hard-stop. Only the bounded {@code dimension} (e.g. tokens,
     * cost, wall-clock) is a metric label; the run identifier goes to the trace span.
     */
    public void recordBudgetStop(String runId, String dimension) {
        markCurrentSpan("run.id", runId);
        meters.counter("runtime.budget.stop", Tags.of("dimension", safe(dimension))).increment();
    }

    private void markCurrentSpan(String key, String value) {
        Observation current = observations.getCurrentObservation();
        if (current != null) {
            current.highCardinalityKeyValue(key, safe(value));
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
