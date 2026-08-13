package com.agentforge.commerce.runtime.tools;

import com.agentforge.commerce.runtime.contract.RuntimeLimits;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;

/**
 * Shared helper for serialising a tool payload and measuring its bounded size in
 * bytes. Tools use this so the {@code payloadBytes} they report is a real,
 * enforced measurement rather than a guess.
 */
public final class ToolPayloads {

    private ToolPayloads() {
    }

    /**
     * @return the UTF-8 byte length of {@code payload} serialised as JSON.
     * @throws IllegalStateException if the payload cannot be serialised or exceeds {@code maxBytes}
     */
    public static int measure(ObjectMapper mapper, Object payload, int maxBytes) {
        if (payload == null) {
            return 0;
        }
        try {
            int bytes = mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8).length;
            int ceiling = Math.min(maxBytes, RuntimeLimits.MAX_TOOL_PAYLOAD_BYTES);
            if (bytes > ceiling) {
                throw new IllegalStateException("tool payload exceeds the bounded size of " + ceiling + " bytes");
            }
            return bytes;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("tool payload is not serialisable", e);
        }
    }
}
