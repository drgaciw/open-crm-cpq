# CRM forecast definitions spec (DRAFT)

**Status:** DRAFT 2026-10-25, SQ1 decided 2026-10-26, for owner review. Documentation only. Does not accept an ADR or authorize any story or code. Tracked as PL-6, RA-54 / DRG-588, OQ22. Source review: [crm-forecasting-requirements-maturity-review.md](crm-forecasting-requirements-maturity-review.md). Separate from RA-66 / DRG-600 (re-forecast on amendment save), which may consume these definitions.

Untagged statements restate an owner decision or a fact from the review. **[INFERENCE]** marks drafting proposals that need confirmation. **OPEN** marks a choice for the owner.

## 1. Decisions applied throughout
- **D1 (FQ1):** sales leadership owns every forecast definition here and is the consumer.
- **D2 (FQ2):** the forecast unit is TCV, in the tenant base currency (ADR-032 D-06: sums use base amounts only).
- **D3 (FQ3):** the renewal forecast source of truth is contract ARR in CAM.
- **D5 (SQ1, decided 2026-10-26, 07:50 Europe/Dublin, Jev router recommendation about 0.7 confidence):** deal `amount` is TCV as entered, labelled "Amount (TCV)" in this spec and the UI. A term field on deals goes to the backlog (see section 5); no ARR is derived from deals until then.
- **D4 (FQ10):** renewal TCV = contract ARR in CAM x renewal term, with these guards:
  - G1 partial years are prorated by month (TCV = ARR x months / 12);
  - G2 ramps: TCV = sum over periods of (period ARR x period duration in years);
  - G3 no renewal term yet: use the expiring contract's term and set `term_assumed = true` on the row;
  - G4 ARR is stored next to TCV so ARR views keep working.

## 2. Common forecast row [INFERENCE: proposed shape]
Every forecast type emits rows with: `tenant`, `source_type` (deal, contract, usage), `source_id`, `forecast_type` (F1 to F10), `period` (close or renewal month), `tcv_base`, `arr_base` (nullable for non-recurring deals), `base_currency`, `term_months`, `term_assumed`, `computed_at`, `rule_version`.

## 3. TCV mappings for existing values
| Source value | Today | TCV mapping | Status |
|---|---|---|---|
| Deal `amount` (F1, F2) | One number, no term semantics (`Deal.java`) | "Amount (TCV)": `amount` is TCV as entered, no conversion; `arr_base` null for deals | **DECIDED SQ1** (D5). Deal term field is a backlog item |
| Deal `weighted_amount` | `amount x probability / 100` | Weighted TCV = Amount (TCV) x probability / 100 | Follows D5 |
| Renewal deal (`DealType` renewal) versus CAM contract | Both may exist | CAM contract ARR wins for F6 (D3); renewal deal is excluded from F6 | **OPEN SQ2**: exclude renewal deals from F1/F2 too, or keep them and dedupe. **OPEN SQ7**: renewal deal amount overwritten by CAM ARR x term or entered by hand |
| arr-bridge components (F7) | Annualized ARR per line, proposal only | Component TCV = component ARR x term months / 12 (G1), ramp per G2 | **OPEN SQ3**: which term applies (new term, remaining term, or coterm term); also blocked on arr-bridge owner decision 1 (component dictionary) |
| One-time charges | Not ARR (DRG-572) | Add at face value to TCV, ARR 0 [INFERENCE] | **OPEN SQ4** |
| Usage (F8) | Projection only | Projected spend over the term as TCV [INFERENCE] | **OPEN SQ5** |

## 4. Forecast types

### F1 Stage pipeline summary
- **Owner:** sales leadership. **Unit:** TCV (base currency).
- **Inputs:** non-deleted deals: stage, "Amount (TCV)" as `base_amount` (D5), `base_currency`.
- **Rule:** count and sum Amount (TCV) per stage, with no term conversion; deals not converted to the base currency are counted in `unconvertedCount`, not summed (ADR-032).
- **Cadence:** on read (live query) [INFERENCE].
- **Acceptance:** AC-F1-1 totals equal the sum of converted deal TCV per stage; AC-F1-2 an unconverted deal is never summed; AC-F1-3 mixed currencies never sum `amount`.

### F2 Weighted pipeline
- **Owner:** sales leadership. **Unit:** weighted TCV.
- **Inputs:** F1 inputs (Amount (TCV), D5) plus `probability`.
- **Rule:** weighted TCV = Amount (TCV) x probability / 100, HALF_UP 2 decimals. Stage defaults 10, 20, 40, 60, 80, won 100, lost 0 (current code).
- **Cadence:** recomputed on deal save; read live.
- **Acceptance:** AC-F2-1 weighted value matches the formula; AC-F2-2 won is 100 percent, lost is 0.
- **Open:** FQ5 (stage probabilities as tenant config, rep override); SQ2 and SQ7 for renewal deals.

