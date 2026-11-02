# Contract Lifecycle Management for CPQ (TDD & UAT Specification)

> **Provenance:** The path given to the agent
> (`/home/username01/cmano-clone/tests/uat/contract-lifecycle-management-cpq.md`) is
> host-local and was not present on GitHub `drgaciw/cmano-clone`, Ubuntu-Host workers,
> or this cloud VM (Linear DRG-718 also tracks the fetch). This document reconstructs an
> executable UAT/TDD suite from the Contract Lifecycle Management agent PRD
> (`agents/contract-lifecycle-management.md`), skill
> (`agents/skills/contract-lifecycle-management/SKILL.md`), CAM subscription /
> amendment / document-apply contracts, quote continuations (DRG-543), and ERP stub
> behavior — so browser UAT against `http://127.0.0.1:4200` can proceed with TDD.
> Replace this file with the host-authored original when it is synced.

**Target UI:** Angular CRM at `http://127.0.0.1:4200` (proxies `/api` → gateway `:8080`).  
**Owning services:** `quote-service` (`:8084`) for quote accept / continuations;
`contract-asset-management` (`:8087`) for subscriptions, amendments, document apply;
`erp-adapter-service` for ERP contract sync (stub).

---

## Part I: Lifecycle phases

### 1. Quote acceptance → subscription / contract record
Accepted quotes drive subscription creation (CAM `AcceptedQuoteSubscriptionService`).
UI precursor: Quotes shell + DRAFT→ACCEPTED path. Subscription list is the OHS for
active commercial commitments (`GET /api/v1/subscriptions`).

### 2. Milestone & status lifecycle
Subscriptions expose status transitions: activate / suspend / cancel.
Deprecated date-only renew (`POST .../renew`) must remain **410 Gone** with successor
`POST /api/v1/quotes/continuations` (DRG-543).

### 3. Amendments (document spine)
CAM `AmendmentController` implements create → revise → submit → approve → accept →
execute → signature. **Not on the gateway** today (controller Javadoc: unreleased;
no ownership checks). UI UAT records GAP/BLOCKED until a gateway route + roles land.

### 4. Renewals / continuations
Priced renewals author via `POST /api/v1/quotes/continuations` (`QuoteContinuationRequest`,
including `renewalSourceMode=FROM_ACTIVE_ASSETS`). Applied on quote acceptance.

### 5. Document apply (internal)
`POST /internal/v1/amendments/{id}:apply` is internal (`Internal.Invoke`); not browser-
routed. Unit/integration tests remain the TDD gate (incl. DRG-651 null-start /
`ASSET_LIFECYCLE_HOLD`).

### 6. ERP synchronization
`GET /api/v1/erp/contracts/{customerExternalId}` — adapter may return **501** until
implemented; UAT accepts stub as GAP/BLOCKED, not FAIL, when body identifies erp-adapter.

---

## Part II: Invariants (TDD contracts)

1. **`INV-CLM-SUB-01`:** Subscription API returns `SubscriptionDto` fields only (never JPA entity).
2. **`INV-CLM-RENEW-01`:** `POST /subscriptions/{id}/renew` → 410 + `SUBSCRIPTION_RENEW_DEPRECATED`.
3. **`INV-CLM-CONT-01`:** Continuations endpoint is live through UI origin (auth/validation 4xx OK; not gateway 503).
4. **`INV-CLM-AMD-HOLD-01`:** Amendment HTTP API is unreleased via gateway (404) until ownership + route land.
5. **`INV-CLM-APPLY-01`:** Document apply is internal-only; mutating apply while S4 hold armed → `ASSET_LIFECYCLE_HOLD`.
6. **`INV-CLM-NULLSTART-01`:** Null start on apply → `START_DATE_MISSING` (DRG-651).
7. **`INV-CLM-TYPE-01`:** FE shared-models cover `SubscriptionDto` (+ amendment view keys for future UI).

---

## Part III: Executable UAT scenarios (against `:4200`)

