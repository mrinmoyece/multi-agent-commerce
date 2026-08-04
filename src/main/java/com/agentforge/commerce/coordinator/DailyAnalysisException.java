package com.agentforge.commerce.coordinator;

/**
 * Raised when the coordinator's daily analysis run fails — either because a
 * specialist agent's future could not be retrieved, or because synthesis of
 * the daily briefing failed.
 *
 * Preserves the original cause (rather than collapsing it into a generic
 * {@link RuntimeException}) so the real failure reason survives in logs and
 * stack traces.
 */
public class DailyAnalysisException extends RuntimeException {

    public DailyAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
