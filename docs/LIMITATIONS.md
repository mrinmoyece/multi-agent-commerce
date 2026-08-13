# Limitations

- Inventory data is deterministic and synthetic.
- Pricing and sentiment findings are model-generated narratives without live
  market, review, support, or returns feeds. They are not decision-grade.
- The REST API, OAuth2 resource server, PostgreSQL persistence, Redis
  coordination, durable run state, and append-only audit trail are in place, but
  agent *inputs* remain synthetic/LLM-extrapolated as above.
- Provider calls are bounded but cannot make a nondeterministic model fully
  reliable or factual.
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