### UAT-CLM-01 — Quotes shell (contract precursor)
**Steps:** Open `/quotes`.  
**Expect:** Heading visible; no uncaught page errors.

### UAT-CLM-02 — Subscriptions OHS through UI origin
**Steps:** `GET /api/v1/subscriptions?page=0&size=1` from browser origin.  
**Expect:** Live CAM (2xx), not 503.

### UAT-CLM-03 — Deprecated renew successor
**Steps:** `POST /api/v1/subscriptions/1/renew?newEndDate=2027-01-01` (CSRF).  
**Expect:** HTTP **410**, errorCode `SUBSCRIPTION_RENEW_DEPRECATED`, Link successor continuations.

### UAT-CLM-04 — Quote continuation surface
**Steps:** `POST /api/v1/quotes/continuations` with minimal/invalid body or FROM_ACTIVE_ASSETS.  
**Expect:** Live quote-service response (4xx business/validation OK; not 503).

### UAT-CLM-05 — Quote DRAFT create (activation precursor)
**Steps:** Create DRAFT quote via API fixture; open detail UI.  
**Expect:** DRAFT status; quote number assigned.

### UAT-CLM-06 — Amendment API via UI origin
**Steps:** `GET /api/v1/amendments/{uuid}`.  
**Expect:** Gateway **404** (unreleased) **or** live CAM if route added — record GAP if 404 with controller note.

### UAT-CLM-07 — ERP contracts stub
**Steps:** `GET /api/v1/erp/contracts/uat-clm`.  
**Expect:** Implemented 2xx **or** GAP/BLOCKED on 501 erp-adapter stub.

### UAT-CLM-08 — Null-start / hold unit gates
**Steps:** Assert in-tree `DocumentApplyNullStartDateTest` + `AssetLifecycleApplyHoldTest` / readiness.  
**Expect:** Gates present; Maven unit suites green.

### UAT-CLM-09 — Type safety contract parity
**Steps:** FE `subscription.model.ts` + parity spec cover Java `SubscriptionDto` / amendment view keys.  
**Expect:** Vitest parity green; UAT check PASS.

---

## Part IV: Traceability matrix

| Phase | REQ / INV | Automated check id | UAT | Sign-off |
| --- | --- | --- | --- | --- |
| Quotes shell | — | `contract-lifecycle.ui.quotes-shell` | UAT-CLM-01 | CRM UI |
| Subscriptions | INV-CLM-SUB-01 | `contract-lifecycle.api.subscriptions-up` | UAT-CLM-02 | CAM |
| Deprecated renew | INV-CLM-RENEW-01 | `contract-lifecycle.api.renew-deprecated` | UAT-CLM-03 | CAM |
| Continuations | INV-CLM-CONT-01 | `contract-lifecycle.api.continuations-live` | UAT-CLM-04 | Quote |
| Quote create | — | `contract-lifecycle.ui.quote-detail` | UAT-CLM-05 | Quote |
| Amendments | INV-CLM-AMD-HOLD-01 | `contract-lifecycle.api.amendments-gateway` | UAT-CLM-06 | CAM/GW |
| ERP | — | `contract-lifecycle.api.erp-contracts` | UAT-CLM-07 | ERP |
| Apply integrity | INV-CLM-NULLSTART-01 / HOLD | Maven + unit-gate checks | UAT-CLM-08 | CAM |
| Type safety | INV-CLM-TYPE-01 | `contract-lifecycle.type-safety.contract-parity` | UAT-CLM-09 | FE |

### Entry criteria
- CRM UI `:4200`, gateway `:8080`, quote-service, CAM `:8087` reachable through the proxy.
- UAT run id prefix `UAT-<runId>-`.

### Exit criteria
- All `UAT-CLM-01`–`09` PASS, BLOCKED (env), or GAP (documented) with zero unexplained FAIL.
- Shared-models CLM contract-parity Vitest green.
- Evidence under `frontend/.ci-tmp/uat-full/`.
