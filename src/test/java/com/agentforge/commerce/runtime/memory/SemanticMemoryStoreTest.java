package com.agentforge.commerce.runtime.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.agentforge.commerce.platform.persistence.entity.MemoryFactEntity;
import com.agentforge.commerce.platform.persistence.repo.MemoryFactRepository;
import com.agentforge.commerce.runtime.config.RuntimeProperties;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Evidence;
import com.agentforge.commerce.runtime.contract.Freshness;
import com.agentforge.commerce.runtime.contract.SourceIdentity;
import com.agentforge.commerce.runtime.contract.SourceKind;
import com.agentforge.commerce.runtime.memory.cache.InMemoryFactCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the durable, tenant-scoped semantic memory store: grounding
 * enforcement, deduplication, provenance matching and invalidation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Semantic memory store")
class SemanticMemoryStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-19T09:00:00Z");

    @Mock
    MemoryFactRepository repository;

    private final RuntimeProperties properties = new RuntimeProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private SemanticMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new SemanticMemoryStore(repository, new InMemoryFactCache(), properties, clock);
    }

    private SourceIdentity live() {
        return new SourceIdentity("wms", "WMS", SourceKind.LIVE, "system");
    }

    private Evidence evidence(SourceIdentity source, boolean cited) {
        List<com.agentforge.commerce.runtime.contract.Citation> citations = cited
                ? List.of(new com.agentforge.commerce.runtime.contract.Citation(source.id(), "loc", "s", "h", NOW))
                : List.of();
        return new Evidence("e", source, citations, "s", Freshness.of(NOW, Duration.ofHours(1)),
                new Confidence(0.9, "r"));
    }

    private MemoryFact fact(String sourceId, SourceKind kind) {
        return new MemoryFact("R1", "inventory.critical", "inventory", "3", sourceId, kind,
                new Confidence(0.9, "r"), "hash", "run-1", NOW, NOW.plusSeconds(3600));
    }

    @Test
    @DisplayName("Grounded, deduplicated fact is persisted")
    void writeGrounded() {
        given(repository.findByRetailerIdAndFactKeyAndValueHash(any(), any(), any())).willReturn(Optional.empty());
        given(repository.countByRetailerId("R1")).willReturn(1L);
        boolean written = store.write(fact("wms", SourceKind.LIVE), evidence(live(), true));
        assertThat(written).isTrue();
        verify(repository).save(any(MemoryFactEntity.class));
    }

    @Test
    @DisplayName("Write is a no-op when memory is disabled")
    void disabled() {
        properties.getMemory().setEnabled(false);
        assertThat(store.write(fact("wms", SourceKind.LIVE), evidence(live(), true))).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Ungrounded fact is rejected")
    void ungrounded() {
        SourceIdentity fixture = SourceIdentity.fixture("wms", "WMS");
        assertThatThrownBy(() -> store.write(fact("wms", SourceKind.FIXTURE), evidence(fixture, false)))
                .isInstanceOf(MemoryWriteRejectedException.class);
        assertThatThrownBy(() -> store.write(fact("wms", SourceKind.FIXTURE), null))
                .isInstanceOf(MemoryWriteRejectedException.class);
    }

    @Test
    @DisplayName("Provenance mismatch between fact and evidence is rejected")
    void provenanceMismatch() {
        assertThatThrownBy(() -> store.write(fact("other-source", SourceKind.LIVE), evidence(live(), true)))
                .isInstanceOf(MemoryWriteRejectedException.class);
    }

    @Test
    @DisplayName("Duplicate value hash is skipped")
    void dedup() {
        given(repository.findByRetailerIdAndFactKeyAndValueHash(any(), any(), any()))
                .willReturn(Optional.of(new MemoryFactEntity()));
        assertThat(store.write(fact("wms", SourceKind.LIVE), evidence(live(), true))).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Overflow eviction removes the oldest facts")
    void overflowEviction() {
        properties.getMemory().setMaxFactsPerRetailer(1);
        given(repository.findByRetailerIdAndFactKeyAndValueHash(any(), any(), any())).willReturn(Optional.empty());
        given(repository.countByRetailerId("R1")).willReturn(3L);
        given(repository.findOldestIds(any(), any())).willReturn(List.of(java.util.UUID.randomUUID()));
        store.write(fact("wms", SourceKind.LIVE), evidence(live(), true));
        verify(repository).deleteAllById(any());
    }

    @Test
    @DisplayName("Read filters expired facts and is tenant scoped")
    void read() {
        MemoryFactEntity fresh = entity(NOW.plusSeconds(60));
        MemoryFactEntity expired = entity(NOW.minusSeconds(60));
        given(repository.findByRetailerIdAndFactKeyOrderByObservedAtDesc("R1", "inventory.critical"))
                .willReturn(List.of(fresh, expired));
        List<MemoryFact> facts = store.read("R1", "inventory.critical");
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).retailerId()).isEqualTo("R1");
    }

    @Test
    @DisplayName("Invalidate deletes and evicts")
    void invalidate() {
        given(repository.deleteByRetailerIdAndFactKey("R1", "inventory.critical")).willReturn(2);
        assertThat(store.invalidate("R1", "inventory.critical")).isEqualTo(2);
    }

    @Test
    @DisplayName("Purge removes expired facts")
    void purge() {
        given(repository.deleteByExpiresAtBefore(NOW)).willReturn(4);
        assertThat(store.purgeExpired()).isEqualTo(4);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteByExpiresAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(NOW);
    }

    private MemoryFactEntity entity(Instant expiresAt) {
        MemoryFactEntity entity = new MemoryFactEntity();
        entity.setRetailerId("R1");
        entity.setFactKey("inventory.critical");
        entity.setCategory("inventory");
        entity.setValue("3");
        entity.setSourceId("wms");
        entity.setSourceKind(SourceKind.LIVE);
        entity.setConfidence(0.9);
        entity.setValueHash("hash");
        entity.setProvenanceRunId("run-1");
        entity.setObservedAt(NOW);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(NOW);
        return entity;
    }
}
