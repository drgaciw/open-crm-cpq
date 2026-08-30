---
status: done
id: d3-security-closeout
---

# d3-security-closeout

## Summary

Wave D security **docs closeout** for the GitHub Sentinel **#451** (`security.open` auth bypass) and **#458** (gateway CORS/CSRF `*.azurewebsites.net` wildcards) families.

**Status `done`:** D1 + D2 merged to `main`, post-merge `rg` verification clean, orchestrator marked Wave D complete, D4 Sentinel PR hygiene applied.

**Plan:** [`docs/forge/13-wave-d-architecture.md`](../13-wave-d-architecture.md)

**Merge tip:** `871e49e` (`docs(forge): mark Wave D security hardening complete`) on `origin/main`.

## Intent verification (D1 / D2)

| PR | Branch / worktree | Intent (from architecture) | Code landed on `main`? |
|----|-------------------|----------------------------|------------------------|
| **D1** | `fix/forge-d1-gateway-cors-csrf` | Replace gateway CORS + CSRF `https://*.azurewebsites.net` with explicit `FRONTEND_URL` / allow-list; reject `*.` patterns in `CsrfProtectionFilter`; unit tests; document env in `docs/CRM_GATEWAY_ENV.md` | **Yes** — merge `0b7f245` / fix `a9b74ab` |
| **D2** | `fix/forge-d2-remove-security-open` | Remove `security.open` / `SECURITY_OPEN` `permitAll()` bypass from all service `SecurityConfig`s; fix CRM `BaseIntegrationTest` via `@AutoConfigureMockMvc(addFilters = false)`; drop compose env keys | **Yes** — merge `5eed0c0` / fix `6cd2a5c` |
| **D3** | `docs/forge-d3-security-closeout` | Issue-family closeout + `rg` verification runbook + orchestrator Wave D status | **Yes** — merge `079bae5` + follow-up `871e49e` |
| **D4** | (no branch) | Comment/close duplicate Sentinel PRs as superseded by forge D1/D2 | **Yes** — applied after post-merge verify |

## GitHub families (canonical → duplicates)

Forge treats **one canonical per vulnerability**; duplicates closed as superseded after D1/D2 merge.

### #451 family — `security.open` / auth bypass (CRITICAL)

