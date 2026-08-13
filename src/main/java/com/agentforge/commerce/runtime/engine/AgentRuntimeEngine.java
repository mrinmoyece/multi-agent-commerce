package com.agentforge.commerce.runtime.engine;

import com.agentforge.commerce.config.LLMConfig;
import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.runtime.brakes.RunBrakes;
import com.agentforge.commerce.runtime.budget.BudgetLedger;
import com.agentforge.commerce.runtime.budget.ModelPolicy;
import com.agentforge.commerce.runtime.budget.RuntimeBudgets;
import com.agentforge.commerce.runtime.config.RuntimeProperties;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Evidence;
import com.agentforge.commerce.runtime.contract.Hashes;
import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.memory.MemoryFact;
import com.agentforge.commerce.runtime.memory.MemoryWriteRejectedException;
import com.agentforge.commerce.runtime.memory.SemanticMemoryStore;
import com.agentforge.commerce.runtime.memory.WorkingMemory;
import com.agentforge.commerce.runtime.prompt.PromptRegistry;
import com.agentforge.commerce.runtime.tools.InventorySnapshot;
import com.agentforge.commerce.runtime.tools.InventoryTool;
import com.agentforge.commerce.runtime.tools.PricingObservations;
import com.agentforge.commerce.runtime.tools.PricingTool;
import com.agentforge.commerce.runtime.tools.SentimentObservations;
import com.agentforge.commerce.runtime.tools.SentimentTool;
import com.agentforge.commerce.runtime.validation.AgentSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The agent runtime engine: the single place where grounded tools, versioned
 * prompts, budgets, brakes, schema validation, telemetry and durable memory are
 * wired together into end-to-end specialist and coordinator steps.
 *
 * <p>Each specialist first fetches a fully-provenanced {@link ToolResult}, records
 * grounded facts to run-local and durable memory, then produces a schema-validated
 * {@link AgentResultEnvelope} whose evidence and confidence come from the tool —
 * never from unverified model text. The coordinator synthesis consumes only the
 * validated specialist payloads. Fixture/simulated grounding is always surfaced as
 * a degraded, non-authoritative result.</p>
 */
@Component
public class AgentRuntimeEngine {

    public static final String INVENTORY_AGENT = "InventoryAgent";
    public static final String PRICING_AGENT = "PricingAgent";
    public static final String SENTIMENT_AGENT = "SentimentAgent";
    public static final String COORDINATOR_AGENT = "Coordinator";

    private static final String INVENTORY_FALLBACK =
            "{\"risks\":[],\"reorders\":[],\"clearance\":[],\"citedSkus\":[],"
                    + "\"summary\":\"Inventory data unavailable; manual review required.\"}";
    private static final String PRICING_FALLBACK =
            "{\"repricingOpportunities\":[],\"marginRisks\":[],\"citedSkus\":[],"
                    + "\"summary\":\"Pricing data unavailable; manual review required.\"}";
    private static final String SENTIMENT_FALLBACK =
            "{\"positiveThemes\":[],\"negativeThemes\":[],\"citedTopics\":[],"
                    + "\"summary\":\"Sentiment data unavailable; manual review required.\"}";
    private static final String COORDINATOR_FALLBACK =
            "{\"crossInsights\":[],\"recommendedActions\":[],\"urgentAlerts\":[],\"citedReferences\":[],"
                    + "\"executiveSummary\":\"Synthesis unavailable; manual review required.\"}";

    private static final Logger LOG = LoggerFactory.getLogger(AgentRuntimeEngine.class);

    private final RuntimeStep step;
    private final ModelPolicy modelPolicy;
    private final RuntimeBudgets budgets;
    private final RuntimeProperties properties;
    private final LLMConfig llmConfig;
    private final ObjectMapper mapper;
    private final SemanticMemoryStore memory;
    private final Clock clock;
    private final InventoryTool inventoryTool;
    private final PricingTool pricingTool;
    private final SentimentTool sentimentTool;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AgentRuntimeEngine(PromptRegistry prompts, com.agentforge.commerce.llm.LlmGateway llm,
                              ModelPolicy modelPolicy, RuntimeBudgets budgets, RuntimeProperties properties,
                              LLMConfig llmConfig, ObjectMapper mapper, SemanticMemoryStore memory,
                              com.agentforge.commerce.runtime.telemetry.RuntimeTelemetry telemetry, Clock clock,
                              InventoryTool inventoryTool, PricingTool pricingTool, SentimentTool sentimentTool) {
        this.step = new RuntimeStep(prompts, llm, mapper, telemetry, clock);
        this.modelPolicy = modelPolicy;
        this.budgets = budgets;
        this.properties = properties;
        this.llmConfig = llmConfig;
        this.mapper = mapper;
        this.memory = memory;
        this.clock = clock;
        this.inventoryTool = inventoryTool;
        this.pricingTool = pricingTool;
        this.sentimentTool = sentimentTool;
    }

