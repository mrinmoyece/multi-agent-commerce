package com.agentforge.commerce.platform.coordination;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived cache of run status snapshots. Lets status polling be served
 * without touching the primary database on every request; entries always carry
 * a TTL so a stale snapshot self-heals.
 */
public interface RunStatusCache {

    void put(UUID runId, String snapshotJson, Duration ttl);

    Optional<String> get(UUID runId);

    void evict(UUID runId);
}
