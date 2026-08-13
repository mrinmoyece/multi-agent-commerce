# Multi-Agent E-Commerce Operations Platform

> Three specialist AI agents — Inventory, Pricing, Sentiment — working in parallel, synthesised by a Coordinator Agent into a daily executive briefing. Built with Java 21, Spring Boot, and Azure OpenAI / GitHub Models.

[![CI](https://github.com/mrinmoyece/multi-agent-commerce/actions/workflows/ci.yml/badge.svg)](https://github.com/mrinmoyece/multi-agent-commerce/actions/workflows/ci.yml)
[![CodeQL](https://github.com/mrinmoyece/multi-agent-commerce/actions/workflows/codeql.yml/badge.svg)](https://github.com/mrinmoyece/multi-agent-commerce/actions/workflows/codeql.yml)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen)](https://spring.io/projects/spring-boot)

## Architecture — Multi-Agent Orchestration

```
┌─────────────────────────────────────────────────────────────────┐
│                    AgentCoordinator                             │
│                                                                 │
│   Dispatches all 3 agents in PARALLEL                          │
│   Synthesises cross-agent insights                              │
│   Generates daily executive briefing                            │
└─────────────┬──────────────────────┬──────────────────┬────────┘
              │                      │                  │
              ▼                      ▼                  ▼
┌─────────────────┐  ┌──────────────────┐  ┌────────────────────┐
│  InventoryAgent │  │   PricingAgent   │  │  SentimentAgent    │
│                 │  │                  │  │                    │
│ • Stock levels  │  │ • Price vs comp. │  │ • Review analysis  │
│ • Reorder flags │  │ • Margin trends  │  │ • Support tickets  │
│ • Overstock     │  │ • Promo opps     │  │ • Social mentions  │
│   clearance     │  │ • Price alerts   │  │ • NPS trends       │
└────────┬────────┘  └────────┬─────────┘  └─────────┬──────────┘
         │                    │                       │
         └────────────────────┼───────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Daily Briefing │
                    │                 │
                    │ Cross-insights  │
                    │ Top 5 actions   │
                    │ Urgent alerts   │
                    │ Exec summary    │
                    └─────────────────┘
```

## Sample Daily Briefing Output

```
📊 DAILY COMMERCE BRIEFING — 17 Jun 2026 | Acme Retail UK

🔗 CROSS-AGENT INSIGHTS
• SKU-4421 (Running Shoes, Blue, Size 9): LOW STOCK (3 days) + TRENDING in 
  positive reviews + COMPETITOR price 15% higher → Restock immediately, 
  hold current price
• SKU-8832 (Winter Coats): OVERSTOCKED (120 days) + NEGATIVE sentiment 
  (sizing complaints) + PRICE ABOVE competitor → Markdown 20%, address 
  sizing in product description

⚠️ URGENT ALERTS
• 3 SKUs at zero stock with active promotions — customer orders failing
• Competitor dropped price on electronics category by 18% at 06:00 today

✅ TODAY'S TOP 5 ACTIONS
1. Emergency restock request for SKU-4421 (Running Shoes) — 3 days left
2. Price match trigger for electronics — review 23 affected SKUs
3. Escalate sizing complaints for Winter Coat range to buying team  
4. Clear overstock on Summer range — suggest 30% promotional email
5. Fix broken promotion on SKU-1102 — customers ordering but stock = 0

📋 EXECUTIVE SUMMARY
Strong demand signals in footwear and activewear require immediate inventory 
action to avoid stockout during peak season. Electronics pricing pressure 
from a competitor needs a response by EOD. Customer sentiment is trending 
positively overall (NPS +4 vs last week) despite isolated sizing issues 
in outerwear.
```

> Note: the sample above illustrates the *shape* of output the LLM is prompted to produce. See [Simulated Data & Current Limitations](#simulated-data--current-limitations) for what's actually backing each agent today.

## Key Design Decisions

**Specialist agents, not a single generalist** — Each agent has a narrow domain and deep tooling for it. The Inventory Agent doesn't know about pricing; the Pricing Agent doesn't care about sentiment. Clean separation means each agent is simpler, cheaper to run, and easier to test.

**Parallel execution** — All three agents are dispatched concurrently from `AgentCoordinator` via `CompletableFuture.supplyAsync`, running on the project's dedicated `commerce-agent-` thread pool (see [Tech Stack](#tech-stack)) instead of the JVM's default common pool. Each agent call is I/O-bound (an LLM API round-trip), so dispatching them in parallel keeps total wall-clock time close to the slowest single agent instead of the sum of all three sequential calls.

**Coordinator as synthesiser, not supervisor** — The coordinator doesn't micromanage agents or review their work mid-flight. It dispatches, waits via `CompletableFuture.allOf(...).join()`, then synthesises. This is a deliberate choice — supervisory loops add latency and complexity.

**Cross-agent insights are the value** — Individual agent outputs are useful. The *combined* view — low stock + positive sentiment + competitor pricing opportunity — is where real commercial value is generated. That's what the coordinator synthesises.

## Tech Stack

| Concern | Technology |
|---------|-----------|
| Framework | Spring Boot 3.5.16, Java 21 LTS |
| Concurrency | Java 21 virtual threads (`spring.threads.virtual.enabled=true`) for request handling; a dedicated `commerce-agent-` task executor (configured under `spring.task.execution` in `application.yml`) for parallel agent dispatch |
| LLM | Azure OpenAI GPT-4o, or GitHub Models (configurable via `llm.provider`) |
| Data | PostgreSQL (Flyway + Spring Data JPA) for durable runs, results, approvals, audit events, and idempotency keys; agent *inputs* remain synthetic/LLM-extrapolated (see below) |
| Coordination | Redis distributed lock, run-status cache, and rate limiting (in-memory stand-ins under `dev`/`test`) |
| API & security | Versioned REST API under `/api/v1`; OAuth2/OIDC JWT resource server with scope/role method security and RFC 9457 Problem Details |
| Resilience | Resilience4j circuit breaker + retry, 30-second transport timeout, bounded LLM responses |
| Scheduling | `DailyAnalysisScheduler` creates one durable run per retailer/date (cron `briefing.cron`, default `0 0 6 * * *`); it is executed exactly once across replicas via a Redis lock + optimistic-locking state machine |
| Observability | Micrometer + Prometheus on the internal management port (`8081`), structured JSON logging |
| Supply chain | Dependency locks/checksums, CycloneDX SBOM, Trivy, pinned Actions and container bases |

## Simulated Data & Current Limitations

This is a portfolio/demo project — it intentionally has no external data dependencies so it runs anywhere with just an LLM credential. Be aware of what's real vs. simulated:

- **InventoryAgent** reads from `InventoryDataTool`, which returns a **hardcoded, deterministic synthetic dataset** of 20 SKUs (no warehouse/ERP system is connected). The Resilience4j circuit breaker and retry annotations on the tool model what a production WMS integration would look like, but today there's no live external call behind them — the "circuit" never has a reason to open.
- **PricingAgent** does **not** call any real competitor-pricing or market-data feed. It asks the LLM to extrapolate "competitor undercutting" risk, margin impact, and seasonal pricing opportunities from just the current date and a retailer ID. This is useful for demonstrating prompt design and agent orchestration, but the pricing analysis is **LLM-extrapolated narrative, not data-backed**. Treat its output as illustrative, not decision-grade.
- **SentimentAgent** similarly does not ingest real reviews, support tickets, or social mentions. It asks the LLM to generate plausible sentiment themes from the date and retailer ID alone.
- **Durable persistence is in place.** Analysis runs, per-agent results, approval
  records, audit events, and idempotency keys are stored in PostgreSQL via Flyway
  migrations and Spring Data JPA. What remains synthetic is the *agent input*
  above, not the platform's run state. Credentials and raw provider payloads are
  never persisted.
- **A versioned REST API is available** under `/api/v1` (create runs, get
  status/results, list runs with pagination, list and approve/reject approvals),
  secured by an OAuth2/OIDC JWT resource server. The `@Scheduled`
  `DailyAnalysisScheduler` remains the automated entry point and now creates a
  durable run executed exactly once across replicas.

In a production version, `InventoryDataTool` would be backed by a real WMS/ERP integration, `PricingAgent` would call a competitor pricing API, and `SentimentAgent` would query a reviews/support-ticket store — at which point the existing Resilience4j circuit breakers and retries would be protecting genuine external calls instead of in-memory data generation.

### Layer 2 — grounded agent runtime & offline evals

Layer 2 replaces the narrative-only assumptions above with a typed,
provider-neutral runtime (`com.agentforge.commerce.runtime.*`). Every agent now
fetches a **grounded tool result** and returns an `AgentResultEnvelope` with
cited `Evidence`, calibrated `Confidence`, `Freshness`, the versioned prompt
id+hash, token/cost accounting and an explicit status. Untrusted tool data is
injection-fenced, every output is strict-schema validated (fail-closed), per-run
token/request/latency/cost **budgets** are hard-stopped before provider calls,
and high-impact actions pause for **human-in-the-loop** approval. Grounding runs
on deterministic **fixtures by default** (`runtime.tools.mode=FIXTURE`) — always
labelled non-authoritative, never presented as live — with **live adapter
contracts** ready for production feeds. Durable, retailer-scoped semantic memory
stores only evidence-backed facts with hard tenant isolation. See
[ARCHITECTURE.md](docs/ARCHITECTURE.md) and [LIMITATIONS.md](docs/LIMITATIONS.md).

## Quick Start

This is a Gradle project (no Maven). The Gradle wrapper is included, so you don't need Gradle installed locally.

```bash
export AZURE_OPENAI_ENDPOINT=https://YOUR-RESOURCE.openai.azure.com
export LLM_PROVIDER=azure-openai
export LLM_API_KEY=your-key
# — or, to use GitHub Models instead of Azure OpenAI —
export LLM_PROVIDER=github-models
export LLM_API_KEY=your-github-token

./gradlew bootRun
```

The daily briefing runs automatically on the schedule defined by `briefing.cron`
(default `0 0 6 * * *`, i.e. 06:00 daily — see `application.yml`). In the `dev`
profile (`application-dev.yml`), it's overridden to run every minute for fast
local iteration. You can also trigger an analysis on demand through the REST API
(`POST /api/v1/analysis-runs` with a bearer JWT).

### Running with PostgreSQL and Redis

The base profile requires PostgreSQL and (unless `PLATFORM_REDIS_ENABLED=false`)
Redis. `docker-compose up` starts both alongside the app with the connection
settings wired in. For a pure local run without external infrastructure, use the
`dev` profile, which disables Redis coordination and points at a local database.
Production configuration (datasource, Redis, OAuth issuer/audience) is supplied
via environment variables / Kubernetes Secret and ConfigMap — see the
[runbook](docs/RUNBOOK.md).

### Running tests

```bash
./gradlew check
```

`check` runs tests, the 70% line-coverage gate, Checkstyle, SpotBugs, and the
**offline agentic evaluation gate** (`evalGate`). CI also scans dependencies and
repository history, emits a CycloneDX SBOM, scans the runtime image, and runs
CodeQL.

The evaluation gate is deterministic and offline. Run the fast subset with
`./gradlew evalSmoke` or the full gate with `./gradlew evalGate`; it scores eight
golden scenarios (normal, partial-failure, prompt-injection, stale/contradictory
evidence, budget-exhaustion, malformed-output, approval-required,
tenant-isolation) for groundedness, citation precision, completeness, schema
validity, safety adherence and budget adherence, then fails on any threshold miss
or drift from the committed baseline (`eval/baseline/eval-report.json`).

## Operations and security

- Application traffic uses port `8080`; health and metrics use the internal
  management port `8081`.
- The production manifest runs multiple replicas behind a rolling update.
  Duplicate daily briefings are prevented by a durable per-retailer/date
  idempotency key plus a Redis lock, so the workload scales horizontally.
- Containers run as UID `10001`, without Linux capabilities or service-account
  credentials, with a read-only root filesystem in Kubernetes.
- LLM output is bounded and validated. Coordinator synthesis fails closed if it
  does not match the briefing JSON schema.

See the [documentation map](docs/README.md), [runbook](docs/RUNBOOK.md),
[security model](docs/SECURITY_MODEL.md), and [known limitations](docs/LIMITATIONS.md).