    /** Build a fresh run context with its own budget ledger, brakes and working memory. */
    public RuntimeRunContext newContext(String runId, String retailerId, LocalDate businessDate) {
        String provider = llmConfig.getProvider();
        modelPolicy.checkProvider(provider);
        BudgetLedger ledger = budgets.newLedger();
        RuntimeProperties.Brakes b = properties.getBrakes();
        RunBrakes brakes = new RunBrakes(b.getMaxToolCalls(), b.getMaxAgentRounds(),
                clock.instant().plus(b.getDeadline()));
        return new RuntimeRunContext(runId, retailerId, businessDate, provider, ledger, brakes, new WorkingMemory());
    }

    /** Run the inventory specialist end-to-end. */
    public SpecialistOutcome runInventory(RuntimeRunContext ctx) {
        ToolResult<InventorySnapshot> tr = step.callTool(ctx, INVENTORY_AGENT, inventoryTool);
        String payloadText = payloadText(tr);
        String model = modelPolicy.resolveModel(INVENTORY_AGENT);
        RuntimeStep.Structured s = step.complete(ctx, INVENTORY_AGENT, model, PromptRegistry.INVENTORY_ANALYSIS,
                AgentSchemas.INVENTORY, Map.of(), "snapshot", payloadText, INVENTORY_FALLBACK,
                tr.isSimulatedOrFixture(), 600, 0.3);
        recordInventoryFacts(ctx, tr);
        return outcome(ctx, INVENTORY_AGENT, PromptRegistry.INVENTORY_ANALYSIS, model, s, tr);
    }

    /** Run the pricing specialist end-to-end. */
    public SpecialistOutcome runPricing(RuntimeRunContext ctx) {
        ToolResult<PricingObservations> tr = step.callTool(ctx, PRICING_AGENT, pricingTool);
        String payloadText = payloadText(tr);
        String model = modelPolicy.resolveModel(PRICING_AGENT);
        RuntimeStep.Structured s = step.complete(ctx, PRICING_AGENT, model, PromptRegistry.PRICING_ANALYSIS,
                AgentSchemas.PRICING, Map.of(), "observations", payloadText, PRICING_FALLBACK,
                tr.isSimulatedOrFixture(), 400, 0.4);
        recordPricingFacts(ctx, tr);
        return outcome(ctx, PRICING_AGENT, PromptRegistry.PRICING_ANALYSIS, model, s, tr);
    }

    /** Run the sentiment specialist end-to-end. */
    public SpecialistOutcome runSentiment(RuntimeRunContext ctx) {
        ToolResult<SentimentObservations> tr = step.callTool(ctx, SENTIMENT_AGENT, sentimentTool);
        String payloadText = payloadText(tr);
        String model = modelPolicy.resolveModel(SENTIMENT_AGENT);
        RuntimeStep.Structured s = step.complete(ctx, SENTIMENT_AGENT, model, PromptRegistry.SENTIMENT_ANALYSIS,
                AgentSchemas.SENTIMENT, Map.of(), "observations", payloadText, SENTIMENT_FALLBACK,
                tr.isSimulatedOrFixture(), 400, 0.4);
        recordSentimentFacts(ctx, tr);
        return outcome(ctx, SENTIMENT_AGENT, PromptRegistry.SENTIMENT_ANALYSIS, model, s, tr);
    }

