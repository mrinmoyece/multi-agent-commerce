package com.agentforge.commerce.runtime.brakes;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-run governor that bounds how much work a single run may perform and lets it
 * be stopped deterministically.
 *
 * <p>It enforces four brakes: a ceiling on tool invocations, a ceiling on agent
 * rounds, an absolute wall-clock deadline (propagated to every tool via the
 * request), and cooperative cancellation. Every tool call and agent round must
 * pass through this governor, so no run can loop, fan out, or overrun unbounded.
 * The runtime performs no arbitrary code execution and no unrestricted URL
 * fetches; the only outbound calls are to the allowlisted provider and configured
 * tool adapters.</p>
 */
public final class RunBrakes {

    private final int maxToolCalls;
    private final int maxAgentRounds;
    private final Instant deadline;
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicInteger agentRounds = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public RunBrakes(int maxToolCalls, int maxAgentRounds, Instant deadline) {
        if (maxToolCalls <= 0 || maxAgentRounds <= 0) {
            throw new IllegalArgumentException("brake ceilings must be positive");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("deadline must be provided");
        }
        this.maxToolCalls = maxToolCalls;
        this.maxAgentRounds = maxAgentRounds;
        this.deadline = deadline;
    }

    /** @return the absolute deadline propagated to tool requests. */
    public Instant deadline() {
        return deadline;
    }

    /** Register a tool call; trips if the ceiling, deadline, or cancellation is hit. */
    public void onToolCall(Instant now) {
        checkNotCancelled();
        checkDeadline(now);
        if (toolCalls.incrementAndGet() > maxToolCalls) {
            throw new BrakeTrippedException("tool-calls",
                    "maximum tool calls exceeded (" + maxToolCalls + ")");
        }
    }

    /** Register an agent round; trips if the ceiling, deadline, or cancellation is hit. */
    public void onAgentRound(Instant now) {
        checkNotCancelled();
        checkDeadline(now);
        if (agentRounds.incrementAndGet() > maxAgentRounds) {
            throw new BrakeTrippedException("agent-rounds",
                    "maximum agent rounds exceeded (" + maxAgentRounds + ")");
        }
    }

    /** Request cooperative cancellation of the run. */
    public void cancel() {
        cancelled.set(true);
    }

    /** @return {@code true} once cancellation has been requested. */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Trip if cancellation has been requested. */
    public void checkNotCancelled() {
        if (cancelled.get()) {
            throw new BrakeTrippedException("cancelled", "run was cancelled");
        }
    }

    /** Trip if the run deadline has passed at {@code now}. */
    public void checkDeadline(Instant now) {
        if (now.isAfter(deadline)) {
            throw new BrakeTrippedException("deadline", "run deadline exceeded");
        }
    }

    /** @return remaining time until the deadline at {@code now} (never negative). */
    public Duration remaining(Instant now) {
        Duration remaining = Duration.between(now, deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** @return the number of tool calls registered so far. */
    public int toolCallCount() {
        return toolCalls.get();
    }

    /** @return the number of agent rounds registered so far. */
    public int agentRoundCount() {
        return agentRounds.get();
    }
}
