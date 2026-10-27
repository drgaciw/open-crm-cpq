# CRM forecast definitions spec (DRAFT)

**Status:** DRAFT 2026-10-25, SQ1 decided 2026-10-26, SQ2 and SQ7 decided 2026-10-27, for owner review. Documentation only. Does not accept an ADR or authorize any story or code. Tracked as PL-6, RA-54 / DRG-588, OQ22. Source review: [crm-forecasting-requirements-maturity-review.md](crm-forecasting-requirements-maturity-review.md). Separate from RA-66 / DRG-600 (re-forecast on amendment save), which may consume these definitions.

Untagged statements restate an owner decision or a fact from the review. **[INFERENCE]** marks drafting proposals that need confirmation. **OPEN** marks a choice for the owner.

## 1. Decisions applied throughout
- **D1 (FQ1):** sales leadership owns every forecast definition here and is the consumer.
- **D2 (FQ2):** the forecast unit is TCV, in the tenant base currency (ADR-032 D-06: sums use base amounts only).
- **D3 (FQ3):** the renewal forecast source of truth is contract ARR in CAM.
- **D5 (SQ1, decided 2026-10-26, 07:50 Europe/Dublin, Jev router recommendation about 0.7 confidence):** deal `amount` is TCV as entered, labelled "Amount (TCV)" in this spec and the UI. A term field on deals goes to the backlog (see section 5); no ARR is derived from deals until then.
- **D6 (SQ2 and SQ7, decided 2026-10-27, 08:46 Europe/Dublin; owner delegated to Grok Bot, which chose Jev router option B at about 0.7 confidence; runner-up option A, CAM only with amount locked, about 0.25):** renewal deals stay in the forecast. Amount is prefilled from CAM (ARR x term) and editable. Forecast is deduplicated by contract id; the deal wins when present. Guards: H1 store CAM TCV next to the edited amount and flag a difference over a threshold (threshold open, SQ8); H2 a renewal deal with no contract id is counted but flagged as an exception; H3 a CAM row with no deal still counts; H4 a closed-lost deal drops the CAM row, no fallback to the CAM figure.
- **D4 (FQ10):** renewal TCV = contract ARR in CAM x renewal term, with these guards:
  - G1 partial years are prorated by month (TCV = ARR x months / 12);
  - G2 ramps: TCV = sum over periods of (period ARR x period duration in years);
  - G3 no renewal term yet: use the expiring contract's term and set `term_assumed = true` on the row;
  - G4 ARR is stored next to TCV so ARR views keep working.

## 2. Common forecast row [INFERENCE: proposed shape]
Every forecast type emits rows with: `tenant`, `source_type` (deal, contract, usage), `source_id`, `forecast_type` (F1 to F10), `period` (close or renewal month), `tcv_base`, `arr_base` (nullable for non-recurring deals), `base_currency`, `term_months`, `term_assumed`, `cam_tcv_base`, `variance_flag`, `exception_flag`, `contract_id`, `computed_at`, `rule_version`.

## 3. TCV mappings for existing values
| Source value | Today | TCV mapping | Status |
|---|---|---|---|
| Deal `amount` (F1, F2) | One number, no term semantics (`Deal.java`) | "Amount (TCV)": `amount` is TCV as entered, no conversion; `arr_base` null for deals | **DECIDED SQ1** (D5). Deal term field is a backlog item |
| Deal `weighted_amount` | `amount x probability / 100` | Weighted TCV = Amount (TCV) x probability / 100 | Follows D5 |
| Renewal deal (`DealType` renewal) versus CAM contract | Both may exist | Renewal deals stay in the forecast. Amount prefilled from CAM (ARR x term, D4), editable. Dedupe by contract id: the deal wins when present; CAM TCV stored beside the edited amount; difference over a threshold flagged; deal with no contract id counted and flagged as exception; CAM row with no deal counts; closed-lost deal removes the CAM row | **DECIDED SQ2 and SQ7** (D6). **OPEN SQ8**: variance threshold |
| arr-bridge components (F7) | Annualized ARR per line, proposal only | Component TCV = component ARR x term months / 12 (G1), ramp per G2 | **OPEN SQ3**: which term applies (new term, remaining term, or coterm term); also blocked on arr-bridge owner decision 1 (component dictionary) |
| One-time charges | Not ARR (DRG-572) | Add at face value to TCV, ARR 0 [INFERENCE] | **OPEN SQ4** |
| Usage (F8) | Projection only | Projected spend over the term as TCV [INFERENCE] | **OPEN SQ5** |

## 4. Forecast types

