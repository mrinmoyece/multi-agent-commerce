package com.agentforge.commerce.platform.coordination;

import java.time.Duration;
import java.util.Optional;

/**
 * Cluster-wide mutual exclusion. Backed by Redis in production so that exactly
 * one replica may hold a named lock at a time; an in-memory implementation backs
 * the {@code local}/{@code test} profiles.
 *
 * <p>Locks are always fenced with a TTL so a crashed holder cannot deadlock the
 * cluster — the lock self-expires and another replica may proceed.</p>
 */
public interface DistributedLock {

    /**
     * Attempt to acquire {@code key} for at most {@code ttl}.
     *
     * @return a handle that must be closed to release the lock, or empty if the
     *     lock is already held elsewhere.
     */
    Optional<LockHandle> tryAcquire(String key, Duration ttl);
}
