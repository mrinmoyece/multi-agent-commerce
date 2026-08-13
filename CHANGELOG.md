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
