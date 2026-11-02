# Asset Lifecycle Management for CPQ (TDD & UAT Specification)

> **Provenance:** The path given to the agent
> (`/home/username01/cmano-clone/tests/uat/asset-lifecycle-management-cpq.md`) is
> host-local and was not present on GitHub `drgaciw/cmano-clone` nor on this cloud
> VM. This document reconstructs an executable UAT/TDD suite from Open-CRM-CPQ
> Asset Management acceptance criteria (DRG-652 children DRG-654/655/656/658/659),
> the S4 asset-lifecycle hold, and DRG-651 null-start apply blocking — so browser
> UAT against `http://127.0.0.1:4200` can proceed with TDD. Replace this file with
> the host-authored original when it is synced.

**Target UI:** Angular CRM at `http://127.0.0.1:4200` (proxies `/api` → gateway `:8080`).  
**Owning service:** `contract-asset-management` (`:8087`) via gateway paths `/api/v1/assets/**`.  
**Related CRM surfaces:** Quotes, Deals, Organizations (no dedicated Assets nav today).

---

## Part I: Lifecycle phases

### 1. Activation → Asset create (DRG-654)
Exactly one Asset per activated contract line, linked to `contractLineId`,
`subscriptionId`, and `entitlementId` when present. Idempotent on replay.
`AssetCreated` + audit via outbox. Acceptance-time writer may be held
(see asset write hold / S4 hold).

### 2. Amendment ChangeLine → Amend / Terminate / Supersede (DRG-655)
ChangeLine actions map to asset ops inside the S4 apply unit:
Update → `AssetAmended`; Remove/Terminate → `AssetTerminated`;
Swap/Upgrade → new asset + `AssetSuperseded` (create-before-terminate).
Failures use existing `APPLY_UNIT_*` codes. S4 hold returns HTTP 503
`ASSET_LIFECYCLE_HOLD` when mutate-capable applies are refused.

### 3. History, supersede chain, as-of installed base (DRG-656)
OHS queries: history, bidirectional supersede chain, account installed base as-of.

### 4. Renewal quotes from active assets (DRG-658)
Rep can open a renewal with mode “from active assets”; lines from account/contract
active assets as of term end; superseded/terminated excluded; pricing via existing
renewal policy; audit records source mode and asset ids.

### 5. Installed-base reporting (DRG-659)
Active assets as-of; upcoming renewals by end-date window; churn excluding supersedes.

### 6. Apply period integrity (DRG-651)
Null source `startDate` / missing effective-from blocks apply with free-form
`START_DATE_MISSING` — no ledger mutation.

---

## Part II: Invariants (TDD contracts)

1. **`INV-ALM-CREATE-01`:** One asset per activated line; replay idempotent.
2. **`INV-ALM-HOLD-01`:** Mutating S4 apply while hold is armed → 503 `ASSET_LIFECYCLE_HOLD`, audited; completed replay still succeeds.
3. **`INV-ALM-HIST-01`:** History + supersede chain rebuild from events.
4. **`INV-ALM-ASOF-01`:** As-of installed base excludes not-yet-effective and already-ended rows.
5. **`INV-ALM-RENEW-01`:** From-assets renewal excludes superseded/terminated; uses renewal pricing policy.
6. **`INV-ALM-REPORT-01`:** Churn counts terminate/downsize only; supersede ≠ churn.
7. **`INV-ALM-NULLSTART-01`:** Null start → `START_DATE_MISSING`, no rate-period write.
8. **`INV-ALM-AUTH-01`:** Asset OHS requires `InstalledBase.Read`.

---

## Part III: Executable UAT scenarios (against `:4200`)

### UAT-ALM-01 — CRM shell reaches Quotes / Deals (browser)
**Steps:** Open `http://127.0.0.1:4200/quotes` and `/deals`.  
**Expect:** Lists render without uncaught page errors; gateway CRM/quote preflight up.

### UAT-ALM-02 — Assets OHS reachable through UI origin
**Steps:** From the browser origin, `GET /api/v1/assets?accountId=uat&asOf=2026-01-01`.  
**Expect:** Live CAM (2xx/4xx auth/validation), not gateway 503 circuit fallback. Missing `InstalledBase.Read` may be 403 — still proves service up.

### UAT-ALM-03 — Asset reports endpoints through UI origin
**Steps:** `GET /api/v1/assets/reports/upcoming-renewals?accountId=uat&withinDays=30` and
`GET /api/v1/assets/reports/churn?accountId=uat&periodStart=2026-01-01&periodEnd=2026-12-31`.  
**Expect:** Live CAM response (not 503 fallback).

### UAT-ALM-04 — Quote create UI (activation precursor)
**Steps:** Create a DRAFT quote via CRM UI with UAT-prefixed name.  
**Expect:** Detail page opens; status DRAFT; quote number assigned.

### UAT-ALM-05 — Renewal-from-assets surface
**Steps:** Inspect Quotes UI for a “from active assets” / renewal-from-assets control.  
**Expect:** Control present **or** recorded as GAP with API-only path documented
(`QuoteContinuation` + `AssetRenewalDrafts`).

### UAT-ALM-06 — Installed-base UI surface
**Steps:** Search CRM nav/routes for Assets / Installed base.  
**Expect:** Dedicated page **or** GAP (API-only OHS today).

### UAT-ALM-07 — S4 hold observable
**Steps:** With CAM up, attempt a mutate-capable document apply (or read hold readiness).  
**Expect:** Hold armed (`AssetLifecycleReadiness` false) → mutating apply 503
`ASSET_LIFECYCLE_HOLD` **or** BLOCKED if no document fixture in env.

### UAT-ALM-08 — Null start date apply block (unit / API)
**Steps:** Drive or assert DRG-651 fixture: null source start → `START_DATE_MISSING`.  
**Expect:** No ledger write; reason free-form `START_DATE_MISSING`.

---

## Part IV: Traceability matrix

| Phase | REQ | Invariant | Automated check id | UAT | Sign-off |
| --- | --- | --- | --- | --- | --- |
| Activation | DRG-654 | INV-ALM-CREATE-01 | `asset-lifecycle.api.assets-up` | UAT-ALM-02 | CAM |
| Hold | S4 hold | INV-ALM-HOLD-01 | `asset-lifecycle.hold.armed` | UAT-ALM-07 | CAM |
| History | DRG-656 | INV-ALM-HIST-01 / ASOF-01 | `asset-lifecycle.api.history-shape` | UAT-ALM-02 | CAM |
| Renew | DRG-658 | INV-ALM-RENEW-01 | `asset-lifecycle.ui.renewal-from-assets` | UAT-ALM-05 | Quote + UI |
| Reports | DRG-659 | INV-ALM-REPORT-01 | `asset-lifecycle.api.reports-up` | UAT-ALM-03 | CAM |
| Null start | DRG-651 | INV-ALM-NULLSTART-01 | Maven `DocumentApplyNullStartDateTest` | UAT-ALM-08 | CAM |
| CRM shell | — | — | `asset-lifecycle.ui.quotes-shell` | UAT-ALM-01/04 | CRM UI |
| Auth | INV-ALM-AUTH-01 | — | HTTP 401/403 without role | UAT-ALM-02 | Security |

### Entry criteria
- CRM UI `:4200`, gateway `:8080`, quote-service, CAM `:8087` reachable through the proxy.
- UAT run id prefix `UAT-<runId>-`.

### Exit criteria
- All `UAT-ALM-01`–`08` PASS, BLOCKED (env), or GAP (documented missing UI) with zero unexplained FAIL.
- DRG-651 unit tests green.
- Evidence report under `frontend/.ci-tmp/uat-full/`.
