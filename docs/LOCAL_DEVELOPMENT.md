# Local development

This guide gets the Multi-Agent E-Commerce Operations Platform running on your
machine with Docker Compose, verifies it is healthy, and walks through calling the
API end to end. It is the canonical local setup reference; the README Quick Start
links here.

> Security note: every command and snippet below uses **placeholders** for
> secrets and identity values. Never paste a real API key, password, token, or
> issuer secret into a file that could be committed. `.env` is git-ignored — keep
> it that way.

## Prerequisites

| Tool | Version | Notes |
| ---- | ------- | ----- |
| **JDK 21** | 21 LTS | Only needed to run `./gradlew` tasks directly (tests, build). The app image is built inside Docker, so the container path does not need a local JDK. |
| **Docker + Docker Compose v2** | Compose **v2** (`docker compose`, space — not the legacy `docker-compose` hyphen binary) | Runs Postgres, Redis, the app, Prometheus and Grafana. |
| **An LLM credential** | real key | Either a GitHub Models token (`LLM_PROVIDER=github-models`) or an Azure OpenAI key + endpoint (`LLM_PROVIDER=azure-openai`). There is no offline stub for the model at runtime. |
| **An OIDC issuer + audience** | real issuer | The API is an OAuth2 resource server with **no static bypass** — it validates real JWTs. You need an issuer that exposes OIDC discovery and can mint tokens with the scopes below. |
| `curl` and (optional) `jq` | any recent | For the API examples. |

The Gradle wrapper is committed, so you do **not** need Gradle installed.

## 1. Configure the environment

Copy the template and fill in real values:

```bash
cp .env.example .env
# then edit .env in your editor
```

### Required variables

Compose fails fast (`Set X in .env`) if any of these are missing — that is
intentional, there are no baked-in default secrets.

| Variable | Purpose |
| -------- | ------- |
| `LLM_API_KEY` | Credential for the model provider. GitHub Models token or Azure OpenAI key. |
| `POSTGRES_PASSWORD` | Password Compose provisions Postgres with and the app connects with. Pick a strong local value. |
| `REDIS_PASSWORD` | Password for the Redis instance and the app's Redis client. |
| `OAUTH2_ISSUER_URI` | Your OIDC issuer URI. The app fetches `${issuer}/.well-known/openid-configuration` at startup to learn signing keys, so this must be reachable and must exactly match the `iss` claim of your tokens. |
| `OAUTH2_AUDIENCE` | The audience the API accepts. At least one value here must appear in the token's `aud` claim, or the request is rejected. |
| `GRAFANA_ADMIN_PASSWORD` | Admin password for the local Grafana at `:3000`. |

### Optional / defaulted variables

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `LLM_PROVIDER` | `github-models` | `github-models` or `azure-openai`. |
| `AZURE_OPENAI_ENDPOINT` | empty | Required only when `LLM_PROVIDER=azure-openai` (e.g. `https://your-resource.openai.azure.com`). |
| `AZURE_CHAT_DEPLOYMENT` | `gpt-4o` | Azure deployment name. |
| `LLM_TIMEOUT` | `30s` | Provider transport timeout. |
| `LLM_MAX_RESPONSE_CHARACTERS` | `32768` | Hard cap on a model response. |
| `RETAILER_ID` | `default-retailer` | Default retailer scope for the scheduled job. |
| `BRIEFING_CRON` | `0 0 6 * * *` | Schedule for the daily briefing (the `dev` profile overrides this to every minute). |
| `PLATFORM_REDIS_ENABLED` | `true` in Compose | Compose runs a real Redis and enables coordination. Running the app outside Compose under the `dev` profile defaults this to `false` (single-JVM in-memory coordination). |
| `ENV` | `local` | Free-form environment label. |

