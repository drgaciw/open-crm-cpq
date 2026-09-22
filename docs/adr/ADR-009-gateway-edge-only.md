# ADR-009: Gateway is the single edge — routing, aliasing, and resilience only (no domain logic)

**Status:** Accepted *(reverse-engineered from code 2026-09-19 — partially implied by ADR-004 and the configuration-API reconciliation ADR, but the general edge principle was never recorded as its own decision)*
**Date (decision embedded):** ~2026-08 (foundation waves; extended by S2B-11 Wave F) · **Documented:** 2026-09-19
**Deciders:** forge foundation + Wave F (de facto) · **Documented by:** architecture reverse-engineering pass
**Provenance:** Reverse-engineered ADR. Generalizes [ADR-004](ADR-004-csrf-edge-boundary.md) (CSRF at edge) and the [backlog configuration ADR](../backlog/ADR-configuration-api-reconciliation.md) (gateway route+adapt for validate) into the umbrella edge rule the code already follows.

---

## Context

All browser traffic terminates at `gateway-service` (`:8080`). The gateway accumulates legitimate edge responsibilities (routing, CSRF, CORS, rate limiting, JWT, circuit breaking) and is repeatedly tempted to absorb domain behavior (e.g., inline configuration validation) because it sits "in the middle" of agent flows.

Verified on the tree (2026-09-19), `backend/services/gateway-service/src/main/resources/application.yml`:

- Spring Cloud Gateway **5 / Boot 4** route model — routes/filters registered under **`spring.cloud.gateway.server.webflux.*`** (the old `spring.cloud.gateway.routes` root silently yields zero routes and API 404s through the proxy).
- **16 route groups** in the default profile (plus a mirrored dev profile): crm, pricing, pricebook, product, quote, configuration-engine, approval-routing, document-generation, contract-asset, ERP **stub**, communication (`lb://`), ai-service (`lb://`), and **CPQ alias routes** for each — `/api/v1/cpq/{segment}/**` → `RewritePath` → `/api/v1/{segment}/**`.
- **S2B-11 shim:** `POST /api/v1/configuration/validate` (and `/api/v1/cpq/configuration/validate`) rewrite to configuration-engine `POST /internal/v1/configurations/evaluate`; the yml comment pins the ordering rule — *agent validate must precede `configuration/**` or SCG first-match keeps agents on product `List<String>`*. Response adaptation happens via shared DTO `ConfigurationValidationResult` in `libs/common`, not gateway logic.
- Default filters: Redis `RequestRateLimiter` (50/100), CORS dedupe, explicit-origin CORS/CSRF allow-list ([ADR-004](ADR-004-csrf-edge-boundary.md)).
- Every domain route carries a **Resilience4j CircuitBreaker** with `forward:/fallback/{domain}`; unreachable/absent services (communication, ERP) degrade via fallbacks, not 404s.
- Actuator gateway endpoint is `management.endpoint.gateway.access: read-only` (Boot 4 style — `enabled` is obsolete).
- Frontend `proxy.conf.json` targets `/api` → `http://localhost:8080` only.

## Decision

**The gateway owns exactly: (1) routing + path rewrites (including the `/api/v1/cpq/**` alias family), (2) edge security (Cookie CSRF + Origin/Referer/CORS allow-list, JWT in prod), (3) rate limiting, (4) resilience (circuit breakers + fallback forwards), and (5) response-shape adaptation where an external agent contract must be preserved (the single S2B-11 validate shim). It must not contain domain validation, business rules, or data access.**

1. Domain decisions always belong to the owning service; the gateway maps transport, never semantics — except the one ratified adaptation (`validate` → CE evaluate → `ConfigurationValidationResult`).
2. New CPQ aliases must follow the existing `RewritePath` segment pattern and get a `CpqGatewayRoutesTest` case.
3. Route keys stay under `spring.cloud.gateway.server.webflux.*`; moving them back to the deprecated root is a regression that produces empty route tables.
4. Missing services (communication, ai-service `lb://`, ERP stub) resolve through circuit-breaker fallbacks; do not "fix" them by adding gateway-side mock logic.
5. Browser clients call **only** `:8080`; direct domain-port access is unsupported (consistent with [ADR-004](ADR-004-csrf-edge-boundary.md)).

## Options considered

| Option | Verdict |
|--------|---------|
| **A. Edge = routing/security/resilience + one ratified adaptation (landed)** | Agent URLs stay stable without moving domain logic; failing services degrade gracefully. Con: the validate rewrite is a special case that must keep its route ordering. |
| B. Rich gateway (inline validation/BFF logic) | Duplicated domain rules in two languages/places; rejected — the configuration-API reconciliation ADR explicitly forbids domain validation in the gateway. |
| C. Thin proxy only (no rewrites/fallbacks) | Agent alias URLs break; dependency outages surface as raw 5xx; rejected — fallbacks and aliases are load-bearing for UAT. |

## Consequences

- **Positive:** Single security perimeter ([ADR-004](ADR-004-csrf-edge-boundary.md)); predictable degradation; CPQ alias family keeps agent PRDs stable across service re-ownership; route inspection possible via read-only actuator.
- **Negative / residual:** First-match route ordering is load-bearing (validate-before-`configuration/**`) — silent breakage risk if routes are reordered; `lb://` defaults for communication/ai-service have no registry in-tree and always fall back locally; frontend must keep proxying to `:8080` (committed `proxy.conf.json` — do not retarget).

## Verification evidence (2026-09-19)

- `backend/services/gateway-service/src/main/resources/application.yml` — route tree (`spring.cloud.gateway.server.webflux.routes`), CPQ `RewritePath` aliases, S2B-11 validate rewrite + ordering comment (lines 83–101), rate limiter, Resilience4j instances, `management.endpoint.gateway.access: read-only`.
- `backend/services/gateway-service/.../config/SecurityConfig.java` — dev `permitAll` + prod JWT chains, both with Cookie CSRF.
- `frontend/proxy.conf.json` — `/api` → `:8080`.
- `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` — canonical path registry.

## References

- [docs/adr/ADR-004-csrf-edge-boundary.md](ADR-004-csrf-edge-boundary.md) — edge CSRF specifics
- [docs/backlog/ADR-configuration-api-reconciliation.md](../backlog/ADR-configuration-api-reconciliation.md) — the ratified validate adaptation
- [docs/ARCHITECTURE.md §3](../ARCHITECTURE.md) — topology + path ownership
