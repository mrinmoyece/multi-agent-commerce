# ADR 0004: Deterministic offline evaluation strategy

- **Status:** Accepted
- **Date:** Layer 2
- **Deciders:** Platform team

## Context

An agentic platform whose behaviour depends on a nondeterministic LLM still needs
a fast, reliable gate in CI that fails a change which breaks grounding, fencing,
budgets, schema handling, risk gating or tenant isolation. Calling a real model in
CI is nondeterministic, slow, costly, and flaky — unsuitable as a required gate.

## Options considered

1. **Live-model evaluation in CI.** Highest fidelity to production generation
   quality, but nondeterministic and non-reproducible; cannot be a blocking gate.
   Rejected as the *gate* (still valuable as an offline, non-blocking experiment).
2. **No evaluation gate.** Fast, but regressions in runtime wiring ship silently.
   Rejected.
3. **Deterministic offline evaluation.** Drive the real engine with a scripted,
   network-free provider that returns fixed responses, and assert on the runtime's
   handling — a reproducible, offline, blocking gate.

## Decision

`evalGate` runs the real runtime against a scripted provider (no network) over a
committed scenario set and asserts deterministic properties: evidence is cited,
ungrounded output is fenced/degraded, budgets stop runs, malformed schemas are
rejected, high-risk actions gate to approval, and tenants stay isolated. It is a
required CI gate with a fixed pass threshold that may only be raised, never
lowered. Model/prompt quality is evaluated separately and out of band (governance/
PROMPT_EVAL_RELEASE.md).

## Consequences

- CI has a fast, deterministic, offline signal that runtime invariants hold.
- The gate validates **wiring and safety**, explicitly *not* real generation
  quality — stated in LIMITATIONS to avoid overclaiming.
- Perf and resilience gates follow the same deterministic-offline philosophy.
- Thresholds are ratcheted up over time; weakening one is a reviewed change.
