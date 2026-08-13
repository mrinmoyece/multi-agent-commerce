package com.agentforge.commerce.platform.persistence.repo;

import com.agentforge.commerce.platform.persistence.entity.AgentResultEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for per-agent results. */
@Repository
public interface AgentResultRepository extends JpaRepository<AgentResultEntity, UUID> {

    List<AgentResultEntity> findByRunIdOrderByAgentName(UUID runId);
}
