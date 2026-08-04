# Multi-Agent E-Commerce Operations Platform 🛒

> Three specialist AI agents — Inventory, Pricing, Sentiment — working in parallel, synthesised by a Coordinator Agent into a daily executive briefing. Built with Java 21, Spring Boot, and Azure OpenAI / GitHub Models.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-brightgreen)](https://spring.io/projects/spring-boot)
[![Multi-Agent](https://img.shields.io/badge/Architecture-Multi--Agent-purple)](https://github.com/YOUR-HANDLE/multi-agent-commerce)

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
| Framework | Spring Boot 3.4.6, Java 21 |
| Concurrency | Java 21 virtual threads (`spring.threads.virtual.enabled=true`) for request handling; a dedicated `commerce-agent-` task executor (configured under `spring.task.execution` in `application.yml`) for parallel agent dispatch |
| LLM | Azure OpenAI GPT-4o, or GitHub Models (configurable via `llm.provider`) |
| Data | Synthetic in-memory data (see below) — no external database |
| Resilience | Resilience4j circuit breaker + retry around LLM and data-tool calls |
| Scheduling | Spring `@Scheduled` (`AgentCoordinator.scheduledDailyAnalysis`), cron driven by `briefing.cron` (default `0 0 6 * * *` — 06:00 daily) |
| Observability | Micrometer + Prometheus (`/actuator/prometheus`), structured JSON logging via Logstash encoder |

## Simulated Data & Current Limitations

This is a portfolio/demo project — it intentionally has no external data dependencies so it runs anywhere with just an LLM credential. Be aware of what's real vs. simulated:

- **InventoryAgent** reads from `InventoryDataTool`, which returns a **hardcoded, deterministic synthetic dataset** of 20 SKUs (no warehouse/ERP system is connected). The Resilience4j circuit breaker and retry annotations on the tool model what a production WMS integration would look like, but today there's no live external call behind them — the "circuit" never has a reason to open.
- **PricingAgent** does **not** call any real competitor-pricing or market-data feed. It asks the LLM to extrapolate "competitor undercutting" risk, margin impact, and seasonal pricing opportunities from just the current date and a retailer ID. This is useful for demonstrating prompt design and agent orchestration, but the pricing analysis is **LLM-extrapolated narrative, not data-backed**. Treat its output as illustrative, not decision-grade.
- **SentimentAgent** similarly does not ingest real reviews, support tickets, or social mentions. It asks the LLM to generate plausible sentiment themes from the date and retailer ID alone.
- **No persistence layer.** There is no MongoDB, Elasticsearch, Postgres, or any other datastore in this project — all data is generated in memory per request.
- **No REST API.** There is currently no controller exposing an HTTP endpoint to trigger analysis on demand. The only entry point is the `@Scheduled` job in `AgentCoordinator`. The empty `api` package is a placeholder for a future on-demand trigger endpoint.

In a production version, `InventoryDataTool` would be backed by a real WMS/ERP integration, `PricingAgent` would call a competitor pricing API, and `SentimentAgent` would query a reviews/support-ticket store — at which point the existing Resilience4j circuit breakers and retries would be protecting genuine external calls instead of in-memory data generation.

## Quick Start

This is a Gradle project (no Maven). The Gradle wrapper is included, so you don't need Gradle installed locally.

```bash
export AZURE_OPENAI_ENDPOINT=https://YOUR-RESOURCE.openai.azure.com
export AZURE_OPENAI_API_KEY=your-key
# — or, to use GitHub Models instead of Azure OpenAI —
export LLM_PROVIDER=github_models
export GITHUB_TOKEN=your-github-token

./gradlew bootRun
```

The daily briefing runs automatically on the schedule defined by `briefing.cron` (default `0 0 6 * * *`, i.e. 06:00 daily — see `application.yml`). In the `dev` profile (`application-dev.yml`), it's overridden to run every minute for fast local iteration. There is currently no on-demand HTTP trigger; to run an ad-hoc analysis locally, call `AgentCoordinator.runDailyAnalysis(...)` directly (e.g. from a test or a temporary `CommandLineRunner`).

### Running tests

```bash
./gradlew test
```