| Role | Number | Title (abbrev.) |
|------|--------|-----------------|
| **Canonical** | [#451](https://github.com/drgaciw/open-crm-cpq/pull/451) | Fix securityOpen authentication bypass |
| Duplicate | [#419](https://github.com/drgaciw/open-crm-cpq/pull/419) | Hardcoded Backdoor securityOpen Flag |
| Duplicate | [#432](https://github.com/drgaciw/open-crm-cpq/pull/432) | authentication bypass backdoor |
| Duplicate | [#441](https://github.com/drgaciw/open-crm-cpq/pull/441) | authentication bypass in CRM SecurityConfig |
| Related | [#413](https://github.com/drgaciw/open-crm-cpq/pull/413) | Overly Permissive Authentication Bypass in Dev Profiles |

**Closed when:** D2 merged + `rg` clean for `security.open` under `backend/services/**/src/main/**` and no `SECURITY_OPEN` footgun in compose.

### #458 family — CORS/CSRF Azure subdomain wildcards (HIGH)

| Role | Number | Title (abbrev.) |
|------|--------|-----------------|
| **Canonical** | [#458](https://github.com/drgaciw/open-crm-cpq/pull/458) | Fix overly permissive CORS/CSRF origins |
| Duplicate | [#457](https://github.com/drgaciw/open-crm-cpq/pull/457) | CORS/CSRF wildcard |
| Duplicate | [#455](https://github.com/drgaciw/open-crm-cpq/pull/455) | CORS wildcard |
| Duplicate | [#453](https://github.com/drgaciw/open-crm-cpq/pull/453) | CORS and CSRF allowed-origins wildcards |
| Duplicate | [#449](https://github.com/drgaciw/open-crm-cpq/pull/449) | CORS and CSRF wildcards |
| Broader cluster (same root cause) | #407 #408 #409 #410 #418 #421 #423 #426 #428 #429 #433 #434 #435 #438 #439 #440 #443 #446 | Sentinel CORS/CSRF wildcard variants |

**Out of this family’s closeout:** #402 / #404 (CSRF disabled / missing protection) — separate defect; not closed via D1 alone.

## Post-merge evidence (`main` @ `871e49e`)

Verified clean:

- No `*.azurewebsites.net` under `backend/services/gateway-service/src/main`
- No `security.open` / `SECURITY_OPEN` under `backend/services/**/src/main/**` or `backend/docker-compose.yml`
- No `security.open` under `backend/services/**/src/test/**`
- `microsite-prompt.xml` has no `*.azurewebsites.net`
- `docs/CRM_GATEWAY_ENV.md` documents `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` (explicit origins only)
- `CsrfProtectionFilterTest` covers wildcard reject + yaml clean assertions

## Files

| Path | Change |
|------|--------|
| `docs/forge/impl/d3-security-closeout.md` | This closeout (`status: done`) |
| `docs/forge/00-orchestrator-status.md` | Wave D COMPLETE |
| `docs/forge/13-wave-d-architecture.md` | Status COMPLETE |

## Acceptance

- [x] Canonical #451 / #458 families listed with duplicates
- [x] D1/D2 intents verified against architecture + `main` tip
- [x] Post-merge `rg` clean checks documented and run
- [x] Orchestrator status updated for Wave D
- [x] D1 merged — gateway wildcards gone + tests
- [x] D2 merged — `security.open` gone from main sources
- [x] Post-merge verification commands exit clean
- [x] Front-matter `status: done`
- [x] D4: comment/close duplicate Sentinel PRs as superseded by forge D1/D2

## Post-merge verification commands

Commands are **clean checks**: empty output means pass. Run from repo root on `main`.

### 1) `#458` / D1 — no Azure subdomain wildcards in gateway allow-lists

```bash
rg -n 'azurewebsites\.net' \
  backend/services/gateway-service/src/main \
  docs/CRM_GATEWAY_ENV.md

rg -n '\*\.azurewebsites\.net' \
  backend/services/gateway-service/src/main

rg -n 'https://\*\.|allowedOriginPatterns|\*\.azurewebsites' \
  backend/services/gateway-service/src/main
```

```bash
rg -n '\*\.azurewebsites\.net' microsite-prompt.xml
```

```bash
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/mise/installs/java/21.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw -pl services/gateway-service -am test \
  -Dtest='CsrfProtectionFilterTest' \
  -DfailIfNoTests=false
```

### 2) `#451` / D2 — no `security.open` bypass in production sources

```bash
rg -n 'security\.open' backend/services --glob '**/src/main/**'

rg -n 'SECURITY_OPEN|security\.open' \
  backend/docker-compose.yml \
  backend/services --glob '**/src/main/**'

rg -n 'security\.open|SECURITY_OPEN' \
  backend/services/crm-service/src/test \
  backend/services --glob '**/SecurityConfig.java'
```

### 3) Docs / env contract

```bash
rg -n 'FRONTEND_URL|CORS_ALLOWED_ORIGINS|allowed-origins' docs/CRM_GATEWAY_ENV.md
test -f docs/forge/impl/d1-gateway-cors-csrf.md
test -f docs/forge/impl/d2-remove-security-open.md
rg -n '^status: done' docs/forge/impl/d1-gateway-cors-csrf.md docs/forge/impl/d2-remove-security-open.md
```

## Issue closeout comment template

```text
Superseded by forge Wave D (docs/forge/13-wave-d-architecture.md).
Canonical fix: forge D1 (CORS/CSRF allow-list) and/or D2 (remove security.open), not this Jules/Sentinel PR.
Landed on main: D1 0b7f245 / a9b74ab, D2 5eed0c0 / 6cd2a5c, closeout 871e49e.
Verification: docs/forge/impl/d3-security-closeout.md post-merge rg clean checks.
Closing as duplicate / superseded.
```

## Out of scope

- Activities API, S2B-11, S2C-12
- CI runners (#238), agent Kafka (#241)
- Full Azure AD B2C resource-server enforcement (beyond removing `security.open`)
- #402 / #404 (CSRF disabled / missing protection)

## Notes

- D1 ∥ D2 per architecture; D3 drafted before merge, then promoted after verify.
- JDK 21 required locally for gateway/CRM Maven tests.
