# DRG-588 — CRM forecast definitions spec (decision frame)

**Status:** Draft. Documentation only. Not an ADR. Accepting this note authorizes **no** product code, **no** Flyway / CRM schema change, **no** feature flag, **no** Kafka topic or registry edit, **no** gateway route, and **no** forecast runtime.

**Story:** [DRG-588](https://linear.app/drgamtd-workspace/issue/DRG-588/crm-forecasting-requirements-maturity-review-separate-from-amendment) (RA-54 / PL-6 / OQ22).

**Date:** 2026-11-06. **Base:** `origin/main` at the commit that carries this file.

**Product / schema / flag NO-GO.** This frame records forecast *definitions* and open forks only. It does not implement, schedule, or unblock CRM forecast persistence, Deal field changes, CAM schema, or any runtime re-forecast path.

## Non-overlap with DRG-600

This story is **SEPARATE** from [DRG-600](https://linear.app/drgamtd-workspace/issue/DRG-600/automatic-crm-re-forecast-on-amendment-save-net-new) (RA-66, automatic CRM re-forecast on amendment save).

| This frame (DRG-588) | DRG-600 |
|---|---|
| Defines who owns the forecast, which unit it uses, and open forks for categories / cadence / mapping | Designs the *trigger* that recomputes forecast rows when an amendment is saved or executed |
| Does not design the amendment → CRM push | May later *consume* settled definitions; is not blocked by FQ4–FQ10 remaining open |
| Does not replace, redesign, or gate DRG-600 | Continues against its own proposed spec on `main`: [specs/amendment-reforecast.md](specs/amendment-reforecast.md) |

Parking lot PL-6 and OQ22 already state the same distinction. This note does not reopen it.

## Sources read for this draft

| Source | On `main`? | Use |
|---|---|---|
| [renewals-amendments-parking-lot.md](../renewals-amendments-parking-lot.md) PL-6 | Yes | Scope boundary vs amendment re-forecast |
| [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) OQ22 | Yes | Parked review tracker |
| [specs/amendment-reforecast.md](specs/amendment-reforecast.md) | Yes | DRG-600 proposed design; cites unmerged review SHA |
| Maturity review on branch `docs/crm-forecasting-maturity-review-2026-10-23` | **No** — absent from `main` | Owner decision sentences for FQ1–FQ3; open FQ list; prior draft definitions |
| Prior draft `crm-forecast-definitions-spec.md` on that same branch | **No** | Prior art only. Not copied wholesale. Not Adopted on `main` |

Pinned prior-art links (unmerged; source can change):

- Review: <https://github.com/drgaciw/open-crm-cpq/blob/docs/crm-forecasting-maturity-review-2026-10-23/docs/renewals-amendments/crm-forecasting-requirements-maturity-review.md>
- Prior definitions draft: <https://github.com/drgaciw/open-crm-cpq/blob/docs/crm-forecasting-maturity-review-2026-10-23/docs/renewals-amendments/crm-forecast-definitions-spec.md>

Later mapping choices (SQ*) that appear only on that unmerged branch are **out of scope** for this frame. They are not Adopted here.

## How to read statuses

| Label | Meaning in this file |
|---|---|
| **Adopted** | Owner decision recorded with citation. Restated here; not re-decided. |
| **Status: Proposed** | Open fork. Options listed. **Do not treat as decided.** No default is an acceptance. |

This file does **not** invent Adopted answers for FQ4–FQ10.

---

## Adopted — FQ1 to FQ3

Recorded by the owner on 2026-10-23 (about 21:39 Europe/Dublin). Primary citation: Linear DRG-588 comment of that day, and the decision sentences on the unmerged maturity-review branch (commit `19ac0f6c4495aeeef4772b9ac4749eca7a7079e5` on `docs/crm-forecasting-maturity-review-2026-10-23`). Word for word from that review section 7.1:

```text
### 7.1 Owner decisions (2026-10-23, about 21:39 Europe/Dublin)
- **FQ1. DECIDED:** sales leadership owns the forecast definition and is its consumer.
- **FQ2. DECIDED:** the forecast unit is TCV.
- **FQ3. DECIDED:** the source of truth for the renewal forecast is contract ARR in CAM.
```

### FQ1 — Owner and consumer

| Field | Value |
|---|---|
| **Status** | **Adopted** |
| **Decision** | Sales leadership owns the forecast definition and is its consumer. |
| **Citation** | Owner decision 2026-10-23 (~21:39 Europe/Dublin); Linear DRG-588; review §7.1 |

### FQ2 — Forecast unit

| Field | Value |
|---|---|
| **Status** | **Adopted** |
| **Decision** | The forecast unit is TCV. |
| **Citation** | Owner decision 2026-10-23 (~21:39 Europe/Dublin); Linear DRG-588; review §7.1 |
| **Note** | ADR-032 (Accepted) already sums pipeline queries in `base_amount`. This Adopted sentence does **not** by itself pick tenant-base-currency wording beyond FQ2's "TCV". Currency of amendment deltas stays with DRG-600 / ADR-032 readers. |

### FQ3 — Renewal forecast source of truth

| Field | Value |
|---|---|
| **Status** | **Adopted** |
| **Decision** | The source of truth for the renewal forecast is contract ARR in CAM. |
| **Citation** | Owner decision 2026-10-23 (~21:39 Europe/Dublin); Linear DRG-588; review §7.1 |

---

## Open forks — FQ4 to FQ10 (`Status: Proposed`)

Each fork is **Status: Proposed**. Options are listed for an owner session. None of the options below is Adopted by this note. Prior unmerged drafting may name a preferred candidate; that preference is **not** an Adopted answer on `main`.

### FQ4 — Forecast categories and submissions

**Question.** Are forecast categories (commit, best case, pipeline, omitted) needed, and who may submit or override?

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A.** No categories in v1 | Stage pipeline + weighted TCV only (F1/F2 as today). No commit / best-case submit workflow. |
| **B.** Salesforce-style categories | Commit, best case, pipeline, omitted; rep submits; manager may override. |
| **C.** Categories without override | Categories exist; only the owning rep may set them; no manager override. |

Owner decision: ___ (name, time).

### FQ5 — Stage probabilities

**Question.** Should stage probabilities become tenant configuration, and may a rep override probability per deal?

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A.** Keep hardcoded stage map | Current entity defaults (10 / 20 / 40 / 60 / 80; won 100; lost 0). No per-deal override. |
| **B.** Tenant-configurable stage map | Defaults per tenant; still no per-deal override. |
| **C.** Tenant config + per-deal override | Rep may override probability; audit who/when. |

Owner decision: ___ (name, time).

### FQ6 — Fiscal calendar and quota

**Question.** Fiscal calendar and period definition; is quota in scope?

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A.** Calendar month / quarter only | Period = close or renewal calendar month; no quota entity. |
| **B.** Tenant fiscal calendar, no quota | Fiscal periods configurable; quota out of scope. |
| **C.** Fiscal calendar + quota | Period forecast versus quota; quota model in scope. |

Owner decision: ___ (name, time).

### FQ7 — Snapshots and accuracy

**Question.** Snapshot cadence and accuracy metric, if any?

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A.** No snapshots in v1 | Live queries only; no forecast-versus-actual history. |
| **B.** Periodic snapshots | Nightly (or weekly) frozen TCV by period; accuracy metric deferred. |
| **C.** Snapshots + accuracy metric | Snapshots plus a stated accuracy measure (for example forecast vs closed-won TCV). |

Owner decision: ___ (name, time).

### FQ8 — AI agent rollups

**Question.** Are the AI agent rollups (win-probability forecast; renewal forecast by risk tier) requirements or illustrative prose?

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A.** Illustrative prose only | Agent markdown is not a product requirement; no acceptance criteria. |
| **B.** Requirements, deferred | Named as forecast types with acceptance later; not buildable until models and storage are specified. |
| **C.** Requirements, near-term | Must reconcile to weighted TCV when predicted probability equals stage probability; storage and cadence required. |

Owner decision: ___ (name, time).

### FQ9 — ARR bridge as reporting model

**Question.** Does the arr-bridge (per-line ARR components) become the reporting model the forecast reads?

**Status: Proposed.** Related: [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) / `specs/arr-bridge.md` (proposal).

| Option | What the owner would be choosing |
|---|---|
| **A.** Bridge stays pricing-side only | Forecast does not read arr-bridge components. |
| **B.** Bridge feeds renewal / movement reporting | F7 (ARR movement) reads accepted bridge components; still needs bridge owner decisions. |
| **C.** Bridge is the sole TCV movement model | All amendment/renewal forecast deltas derive from bridge components. |

Owner decision: ___ (name, time).

### FQ10 — ARR → TCV conversion for renewals

**Question.** Given FQ2 (unit = TCV) and FQ3 (renewal source = CAM contract ARR), what rule converts renewal ARR into TCV?

**Status: Proposed.** (This frame does **not** Adopt an answer. An ARR×term candidate appears in unmerged prior art and Linear discussion; until the owner records Adopted wording against a landed doc, FQ10 stays open here.)

| Option | What the owner would be choosing |
|---|---|
| **A.** ARR × renewal term | `renewal_TCV = CAM_ARR × renewal_term` (years or months/12). Needs guards for partial years, ramps, and missing term. |
| **B.** ARR only in renewal views; TCV elsewhere | Keep renewal forecast in ARR; convert only at rollup boundaries. Conflicts with Adopted FQ2 unless the owner narrows FQ2. |
| **C.** Deal amount wins; CAM ARR is reference only | Renewal TCV comes from the renewal deal amount; CAM ARR is not multiplied. Conflicts with reading FQ3 as the amount source. |
| **D.** Explicit term table | Owner publishes a closed mapping (for example 12→1.0, 24→2.0, 36→3.0) with no proration; ramps out of scope until later. |

Owner decision: ___ (name, time).

---

## Summary table

| ID | Topic | Status on this frame |
|---|---|---|
| FQ1 | Owner / consumer = sales leadership | **Adopted** (2026-10-23) |
| FQ2 | Unit = TCV | **Adopted** (2026-10-23) |
| FQ3 | Renewal SoT = CAM contract ARR | **Adopted** (2026-10-23) |
| FQ4 | Categories / submissions | **Proposed** |
| FQ5 | Stage probability config / override | **Proposed** |
| FQ6 | Fiscal calendar / quota | **Proposed** |
| FQ7 | Snapshots / accuracy | **Proposed** |
| FQ8 | AI rollups = requirement or prose | **Proposed** |
| FQ9 | Arr-bridge as forecast model | **Proposed** |
| FQ10 | ARR → TCV conversion rule | **Proposed** |

## Explicit holds

- **No product code** under `backend/` or `frontend/`.
- **No Flyway**, Deal schema, CAM schema, or forecast table.
- **No feature flag** and no `opencrm.*` property for forecast runtime.
- **No** change to DRG-600 design choices; that story keeps its own Proposed spec.
- **No** merge of the unmerged maturity-review branch by this note; landing that branch is a separate act.

## Suggested next owner session

1. Decide FQ10 (ARR→TCV) so Adopted FQ2 and FQ3 have a closed conversion rule.
2. Decide FQ4–FQ9 in any order; none blocks DRG-600 design drafting.
3. Only after FQ10 (and any mapping choices the owner wants) are Adopted on a landed doc: allow a later implementation story. That story is not this file.
