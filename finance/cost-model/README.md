# Finance cost model (interim producing system)

This directory is the **producing system of the CPQ cost basis** until Finance runs a driver-based FP&A platform (finance decision F-3, 2026-10-15). It emits the canonical **F-2 cost export**, the CSV that pricing-service ingests for contribution margin (DRG-526 / BC-03-13).

**The ERP is the system of record for actual spend. This cost model produces the per-unit cost basis.** SaaS ERPs hold no per-product standard cost, so the ERP adapter is not the source.

## How an export is approved and loaded

1. Finance changes the assumptions in `build_mock_cost_export.py` (or, later, its successor) in a **pull request**. The PR review is the Finance sign-off.
2. Every row carries the approver in `approvedBy`, and every export carries one monotonic `version`. Raise `version` for any change; reusing a version with different figures is rejected (`VERSION_CONFLICT`).
3. An `Admin` (ADR-016 app role) posts the CSV to pricing-service:

   ```http
   POST /api/v1/cost-basis/imports
   Content-Type: text/csv

   productExternalId,component,billingUnit,unitCost,currency,effectiveFrom,version,approvedBy
   ...
   ```

   - Rows with an unknown product, a negative cost, a billing unit that contradicts the catalog, or a malformed field are rejected and listed in the response. The rest of the file loads.
   - Re-posting the same version changes nothing.
   - Every post is recorded in the audit trail (`GET /api/v1/cost-basis/imports`): version, approver, the acting principal from the token, time, file SHA-256, and row counts.

Run the generator with `python3 build_mock_cost_export.py <outdir>`. It writes `cpq-cost-export-v1.csv` and `margin-check.csv`. It is deterministic, and its output is byte-identical to the approved v1 export.

## Export contract (F-2)

| Field | Meaning |
|-------|---------|
| `productExternalId` | Catalog product id, which must exist in pricing-service's catalog replica |
| `component` | `DIRECT`, `COST_TO_SERVE`, `USAGE_UNIT`, `LABOUR` or `PASS_THROUGH` |
| `billingUnit` | Seat, subscription, rated unit or hour. It must match the catalog where the catalog states one, and all components of one product and date must share it |
| `unitCost` | Decimal with at most 4 places, ≥ 0. Recurring products are costed per billing unit per month; usage, labour and one-time products per unit |
| `currency` | ISO 4217, one per product and date |
| `effectiveFrom` | Calendar date. Refresh is quarterly, on the first day of the quarter, plus an ad hoc refresh within 5 business days of a ±10% move |
| `version` | Monotonic per export. A higher version replaces rows with the same product, component and date; the earlier rows stay on record |
| `approvedBy` | Finance approver, one per export |

**Staleness.** A cost effective from `E` is usable while `asOf < E + 6 months` (two quarters). From then on the product has no cost, and margin fails closed (it routes to Deal Desk and is never computed from a placeholder).

## Version 1 is a benchmark mock

- **Effective 2026-10-01, version 1, USD. Approved 2026-10-15 by Danny** (project owner, acting as Finance approver).
- The figures are sized from **public industry benchmarks, not from this company's spend**. A SaaS seat has a 73.8% margin at list, against a 71–74% median. There is one mock product per catalog type, plus `PROD-SEAT`, the AC-02 acceptance fixture.
  - HYBRID_MIGRATION_RAMP (costed from its components) and CUSTOM (costed per deal) are deliberately absent, so they fail closed.
- **Stale from 2027-04-01.** After that, every product fails closed.
- **Never seeded into an environment.** It is a test fixture only: `backend/services/pricing-service/src/test/resources/fixtures/cost-basis/cpq-cost-export-v1.csv`. No migration, profile or startup hook loads it.
- **Replace it with a cost basis from real actuals** (hosting, support, labour) before margin-based policy or `acv-version=term-years` governs real deals.
- **Open with Deal Desk:** hardware is at 20% margin, below the 35% software floor even at list. It needs its own floor, or an exclusion from the margin rule. This is not decided here.

## Target producing system

| Stage | Producing system |
|-------|------------------|
| Now | This directory: assumptions plus generator, approved by PR |
| Target | A driver-based FP&A platform (for example Pigment, Aleph or Abacum; Anaplan at large scale) that pulls GL actuals from the ERP and emits the same F-2 export each quarter |
| Hosting input | Azure Cost Management export (AKS), or a unit-economics FinOps tool |

The ingestion contract does not change when the producing system does.
