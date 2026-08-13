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

### Added — Layer 3 (production operations)

- **Concrete LIVE HTTP adapters** for WMS inventory, competitor pricing and
  customer sentiment behind the Layer 2 contracts, on one hardened client
  (`runtime.tools.live.http`): HTTPS-only, host allowlist (no arbitrary
  fetch/SSRF), bounded request/response sizes, connect/read deadlines, mTLS/OAuth2
  client-credentials where configured, pagination limits, strict schema
  validation, per-adapter resilience4j retry + circuit breaker, correlation-ID
  propagation, evidence provenance/freshness, and secret-safe errors/logging.
  `runtime.tools.mode=LIVE` **fails fast at startup** unless every required
  endpoint/auth setting is valid — no fake production defaults. Deterministic
  WireMock **contract tests** cover success, auth, timeout, oversized payload,
  malformed schema, partial pagination, stale evidence, retry, circuit-open and
  forbidden-host.
- **Kubernetes productionization** via Kustomize `k8s/base` + `k8s/overlays/{dev,
  staging,prod}`: immutable image-digest promotion, External Secrets/workload
  identity examples, a default-deny **NetworkPolicy** covering API ingress,
  management scrape, DNS, PostgreSQL, Redis and HTTPS adapter/provider egress
  (RFC-1918 + link-local/metadata blocked), Pod Security restricted, topology
  spread + anti-affinity, PodDisruptionBudget and HPA (safe under exactly-once
  execution), resource limits, graceful shutdown, a pre-roll Flyway **migration
  Job** (`--app.migrate-only=true`), a ServiceMonitor and PrometheusRule.
- **Observability assets**: SLO/SLI definitions (`docs/SLO.md`), a Grafana
  dashboard and Prometheus alert rules with runbook-linked annotations
  (`observability/`). New metrics (`runtime.run.completed`,
  `runtime.evidence.stale`, `runtime.run.queue.age.seconds`) plus a **MeterFilter**
  guaranteeing run/retailer/tenant/correlation IDs never become metric labels
  (they remain on trace spans only).
- **Deterministic performance and resilience gates** (`perfGate`,
  `resilienceGate`, plus `perfSmoke`/`resilienceSmoke`) wired into `check` and CI:
  API latency budget, concurrent durable-claim/no-duplicates, provider throttling
  recovery, partial tool outage/circuit opening, coordination outage fail-closed,
  deadline honouring and graceful-shutdown drain.
- **Flyway migration validation** (`FlywayMigrationTest`): a deterministic
  ordering/non-destructive guard that runs everywhere, plus a Testcontainers
  empty-DB + idempotent-upgrade check when Docker is present.
- **Secure release workflow** (`.github/workflows/release.yml`): immutable GHCR
  multi-arch images, build provenance attestation, keyless Sigstore/Cosign
  signing, CycloneDX SBOM, vulnerability scan, environment protection/deployment
  gates, promotion by digest and rollback metadata. Every Action is SHA-pinned and
  job permissions are minimized.
- **Operational governance**: Architecture Decision Records (`docs/adr/`) for
  durable orchestration, memory trust, HITL policy, eval strategy, live adapter
  trust boundary and singleton-vs-horizontal execution; and governance docs
  (`docs/governance/`) for data retention/privacy classification, tenant isolation
  verification, incident response, model/provider change management, prompt/eval
  release process and on-call playbooks. The RUNBOOK adds backup/restore, disaster
  recovery (RPO ≤ 5 min / RTO ≤ 30 min), Redis-loss behaviour, rollback and
  forward-fix procedures, and per-alert response steps.

### Changed

- Docker Compose now provisions PostgreSQL and Redis; Kubernetes manifests are
  Kustomize (base + dev/staging/prod overlays) scaling to a rolling-updated
  multi-replica Deployment that sources all connection secrets/config from an
  externally-managed Secret/ConfigMap and External Secrets (no default passwords).
  `.env.example` and the docs were updated accordingly.
- Hardened LLM configuration, transport, retries, response bounds, and
  fail-closed briefing validation.
- Added blocking quality, coverage, dependency, secret, container, SBOM, and
  CodeQL gates.
- Pinned GitHub Actions, build images, and local observability images.
- Hardened Docker, Compose, and Kubernetes runtime defaults.
- Added enterprise governance, security, operations, and architecture
  documentation.
