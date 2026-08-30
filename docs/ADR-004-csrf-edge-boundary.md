# ADR-004: CSRF edge-only boundary (gateway vs STATELESS services)

**Status:** Accepted  
**Date:** 2026-08-30  
**Deciders:** Open CRM/CPQ forge (Wave E / E1); PM-authorized architecture `docs/forge/14-wave-e-architecture.md`  
**Related:** Wave D D1 CORS/CSRF allow-list; Wave E E2 (pricing/pricebook `csrf.disable()` alignment); GitHub Jules/Sentinel [#402](https://github.com/drgaciw/open-crm-cpq/pull/402), [#404](https://github.com/drgaciw/open-crm-cpq/pull/404)

---

## Context

Browser clients (Angular CRM on `:4200`) call the **API gateway** (`:8080`). The gateway authenticates (JWT in production) and proxies to domain microservices. Domain services are **resource servers**: Bearer / internal-header callers, `SessionCreationPolicy.STATELESS`, no browser session affinity.

CSRF (Double-Submit Cookie) protects **browser cookie / credentialed cross-site** mutating requests. It belongs at the **edge** where browsers terminate — not on every mesh hop.

### Inventory (tree evidence @ Wave E baseline)

| CSRF posture | Services |
|--------------|----------|
| **Edge CSRF ON** (intended for browsers) | `gateway-service` — reactive `CookieServerCsrfTokenRepository` + `CsrfProtectionFilter` (D1 explicit Origin/Referer allow-list) |
| **Cookie CSRF ON + STATELESS** (anomaly; align in E2) | `pricing-service`, `pricebook-service` — servlet `CookieCsrfTokenRepository.withHttpOnlyFalse()` |
| **CSRF disabled + STATELESS** (intended for domain) | `crm-service`, `quote-service`, `product-service`, `approval-routing`, `configuration-engine`, `contract-asset-management`, `document-generation` |

CRM `SecurityConfig` documents the model: gateway authenticates; the service validates gateway origin / JWT context and keeps `csrf.disable()` + `STATELESS`.

Jules PRs **#402** / **#404** proposed enabling Cookie CSRF across microservices that currently disable it. That approach is wrong for this architecture (see Decision / Consequences).

---

## Decision

**Browser Cookie CSRF is enforced only at the gateway edge.**

1. **Gateway** keeps Cookie CSRF (`CookieServerCsrfTokenRepository` + `CsrfProtectionFilter`) with the explicit CORS/CSRF origin allow-list (`FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` — see [CRM_GATEWAY_ENV.md](./CRM_GATEWAY_ENV.md)).
2. **Domain services** intentionally use **`csrf.disable()`** and **`SessionCreationPolicy.STATELESS`**. Downstream hops are Bearer / service-mesh, not browser Double-Submit Cookie flows.
3. **Reject** merging Jules **#402** / **#404** as written (enable-all Cookie CSRF on STATELESS JWT microservices behind the gateway).
4. **E2** (separate PR) aligns `pricing-service` and `pricebook-service` to the same `csrf.disable()` + `STATELESS` posture as CRM/quote/product. After E2, `CookieCsrfTokenRepository` / `CookieServerCsrfTokenRepository` in `**/SecurityConfig.java` must be **gateway-only**.

Auth model is unchanged this ADR: gateway JWT/prod auth; services `.authenticated()` on `/api/v1/**`; health/docs public. Full Azure AD B2C resource-server rollout is out of scope.

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
- Clear acceptance for E2: restore `csrf.disable()` on pricing/pricebook; leave gateway CSRF ON.
- E4 hygiene can close #402 / #404 with a pointer to this ADR + E2, not merge-as-written.

### Negative / follow-ups

- Until E2 merges, pricing/pricebook remain the Cookie CSRF + STATELESS anomaly.
- Any external integrator posting **directly** to pricing (`:8082`) / pricebook (`:8089`) with browser cookies and XSRF headers (gateway bypass) would see CSRF expectations change after E2. No such client found in `frontend/` (FE uses gateway `:8080`). Document that **direct browser access to domain ports is unsupported**.

### Explicit non-goals

- Do not implement Activities API, S2B-11 shim, or S2C-12 in the CSRF workstream.
- Do not enable Cookie CSRF on crm/quote/product/etc. “for parity with Jules.”

---

## Acceptance criteria (E1)

- [x] This ADR states gateway-only browser Cookie CSRF.
- [x] Documents why domain-service `csrf.disable()` + `STATELESS` is intentional.
- [x] Explicitly rejects Jules #402 / #404 enable-all approach.
- [x] Lists service inventory (gateway ON; majority disable; pricing/pricebook anomaly → E2).
- [x] Cross-linked from [CRM_GATEWAY_ENV.md](./CRM_GATEWAY_ENV.md).
- [x] Forge status note [docs/forge/impl/e1-csrf-adr.md](./forge/impl/e1-csrf-adr.md).

---

## References

- `docs/forge/14-wave-e-architecture.md` — Wave E decisions and PR DAG  
- `docs/forge/impl/d1-gateway-cors-csrf.md` — D1 allow-list (no `*.azurewebsites.net`)  
- `docs/forge/impl/d3-security-closeout.md` — left #402/#404 out of D3 closeout  
- `backend/services/gateway-service/.../SecurityConfig.java` — edge CSRF ON  
- `backend/services/crm-service/.../SecurityConfig.java` — intentional `csrf.disable()` + STATELESS  
