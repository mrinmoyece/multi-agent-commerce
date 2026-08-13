package com.agentforge.commerce.runtime.brakes;

/**
 * Thrown when a runtime brake trips: too many tool calls or agent rounds, a
 * passed deadline, or an explicit cancellation. These are safety stops, not
 * errors in the data — the run is halted deterministically rather than allowed to
 * run away.
 */
public class BrakeTrippedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String brake;

    public BrakeTrippedException(String brake, String message) {
        super(message);
        this.brake = brake;
    }

    /** @return the brake that tripped (e.g. {@code tool-calls}, {@code deadline}, {@code cancelled}). */
    public String brake() {
        return brake;
    }
}
