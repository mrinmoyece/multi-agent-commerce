# Prompt & eval release process

Prompts and evaluation scenarios are versioned artifacts with their own release
discipline, separate from model/provider changes (CHANGE_MANAGEMENT.md).

## Principles

- Prompts are code: reviewed, versioned in git, and shipped by digest with the
  application image — never hot-edited in production.
- The evaluation set is the contract. A prompt change is acceptable only if the
  deterministic `evalGate` stays green and out-of-band quality evaluation does not
  regress.
- Eval thresholds ratchet up, never down (ADR 0004). Lowering a threshold is a
  reviewed, justified exception.

## Prompt change flow

1. Edit the prompt; open a PR describing intent and expected behavioural change.
2. `evalGate` (deterministic, offline) must pass — it proves grounding, fencing,
   budget, schema and risk-gating invariants still hold.
3. Run the out-of-band model-quality evaluation on representative scenarios;
   attach results to the PR.
4. Review + approve; merge builds a new signed image.
5. Promote by digest through staging → prod with a soak window, watching
   groundedness and `degraded-runs`.

## Eval set change flow

- **Adding** scenarios or **raising** thresholds: normal review; encouraged.
- **Weakening** a scenario or **lowering** a threshold: requires explicit sign-off
  and a recorded rationale in the PR; treated as an exception.
- New scenarios must be deterministic and network-free to remain a valid gate.

## Rollback

Revert to the previous prompt/eval digest. Because both ship with the image,
rollback is the same digest redeploy as any other application rollback.
