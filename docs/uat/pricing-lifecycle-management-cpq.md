# Pricing Lifecycle Management for CPQ (TDD & UAT Specification)

> **Provenance:** The path given to the agent
> (`/home/username01/cmano-clone/tests/uat/pricing-lifecycle-management-cpq.md`) is
> host-local and was **not** present on this cloud VM or as a `*-cpq.md` sibling in
> `drgaciw/cmano-clone`. This document adapts the Project Aegis PLM UAT
> (`pricing-lifecycle-management.md`, UAT-PLM-01..06) onto **open-crm-cpq** surfaces
> that actually exist at `http://127.0.0.1:4200`: price books, pricing customers,
> rule sets, pricing simulator (`POST /api/v1/pricing/execute` / `simulate`), quotes,
> and renewal-uplift unit gates. Aegis-only engines (Van Westendorp analyzer,
> `PlmStrategySimulator` seeded portfolio twin, pocket-margin auditor service) are
> recorded as **GAP** when absent — not redefined away. Replace this file with the
> host-authored `*-cpq.md` when it is synced.

**Target UI:** Angular CRM at `http://127.0.0.1:4200` (proxies `/api` → gateway `:8080`).  
**Owning services:** `pricebook-service` (`:8089`), `pricing-service` (`:8082`),
`quote-service` (`:8084`); ERP via `erp-adapter-service` (often stub).

---

## Part I: Lifecycle phases → open-crm-cpq mapping

| Aegis PLM phase | open-crm-cpq surface | Notes |
| --- | --- | --- |
| 1. WTP / value modeling | Price-book **entry** `floorPrice` + list price; no Van Westendorp service | Floor gate is entry CRUD; WTP survey engine = GAP |
| 2. Packaging / tiers | Price books + entries; product bundles; rule sets | Anti-cannibalization `TierFenceValidator` = GAP |
| 2B. Strategy simulation | `/pricing/simulator` → `POST /api/v1/pricing/execute` (+ `/simulate`) | Seeded 24-month cohort twin = GAP; live execute/simulate is the Day-1 gate |
| 3. Q2C readiness | Quotes shell + DRAFT quote + pricing execute; ERP contracts | Penny ERP ledger parity = GAP when erp-adapter 501 |
| 4. Leakage / waterfall | `PricingResult.waterfall` steps from execute | Dedicated pocket-margin auditor = GAP; assert step conservation on wire |
| 5. Migration / sunset | Rule-set `ACTIVE→DEPRECATED`; price-book soft-delete; `RenewalUpliftResolver` | Grandfathering cohort engine = GAP; uplift-cap unit suite is TDD gate |

---

## Part II: Invariants (TDD contracts)

1. **`INV-PLM-FLOOR-01`:** Price-book entries accept `floorPrice`; list ≥ floor when both set (BE validation). Van Westendorp PMC≤OPP≤IPP≤PME ordering is **GAP** (no analyzer service).
2. **`INV-PLM-PKG-01`:** Multi-entry price books and rule-sets are reachable through UI origin. Add-on undercut / tier-fence rejection is **GAP** until a packaging validator lands.
3. **`INV-PLM-SIM-01`:** `POST /api/v1/pricing/execute` (and `/simulate`) return `PricingResult` through the UI origin for a seeded customer + book entry. Bit-identical portfolio `SimulationWorldHash` is **GAP**.
4. **`INV-PLM-Q2C-01`:** Quotes shell + DRAFT create live; ERP contracts may be GAP/501.
5. **`INV-PLM-LKG-01`:** For a successful execute, waterfall `LIST_PRICE` / `NET_PRICE` amounts are present and `netPrice` equals sum of line `finalPrice` (wire conservation). Full off-invoice pocket auditor = GAP.
6. **`INV-PLM-MIG-01`:** Rule-set deprecate path exists (`POST …/rule-sets/{id}/deprecate`); `RenewalUpliftResolver` unit tests cover uplift caps. Aegis grandfathering credit = GAP.
7. **`INV-PLM-TYPE-01`:** FE `pricing.model.ts` + `backend-fe-contract-parity.spec.ts` cover price-book / customer / rule-set / `PricingResult` / `FinancialMetrics` (incl. `exitArr`) Java DTO fields.

