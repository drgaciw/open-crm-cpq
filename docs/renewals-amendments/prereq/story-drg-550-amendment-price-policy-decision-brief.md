# DRG-550 — Amendment price policy, proration, credit/termination fees: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted cells in the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § Catalog and policy / Cluster A 550-F1..F4, does **not** restate or amend the Accepted [amendment proration formula ADR](adr-amendment-proration-formula.md), does **not** accept Cluster A as a second decision authority, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / amendment price policy. **Story:** [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550/amendment-price-policy-proration-and-credittermination-fee-policies) (RA-16). Linear read this pass: **In Review**. Notion: [RA-16](https://app.notion.com/p/3fbf7cb4e4df81d8aea2c2698f8961ab) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / ADR authority (cite, do not reopen Adopted cells):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Catalog and policy + fork index 550-F1..F4 | **Adopted baseline:** `RetainContracted` default, `NoCredit`, `NoFee`, line overrides snapshotted at pricing, Accepted proration formula. Legal-review / Override refuse until named legal permission. Missing commercial inputs refuse; never invent values |
| [cluster-a-amendment-modes-and-policy-decision-frame.md](cluster-a-amendment-modes-and-policy-decision-frame.md) §5 / 550-F1..F4 | Historical frame + rationale; dispositions already indexed. Do not re-litigate Adopted rows |
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) D-01..D-04 | **Accepted** single day-stub formula in `QuoteContinuationPolicy.plan`. Number line remains `none`. This brief **does not invent a second formula**. `None` / `NextCycle` (if added) are switches around that method only |
| [mid-term-amendment-policy-spec.md](../mid-term-amendment-policy-spec.md) §1 item 4, call clarification, OQ10 | Working proposal: flat adjustment + prorated delta modes; OQ10 minimum-commitment downsell still open. Authorizes no code |
| [oq9-pricing-continuation-capability.md](oq9-pricing-continuation-capability.md) | Cite-only: pricing-service cannot yet satisfy AmendmentPolicy + PolicyDecision ids on execute/simulate. **BLOCKS S5** until verified |
| [story-drg-572-one-time-retroactive-charge-lines-decision-frame.md](story-drg-572-one-time-retroactive-charge-lines-decision-frame.md) ([#654](https://github.com/drgaciw/open-crm-cpq/pull/654), merged) | Cite-only: one-time lines later scope; new negative credit-line behavior excluded; credits stay on separately versioned DRG-550 policy |
| Wave-2 pricing-audit Decision brief [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) (DRG-564) | Cite-only: residual PolicyDecision / commercial-decision trail forks. This brief does **not** adopt #822 or invent a second audit store |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Role |
|---|---|
| [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542) / pricing `V9__disable_unedited_system_amendment_proration.sql` | Retire unused full-term `rule-007`. Cite as the Accepted ADR's delete path; do not re-enable `rule-007` as a credit formula |
| [DRG-603](https://linear.app/drgamtd-workspace/issue/DRG-603) | Story that landed the Accepted proration ADR |
| [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539) | Price-book read required only if `List` is exercised |
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) / [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) | Continuation pricing audit trail (Wave-2 residual). Cite-only |
| [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) / [#654](https://github.com/drgaciw/open-crm-cpq/pull/654) | One-time / retroactive charge lines. Cite-only |
| [DRG-589](https://linear.app/drgamtd-workspace/issue/DRG-589) | Mid-term customer-cancellation refunds stay **parked** |
| [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) / OQ9 | S5 LifecycleChange pricing; blocked on verified continuation capability |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) | Pricing-service continuation design — not wholesale-accepted for conversion price / store |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** for those CAM stories. This brief writes **no** migration and claims **no** CAM version |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief for DRG-550 is **blocked** until CPQ Decision scores the open **OQ-550-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-550-* row stays open.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/query after `npx gitnexus@latest analyze --index-only` on this checkout. |

## Problem (RA-16)

Amendments today keep contracted unit rates on the continuation path and price a stub via the Accepted day-stub formula. There is still no owner-scored residual Decision brief that (a) separates **already Adopted** amendment price / credit / fee / storage / formula contracts from **remaining delivery forks**, and (b) keeps residual switches (`None` / `NextCycle`, flat-adjustment encoding, List as-of, Override V14, lifting `NoCredit` / `NoFee`, snapshot columns, OQ9 / OQ10) as **Proposed OQ-550-*** without inventing a second proration formula.

Without that split, an implementation story could re-open 550-F1..F4, revive `rule-007` as a credit engine, claim a CAM Flyway slot in the V1_16–V1_21 band reserved by [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812), or treat mid-term spec labels `Daily` / `MonthDay` as a second arithmetic.

Linear DRG-550 (`Linear read` 2026-11-06): Medium; **In Review**; attachments include Cluster A draft [#650](https://github.com/drgaciw/open-crm-cpq/pull/650). Call comment 2026-10-16: flat adjustment and prorated delta must be selectable; credit and termination-fee stay on this item; mid-term cancellation refunds stay parked (DRG-589).

## Already Adopted (do not re-decide)

Restated from the delegated record and Accepted proration ADR (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Amendment price-mode set is `RetainContracted`, `List`, `Override`; **default when omitted is `RetainContracted`** | Delegated § Catalog and policy; 550-F1 Adopt baseline |
| A2 | Credit policy default is **`NoCredit`** | Same; 550-F2 |
| A3 | Termination-fee default is **`NoFee`** | Same; 550-F3 |
| A4 | Policies live as **tenant default + per-line override**, **snapshotted when priced**; apply reads the snapshot | Same; 550-F4 |
| A5 | Day arithmetic is the **Accepted single formula** in `QuoteContinuationPolicy.plan` (partial billing period by inclusive days at scale 2 HALF_UP, then full months; no day proration on a whole-month boundary). Requested quantity is priced forward; the formula does **not** emit unused-days credit | `adr-amendment-proration-formula.md` D-01, D-02, D-04 |
| A6 | `None` / `NextCycle`, if later added, are **switches around** that method — not a full-term-ratio option and not a reimplementation of `rule-007` | ADR D-04 |
| A7 | Credit for unused days of the old quantity, and a termination fee, are **DRG-550 policy** (defaults A2/A3). They are **not** implemented by turning `rule-007` back on | ADR D-04 |
| A8 | Missing policy / commercial input is a **refused request**, never an invented rate, multiple, or fee percent | Delegated § Catalog and policy |
| A9 | Until a named legal permission is delivered, **Override refuses**; sales cannot override | Same |
| A10 | Mid-term customer-cancellation refunds stay **parked** (DRG-589). New negative credit-line behavior is excluded on the DRG-572 path; credits retain this versioned policy | Delegated; #654 cite |
| A11 | Holds: ramp flag off; no Jev enablement; no product code / schema / flag from docs PRs alone; CAM **V1_16–V1_21** claimed by other drafts — not available here | AGENT.md; #805–#812 |

## What the tree does today (price / proration / credit / fee)

`verified by reading code` at the base SHA.

1. `QuoteContinuationPolicy.plan` retains the contracted unit rate and builds the Accepted stub + full-month plan for amendment / expansion. That is the live `RetainContracted` + prorated-delta behaviour. There is no tenant `AmendmentPricePolicy`, `creditPolicy`, or `terminationFee` column or enum on CAM documents.
2. Money columns `credit_total`, `tcv_delta`, `arr_delta`, `one_time_total` exist on `amendment_documents` (`V1_7__contract_documents.sql`). A search for `price_mode` / `credit_policy` / `termination_fee` under CAM migrations finds **none**.
3. LifecycleChange paths can stamp `prorationMethod` with the string `DAILY` (`LifecycleChangeService`). That label is **not** a second formula and must not be read as authorizing `rule-007` or a full-term ratio (see OQ-550-5).
4. Pricing `V9__disable_unedited_system_amendment_proration.sql` disables unedited system `rule-007`. Classpath `amendment-proration.drl` is removed from the phase-0 corpus (`PricingDrlCorpusCompileTest`). DRG-542 remains Linear **In Review** (`Linear read`); retirement of the unused rule is cite-only context for this brief, not authorization to edit Drools here.
5. OQ9 still records that execute/simulate cannot return AmendmentPolicy modes + PolicyDecision identities (`oq9-pricing-continuation-capability.md`). S5 stays blocked on that verification.
6. CAM tip on this `main` ends at `V1_15__document_apply_replay_evidence.sql`. Draft PRs [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. This brief assigns **no** CAM Flyway number.

### GitNexus (this checkout)

Repository: `open-crm-cpq` (`/agent/repos/open-crm-cpq`). Worktree: same. Index rebuilt this pass via `npx gitnexus@latest analyze --index-only` (process walk reported **truncated** entry-point coverage — not a clean graph).

| Symbol | Upstream risk | Notes |
|---|---|---|
| `QuoteContinuationPolicy` | **UNKNOWN** / truncated processes | Impact walk after index; empty or incomplete caller set is **not** evidence the class is unused. Text search shows callers in `QuoteContinuationService` / `QuoteService` |
| Docs-only path for this PR | N/A product blast | No Java/TS/SQL edited. `detect_changes` after the doc commit must still be reported; `partial` / `truncated` / `UNKNOWN` is **not** a clean check |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time).

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-550-1 | Flat adjustment vs prorated delta (call 2026-10-16) without a fourth mode name | Encode **prorated delta** as A1 + Accepted stub; encode **flat adjustment** as `Override` (or an explicit money/percent delta on `RetainContracted`) — **not** a fourth mode enum | Fourth mode `FlatAdjustment`; drop flat adjustment until later | Yes — selectable modes on S5 / DRG-583 |
| OQ-550-2 | Stub switches `None` / `NextCycle` (ADR D-04) | Defer; keep today's stub-on-amendment behaviour until owner scores | Add `None` (skip stub); add `NextCycle` (defer charge); add both | Soft until priced mid-month UX needs them |
| OQ-550-3 | Mid-term labels `Daily` / `MonthDay` vs Accepted day-stub | Treat labels as **vocabulary only**; arithmetic stays D-01. Do **not** add a second formula | Map `Daily` to full-term ratio (rejected by ADR); invent `MonthDay` arithmetic | Yes — any pricing AC that names a method |
| OQ-550-4 | When `List` may run | Refuse `List` until DRG-539 price-book read is delivered and verified; missing list → no price (no silent `RetainContracted` fallback) | Allow `List` with quote-local override only; silent fallback to contracted | Yes — List lines |
| OQ-550-5 | LifecycleChange `prorationMethod=DAILY` string | Keep string as a **non-authoritative label** pointing at Accepted stub; rename/align in a later docs+code story after OQ-550-3 closes | Treat `DAILY` as rule-007; remove the field now | Soft for docs; hard if readers treat it as formula choice |
| OQ-550-6 | Override / V14 gate before money lands | Keep A9: Override refuses until named legal permission + V14 fields (actor, reason, overridden value, approval) | Allow sales Override; skip V14 | Yes — Override ACs |
| OQ-550-7 | Lifting `NoCredit` later | Keep A2 until a separate owner decision; if lifted, credit uses **Accepted stub day-count** on the old quantity and **never** re-enables `rule-007` | Adopt `ProratedUnused` / `FullRemaining` / seller-must-pick now | Soft while A2 holds; hard when credit lines ship |
| OQ-550-8 | Lifting `NoFee` later | Keep A3 until a separate owner decision; if lifted, choose flat amount **or** percent of remaining TCV with configured values snapshotted; cut-off window stays out of the recommended set | Flat now; percent now; cut-off window | Soft while A3 holds; hard when fee lines ship |
| OQ-550-9 | Snapshot / policy column home (delivery of A4) | Additive columns on priced lines + document totals; **Flyway number = next free after V1_21** (or pricing-owned store if DRG-609/564 sequence says so). **Never** V1_16–V1_21 | Claim V1_16–V1_21; put body only in `commercial_decision_audit`; invent parallel quote table | Yes — persistence AC |
| OQ-550-10 | OQ9 / S5 verification gate | Keep **BLOCKS S5 until verified**; no AmendmentPolicy product path until execute/simulate (or successor continuation evaluate) returns mode + decision identities | Start S5 on quote-local RetainContracted only without identities | Yes — S5 / DRG-583 |
| OQ-550-11 | Mid-term OQ10 (minimum-commitment downsell + credit/fee) | Leave open; while open, refuse downsell that needs a credit/fee beyond A2/A3, or require explicit owner config | Allow downsell with silent `NoCredit`/`NoFee`; invent commitment fee percent | Soft for seats-only Update; hard for commitment downsell |
| OQ-550-12 | Coupling to DRG-572 / #654 | One-time / retroactive lines stay later scope; do **not** use ONE_TIME as the amendment credit vehicle; no new negative credit-line behaviour from 572 | Emit credit as ONE_TIME ADD now | Soft while A2; hard if credit ships via 572 |
| OQ-550-13 | Coupling to Wave-2 #822 (DRG-564) | Cite #822 only; amendment price snapshot pins must eventually point at the **one** pricing-owned PolicyDecision mechanism once OQ-564-* / DRG-612 close — do not invent a second trail here | Widen `commercial_decision_audit` for price policy; mint quote-local fake traces | Soft until audit store exists; hard for commercial confirm |
| OQ-550-MKT | Salesforce / HubSpot / Chargebee / Oracle BRM research on the issue | Cite as research forks only; no vendor parity commitment | Chargebee-shaped credit/fee enums as wire format; HubSpot month/day as second formula | Soft |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-550-1 — Flat adjustment vs prorated delta

| Option | What the owner would be choosing |
|---|---|
| **A. Map onto A1 without a fourth mode (Recommended, Proposed)** | Prorated delta = `RetainContracted` (or priced mode) + Accepted stub. Flat adjustment = `Override` amount/percent (V14) or an explicit delta field on `RetainContracted`. Matches Cluster A recommendation and call 2026-10-16 without reopening A1. |
| **B. Fourth mode `FlatAdjustment`** | Reopens 550-F1 alternative C. Out of Adopted set unless owner supersedes. |
| **C. Drop flat adjustment** | Contradicts call comment tracked on DRG-550. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-2 — Stub switches `None` / `NextCycle`

| Option | What the owner would be choosing |
|---|---|
| **A. Defer; keep stub behaviour (Recommended, Proposed)** | Today's amendment/expansion path builds the stub when the effective date is mid-period. Matches current continuation. |
| **B. Add `None`** | Skip stub; charge starts next whole period. Switch around D-01 — **not** a new formula. |
| **C. Add `NextCycle`** | Defer charge to the next billing cycle boundary. Same constraint as B. |
| **D. Add both** | Needs UI/API selection and snapshot fields (OQ-550-9). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-3 — Spec method labels vs Accepted arithmetic

| Option | What the owner would be choosing |
|---|---|
| **A. Vocabulary only; arithmetic = D-01 (Recommended, Proposed)** | Mid-term `Daily` / `MonthDay` / `None` / `NextCycle` remain words until mapped. Money uses Accepted day-stub. Full-term ratio stays rejected. |
| **B. Treat `Daily` as `remaining/full` ratio** | Conflicts with Accepted ADR. **NO-GO.** |
| **C. Invent separate `MonthDay` math** | Second formula. **NO-GO** unless owner supersedes the ADR with words and a time. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-4 — `List` readiness

| Option | What the owner would be choosing |
|---|---|
| **A. Refuse until DRG-539 verified (Recommended, Proposed)** | Missing list → no price. No silent fallback to contracted (would hide a `List` selection). |
| **B. Quote-local list override only** | Narrow escape; still needs audit (OQ-550-13). |
| **C. Silent fallback to `RetainContracted`** | Undermines A1 mode meaning. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-5 — `prorationMethod=DAILY` label

| Option | What the owner would be choosing |
|---|---|
| **A. Non-authoritative label for Accepted stub (Recommended, Proposed)** | Docs/readers must not treat the string as formula choice. Align naming in a later story after OQ-550-3. |
| **B. Bind `DAILY` to `rule-007`** | Conflicts with A5–A7 / V9 retirement path. **NO-GO.** |
| **C. Delete the field in this story** | Product edit — out of this docs brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-6 — Override / V14

| Option | What the owner would be choosing |
|---|---|
| **A. Keep refuse-until-legal + V14 (Recommended, Proposed)** | Restates A9. Flat adjustment via Override waits on the same gate. |
| **B. Sales may Override** | Supersedes delegated refuse path. |
| **C. Skip V14 fields** | Loses actor/reason/approval evidence required by mid-term V14. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-7 — Credit lift (beyond Adopted `NoCredit`)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `NoCredit` until separate decision (Recommended, Proposed)** | A2 stands. Shortened co-term under 554-F4 still writes no credit under the Adopted default. |
| **B. `ProratedUnused` now** | Policy name only until money rules land; day-count **must** come from Accepted stub period math on the **old** quantity — not `rule-007`. |
| **C. `FullRemaining` now** | Same constraint as B. |
| **D. Seller must pick every line; no tenant default** | Historical 550-F2 alternative D. |

Choosing B/C/D does **not** start DRG-572 and does **not** authorize negative ONE_TIME credit lines (#654 cite).

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-8 — Fee lift (beyond Adopted `NoFee`)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `NoFee` until separate decision (Recommended, Proposed)** | A3 stands. DRG-589 stays parked. |
| **B. Flat amount (configured, snapshotted)** | Needs configured value; missing value refuses (A8). |
| **C. Percent of remaining contract value** | How remaining value is counted stays on Accepted proration / TCV — not a new formula. |
| **D. Cut-off window** | Historical alternative; not in Cluster A recommended set. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-9 — Snapshot storage / Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. Next free after reserved band; or pricing-owned store per 564/609 sequence (Recommended, Proposed)** | Delivers A4 without colliding with CAM **V1_16–V1_21** (#805–#812). Number settled when an authorized migration PR exists. |
| **B. Claim V1_16–V1_21** | Conflicts with reserved CAM drafts. **NO-GO.** |
| **C. Only widen `commercial_decision_audit`** | Wrong grain for per-line price/credit/fee snapshot (see #822 / DRG-564). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-10 — OQ9 / S5

| Option | What the owner would be choosing |
|---|---|
| **A. Block S5 until capability verified (Recommended, Proposed)** | Restates register OQ9. Quote-local RetainContracted stub may continue as today's path; it is **not** S5 complete. |
| **B. Ship S5 without decision identities** | Fails AC-S5-2 / V8. |
| **C. Claim OQ9 closed from this brief** | False; this file does not re-verify execute. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-11 — Mid-term OQ10

| Option | What the owner would be choosing |
|---|---|
| **A. Leave open; refuse commitment downsell that needs credit/fee beyond A2/A3 (Recommended, Proposed)** | Spec OQ10 stays open. |
| **B. Allow with silent NoCredit/NoFee** | May be acceptable for seats-only; must be explicit. |
| **C. Invent commitment fee percent** | Violates A8. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-12 — DRG-572 / #654

| Option | What the owner would be choosing |
|---|---|
| **A. Keep credit on DRG-550 policy; 572 stays later-scope (Recommended, Proposed)** | Matches #654 / delegated exclusion of new negative credit-line behaviour. |
| **B. Emit amendment credits as ONE_TIME ADD now** | Couples later-scope 572 into first-slice commercial path. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-13 — Wave-2 #822 (DRG-564) audit coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; one pricing-owned trail later (Recommended, Proposed)** | Amendment price/credit/fee snapshots pin to the future PolicyDecision mechanism once OQ-564-* / DRG-612 close. This brief adopts **none** of #822's Proposed forks. |
| **B. Second amendment-only audit table** | Splits the "one mechanism" mandate. |
| **C. Fake quote-local traces** | Undermines CAM priced-write validation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-550-MKT — Vendor research

| Option | What the owner would be choosing |
|---|---|
| **A. Research forks only (Recommended, Proposed)** | Salesforce contracted-price delta, HubSpot hybrid proration, Chargebee credit/fee/cut-off, Oracle BRM transition — illustrations for OQ-550-7/8/2, not parity commitments. |
| **B. Vendor wire-format parity** | Out of slice without a separate product decision. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed **OQ-550-***.
2. CPQ Decision must score the OQ-550-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, Drools edits, or flag flips for amendment price / credit / termination-fee policy. **Product / schema / flag = NO-GO.**
4. Even after forks close: A1–A11 hold; Accepted proration ADR stays single-formula; ramp flag stays off; no Jev / dual-read / S4 / asset-write hold bypass; CAM V1_16–V1_21 stay reserved for #805–#812; #654 and #822 remain cite-only unless separately accepted.
5. Linear status for DRG-550 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

## Out of scope

- Editing `QuoteContinuationPolicy`, Drools, pricing `V9`, LifecycleChange, or CAM schema.
- Accepting DRG-609, DRG-539, #822 OQ-564-*, or unparking DRG-589.
- Assigning an ADR number to the proration record or copying it into `docs/adr/`.
- Enabling `opencrm.pricing.ramp.enabled` or any `opencrm.jev.*.enabled` flag.
- Claiming CAM Flyway V1_16–V1_21.
- Reopening Adopted 550-F1..F4 or inventing a second proration formula.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-550, DRG-542, relations | Linear, this pass |
| Delegated 550-F1..F4 + Catalog and policy sentence | `delegated-renewals-amendments-decisions-2026-10-17.md` |
| Cluster A §5 | `cluster-a-amendment-modes-and-policy-decision-frame.md` |
| Accepted proration ADR D-01..D-04 | `adr-amendment-proration-formula.md` |
| Mid-term modes + OQ10 | `mid-term-amendment-policy-spec.md` |
| OQ9 capability | `oq9-pricing-continuation-capability.md` |
| DRG-572 frame / #654 | `story-drg-572-one-time-retroactive-charge-lines-decision-frame.md`; GitHub PR #654 |
| Wave-2 pricing audit #822 | GitHub PR #822 (DRG-564 Decision brief) |
| CAM money columns; no policy columns | `V1_7__contract_documents.sql`; migration directory listing |
| Stub implementation / `DAILY` stamp | `QuoteContinuationPolicy.java`; `LifecycleChangeService.java` |
| `rule-007` disable | `V9__disable_unedited_system_amendment_proration.sql` |
| CAM V1_16–V1_21 reservation | Draft PRs #805–#812 |
| GitNexus | `analyze --index-only`; impact on `QuoteContinuationPolicy` (UNKNOWN / truncated — **not clean**) |

## Unverified in this pass

- Any runtime pricing execute, stub money, or Flyway apply.
- Whether draft CAM PRs #805–#812 keep their claimed version numbers through merge.
- Byte shape of a future price/credit/fee snapshot JSON.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
