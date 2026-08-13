# Security model

## Assets

- LLM provider credentials and quota
- Upstream adapter credentials (WMS/pricing/sentiment: mTLS keys, OAuth2 client secrets)
- Integrity of daily briefing content and grounding evidence
- Durable run state, approvals, and the audit trail in PostgreSQL
- Database and Redis credentials
- CI credentials and published container images
- Availability of the scheduled analysis
- Operational telemetry

## Trust boundaries

1. API client to resource server (bearer JWT over TLS)
2. Process to external LLM provider over TLS
3. Runtime to external systems of record via LIVE adapters (WMS/pricing/sentiment) — hardened HTTP substrate, see [ADR 0005](adr/0005-live-adapter-trust-boundary.md)
4. Specialist output to coordinator synthesis
5. Application to PostgreSQL and Redis
6. Source repository to GitHub Actions runners
7. Container to Kubernetes node and cluster network
8. Prometheus/Grafana to the management endpoint

## Threats and controls

| Threat | Controls |
|---|---|
| Credential disclosure | Environment/Kubernetes secrets, redacted operational guidance, no secret defaults, Gitleaks and GitHub secret scanning |
| Unauthenticated or over-scoped API access | OAuth2/OIDC resource server with issuer + audience validation, per-operation scope/role method security, stateless sessions, problem-detail auth failures, no static bypass |
| Idempotency-key or replay abuse | Durable unique-key store as source of truth, request-fingerprint mismatch detection, Redis lock to reduce races, fixed-window rate limiting |
| Prompt injection through agent findings | Findings encoded as JSON, explicit untrusted-data instruction, no tool execution, strict response schema; Layer 2 adds a per-call fenced `BoundaryEncoder` block and evaluated prompt-injection golden scenario |
| Fabricated or ungrounded recommendations | Grounded tool contracts with `SourceIdentity`/`Evidence`/`Citation`; fixture/simulated sources auto-degraded and never presented as live; evidence-or-nothing memory writes; groundedness/citation-precision eval metrics |
| Runaway provider cost or looping | Per-run token/request/latency/cost budgets enforced as a hard stop before provider calls, plus runtime brakes (max tool calls, max agent rounds, propagated deadline, bounded concurrency) |
| Unapproved high-impact action | Configurable human-in-the-loop `RiskPolicy` pauses pricing changes, emergency orders and low-confidence recommendations in `AWAITING_APPROVAL`; deterministic audited resume |
| Cross-tenant memory leakage | Retailer-scoped semantic memory with provenance-matched, deduplicated, TTL-bounded writes and reads scoped by `retailerId`; tenant-isolation golden scenario |
| Sensitive data in telemetry | OpenTelemetry records only non-sensitive metrics/attributes (tokens, cost, latency, status, degraded modes) — never prompts, payloads or credentials |
| Unbounded or malformed model output | Transport timeout, token limits, character limit, empty-response rejection, fail-closed parsing |
| Malicious/compromised upstream via LIVE adapters (SSRF, oversized payload, secret leak) | HTTPS-only, host allowlist (no arbitrary fetch), bounded request/response sizes, connect/read deadlines, mTLS/OAuth2 where configured, pagination limits, schema validation, per-adapter retry+circuit isolation, correlation IDs, provenance/freshness stamping, secret-safe errors; LIVE fails fast on misconfig; NetworkPolicy blocks RFC-1918/link-local/metadata egress. See [ADR 0005](adr/0005-live-adapter-trust-boundary.md) |
| Provider outage or throttling | Bounded retry, circuit breaker, explicit degraded specialist output |
| Dependency/build compromise | Dependency locks/checksums, Trivy, CodeQL, CycloneDX SBOM attached to release, keyless Sigstore/Cosign signing + build provenance attestation, pinned Actions (incl. transitive) and base-image digests, promotion by immutable digest |
| Container breakout | Non-root UID, no capabilities, no privilege escalation, seccomp, read-only root filesystem, Pod Security restricted |
| Network pivot | No service-account token, default-deny ingress/egress NetworkPolicy scoped to DNS, in-cluster datastores, management scrape, and HTTPS egress with private/metadata ranges denied |
| High-cardinality identifier leakage into metrics | Run/retailer/tenant/correlation IDs kept on trace spans only; `MetricsConfig` MeterFilter strips them from meters so they can never become Prometheus labels |
| Duplicate scheduled work | One durable run per retailer/date via a unique idempotency key, executed exactly once across replicas by a Redis lock plus optimistic-locking state transition |

## Residual risk

The provider remains a trusted processor. Model output is nondeterministic. An
append-only audit trail is now retained, but briefing content is not
cryptographically signed. See [LIMITATIONS.md](LIMITATIONS.md).
