package com.agentforge.commerce.runtime.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An immutable, versioned prompt template.
 *
 * <p>Every template has a stable {@code id}, a {@code version}, and a
 * {@code hash} computed deterministically from its content. The hash is recorded
 * on each durable run and agent result so any briefing can be traced back to the
 * exact prompt bytes that produced it, and prompt drift is detectable.</p>
 *
 * <p>Rendering only substitutes {@code {{name}}} placeholders with values the
 * caller supplies. Structural prompt text is fixed at build time; untrusted data
 * must be wrapped with {@link BoundaryEncoder} before being passed as a value.</p>
 *
 * @param id       stable template identifier (e.g. {@code inventory.analysis})
 * @param version  monotonic version label (e.g. {@code v1})
 * @param hash     hex SHA-256 of the raw template content
 * @param template raw template text with {@code {{placeholder}}} markers
 */
public record PromptTemplate(String id, String version, String hash, String template) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    public PromptTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("prompt id must be provided");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("prompt version must be provided");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("prompt template must be provided");
        }
        hash = (hash == null || hash.isBlank()) ? sha256(template) : hash;
    }

    /** Build a template, deriving the content hash from the raw text. */
    public static PromptTemplate of(String id, String version, String template) {
        return new PromptTemplate(id, version, sha256(template), template);
    }

    /** @return the fully-qualified {@code id@version} reference. */
    public String reference() {
        return id + "@" + version;
    }

    /**
     * Render the template, substituting every {@code {{name}}} with the matching
     * entry from {@code values}. Fails closed if a placeholder has no value, so a
     * missing binding can never silently ship an incomplete prompt.
     */
    public String render(Map<String, String> values) {
        Map<String, String> safe = values == null ? Map.of() : values;
        var matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 64);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = safe.get(key);
            if (value == null) {
                throw new IllegalArgumentException("no value supplied for prompt placeholder '" + key + "'");
            }
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
