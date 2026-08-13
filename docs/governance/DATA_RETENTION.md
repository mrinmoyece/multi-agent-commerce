# Data retention & privacy classification

## Data classification

| Class | Examples | Handling |
| ----- | -------- | -------- |
| **Secret** | Provider API keys, datasource/Redis passwords, mTLS keys, OAuth client secrets | Never persisted by the app, never logged, only via External Secrets / workload identity. Rotate on exposure. |
| **Confidential** | Tenant business data: inventory levels, pricing, customer sentiment/review evidence, recommendations | Stored in PostgreSQL, tenant-scoped, encrypted at rest by the managed database, TLS in transit. |
| **Internal** | Run metadata, audit trail, correlation IDs | Stored in PostgreSQL; correlation/run IDs are high-cardinality and are **never** used as metric labels. |
| **Public / non-sensitive** | Aggregate metrics, SLO numbers, non-identifying dashboards | Exposed on the internal management port only. |

## Personal data

The platform processes **business** evidence. Customer sentiment/review evidence
may contain personal data from upstream systems. It is treated as Confidential,
tenant-scoped, and retained only as long as needed to ground a run and its audit.
No customer PII is placed in logs, metric labels, or traces beyond what a redacted
audit requires.

## Retention

| Data | Retention | Mechanism |
| ---- | --------- | --------- |
| Run state & results | Operational lifetime + audit window (default 400 days) | PostgreSQL; pruned by a change-managed retention job |
| Audit trail | ≥ 1 year (append-only) | PostgreSQL; never edited in place |
| Semantic memory | TTL-bounded, tenant-scoped | Best-effort expiry at read/write (see ADR 0002) |
| Ephemeral coordination (locks, rate limits, status cache) | Seconds–minutes | Redis; safe to lose |
| Logs / traces | Per platform log retention (redacted) | Aggregator; no secrets, no metric-label PII |
| Backups | ≥ 14 days daily + weekly cross-region | Managed PostgreSQL PITR (see RUNBOOK) |

## Deletion

Tenant offboarding deletes that tenant's rows (runs, results, approvals, memory)
under the tenant scope key; audit records required for compliance are retained for
their mandated window and then purged. Backups age out per the retention above.
