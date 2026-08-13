package com.agentforge.commerce.runtime.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Small deterministic hashing helper used to fingerprint observed values for
 * citations and drift detection. Centralised so every content hash across the
 * runtime is computed the same way.
 */
public final class Hashes {

    private Hashes() {
    }

    /** @return the hex-encoded SHA-256 of {@code content} (empty string for {@code null}). */
    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    /** @return the first {@code length} chars of the hex SHA-256 of {@code content}. */
    public static String shortSha256(String content, int length) {
        String full = sha256Hex(content);
        return full.length() <= length ? full : full.substring(0, length);
    }
}
