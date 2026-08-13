package com.agentforge.commerce.perf;

import com.agentforge.commerce.platform.coordination.LockHandle;
import com.agentforge.commerce.platform.coordination.memory.InMemoryDistributedLock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic, offline performance and concurrency harness.
 *
 * <p>Every scenario runs entirely in-process against the real coordination
 * primitives (the same {@link InMemoryDistributedLock} the app uses when Redis is
 * disabled), so results are reproducible on any machine with no external
 * dependencies. Correctness invariants (e.g. exactly-once claiming) are asserted
 * hard; latency ceilings are deliberately generous so the gate is not flaky on
 * shared CI runners — it catches order-of-magnitude regressions, not micro-noise.</p>
 */
public final class PerfHarness {

    /** Result of a single perf scenario. */
    public record Scenario(String name, boolean passed, String budget, Map<String, Object> metrics) {
    }

    /**
     * Concurrent durable-run claiming: many threads race to claim the same run;
     * exactly one must win. Models the executor's lock + optimistic-claim protocol.
     */
    public Scenario concurrentClaimNoDuplicates() {
        int runs = 200;
        int threadsPerRun = 8;
        InMemoryDistributedLock lock = new InMemoryDistributedLock();
        AtomicInteger duplicateClaims = new AtomicInteger();
        AtomicInteger unclaimed = new AtomicInteger();
        List<Long> latenciesNanos = new ArrayList<>();
        Object latencyGuard = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(threadsPerRun);
        try {
            for (int r = 0; r < runs; r++) {
                String key = "run:" + r;
                AtomicBoolean claimed = new AtomicBoolean(false);
                AtomicInteger winners = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(threadsPerRun);
                for (int t = 0; t < threadsPerRun; t++) {
                    pool.execute(() -> {
                        await(start);
                        long t0 = System.nanoTime();
                        LockHandle handle = lock.tryAcquire(key, Duration.ofSeconds(5)).orElse(null);
                        long elapsed = System.nanoTime() - t0;
                        synchronized (latencyGuard) {
                            latenciesNanos.add(elapsed);
                        }
                        if (handle != null) {
                            try (handle) {
                                if (claimed.compareAndSet(false, true)) {
                                    winners.incrementAndGet();
                                }
                            }
                        }
                        done.countDown();
                    });
                }
                start.countDown();
                await(done);
                int w = winners.get();
                if (w > 1) {
                    duplicateClaims.incrementAndGet();
                } else if (w == 0) {
                    unclaimed.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
        }

        long p99 = percentileMillis(latenciesNanos, 99);
        boolean passed = duplicateClaims.get() == 0 && unclaimed.get() == 0 && p99 < 250;
        return new Scenario("concurrent-claim-no-duplicates", passed,
                "duplicates==0 && unclaimed==0 && p99<250ms",
                Map.of("runs", runs, "threadsPerRun", threadsPerRun,
                        "duplicateClaims", duplicateClaims.get(), "unclaimed", unclaimed.get(),
                        "claimP99Millis", p99));
    }

    /**
     * Coordination throughput and op latency: sustained acquire/release across a
     * pool. Proxies the platform's hot coordination path with an ops/sec floor.
     */
    public Scenario coordinationThroughput() {
        int threads = 8;
        int opsPerThread = 20_000;
        InMemoryDistributedLock lock = new InMemoryDistributedLock();
        ConcurrentHashMap<Integer, AtomicLong> perThread = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long wallStart;
        try {
            for (int i = 0; i < threads; i++) {
                int id = i;
                perThread.put(id, new AtomicLong());
                pool.execute(() -> {
                    await(start);
                    long ok = 0;
                    for (int op = 0; op < opsPerThread; op++) {
                        String key = "k:" + id + ":" + (op & 0x3F);
                        LockHandle h = lock.tryAcquire(key, Duration.ofSeconds(1)).orElse(null);
                        if (h != null) {
                            h.close();
                            ok++;
                        }
                    }
                    perThread.get(id).addAndGet(ok);
                    done.countDown();
                });
            }
            wallStart = System.nanoTime();
            start.countDown();
            await(done);
        } finally {
            pool.shutdownNow();
        }
        long wallNanos = System.nanoTime() - wallStart;
        long totalOps = perThread.values().stream().mapToLong(AtomicLong::get).sum();
        double opsPerSec = totalOps / (wallNanos / 1_000_000_000.0);
        boolean passed = opsPerSec > 50_000;
        return new Scenario("coordination-throughput", passed, "opsPerSec>50000",
                Map.of("threads", threads, "opsPerThread", opsPerThread,
                        "totalOps", totalOps, "opsPerSec", Math.round(opsPerSec)));
    }

    /** Runs every scenario. */
    public List<Scenario> runAll() {
        List<Scenario> results = new ArrayList<>();
        results.add(concurrentClaimNoDuplicates());
        results.add(coordinationThroughput());
        return results;
    }

    /** Fast subset for the smoke task. */
    public List<Scenario> runSmoke() {
        return List.of(concurrentClaimNoDuplicates());
    }

    private static long percentileMillis(List<Long> nanos, int percentile) {
        if (nanos.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(nanos);
        sorted.sort(Long::compareTo);
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return TimeUnit.NANOSECONDS.toMillis(sorted.get(idx));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting latch", e);
        }
    }
}
