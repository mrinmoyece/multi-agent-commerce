package com.agentforge.commerce.runtime.telemetry;

import io.micrometer.observation.Observation;

/**
 * Handle over an in-flight {@link Observation} span. Provides a minimal, null-safe
 * surface for attaching low-cardinality attributes and closing the span. Never
 * accepts prompts, credentials or payloads.
 */
public final class RuntimeSpan {

    private final Observation observation;

    RuntimeSpan(Observation observation) {
        this.observation = observation;
    }

    public RuntimeSpan attribute(String key, String value) {
        if (observation != null && key != null && value != null) {
            observation.lowCardinalityKeyValue(key, value);
        }
        return this;
    }

    public RuntimeSpan attribute(String key, long value) {
        return attribute(key, Long.toString(value));
    }

    public void error(Throwable error) {
        if (observation != null && error != null) {
            observation.error(error);
        }
    }

    public void stop() {
        if (observation != null) {
            observation.stop();
        }
    }
}
