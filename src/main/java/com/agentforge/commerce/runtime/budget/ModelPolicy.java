package com.agentforge.commerce.runtime.budget;

import com.agentforge.commerce.runtime.config.RuntimeProperties;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Enforces model/provider allowlists and resolves the per-agent model.
 *
 * <p>The runtime consults this before any provider call so an agent can only ever
 * use an explicitly approved provider and model. Per-agent overrides let, for
 * example, a low-stakes agent run on a cheaper model while high-stakes synthesis
 * uses the flagship — always within the allowlist.</p>
 */
@Component
public class ModelPolicy {

    private final Set<String> allowedProviders;
    private final Set<String> allowedModels;
    private final String defaultModel;
    private final RuntimeProperties properties;

    public ModelPolicy(RuntimeProperties properties) {
        this.properties = properties;
        RuntimeProperties.Models models = properties.getModels();
        this.allowedProviders = models.getAllowedProviders().stream()
                .map(ModelPolicy::normalise).collect(Collectors.toUnmodifiableSet());
        this.allowedModels = models.getAllowedModels().stream()
                .map(ModelPolicy::normalise).collect(Collectors.toUnmodifiableSet());
        this.defaultModel = models.getDefaultModel();
        if (!allowedModels.contains(normalise(defaultModel))) {
            throw new IllegalStateException("runtime.models.default-model must be within the allowlist");
        }
    }

    /** Validate that {@code provider} is on the allowlist, or throw. */
    public void checkProvider(String provider) {
        if (provider == null || !allowedProviders.contains(normalise(provider))) {
            throw new ModelNotAllowedException("provider not allowed: " + provider);
        }
    }

    /**
     * Resolve and validate the model for {@code agentName}: an explicit per-agent
     * override when present, otherwise the default. The result is always on the
     * allowlist or an exception is thrown.
     */
    public String resolveModel(String agentName) {
        String configured = properties.getModels().getPerAgent().getOrDefault(agentName, defaultModel);
        if (!allowedModels.contains(normalise(configured))) {
            throw new ModelNotAllowedException("model not allowed for agent " + agentName + ": " + configured);
        }
        return configured;
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
