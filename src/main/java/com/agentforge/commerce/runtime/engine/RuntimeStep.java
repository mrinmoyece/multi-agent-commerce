package com.agentforge.commerce.runtime.engine;

import com.agentforge.commerce.llm.LlmCompletion;
import com.agentforge.commerce.llm.LlmGateway;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.runtime.budget.BudgetExceededException;
import com.agentforge.commerce.runtime.budget.BudgetLedger;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Evidence;
import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.prompt.BoundaryEncoder;
import com.agentforge.commerce.runtime.prompt.PromptRegistry;
import com.agentforge.commerce.runtime.prompt.PromptTemplate;
import com.agentforge.commerce.runtime.telemetry.RuntimeSpan;
import com.agentforge.commerce.runtime.telemetry.RuntimeTelemetry;
import com.agentforge.commerce.runtime.validation.JsonSchema;
import com.agentforge.commerce.runtime.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared execution core for a single specialist or coordinator step.
 *
 * <p>Every structured LLM step flows through {@link #complete}: it counts an agent
 * round against the run brakes, injection-fences the untrusted grounded data,
 * resolves an allowlisted per-agent model, enforces the token/cost/latency budget
 * as a hard stop <em>before</em> the provider call, reconciles actual usage after,
 * strips code fences, and validates the output against a strict schema — failing
 * closed to a deterministic degraded payload rather than throwing. Telemetry spans
 * wrap the call without ever recording the prompt, payload or credentials.</p>
 */
public final class RuntimeStep {

    private static final int PROMPT_TOKENS_PER_CHAR = 4;

    private final PromptRegistry prompts;
    private final LlmGateway llm;
    private final ObjectMapper mapper;
    private final RuntimeTelemetry telemetry;
    private final Clock clock;

    public RuntimeStep(PromptRegistry prompts, LlmGateway llm, ObjectMapper mapper,
                       RuntimeTelemetry telemetry, Clock clock) {
        this.prompts = prompts;
        this.llm = llm;
        this.mapper = mapper;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    /** The structured outcome of one LLM step. */
    public record Structured(
            String payloadJson,
            TokenUsage usage,
            boolean schemaValid,
            ResultStatus status,
            List<String> degradedReasons,
            long latencyMillis) {

        public Structured {
            degradedReasons = degradedReasons == null ? List.of() : List.copyOf(degradedReasons);
            usage = usage == null ? TokenUsage.ZERO : usage;
        }
    }

    /**
     * Execute one structured step end-to-end (fence → budget → model → LLM →
     * validate), returning a schema-valid payload or a deterministic degraded one.
     *
     * @param ctx             the run context (brakes + budget)
     * @param agentName       producing agent name (for model selection + telemetry)
     * @param model           the resolved, allowlisted model/deployment
     * @param promptId        the versioned prompt template id
     * @param schema          the strict output schema
     * @param placeholders    non-fenced placeholder values (e.g. {@code date})
     * @param untrustedKey    the placeholder key that receives the fenced untrusted data
     * @param untrustedData   the untrusted grounded data (JSON text) to fence
     * @param fallbackJson    deterministic schema-valid payload used on any failure
     * @param sourceDegraded  whether the grounding source is non-authoritative
     * @param maxTokens       completion token ceiling
     * @param temperature     sampling temperature
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Structured complete(RuntimeRunContext ctx, String agentName, String model, String promptId,
                               JsonSchema schema, Map<String, String> placeholders, String untrustedKey,
                               String untrustedData, String fallbackJson, boolean sourceDegraded,
                               int maxTokens, double temperature) {
        ctx.brakes().onAgentRound(clock.instant());
        String seed = agentName + ":" + ctx.runId();
        PromptTemplate template = prompts.get(promptId);
        Map<String, String> values = new java.util.HashMap<>(placeholders);
        values.put("guard", BoundaryEncoder.guardInstruction(seed));
        values.put(untrustedKey, BoundaryEncoder.encode(untrustedData, seed));
        String prompt = template.render(values);

        List<String> degraded = new ArrayList<>();
        if (sourceDegraded) {
            degraded.add("non_authoritative_source");
        }

        BudgetLedger ledger = ctx.ledger();
        long estPrompt = (long) prompt.length() / PROMPT_TOKENS_PER_CHAR;
        BudgetLedger.Reservation reservation;
        try {
            reservation = ledger.reserve(estPrompt, maxTokens);
        } catch (BudgetExceededException e) {
            telemetry.recordBudgetStop(ctx.runId(), e.dimension());
            telemetry.recordDegraded(agentName, "budget_" + e.dimension());
            degraded.add("budget_" + e.dimension());
            return new Structured(fallbackJson, TokenUsage.ZERO, true, ResultStatus.DEGRADED, degraded, 0);
        }

        RuntimeSpan span = telemetry.llmSpan(agentName, ctx.provider(), model);
        Instant start = clock.instant();
        LlmCompletion completion;
        try {
            completion = llm.completeWithUsage(model, prompt, maxTokens, temperature);
        } catch (RuntimeException e) {
            long latency = Duration.between(start, clock.instant()).toMillis();
            ledger.abort(reservation, latency);
            span.error(e);
            span.stop();
            telemetry.recordDegraded(agentName, "llm_unavailable");
            degraded.add("llm_unavailable");
            return new Structured(fallbackJson, TokenUsage.ZERO, true, ResultStatus.DEGRADED, degraded, latency);
        }
        long latency = Duration.between(start, clock.instant()).toMillis();
        long cost = ledger.budget().estimateCostMicros(
                completion.usage().promptTokens(), completion.usage().completionTokens());
        TokenUsage actual = new TokenUsage(1, completion.usage().promptTokens(),
                completion.usage().completionTokens(), cost);
        ledger.settle(reservation, actual, latency);
        telemetry.recordTokens(agentName, actual);
        telemetry.recordLlmLatency(agentName, model, Duration.ofMillis(latency), 0);
        span.attribute("llm.prompt_tokens", actual.promptTokens());
        span.attribute("llm.completion_tokens", actual.completionTokens());
        span.stop();

        String raw = stripFences(completion.content());
        ValidationResult validation = validate(schema, raw);
        if (!validation.valid()) {
            telemetry.recordDegraded(agentName, "schema_invalid");
            degraded.add("schema_invalid");
            return new Structured(fallbackJson, actual, false, ResultStatus.DEGRADED, degraded, latency);
        }
        ResultStatus status = sourceDegraded ? ResultStatus.DEGRADED : ResultStatus.OK;
        return new Structured(raw, actual, true, status, degraded, latency);
    }

    /** Build an envelope from a completed step and its grounding evidence/confidence. */
    public AgentResultEnvelope envelope(String agentName, String promptId, String provider, String model,
                                        Structured step, List<Evidence> evidence, Confidence confidence) {
        PromptTemplate template = prompts.get(promptId);
        return new AgentResultEnvelope(agentName, step.status(), template.reference(), template.hash(),
                provider, model, step.payloadJson(), evidence, confidence, step.usage(),
                step.latencyMillis(), step.schemaValid(), step.degradedReasons());
    }

    /** Project an envelope into the legacy {@link AgentMessage} for Layer 1 persistence. */
    public AgentMessage message(String agentName, Structured step) {
        AgentMessage.MessageType type = AgentMessage.MessageType.FINDINGS;
        AgentMessage.AgentMessagePriority priority = step.status() == ResultStatus.OK
                ? AgentMessage.AgentMessagePriority.NORMAL : AgentMessage.AgentMessagePriority.HIGH;
        return AgentMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .agentName(agentName)
                .messageType(type)
                .priority(priority)
                .content(step.payloadJson())
                .timestamp(clock.instant())
                .build();
    }

    /** Count and telemeter a grounded tool call, honouring the run brakes. */
    public <T> ToolResult<T> callTool(RuntimeRunContext ctx, String agentName,
                                      com.agentforge.commerce.runtime.tools.GroundedTool<T> tool) {
        ctx.brakes().onToolCall(clock.instant());
        RuntimeSpan span = telemetry.toolSpan(agentName, tool.name());
        Instant start = clock.instant();
        try {
            ToolRequest request = new ToolRequest(tool.name(), ctx.retailerId(), Map.of(),
                    ctx.deadline(), com.agentforge.commerce.runtime.contract.RuntimeLimits.MAX_TOOL_PAYLOAD_BYTES);
            ToolResult<T> result = tool.fetch(request);
            telemetry.recordToolCall(tool.name(), result.status().name(),
                    Duration.between(start, clock.instant()));
            span.attribute("tool.status", result.status().name());
            span.attribute("tool.source_kind", result.source().kind().name());
            return result;
        } finally {
            span.stop();
        }
    }

    private ValidationResult validate(JsonSchema schema, String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return schema.validate(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return ValidationResult.failed(List.of(schema.name() + ": payload is not valid JSON"));
        }
    }

    private static String stripFences(String content) {
        return content.replaceAll("(?s)```json\\s*|```\\s*$", "").trim();
    }
}
