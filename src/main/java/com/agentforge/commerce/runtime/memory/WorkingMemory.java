package com.agentforge.commerce.runtime.memory;

import com.agentforge.commerce.runtime.contract.Evidence;
import com.agentforge.commerce.runtime.contract.RuntimeLimits;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Run-local working memory: a small, bounded scratchpad scoped to a single run.
 *
 * <p>Unlike durable semantic memory, working memory is ephemeral — it lives only
 * for the duration of one orchestration and is discarded afterwards. It lets the
 * coordinator and specialists share grounded intermediate observations (each
 * carrying its {@link Evidence}) without re-fetching, while a hard entry ceiling
 * prevents unbounded growth. It is never persisted and never memorised as fact
 * without independent validation.</p>
 */
public final class WorkingMemory {

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** A single working-memory entry: a bounded value plus the evidence that grounds it. */
    public record Entry(String value, Evidence evidence) {
    }

    /** Store a grounded value under {@code key}, enforcing the entry ceiling. */
    public synchronized void put(String key, String value, Evidence evidence) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("working-memory key must be provided");
        }
        if (value == null) {
            throw new IllegalArgumentException("working-memory value must be provided");
        }
        if (!entries.containsKey(key) && entries.size() >= RuntimeLimits.MAX_WORKING_MEMORY_ENTRIES) {
            throw new IllegalStateException("working memory is full ("
                    + RuntimeLimits.MAX_WORKING_MEMORY_ENTRIES + " entries)");
        }
        entries.put(key, new Entry(value, evidence));
    }

    /** @return the entry stored under {@code key}, if present. */
    public synchronized Optional<Entry> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    /** @return the number of entries currently held. */
    public synchronized int size() {
        return entries.size();
    }
}