> `RUNTIME_TOOLS_MODE` is **not** set for local development — see
> [Fixture vs LIVE mode](#fixture-vs-live-mode). It defaults to `FIXTURE`.

## 2. Start the stack

```bash
docker compose up --build
```

This builds the app image and starts, in dependency order:

| Service | Host address (bound to loopback only) | Purpose |
| ------- | ------------------------------------- | ------- |
| app — API | `http://127.0.0.1:8080` | Versioned REST API under `/api/v1`. |
| app — management | `http://127.0.0.1:8081` | Health, info, Prometheus, metrics (internal port). |
| postgres | `127.0.0.1:5432` | Durable run state. |
| redis | `127.0.0.1:6379` | Distributed lock / rate limit / status cache. |
| prometheus | `http://127.0.0.1:9090` | Scrapes the management port. |
| grafana | `http://127.0.0.1:3000` | Dashboards (login `admin` / `GRAFANA_ADMIN_PASSWORD`). |

All ports are published on `127.0.0.1` only, so nothing is exposed on your
network. The app runs under the `dev` Spring profile with a read-only root
filesystem and dropped capabilities.

## 3. Verify readiness

The app is healthy once its readiness probe passes (Postgres and Redis must be up
first — Compose waits on their health checks):

```bash
curl -fsS http://127.0.0.1:8081/actuator/health/readiness
# {"status":"UP"}

curl -fsS http://127.0.0.1:8081/actuator/health/liveness
# {"status":"UP"}
```

Health detail is deliberately never exposed (`show-details: never`), so you will
see only the aggregate status. You can also confirm container health with:

```bash
docker compose ps
```

Metrics are at `http://127.0.0.1:8081/actuator/prometheus` (used by the bundled
Prometheus). The application port `8080` serves only the secured API — calling it
without a valid token returns `401`.

## 4. Obtain a bearer JWT

The API accepts a standard OAuth2 bearer token from your configured issuer. Mint a
token however your identity provider supports (for local dev this is typically a
**client-credentials** grant against the same issuer in `OAUTH2_ISSUER_URI`). The
token must satisfy all of:

- **Issuer** — `iss` equals `OAUTH2_ISSUER_URI`.
- **Audience** — `aud` contains a value from `OAUTH2_AUDIENCE`.
- **Scopes** — the `scope` claim contains the scope required by the operation:
  - `runs:read` — read runs and approvals
  - `runs:write` — create runs
  - `approvals:write` — approve/reject an approval
- **Roles** *(optional)* — a `roles` claim maps to `ROLE_*` authorities for
  coarse RBAC, in addition to the scopes above.

Example shape of a client-credentials request (fill in your issuer's real token
endpoint and a client you control — values shown are placeholders):

```bash
# Placeholders only — never commit real client credentials.
export TOKEN=$(curl -fsS -X POST "$OIDC_TOKEN_ENDPOINT" \
  -d grant_type=client_credentials \
  -d client_id="$OIDC_CLIENT_ID" \
  -d client_secret="$OIDC_CLIENT_SECRET" \
  -d scope="runs:read runs:write approvals:write" \
  -d audience="$OAUTH2_AUDIENCE" \
  | jq -r .access_token)
```

Keep the token in an environment variable (as above) rather than pasting it into
commands, so it does not land in your shell history in plain sight.

## 5. Trigger and retrieve an analysis run

Create a run (`runs:write`). `retailerId` is required (≤100 chars); `businessDate`
is optional and defaults to today server-side. The optional `Idempotency-Key`
header makes retries safe — the same key returns the same run.

```bash
curl -fsS -X POST http://127.0.0.1:8080/api/v1/analysis-runs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: local-demo-001" \
  -d '{"retailerId":"default-retailer"}'
```

The response is `201 Created` with a `Location: /api/v1/analysis-runs/{id}` header
and a body like (abridged):

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "retailerId": "default-retailer",
  "businessDate": "2026-01-01",
  "status": "PENDING",
  "triggerSource": "API",
  "correlationId": "…",
  "executiveSummary": null,
  "crossInsights": [],
  "recommendedActions": [],
  "urgentAlerts": [],
  "agentResults": []
}
```

Fetch the run by id (`runs:read`) to watch it progress and read results once
`status` is `SUCCEEDED` (or `AWAITING_APPROVAL` if a high-impact action needs a
human decision):

```bash
RUN_ID=00000000-0000-0000-0000-000000000000   # from the Location header above
curl -fsS http://127.0.0.1:8080/api/v1/analysis-runs/$RUN_ID \
  -H "Authorization: Bearer $TOKEN"
```

List recent runs with bounded pagination (max page size 100):

```bash
curl -fsS "http://127.0.0.1:8080/api/v1/analysis-runs?page=0&size=20&retailerId=default-retailer" \
  -H "Authorization: Bearer $TOKEN"
```

If a run is `AWAITING_APPROVAL`, list its approvals, then approve or reject one
(`approvals:write`, optional `reason`):

```bash
curl -fsS http://127.0.0.1:8080/api/v1/analysis-runs/$RUN_ID/approvals \
  -H "Authorization: Bearer $TOKEN"

