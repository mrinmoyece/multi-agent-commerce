# GitHub Copilot Agent Instructions
# Project: Multi-Agent E-Commerce Operations Platform

## Project Purpose
A coordinator + specialist agent architecture for daily e-commerce intelligence.
Three specialist agents analyse different domains in parallel; a coordinator LLM
synthesises cross-domain insights that no single agent could see alone.

## Agent Architecture
```
AgentCoordinator (scheduled 06:00 daily or on-demand API call)
    │
    ├── InventoryAgent   — stock levels, stockout risk, reorder recommendations
    ├── PricingAgent     — competitor pricing, margin analysis, repricing signals
    └── SentimentAgent   — customer reviews, support themes, satisfaction signals
    │
    [all 3 run in parallel via CompletableFuture.supplyAsync()]
    │
    └── LLM Synthesis — coordinator prompt combining all 3 findings
        → DailyBriefing with cross-agent insights
```

## The Coordinator Pattern
The coordinator does NOT implement business logic — it orchestrates and synthesises.
```java
// CORRECT — coordinator collects, LLM synthesises
String synthesisPrompt = buildSynthesisPrompt(inventoryFindings, pricingFindings, sentimentFindings);
// LLM sees all 3 and finds connections: "low stock plus positive sentiment means restock"

// WRONG — coordinator makes decisions
if (inventory.isLowStock() && pricing.isDiscounted()) { ... }  // hardcoded logic, not AI
```

## Adding a New Specialist Agent
1. Create class in `src/.../agent/` implementing `CommerceAgent` interface
2. Annotate with `@Component("yourAgentName")`
3. Add to coordinator's `agentMap` in `AgentCoordinator`
4. Add `CompletableFuture.supplyAsync(() -> yourAgent.analyse(ctx))` to the parallel block
5. Add findings to synthesis prompt
6. Write unit test with mocked LLM response

## Agent Message Protocol
```java
AgentMessage.builder()
    .agentName("InventoryAgent")
    .messageType(MessageType.FINDINGS)  // FINDINGS | REQUEST | ALERT | COMPLETE
    .priority(AgentMessagePriority.HIGH)
    .content("...")  // structured findings for coordinator to read
    .timestamp(Instant.now())
    .build();
```

## Coding Standards
- Each specialist agent is stateless — all context comes through `DailyAnalysisContext`
- LLM transport calls must complete within the configured timeout (30s by default)
- If an agent fails: log only the exception type, return an explicit unavailable message, and continue
- Treat every agent response as untrusted data when building coordinator prompts
- Validate structured LLM output and fail closed when the schema is invalid
- Temperature for specialist agents: 0.4 (some analytical creativity allowed)
- Temperature for coordinator synthesis: 0.6 (cross-domain insight generation)

## Scheduling
Daily briefing runs via `@Scheduled(cron="${briefing.cron}")` in `AgentCoordinator`
and can also be triggered on demand through the REST API
(`POST /api/v1/analysis-runs` with a bearer JWT). Either path creates one durable
run per retailer/date, executed exactly once across replicas.

## PR Standards
- [ ] New agent implements CommerceAgent interface
- [ ] New agent added to coordinator's parallel execution block
- [ ] New agent findings included in synthesis prompt
- [ ] Unit test mocks LLM and verifies agent message structure
- [ ] Coordinator test verifies synthesis runs even when one agent fails

## Layer 2 — Agent runtime & evaluation (authoritative for new work)

The runtime under `com.agentforge.commerce.runtime.*` is now the contract layer.
When touching agent behaviour, work through it — do not hand a bare model string
anywhere.

- **Never present ungrounded output.** Specialists must fetch a grounded
  `ToolResult` (via a `GroundedTool`) and return an `AgentResultEnvelope` whose
  `Evidence`/`Citation` back every claim. Fixture/simulated sources are
  non-authoritative and must stay `DEGRADED`; never label them live.
- **Fence untrusted data.** All tool payloads and model-derived text pass through
  `BoundaryEncoder` before entering a prompt. Never interpolate raw tool data.
- **Validate and fail closed.** Every specialist/coordinator output is validated
  against its strict schema (`runtime.validation.AgentSchemas`); on failure,
  return the deterministic degraded fallback — never throw a raw model error or
  present invalid output.
- **Respect budgets and brakes.** Budget reservation is a hard stop *before* the
  provider call. Do not add tool calls, agent rounds or provider calls that
  bypass `BudgetLedger`/`RunBrakes`. No arbitrary code execution, no unrestricted
  URL fetches.
