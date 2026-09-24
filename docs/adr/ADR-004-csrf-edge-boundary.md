# ADR-004: CSRF edge-only boundary (gateway vs STATELESS services)

**Status:** Accepted  
**Date:** 2026-08-30  
**Inventory verified:** 2026-08-31 (`main` @ `7874eac` / Wave N N1+N3; GitNexus index `e09a4a3`, ~5 behind — **WARN**)  
**Deciders:** Open CRM/CPQ forge (Wave E / E1); PM-authorized architecture `docs/forge/14-wave-e-architecture.md`  
**Related:** Wave D D1 CORS/CSRF allow-list; Wave E **E2 landed** (pricing/pricebook `csrf.disable()`); GitHub Jules/Sentinel [#402](https://github.com/drgaciw/open-crm-cpq/pull/402), [#404](https://github.com/drgaciw/open-crm-cpq/pull/404)

**Canonical path:** `docs/adr/ADR-004-csrf-edge-boundary.md`  
**Stub (old path):** [`docs/ADR-004-csrf-edge-boundary.md`](../ADR-004-csrf-edge-boundary.md)

---

## Context

Browser clients (Angular CRM on `:4200`) call the **API gateway** (`:8080`). The gateway authenticates (JWT in production) and proxies to domain microservices. Domain services are **resource servers**: Bearer / internal-header callers, `SessionCreationPolicy.STATELESS`, no browser session affinity.

CSRF (Double-Submit Cookie) protects **browser cookie / credentialed cross-site** mutating requests. It belongs at the **edge** where browsers terminate — not on every mesh hop.

### Inventory (tree evidence @ 2026-08-31 — E2 **LANDED**)

`rg csrf **/SecurityConfig.java` + `rg CookieCsrfTokenRepository|CookieServerCsrfTokenRepository`:

| CSRF posture | Services |
|--------------|----------|
| **Edge CSRF ON** (browsers) | `gateway-service` only — reactive `CookieServerCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfProtectionFilter` (D1 explicit Origin/Referer allow-list; no `*` / `*.` wildcards) |
| **CSRF disabled + STATELESS** (domain — **all** servlet `SecurityConfig`) | `crm-service`, `quote-service`, `product-service`, **`pricing-service`**, **`pricebook-service`**, `approval-routing`, `configuration-engine`, `contract-asset-management`, `document-generation` |
| **Servlet `CookieCsrfTokenRepository`** | **EMPTY** — no hits under `backend/` except comments |
| **No `SecurityConfig`** | `ai-service`, `communication-service` (N/A for this inventory) |

**E2 complete:** pricing-service and pricebook-service match CRM/quote (`csrf.disable()` + `STATELESS`). The Wave E baseline row “Cookie CSRF ON + STATELESS anomaly” is **obsolete**.

CRM / pricing / pricebook `SecurityConfig` Javadoc: gateway authenticates; browser CSRF at the edge; this STATELESS resource server disables servlet Cookie CSRF so Bearer / mesh callers are not rejected for missing XSRF tokens.

GitNexus (`-r /home/username01/Projects/open-crm-cpq --file <path>`): `CsrfProtectionFilter` **UNKNOWN** (0 upstream, Spring `WebFilter` wiring). `query CSRF` returns **definitions** (gateway + domain `SecurityConfig`, filter + test) — not a call graph. **UNKNOWN ≠ unused.** Confirm with `rg` (this inventory) before any `SecurityConfig` edit.

Jules PRs **#402** / **#404** proposed enabling Cookie CSRF across microservices that disable it. That approach remains **wrong** for this architecture (see Decision).

---

## Decision

**Browser Cookie CSRF is enforced only at the gateway edge.**

1. **Gateway** keeps Cookie CSRF (`CookieServerCsrfTokenRepository` + `CsrfProtectionFilter`) with the explicit CORS/CSRF origin allow-list (`FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` — see [CRM_GATEWAY_ENV.md](../CRM_GATEWAY_ENV.md)).
2. **Domain services** intentionally use **`csrf.disable()`** and **`SessionCreationPolicy.STATELESS`**. Downstream hops are Bearer / service-mesh, not browser Double-Submit Cookie flows.
3. **Reject** merging Jules **#402** / **#404** as written (enable-all Cookie CSRF on STATELESS JWT microservices behind the gateway).
4. **E2 landed:** `pricing-service` and `pricebook-service` use the same `csrf.disable()` + `STATELESS` posture. `CookieCsrfTokenRepository` / `CookieServerCsrfTokenRepository` in `**/SecurityConfig.java` is **gateway-only**.

Auth model is unchanged this ADR: gateway JWT/prod auth; services `.authenticated()` on `/api/v1/**`; health/docs public. Full Azure AD B2C resource-server rollout is out of scope.

> **Update 2026-09-23 ([ADR-016](ADR-016-identity-authentication-service-trust.md) step 3).** The gateway is now a backend-for-frontend: the browser holds only a session cookie, so this ADR's cookie CSRF is load-bearing, as ADR-016 decision 2 said it would be.
> - The gateway checks the CSRF token on every state-changing request that does **not** carry `Authorization: Bearer`. A bearer request (agents, ai-service's agent tools) is authenticated by its token, not by a cookie, so it is exempt. A cross-site page cannot add that header without a CORS preflight, which the allow-list refuses.
> - `CsrfProtectionFilter` (the Origin/Referer allow-list) is unchanged. `POST /logout` needs the XSRF token too.
> - The auth-model sentence above is superseded by ADR-016: every service is a resource server, and Azure AD B2C is not used.

---

## Options considered

### Option A: Gateway-only browser CSRF (Selected)

| Dimension | Assessment |
|-----------|------------|
| Browser protection | High — FE hits gateway; cookie CSRF + Origin/Referer checks at edge |
| Gateway→service hops | High — Bearer/internal headers; no XSRF cookie requirement |
| Operational complexity | Low — single CSRF enforcement point; FE XSRF wiring only for `:8080` |
| Consistency with STATELESS | High — domain services have no browser session |

**Pros:** Matches how the SPA and gateway are wired; avoids 403s when cookies are not forwarded on internal hops; aligns with CRM Javadoc intent.

**Cons:** Services reached **directly** by a browser (bypass gateway) would not get Cookie CSRF — that bypass is not a supported FE path; integrators must not call domain ports with browser cookies.

### Option B: Enable Cookie CSRF on every microservice (Jules #402 / #404 — Rejected)

| Dimension | Assessment |
|-----------|------------|
| Browser protection | No incremental gain — browsers already terminate at gateway CSRF |
| Gateway→service hops | High risk of **403** if XSRF cookies/headers are absent (Bearer-only clients, missing cookie forward) |
| FE wiring | Would require XSRF against every service port — FE uses gateway only |
| STATELESS + Cookie CSRF | Confused half-measure — cookie token repo without browser session affinity |

**Rejected.** Blind enable-all Cookie CSRF on STATELESS services behind a CSRF-capable gateway does not improve the real browser threat model and breaks internal/Bearer callers.

### Option C: Defense-in-depth Cookie CSRF on domain services *and* gateway

Same failure modes as Option B unless every internal client is taught Double-Submit Cookie semantics. Rejected for current JWT/mesh model.

---

## Consequences

### Positive

- Single documented CSRF boundary for security review and Sentinel closeout.
- E2 restored `csrf.disable()` on pricing/pricebook; gateway CSRF remains ON.
- E4 hygiene can close #402 / #404 with a pointer to this ADR + E2, not merge-as-written.

### Negative / follow-ups

- Direct browser access to domain ports (`:8082` pricing, `:8089` pricebook, etc.) is **unsupported**. Integrators posting with browser cookies and XSRF headers (gateway bypass) will not get Cookie CSRF. FE `proxy.conf` / data-access target gateway `:8080` only (`rg` FE `xsrf`/`csrf`/`withCredentials`: 0).
- GitNexus cannot resolve Spring `SecurityFilterChain` callers — treat `SecurityConfig` impact **UNKNOWN**; always `rg` before edits.

### Explicit non-goals

- Do not implement Activities API, S2B-11 shim, or S2C-12 in the CSRF workstream.
- Do not enable Cookie CSRF on crm/quote/product/pricing/pricebook/etc. “for parity with Jules.”
- Do not reintroduce servlet `CookieCsrfTokenRepository` on domain services.

---

## Acceptance criteria (E1) + E2 closeout

- [x] This ADR states gateway-only browser Cookie CSRF.
- [x] Documents why domain-service `csrf.disable()` + `STATELESS` is intentional.
- [x] Explicitly rejects Jules #402 / #404 enable-all approach.
- [x] Lists service inventory (**post-E2:** gateway ON; **all** domain `SecurityConfig` disable; servlet cookie repo empty).
- [x] Cross-linked from [CRM_GATEWAY_ENV.md](../CRM_GATEWAY_ENV.md).
- [x] Forge status note [docs/forge/impl/e1-csrf-adr.md](../forge/impl/e1-csrf-adr.md); E2 note [e2-align-csrf-disable.md](../forge/impl/e2-align-csrf-disable.md).
- [x] **E2 landed** — pricing/pricebook `csrf.disable()` + `STATELESS`.

---

## References

- `docs/forge/14-wave-e-architecture.md` — Wave E decisions and PR DAG  
- `docs/forge/impl/d1-gateway-cors-csrf.md` — D1 allow-list (no `*.azurewebsites.net`)  
- `docs/forge/impl/d3-security-closeout.md` — left #402/#404 out of D3 closeout  
- `docs/forge/impl/e2-align-csrf-disable.md` — pricing/pricebook alignment **done**  
- `backend/services/gateway-service/.../SecurityConfig.java` — edge CSRF ON  
- `backend/services/crm-service/.../SecurityConfig.java` — intentional `csrf.disable()` + STATELESS  
- `backend/services/pricing-service/.../SecurityConfig.java` — same (E2)  
- `backend/services/pricebook-service/.../SecurityConfig.java` — same (E2)  
