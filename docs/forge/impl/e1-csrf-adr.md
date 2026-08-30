---
status: done
id: e1-csrf-adr
---

# e1-csrf-adr

## Summary

Published **ADR-004** documenting the CSRF edge-only boundary: browser Cookie CSRF lives on **gateway-service** only; domain microservices intentionally keep `csrf.disable()` + `STATELESS`. Explicitly **rejects** Jules/Sentinel PRs **#402** / **#404** (enable-all Cookie CSRF on STATELESS JWT services behind the gateway). Cross-linked from `docs/CRM_GATEWAY_ENV.md`. **No** Java `SecurityConfig` changes in E1 (E2 owns pricing/pricebook alignment).

## Files

| Path | Change |
|------|--------|
| `docs/ADR-004-csrf-edge-boundary.md` | New Accepted ADR: gateway-only browser CSRF; inventory; reject #402/#404 |
| `docs/CRM_GATEWAY_ENV.md` | Brief CSRF enforcement boundary + link to ADR-004 |
| `docs/forge/impl/e1-csrf-adr.md` | This status note |

## Acceptance

- [x] ADR states gateway-only browser Cookie CSRF
- [x] Documents why microservice `csrf.disable()` + `STATELESS` is intentional
- [x] Explicitly rejects #402/#404 Jules enable-all approach
- [x] Lists service inventory (gateway ON; majority disable; pricing/pricebook anomaly → E2)
- [x] `docs/CRM_GATEWAY_ENV.md` cross-link
- [x] This impl note `status: done`
- [x] No Java `SecurityConfig` edits in this PR

## Out of scope (siblings)

- **E2** — align `pricing-service` / `pricebook-service` to `csrf.disable()`
- **E3** — Accept configuration API reconciliation ADR
- **E4** — GitHub Sentinel/Bolt hygiene (close #402/#404 with pointer here)

## Verify

```bash
test -f docs/ADR-004-csrf-edge-boundary.md
rg -n 'ADR-004|CSRF enforcement boundary' docs/CRM_GATEWAY_ENV.md
rg -n '^status: done' docs/forge/impl/e1-csrf-adr.md
# E1 must not touch SecurityConfigs:
git diff --name-only HEAD | rg 'SecurityConfig' && exit 1 || true
```
