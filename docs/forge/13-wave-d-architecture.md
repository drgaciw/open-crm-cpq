# Wave D architecture — Security hardening

**Status:** COMPLETE — merged to `main` @ `871e49e` (D1∥D2→D3→D4)  
**Architect:** `opencrm-architect` agent + `/opencrm-architect` skill  
**Evidence date:** 2026-08-30 (pre-fix baseline @ `6f1844d`; post-fix tip `871e49e`)  
**Sources:** GitHub Sentinel issues; `SecurityConfig.java` × N; gateway `application.yml` + `CsrfProtectionFilter`

## 1. Problem / evidence (pre-fix baseline)

### D-CRIT — `security.open` authentication bypass (GitHub #451, #419, …)

**Was present** on `main` before D2 (Jules/Sentinel PRs claimed removal; tree contradicted). D2 removed:

- `@Value("${security.open:false}")` + `permitAll()` when true from:  
  `crm`, `pricing`, `pricebook`, `product`, `quote`, `approval-routing`, `configuration-engine`, `contract-asset-management`, `document-generation`  
- CRM `BaseIntegrationTest` no longer sets `security.open=true`  
- Compose no longer exposes `SECURITY_OPEN`

### D-HIGH — Gateway CORS/CSRF subdomain wildcards (GitHub #458, #457, #455, …)

**Was present** before D1:

```yaml
# gateway-service application.yml (pre-D1)
allowedOrigins:
  - "http://localhost:4200"
  - "https://*.azurewebsites.net"
security.csrf.allowed-origins:
  - "http://localhost:4200"
  - "https://*.azurewebsites.net"
```

`CsrfProtectionFilter` previously supported `*.` suffix matching. D1 replaced this with an explicit `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` allow-list and rejects `*` entries at bind time.

## 2. Decisions

| Topic | Decision | Rejected |
|-------|----------|----------|
| `security.open` | **Remove** property and branches from all service `SecurityConfig`s | Keep flag gated by profile — still injectable via env |
| Test auth | Use `@AutoConfigureMockMvc(addFilters = false)` and/or `@WithMockUser` / Security test support | Reintroduce test-only property with same name |
| CORS origins | Explicit allow-list from env: `FRONTEND_URL` (+ optional comma-separated `CORS_ALLOWED_ORIGINS`) — **no** `*.` patterns | Spring `allowedOriginPatterns` with Azure wildcard |
| CSRF origins | Same explicit list; reject patterns containing `*.` at config bind time | Silent ignore of wildcards |
| Issue hygiene | One fix PR closes canonical #451 and #458; comment duplicates as superseded | Separate PR per duplicate Sentinel issue |

## 3. PR DAG (forge-ready)

