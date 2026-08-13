package com.agentforge.commerce.platform.persistence.repo;

import com.agentforge.commerce.platform.persistence.entity.MemoryFactEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Data access for durable semantic-memory facts. Every finder is scoped by
 * {@code retailerId} so tenant isolation is enforced at the query boundary.
 */
@Repository
public interface MemoryFactRepository extends JpaRepository<MemoryFactEntity, UUID> {

    Optional<MemoryFactEntity> findByRetailerIdAndFactKeyAndValueHash(
            String retailerId, String factKey, String valueHash);

    List<MemoryFactEntity> findByRetailerIdAndFactKeyOrderByObservedAtDesc(
            String retailerId, String factKey);

    List<MemoryFactEntity> findByRetailerIdOrderByObservedAtDesc(String retailerId, Pageable pageable);

    long countByRetailerId(String retailerId);

    @Modifying
    @Query("delete from MemoryFactEntity f where f.retailerId = :retailerId and f.factKey = :factKey")
    int deleteByRetailerIdAndFactKey(@Param("retailerId") String retailerId, @Param("factKey") String factKey);

    @Modifying
    @Query("delete from MemoryFactEntity f where f.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);

    @Query("select f.id from MemoryFactEntity f where f.retailerId = :retailerId order by f.observedAt asc")
    List<UUID> findOldestIds(@Param("retailerId") String retailerId, Pageable pageable);
}