    /** Synthesise the coordinator briefing from the validated specialist payloads. */
    public SynthesisOutcome synthesise(RuntimeRunContext ctx, SpecialistOutcome inventory,
                                       SpecialistOutcome pricing, SpecialistOutcome sentiment) {
        ObjectNode findings = mapper.createObjectNode();
        findings.set("inventory", parseOrText(inventory.envelope().payloadJson()));
        findings.set("pricing", parseOrText(pricing.envelope().payloadJson()));
        findings.set("sentiment", parseOrText(sentiment.envelope().payloadJson()));
        String findingsText;
        try {
            findingsText = mapper.writeValueAsString(findings);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            findingsText = "{}";
        }

        String model = modelPolicy.resolveModel(COORDINATOR_AGENT);
        boolean sourceDegraded = inventory.envelope().isDegraded() || pricing.envelope().isDegraded()
                || sentiment.envelope().isDegraded();
        RuntimeStep.Structured s = step.complete(ctx, COORDINATOR_AGENT, model,
                PromptRegistry.COORDINATOR_SYNTHESIS, AgentSchemas.COORDINATOR,
                Map.of("date", String.valueOf(ctx.businessDate())), "findings", findingsText,
                COORDINATOR_FALLBACK, sourceDegraded, 1500, 0.2);

        DailyBriefing briefing = toBriefing(ctx, inventory, pricing, sentiment, s.payloadJson());
        List<Evidence> evidence = mergeEvidence(inventory, pricing, sentiment);
        Confidence confidence = weakestConfidence(inventory, pricing, sentiment);
        AgentResultEnvelope envelope = step.envelope(COORDINATOR_AGENT, PromptRegistry.COORDINATOR_SYNTHESIS,
                ctx.provider(), model, s, evidence, confidence);
        return new SynthesisOutcome(briefing, envelope, s.status());
    }

    private SpecialistOutcome outcome(RuntimeRunContext ctx, String agentName, String promptId, String model,
                                      RuntimeStep.Structured s, ToolResult<?> tr) {
        List<Evidence> evidence = tr.evidence();
        Confidence confidence = s.status() == ResultStatus.OK ? tr.confidence()
                : Confidence.none("degraded: " + String.join(",", s.degradedReasons()));
        AgentResultEnvelope envelope = step.envelope(agentName, promptId, ctx.provider(), model, s,
                evidence, confidence);
        rememberWorking(ctx, agentName, s, evidence);
        return new SpecialistOutcome(envelope, step.message(agentName, s));
    }

    private void rememberWorking(RuntimeRunContext ctx, String agentName, RuntimeStep.Structured s,
                                 List<Evidence> evidence) {
        try {
            Evidence primary = evidence.isEmpty() ? null : evidence.get(0);
            ctx.workingMemory().put(agentName + ".status", s.status().name(), primary);
        } catch (RuntimeException e) {
            LOG.debug("Working-memory write skipped for {} ({})", agentName, e.getClass().getSimpleName());
        }
    }

    private void recordInventoryFacts(RuntimeRunContext ctx, ToolResult<InventorySnapshot> tr) {
        if (!tr.status().hasData() || tr.payload() == null || tr.evidence().isEmpty()) {
            return;
        }
        long critical = tr.payload().items().stream().filter(InventorySnapshot.Item::isCritical).count();
        writeFact(ctx, tr, "inventory.critical_sku_count", "inventory", Long.toString(critical));
    }

    private void recordPricingFacts(RuntimeRunContext ctx, ToolResult<PricingObservations> tr) {
        if (!tr.status().hasData() || tr.payload() == null || tr.evidence().isEmpty()) {
            return;
        }
        long undercut = tr.payload().observations().stream()
                .filter(PricingObservations.Observation::isUndercut).count();
        writeFact(ctx, tr, "pricing.undercut_sku_count", "pricing", Long.toString(undercut));
    }

    private void recordSentimentFacts(RuntimeRunContext ctx, ToolResult<SentimentObservations> tr) {
        if (!tr.status().hasData() || tr.payload() == null || tr.evidence().isEmpty()) {
            return;
        }
        long negative = tr.payload().signals().stream()
                .filter(SentimentObservations.Signal::isNegativeLeaning).count();
        writeFact(ctx, tr, "sentiment.negative_topic_count", "sentiment", Long.toString(negative));
    }

