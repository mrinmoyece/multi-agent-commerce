# ADR 0002: Semantic memory trust model

- **Status:** Accepted
- **Date:** Layer 2
- **Deciders:** Platform team

## Context

The runtime can carry learned context ("semantic memory") across runs to improve
recommendations. Memory is powerful but dangerous: unbounded or cross-tenant
memory becomes an injection and data-leak vector, and stale or unattributed
memory silently degrades groundedness. We must decide what memory is trusted
enough to influence a run.

## Options considered

1. **No memory.** Safe but throws away useful context.
2. **Free-form memory** — store any model output and replay it. Maximises recall
   but has no provenance, no tenant boundary, and lets one run poison the next.
   Rejected.
3. **Provenance-scoped, tenant-isolated, TTL-bounded memory.** Every entry
   records its source, is scoped to a single tenant, is deduplicated, and expires.

## Decision

Memory entries are **trusted only when** they (a) carry provenance identifying
the source evidence/run, (b) are scoped to exactly one tenant and never read
across tenants, (c) pass dedup, and (d) are within TTL. Memory is treated as
*hint* context, not authoritative evidence: it can inform but never substitutes
for freshly grounded tool evidence, and it is fenced through the same schema and
grounding checks as any other model-visible input.

## Consequences

- Cross-tenant leakage is structurally prevented; tenant scoping is verified by
  tests (see governance/TENANT_ISOLATION.md).
- Memory cannot fabricate grounding: recommendations still require live/fixture
  evidence, and stale memory cannot mark a run grounded.
- Eviction/expiry are best-effort at read/write time, not a continuous reaper —
  documented in LIMITATIONS.
- Prompt-injection surface is reduced because untrusted, unattributed text never
  becomes trusted memory.
