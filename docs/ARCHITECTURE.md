# Architecture

## Execution flow

`AgentCoordinator` runs on a configurable schedule. It dispatches the inventory,
pricing, and sentiment agents on the application task executor. Each specialist
calls `LlmGateway`, which is the only provider boundary and owns transport
timeouts, retry, circuit breaking, empty-response rejection, and response-size
limits. The coordinator serializes specialist findings as untrusted JSON and
requests one structured synthesis.

The synthesis is accepted only when it contains string arrays for
`crossInsights`, `recommendedActions`, and `urgentAlerts`, plus a non-empty
`executiveSummary`. Invalid output fails the run; it is never presented as a
successful briefing.

## Platform foundations (Layer 1)

A durable control plane wraps the coordinator:

- **REST API** (`/api/v1`, package `platform.api`) creates runs, returns run
  status/results, lists runs with bounded pagination, and records approval
  decisions. A correlation-id filter tags every request; errors are RFC 9457
  Problem Details.
- **Security** (`platform.security`) is an OAuth2/OIDC resource server. JWTs are
  validated for signature, issuer, expiry, and audience; scopes map to
  `SCOPE_*` and the roles claim to `ROLE_*` authorities enforced with method
  security. There is no static bypass.
- **Persistence** (`platform.persistence`) stores analysis runs, per-agent
  results, approval records, audit events, and idempotency keys in PostgreSQL
  via Flyway migrations and Spring Data JPA. Runs and approvals carry explicit
  state machines and an optimistic-locking `version`.
- **Coordination** (`platform.coordination`) provides a Redis-backed distributed
  lock, rate limiter, and run-status cache, with in-memory stand-ins for
  `dev`/`test`. `AnalysisRunService` owns the transactional aggregate;
  `DurableRunExecutor` runs each durable run exactly once across replicas by
  claiming a cluster lock and guarding the `PENDING → RUNNING` transition with
  optimistic locking, bounded by a timeout on a dedicated executor.
- **Scheduling** creates one durable run keyed by retailer + business date; the
  unique idempotency key means every replica firing the cron converges on a
  single run that is then executed once.

Credentials and raw provider payloads are never persisted — only synthesised,
non-sensitive briefing fields and audit metadata.

## Invariants

- Specialist agents remain stateless.
- External and model-generated content is untrusted.
- Provider credentials are supplied at runtime and never persisted.
- A failed specialist is explicit in the briefing input; raw exception text is
  not exposed.
- Durable run state lives in PostgreSQL; cross-replica coordination lives in
  Redis. Scheduled work is created once and executed exactly once, so the
  Deployment scales horizontally.
- Management endpoints are separate from application traffic.

## Deployment

The image is multi-stage and runs as UID `10001`. Kubernetes drops all
capabilities, disables privilege escalation and service-account token mounting,
uses a read-only root filesystem, and permits only DNS, in-cluster
PostgreSQL/Redis, and public HTTPS egress. Connection secrets and config are
externalised to a Secret and ConfigMap. Release automation must promote images
by digest.
