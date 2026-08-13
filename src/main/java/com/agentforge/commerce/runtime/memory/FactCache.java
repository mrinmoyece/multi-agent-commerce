package com.agentforge.commerce.runtime.memory;

import java.time.Duration;
import java.util.Optional;

/**
 * Hot cache for recently read semantic-memory facts. Entries are always written
 * with a TTL so a stale value self-heals, and keys are tenant-qualified so one
 * retailer can never read another's cached facts.
 */
public interface FactCache {

    /** Cache the serialised fact snapshot for {@code retailerId}/{@code key}. */
    void put(String retailerId, String key, String snapshotJson, Duration ttl);

    /** @return the cached snapshot for {@code retailerId}/{@code key}, if still fresh. */
    Optional<String> get(String retailerId, String key);

    /** Remove any cached value for {@code retailerId}/{@code key}. */
    void evict(String retailerId, String key);
}
