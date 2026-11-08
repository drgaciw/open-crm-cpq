# DRG-553 — Separate effective date from action date (trigger dates): Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted Cluster E forks D1–D6, reopen Adopted DRG-572 Forks 1–7, amend the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md), accept [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md), reopen the Accepted proration formula, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a gateway route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `lifecycle-intents` / Cluster E dates. **Story:** [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553/separate-effective-date-from-action-date-trigger-dates) (RA-19). Linear read this pass: **In Review**. Notion: [RA-19](https://app.notion.com/p/3fbf7cb4e4df81bbb739ecd8f7f0c504) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Cluster E D1–D6 + § One-time charges (DRG-572) | **Adopted baseline** for price-as-of vs proration, nullable service/acceptance dates, backdating check shape, header vs line dates, quote-request hold, rate-period date columns, and ONE_TIME early service window |
| [cluster-e-lifecyclechange-model-decision-frame.md](cluster-e-lifecyclechange-model-decision-frame.md) § DRG-553 | Historical D1–D6 options/rationale; dispositions already indexed. Do not re-litigate Adopted cells |
| [specs/lifecycle-intents.md](specs/lifecycle-intents.md) Dates + OQ5 / OQ6 / OQ7 / OQ10 + fixtures | Proposed acceptance / fixture reading for DRG-646; authorizes no code |
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) D-01 | **Accepted** day-stub; this brief names which stored date fills the existing effective-date input and does **not** reopen the formula |
| [rca-sda-amendment-placement.md](../rca-sda-amendment-placement.md) §3 | Market/RCA orientation: action date kept separate from effective date; document signed/executed time ≠ change effective date |
| Mid-term [V6](../mid-term-amendment-policy-spec.md) + FR-17 / AC-S3-8 | Requirements text: store action and effective separately; backdating needs reason + approval. Thresholds stay open (mid-term OQ9) |
| [story-drg-572-one-time-retroactive-charge-lines-decision-frame.md](story-drg-572-one-time-retroactive-charge-lines-decision-frame.md) | Adopted dispositions cited; residual interaction with DRG-553 clocks is Proposed below |

**In-flight alignment (do not contradict Accepted / draft semantics):**

