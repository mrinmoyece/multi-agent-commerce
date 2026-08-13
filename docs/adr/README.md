# Architecture Decision Records

Each ADR captures one long-lived, hard-to-reverse decision: its context, the
options considered, the decision, and its consequences. ADRs are immutable once
`Accepted`; supersede rather than edit.

| ADR | Title | Status |
| --- | ----- | ------ |
| [0001](0001-durable-orchestration.md) | Durable orchestration on PostgreSQL | Accepted |
| [0002](0002-memory-trust.md) | Semantic memory trust model | Accepted |
| [0003](0003-hitl-policy.md) | Human-in-the-loop approval policy | Accepted |
| [0004](0004-eval-strategy.md) | Deterministic offline evaluation strategy | Accepted |
| [0005](0005-live-adapter-trust-boundary.md) | Live adapter trust boundary | Accepted |
| [0006](0006-singleton-vs-horizontal-execution.md) | Singleton vs horizontal execution | Accepted |

Operational governance documents (retention, tenant isolation, incident
response, change management, release process, on-call) live in
[`../governance/`](../governance/README.md).
