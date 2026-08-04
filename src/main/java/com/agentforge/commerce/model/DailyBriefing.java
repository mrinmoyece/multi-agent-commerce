package com.agentforge.commerce.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily executive briefing produced by the AgentCoordinator.
 * Contains both raw agent findings and the synthesised cross-agent insights.
 */
@Data
@Builder
public class DailyBriefing {
    private String briefingId;
    private LocalDate date;
    private String retailerId;

    // Raw findings from each specialist agent
    private String inventoryFindings;
    private String pricingFindings;
    private String sentimentFindings;

    // Coordinator synthesis
    private List<String> crossInsights;
    private List<String> recommendedActions;
    private List<String> urgentAlerts;
    private String executiveSummary;
    private String rawSynthesis;

    private Instant generatedAt;
}
