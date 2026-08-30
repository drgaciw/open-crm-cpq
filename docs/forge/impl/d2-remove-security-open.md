---
status: done
id: d2-remove-security-open
---

# d2-remove-security-open

## Summary

Removed the `security.open` / `SECURITY_OPEN` `permitAll()` bypass from all domain service `SecurityConfig` classes (Wave D2 / GitHub #451 family). Integration tests no longer set `security.open=true`; CRM MockMvc ITs use `@AutoConfigureMockMvc(addFilters = false)` instead. Compose no longer exposes the env footgun.

## Files

| Path | Change |
|------|--------|
| `backend/services/{crm,pricing,pricebook,product,quote,approval-routing,configuration-engine,contract-asset-management,document-generation}-service/**/SecurityConfig.java` | Drop `@Value("${security.open}")` and `if (securityOpen) permitAll` branch; keep health/docs (and pricebook `/internal/**`) public; `/api/v1/**` requires authentication |
| `backend/services/crm-service/.../BaseIntegrationTest.java` | `@AutoConfigureMockMvc(addFilters = false)`; remove DynamicProperty `security.open=true` |
| `backend/docker-compose.yml` | Remove `SECURITY_OPEN: "false"` from crm/pricebook/pricing/product/quote services |
| `docs/forge/impl/d2-remove-security-open.md` | This status note |

Gateway `SecurityConfig` was out of scope (no `security.open` flag).

## Acceptance

- [x] `rg 'security\.open' backend/services --glob '*.java'` empty (main + test)
- [x] No `SECURITY_OPEN` in `backend/docker-compose.yml`
- [x] ITs do not reintroduce `security.open`
- [x] Actuator health/info and swagger matchers remain `permitAll`
- [x] Pricebook `/internal/**` remains `permitAll`
- [ ] Full CRM docker-tagged ITs (requires Docker/Testcontainers) — not run in this worktree (no Docker daemon)

## Blast radius

- **GitNexus:** `SecurityConfig` / `securityFilterChain` impact returned **UNKNOWN** (Spring `@Bean` wiring not edge-linked). Confirmed via grep: 9 service configs + CRM `BaseIntegrationTest` subclasses (Lead/Deal/Contact/Organization/Lifecycle/QuoteAccepted Redis ITs).
- **Architecture risk:** **HIGH** (auth path). Approved to proceed.
- **detect_changes:** CLI reported clean from main checkout (no `--worktree` support); reviewed `git status` / `git diff` in this worktree instead — 9 `SecurityConfig`s, `BaseIntegrationTest`, compose, impl doc.
- Runtime: without gateway JWT / resource-server wiring, unauthenticated `/api/v1/**` calls receive 401/403 (intended). Local compose previously already set `SECURITY_OPEN=false`.

## Verify (executed)

```text
rg 'security\.open' backend/services --glob '*.java'  → empty
SECURITY_OPEN absent from backend/docker-compose.yml
./mvnw -pl <9 services> -am compile -DskipTests     → SUCCESS
./mvnw -pl services/crm-service -am test -Dgroups='!docker' → BUILD SUCCESS (139 tests)
```
