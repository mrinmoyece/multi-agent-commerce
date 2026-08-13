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
