---
status: done
id: d3-security-closeout
---

# d3-security-closeout

## Summary

Wave D security **docs closeout** for the GitHub Sentinel **#451** (`security.open` auth bypass) and **#458** (gateway CORS/CSRF `*.azurewebsites.net` wildcards) families.

**Status `partial`:** this note and forge orchestrator update are landed; **D1** (`fix/forge-d1-gateway-cors-csrf`) and **D2** (`fix/forge-d2-remove-security-open`) code fixes are **not merged** yet. Evidence on current tree still shows both vulnerabilities. Promote this file to `status: done` after D1+D2 merge and the post-merge verification block below goes clean.

**Plan:** [`docs/forge/13-wave-d-architecture.md`](../13-wave-d-architecture.md)

## Intent verification (D1 / D2)

| PR | Branch / worktree | Intent (from architecture) | Code landed on this tip? |
|----|-------------------|----------------------------|--------------------------|
| **D1** | `fix/forge-d1-gateway-cors-csrf` | Replace gateway CORS + CSRF `https://*.azurewebsites.net` with explicit `FRONTEND_URL` / allow-list; reject `*.` patterns in `CsrfProtectionFilter`; unit tests; document env in `docs/CRM_GATEWAY_ENV.md` | **No** — wildcards still in `gateway-service` `application.yml` (+ `microsite-prompt.xml`) |
| **D2** | `fix/forge-d2-remove-security-open` | Remove `security.open` / `SECURITY_OPEN` `permitAll()` bypass from all service `SecurityConfig`s; fix CRM `BaseIntegrationTest` via `@AutoConfigureMockMvc(addFilters = false)` / `@WithMockUser`; drop compose env keys | **No** — `@Value("${security.open:false}")` still in 9 service configs; compose still sets `SECURITY_OPEN` |
| **D3** | `docs/forge-d3-security-closeout` | Issue-family closeout + `rg` verification runbook + orchestrator Wave D status | **This doc** (partial until D1/D2) |

Jules/Sentinel PRs claim fixes; **tree evidence contradicts** until forge D1/D2 merge (do not close canonicals on Jules claims alone).

## GitHub families (canonical → duplicates)

Sentinel opened many near-duplicate PRs. Forge treats **one canonical per vulnerability**; after D1/D2 merge, comment duplicates as superseded (optional **D4**) and close/supersede the open cluster.

### #451 family — `security.open` / auth bypass (CRITICAL)