    private void writeFact(RuntimeRunContext ctx, ToolResult<?> tr, String key, String category, String value) {
        Evidence primary = tr.evidence().get(0);
        Instant observedAt = tr.freshness() == null ? clock.instant() : tr.freshness().observedAt();
        Instant expiresAt = observedAt.plus(properties.getMemory().getFactTtl());
        MemoryFact fact = new MemoryFact(ctx.retailerId(), key, category, value, tr.source().id(),
                tr.source().kind(), tr.confidence(), Hashes.shortSha256(key + '=' + value, 32),
                ctx.runId(), observedAt, expiresAt);
        try {
            memory.write(fact, primary);
        } catch (MemoryWriteRejectedException e) {
            LOG.debug("Semantic-memory write rejected for {} ({})", key, e.getMessage());
        }
    }

    private String payloadText(ToolResult<?> tr) {
        if (tr.payload() == null) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(tr.payload());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{}";
        }
    }

    private JsonNode parseOrText(String json) {
        try {
            return mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return mapper.getNodeFactory().textNode(json);
        }
    }

    private DailyBriefing toBriefing(RuntimeRunContext ctx, SpecialistOutcome inventory, SpecialistOutcome pricing,
                                     SpecialistOutcome sentiment, String synthesisJson) {
        JsonNode node = parseOrText(synthesisJson);
        return DailyBriefing.builder()
                .briefingId(UUID.randomUUID().toString())
                .date(ctx.businessDate())
                .retailerId(ctx.retailerId())
                .inventoryFindings(inventory.envelope().payloadJson())
                .pricingFindings(pricing.envelope().payloadJson())
                .sentimentFindings(sentiment.envelope().payloadJson())
                .crossInsights(readList(node, "crossInsights"))
                .recommendedActions(readList(node, "recommendedActions"))
                .urgentAlerts(readList(node, "urgentAlerts"))
                .executiveSummary(readText(node, "executiveSummary"))
                .rawSynthesis(synthesisJson)
                .generatedAt(clock.instant())
                .build();
    }

    private static List<String> readList(JsonNode node, String field) {
        JsonNode array = node.path(field);
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode element : array) {
            if (element.isTextual() && !element.asText().isBlank()) {
                values.add(element.asText());
            }
        }
        return List.copyOf(values);
    }

    private static String readText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText()
                : "Synthesis degraded; manual review required.";
    }

    private static List<Evidence> mergeEvidence(SpecialistOutcome... outcomes) {
        List<Evidence> merged = new java.util.ArrayList<>();
        for (SpecialistOutcome outcome : outcomes) {
            merged.addAll(outcome.envelope().evidence());
        }
        return List.copyOf(merged);
    }

    private static Confidence weakestConfidence(SpecialistOutcome... outcomes) {
        double min = 1.0;
        for (SpecialistOutcome outcome : outcomes) {
            min = Math.min(min, outcome.envelope().confidence().value());
        }
        return new Confidence(min, "weakest specialist confidence");
    }

    /** The coordinator synthesis result: the briefing plus its traceable envelope. */
    public record SynthesisOutcome(DailyBriefing briefing, AgentResultEnvelope envelope, ResultStatus status) {
    }

    /** Build a deterministic degraded outcome for a specialist that could not run. */
    public SpecialistOutcome degradedOutcome(String agentName, String reason) {
        String fallback = fallbackFor(agentName);
        AgentResultEnvelope envelope = new AgentResultEnvelope(agentName, ResultStatus.DEGRADED, "", "",
                llmConfig.getProvider(), "", fallback, List.of(),
                Confidence.none(reason), com.agentforge.commerce.runtime.contract.TokenUsage.ZERO,
                0, true, List.of(reason));
        var message = com.agentforge.commerce.model.AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .agentName(agentName)
                .messageType(com.agentforge.commerce.model.AgentMessage.MessageType.COMPLETE)
                .priority(com.agentforge.commerce.model.AgentMessage.AgentMessagePriority.HIGH)
                .content(fallback)
                .timestamp(clock.instant())
                .build();
        return new SpecialistOutcome(envelope, message);
    }

    private static String fallbackFor(String agentName) {
        return switch (agentName) {
            case PRICING_AGENT -> PRICING_FALLBACK;
            case SENTIMENT_AGENT -> SENTIMENT_FALLBACK;
            case COORDINATOR_AGENT -> COORDINATOR_FALLBACK;
            default -> INVENTORY_FALLBACK;
        };
    }
}
