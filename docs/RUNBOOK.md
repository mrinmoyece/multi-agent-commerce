# Operations runbook

## Configuration

Required:

- `LLM_PROVIDER`: `github-models` or `azure-openai`
- `LLM_API_KEY`: provider credential
- `AZURE_OPENAI_ENDPOINT`: required for Azure OpenAI
- `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`:
  PostgreSQL connection (no defaults in the base profile)
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` and
  `PLATFORM_SECURITY_AUDIENCES`: OIDC issuer and accepted audience(s); the app
  refuses to start without a decoder in non-test profiles

Coordination:

- `PLATFORM_REDIS_ENABLED` (default `true`): when `true`, `SPRING_DATA_REDIS_HOST`
  (and optionally `_PORT`, `_PASSWORD`, `_SSL`) must point at a reachable Redis
  or startup fails fast. The `dev`/`test` profiles may set it `false` to use
  in-memory single-JVM coordination.

Optional controls include `LLM_TIMEOUT`, `LLM_MAX_RESPONSE_CHARACTERS`,
`AZURE_CHAT_DEPLOYMENT`, `RETAILER_ID`, `BRIEFING_CRON`, `MANAGEMENT_PORT`, and
the `PLATFORM_*` tunables (lock TTL, status-cache TTL, orchestration timeout/pool,
rate-limit window/limit).

### Agent runtime (Layer 2)

The runtime is configured under the `runtime.*` prefix (`RuntimeProperties`):

- `RUNTIME_TOOLS_MODE` (`FIXTURE` default, or `LIVE`): grounding source.
  `FIXTURE` serves deterministic committed data labelled non-authoritative; use
  `LIVE` only once production tool adapters and their credentials are configured.
- `RUNTIME_BUDGET_*`: per-run ceilings — `MAXTOKENS` (120000), `MAXREQUESTS`
  (24), `MAXLATENCY` (3m), `MAXCOSTMICROS` (500000) — plus the
  `PROMPTMICROSPERTHOUSAND` / `COMPLETIONMICROSPERTHOUSAND` cost model.
- `RUNTIME_MODELS_*`: `ALLOWEDPROVIDERS`, `ALLOWEDMODELS`, `DEFAULTMODEL` and
  optional `PERAGENT` overrides. A run refuses to start on a disallowed provider.
- `RUNTIME_BRAKES_*`: `MAXTOOLCALLS` (32), `MAXAGENTROUNDS` (4), `MAXCONCURRENCY`
  (3), `DEADLINE` (2m).
- `RUNTIME_MEMORY_*`: `ENABLED` (true), `FACTTTL` (7d), `MAXFACTSPERRETAILER`
  (500), `CACHETTL` (10m).
- `RUNTIME_RISK_*`: `ENABLED` (true), `LOWCONFIDENCETHRESHOLD` (0.5), and the
  `PRICINGKEYWORDS` / `EMERGENCYKEYWORDS` lists that trigger a human-in-the-loop
  hold. Runs pause in `AWAITING_APPROVAL` and resume only after every approval
  is decided.

Telemetry is emitted via Micrometer/OpenTelemetry on the management port and
records only non-sensitive metrics (tokens, cost, latency, status, degraded
modes, approval waits) — never prompts, payloads or credentials.

### Offline evaluation gate

- `./gradlew evalSmoke` — fast local subset (the `normal` scenario).
- `./gradlew evalGate` — runs all eight golden scenarios, writes
  `build/eval/eval-report.json`, and fails on any threshold miss or drift from
  the committed baseline `eval/baseline/eval-report.json`. It also runs as part
  of `./gradlew check` and as a CI merge gate.
- `./gradlew evalGate -PevalUpdateBaseline` — regenerate the committed baseline
  after an intended, reviewed behavioural change. The report is deterministic
  and byte-stable, so an unreviewed change to runtime behaviour surfaces as a
  drift failure.

## Database migrations

Schema is owned by Flyway (`src/main/resources/db/migration`). Migrations run
automatically on startup in every non-test profile (`ddl-auto=none`). Review new
migrations before promotion; never edit an applied migration in place. Tests
build the schema from the JPA model on H2 and keep it in step with the DDL.

## Health and telemetry

- Application: `http://localhost:8080`
- Liveness: `http://localhost:8081/actuator/health/liveness`
- Readiness: `http://localhost:8081/actuator/health/readiness`
- Prometheus: `http://localhost:8081/actuator/prometheus`

Health details are never exposed. The management port must remain internal.

## Deploy

1. Run `./gradlew check` and the security workflow.
2. Build and scan the runtime image.
3. Promote the exact image digest; do not deploy `latest`.
4. Update the manifest image to that digest.
5. Verify startup/readiness probes and one successful scheduled run.

## Incident response

For suspected credential exposure, revoke the provider key first, pause the
schedule, preserve redacted logs and CI provenance, rotate Kubernetes secrets,
and redeploy a known-good digest. For malformed briefings, inspect schema
validation failures and provider status; never bypass validation to restore
service.

## Scaling and secrets

The Deployment runs multiple replicas behind a rolling update. Duplicate work is
prevented by the durable run's unique idempotency key plus a Redis lock, so
scaling is horizontal and safe. Provision `multi-agent-commerce-secrets`
(datasource URL/username/password, Redis password) and
`multi-agent-commerce-config` (Redis host/port/ssl, OAuth issuer and audiences)
from your secret manager before rollout — the manifests reference them and ship
no default passwords.

## Rollback

Redeploy the last known-good image digest and configuration. Run state lives in
PostgreSQL, so application rollback needs no data migration; only ever roll the
database forward via Flyway. Confirm the pods report ready and one durable run
per retailer/date is created.
