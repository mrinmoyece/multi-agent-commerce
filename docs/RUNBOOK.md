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

Schema is owned by Flyway (`src/main/resources/db/migration`; `V1`, `V2` today).
`ddl-auto=none` in every non-test profile — the JPA model never mutates the schema.

**Release strategy (expand/contract).** Every migration must be backward compatible
with the currently running version so a rolling deploy is safe:

1. *Expand* — add nullable columns/tables/indexes in one release; deploy code that
   writes both old and new shapes.
2. *Migrate* — backfill in a later release.
3. *Contract* — drop the old column/constraint only once no running replica needs it.

Never edit an applied migration in place; add a new versioned file. In Kubernetes a
dedicated **migration Job** (`k8s/base/migration-job.yaml`, `--app.migrate-only=true`)
applies migrations to completion and exits *before* the Deployment rolls, so replicas
never race on schema changes. Locally/compose, migrations still run on startup. Tests
validate the migrations from an empty database and across the upgrade path
(`FlywayMigrationTest`, Testcontainers when Docker is present; a deterministic
migration-ordering check runs even without Docker).

## Health and telemetry

- Application: `http://localhost:8080`
- Liveness: `http://localhost:8081/actuator/health/liveness`
- Readiness: `http://localhost:8081/actuator/health/readiness`
- Prometheus: `http://localhost:8081/actuator/prometheus`

Health details are never exposed. The management port must remain internal.
SLIs/SLOs are defined in [SLO.md](SLO.md); dashboards and alert rules live under
`observability/` (compose) and `k8s/base/{servicemonitor,prometheusrule}.yaml`
(Operator). No run/retailer/tenant identifier is ever a metric label.

## Deploy

Manifests are Kustomize: `k8s/base` plus `k8s/overlays/{dev,staging,prod}`.

1. Run `./gradlew clean check cyclonedxBom` and the release workflow gates.
2. Build/sign/scan the multi-arch image once (see `.github/workflows/release.yml`).
3. Apply the **migration Job** and wait for completion.
4. Promote the exact signed image **digest** into the target overlay's
   `images[].digest` — never a tag, never `latest`, never a rebuild between envs.
5. `kubectl apply -k k8s/overlays/<env>` (or via GitOps).
6. Verify readiness probes, signature/provenance (`cosign verify`), and one
   successful scheduled run.

## Incident response

For suspected credential exposure, revoke the provider key first, pause the
schedule, preserve redacted logs and CI provenance, rotate the External Secret
source, and redeploy a known-good digest. For malformed briefings, inspect schema
validation failures and provider status; never bypass validation to restore
service. See `docs/adr/` and `docs/governance/INCIDENT_RESPONSE.md` for the full
policy and severity ladder.

## Alert response

Each Prometheus alert links here via its `runbook_url`. First actions per alert:

### api-down
No healthy targets. Check pod status/events, recent deploy, node health. Roll back
to the last known-good digest if a deploy correlates.

### high-error-rate
5xx over budget. Inspect logs for the failing endpoint, provider/DB health, and
recent config. Consider pausing the schedule if runs amplify the errors.

### api-latency
p95 > 1.5s. Check DB pool saturation, provider latency, GC/CPU throttling, and HPA
state. Scale out if CPU-bound.

### run-failures
Run failure ratio > 10%. Inspect `RUN_FAILED` audit reasons, provider availability,
and circuit state. A single upstream outage usually explains a spike.

### degraded-runs
Agents degrading (schema/LLM/budget). Groundedness at risk — check provider health
and recent prompt/model changes; consider reverting a prompt/eval release.

### stale-evidence
Tools returning evidence past its freshness window. Check the upstream system of
record and adapter clock/freshness config; runs remain safe (stale flagged) but act
before groundedness SLO burns.

### approval-backlog
Many runs `AWAITING_APPROVAL`. Page the approval on-call; verify the approvals API
and that decisions are being recorded.

### provider-latency
Azure OpenAI p95 high. Check provider status page, region, and quota; expect run
slowdowns. Budgets will hard-stop runs before cost runs away.

### tool-errors
An adapter is failing. Check adapter egress (NetworkPolicy), upstream health, and
auth/token expiry. Sustained failures trip the circuit (see below).

### circuit-open
An adapter's circuit breaker has opened and is isolating the failing upstream.
Confirm the upstream and auth; the circuit half-opens automatically — do not force
it closed. Runs continue with that evidence marked degraded.

### budget-exhaustion
Frequent hard-stops. Review whether upstream latency inflated token/wall-clock use,
or whether budgets need adjustment (change-managed).

### queue-age
Oldest PENDING run > 15m. The execution pool or all replicas may be stalled/blocked
on the lock backend. Check Redis health, pool metrics, and replica readiness.

## Backup, restore & disaster recovery

**State of record is PostgreSQL.** Redis holds only ephemeral coordination
(locks, rate limits, status cache); losing it is survivable.

- **RPO ≤ 5 min, RTO ≤ 30 min** (targets).
- **Backups:** managed PostgreSQL with point-in-time recovery (WAL archiving) and
  automated daily base backups retained ≥ 14 days, plus weekly cross-region copies.
  Verify restores monthly into a scratch database.
- **Restore drill:** provision a new PostgreSQL, PITR to the target timestamp, point
  a fresh overlay's datasource secret at it, run the migration Job (no-op if the
  backup is current), then roll out the app at the last known-good digest.
- **Redis loss:** in-flight locks/status vanish. The platform fails closed — a run
  that cannot claim its lock is simply not executed by that replica, and the
  idempotency key prevents duplicates when the schedule next fires. On Redis
  recovery, coordination resumes with no manual step. If Redis is down at schedule
  time, the run stays PENDING (watch the `queue-age` alert) and is claimed once
  Redis returns.
- **Disaster (region loss):** restore PostgreSQL cross-region, stand up the cluster
  from the committed Kustomize overlays, reprovision secrets via External Secrets,
  deploy the last signed digest, and verify one scheduled run. No application state
  lives outside PostgreSQL and the manifests.

## Rollback

Redeploy the last known-good image **digest** (recorded in the release's
`release-metadata.json` artifact) and its configuration. Run state lives in
PostgreSQL, so app rollback needs no data migration — the expand/contract rule keeps
one prior version compatible with the current schema. If a bad migration shipped,
**forward-fix** with a new corrective migration rather than dropping columns under a
running fleet. Confirm pods report ready and exactly one durable run per
retailer/date is created.
