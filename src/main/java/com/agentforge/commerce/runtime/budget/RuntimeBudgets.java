package com.agentforge.commerce.runtime.budget;

import com.agentforge.commerce.runtime.config.RuntimeProperties;
import org.springframework.stereotype.Component;

/**
 * Builds a fresh {@link BudgetLedger} for each run from the configured
 * {@link RuntimeProperties}. Keeping construction in one place ensures every run
 * starts with the same, externally-tunable ceilings and cost model.
 */
@Component
public class RuntimeBudgets {

    private final RuntimeProperties properties;

    public RuntimeBudgets(RuntimeProperties properties) {
        this.properties = properties;
    }

    /** @return the immutable run budget derived from current configuration. */
    public RunBudget budget() {
        RuntimeProperties.Budget b = properties.getBudget();
        return new RunBudget(
                b.getMaxTokens(),
                b.getMaxRequests(),
                b.getMaxLatency(),
                b.getMaxCostMicros(),
                b.getPromptMicrosPerThousand(),
                b.getCompletionMicrosPerThousand());
    }

    /** @return a new, empty ledger enforcing the current budget. */
    public BudgetLedger newLedger() {
        return new BudgetLedger(budget());
    }
}
