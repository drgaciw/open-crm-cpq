---
status: done
id: f3-gateway-validate-shim
---

# f3-gateway-validate-shim

## Summary

Gateway S2B-11 compatibility shim: `POST /api/v1/configuration/validate` (and CPQ alias) routes to configuration-engine `POST /internal/v1/configurations/evaluate` via Path + Method predicates and RewritePath. Declared **before** product `configuration/**` / `cpq/configuration/**` so Spring Cloud Gateway first-match does not keep agents on product `List<String>`. No domain REQUIRES/EXCLUDES logic in gateway Java — route + rewrite only. Product `ConfigurationRuleController.validate` retained.

## Changes

| Area | Detail |
|------|--------|
| `application.yml` (default + `dev`) | `configuration-validate`, `cpq-configuration-validate` before product wildcards |
| Rewrite | `/api/v1/configuration/validate` → `/internal/v1/configurations/evaluate`; CPQ alias likewise |
| URI | `${CONFIGURATION_ENGINE_URI:http://localhost:8088}` (default profile) |
| `CpqGatewayRoutes` | Route ids added to `ROUTE_IDS` / `AGENT_CONTRACT_ROUTE_IDS` / default profile lists |
| Appendix A | `A-CFG-VALIDATE` `gatewayRouteId` → `configuration-validate` |
| Tests | `CpqGatewayRoutesTest` shim assertions; `ConfigurationValidateRoutePredicateTest` requires specific-before-wildcard |

## Acceptance

- [x] External path `POST /api/v1/configuration/validate` unchanged
- [x] Validate route precedes `configuration/**` (both profiles)
- [x] Rules CRUD still on product via `configuration/**`
- [x] No REQUIRES/EXCLUDES in `gateway-service` Java (`rg` clean)
- [x] Product `/api/v1/configuration/validate` handler still present
- [x] Contract/unit route tests updated

## Risk note

**HIGH** agent-contract risk (PM acknowledged). GitNexus impact on YAML routes / `CpqGatewayRoutes` is **UNKNOWN** — confirmed with `rg` + route predicate tests, not graph alone.
