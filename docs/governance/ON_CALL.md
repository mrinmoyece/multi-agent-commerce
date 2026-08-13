# On-call playbooks

On-call owns availability and the SLOs in [../SLO.md](../SLO.md). Every alert links
here or to the RUNBOOK "Alert response" section via its `runbook_url`.

## Getting started each shift

- Confirm access: cluster (`kubectl`), dashboards (Grafana), alerts
  (Prometheus/Alertmanager), and secret manager.
- Check the SLO dashboard and error budget burn for each SLO.
- Review open incidents and recent deploys/releases (by digest).

## Golden signals & where to look

| Symptom | First dashboard/metric | Likely playbook |
| ------- | ---------------------- | --------------- |
| API errors/latency | `http_server_requests` p95 / 5xx | RUNBOOK: api-down, high-error-rate, api-latency |
| Runs failing/degraded | `runtime_run_completed_total`, degraded ratio | RUNBOOK: run-failures, degraded-runs |
| Approvals piling up | runs `AWAITING_APPROVAL` | RUNBOOK: approval-backlog + ADR 0003 |
| Provider slow/erroring | provider latency/error metrics | RUNBOOK: provider-latency; CHANGE_MANAGEMENT rollback |
| Adapter failing | circuit state, tool errors | RUNBOOK: tool-errors/circuit-open; ADR 0005 |
| Budgets hard-stopping | `budget-exhaustion` | RUNBOOK: budget-exhaustion + CHANGE_MANAGEMENT |
| Runs not starting | `runtime_run_queue_age_seconds` | RUNBOOK: queue-age; check Redis + replicas |
| Stale evidence | `runtime_evidence_stale_total` | RUNBOOK: stale-evidence; check upstream + adapter freshness |

## Standard mitigations (safe, reversible)

- **Roll back** to the last signed good digest (from `release-metadata.json`).
- **Pause the schedule** to stop amplifying a failing dependency.
- **Isolate an adapter** — its circuit breaker already opens on sustained failure;
  do not force it closed.
- **Scale out** for CPU-bound latency (HPA usually handles this).

Never bypass schema validation, grounding, HITL approval, or tenant scoping to
restore service. If a mitigation would weaken a safety control, escalate instead.

## Escalation

- Cannot mitigate within the severity's target (INCIDENT_RESPONSE.md) → escalate to
  the platform team and, for SEV1, page the incident commander.
- Suspected credential/data exposure → SEV1 immediately; follow RUNBOOK incident
  response (revoke first).

## Deploys & releases

- Promotion is by **digest** with environment protection; never deploy `latest` or
  rebuild between environments.
- Apply the migration Job before rolling the app; migrations are expand/contract
  and rolling-safe.
- After any deploy, verify readiness, signature/provenance, and one successful
  scheduled run.
