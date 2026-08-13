# Tenant isolation verification

Tenant isolation is a correctness property, not a best effort. Every request and
run is scoped to a single tenant, and no code path may read or write another
tenant's data.

## Controls

- **Authenticated tenant scope.** The OAuth2 resource server establishes the
  tenant from validated JWT claims; the tenant scope is derived from the token,
  never from a client-supplied body field.
- **Query scoping.** Persistence queries for runs, results, approvals and memory
  are filtered by tenant/retailer scope; there is no unscoped "all tenants" read
  path in the request flow.
- **Memory isolation.** Semantic memory is written and read only within a single
  tenant scope (ADR 0002); cross-tenant reads are structurally impossible.
- **No tenant IDs as metric labels.** Tenant/retailer/run identifiers are
  high-cardinality and are stripped from metrics (`MetricsConfig` MeterFilter),
  so isolation is not weakened by observability.

## Verification

Isolation is verified by automated tests, not assertion:

- The deterministic `evalGate` includes a tenant-isolation scenario asserting one
  tenant cannot influence or observe another's run.
- Service/persistence tests assert scoped queries reject or exclude foreign-tenant
  rows.
- Security tests assert the tenant scope comes from the validated token and that a
  forged/absent claim is rejected.

Any change that adds a data path touching runs, results, approvals or memory MUST
add or extend a test proving the path stays tenant-scoped before it can merge.

## Audit

The append-only audit trail records the tenant scope of every state transition, so
cross-tenant access — if it ever occurred — would be detectable after the fact.
