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
Daily briefing runs via `@Scheduled(cron="${briefing.cron}")` in AgentCoordinator.
There is no HTTP trigger in the current application.

## PR Standards
- [ ] New agent implements CommerceAgent interface
- [ ] New agent added to coordinator's parallel execution block
- [ ] New agent findings included in synthesis prompt
- [ ] Unit test mocks LLM and verifies agent message structure
- [ ] Coordinator test verifies synthesis runs even when one agent fails
