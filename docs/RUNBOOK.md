# Operations runbook

## Configuration

Required:

- `LLM_PROVIDER`: `github-models` or `azure-openai`
- `LLM_API_KEY`: provider credential
- `AZURE_OPENAI_ENDPOINT`: required for Azure OpenAI

Optional controls include `LLM_TIMEOUT`, `LLM_MAX_RESPONSE_CHARACTERS`,
`AZURE_CHAT_DEPLOYMENT`, `RETAILER_ID`, `BRIEFING_CRON`, and
`MANAGEMENT_PORT`.

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

## Rollback

Redeploy the last known-good image digest and configuration. The service is
stateless, so rollback does not require data migration. Confirm that only one
pod is running and that the management endpoint reports ready.
