# Service Level Objectives & Indicators

This document defines the platform's SLIs (what we measure), SLOs (the target for
each SLI) and how they map to alerts and dashboards. All indicators are derived from
low-cardinality metrics — no run, retailer or tenant identifier is ever a metric
label (those live only on trace spans). See `RuntimeTelemetry` and `MetricsConfig`.

Alerting rules live in `observability/alert-rules.yml` (compose / standalone
Prometheus) and `k8s/base/prometheusrule.yaml` (Prometheus Operator). The dashboard
is `observability/grafana-dashboard.json`. Every alert carries a `runbook_url`
annotation pointing at the matching `docs/RUNBOOK.md` anchor.

## Measurement window & error budgets

- SLOs are evaluated on a rolling **28-day** window.
- The error budget is `1 - SLO`. Fast-burn alerts (short windows, tight thresholds)
  page immediately; the slow-burn view is the dashboard's SLO stat panels.

## Indicators

| # | SLI | Definition (PromQL sketch) | SLO target | Alert |
|---|-----|----------------------------|-----------|-------|
| 1 | **Availability** | `1 - (5xx / total)` on `http_server_requests_seconds_count` | ≥ 99.9% | `CommerceApiDown`, `CommerceApiHighErrorRate` |
| 2 | **API latency** | p95 of `http_server_requests_seconds_bucket` (non-actuator) | p95 ≤ 1.5s | `CommerceApiLatencyHigh` |
| 3 | **Run success** | `success / total` on `runtime_run_completed_total` | ≥ 95% | `CommerceRunFailureRateHigh` |
| 4 | **Groundedness** | `1 - rate(runtime_degraded_total)` relative to run rate; offline eval gate enforces per-scenario groundedness | ≥ 99% grounded; eval gate 8/8 | `CommerceDegradedRunsHigh` |
| 5 | **Evidence freshness** | `rate(runtime_evidence_stale_total)` | < 0.1/s sustained | `CommerceStaleEvidenceHigh` |
| 6 | **Approval latency / backlog** | `increase(runtime_approval_wait_total[1h])`; duration derived from audit `RUN_AWAITING_APPROVAL`→`APPROVAL_*` timestamps | backlog < 20/h | `CommerceApprovalBacklogGrowing` |
| 7 | **Provider latency** | p95 of `runtime_llm_latency_seconds_bucket` | p95 ≤ 20s | `CommerceLlmLatencyHigh` |
| 8 | **Provider/tool errors** | `ERROR+DEGRADED / total` on `runtime_tool_latency_seconds_count`; `runtime_circuit_state_total{state="OPEN"}` | tool error ratio < 10% | `CommerceToolErrorRateHigh`, `CommerceCircuitOpen` |
| 9 | **Budget exhaustion** | `increase(runtime_budget_stop_total[1h])` by dimension | < 10/h | `CommerceBudgetExhaustionSpike` |
| 10 | **Queue age** | `max(runtime_run_queue_age_seconds)` (oldest PENDING run) | ≤ 900s | `CommerceRunQueueAgeHigh` |

## Metric catalogue (all low-cardinality)

| Metric | Type | Labels | Notes |
|--------|------|--------|-------|
| `http_server_requests_seconds` | timer | `method`, `uri`, `status`, `outcome` | Spring MVC; `uri` is templated, bounded |
| `runtime_run_completed_total` | counter | `outcome` | success / failed / awaiting_approval |
| `runtime_degraded_total` | counter | `agent_name`, `reason` | bounded agent set + reason enum |
| `runtime_evidence_stale_total` | counter | `tool_name` | bounded tool set |
| `runtime_approval_wait_total` | counter | – | run id NOT a label (on span only) |
| `runtime_budget_stop_total` | counter | `dimension` | tokens / cost / wall-clock |
| `runtime_llm_latency_seconds` | timer | `agent_name`, `llm_model` | |
| `runtime_llm_requests/prompt.tokens/completion.tokens/cost.micros` | counter | `agent_name` | |
| `runtime_tool_latency_seconds` | timer | `tool_name`, `status` | |
| `runtime_circuit_state_total` | counter | `circuit`, `state` | |
| `runtime_run_queue_age_seconds` | gauge | – | oldest unclaimed durable run age |

### High-cardinality guard

`MetricsConfig#highCardinalityTagStripper` is a Micrometer `MeterFilter` that strips
`run.id`, `retailer.id`, `tenant.id`, `correlation.id` (and their snake/camel
variants) from **every** meter before registration, as defence in depth. Run and
retailer identifiers are attached to trace spans as high-cardinality attributes so
they remain available for debugging without polluting the metric time-series space.
