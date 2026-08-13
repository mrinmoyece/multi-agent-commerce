# Limitations

- **Default grounding is deterministic fixture data, not live feeds.** In the
  default `runtime.tools.mode=FIXTURE` mode the inventory, pricing and sentiment
  tools return committed deterministic fixtures. These are explicitly labelled
  non-authoritative (`SourceKind.FIXTURE`) and surfaced as `DEGRADED`, so the
  system never claims live data — but they are not decision-grade. Live adapter
  contracts exist (`runtime.tools.mode=LIVE`); the concrete production adapters
  and their credentials/endpoints must be supplied before live use.
- Recommendations now trace to cited evidence, but the underlying evidence is
  only as good as the configured source; with fixtures active it is illustrative.
- Provider calls are bounded and budgeted, but a nondeterministic model cannot be
  made fully reliable or factual; strict schema validation and fail-closed
  degradation reduce, but do not eliminate, low-quality output.
- The offline evaluation gate is deterministic by design: it drives the engine
  with a scripted, network-free provider, so it validates runtime wiring,
  grounding, fencing, budgets, schema handling, risk gating and tenant isolation
  — not the quality of a real model's generations.
- Semantic memory enforces provenance, dedup, TTL and tenant scoping, but
  eviction and expiry are best-effort at read/write time rather than a
  continuously running reaper.
- The REST API, OAuth2 resource server, PostgreSQL persistence, Redis
  coordination, durable run state, and append-only audit trail are in place.
- Testcontainers-based integration tests (real PostgreSQL) are provided but are
  automatically skipped when a Docker daemon is unavailable; the mandatory suite
  runs deterministically on H2 (PostgreSQL mode) with in-memory coordination, so
  the Flyway migration is fully exercised only where Docker is present (e.g. CI).
- Rate limiting and the run-status cache use a fixed window and best-effort
  semantics; they reduce, but do not perfectly eliminate, races under extreme
  contention.
- The network policy allows public HTTPS because provider endpoints vary. A
  production platform should narrow egress to organization-approved proxies or
  provider ranges.
- Kubernetes secrets are references, not a complete secret-management system.
  Production deployments should use an external secret manager and workload
  identity where supported.
