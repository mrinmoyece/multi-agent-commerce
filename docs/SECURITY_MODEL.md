# Security model

## Assets

- LLM provider credentials and quota
- Integrity of daily briefing content
- Durable run state, approvals, and the audit trail in PostgreSQL
- Database and Redis credentials
- CI credentials and published container images
- Availability of the scheduled analysis
- Operational telemetry

## Trust boundaries

1. API client to resource server (bearer JWT over TLS)
2. Process to external LLM provider over TLS
3. Specialist output to coordinator synthesis
4. Application to PostgreSQL and Redis
5. Source repository to GitHub Actions runners
6. Container to Kubernetes node and cluster network
7. Prometheus/Grafana to the management endpoint

## Threats and controls

| Threat | Controls |
|---|---|
| Credential disclosure | Environment/Kubernetes secrets, redacted operational guidance, no secret defaults, Gitleaks and GitHub secret scanning |
| Unauthenticated or over-scoped API access | OAuth2/OIDC resource server with issuer + audience validation, per-operation scope/role method security, stateless sessions, problem-detail auth failures, no static bypass |
| Idempotency-key or replay abuse | Durable unique-key store as source of truth, request-fingerprint mismatch detection, Redis lock to reduce races, fixed-window rate limiting |
| Prompt injection through agent findings | Findings encoded as JSON, explicit untrusted-data instruction, no tool execution, strict response schema |
| Unbounded or malformed model output | Transport timeout, token limits, character limit, empty-response rejection, fail-closed parsing |
| Provider outage or throttling | Bounded retry, circuit breaker, explicit degraded specialist output |
| Dependency/build compromise | Dependency locks/checksums, Trivy, CodeQL, SBOM, pinned Actions and base-image digests |
| Container breakout | Non-root UID, no capabilities, no privilege escalation, seccomp, read-only root filesystem |
| Network pivot | No service-account token and default-deny ingress/egress policy with DNS, in-cluster datastore, and HTTPS exceptions |
| Duplicate scheduled work | One durable run per retailer/date via a unique idempotency key, executed exactly once across replicas by a Redis lock plus optimistic-locking state transition |

## Residual risk

The provider remains a trusted processor. Model output is nondeterministic. An
append-only audit trail is now retained, but briefing content is not
cryptographically signed. See [LIMITATIONS.md](LIMITATIONS.md).
