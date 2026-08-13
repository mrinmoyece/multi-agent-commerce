package com.agentforge.commerce.resilience;

import com.agentforge.commerce.platform.coordination.DistributedLock;
import com.agentforge.commerce.platform.coordination.LockHandle;
import com.agentforge.commerce.platform.coordination.memory.InMemoryDistributedLock;
import com.agentforge.commerce.runtime.tools.live.http.AdapterException;
import com.agentforge.commerce.runtime.tools.live.http.AdapterResilience;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Deterministic, offline resilience / chaos harness.
 *
 * <p>Each scenario injects a specific failure mode into the real resilience
 * primitives (the adapter {@link CircuitBreaker}/{@link Retry} policy and the
 * coordination {@link DistributedLock}) and asserts the platform's documented
 * behaviour: transient faults are retried and isolated by an opening circuit;
 * deterministic faults fail fast; a lost coordination backend fails closed without
 * duplicate execution; deadlines are honoured. No network, Docker or clock sleeps
 * beyond bounded retry backoff are used, so the gate is reproducible everywhere.</p>
 */
public final class ResilienceHarness {

    /** Result of a single resilience scenario. */
    public record Scenario(String name, boolean passed, String expectation, Map<String, Object> observed) {
    }

    /**
     * Provider/adapter throttling (HTTP 429 → transient): the retry policy should
     * re-attempt and ultimately succeed within the attempt budget.
     */
    public Scenario providerThrottlingRecovers() {
        Retry retry = AdapterResilience.retry("throttle", 3, Duration.ofMillis(1));
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new AdapterException(AdapterException.Kind.TIMEOUT, "pricing");
            }
            return "ok";
        });
        String result = call.get();
        boolean passed = "ok".equals(result) && attempts.get() == 3;
        return new Scenario("provider-throttling-recovers", passed,
                "retryable 429/timeout retried then succeeds",
                Map.of("attempts", attempts.get(), "result", result));
    }

    /**
     * Partial tool outage: a persistently failing adapter must open its circuit and
     * then fail fast (CircuitBreaker short-circuits) rather than hammering the
     * backend — isolating the fault from the rest of the run.
     */
    public Scenario partialToolOutageOpensCircuit() {
        CircuitBreaker breaker = AdapterResilience.circuitBreaker(
                "sentiment", 5, 50.0f, 5, Duration.ofSeconds(30));
        Supplier<String> failing = CircuitBreaker.decorateSupplier(breaker, () -> {
            throw new AdapterException(AdapterException.Kind.TIMEOUT, "sentiment");
        });
        int transientFailures = 0;
        int shortCircuited = 0;
        for (int i = 0; i < 20; i++) {
            try {
                failing.get();
            } catch (CallNotPermittedException e) {
                shortCircuited++;
            } catch (AdapterException e) {
                transientFailures++;
            }
        }
        boolean opened = breaker.getState() == CircuitBreaker.State.OPEN;
        boolean passed = opened && shortCircuited > 0 && transientFailures <= 6;
        return new Scenario("partial-tool-outage-opens-circuit", passed,
                "circuit OPEN and calls short-circuit after threshold",
                Map.of("state", breaker.getState().name(),
                        "transientFailures", transientFailures, "shortCircuited", shortCircuited));
    }

    /**
     * Deterministic client fault (auth/malformed) must fail fast: never retried,
     * never counted toward opening the circuit.
     */
    public Scenario deterministicFaultFailsFast() {
        Retry retry = AdapterResilience.retry("auth", 3, Duration.ofMillis(1));
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new AdapterException(AdapterException.Kind.AUTH, "inventory");
        });
        boolean failedFast = false;
        try {
            call.get();
        } catch (AdapterException e) {
            failedFast = e.kind() == AdapterException.Kind.AUTH;
        }
        boolean passed = failedFast && attempts.get() == 1;
        return new Scenario("deterministic-fault-fails-fast", passed,
                "non-retryable fault attempted exactly once",
                Map.of("attempts", attempts.get()));
    }

    /**
     * Redis/coordination outage: a lock backend that cannot grant the lock (models
     * Redis being unreachable) must cause the claim to fail closed — the run is NOT
     * executed by this replica, and no duplicate execution occurs.
     */
    public Scenario coordinationOutageFailsClosed() {
        DistributedLock unavailable = (key, ttl) -> Optional.empty();
        AtomicInteger executed = new AtomicInteger();
        boolean claimed = tryClaimAndRun(unavailable, "run:x", executed);
        boolean passed = !claimed && executed.get() == 0;
        return new Scenario("coordination-outage-fails-closed", passed,
                "unavailable lock => run not executed (fail closed)",
                Map.of("claimed", claimed, "executed", executed.get()));
    }

    /**
     * Transient coordination failure then recovery: first acquire is held by another
     * replica; after it releases, this replica can claim exactly once.
     */
    public Scenario coordinationTransientThenRecovers() {
        InMemoryDistributedLock lock = new InMemoryDistributedLock();
        AtomicInteger executed = new AtomicInteger();
        LockHandle other = lock.tryAcquire("run:y", Duration.ofSeconds(30)).orElseThrow();
        boolean blocked = tryClaimAndRun(lock, "run:y", executed);
        other.close();
        boolean afterRelease = tryClaimAndRun(lock, "run:y", executed);
        boolean passed = !blocked && afterRelease && executed.get() == 1;
        return new Scenario("coordination-transient-then-recovers", passed,
                "blocked while held, single execution after release",
                Map.of("blockedWhileHeld", !blocked, "ranAfterRelease", afterRelease,
                        "executed", executed.get()));
    }

    /**
     * Cancellation / deadline: a supplier that exceeds the deadline is abandoned and
     * surfaces a TIMEOUT rather than blocking indefinitely.
     */
    public Scenario deadlineIsHonoured() {
        Duration deadline = Duration.ofMillis(50);
        long start = System.nanoTime();
        boolean timedOut = false;
        try {
            runWithDeadline(deadline, () -> {
                sleep(500);
                return "late";
            });
        } catch (AdapterException e) {
            timedOut = e.kind() == AdapterException.Kind.TIMEOUT;
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        boolean passed = timedOut && elapsedMillis < 400;
        return new Scenario("deadline-is-honoured", passed,
                "work exceeding deadline aborts with TIMEOUT promptly",
                Map.of("timedOut", timedOut, "elapsedMillis", elapsedMillis));
    }

    /**
     * Graceful shutdown drain: in-flight work started before shutdown completes; new
     * work submitted after shutdown is rejected. Models the app's graceful drain.
     */
    public Scenario gracefulShutdownDrains() {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            pool.execute(() -> {
                sleep(20);
                completed.incrementAndGet();
            });
        }
        pool.shutdown();
        try {
            pool.execute(completed::incrementAndGet);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            rejected.incrementAndGet();
        }
        boolean drained;
        try {
            drained = pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            drained = false;
        }
        boolean passed = drained && completed.get() == 4 && rejected.get() == 1;
        return new Scenario("graceful-shutdown-drains", passed,
                "in-flight tasks finish, post-shutdown work rejected",
                Map.of("completed", completed.get(), "rejected", rejected.get(), "drained", drained));
    }

    /** Runs every scenario. */
    public List<Scenario> runAll() {
        List<Scenario> results = new ArrayList<>();
        results.add(providerThrottlingRecovers());
        results.add(partialToolOutageOpensCircuit());
        results.add(deterministicFaultFailsFast());
        results.add(coordinationOutageFailsClosed());
        results.add(coordinationTransientThenRecovers());
        results.add(deadlineIsHonoured());
        results.add(gracefulShutdownDrains());
        return results;
    }

    /** Fast subset for the smoke task. */
    public List<Scenario> runSmoke() {
        return List.of(coordinationOutageFailsClosed(), partialToolOutageOpensCircuit());
    }

    private static boolean tryClaimAndRun(DistributedLock lock, String key, AtomicInteger executed) {
        LockHandle handle = lock.tryAcquire(key, Duration.ofSeconds(5)).orElse(null);
        if (handle == null) {
            return false;
        }
        try (handle) {
            executed.incrementAndGet();
            return true;
        }
    }

    private static <T> T runWithDeadline(Duration deadline, Supplier<T> work) {
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<T> future = exec.submit(work::get);
            return future.get(deadline.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AdapterException(AdapterException.Kind.TIMEOUT, "deadline");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AdapterException(AdapterException.Kind.TRANSPORT, "deadline");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdapterException(AdapterException.Kind.TRANSPORT, "deadline");
        } finally {
            exec.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
