package com.agentforge.commerce.platform.coordination;

import java.util.function.Consumer;

/**
 * Handle to an acquired {@link DistributedLock}. Closing the handle releases the
 * lock and is idempotent, so it is safe to use in try-with-resources.
 */
public final class LockHandle implements AutoCloseable {

    private final String key;
    private final String fencingToken;
    private final transient Consumer<LockHandle> releaser;
    private boolean released;

    public LockHandle(String key, String fencingToken, Consumer<LockHandle> releaser) {
        this.key = key;
        this.fencingToken = fencingToken;
        this.releaser = releaser;
    }

    public String getKey() {
        return key;
    }

    /** Unique per-acquisition token; only the holder of this token may release. */
    public String getFencingToken() {
        return fencingToken;
    }

    @Override
    public void close() {
        if (!released) {
            released = true;
            releaser.accept(this);
        }
    }
}
