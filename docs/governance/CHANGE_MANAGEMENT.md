# Model / provider change management

Changing the model, provider, region, or provider-side configuration changes
production behaviour even when no application code changes. Such changes are
managed, not ad hoc.

## Scope

Applies to: Azure OpenAI deployment/model version, API version, region/endpoint,
token/latency budgets, and adapter upstream endpoints or auth modes.

## Process

1. **Propose** — record the change, its motivation, and expected impact
   (behaviour, cost, latency).
2. **Evaluate offline** — run the deterministic `evalGate` (must stay green with
   no threshold weakening) and any out-of-band model-quality evaluation
   (PROMPT_EVAL_RELEASE.md) against the candidate.
3. **Review** — a second person approves; budget/threshold loosening requires
   explicit sign-off and a recorded rationale.
4. **Stage** — roll to staging by digest; observe SLIs (groundedness, provider
   latency/errors, budget exhaustion) for a soak window.
5. **Promote** — promote the **same** artifact/config to prod by digest with
   environment protection; never rebuild between environments.
6. **Record** — capture the change in the release metadata and CHANGELOG.

## Guardrails

- Budgets and safety thresholds may be **raised** freely but only **lowered** via
  reviewed change with rationale.
- A provider change never bypasses schema validation, grounding, or HITL policy.
- Rollback is redeploying the previous digest/config; provider-side changes have a
  documented revert (previous deployment/version pinned).

## Rollback triggers

Groundedness or run-success SLO fast-burn, a spike in `degraded-runs`,
`provider-latency`, or `budget-exhaustion` after a change → revert to the prior
pinned model/config immediately, then diagnose.