| Ticket / PR | Linear / GH | What this brief must not contradict |
|---|---|---|
| [DRG-646](https://linear.app/drgamtd-workspace/issue/DRG-646) [#702](https://github.com/drgaciw/open-crm-cpq/pull/702) | Done / merged | Proposed backdated / future-dated / same-day / null-effective-end fixtures under `specs/fixtures/lifecycle-intents/`; no business choice Adopted without owner words + time |
| [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) [#691](https://github.com/drgaciw/open-crm-cpq/pull/691) | Done / merged (phase A) | `lifecycle_changes` stores `action_date` + `contract_effective_date` (+ nullable SQL columns for service/acceptance/backdating). Java create/response today maps only the two required dates (`verified by reading code`) |
| [DRG-651](https://linear.app/drgamtd-workspace/issue/DRG-651) [#761](https://github.com/drgaciw/open-crm-cpq/pull/761) | Done / merged | Apply blocks when start is missing (`START_DATE_MISSING` → durable `APPLY_BLOCKED`). Do not reopen reject-at-apply |
| [DRG-715](https://linear.app/drgamtd-workspace/issue/DRG-715) [#815](https://github.com/drgaciw/open-crm-cpq/pull/815) | In Progress / draft | Test-only replay coverage for null-start apply; **no** production / Flyway change. Cite relation only |
| [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) [#654](https://github.com/drgaciw/open-crm-cpq/pull/654) | In Review / merged frame | Adopted early `ONE_TIME` window + optional line `effectiveDate`; later-scope delivery, not first-slice AMEND widen |
| [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) | Separate gate | Backdating approval workflow / AC-S3-8 remain on S3 authorization, not this brief |
| Dual-read / NgRx / Jev / ramp / S4 | `AGENT.md` | Untouched |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. `opencrm.pricing.ramp.enabled` stays default off. An implementation brief for DRG-553 (or any product change that binds apply / entitlement / billing clocks, widens LifecycleChange wire fields, or changes null-start authoring rules) is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/run.cjs` / no indexed repo. Graph impact is **UNKNOWN** (unresolved), not a clean check. |

## Problem (RA-19)

Research gap (Salesforce RCA AssetAction `ActionDate` vs state-period start; Zuora per-action trigger dates; SAP Contract Change Activation Date; Salesforce CPQ Amendment Start Date): OpenCRM needs a distinct **action date** from **effective / trigger dates** for correct proration, backdating, and ARR reporting.

Cluster E already Adopted the storage defaults (D1–D6): four commercial dates on the amendment document header; line `effective_from` / `effective_to`; rate periods keep their own bounds and trace to the line; `price_as_of` selects rate lookup without moving the Accepted proration effective-date input; backdating of `contract_effective_date` before `action_date` requires `backdating_reason_code` in storage; `QuoteContinuationRequest` stays single-`effectiveDate` until a separately authorized change. DRG-646 landed Proposed fixtures. S5 phase A landed `lifecycle_changes` columns. DRG-651 landed apply-time `START_DATE_MISSING`.

There is still no owner-scored Decision brief that (a) cites those Adopted date semantics without reopening them, and (b) lists **residual delivery forks** — wire shape across quote LifecycleChange vs CAM document vs continuation request; apply vs entitlement vs billing clocks; mid-term V6 approval thresholds; interaction with Adopted DRG-572 retroactive `ONE_TIME` windows; and null-start / `START_DATE_MISSING` relation to [#815](https://github.com/drgaciw/open-crm-cpq/pull/815) — that must close before product code, schema, or flags for remaining DRG-553 work.

Without that split, a later story could re-litigate D1–D6, invent a single merged clock for apply/entitlement/billing, treat DRG-572's early service window as the backdating predicate, claim authoring-time null starts are allowed because apply already blocks, or treat merge of this brief as wire authorization.

Linear DRG-553 (`Linear read` 2026-11-06): Medium; **In Review**; related DRG-646 / DRG-583 / DRG-572 / DRG-598 / DRG-607 / DRG-547. Attachments: Cluster E draft PR [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) (merged; later Adopted via the delegated record). Wave-2 lane refill comment records docs-only Decision brief scope with product-code **NO-GO**.

## Market research — cited as forks only

Issue research links are orientation only. They are **not** selected options and do not override the delegated record.

| Vendor pattern | Observed options (research) | How this brief treats them |
|---|---|---|
| Salesforce RCA `AssetAction.ActionDate` vs state-period start | Action timestamp separate from effective-dated asset state | Orientation for A1 / RCA §3 — not a Salesforce field map |
| Zuora order-action trigger dates (`ContractEffective`, `ServiceActivation`, `CustomerAcceptance`) | Three named triggers may differ per action | Naming parallel for the three stored triggers — OpenCRM semantics stay as Adopted D1–D4 |
| SAP CPQ/BRIM Contract Change Activation Date | Activation drives pricing | Fork illustration for OQ-553-2 billing/apply clock — not SAP parity |
| Salesforce CPQ Amendment Start Date | Single amendment start often used as effective | Fork illustration for a collapsed clock — conflicts with Adopted four-date header unless owner supersedes |

## Already Adopted (do not re-decide)

Restated from the delegated record § Cluster E D1–D6, § One-time charges / DRG-572, ADR-035 D-06, Accepted proration D-01, and landed schema (`read` / `verified by reading code`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Action date and change effective / trigger dates are **separate**. Document `executed_at` / signed time is **not** the commercial effective date | RCA §3; ADR-035 D-06; FR-17 / mid-term V6 text |
| A2 | Amendment document header stores `action_date` and `contract_effective_date` `NOT NULL`; `service_activation_date` and `customer_acceptance_date` nullable; `backdating_reason_code` required when contract effective precedes action (`chk_amendment_document_backdating`) | `V1_7__contract_documents.sql`; Cluster E D2-A / D3-A |
| A3 | **D1-A:** `price_as_of` selects rate lookup (`ACTION_DATE` / `EFFECTIVE_DATE` / `LINE_START`). Proration still begins at the amendment effective date; first-slice mapping is `contract_effective_date` (matching line `effective_from`). Does **not** reopen the Accepted day-stub | Delegated D1; proration ADR D-01 |
| A4 | **D2-A:** Service activation and customer acceptance stay nullable and are **not** proration inputs | Delegated D2 |
| A5 | **D3-A:** Keep the existing backdating check on `contract_effective_date` vs `action_date`. Approval thresholds stay on S3 / mid-term OQ9 — not decided as numbers here | Delegated D3 |
| A6 | **D4-A:** Four dates stay on the **header**. Line keeps `effective_from` / `effective_to`. First-slice lines set `effective_from` = `contract_effective_date`. A line whose service period starts earlier is DRG-572 | Delegated D4 |
| A7 | **D5-A:** `QuoteContinuationRequest` stays one `effectiveDate` until a separately authorized change. Proposed future mapping (not implemented by this brief): request `effectiveDate` → `contract_effective_date`, submit date → `action_date`, `price_as_of = EFFECTIVE_DATE` | Delegated D5 |
| A8 | **D6-A:** Add **no** action/trigger columns to `subscription_rate_periods`. Periods keep V1_6 bounds; trace via `amendment_document_line_id` / `revision_no` | Delegated D6 |
| A9 | DRG-572 Adopted: optional `servicePeriodStart`/`End` (both absent or both present); early window **allowed for `ONE_TIME` only**; optional per-line `effectiveDate` falls back to `contractEffectiveDate`; triggers remain on the header; `ADD` + `ONE_TIME` for non-commitment charge; billing fields-only / no receiver | Delegated § One-time charges; 572 Forks 2–6 |
| A10 | Apply refuses a missing start: `START_DATE_MISSING` / `APPLY_BLOCKED` (DRG-651 / #761). Null effective **end** open-ended rule is DRG-624 — not this story | Landed CAM apply; UAT notes cite the code |
| A11 | Holds: no product code / Flyway / dual-read / Jev / ramp / S4 changes from this brief; S3 remains separately authorized for backdating approval behavior | AGENT.md; Cluster E holds |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-553-1 | Wire shape (LifecycleChange API / entity vs SQL vs CAM document vs continuation request) | Document the **gap**: SQL `lifecycle_changes` already has four dates + `backdating_reason_code`; Java create/response maps only `actionDate` + `contractEffectiveDate` (`verified by reading code`). CAM document already has the full four. Canonical commercial record for apply remains the CAM document header (A2/A6). Closing the quote-service mapping is a **later implementation brief** after this Decision scores — not a silent widen in this PR | Map all four + reason on LifecycleChange create now; collapse to two dates everywhere; put four dates on `QuoteContinuationRequest` now (reopens A7) | Yes — any public create/submit contract that claims four triggers |
| OQ-553-2 | Apply vs entitlement vs billing clocks | Three clocks stay distinct: **apply / ledger** uses `contract_effective_date` (A3/A6); **entitlement activation** may use `service_activation_date` when present else falls back to contract effective (nullable per A4); **billing charge start** is **out of slice** until a billing receiver exists (A9). `action_date` is audit / backdating predicate only, not a ledger start | Single merged clock; bill on `action_date`; entitle only on `customer_acceptance_date` with hard refuse when null (conflicts with A4 unless owner supersedes) | Yes — fulfilment / billing / ARR reporting ACs |
| OQ-553-3 | Mid-term amendment backdating (V6 approval) | Keep A5 storage check. Customer-visible transition stays **refused** until owner sets reason-code vocabulary + approval thresholds (mid-term OQ9 / AC-S3-8 / register OQ17). Numbers and roles are **not** invented here; S3 owns the workflow once separately authorized | Extend check to service/acceptance dates now (reopens D3-B); forbid all backdating (D3-C); invent Finance threshold numbers in this brief | Soft for storage; hard for AC-S3-8 / customer-visible submit |
| OQ-553-4 | Retroactive charges vs DRG-572 | Cite A9 without reopening. Early `ONE_TIME` **service period** before line effective is **not** the backdating predicate (trigger before `actionDate`). Backdating remains A5 / OQ-553-3. Retroactive windows do not move `action_date` or invent a fifth header trigger | Treat early service window as backdating; require `backdating_reason_code` whenever `servicePeriodStart` < `actionDate`; reopen 572 Fork 3 to refuse | Soft until ONE_TIME delivery; hard if someone couples 572 to V6 refuse |
| OQ-553-5 | Null-start / `START_DATE_MISSING` / [#815](https://github.com/drgaciw/open-crm-cpq/pull/815) | Keep A10 apply block. Authoring: required `actionDate` + `contractEffectiveDate` on LifecycleChange create stay required (`@NotNull` today). Residual Proposed choice for a **null start used as effective** (lifecycle-intents OQ10: reject vs default to `actionDate`) stays open and is **not** closed by #815. #815 (DRG-715) is **test-only** replay proof of durable `START_DATE_MISSING` — scoring this brief does **not** merge #815 and does **not** authorize production schema nullability changes | Default null start to `actionDate` at authoring; allow null `effective_from` in DB (conflicts with `NOT NULL` columns); treat #815 merge as product authorization | Yes — apply / authoring boundary; soft for #815 test merge |
| OQ-553-6 | Which trigger sibling policies read for price (beyond A3) | First-slice writers keep `price_as_of = EFFECTIVE_DATE` → `contract_effective_date` (A3). Other policies (maintenance, true-up, conversion) that need `serviceActivation` or `customerAcceptance` declare that pin in **their** Decision / implementation brief — not here | Force every policy onto `customer_acceptance_date`; always `action_date` (reopens D1-B) | Soft for amendment first slice; hard for sibling policy ACs |
| OQ-553-7 | Fixture / error-code freeze | Keep DRG-646 fixtures `status: Proposed` until an implementation brief; do not invent new public date-error codes here beyond landed `START_DATE_MISSING` | Rename fixture expectations now; add gateway-only date routes in this brief | Soft (docs) |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-553-1 — Wire shape

| Option | What the owner would be choosing |
|---|---|
| **A. Document the gap; CAM header remains canonical; map later (Recommended, Proposed)** | `V1_14__lifecycle_changes.sql` already declares `service_activation_date`, `customer_acceptance_date`, `backdating_reason_code` beside the two required dates. `LifecycleChange` / `CreateLifecycleChangeRequest` / `LifecycleChangeResponse` today expose only `actionDate` and `contractEffectiveDate` (`verified by reading code` at base SHA). CAM `AmendmentDocument` already maps all four + reason. Closing the quote-service JPA/API gap is delivery work **after** forks close — not this docs PR. Does not reopen A2/A6/A7. |
| **B. Map all four + reason on LifecycleChange create in the next product PR without a scored brief** | Premature public contract; still needs OQ-553-2..5. |
| **C. Collapse everywhere to two dates** | Reopens A2/A4 and Zuora-shaped three-trigger storage. |
| **D. Add four dates to `QuoteContinuationRequest` now** | Reopens A7 / D5-B. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-553-2 — Apply vs entitlement vs billing clocks

| Option | What the owner would be choosing |
|---|---|
| **A. Three clocks; apply = contract effective; entitlement = service activation with fallback; billing deferred (Recommended, Proposed)** | Preserves A3/A4/A9. `action_date` stays the actor/submit clock and the backdating compare (A5). No billing receiver is selected here. |
| **B. One merged clock** | Collapses research gap; conflicts with A1. |
| **C. Entitlement hard-requires `customer_acceptance_date`** | Reopens A4 / D2-C unless signature-policy scoped. |
| **D. Billing starts on `action_date`** | Conflicts with Accepted proration input mapping (A3) and with external-billing hold (A9). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-553-3 — Mid-term amendment backdating (V6)

| Option | What the owner would be choosing |
|---|---|
| **A. Storage check stays; approval thresholds stay open on S3 (Recommended, Proposed)** | Restates A5. Fixtures (`backdated.json`) keep `customerVisibleTransition: refused until reason + approval`. No Finance number invented. |
| **B. Extend SQL check to service/acceptance** | Reopens D3-B; migration. |
| **C. Forbid backdating** | Reopens D3-C. |
| **D. Publish threshold integers in this brief** | Belongs to Finance / OQ17; out of scope. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-553-4 — Retroactive charges vs DRG-572

| Option | What the owner would be choosing |
|---|---|
| **A. Keep 572 early window separate from V6 backdating (Recommended, Proposed)** | A9 stands. `servicePeriodStart` before line effective (ONE_TIME only) is commercial service coverage, not "trigger before actionDate". OQ-553-3 still governs header backdating. |
| **B. Require backdating reason whenever service period precedes actionDate** | Couples unrelated predicates; invents a rule neither frame Adopted. |
| **C. Re-open 572 Fork 3 to refuse early windows** | Forbidden — delegated disposition already chose allow-for-ONE_TIME. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-553-5 — Null-start / `START_DATE_MISSING` / #815

| Option | What the owner would be choosing |
|---|---|
| **A. Apply block stays; authoring keeps required dates; OQ10 stays Proposed; #815 is test-only (Recommended, Proposed)** | A10 unchanged. Lifecycle create already `@NotNull` on action/contract-effective. lifecycle-intents OQ10 (reject vs default null start to action date) remains an open Proposed slot for any path that could still present a null start to apply. [#815](https://github.com/drgaciw/open-crm-cpq/pull/815) proves durable replay of `START_DATE_MISSING` without seeding NULL into `NOT NULL` columns — cite, do not treat as product GO. |
| **B. Default null start to `actionDate` at authoring** | Would close lifecycle-intents OQ10 option "default"; needs owner words + time. |
| **C. Allow null `effective_from` / `start_date` in schema** | Conflicts with landed `NOT NULL` columns; Flyway **NO-GO** here. |
| **D. Scoring this brief merges or authorizes #815 production behavior** | Explicitly forbidden. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-553-6 — Sibling-policy price trigger

| Option | What the owner would be choosing |
|---|---|
| **A. First slice stays A3; siblings declare their own pin later (Recommended, Proposed)** | Matches lifecycle-intents OQ5 left open without reopening D1. |
| **B. All policies use `customer_acceptance_date`** | Reopens D1-C-shaped behavior as a universal rule. |
| **C. All policies use `action_date`** | Reopens D1-B. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-553-7 — Fixture / error-code freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposed DRG-646 fixtures until implementation brief (Recommended, Proposed)** | Soft docs gate. Landed `START_DATE_MISSING` stays the apply code. |
| **B. Rename or add gateway date routes now** | Premature public contract; gateway **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-553-1..OQ-553-7 (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (Java / TypeScript) that widens LifecycleChange request/response to the four triggers, changes apply/entitlement/billing clock binding, or changes null-start authoring rules.
2. **NO-GO:** Flyway / schema for new date columns on rate periods, continuation request, or nullability changes to `effective_from` / `start_date`.
3. **NO-GO:** feature flags (including any `opencrm.jev.*.enabled`), outbox / relay / ramp enablement.
4. **NO-GO:** new or changed gateway routes / filters for date apply semantics.
5. **NO-GO:** treating this file, Cluster E, DRG-646 fixtures, DRG-572 Adopted cells, DRG-651, or [#815](https://github.com/drgaciw/open-crm-cpq/pull/815) as authorization to ship remaining RA-19 product behavior.
6. Even after forks close: S3 remains separately authorized for V6 approval workflow; DRG-572 ONE_TIME delivery remains later-scope; dual-read / NgRx / ramp / S4 / Jev holds stay as in `AGENT.md`; Accepted proration formula stays closed.

Linear status for DRG-553 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No date-column / nullability migration here |
| Flag / deploy | No flag flip; no deploy |
| Gateway | No date-semantics route or filter |
| Jev / ramp / dual-read / NgRx / S4 | Untouched |
| S3 / V6 approval | Not authorized; thresholds not invented |
| DRG-572 | Adopted cells cited only; no reopen |
| #815 / DRG-715 | Test-only relation; not product GO |
| Accepted proration ADR | Not reopened |

## Out of scope

- Reopening D1–D6 or DRG-572 Forks 1–7.
- Editing the delegated decision record, lifecycle-intents Dates section status, OpenAPI, or UI spec.
- Implementing AC-8 / AC-9 from `lifecycle-intents.md` or running fixtures against a database.
- Closing mid-term OQ9 threshold integers or register OQ17.
- Authorizing S3, S4, S6, billing receiver, or ARR reporting code.
- Merging or extending [#815](https://github.com/drgaciw/open-crm-cpq/pull/815).

## References

- Linear: DRG-553, DRG-646, DRG-583, DRG-572, DRG-651, DRG-715, DRG-581, DRG-607, DRG-547.
- GitHub: Cluster E frame [#651](https://github.com/drgaciw/open-crm-cpq/pull/651); fixtures [#702](https://github.com/drgaciw/open-crm-cpq/pull/702); null-start apply [#761](https://github.com/drgaciw/open-crm-cpq/pull/761); null-start replay IT [#815](https://github.com/drgaciw/open-crm-cpq/pull/815); 572 frame [#654](https://github.com/drgaciw/open-crm-cpq/pull/654).
- Docs: `cluster-e-lifecyclechange-model-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `specs/lifecycle-intents.md`, `story-drg-572-one-time-retroactive-charge-lines-decision-frame.md`, `adr-amendment-proration-formula.md`, `rca-sda-amendment-placement.md`, `mid-term-amendment-policy-spec.md`.
- Fixtures: `specs/fixtures/lifecycle-intents/backdated.json`, `future-dated.json`, `same-day.json`, `null-effective-end.json`.
- Code (read-only evidence): `V1_7__contract_documents.sql`, `V1_14__lifecycle_changes.sql`, `LifecycleChange.java`, `CreateLifecycleChangeRequest.java`, `DocumentApplyPeriods.java` (`START_DATE_MISSING`).