---

## Part III: Executable UAT scenarios (against `:4200`)

### UAT-PLM-01 — Floor / WTP bounds (Phase 1)
**Steps:** Open `/price-books`; create draft book + entry with `listPrice` and `floorPrice` (e.g. 1000 / 600 = 40% margin above $600 cost-proxy).  
**Expect:** Entry persists; floor present on GET. Van Westendorp survey intersections → **GAP**.

### UAT-PLM-02 — Packaging / rule-set shell (Phase 2)
**Steps:** Open `/rule-sets` and `/price-books`; list APIs 2xx.  
**Expect:** Shells render; live APIs. TierFence cannibalization reject → **GAP**.

### UAT-PLM-03 — Pricing strategy simulation (Phase 2B)
**Steps:** Open `/pricing/simulator`; `POST /api/v1/pricing/execute` with seeded pricing-customer + price-book entry (unique region).  
**Expect:** `PricingResult.success === true`; waterfall + financialMetrics present. Seeded 24-month NRR twin → **GAP**.

### UAT-PLM-04 — Q2C readiness (Phase 3)
**Steps:** Open `/quotes`; create DRAFT quote; optional ERP `GET /api/v1/erp/contracts/…`.  
**Expect:** Quote DRAFT live; ERP 2xx **or** GAP on 501 stub.

### UAT-PLM-05 — Waterfall conservation (Phase 4)
**Steps:** From execute result, assert `waterfall.netPrice` equals Σ `lineResults[].finalPrice` and steps include `LIST_PRICE` + `NET_PRICE`.  
**Expect:** Conservation holds; pocket-margin alert service → **GAP**.

### UAT-PLM-06 — Sunset / uplift gate (Phase 5)
**Steps:** Create DRAFT rule-set (or use fixture); assert deprecate endpoint reachable (4xx business OK if not ACTIVE); assert `RenewalUpliftResolver` / uplift tests exist in-tree. Soft-delete draft price book.  
**Expect:** Deprecate path live; unit gate present. Grandfathering migration credit → **GAP**.

### UAT-PLM-07 — Type safety contract parity
**Steps:** Assert FE models + PLM parity suite cover Java DTOs.  
**Expect:** Artifacts present; Vitest parity green.

---

## Part IV: Traceability matrix

| Phase | REQ / INV | Automated check id | UAT | Sign-off |
| --- | --- | --- | --- | --- |
| Floor / WTP | INV-PLM-FLOOR-01 | `pricing-lifecycle.api.floor-entry` | UAT-PLM-01 | Pricebook |
| Packaging | INV-PLM-PKG-01 | `pricing-lifecycle.ui.rule-sets` / `price-books` | UAT-PLM-02 | Pricing |
| Simulation | INV-PLM-SIM-01 | `pricing-lifecycle.api.execute` / `ui.simulator` | UAT-PLM-03 | Pricing |
| Q2C | INV-PLM-Q2C-01 | `pricing-lifecycle.ui.quotes` / `api.erp` | UAT-PLM-04 | Quote/ERP |
| Leakage | INV-PLM-LKG-01 | `pricing-lifecycle.api.waterfall-conservation` | UAT-PLM-05 | Pricing |
| Sunset | INV-PLM-MIG-01 | `pricing-lifecycle.api.ruleset-deprecate` / `uplift.unit-gate` | UAT-PLM-06 | Pricing |
| Type safety | INV-PLM-TYPE-01 | `pricing-lifecycle.type-safety.contract-parity` | UAT-PLM-07 | FE |

### Entry criteria
- CRM UI `:4200`, gateway `:8080`, pricing-service `:8082`, pricebook-service `:8089` reachable through the proxy.
- UAT run id prefix `UAT-<runId>-`.

### Exit criteria
- All `UAT-PLM-01`–`07` PASS, BLOCKED (env), or GAP (documented) with zero unexplained FAIL.
- Shared-models PLM contract-parity Vitest green.
- Evidence under `frontend/.ci-tmp/uat-full/`.
