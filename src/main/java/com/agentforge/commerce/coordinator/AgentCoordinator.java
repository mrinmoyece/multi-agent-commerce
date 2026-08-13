package com.agentforge.commerce.coordinator;

import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.engine.AgentRuntimeEngine;
import com.agentforge.commerce.runtime.engine.AgentRuntimeEngine.SynthesisOutcome;
import com.agentforge.commerce.runtime.engine.RuntimeRunContext;
import com.agentforge.commerce.runtime.engine.RuntimeRunReport;
import com.agentforge.commerce.runtime.engine.SpecialistOutcome;
import com.agentforge.commerce.runtime.telemetry.RuntimeSpan;
import com.agentforge.commerce.runtime.telemetry.RuntimeTelemetry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Coordinator Agent — the "manager" of the multi-agent system.
 *
 * <p>Responsibilities: dispatch the three grounded specialists (Inventory,
 * Pricing, Sentiment) in parallel through the {@link AgentRuntimeEngine},
 * synthesise a unified daily executive briefing from their schema-validated,
 * evidence-bearing envelopes, and surface cross-agent insights no single
 * specialist could see alone. The coordinator owns no domain logic — the engine
 * owns grounding, prompts, budgets, brakes, schema validation, telemetry and
 * memory; the coordinator owns fan-out, deadline/cancellation and assembly.</p>
 *
 * <p>Durable, once-only scheduling across replicas is owned by the platform run
 * executor, which drives this coordinator via {@link #orchestrate}. The legacy
 * {@link #runDailyAnalysis} entry point is retained for direct, in-process use.</p>
 */
@Component
public class AgentCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(AgentCoordinator.class);

    private final AgentRuntimeEngine engine;
    private final RuntimeTelemetry telemetry;
    private final Executor applicationTaskExecutor;

    public AgentCoordinator(AgentRuntimeEngine engine, RuntimeTelemetry telemetry,
                            @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.engine = engine;
        this.telemetry = telemetry;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /** Legacy in-process entry point returning only the briefing. */
    public CompletableFuture<DailyBriefing> runDailyAnalysis(DailyAnalysisContext context) {
        LOG.info("Coordinator: starting daily analysis — retailer={} date={}",
                context.getRetailerId(), context.getDate());
        try {
            OrchestrationResult result = orchestrateInternal(context, null);
            return CompletableFuture.completedFuture(result.briefing());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Coordinator daily analysis failed ({})", e.getClass().getSimpleName());
            throw new DailyAnalysisException("Daily analysis failed for retailer "
                    + context.getRetailerId() + " on " + context.getDate(), e);
        }
    }

    /**
     * Durable-execution entry point. Runs the specialists (bounded by the run
     * brakes and {@code timeout}), synthesises the briefing, and returns the
     * briefing, the legacy per-agent messages, the traceable envelopes and a
     * non-sensitive run report for persistence and telemetry.
     *
     * @param context the run context
     * @param timeout hard ceiling on the parallel agent phase (never {@code null})
     * @return the full orchestration outcome
     */
    public OrchestrationResult orchestrate(DailyAnalysisContext context, Duration timeout) {
        return orchestrateInternal(context, timeout);
    }

    private OrchestrationResult orchestrateInternal(DailyAnalysisContext context, Duration timeout) {
        String runId = context.getRunId() != null ? context.getRunId() : UUID.randomUUID().toString();
        RuntimeRunContext ctx = engine.newContext(runId, context.getRetailerId(), context.getDate());
        RuntimeSpan span = telemetry.runSpan(runId, context.getRetailerId());
        try {
            CompletableFuture<SpecialistOutcome> inventoryFuture = dispatch(
                    AgentRuntimeEngine.INVENTORY_AGENT, () -> engine.runInventory(ctx));
            CompletableFuture<SpecialistOutcome> pricingFuture = dispatch(
                    AgentRuntimeEngine.PRICING_AGENT, () -> engine.runPricing(ctx));
            CompletableFuture<SpecialistOutcome> sentimentFuture = dispatch(
                    AgentRuntimeEngine.SENTIMENT_AGENT, () -> engine.runSentiment(ctx));

            SpecialistOutcome inventory;
            SpecialistOutcome pricing;
            SpecialistOutcome sentiment;
            try {
                CompletableFuture<Void> all =
                        CompletableFuture.allOf(inventoryFuture, pricingFuture, sentimentFuture);
                if (timeout == null) {
                    all.join();
                } else {
                    all.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                }
                inventory = inventoryFuture.get();
                pricing = pricingFuture.get();
                sentiment = sentimentFuture.get();
            } catch (TimeoutException e) {
                ctx.brakes().cancel();
                inventoryFuture.cancel(true);
                pricingFuture.cancel(true);
                sentimentFuture.cancel(true);
                span.error(e);
                throw new DailyAnalysisException("Agent orchestration timed out after " + timeout, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                span.error(e);
                throw new DailyAnalysisException("Agent orchestration was interrupted", e);
            } catch (ExecutionException e) {
                span.error(e);
                throw new DailyAnalysisException("Agent orchestration failed", e);
            }

            LOG.info("All specialists reported — synthesising briefing");
            SynthesisOutcome synthesis = engine.synthesise(ctx, inventory, pricing, sentiment);
            OrchestrationResult result = assemble(ctx, inventory, pricing, sentiment, synthesis);
            LOG.info("Daily briefing ready — {} actions, {} alerts",
                    safeSize(result.briefing().getRecommendedActions()),
                    safeSize(result.briefing().getUrgentAlerts()));
            return result;
        } finally {
            span.stop();
        }
    }

    private CompletableFuture<SpecialistOutcome> dispatch(String agentName, Supplier<SpecialistOutcome> work) {
        return CompletableFuture.supplyAsync(() -> safeRun(agentName, work), applicationTaskExecutor);
    }

    private SpecialistOutcome safeRun(String agentName, Supplier<SpecialistOutcome> work) {
        try {
            return work.get();
        } catch (RuntimeException e) {
            LOG.warn("[Coordinator] {} failed with {} — proceeding degraded",
                    agentName, e.getClass().getSimpleName());
            return engine.degradedOutcome(agentName, "specialist_error:" + e.getClass().getSimpleName());
        }
    }

    private OrchestrationResult assemble(RuntimeRunContext ctx, SpecialistOutcome inventory,
                                         SpecialistOutcome pricing, SpecialistOutcome sentiment,
                                         SynthesisOutcome synthesis) {
        List<AgentResultEnvelope> specialists = List.of(
                inventory.envelope(), pricing.envelope(), sentiment.envelope());
        TokenUsage total = TokenUsage.ZERO;
        for (AgentResultEnvelope envelope : specialists) {
            total = total.plus(envelope.usage());
        }
        total = total.plus(synthesis.envelope().usage());
        boolean degraded = synthesis.envelope().isDegraded()
                || specialists.stream().anyMatch(AgentResultEnvelope::isDegraded);
        RuntimeRunReport report = new RuntimeRunReport(specialists, synthesis.envelope(), total,
                ctx.ledger().snapshot(), ctx.brakes().toolCallCount(), ctx.brakes().agentRoundCount(), degraded);
        List<AgentResultEnvelope> envelopes = List.of(inventory.envelope(), pricing.envelope(),
                sentiment.envelope(), synthesis.envelope());
        List<AgentMessage> messages = List.of(inventory.message(), pricing.message(), sentiment.message());
        return new OrchestrationResult(synthesis.briefing(), messages, envelopes, report);
    }

    private static int safeSize(List<String> values) {
        return values == null ? 0 : values.size();
    }

    /**
     * The full outcome of an orchestration: the synthesised briefing, the legacy
     * per-agent messages (Layer 1 persistence), the traceable envelopes, and a
     * non-sensitive run report. The two-argument constructor is retained for
     * backward-compatible construction paths and tests.
     *
     * @param briefing      the synthesised daily briefing
     * @param agentMessages legacy per-specialist messages
     * @param envelopes     traceable specialist + coordinator envelopes
     * @param report        non-sensitive run report (budgets, brakes, usage)
     */
    public record OrchestrationResult(DailyBriefing briefing, List<AgentMessage> agentMessages,
                                      List<AgentResultEnvelope> envelopes, RuntimeRunReport report) {

        public OrchestrationResult {
            agentMessages = agentMessages == null ? List.of() : List.copyOf(agentMessages);
            envelopes = envelopes == null ? List.of() : List.copyOf(envelopes);
            report = report == null ? RuntimeRunReport.empty() : report;
        }

        public OrchestrationResult(DailyBriefing briefing, List<AgentMessage> agentMessages) {
            this(briefing, agentMessages, List.of(), RuntimeRunReport.empty());
        }
    }
}
