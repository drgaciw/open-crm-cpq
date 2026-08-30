# Wave E architecture — Residual security boundary + ADR unblock + Sentinel hygiene

**Status:** APPROVED FOR FORGE — PM authorized 2026-08-30  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** `origin/main` @ `8cd0ca4` (Waves A–D complete; D1 CORS/CSRF allow-list + D2 `security.open` removal)  
**Sources:** GitNexus CLI (index @ `24c468f`, 26 commits stale — re-analyze before symbol edits); GitHub Sentinel/Bolt PRs; tree inventory of `SecurityConfig` × N; `docs/backlog/ADR-configuration-api-reconciliation.md` (**Proposed**); Wave C `c6-pricebook-n1` closeout

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable: do not invent |
| **S2B-11 validate shim** | **HOLD (impl)** | ADR still **Proposed** with 4 open questions — full impl blocked |
| **S2C-12 quote→approval BFF** | **HOLD** | Needs Accepted ownership ADR + agent-contract fixtures; HIGH risk |
| **CI runners (#238)** | **HOLD** | Billing/runner infra — not forgeable in app worktrees |
| **Agent Kafka (#241)** | **HOLD** | Agent-stack migration; out of security/cutover residual |
| **Jules CSRF enable-all (#402/#404)** | **REJECT as proposed** | Blind Cookie CSRF on STATELESS JWT services behind gateway is wrong |
| **WhatsApp SQLi (#414/#416)** | **OUT / triage** | Legacy `cpq_beta`; tree already uses `%(mobile_no)s` named params (#414 admits FP) |
| **Bolt PriceBook N+1 family** | **SUPERSEDED** | Wave C **C6** already shipped batch `IN` query on `main` |
| **CI passwords (#415)** | **STALE** | `.github/workflows/ci.yml` is Jenkins-cutover manual fallback — no MariaDB `root` password block |
| **CPQ Appendix A smoke expansion** | **DEFER** | `CpqGatewayRoutesTest` already covers Appendix A path predicates + ERP 501 HTTP |
| **✅ Chosen: Wave E** | **P0 residual security boundary + ADR Accept + hygiene** | Highest value **safe** forge: closes CRITICAL/HIGH Sentinel noise correctly, unblocks future S2B-11 via ADR Accept only, cleans superseded PRs — 3–4 parallel panes, no HIGH ADR blockers for the work itself |

**PM authorization used:** “Architect picks from GitNexus+GitHub evidence” for Wave E.

---

## 1. Problem / evidence

### E-P0 — CSRF boundary inconsistency (GitHub #402 CRITICAL, #404 HIGH)

**Tree @ `8cd0ca4`:**

| CSRF posture | Services |
|--------------|----------|
| **Edge CSRF ON** (correct for browser) | `gateway-service` — `CookieServerCsrfTokenRepository` + `CsrfProtectionFilter` (D1 allow-list) |
| **Cookie CSRF ON + STATELESS** (inconsistent) | `pricing-service`, `pricebook-service` |
| **CSRF disabled + STATELESS** (majority) | `crm`, `quote`, `product`, `approval-routing`, `configuration-engine`, `contract-asset-management`, `document-generation` |

CRM `SecurityConfig` documents the intended model: gateway authenticates; services are JWT/internal-header resource servers with `SessionCreationPolicy.STATELESS`. Downstream `csrf.disable()` is **correct** for Bearer/service-mesh callers. Jules PRs #402/#404 would enable Double-Submit Cookie CSRF on those services — that:

1. Does **not** improve browser CSRF (browsers already hit the **gateway**, which has CSRF).
2. Risks **403** on gateway→service hops if cookies are not forwarded or clients send Bearer-only.
3. Leaves pricing/pricebook as confused half-measures (Cookie CSRF without browser session affinity).

Wave D explicitly left #402/#404 out of D3 closeout (`docs/forge/impl/d3-security-closeout.md`).

### E-P1 — Configuration ADR still Proposed (blocks S2B-11)

`docs/backlog/ADR-configuration-api-reconciliation.md` **Status: Proposed** (2026-05-18). Open questions (§Open questions):

1. Stateless evaluator vs ephemeral session  
2. Shared DTO location (`libs/common` vs gateway-local)  
3. Rule projection (sync REST vs cache + S2B-9 events)  
4. product-service validate deprecation date  

**Constraint:** Do **not** schedule full S2B-11 gateway shim / response-shape rewrite in Wave E. Promote ADR → **Accepted** with **frozen defaults** only.

GitNexus (stale index): `validateConfiguration` candidates include product-service + ai-service tool client (LOW where resolved) and quote `ProductServiceClient` (**UNKNOWN** — confirm via text before any later S2B-11 edit). `ConstraintSolver` callers: `ConfigurationSessionService` only (placeholder `color` / `option_a` logic still present — **out of Wave E scope**).

### E-HYGIENE — Superseded / false-positive Sentinel & Bolt noise

| Family | Evidence | Action |
|--------|----------|--------|
| PriceBook N+1 (#400/#405/#406/#420/#425/#427/#431/#442/#445/#447/#448/#450/#452/#456/…) | C6 on `main`: `findByPriceBookIdAndProductExternalIdInAndDeletedFalse`; unit proves `times(1)` | Close as superseded by forge C6 |
| WhatsApp SQLi (#414 Low FP, #416 High) | `cpq_beta/crm/api/whatsapp.py` already uses `%(mobile_no)s`; modernization Phase 2 | Comment + close / defer to Phase-2 Frappe track |
| CI passwords (#415 Low) | Current `ci.yml` = workflow_dispatch Maven jobs; no hardcoded MySQL service password | Close as obsolete after Jenkins cutover |
| CSRF Jules (#402/#404) | Wrong fix (see E-P0) | Close with pointer to Wave E ADR + E2 alignment |

### GitNexus blast-radius notes (pre-edit)

- `CsrfProtectionFilter` / `SecurityConfig*` upstream impact: **risk UNKNOWN** (0 graph callers; Spring `@Configuration` / `@Component`). **UNKNOWN ≠ safe** — confirm with `rg` inventory (done above) and re-run `impact` after `gitnexus analyze` on tip before editing.
- Index used for this plan is **26 commits behind** worktree tip — forge agents **must** re-analyze or accept UNKNOWN + text confirmation.

---

## 2. Decisions

| Topic | Decision | Rejected |
|-------|----------|----------|
| CSRF enforcement point | **Gateway edge only** for browser cookie CSRF (`SecurityConfig` + `CsrfProtectionFilter`). Document as `docs/ADR-004-csrf-edge-boundary.md` (same location as ADR-001..003). | Merge Jules #402/#404 enable-all Cookie CSRF on microservices |
| Downstream services | Keep / restore **`csrf.disable()`** + `STATELESS` on all domain services including **pricing** and **pricebook** | Defense-in-depth Cookie CSRF on every service without FE XSRF wiring |
| Auth model | Unchanged: gateway JWT/prod auth; services `.authenticated()` on `/api/v1/**`; health/docs public | Full Azure AD B2C resource-server rollout this wave |
| S2B-11 | **ADR Accept only** — freeze open-question defaults; **no** gateway validate shim / DTO rewrite / product-service validate removal | Full S2B-11 implementation |
| S2B-11 open Q defaults (frozen for Accept) | (1) Prefer `POST /internal/v1/configurations/evaluate` **stateless**; (2) shared DTO in `backend/libs/common`; (3) sync REST rule fetch first (cache = S2B-9); (4) retain product-service validate until contract-test parity — **no removal date** until S2B-11 lands | Ephemeral-session-only agent path; gateway-local DTOs as long-term home |
| Activities / S2C-12 / #238 / #241 | Remain **held** | Invent activities schema; start S2C-12; CI billing; Kafka agent migration |
| WhatsApp / N+1 Bolts / #415 | Hygiene closeout only | Re-implement C6; big-bang Frappe WhatsApp rewrite |

---

## 3. PR DAG (forge-ready)

```
E1 (ADR CSRF edge-only) ────────┐
E2 (align pricing/pricebook CSRF)┤──► E4 (GitHub Sentinel/Bolt hygiene)
E3 (Accept config ADR) ─────────┘
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **E1** | `docs(adr): CSRF edge-only boundary for gateway vs STATELESS services` | New `docs/ADR-004-csrf-edge-boundary.md`; cross-link `docs/CRM_GATEWAY_ENV.md` + `docs/forge/impl/e1-csrf-adr.md` | **LOW** | ADR states gateway-only browser CSRF; documents why microservice `csrf.disable()` is intentional; explicitly rejects #402/#404 Jules approach; lists service inventory |
| **E2** | `fix(security): align pricing/pricebook CSRF with STATELESS csrf.disable()` | `pricing-service` + `pricebook-service` `SecurityConfig.java`; short class Javadoc parity with CRM; unit/module tests still green | **MED** | `rg 'CookieCsrfTokenRepository' backend/services --glob '**/SecurityConfig.java'` → **gateway only** (reactive `CookieServerCsrfTokenRepository`); pricing/pricebook use `csrf.disable()`; no FE/proxy changes |
| **E3** | `docs(adr): Accept configuration API reconciliation ADR (freeze open questions)` | `docs/backlog/ADR-configuration-api-reconciliation.md` Status → **Accepted**; record frozen defaults; note S2B-11 impl deferred to later wave; `docs/forge/impl/e3-adr-accept.md` | **LOW** | Status Accepted; open questions answered with Wave E defaults; **no** product/gateway Java changes; matrix still lists current `List<String>` validate shape |
| **E4** | `chore(github): Wave E Sentinel/Bolt PR hygiene` | GitHub comments + close (no product code). Families: N+1 Bolts (C6), #402/#404 (E1/E2), #414/#416 (legacy/FP), #415 (obsolete) | **LOW** | Canonical comment templates applied; stale PRs closed or labeled superseded; forge status note `docs/forge/impl/e4-sentinel-hygiene.md` |

**Parallelism:** E1 ∥ E2 ∥ E3 (disjoint files: docs ADR vs two SecurityConfigs vs config ADR). E4 after E1 text exists (comment templates); N+1 closes can start immediately.

**Optional E5 (capacity only):** expand gateway env docs with “do not enable Cookie CSRF on downstream services” checklist item — fold into E1 if preferred.

---

## 4. Blast radius

| Change | Upstream / dependents | GitNexus / text |
|--------|----------------------|-----------------|
| E1 docs ADR | Reviewers, Sentinel closeout comments, future security audits | Docs only |
| E2 pricing/pricebook `SecurityConfig` | Any client that relied on XSRF cookies **direct** to :8082/:8089 (FE uses gateway `:8080` — no evidence of direct Cookie CSRF clients) | Impact **UNKNOWN** on `SecurityConfig` — confirm with `rg CookieCsrfTokenRepository` + FE `withCredentials`/`XSRF` search (FE goes via gateway) |
| E3 ADR Accept | Unblocks **future** S2B-11 design; does **not** change runtime validate (`List<String>` remains) | `validateConfiguration` — do not edit symbols this wave |
| E4 GitHub only | Issue tracker noise; no runtime | — |

**Warn:** If any external integrator posts **directly** to pricing/pricebook with browser cookies and XSRF headers (bypass gateway), E2 could  change their CSRF expectations — document in E1; no such client found in `frontend/`.

---

## 5. Forge layout (Herdr panes)

| Pane | Agent id | Worktree branch |
|------|----------|-----------------|
| 1 | `we-csrf-adr` | `docs/forge-e1-csrf-edge-adr` |
| 2 | `we-csrf-align` | `fix/forge-e2-align-csrf-disable` |
| 3 | `we-adr-accept` | `docs/forge-e3-accept-config-adr` |
| 4 | `we-hygiene` | `chore/forge-e4-sentinel-hygiene` (comments; may be GH-only + status md) |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Before E2 symbol edits — `node .gitnexus/run.cjs analyze` (or `npx gitnexus analyze`) on tip, then `impact` on each `SecurityConfig` being changed; treat UNKNOWN as unresolved and confirm with text inventory.  
**Do not** implement Activities, S2B-11 shim, S2C-12, #238, or #241.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**` gateway route  
- Full **S2B-11** gateway validate compatibility layer / `{valid,violations[]}` rewrite  
- **S2C-12** quote BFF → approval-routing  
- ConstraintSolver placeholder replacement (S2B-8)  
- CI runner acquisition (#238), agent Kafka migration (#241)  
- Full Azure AD B2C resource-server enforcement  
- Merging Jules #402/#404 as written  
- Frappe `cpq_beta` WhatsApp big-bang  
- Re-fixing PriceBook N+1 (already C6)  
- Live staging cutover checkbox execution (ops; scripts already in Wave B)

---

## 7. Goal text (paste into `/goal` when forging)

```text
Implement Open CRM/CPQ Wave E per docs/forge/14-wave-e-architecture.md: (E1) publish CSRF edge-only ADR rejecting Jules #402/#404 enable-all Cookie CSRF on STATELESS microservices; (E2) align pricing-service and pricebook-service SecurityConfig to csrf.disable() + STATELESS to match crm/quote/product; (E3) Accept docs/backlog/ADR-configuration-api-reconciliation.md with frozen open-question defaults — do NOT implement S2B-11 shim; (E4) GitHub hygiene — close superseded Bolt N+1 PRs (C6), comment/close #402/#404 against E1/E2, triage #414/#416 as legacy/FP, close obsolete #415. Use GitNexus impact before SecurityConfig edits; re-analyze if index stale. Do not implement activities API, S2B-11 validate rewrite, S2C-12, #238, or #241. Evidence: ADR files landed, rg CookieCsrfTokenRepository limited to gateway, docs/forge/impl/e*.md status done, merge+push main.
```

---

## 8. Architect sign-off checklist

- [x] Waves A–D verified complete on tip `8cd0ca4`  
- [x] S2B-11 ADR confirmed still **Proposed** — full impl **not** scheduled  
- [x] CSRF inventory confirmed (gateway ON; 7 services disable; pricing+pricebook Cookie CSRF anomaly)  
- [x] WhatsApp named-param evidence + #414 FP admission recorded  
- [x] PriceBook N+1 confirmed fixed on main (C6)  
- [x] CI #415 obsolete vs current `ci.yml`  
- [x] PR DAG parallelizable (E1∥E2∥E3 → E4) without same-file fights  
- [x] Holds respected: Activities, S2B-11 impl, S2C-12, #238, #241  
- [x] PM authorizes forge execution (`forge-wave-e` / goal text in §7)  
- [ ] Post-merge: E2 `rg` clean; E3 Status Accepted; E4 PRs closed  

**PM WARNING (GitNexus):** `SecurityConfig` impact is **UNKNOWN** (Spring config beans — no resolvable callers). E2 proceeds only with text confirmation: FE uses gateway `:8080`; no `XSRF`/`withCredentials` clients hit pricing/pricebook directly. Re-run `impact` after index refresh before merge.

**FORGE AUTHORIZED** — E1∥E2∥E3 → E4.
