# ADR 0005: Live adapter trust boundary

- **Status:** Accepted
- **Date:** Layer 3
- **Deciders:** Platform team

## Context

LIVE mode calls external systems of record — WMS inventory, competitor pricing,
and customer sentiment/review APIs — over the network. These are the platform's
grounding sources, so a compromised, malicious, slow or misconfigured upstream
must not corrupt runs, exfiltrate secrets, exhaust resources, or open an SSRF
path. External input crossing into the runtime is an explicit trust boundary.

## Options considered

1. **Trust configured URLs and fetch freely.** Simplest, but permits SSRF,
   unbounded payloads, secret leakage in errors, and lets one slow upstream stall
   the fleet. Rejected.
2. **Generic HTTP client with ad-hoc checks per adapter.** Inconsistent and easy
   to get wrong. Rejected.
3. **A single hardened adapter substrate** enforcing every control uniformly, with
   LIVE mode failing fast on misconfiguration.

## Decision

All LIVE adapters share one hardened HTTP substrate that enforces: HTTPS-only, a
**host allowlist** (no arbitrary/unrestricted fetch, no SSRF), bounded
request/response sizes, connection/read deadlines, mTLS/OAuth2 client-credentials
where configured, pagination limits, strict schema validation, per-adapter
retry + circuit-breaker isolation, correlation-ID propagation, evidence
provenance/freshness stamping, and secret-safe errors/logging. **LIVE mode fails
fast at startup** unless every required endpoint and auth setting is present and
valid — there are no fake production defaults. Evidence from a stale or
unverifiable upstream is marked stale/degraded rather than trusted.

## Consequences

- Upstream compromise/misbehaviour is contained: bounded blast radius, isolated
  circuits, no secret leakage, no SSRF/metadata access (reinforced by the
  NetworkPolicy egress guard).
- Misconfiguration is caught at boot, not at first run in production.
- Every LIVE case is covered by deterministic WireMock contract tests (success,
  auth, timeout, oversized, malformed, partial pagination, stale, retry, circuit
  open, forbidden host).
- Adding an adapter means implementing the contract on the shared substrate, not
  writing bespoke networking — the trust boundary stays uniform.