### F1 Stage pipeline summary
- **Owner:** sales leadership. **Unit:** TCV (base currency).
- **Inputs:** non-deleted deals: stage, "Amount (TCV)" as `base_amount` (D5), `base_currency`. Renewal deals included (D6); amount prefilled from CAM, editable.
- **Rule:** count and sum Amount (TCV) per stage, with no term conversion; deals not converted to the base currency are counted in `unconvertedCount`, not summed (ADR-032).
- **Cadence:** on read (live query) [INFERENCE].
- **Acceptance:** AC-F1-1 totals equal the sum of converted deal TCV per stage; AC-F1-2 an unconverted deal is never summed; AC-F1-3 mixed currencies never sum `amount`.

### F2 Weighted pipeline
- **Owner:** sales leadership. **Unit:** weighted TCV.
- **Inputs:** F1 inputs (Amount (TCV), D5) plus `probability`.
- **Rule:** weighted TCV = Amount (TCV) x probability / 100, HALF_UP 2 decimals. Stage defaults 10, 20, 40, 60, 80, won 100, lost 0 (current code).
- **Cadence:** recomputed on deal save; read live.
- **Acceptance:** AC-F2-1 weighted value matches the formula; AC-F2-2 won is 100 percent, lost is 0.
- **Open:** FQ5 (stage probabilities as tenant config, rep override). Renewal deals per D6 (SQ2 and SQ7 decided).

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
- **Inputs:** F2 rows by `expected_close_date`; F6 rows by renewal date, after the D6 dedupe by contract id.
- **Rule:** sum TCV per period. A contract is counted once (D6): the renewal deal row if present, else the CAM row; a closed-lost renewal deal removes the CAM row. A renewal is counted in the period of its renewal start date [INFERENCE].
- **Cadence:** on read.
- **Acceptance:** AC-F5-1 period totals equal the sum of rows whose date falls in the period; AC-F5-2 a contract with both a renewal deal and a CAM row is counted once, at the deal amount.
- **Open:** FQ6 (fiscal calendar, quota).

### F6 Renewal forecast
- **Owner:** sales leadership. **Unit:** TCV, with ARR stored beside it (G4).
- **Inputs:** CAM contract: lines and ARR, end date, term, status, `precedence_flag` (OQ27); renewal term if known.
- **Rule:** D4 with G1 to G3. One row per expiring contract where the contract is active and not superseded (precedence) [INFERENCE: avoids double counting both contracts under new-contract-preferred]. Auto-renew off by default (RA-62) does not change the amount [INFERENCE]. Dedupe by contract id (D6): when a renewal deal exists, the deal amount is used and the CAM TCV is stored beside it as `cam_tcv_base` with a `variance_flag` when the difference exceeds the threshold (SQ8); a CAM row with no deal counts; a closed-lost deal drops the CAM row (no fallback); a renewal deal with no contract id counts with `exception_flag = true`.
- **Cadence:** recomputed when the CAM contract changes or a renewal term is set [INFERENCE]; DRG-600 may trigger it.
- **Acceptance:** AC-F6-1 ARR 120,000 for a 12-month term gives TCV 120,000; AC-F6-2 ARR 120,000 for 18 months gives 180,000 (G1); AC-F6-3 ramp 100,000 for 12 months then 120,000 for 12 months gives 220,000 (G2); AC-F6-4 no renewal term uses the expiring term and sets `term_assumed = true` (G3); AC-F6-5 `arr_base` is stored on every row (G4); AC-F6-6 a superseded contract produces no row. AC-F6-7 renewal deal edited to 130,000 over CAM 120,000 counts 130,000 and stores 120,000 beside it; AC-F6-8 closed-lost renewal deal: contract contributes 0; AC-F6-9 renewal deal with no contract id counts and is flagged as exception; AC-F6-10 CAM row with no deal counts.
- **Open:** risk tier definition; SQ8 variance threshold.

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
- **SQ2 DECIDED 2026-10-27** (D6): renewal deals stay in F1, F2, F5 and dedupe with CAM rows by contract id, deal wins.
- **SQ3** which term converts arr-bridge ARR to TCV.
- **SQ4** one-time charges in TCV.
- **SQ5** usage projection rule and term.
- **SQ7 DECIDED 2026-10-27** (D6): renewal deal amount is prefilled from CAM ARR x term and editable; not locked.
- **SQ8 (open)** variance threshold between the edited renewal deal amount and CAM TCV (absolute, percent, or both; per tenant or global). Not decided.
- **SQ9 (open) [INFERENCE]** whether the prefill refreshes when CAM ARR changes after the deal is created (for example after an amendment, DRG-600), or stays as first prefilled. Not decided.
- **SQ6** cadence and freshness of F6 recomputation (event-driven versus nightly).
