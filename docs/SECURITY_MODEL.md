# Security model

## Assets

- LLM provider credentials and quota
- Integrity of daily briefing content
- CI credentials and published container images
- Availability of the scheduled analysis
- Operational telemetry

## Trust boundaries

1. Process to external LLM provider over TLS
2. Specialist output to coordinator synthesis
3. Source repository to GitHub Actions runners
4. Container to Kubernetes node and cluster network
5. Prometheus/Grafana to the management endpoint

## Threats and controls

| Threat | Controls |
|---|---|
| Credential disclosure | Environment/Kubernetes secrets, redacted operational guidance, no secret defaults, Gitleaks and GitHub secret scanning |
| Prompt injection through agent findings | Findings encoded as JSON, explicit untrusted-data instruction, no tool execution, strict response schema |
| Unbounded or malformed model output | Transport timeout, token limits, character limit, empty-response rejection, fail-closed parsing |
| Provider outage or throttling | Bounded retry, circuit breaker, explicit degraded specialist output |
| Dependency/build compromise | Dependency locks/checksums, Trivy, CodeQL, SBOM, pinned Actions and base-image digests |
| Container breakout | Non-root UID, no capabilities, no privilege escalation, seccomp, read-only root filesystem |
| Network pivot | No service-account token and default-deny ingress/egress policy with DNS and HTTPS exceptions |
| Duplicate scheduled work | One replica with `Recreate` strategy until distributed coordination exists |

## Residual risk

The provider remains a trusted processor. Model output is nondeterministic, and
the application does not currently sign briefing content or retain an
append-only audit trail. See [LIMITATIONS.md](LIMITATIONS.md).
