package com.agentforge.commerce.runtime.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.SourceKind;
import com.agentforge.commerce.runtime.memory.cache.InMemoryFactCache;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for run-local working memory, the durable fact value type and the
 * in-memory hot cache.
 */
@DisplayName("Runtime memory primitives")
class RuntimeMemoryTest {

    private static final Instant NOW = Instant.parse("2026-06-19T09:00:00Z");

    @Test
    @DisplayName("Working memory stores and retrieves grounded entries")
    void workingMemory() {
        WorkingMemory memory = new WorkingMemory();
        memory.put("k", "v", null);
        assertThat(memory.get("k")).isPresent();
        assertThat(memory.get("k").orElseThrow().value()).isEqualTo("v");
        assertThat(memory.get("absent")).isEmpty();
        assertThat(memory.size()).isEqualTo(1);
        assertThatThrownBy(() -> memory.put(" ", "v", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memory.put("k", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MemoryFact enforces value bounds and expiry")
    void memoryFact() {
        MemoryFact fact = new MemoryFact("R1", "inventory.critical", null, "3", "src",
                SourceKind.FIXTURE, new Confidence(0.9, "r"), "hash", "run-1", NOW, NOW.plusSeconds(60));
        assertThat(fact.category()).isEqualTo("general");
        assertThat(fact.isExpiredAt(NOW.plusSeconds(120))).isTrue();
        assertThat(fact.isExpiredAt(NOW)).isFalse();
        assertThatThrownBy(() -> new MemoryFact("R1", "k", "c", " ", "src", SourceKind.LIVE,
                null, "h", "run", NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        String huge = "x".repeat(MemoryFact.MAX_VALUE_CHARS + 1);
        assertThatThrownBy(() -> new MemoryFact("R1", "k", "c", huge, "src", SourceKind.LIVE,
                null, "h", "run", NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("In-memory cache honours TTL and eviction")
    void cache() {
        InMemoryFactCache cache = new InMemoryFactCache();
        cache.put("R1", "k", "snapshot", Duration.ofMinutes(5));
        assertThat(cache.get("R1", "k")).contains("snapshot");
        cache.evict("R1", "k");
        assertThat(cache.get("R1", "k")).isEmpty();
        cache.put("R1", "k2", "snap", Duration.ofNanos(1));
        assertThat(cache.get("R1", "k2")).isEmpty();
        assertThat(cache.get("R1", "missing")).isEmpty();
    }
}
