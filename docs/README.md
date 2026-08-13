# Documentation

- [Local development](LOCAL_DEVELOPMENT.md) - canonical local setup: prerequisites, `.env`, `docker compose`, readiness, JWT, and API walkthrough
- [Architecture](ARCHITECTURE.md) - components, execution flow, and invariants
- [Security model](SECURITY_MODEL.md) - assets, trust boundaries, threats, and controls
- [Runbook](RUNBOOK.md) - deploy, health, observability, alert response, backup/DR, rollback
- [SLOs/SLIs](SLO.md) - service level objectives, indicators, and error budgets
- [Limitations](LIMITATIONS.md) - capabilities the current system does not claim
- [Architecture decisions](adr/README.md) - durable orchestration, memory trust,
  HITL, eval strategy, live adapter trust boundary, execution model
- [Operational governance](governance/README.md) - data retention, tenant
  isolation, incident response, change management, prompt/eval release, on-call

Architecture decisions that change long-lived constraints belong in
`docs/adr/`; operational policies belong in `docs/governance/`.
