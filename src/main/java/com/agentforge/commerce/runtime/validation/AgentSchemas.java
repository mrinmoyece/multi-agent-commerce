package com.agentforge.commerce.runtime.validation;

/**
 * The canonical output schemas for every specialist agent and the coordinator.
 * Centralising them keeps the strict contracts in one auditable place and lets
 * the evaluation harness assert schema validity against the exact same rules the
 * runtime enforces at execution time.
 */
public final class AgentSchemas {

    /** Inventory specialist output contract. */
    public static final JsonSchema INVENTORY = JsonSchema.named("inventory.analysis")
            .require("risks", FieldType.STRING_ARRAY)
            .require("reorders", FieldType.STRING_ARRAY)
            .require("clearance", FieldType.STRING_ARRAY)
            .require("citedSkus", FieldType.STRING_ARRAY)
            .require("summary", FieldType.NONEMPTY_TEXT)
            .build();

    /** Pricing specialist output contract. */
    public static final JsonSchema PRICING = JsonSchema.named("pricing.analysis")
            .require("repricingOpportunities", FieldType.STRING_ARRAY)
            .require("marginRisks", FieldType.STRING_ARRAY)
            .require("citedSkus", FieldType.STRING_ARRAY)
            .require("summary", FieldType.NONEMPTY_TEXT)
            .build();

    /** Sentiment specialist output contract. */
    public static final JsonSchema SENTIMENT = JsonSchema.named("sentiment.analysis")
            .require("positiveThemes", FieldType.STRING_ARRAY)
            .require("negativeThemes", FieldType.STRING_ARRAY)
            .require("citedTopics", FieldType.STRING_ARRAY)
            .require("summary", FieldType.NONEMPTY_TEXT)
            .build();

    /** Coordinator synthesis output contract. */
    public static final JsonSchema COORDINATOR = JsonSchema.named("coordinator.synthesis")
            .require("crossInsights", FieldType.STRING_ARRAY)
            .require("recommendedActions", FieldType.STRING_ARRAY)
            .require("urgentAlerts", FieldType.STRING_ARRAY)
            .require("citedReferences", FieldType.STRING_ARRAY)
            .require("executiveSummary", FieldType.NONEMPTY_TEXT)
            .build();

    private AgentSchemas() {
    }
}
