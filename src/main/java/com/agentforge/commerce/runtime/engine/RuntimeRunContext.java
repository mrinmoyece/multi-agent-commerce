package com.agentforge.commerce.runtime.engine;

import com.agentforge.commerce.runtime.brakes.RunBrakes;
import com.agentforge.commerce.runtime.budget.BudgetLedger;
import com.agentforge.commerce.runtime.memory.WorkingMemory;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Per-run execution context threaded through the agent runtime.
 *
 * <p>It carries the identity of the run, its tenant, the {@link BudgetLedger} that
 * enforces token/cost/latency ceilings, the {@link RunBrakes} that bound tool
 * calls, agent rounds and the deadline, and a run-local {@link WorkingMemory}
 * scratchpad. Bundling these into one immutable context keeps every runtime
 * component reading the same safety envelope.</p>
 */
public final class RuntimeRunContext {

    private final String runId;
    private final String retailerId;
    private final LocalDate businessDate;
    private final String provider;
    private final BudgetLedger ledger;
    private final RunBrakes brakes;
    private final WorkingMemory workingMemory;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public RuntimeRunContext(String runId, String retailerId, LocalDate businessDate, String provider,
                             BudgetLedger ledger, RunBrakes brakes, WorkingMemory workingMemory) {
        this.runId = runId;
        this.retailerId = retailerId;
        this.businessDate = businessDate;
        this.provider = provider;
        this.ledger = ledger;
        this.brakes = brakes;
        this.workingMemory = workingMemory;
    }

    public String runId() {
        return runId;
    }

    public String retailerId() {
        return retailerId;
    }

    public LocalDate businessDate() {
        return businessDate;
    }

    public String provider() {
        return provider;
    }

    public BudgetLedger ledger() {
        return ledger;
    }

    public RunBrakes brakes() {
        return brakes;
    }

    public WorkingMemory workingMemory() {
        return workingMemory;
    }

    /** @return the tool-request/deadline instant enforced by the run brakes. */
    public Instant deadline() {
        return brakes.deadline();
    }
}
