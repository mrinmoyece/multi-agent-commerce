package com.agentforge.commerce.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void isStableForSameInputs() {
        assertThat(RequestFingerprint.of("acme", "2024-01-01"))
                .isEqualTo(RequestFingerprint.of("acme", "2024-01-01"));
    }

    @Test
    void differsForDifferentInputs() {
        assertThat(RequestFingerprint.of("acme", "2024-01-01"))
                .isNotEqualTo(RequestFingerprint.of("acme", "2024-01-02"));
    }

    @Test
    void nullSegmentsAreHandled() {
        assertThat(RequestFingerprint.of((String) null)).isNotBlank();
        assertThat(RequestFingerprint.of("a", null)).hasSize(64);
    }

    @Test
    void producesHexSha256() {
        assertThat(RequestFingerprint.of("x")).hasSize(64).matches("[0-9a-f]{64}");
    }
}