### F3 AI win-probability rollup
- **Owner:** sales leadership. **Unit:** weighted TCV with an interval.
- **Inputs:** F2 rows plus model-predicted probability.
- **Rule:** replace stage probability with predicted probability; roll up by segment, region, deal type, close quarter.
- **Cadence:** OPEN.
- **Acceptance:** AC-F3-1 the rollup reconciles to F2 when the predicted probability equals the stage probability.
- **Open:** FQ8 (requirement or illustrative prose). Not buildable until FQ8 is decided.

### F4 Forecast categories and submissions
- **Owner:** sales leadership. **Unit:** TCV.
- **Status:** placeholder. Categories, submitters and overrides are **OPEN FQ4**. No rule is defined here.

### F5 Period forecast
- **Owner:** sales leadership. **Unit:** TCV per period.
- **Inputs:** F2 rows by `expected_close_date`; F6 rows by renewal date.
- **Rule:** sum TCV per period. A renewal is counted once, in the period of its renewal start date [INFERENCE].
- **Cadence:** on read.
- **Acceptance:** AC-F5-1 period totals equal the sum of rows whose date falls in the period.
- **Open:** FQ6 (fiscal calendar, quota).

### F6 Renewal forecast
- **Owner:** sales leadership. **Unit:** TCV, with ARR stored beside it (G4).
- **Inputs:** CAM contract: lines and ARR, end date, term, status, `precedence_flag` (OQ27); renewal term if known.
- **Rule:** D4 with G1 to G3. One row per expiring contract where the contract is active and not superseded (precedence) [INFERENCE: avoids double counting both contracts under new-contract-preferred]. Auto-renew off by default (RA-62) does not change the amount [INFERENCE].
- **Cadence:** recomputed when the CAM contract changes or a renewal term is set [INFERENCE]; DRG-600 may trigger it.
- **Acceptance:** AC-F6-1 ARR 120,000 for a 12-month term gives TCV 120,000; AC-F6-2 ARR 120,000 for 18 months gives 180,000 (G1); AC-F6-3 ramp 100,000 for 12 months then 120,000 for 12 months gives 220,000 (G2); AC-F6-4 no renewal term uses the expiring term and sets `term_assumed = true` (G3); AC-F6-5 `arr_base` is stored on every row (G4); AC-F6-6 a superseded contract produces no row.
- **Open:** risk tier definition; SQ2.

### F7 ARR movement bridge
- **Owner:** sales leadership. **Unit:** TCV per component, ARR stored beside it.
- **Inputs:** arr-bridge components per line (proposal, DRG-575).
- **Rule:** per section 3 row "arr-bridge".
- **Cadence:** written at pricing time (arr-bridge spec).
- **Acceptance:** AC-F7-1 component TCV sums to the line TCV change.
- **Open:** SQ3; arr-bridge owner decisions 1 to 8; FQ9 (bridge as reporting model).

### F8 Usage and spend projection
- **Owner:** sales leadership. **Unit:** TCV (projected).
- **Inputs:** SaaS meter readings; on-prem self-report (PL-1 to PL-5, not built).
- **Rule:** OPEN SQ5. On-prem excluded until PL-1 to PL-5 exist.
- **Acceptance:** none until SQ5 is decided.

### F9 Re-forecast on amendment save
- **Owner:** sales leadership (definition); feature tracked as RA-66 / DRG-600. **Unit:** TCV.
- **Rule:** on amendment execution, recompute the F6 and F7 rows of the affected contract with D4. This spec only defines the target; DRG-600 designs the trigger.
- **Acceptance:** AC-F9-1 after an executed amendment the F6 row equals a fresh F6 computation.

### F10 Snapshots and accuracy
- **Owner:** sales leadership. **Unit:** TCV.
- **Status:** placeholder. Snapshot cadence, retention and accuracy metric are **OPEN FQ7**.

## 5. Open items
- FQ4 to FQ9 from the review: open, not decided here.
- **SQ1 DECIDED 2026-10-26:** deal amount is TCV as entered, "Amount (TCV)"; deal term field to backlog (RA-102 / DRG-636).
- **SQ2** renewal deals versus CAM renewal rows in F1, F2, F5 (exclude or dedupe).
- **SQ3** which term converts arr-bridge ARR to TCV.
- **SQ4** one-time charges in TCV.
- **SQ5** usage projection rule and term.
- **SQ7 (open, tied to SQ2)** for renewal deals: is the amount overwritten by the CAM ARR x term calculation (D4) or entered by hand? Raised by Jev router. Not decided.
- **SQ6** cadence and freshness of F6 recomputation (event-driven versus nightly).
