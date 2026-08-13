# Contributing

## Setup

Use Java 21. The checked-in Gradle wrapper is the supported build entry point.

```bash
cp .env.example .env
./gradlew check
```

## Ground rules

- Every behavior change includes tests; every bug fix includes a regression test.
- Treat external data and model output as untrusted.
- Do not log credentials, provider responses, or raw exception messages that
  may contain request data.
- Keep LLM calls behind `LlmGateway`; do not bypass its timeout, retry,
  circuit-breaker, or response-bound controls.
- Significant architectural or trust-boundary changes require an ADR under
  `docs/adr/`.
- Update the README, runbook, security model, and limitations when their claims
  change.

## Pull requests

Keep each pull request to one logical change. `./gradlew check` must pass, CI
must be green, and dependency or deployment changes must include rollback
notes. Use squash merge; protected `main` requires an approving code-owner
review, resolved review threads, signed commits, and all required checks.
