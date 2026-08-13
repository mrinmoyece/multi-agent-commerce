# Local Keycloak realm (DEVELOPMENT ONLY)

`commerce-local-realm.json` is imported by the opt-in `local-auth` Docker Compose
profile (see [`docs/LOCAL_DEVELOPMENT.md`](../docs/LOCAL_DEVELOPMENT.md)). It
exists purely so a developer who does **not** already own an OIDC issuer can test
the OAuth2-protected API locally.

**This realm must never be imported into a shared or production Keycloak.**

- The confidential client `commerce-local-client` ships with a **well-known
  placeholder secret** (`local-dev-only-not-a-real-secret`). It is reachable only
  on `127.0.0.1` and only when the `local-auth` profile is explicitly enabled, and
  it grants nothing outside this throwaway local realm.
- The client uses the client-credentials grant and is granted least privilege: the
  three API scopes `runs:read`, `runs:write`, `approvals:write` and the API
  audience `api://multi-agent-commerce` — nothing else.
- No standard browser login, implicit, or direct-access grants are enabled.
- The issuer is pinned to the in-network URL `http://keycloak:8080/realms/commerce-local`
  so token validation works identically from the app container and the host.

Changing scopes/audience here must stay in sync with the resource-server
configuration. This profile adds **no** static auth bypass and does not disable
JWT verification — the app still fully validates issuer, signature, audience, and
scopes.
