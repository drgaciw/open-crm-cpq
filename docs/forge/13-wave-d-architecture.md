# Wave D architecture — Security hardening (architect-only)

**Status:** DESIGN READY — awaiting PM go-ahead to forge  
**Architect:** `opencrm-architect` agent + `/opencrm-architect` skill  
**Evidence date:** 2026-08-30 against `main` @ `6f1844d`  
**Sources:** GitHub Sentinel issues; `SecurityConfig.java` × N; gateway `application.yml` + `CsrfProtectionFilter`

## 1. Problem / evidence

### D-CRIT — `security.open` authentication bypass (GitHub #451, #419, …)

Still present in code (Jules issue text claims removal; **not applied on main**):

- `@Value("${security.open:false}")` + `permitAll()` when true in at least:  
  `crm`, `pricing`, `pricebook`, `product`, `quote`, `approval-routing`, `configuration-engine`, `contract-asset-management`, `document-generation`  
- Tests: `crm-service` `BaseIntegrationTest` sets `security.open=true`  
- Compose sets `SECURITY_OPEN: "false"` (safe default) but **env override remains a prod footgun**

### D-HIGH — Gateway CORS/CSRF subdomain wildcards (GitHub #458, #457, #455, …)

Still present:

```yaml
# gateway-service application.yml
allowedOrigins:
  - "http://localhost:4200"
  - "https://*.azurewebsites.net"
security.csrf.allowed-origins:
  - "http://localhost:4200"
  - "https://*.azurewebsites.net"
```

`CsrfProtectionFilter.matchesPattern` explicitly supports `*.` suffix matching → any Azure App Service subdomain can satisfy Origin checks.

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

- [x] Vulnerabilities confirmed present on current `main`  
- [x] Jules “fixed” claims contradicted by tree evidence  
- [x] PR DAG parallelizable without same-file fights (D1 vs D2)  
- [ ] PM authorizes forge execution  
