# Architecture

## Execution flow

`AgentCoordinator` runs on a configurable schedule. It dispatches the inventory,
pricing, and sentiment agents on the application task executor. Each specialist
calls `LlmGateway`, which is the only provider boundary and owns transport
timeouts, retry, circuit breaking, empty-response rejection, and response-size
limits. The coordinator serializes specialist findings as untrusted JSON and
requests one structured synthesis.

The synthesis is accepted only when it contains string arrays for
`crossInsights`, `recommendedActions`, and `urgentAlerts`, plus a non-empty
`executiveSummary`. Invalid output fails the run; it is never presented as a
successful briefing.

## Invariants

- Specialist agents remain stateless.
- External and model-generated content is untrusted.
- Provider credentials are supplied at runtime and never persisted.
- A failed specialist is explicit in the briefing input; raw exception text is
  not exposed.
- The deployment remains a singleton while scheduling and results are
  process-local.
- Management endpoints are separate from application traffic.

## Deployment

The image is multi-stage and runs as UID `10001`. Kubernetes drops all
capabilities, disables privilege escalation and service-account token mounting,
uses a read-only root filesystem, and permits only DNS and public HTTPS egress.
Release automation must promote images by digest.
