# ADR 0001: Durable orchestration on PostgreSQL

- **Status:** Accepted
- **Date:** Layer 1
- **Deciders:** Platform team

## Context

Scheduled and API-triggered analysis runs must survive process restarts, deploys
and crashes without losing work or double-executing. Multiple API replicas run
concurrently. We need exactly-once execution per (retailer, business date) with an
auditable state machine, and the ability to pause a run for human approval and
resume it later — possibly on a different replica, minutes or hours later.

## Options considered

1. **In-memory orchestration** (e.g. a workflow library holding state in the JVM).
   Simple, but state is lost on restart and cannot span replicas or long approval
   pauses. Rejected.
2. **External workflow engine** (Temporal, Camunda). Powerful, but adds a heavy
   dependency, a second data store to operate, and operational surface area
   disproportionate to a single run-per-retailer-per-day workload.
3. **Durable state in PostgreSQL** with an idempotency key, an explicit status
   enum, optimistic locking, and a Redis distributed lock for claim arbitration.
   Reuses the database we already operate for the system of record.

## Decision

Persist every run as an `analysis_run` row with a unique idempotency key and an
explicit status (`PENDING → RUNNING → {SUCCEEDED, FAILED, AWAITING_APPROVAL}` …).
A replica claims a run by acquiring a Redis lock (`DistributedLock`) and
transitioning status under optimistic locking (`beginExecution`). Approvals are
persisted; a paused run is resumed by any replica that re-claims it. All state
transitions append to an immutable audit trail.

## Consequences

- **Exactly-once** is guaranteed by the unique idempotency key plus lock-guarded
  transition, not by running a single instance — so the service scales
  horizontally (see ADR 0006).
- No extra infrastructure beyond PostgreSQL + Redis, both already required.
- Recovery is automatic: an interrupted run is re-claimable; nothing is lost.
- The database is the throughput ceiling for orchestration; acceptable for this
  workload and observable via the `queue-age` SLI.
- Migrations must follow expand/contract so a rolling fleet stays compatible
  (see RUNBOOK).
