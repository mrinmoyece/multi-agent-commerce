# Security policy

## Reporting a vulnerability

Use GitHub **Security → Report a vulnerability** to report security issues
privately. Do not disclose exploit details in a public issue. Include the
affected commit, impact, prerequisites, a minimal reproduction, and suggested
mitigation. Expect acknowledgement within 72 hours.

## Supported versions

Security fixes are applied to the latest release and the `main` branch. Older
releases are unsupported.

## Scope

In scope: secret exposure, prompt injection that changes coordinator behavior,
unsafe LLM output handling, dependency or build-chain compromise, container
escape, and Kubernetes privilege or network-boundary bypass.

The synthetic datasets are not production data. Reports that only show
fabricated output from the documented demo-only pricing or sentiment agents are
out of scope unless they cross a documented trust boundary.

## Security architecture

The service has no public controller or database. Its primary boundaries are
the scheduled process, the external LLM provider, CI, the container, and the
Kubernetes network. Credentials are environment-only and are never committed.
LLM calls have transport timeouts, retries, circuit breaking, response-size
limits, and strict coordinator response validation.

See [docs/SECURITY_MODEL.md](docs/SECURITY_MODEL.md) for the threat model and
[docs/LIMITATIONS.md](docs/LIMITATIONS.md) for known gaps.