APPROVAL_ID=00000000-0000-0000-0000-000000000000
curl -fsS -X POST http://127.0.0.1:8080/api/v1/approvals/$APPROVAL_ID/approve \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"looks good for local testing"}'
```

The scheduler also creates a run automatically on the `dev` cron (every minute),
so runs will appear even without an API call.

## Fixture vs LIVE mode

Grounding data is controlled by `RUNTIME_TOOLS_MODE`:

- **`FIXTURE`** (default, and what local dev uses) — the inventory, pricing and
  sentiment tools return committed deterministic fixtures. They are explicitly
  labelled non-authoritative and surfaced as `DEGRADED`, so the system never
  claims live data. This is the correct mode for local development and demos.
- **`LIVE`** — the tools call real external systems through the hardened HTTP
  adapters. LIVE mode **fails fast at startup** unless every required
  `runtime.tools.live.*` endpoint and auth setting is present and valid (HTTPS,
  allow-listed host, bounded deadlines/sizes/pagination/freshness, complete
  credentials). There are no fake production defaults, so you cannot accidentally
  run LIVE against an unconfigured backend. See
  [ADR 0005](adr/0005-live-adapter-trust-boundary.md) and the commented
  `runtime.tools.live.*` block in `src/main/resources/application.yml`.

Leave `RUNTIME_TOOLS_MODE` unset locally unless you are specifically testing the
live adapters against your own mock/real backends.

## Running the offline checks

The full gate suite runs without Docker and without any network — it uses H2 (in
PostgreSQL mode), an in-memory coordination stand-in, and a scripted provider:

```bash
./gradlew clean check
```

`check` runs the tests, the 70% coverage gate, Checkstyle, SpotBugs, the offline
agentic evaluation gate (`evalGate`), and the deterministic performance
(`perfGate`) and resilience (`resilienceGate`) gates. A real PostgreSQL migration
test (`FlywayMigrationTest`) runs additionally when a Docker daemon is available
via Testcontainers, and is skipped (not failed) when it is not.

## Stopping and cleanup

Stop the stack but **keep** your local database and Redis data:

```bash
docker compose down
```

> **Warning:** the next command deletes local data. Adding `-v` removes the named
> volumes, including `postgres-data` — every local run, result, approval and audit
> record is permanently erased. Only use it when you want a clean slate.

```bash
docker compose down -v   # DESTROYS local Postgres data
```

## Troubleshooting

### Issuer discovery fails at startup
Symptom: the app fails to start with an error fetching OpenID configuration.
- Confirm `OAUTH2_ISSUER_URI` is reachable from inside the app container and that
  `${OAUTH2_ISSUER_URI}/.well-known/openid-configuration` returns JSON.
- The issuer must exactly match your tokens' `iss` claim (including scheme, host
  and any path segment, and trailing-slash handling).
- If your IdP runs on `localhost`, remember the app is in a container:
  `127.0.0.1` there is the container, not your host. Use a container-reachable
  address (e.g. `host.docker.internal`) for the issuer.

### `401`/`403` on API calls (audience or scope mismatch)
- `401` usually means the token is missing, expired, or from the wrong issuer.
- `403` means authenticated but not authorized — the token lacks the required
  scope (`runs:read` / `runs:write` / `approvals:write`).
- Decode your JWT (e.g. paste the payload segment into a local decoder) and check:
  `aud` contains a value from `OAUTH2_AUDIENCE`; `iss` equals `OAUTH2_ISSUER_URI`;
  `scope` contains the scope the endpoint needs.

### Postgres or Redis unhealthy
- `docker compose ps` shows health; `docker compose logs postgres` /
  `docker compose logs redis` show why.
- The app waits for both to be healthy before starting. If the app container exits
  early, it usually means Postgres/Redis never became healthy — most often a
  missing/incorrect `POSTGRES_PASSWORD` or `REDIS_PASSWORD` in `.env`.
- Port already in use (`5432`/`6379` bind fails): stop the local Postgres/Redis
  you already run, or change the published port in `docker-compose.yml`.

### Docker daemon not running
- `Cannot connect to the Docker daemon` means Docker Desktop / the engine is not
  started. Start it and retry `docker compose up --build`.
- If you are using the legacy `docker-compose` (hyphen) binary and see command
  differences, switch to Compose v2 (`docker compose`, space) — that is the
  supported form throughout this repo.

### App starts but every run stays `PENDING`
- Under the `dev` profile coordination is in-memory unless
  `PLATFORM_REDIS_ENABLED=true` (Compose sets it true). If you run the app outside
  Compose with Redis enabled but unreachable, run claiming fails closed and runs
  stay `PENDING`. Point `SPRING_DATA_REDIS_HOST`/password at a reachable Redis or
  set `PLATFORM_REDIS_ENABLED=false` for single-JVM local runs.

## Related documentation

- [Runbook](RUNBOOK.md) — deploy, health, alert response, backup/DR, rollback
- [Architecture](ARCHITECTURE.md) — components, execution flow, invariants
- [Security model](SECURITY_MODEL.md) — trust boundaries and controls
- [Limitations](LIMITATIONS.md) — what the system does not claim
