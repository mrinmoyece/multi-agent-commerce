# Incident response

## Severity ladder

| Sev | Definition | Response |
| --- | ---------- | -------- |
| **SEV1** | Customer-facing outage, data loss, or credential/data exposure | Page on-call immediately; incident commander; comms every 30 min |
| **SEV2** | Major degradation (SLO fast-burn, approvals stalled, provider down) | Page on-call; mitigate within the hour |
| **SEV3** | Minor/partial degradation with a workaround | Ticket; handle in business hours |

## Roles

- **Incident commander** — owns the incident, coordinates, decides rollback.
- **Operator** — executes runbook steps.
- **Scribe** — timeline, decisions, follow-ups.
- **Comms** — stakeholder updates (SEV1/SEV2).

## Flow

1. **Detect** — alert fires (`runbook_url` → RUNBOOK "Alert response") or a report.
2. **Triage** — assign severity; open an incident channel and doc.
3. **Mitigate** — prefer the fastest safe action: roll back to the last signed
   good **digest**, pause the schedule, or isolate a failing adapter (its circuit
   already isolates it). Never bypass schema validation or tenant scoping to
   restore service.
4. **Diagnose** — use dashboards, audit trail, and correlation IDs (in traces, not
   metrics) to find root cause.
5. **Recover** — restore normal operation; confirm SLIs recover and one scheduled
   run succeeds.
6. **Review** — blameless postmortem within 3 business days with dated action items.

## Credential exposure (SEV1)

Follow RUNBOOK → Incident response: revoke the provider key first, pause the
schedule, preserve redacted logs + CI provenance, rotate the External Secret
source, redeploy a known-good digest, then rotate any co-located secrets.

## Data / disaster events

For data loss or region loss, follow RUNBOOK → "Backup, restore & disaster
recovery" (RPO ≤ 5 min, RTO ≤ 30 min). PostgreSQL is the only system of record;
Redis loss is survivable and self-healing.
