package com.agentforge.commerce.runtime.memory;

import com.agentforge.commerce.platform.persistence.entity.MemoryFactEntity;
import com.agentforge.commerce.platform.persistence.repo.MemoryFactRepository;
import com.agentforge.commerce.runtime.config.RuntimeProperties;
import com.agentforge.commerce.runtime.contract.Confidence;
import com.agentforge.commerce.runtime.contract.Evidence;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, retailer-scoped semantic/fact memory backed by PostgreSQL with a Redis
 * hot cache.
 *
 * <p>The store is deliberately strict about what may be remembered. A fact is
 * accepted only when it is backed by {@link Evidence} that is either authoritative
 * or carries at least one citation, belongs to the same retailer, and names the
 * same source — so raw, ungrounded model output can never become memory. Writes
 * are idempotent through a {@code (retailer, key, valueHash)} deduplication guard,
 * bounded per retailer with oldest-first eviction, and time-bounded through an
 * explicit TTL. Every read is scoped by {@code retailerId}, giving hard tenant
 * isolation.</p>
 */
@Service
public class SemanticMemoryStore {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticMemoryStore.class);

    private final MemoryFactRepository repository;
    private final FactCache cache;
    private final RuntimeProperties properties;
    private final Clock clock;

    public SemanticMemoryStore(MemoryFactRepository repository, FactCache cache,
                               RuntimeProperties properties, Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Persist a validated, evidence-backed fact. Returns {@code false} (a no-op)
     * when semantic memory is disabled; throws {@link MemoryWriteRejectedException}
     * when the fact is not sufficiently grounded or its provenance does not match
     * the supplied evidence.
     */
    @Transactional
    public boolean write(MemoryFact fact, Evidence supportingEvidence) {
        if (!properties.getMemory().isEnabled()) {
            return false;
        }
        requireGrounded(fact, supportingEvidence);

        Optional<MemoryFactEntity> existing = repository
                .findByRetailerIdAndFactKeyAndValueHash(fact.retailerId(), fact.key(), fact.valueHash());
        if (existing.isPresent()) {
            LOG.debug("Semantic memory dedup hit for retailer/key {}/{}", fact.retailerId(), fact.key());
            return false;
        }

        MemoryFactEntity entity = new MemoryFactEntity();
        entity.setId(UUID.randomUUID());
        entity.setRetailerId(fact.retailerId());
        entity.setFactKey(fact.key());
        entity.setCategory(fact.category());
        entity.setValue(fact.value());
        entity.setSourceId(fact.sourceId());
        entity.setSourceKind(fact.sourceKind());
        entity.setConfidence(fact.confidence().value());
        entity.setValueHash(fact.valueHash());
        entity.setProvenanceRunId(fact.provenanceRunId());
        entity.setObservedAt(fact.observedAt());
        entity.setExpiresAt(fact.expiresAt());
        entity.setCreatedAt(clock.instant());
        repository.save(entity);

        evictOverflow(fact.retailerId());
        cache.evict(fact.retailerId(), fact.key());
        return true;
    }

    /** @return the freshest non-expired facts for {@code retailerId}/{@code key}. */
    @Transactional(readOnly = true)
    public List<MemoryFact> read(String retailerId, String key) {
        Instant now = clock.instant();
        return repository.findByRetailerIdAndFactKeyOrderByObservedAtDesc(retailerId, key).stream()
                .filter(e -> now.isBefore(e.getExpiresAt()) || now.equals(e.getExpiresAt()))
                .map(SemanticMemoryStore::toFact)
                .toList();
    }

    /** @return up to {@code limit} of the most recent non-expired facts for a retailer. */
    @Transactional(readOnly = true)
    public List<MemoryFact> recent(String retailerId, int limit) {
        Instant now = clock.instant();
        return repository.findByRetailerIdOrderByObservedAtDesc(retailerId, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .filter(e -> now.isBefore(e.getExpiresAt()) || now.equals(e.getExpiresAt()))
                .map(SemanticMemoryStore::toFact)
                .toList();
    }

    /** Invalidate every fact for {@code retailerId}/{@code key}. */
    @Transactional
    public int invalidate(String retailerId, String key) {
        int removed = repository.deleteByRetailerIdAndFactKey(retailerId, key);
        cache.evict(retailerId, key);
        return removed;
    }

    /** Purge facts whose validity window has elapsed. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteByExpiresAtBefore(clock.instant());
    }

    private void requireGrounded(MemoryFact fact, Evidence evidence) {
        if (evidence == null) {
            throw new MemoryWriteRejectedException("fact " + fact.key() + " has no supporting evidence");
        }
        if (!evidence.isAuthoritative() && !evidence.isCited()) {
            throw new MemoryWriteRejectedException(
                    "fact " + fact.key() + " is neither authoritative nor cited; refusing to memorise");
        }
        if (!evidence.source().id().equals(fact.sourceId())) {
            throw new MemoryWriteRejectedException(
                    "fact " + fact.key() + " provenance does not match its supporting evidence source");
        }
    }

    private void evictOverflow(String retailerId) {
        long max = properties.getMemory().getMaxFactsPerRetailer();
        long count = repository.countByRetailerId(retailerId);
        if (count <= max) {
            return;
        }
        int overflow = (int) Math.min(count - max, Integer.MAX_VALUE);
        List<UUID> oldest = repository.findOldestIds(retailerId, PageRequest.of(0, overflow));
        repository.deleteAllById(oldest);
    }

    private static MemoryFact toFact(MemoryFactEntity e) {
        return new MemoryFact(
                e.getRetailerId(),
                e.getFactKey(),
                e.getCategory(),
                e.getValue(),
                e.getSourceId(),
                e.getSourceKind(),
                new Confidence(e.getConfidence(), "recalled from durable memory"),
                e.getValueHash(),
                e.getProvenanceRunId(),
                e.getObservedAt(),
                e.getExpiresAt());
    }
}
