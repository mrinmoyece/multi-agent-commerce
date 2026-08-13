# Limitations

- **Default grounding is deterministic fixture data, not live feeds.** In the
  default `runtime.tools.mode=FIXTURE` mode the inventory, pricing and sentiment
  tools return committed deterministic fixtures. These are explicitly labelled
  non-authoritative (`SourceKind.FIXTURE`) and surfaced as `DEGRADED`, so the
  system never claims live data — but they are not decision-grade. Concrete
  production LIVE adapters exist (`runtime.tools.mode=LIVE`) for WMS inventory,
  competitor pricing and customer sentiment; LIVE mode **fails fast** at startup
  unless every required endpoint/auth setting is present and valid — there are no
  fake production defaults, so live use still requires supplying real
  credentials/endpoints.
- Recommendations now trace to cited evidence, but the underlying evidence is
  only as good as the configured source; with fixtures active it is illustrative.
- Provider calls are bounded and budgeted, but a nondeterministic model cannot be
  made fully reliable or factual; strict schema validation and fail-closed
  degradation reduce, but do not eliminate, low-quality output.
- The offline evaluation, performance and resilience gates are deterministic by
  design: they drive the engine and coordination primitives with scripted,
  network-free fakes, so they validate runtime wiring, grounding, fencing,
  budgets, schema handling, risk gating, tenant isolation, exactly-once claiming
  and fail-closed behaviour — not the quality of a real model's generations, nor
  production throughput on real hardware.
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
- The NetworkPolicy default-denies ingress and egress and allows only the
  required flows: API ingress from the ingress controller, management scrape from
  the monitoring namespace, DNS, PostgreSQL, Redis, and HTTPS egress with RFC-1918
  and link-local/metadata ranges blocked (SSRF guard). Because Azure OpenAI and
  external adapter endpoints vary by tenant, public HTTPS egress is permitted to
  non-private ranges; hardening further to an approved egress proxy or explicit
  provider CIDRs is recommended where the environment allows it.
- Kubernetes secrets are referenced via External Secrets/workload-identity
  examples, not a complete secret-management system. Production deployments should
  supply a real secret manager and workload identity; the manifests ship no
  default passwords and reference-only secrets.
