# ADR 0006: Singleton vs horizontal execution

- **Status:** Accepted
- **Date:** Layer 3
- **Deciders:** Platform team

## Context

The platform runs a scheduler that must create exactly one run per (retailer,
business date) and execute it exactly once. The naïve way to guarantee that is to
run a single instance. But a single instance is a single point of failure, caps
availability, and makes zero-downtime deploys impossible. We must decide between a
singleton and a horizontally scaled fleet.

## Options considered

1. **Singleton (one replica / leader election only).** Trivially exactly-once, but
   no HA, slow failover, and rolling deploys drop the scheduler. Rejected for a
   production platform.
2. **Horizontal fleet with no coordination.** Highly available but duplicates
   work and violates exactly-once. Rejected.
3. **Horizontal fleet with durable coordination.** Multiple replicas, each safe to
   claim work, with correctness enforced by data, not by cardinality.

## Decision

Run **multiple replicas**. Correctness does not depend on how many replicas exist:
the unique idempotency key (ADR 0001) makes run creation idempotent, and a Redis
`DistributedLock` plus optimistic status transition makes claiming exactly-once.
If two replicas race, exactly one wins the lock and transition; the other no-ops.
If Redis is unavailable, claiming **fails closed** (the run stays PENDING and is
picked up later) rather than risking a duplicate. HPA, PodDisruptionBudget,
topology spread and rolling updates are therefore all safe.

## Consequences

- Highly available and zero-downtime deployable while preserving exactly-once.
- Scaling is a capacity decision, never a correctness risk — verified by the
  `perfGate` concurrent-claim-no-duplicates and `resilienceGate`
  coordination-outage-fails-closed tests.
- Depends on Redis for liveness of claiming (not for correctness of duplicates);
  Redis loss degrades throughput, not safety (see RUNBOOK disaster recovery).
- Every migration must be rolling-safe (expand/contract), since old and new
  replicas run concurrently during deploys.