```
D1 (gateway CORS/CSRF) ──┐
                          ├──► D3 (docs + issue closeout)
D2 (remove security.open)┘
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **D1** | `fix(gateway): replace Azure CORS/CSRF wildcards with explicit allow-list` | `gateway-service` yml + `CsrfProtectionFilter` (+ unit tests) | MED | No `*.azurewebsites.net` in tree; wildcard Origin rejected by filter tests; localhost:4200 still works; `FRONTEND_URL` documented in `docs/CRM_GATEWAY_ENV.md` |
| **D2** | `fix(security): remove security.open permitAll bypass from services` | All `SecurityConfig.java` with flag; CRM `BaseIntegrationTest` (+ any IT relying on flag) | HIGH | `rg security.open` empty under `backend/services/**/main`; ITs green without property; actuator health still public where intended |
| **D3** | `docs(security): Wave D runbook + GitHub issue closeout notes` | `docs/forge/impl/*`, gateway env docs | LOW | Canonical issues listed; forge status updated |

**Parallelism:** D1 ∥ D2 (different owners: gateway vs service SecurityConfigs), then D3.

**Optional D4 (same wave if capacity):** collapse duplicate Sentinel issues via comments after D1/D2 merge (no code).

## 4. Blast radius

| Change | Upstream / dependents |
|--------|----------------------|
| Gateway CORS/CSRF | Angular CRM (4200), any Azure-hosted FE, browser clients, CSRF cookie flow |
| Remove `security.open` | Every service IT/MockMvc setup; local docker-compose env (remove unused keys); developer docs that mention `SECURITY_OPEN=true` |

GitNexus: before editing each `SecurityConfig` / `CsrfProtectionFilter`, run impact upstream. Expect HIGH on shared security patterns.

## 5. Forge layout (when PM exits architect-only)

| Pane | Agent | Worktree branch |
|------|-------|-----------------|
| 1 | `wd-cors` | `fix/forge-d1-gateway-cors-csrf` |
| 2 | `wd-secopen` | `fix/forge-d2-remove-security-open` |
| 3 | `wd-docs` | `docs/forge-d3-security-closeout` (after D1/D2 or docs-only stubs) |

Model: Grok 4.5, `--always-approve`, architect reviews diffs before merge if risk HIGH.

## 6. Out of scope (this wave)

- Activities API, S2B-11, S2C-12  
- Full Azure AD B2C enforcement hardening beyond removing bypass  
- CI runner acquisition (#238)  
- CPQ agent Kafka migration (#241)  

## 7. Goal text (paste into `/goal` when forging)

```text
Implement Open CRM/CPQ Wave D security hardening per docs/forge/13-wave-d-architecture.md: (D1) remove gateway CORS/CSRF *.azurewebsites.net wildcards in favor of explicit FRONTEND_URL allow-list with unit tests; (D2) remove security.open permitAll bypass from all service SecurityConfigs and fix ITs without reintroducing the flag; (D3) document closeout for GitHub #451/#458. Use GitNexus impact before symbol edits. Do not implement activities API or S2B-11/S2C-12. Evidence: rg clean for security.open and azurewebsites wildcards, gateway unit tests green, docs/forge/impl/d*.md status done, merge+push main.
```

## 8. Architect sign-off checklist

- [x] Vulnerabilities confirmed present on pre-fix `main`  
- [x] Jules “fixed” claims contradicted by tree evidence  
- [x] PR DAG parallelizable without same-file fights (D1 vs D2)  
- [x] PM authorized forge execution  
- [x] D1∥D2 merged; D3 docs done; D4 Sentinel PRs closed  
- [x] Post-merge `rg` clean on `main` @ `871e49e`  


## Architect review notes

**Reviewer:** `opencrm-architect` (plan mode, no code)  
**Re-verified:** 2026-08-30 against HEAD `9e3aecd` (doc evidence SHA `6f1844d` is an ancestor; vulnerability presence unchanged)  
**Forge recommendation:** **APPROVE** — DAG D1∥D2→D3 remains sound; treat notes below as mandatory implementer constraints, not a redesign.

### 1) `security.open` — confirmed present

Exact main-source inventory (**9** services), matching §1:

| Service | Path |
|---------|------|
| crm-service | `.../crm/config/SecurityConfig.java` |
| pricing-service | `.../pricing/config/SecurityConfig.java` |
| pricebook-service | `.../pricebook/config/SecurityConfig.java` |
| product-service | `.../product/config/SecurityConfig.java` |
| quote-service | `.../quote/config/SecurityConfig.java` |
| approval-routing | `.../approval/config/SecurityConfig.java` |
| configuration-engine | `.../configuration/config/SecurityConfig.java` |
| contract-asset-management | `.../contract/config/SecurityConfig.java` |
| document-generation | `.../document/config/SecurityConfig.java` |

Also still present:

- `crm-service` `BaseIntegrationTest` → `registry.add("security.open", () -> "true")`
- `backend/docker-compose.yml` → `SECURITY_OPEN: "false"` on crm, pricing, pricebook, product, quote (safe default, still an env footgun)

**Explicit non-hits (do not invent D2 work):**

- **gateway-service** `SecurityConfig` — no `security.open`; uses `@Profile("dev")` `permitAll()` (separate bypass; remains **out of scope** per §6 unless PM expands wave)
- **ai-service** — no `SecurityConfig` class (depends on `libs/security` artifact only); nothing to strip for D2

None of the nine domain `SecurityConfig`s configure `oauth2ResourceServer`. Closed mode already requires `.authenticated()` with no resource-server wiring (compose default `SECURITY_OPEN=false`). D2’s job is **remove the bypass property**, not add full Azure AD B2C enforcement (still §6).

### 2) Gateway CORS/CSRF wildcards — confirmed present

Still on tree:

- `gateway-service` `application.yml` `spring.cloud.gateway.globalcors...allowedOrigins` → `https://*.azurewebsites.net`
- `gateway-service` `application.yml` `security.csrf.allowed-origins` → same
- `CsrfProtectionFilter.matchesPattern` — explicit `*.` suffix matching (any Azure App Service subdomain can pass Origin/Referer when cookies present)
- **No** existing gateway unit tests covering CSRF/CORS origin matching

**Additional tree hit for D1 “no `*.azurewebsites.net` in tree” acceptance:**

- `microsite-prompt.xml` (`allowed_origins` includes `https://*.azurewebsites.net`) — include in **D1** (or D1+D3 scrub), not leave as residual

OK to leave: `docs/scripts/staging-gateway-smoke.ps1` example hostname (concrete URL in docs, not an allow-list wildcard).

**CORS API nuance (implementer constraint):** Spring `allowedOrigins` does **not** accept host wildcards; patterns belong in `allowedOriginPatterns`. The YAML wildcard may already be ineffective for CORS while **CSRF still honors it**. D1 must replace with explicit origins via `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` — do **not** “fix” by moving the Azure wildcard into `allowedOriginPatterns` (already rejected in §2).

### 3) PR DAG D1∥D2→D3 — validated

Parallelism and ownership split are correct (gateway vs domain `SecurityConfig`s; no same-file fights). Sequencing to D3 docs/closeout is correct.

| PR | Amendment / risk |
|----|------------------|
| **D1** | Also scrub `microsite-prompt.xml`. Harden `CsrfProtectionFilter` to **reject** patterns containing `*.` (fail closed). Add unit tests (none today). Document `FRONTEND_URL` + optional `CORS_ALLOWED_ORIGINS` in `docs/CRM_GATEWAY_ENV.md` (section absent today). Risk remains **MED**. |
| **D2** | Scope remains the nine services above. **Must** remove `SECURITY_OPEN` keys from `docker-compose.yml`. **Primary test risk:** CRM `BaseIntegrationTest` + MockMvc controller/lifecycle ITs currently depend on `security.open=true` with filters **enabled** — switch to `@AutoConfigureMockMvc(addFilters = false)` and/or `@WithMockUser` / Security test support; do **not** reintroduce a same-named property. Other services show little/no MockMvc IT coupling to the flag today; still run module tests after config edits. Risk remains **HIGH**. |
| **D3** | Unchanged; run after D1/D2 land. Optional D4 duplicate-issue hygiene still valid. |

**Missing services?** No — inventory is complete for `security.open`. Do not expand D2 to ai-service or gateway profile bypass in this wave.

### 4) GitNexus blast-radius note

Upstream `impact` on `CsrfProtectionFilter` and ambiguous `SecurityConfig` candidates returns **risk: UNKNOWN** (0 resolved callers; index reported **16 commits behind** HEAD). Per project rules, UNKNOWN is unresolved — confirm via text inventory (done above) before edits; forge agents must re-run impact per symbol/file before changing, and prefer re-analyze if operating far behind index.

### 5) Sign-off

- [x] Live-tree confirmation of D-CRIT / D-HIGH  
- [x] DAG structure approved for forge  
- [x] Amendments captured for implementers  
- [ ] PM authorizes forge execution (`forge-wave-d` / goal text in §7)

**ARCH_REVIEW_DONE — recommendation: APPROVE for forge** (with notes above binding on D1/D2 agents).
