package com.agentforge.commerce.eval;

import java.util.List;
import java.util.Map;

/**
 * The deterministic outcome of one golden evaluation scenario.
 *
 * <p>Metrics are ordered by key so the serialised report is byte-stable across
 * runs, enabling committed baselines and drift detection. No prompts, payloads or
 * credentials are ever recorded — only aggregate, non-sensitive scores and the
 * short reason notes that explain a pass or fail.</p>
 *
 * @param id          stable scenario identifier
 * @param description one-line human description of what the scenario proves
 * @param passed      whether every threshold for this scenario was met
 * @param metrics     ordered metric name → rounded score
 * @param notes       short, non-sensitive explanatory notes
 */
public record ScenarioResult(
        String id,
        String description,
        boolean passed,
        Map<String, Double> metrics,
        List<String> notes) {

    public ScenarioResult {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
