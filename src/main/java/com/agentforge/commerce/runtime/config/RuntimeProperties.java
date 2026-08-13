package com.agentforge.commerce.runtime.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Externalised configuration for the Layer 2 agent runtime. Everything that
 * governs safety posture — budgets, model/provider allowlists, tool grounding
 * mode, runtime brakes, memory retention, and risk thresholds — is bound from the
 * {@code runtime.*} namespace so it can be tuned per environment without code
 * changes and audited in one place.
 */
@ConfigurationProperties(prefix = "runtime")
@Getter
@Setter
public class RuntimeProperties {

    @NestedConfigurationProperty
    private Budget budget = new Budget();

    @NestedConfigurationProperty
    private Models models = new Models();

    @NestedConfigurationProperty
    private Tools tools = new Tools();

    @NestedConfigurationProperty
    private Brakes brakes = new Brakes();

    @NestedConfigurationProperty
    private Memory memory = new Memory();

    @NestedConfigurationProperty
    private Risk risk = new Risk();

    /** Per-run token, request, latency, and estimated-cost budgets and cost model. */
    @Getter
    @Setter
    public static class Budget {
        /** Hard ceiling on total tokens (prompt + completion) per run. */
        private long maxTokens = 120_000;
        /** Hard ceiling on provider requests per run. */
        private long maxRequests = 24;
        /** Hard ceiling on wall-clock provider latency budget per run. */
        private Duration maxLatency = Duration.ofMinutes(3);
        /** Hard ceiling on estimated cost per run, in micro-USD. */
        private long maxCostMicros = 500_000;
        /** Estimated cost per 1000 prompt tokens, in micro-USD. */
        private long promptMicrosPerThousand = 2_500;
        /** Estimated cost per 1000 completion tokens, in micro-USD. */
        private long completionMicrosPerThousand = 10_000;
    }

    /** Model/provider allowlists and per-agent model selection. */
    @Getter
    @Setter
    public static class Models {
        /** Providers permitted to be used at runtime. */
        private List<String> allowedProviders = List.of("azure-openai", "github-models");
        /** Models/deployments permitted to be used at runtime. */
        private List<String> allowedModels = List.of("gpt-4o", "gpt-4o-mini");
        /** Default model when an agent has no explicit override. */
        private String defaultModel = "gpt-4o";
        /** Optional per-agent model overrides keyed by agent name. */
        private Map<String, String> perAgent = Map.of();
    }

    /** Grounding mode for tool adapters. */
    @Getter
    @Setter
    public static class Tools {
        /**
         * Active grounding source: {@code FIXTURE} (deterministic committed data,
         * the default) or {@code LIVE} (production adapters). The runtime labels
         * every result with its true provenance regardless of this setting.
         */
        private String mode = "FIXTURE";
    }

    /** Runtime brakes that bound how much work a single run may perform. */
    @Getter
    @Setter
    public static class Brakes {
        /** Maximum number of tool invocations per run. */
        private int maxToolCalls = 32;
        /** Maximum number of agent rounds (specialist + synthesis passes) per run. */
        private int maxAgentRounds = 4;
        /** Maximum number of specialist agents executed concurrently. */
        private int maxConcurrency = 3;
        /** Absolute deadline budget for a whole run's agent phase. */
        private Duration deadline = Duration.ofMinutes(2);
    }

    /** Retailer-scoped semantic memory retention and bounds. */
    @Getter
    @Setter
    public static class Memory {
        /** Whether durable semantic memory is enabled. */
        private boolean enabled = true;
        /** Default time-to-live for a written fact. */
        private Duration factTtl = Duration.ofDays(7);
        /** Maximum retained facts per retailer (older facts are evicted). */
        private int maxFactsPerRetailer = 500;
        /** Hot-cache TTL for recently read facts. */
        private Duration cacheTtl = Duration.ofMinutes(10);
    }

    /** Thresholds and keyword rules for human-in-the-loop risk policy. */
    @Getter
    @Setter
    public static class Risk {
        /** Whether human-in-the-loop risk gating is enforced (pauses runs for approval). */
        private boolean enabled = true;
        /** Confidence at or below which a recommendation is escalated for approval. */
        private double lowConfidenceThreshold = 0.5;
        /** Substrings that mark a recommendation as a high-impact pricing change. */
        private List<String> pricingKeywords = List.of("reprice", "price change", "markdown", "discount", "raise price");
        /** Substrings that mark a recommendation as a high-impact emergency order. */
        private List<String> emergencyKeywords = List.of("emergency order", "expedite", "air freight", "immediate reorder");
    }
}