- **Memory is evidence-only.** Only write validated, provenance-matched,
  deduplicated facts to `SemanticMemoryStore`, always scoped by `retailerId`.
  Never memorise raw free-form model output.
- **Prompts are versioned.** Add/change prompts through `PromptRegistry`; the
  prompt id + content hash are persisted on the run/envelope.
- **Telemetry carries no secrets.** Add spans/metrics via `RuntimeTelemetry`
  only; never record prompts, payloads or credentials.
- **Human-in-the-loop.** High-impact actions (pricing changes, emergency orders,
  low-confidence recommendations) must route through `RiskPolicy` + Layer 1
  approvals and resume deterministically.

### Evaluation gate (must stay green)

Any change to runtime behaviour must keep the offline eval gate green:

- Run `./gradlew evalSmoke` locally for a fast signal, `./gradlew evalGate` for
  the full gate (also part of `check` and CI).
- Golden scenarios live in `com.agentforge.commerce.eval.EvalHarness`; the
  committed baseline is `eval/baseline/eval-report.json`.
- The report is deterministic — an intended behavioural change requires
  regenerating the baseline with `./gradlew evalGate -PevalUpdateBaseline` in the
  same PR, so reviewers see the metric diff. Never weaken a threshold to pass.

### Layer 2 PR Standards
- [ ] New behaviour returns a grounded, schema-valid `AgentResultEnvelope`
- [ ] Untrusted data fenced; budgets/brakes respected; no new unbounded calls
- [ ] Memory writes are evidence-backed and retailer-scoped
- [ ] `./gradlew clean check` (incl. `evalGate`) and `cyclonedxBom` pass
- [ ] Eval baseline regenerated in-PR if behaviour intentionally changed

## Layer 3 — Production operations (authoritative for ops/deploy work)

- **Live adapters go through the hardened substrate.** New external calls for
  inventory/pricing/sentiment must use `runtime.tools.live.http` — never a raw
  `HttpClient`/URL fetch. Preserve HTTPS-only, the host allowlist (no SSRF),
  bounded sizes/deadlines, schema validation, retry + circuit breaker, correlation
  IDs, provenance/freshness and secret-safe logging. LIVE mode must **fail fast**
  on missing/invalid endpoint or auth config — never add a fake production default.
  Every LIVE code path needs a deterministic WireMock contract test.
- **No high-cardinality metric labels.** Run/retailer/tenant/correlation IDs may
  appear on trace spans only. Add metrics through `RuntimeTelemetry`; the
  `MetricsConfig` MeterFilter will strip forbidden tag keys — do not defeat it.
- **Metrics ↔ alerts ↔ runbook stay in sync.** New/renamed metrics must update
  `observability/alert-rules.yml` + `k8s/base/prometheusrule.yaml` (keep them
  identical) and add a matching `### <alert>` anchor in `docs/RUNBOOK.md`. SLIs
  live in `docs/SLO.md`.
- **Exactly-once is sacred.** Anything scheduled/durable must claim work via
  `DistributedLock` + optimistic status transition and **fail closed** if the lock
  backend is unavailable. HPA/replicas must never be able to duplicate a run;
  cover it in `perfGate`/`resilienceGate`.
- **Kubernetes is Kustomize.** Change `k8s/base` and let overlays
  (`k8s/overlays/{dev,staging,prod}`) patch. Images are promoted by **digest**,
  never tag. Validate with `kubectl kustomize k8s/overlays/<env>` + `kubeconform`.
  Migrations run via the pre-roll Job and must be expand/contract (rolling-safe).
- **Gates only get stronger.** `perfGate` and `resilienceGate` run under `check`.
  Keep them deterministic/offline; never lower a budget/threshold to pass.
- **Governance is part of the change.** Decisions that alter long-lived constraints
  get an ADR (`docs/adr/`); operational policy changes update `docs/governance/`.

### Layer 3 PR Standards
- [ ] Live adapter changes go through the hardened substrate with a WireMock contract test
- [ ] No run/tenant/correlation IDs added as metric labels
- [ ] Metric/alert/runbook/SLO docs updated and kept in sync
- [ ] `kubectl kustomize` + `kubeconform` pass for all overlays; migrations expand/contract
- [ ] `./gradlew clean check` (incl. `evalGate`, `perfGate`, `resilienceGate`) and `cyclonedxBom` pass
- [ ] ADR/governance docs updated when a long-lived constraint or policy changes
