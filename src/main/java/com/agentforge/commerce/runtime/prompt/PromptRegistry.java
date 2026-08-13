package com.agentforge.commerce.runtime.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Loads versioned prompt templates from committed classpath resources and exposes
 * them by id. Templates are treated as immutable, versioned resources: their
 * content hashes are computed at load time and recorded on every run and agent
 * result so briefings are reproducible and prompt drift is detectable.
 *
 * <p>The registry loads a fixed, known manifest of prompts at construction and
 * fails fast if any is missing or empty — a deployment can never start with a
 * partially-wired prompt set.</p>
 */
@Component
public class PromptRegistry {

    /** Well-known prompt identifiers used across the runtime. */
    public static final String INVENTORY_ANALYSIS = "inventory.analysis";
    public static final String PRICING_ANALYSIS = "pricing.analysis";
    public static final String SENTIMENT_ANALYSIS = "sentiment.analysis";
    public static final String COORDINATOR_SYNTHESIS = "coordinator.synthesis";

    private final Map<String, PromptTemplate> templates;

    public PromptRegistry() {
        Map<String, PromptTemplate> loaded = new LinkedHashMap<>();
        loaded.put(INVENTORY_ANALYSIS, load(INVENTORY_ANALYSIS, "v1", "prompts/inventory-analysis.v1.txt"));
        loaded.put(PRICING_ANALYSIS, load(PRICING_ANALYSIS, "v1", "prompts/pricing-analysis.v1.txt"));
        loaded.put(SENTIMENT_ANALYSIS, load(SENTIMENT_ANALYSIS, "v1", "prompts/sentiment-analysis.v1.txt"));
        loaded.put(COORDINATOR_SYNTHESIS, load(COORDINATOR_SYNTHESIS, "v1", "prompts/coordinator-synthesis.v1.txt"));
        this.templates = Map.copyOf(loaded);
    }

    /** @return the template for {@code id}, or throws if unknown. */
    public PromptTemplate get(String id) {
        PromptTemplate template = templates.get(id);
        if (template == null) {
            throw new IllegalArgumentException("unknown prompt id: " + id);
        }
        return template;
    }

    /** @return the number of registered templates. */
    public int size() {
        return templates.size();
    }

    private static PromptTemplate load(String id, String version, String resourcePath) {
        try (InputStream in = PromptRegistry.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("prompt resource not found: " + resourcePath);
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return PromptTemplate.of(id, version, content);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load prompt resource: " + resourcePath, e);
        }
    }
}
