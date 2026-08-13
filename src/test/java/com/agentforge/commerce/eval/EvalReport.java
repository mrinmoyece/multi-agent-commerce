package com.agentforge.commerce.eval;

import java.util.List;

/**
 * The full, deterministic evaluation report over every golden scenario.
 *
 * <p>The report intentionally contains no timestamps or environment-specific
 * values so it serialises identically on every machine; that byte-stability is
 * what lets a committed baseline drive CI drift detection. {@link #allPassed()}
 * is the merge gate signal.</p>
 *
 * @param schemaVersion report schema version for forward compatibility
 * @param generatedBy   fixed producer identifier (no host/user data)
 * @param scenarios     per-scenario results, in declaration order
 */
public record EvalReport(int schemaVersion, String generatedBy, List<ScenarioResult> scenarios) {

    public EvalReport {
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
    }

    /** @return the number of scenarios that met every threshold. */
    public long passedCount() {
        return scenarios.stream().filter(ScenarioResult::passed).count();
    }

    /** @return {@code true} when every scenario passed its thresholds. */
    public boolean allPassed() {
        return scenarios.stream().allMatch(ScenarioResult::passed);
    }
}
