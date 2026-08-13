# Changelog

## Unreleased

### Added

- **Versioned REST API** (`/api/v1`) to create analysis runs, fetch run
  status/results, list runs with bounded pagination, list a run's approvals,
  and approve/reject approval requests. Errors are RFC 9457 Problem Details
  carrying a correlation id; no internal detail is leaked.
- **OAuth2/OIDC resource-server security** — JWT validation with issuer and
  audience checks, scope/role authorities, method-level authorization, stateless
  sessions, and problem-detail auth failures. No static bypass in production;
  tests use mocked JWTs under a dedicated profile.
- **PostgreSQL persistence** via Flyway migrations and Spring Data JPA for
  analysis runs, per-agent results, approval records, audit events, and
  idempotency keys — with explicit state machines and optimistic locking. No
  credentials or raw provider payloads are stored.
- **Redis coordination** for distributed locks, exactly-once run execution,
  short-lived run-status cache, fixed-window rate limiting, and idempotency
  coordination. Startup fails fast when Redis is required but unreachable;
  in-memory stand-ins are permitted only under the `dev`/`test` profiles.
- **Durable, once-only scheduled coordinator** — the daily job creates one
  durable run keyed by retailer + date, executed exactly once across replicas
  with bounded executors and timeouts. Existing agent behaviour and fail-closed
  synthesis are preserved.
- Transactional service boundaries, request validation, correlation-id
  propagation, an append-only audit trail, and unit + integration tests
  (deterministic; no Docker required for the mandatory suite).

### Added — Layer 2 (agent runtime & offline evaluation)

- **Typed, provider-neutral agent runtime** (`runtime.*`). Every specialist and
  the coordinator now return an `AgentResultEnvelope` carrying explicit status
  (OK/DEGRADED/…), grounded `Evidence` with citations, calibrated `Confidence`,
  `Freshness`, `SourceIdentity`, token/cost `TokenUsage`, the versioned prompt
  id + hash, and bounded payloads. Azure OpenAI remains the production provider.
- **Grounded tool contracts** for inventory, pricing and sentiment, with
  deterministic committed **fixture adapters** (default) and **live adapter
  contracts** (`runtime.tools.mode=LIVE`). Fixture/simulated sources are always
  labelled non-authoritative and surfaced as degraded — the system never claims
  live data when a fixture is active, and every recommendation traces to cited
  evidence.
- **Versioned prompt registry** with content-hash pinning, an **injection-
  resistant boundary encoder** that fences untrusted tool data, and **strict
  JSON-schema validation** for every specialist and coordinator output (invalid
  output fails closed to a deterministic degraded payload).
- **Per-run budgets** (tokens, requests, latency, estimated cost) enforced as
  hard stops *before* provider calls, with actual-usage accounting and persisted
  budget/audit events; plus **model/provider allowlists** and per-agent model
  selection.
- **Durable, retailer-scoped semantic memory** backed by PostgreSQL with a Redis
  hot cache. Only validated, evidence-backed facts are written, with provenance,
  TTL, invalidation, deduplication and hard tenant isolation; raw model text is
  never memorised.
- **Human-in-the-loop risk policy** integrated with Layer 1 approvals:
  configurable rules pause pricing changes, emergency orders and low-confidence
  recommendations in `AWAITING_APPROVAL`, resuming deterministically after every
  audited approval/rejection.
- **OpenTelemetry tracing/metrics** correlated across run → agent → tool → LLM,
  recording tokens, cost, latency, retries, degraded modes, budget stops and
  approval waits — never prompts, credentials or sensitive payloads.
- **Runtime brakes**: max tool calls, max agent rounds, deadline propagation,
  cancellation, bounded concurrency, no arbitrary code execution, no
  unrestricted URL fetches.
- **Offline deterministic evaluation framework** (`eval.*`) with eight golden
  scenarios (normal, partial-failure, prompt-injection, stale/contradictory
  evidence, budget-exhaustion, malformed-output, approval-required,
  tenant-isolation). It scores groundedness, citation precision, completeness,
  schema validity, safety-policy adherence and budget adherence, writes a
  byte-stable report, and enforces a committed baseline with drift detection via
  `./gradlew evalSmoke` / `evalGate` (wired into `check` and a CI merge gate).

### Changed

- Docker Compose now provisions PostgreSQL and Redis; Kubernetes manifests scale
  to a rolling-updated multi-replica Deployment and source all connection
  secrets/config from an externally-managed Secret and ConfigMap (no default
  passwords). `.env.example` and the docs were updated accordingly.
- Hardened LLM configuration, transport, retries, response bounds, and
  fail-closed briefing validation.
- Added blocking quality, coverage, dependency, secret, container, SBOM, and
  CodeQL gates.
- Pinned GitHub Actions, build images, and local observability images.
- Hardened Docker, Compose, and Kubernetes runtime defaults.
- Added enterprise governance, security, operations, and architecture
  documentation.
