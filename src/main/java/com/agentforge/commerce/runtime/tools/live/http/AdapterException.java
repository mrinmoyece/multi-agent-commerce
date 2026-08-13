package com.agentforge.commerce.runtime.tools.live.http;

/**
 * Secret-safe failure raised by a live production adapter. The {@link Kind}
 * classifies the failure so the runtime, telemetry and tests can reason about it
 * without ever parsing a free-text message. The message is deliberately coarse
 * (adapter name + kind only) and must never carry a URL, credential, token,
 * header, or raw upstream body — those are the values most likely to leak
 * secrets into logs or error responses.
 */
public final class AdapterException extends RuntimeException {

    /** Classifies an adapter failure for retry, circuit-breaking and telemetry. */
    public enum Kind {
        /** Configured endpoint host is not on the adapter allowlist, or scheme is not HTTPS. */
        FORBIDDEN_HOST(false),
        /** Upstream rejected our credentials (401/403) or a token could not be obtained. */
        AUTH(false),
        /** Connect or read deadline elapsed. */
        TIMEOUT(true),
        /** Response body exceeded the adapter's bounded size ceiling. */
        OVERSIZED(false),
        /** Response could not be parsed against the strict adapter schema. */
        MALFORMED(false),
        /** Upstream evidence is older than the adapter's freshness ceiling. */
        STALE(false),
        /** Pagination did not complete within the adapter's page ceiling. */
        PARTIAL(false),
        /** A transient transport/5xx/429 error that is safe to retry. */
        TRANSPORT(true),
        /** The adapter circuit is open and the call was not attempted. */
        CIRCUIT_OPEN(false),
        /** The adapter is misconfigured and cannot run in LIVE mode. */
        CONFIG(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }

        /** @return {@code true} when a failure of this kind is safe to retry. */
        public boolean isRetryable() {
            return retryable;
        }
    }

    private final transient Kind kind;

    public AdapterException(Kind kind, String adapterName) {
        super(adapterName + " adapter failed: " + kind.name());
        this.kind = kind;
    }

    public AdapterException(Kind kind, String adapterName, Throwable cause) {
        super(adapterName + " adapter failed: " + kind.name(), sanitize(cause));
        this.kind = kind;
    }

    /** @return the classification of this failure. */
    public Kind kind() {
        return kind;
    }

    /** @return {@code true} when this failure is safe to retry. */
    public boolean isRetryable() {
        return kind.isRetryable();
    }

    /**
     * Strips a cause down to its type only. The original cause may carry a URL,
     * host, or upstream body in its message; we keep the class name (useful for
     * telemetry) but drop the message and any deeper linkage.
     */
    private static Throwable sanitize(Throwable cause) {
        if (cause == null) {
            return null;
        }
        return new IllegalStateException(cause.getClass().getSimpleName());
    }
}