| Role | Number | Title (abbrev.) |
|------|--------|-----------------|
| **Canonical** | [#451](https://github.com/drgaciw/open-crm-cpq/pull/451) | Fix securityOpen authentication bypass |
| Duplicate | [#419](https://github.com/drgaciw/open-crm-cpq/pull/419) | Hardcoded Backdoor securityOpen Flag |
| Duplicate | [#432](https://github.com/drgaciw/open-crm-cpq/pull/432) | authentication bypass backdoor |
| Duplicate | [#441](https://github.com/drgaciw/open-crm-cpq/pull/441) | authentication bypass in CRM SecurityConfig |
| Related | [#413](https://github.com/drgaciw/open-crm-cpq/pull/413) | Overly Permissive Authentication Bypass in Dev Profiles |

**Closes when:** D2 merged + `rg` clean for `security.open` under `backend/services/**/src/main/**` and no `SECURITY_OPEN` footgun in compose/docs used for prod-like runs.

### #458 family — CORS/CSRF Azure subdomain wildcards (HIGH)

| Role | Number | Title (abbrev.) |
|------|--------|-----------------|
| **Canonical** | [#458](https://github.com/drgaciw/open-crm-cpq/pull/458) | Fix overly permissive CORS/CSRF origins |
| Duplicate | [#457](https://github.com/drgaciw/open-crm-cpq/pull/457) | CORS/CSRF wildcard |
| Duplicate | [#455](https://github.com/drgaciw/open-crm-cpq/pull/455) | CORS wildcard |
| Duplicate | [#453](https://github.com/drgaciw/open-crm-cpq/pull/453) | CORS and CSRF allowed-origins wildcards |
| Duplicate | [#449](https://github.com/drgaciw/open-crm-cpq/pull/449) | CORS and CSRF wildcards |
| Broader cluster (same root cause) | #407 #408 #409 #410 #418 #421 #423 #426 #428 #429 #433 #434 #435 #438 #439 #440 #443 #446 | Sentinel CORS/CSRF wildcard variants |

**Out of this family’s closeout:** #402 / #404 (CSRF disabled / missing protection) — separate defect; do not close via D1 alone.

**Closes when:** D1 merged + no `*.azurewebsites.net` in gateway allow-lists; filter rejects wildcard Origins; `FRONTEND_URL` (and optional explicit extras) documented.

## Pre-merge evidence (baseline @ docs tip)

Still vulnerable (expected until D1/D2):

- Gateway CORS + CSRF: `backend/services/gateway-service/src/main/resources/application.yml` contains `https://*.azurewebsites.net` (CORS `allowedOrigins` and `security.csrf.allowed-origins`).
- `CsrfProtectionFilter.matchesPattern` still supports `*.` suffix matching.
- `security.open` in 9 `SecurityConfig.java` files: `crm`, `pricing`, `pricebook`, `product`, `quote`, `approval-routing`, `configuration-engine`, `contract-asset-management`, `document-generation`.
- `backend/docker-compose.yml` sets `SECURITY_OPEN: "false"` on multiple services (safe default, still injectable).
- `crm-service` `BaseIntegrationTest` registers `security.open=true`.
- `microsite-prompt.xml` still lists `https://*.azurewebsites.net` in `allowed_origins` (align with D1 or track as follow-up if D1 scopes gateway-only).

## Files (this PR / commit)

| Path | Change |
|------|--------|
| `docs/forge/impl/d3-security-closeout.md` | This closeout (status partial) |
| `docs/forge/00-orchestrator-status.md` | Wave D progress: D3 docs drafted; D1∥D2 pending |

## Acceptance

- [x] Canonical #451 / #458 families listed with duplicates
- [x] D1/D2 intents verified against architecture + current tree (not merged)
- [x] Post-merge `rg` clean checks documented
- [x] Orchestrator status updated for Wave D
- [ ] D1 merged — gateway wildcards gone + tests
- [ ] D2 merged — `security.open` gone from main sources + ITs green
- [ ] Post-merge verification commands exit clean
- [ ] Flip this front-matter to `status: done`
- [ ] Optional D4: comment/close duplicate Sentinel PRs as superseded by forge D1/D2

## Post-merge verification (run after D1 + D2 land)

Commands are **clean checks**: empty output / exit 0 with no matches means pass. Run from repo root on the merge tip (or `main` after merge).

### 1) `#458` / D1 — no Azure subdomain wildcards in gateway allow-lists

```bash
# Expect: no matches
rg -n 'azurewebsites\.net' \
  backend/services/gateway-service/src/main \
  docs/CRM_GATEWAY_ENV.md

# Expect: no matches for subdomain wildcard form in gateway config
rg -n '\*\.azurewebsites\.net' \
  backend/services/gateway-service

# Stronger: no *. host patterns in CSRF/CORS allow config
rg -n 'https://\*\.|allowedOriginPatterns|\*\.azurewebsites' \
  backend/services/gateway-service/src/main
```

Optional microsite alignment (if D1 includes it; else track separately):

```bash
rg -n '\*\.azurewebsites\.net' microsite-prompt.xml
```

Gateway unit tests (adjust `-Dtest` to classes D1 adds, e.g. CSRF/CORS origin tests):

```bash
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/mise/installs/java/21.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw -pl services/gateway-service -am test \
  -Dtest='*Csrf*,*Cors*,*Origin*' \
  -DfailIfNoTests=false
```

### 2) `#451` / D2 — no `security.open` bypass in production sources

```bash
# Expect: no matches under main sources
rg -n 'security\.open' backend/services --glob '**/src/main/**'

# Expect: no SECURITY_OPEN in compose / service env used as bypass
rg -n 'SECURITY_OPEN|security\.open' \
  backend/docker-compose.yml \
  backend/services --glob '**/src/main/**'

# Tests may mention removal historically; main code must not reintroduce the property.
# Expect: no registry.add("security.open"...) or @Value("${security.open
rg -n 'security\.open|SECURITY_OPEN' \
  backend/services/crm-service/src/test \
  backend/services --glob '**/SecurityConfig.java'
```

Focused IT smoke after D2 (CRM was the flag consumer in tests):

```bash
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/mise/installs/java/21.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw -pl services/crm-service -am test \
  -Dskip.docker.tests=true \
  -DfailIfNoTests=false
```

### 3) Docs / env contract

```bash
# Expect: FRONTEND_URL (or explicit CORS allow-list) documented after D1
rg -n 'FRONTEND_URL|CORS_ALLOWED_ORIGINS|allowed-origins' docs/CRM_GATEWAY_ENV.md

# Expect: forge impl notes present
test -f docs/forge/impl/d1-gateway-cors-csrf.md
test -f docs/forge/impl/d2-remove-security-open.md
rg -n '^status: done' docs/forge/impl/d1-gateway-cors-csrf.md docs/forge/impl/d2-remove-security-open.md
```

### 4) Promote D3 when green

1. Re-run sections 1–3 with zero unexpected hits.
2. Set this file front-matter to `status: done`.
3. Update `docs/forge/00-orchestrator-status.md` → Wave D complete (or awaiting D4 issue hygiene only).
4. Close [#451](https://github.com/drgaciw/open-crm-cpq/pull/451) / [#458](https://github.com/drgaciw/open-crm-cpq/pull/458) with links to forge merge SHAs; comment duplicates (D4).

## Issue closeout comment template (post-merge)

```text
Superseded by forge Wave D (docs/forge/13-wave-d-architecture.md).
Canonical fix: forge D1 (CORS/CSRF allow-list) and/or D2 (remove security.open), not this Jules/Sentinel PR.
Verification: docs/forge/impl/d3-security-closeout.md post-merge rg clean checks.
Closing as duplicate / superseded.
```

## Out of scope

- Activities API, S2B-11, S2C-12
- CI runners (#238), agent Kafka (#241)
- Merging or pushing D1/D2 from this docs worktree
- Closing GitHub PRs from this commit (wait for code merge + clean `rg`)

## Notes

- D1 ∥ D2 per architecture; D3 may draft before they land (this file).
- JDK 21 required locally for gateway/CRM Maven tests (JDK 27 hits known compile failures in this environment).
