package com.agentforge.commerce.runtime.contract;

/**
 * A bounded, calibrated confidence in a claim, expressed on the closed interval
 * {@code [0.0, 1.0]}. The runtime uses confidence to drive human-in-the-loop
 * escalation: low-confidence recommendations are paused for approval.
 *
 * @param value     confidence in {@code [0.0, 1.0]}
 * @param rationale short, non-sensitive explanation of the score
 */
public record Confidence(double value, String rationale) {

    /** Threshold at or below which a recommendation is treated as low-confidence. */
    public static final double LOW_THRESHOLD = 0.5;

    /** Threshold at or above which a recommendation is treated as high-confidence. */
    public static final double HIGH_THRESHOLD = 0.8;

    public Confidence {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("confidence value must be within [0.0, 1.0]");
        }
        rationale = rationale == null ? "" : rationale;
    }

    /** @return {@code true} when confidence is at or below {@link #LOW_THRESHOLD}. */
    public boolean isLow() {
        return value <= LOW_THRESHOLD;
    }

    /** @return {@code true} when confidence is at or above {@link #HIGH_THRESHOLD}. */
    public boolean isHigh() {
        return value >= HIGH_THRESHOLD;
    }

    /** Confidence backed by no authoritative evidence. */
    public static Confidence none(String rationale) {
        return new Confidence(0.0, rationale);
    }

    /** Full confidence in a deterministically computed claim. */
    public static Confidence certain(String rationale) {
        return new Confidence(1.0, rationale);
    }
}
