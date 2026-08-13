package com.agentforge.commerce.platform.persistence.repo;

import com.agentforge.commerce.platform.persistence.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for persisted idempotency keys. */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {
}
