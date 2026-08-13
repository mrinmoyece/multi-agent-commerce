package com.agentforge.commerce.platform.persistence.repo;

import com.agentforge.commerce.platform.persistence.domain.RunStatus;
import com.agentforge.commerce.platform.persistence.entity.AnalysisRunEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Data access for durable analysis runs. */
@Repository
public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, java.util.UUID> {

    Optional<AnalysisRunEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<AnalysisRunEntity> findFirstByRetailerIdAndBusinessDateAndStatusIn(
            String retailerId, LocalDate businessDate, java.util.Collection<RunStatus> statuses);

    Page<AnalysisRunEntity> findByRetailerId(@Param("retailerId") String retailerId, Pageable pageable);

    /**
     * Creation instant of the oldest run in the given status, or {@code null} when
     * none exist. Backs the {@code runtime.run.queue.age.seconds} gauge that measures
     * how long the oldest unclaimed run has been waiting.
     */
    @Query("select min(r.createdAt) from AnalysisRunEntity r where r.status = :status")
    Instant findOldestCreatedAtByStatus(@Param("status") RunStatus status);
}
