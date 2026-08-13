# ADR 0003: Human-in-the-loop approval policy

- **Status:** Accepted
- **Date:** Layer 2
- **Deciders:** Platform team

## Context

Some recommended actions are high-impact (e.g. large price moves, inventory
reallocation) and must not be executed autonomously. We need a policy that pauses
those actions for a human decision without blocking low-risk output, and that is
durable across restarts and replicas.

## Options considered

1. **Autonomous everything.** Fastest, but unacceptable risk for high-impact
   actions and unauditable. Rejected.
2. **Approve everything.** Safe but defeats the point of an agentic platform and
   creates an approval backlog for trivial actions. Rejected.
3. **Risk-tiered approval.** Classify each recommended action by risk; auto-allow
   low risk, require explicit human approval for high risk, and record the
   decision.

## Decision

Recommended actions are risk-classified during the run. Actions above the risk
threshold transition the run to `AWAITING_APPROVAL` and persist an approval
request; the run resumes only after a recorded human decision (approve/reject),
which is appended to the audit trail. Approvals are durable (ADR 0001), so a
paused run survives restarts and can be decided by any operator. Rejection is a
terminal, audited outcome — the action is never silently dropped.

## Consequences

- High-impact actions cannot execute without an attributable human decision.
- Approval latency becomes an SLI (`approval-backlog` alert) so backlogs are
  visible and on-call can act.
- The risk threshold is change-managed (governance/CHANGE_MANAGEMENT.md); loosening
  it is a reviewed decision, not a code tweak.
- Adds operational load proportional to the volume of high-risk recommendations;
  acceptable given the blast radius they carry.
