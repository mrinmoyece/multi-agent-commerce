package com.agentforge.commerce.agent;

import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;
import com.agentforge.commerce.llm.LlmGateway;
import com.agentforge.commerce.tool.InventoryDataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inventory Specialist Agent.
 *
 * Monitors stock levels across all SKUs, flags critical stockouts,
 * identifies overstock clearance opportunities, and surfaces reorder recommendations.
 *
 * This agent knows only about inventory — pricing and sentiment are other agents' domain.
 * Cross-cutting insights are produced by the AgentCoordinator after all agents report.
 */
@Component("inventoryAgent")
@RequiredArgsConstructor
@Slf4j
public class InventoryAgent implements CommerceAgent {

    private final InventoryDataTool inventoryDataTool;
    private final LlmGateway llmGateway;

    @Override
    public String getName() { return "InventoryAgent"; }

    @Override
    public AgentMessage analyse(DailyAnalysisContext context) {
        log.info("[InventoryAgent] Starting analysis for date={} retailer={}",
                context.getDate(), context.getRetailerId());

        List<InventoryDataTool.StockLevel> stockLevels   = inventoryDataTool.getCurrentStockLevels();
        List<InventoryDataTool.StockLevel> criticalItems  = stockLevels.stream()
                .filter(s -> s.getDaysOfStockRemaining() < 7).toList();
        List<InventoryDataTool.StockLevel> overstocked    = stockLevels.stream()
                .filter(s -> s.getDaysOfStockRemaining() > 90).toList();

        log.info("[InventoryAgent] {} critical, {} overstocked of {} total SKUs",
                criticalItems.size(), overstocked.size(), stockLevels.size());

        String findings = callLLM(stockLevels, criticalItems, overstocked);

        return AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .agentName(getName())
                .messageType(AgentMessage.MessageType.FINDINGS)
                .priority(criticalItems.isEmpty()
                        ? AgentMessage.AgentMessagePriority.NORMAL
                        : AgentMessage.AgentMessagePriority.HIGH)
                .content(findings)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Calls the LLM to turn raw stock-level data into an inventory briefing.
     * The injected gateway owns retries, circuit breaking, response bounds, and
     * transport timeouts. A failure produces an explicit degraded response.
     */
    String callLLM(List<InventoryDataTool.StockLevel> all,
                   List<InventoryDataTool.StockLevel> critical,
                   List<InventoryDataTool.StockLevel> overstocked) {
        String prompt = """
                You are an inventory analyst for a high-volume UK retailer.

                Stock summary:
                - Total SKUs monitored: %d
                - Critical (< 7 days stock): %d — SKUs: %s
                - Overstocked (> 90 days): %d — SKUs: %s

                Provide:
                1. Top 3 inventory risks with recommended actions
                2. Reorder recommendations for critical items
                3. Clearance opportunities for overstocked items
                4. One-sentence executive summary

                Return JSON: {"risks":[],"reorders":[],"clearance":[],"summary":""}
                """.formatted(
                all.size(),
                critical.size(),
                critical.stream().map(InventoryDataTool.StockLevel::getSku).limit(5).toList(),
                overstocked.size(),
                overstocked.stream().map(InventoryDataTool.StockLevel::getSku).limit(5).toList());

        try {
            return llmGateway.complete(prompt, 600, 0.3);
        } catch (RuntimeException exception) {
            return callLLMFallback(all, critical, overstocked, exception);
        }
    }

    /** Fallback when the "llm" circuit breaker is open or the LLM call fails. */
    String callLLMFallback(List<InventoryDataTool.StockLevel> all,
                           List<InventoryDataTool.StockLevel> critical,
                           List<InventoryDataTool.StockLevel> overstocked,
                           Throwable t) {
        log.warn("[InventoryAgent] LLM unavailable ({}), returning degraded summary",
                t.getClass().getSimpleName());
        return """
                {"risks":[],"reorders":[],"clearance":[],"summary":"LLM unavailable — %d critical and %d overstocked SKUs require manual review."}
                """.formatted(critical.size(), overstocked.size());
    }
}
