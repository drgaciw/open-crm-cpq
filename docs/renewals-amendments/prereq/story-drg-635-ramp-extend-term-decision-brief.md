# DRG-635 — Ramp: extend saves the new term on the quote: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **delivery forks that remain after** the Accepted escalator procedure (DRG-533 / [#644](https://github.com/drgaciw/open-crm-cpq/pull/644)), the merged authoring/persistence slice ([#750](https://github.com/drgaciw/open-crm-cpq/pull/750)), and the merged dated-term correction ([#772](https://github.com/drgaciw/open-crm-cpq/pull/772)). This file does **not** reopen Adopted ADR-023 cells, does **not** reopen Accepted DRG-533 D-01..D-06 recommendations already recorded as the escalator procedure, does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** adopt open **OQ-633-*** rows from the DRG-633 residual Decision brief draft [#871](https://github.com/drgaciw/open-crm-cpq/pull/871), does **not** adopt open **OQ-634-*** rows from the DRG-634 residual Decision brief draft [#872](https://github.com/drgaciw/open-crm-cpq/pull/872), does **not** accept [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize further product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed` under id **OQ-635-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp` / extended-term persistence. **Story:** [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635/ramp-extend-saves-the-new-term-on-the-quote). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #803 / #871 / #872 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted ramp baseline:** schedule model, whole-month periods, D-06 ramp flag default **off**, D-03 ACV version switch separate from the ramp flag |
| [DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md](DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md) (Accepted via DRG-533 / [#644](https://github.com/drgaciw/open-crm-cpq/pull/644), 2026-10-17) | **Adopted escalator procedure:** expand/extend arithmetic and renewal lock-shape rules. Term **persistence** on extend is the residual delivery this brief scores |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults / OQ1 / OQ8). Restates stored-step AC-1..4 path and **ramp hold**. This brief **does not score or close** those rows |
| Wave-2 DRG-633 residual Decision brief [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) | Cite-only sibling residual forks (**OQ-633-***). Same #750 head; compounding / percent arithmetic. This brief **does not score or close** those rows |
| Wave-2 DRG-634 residual Decision brief [#872](https://github.com/drgaciw/open-crm-cpq/pull/872) | Cite-only sibling residual forks (**OQ-634-***). Same #750 head; short-final authoring. This brief **does not score or close** those rows |
| [`specs/ramp-amendments.md`](specs/ramp-amendments.md) | Proposal only (DRG-639). Open questions stay Proposed. Not accepted by this file |
| [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) (merged) | Authoring + atomic extend persistence for DRG-633/634/635: paired line/version save, optimistic concurrency, editor request shape. **CPQ Decision PRE-PASS 80** for this story (below 85). Not owner Done |
| [#772](https://github.com/drgaciw/open-crm-cpq/pull/772) (merged) | Dated-term correction: inclusive end date derived from start + new duration; response/client reconciliation. Merge recorded; formal PASS / Done still pending |
| [docs/plans/ramp-extension-dated-term-review-20261101.md](../../plans/ramp-extension-dated-term-review-20261101.md) | Delivery note for DATE-1..DATE-4 / CI-1. Cite only; not a Decision acceptance |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Backlog / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Regeneration from percent stays out of the amendment stored-step slice |
| [DRG-633](https://linear.app/drgamtd-workspace/issue/DRG-633) / [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) | In Review / draft | Non-compounding percent residual. Same merged head; separate Decision scoring. Do **not** close via this brief; do **not** adopt open **OQ-633-*** |
| [DRG-634](https://linear.app/drgamtd-workspace/issue/DRG-634) / [#872](https://github.com/drgaciw/open-crm-cpq/pull/872) | In Review / draft | Short final period residual. Same merged head; separate Decision scoring. Do **not** close via this brief; do **not** adopt open **OQ-634-*** |
| [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532) | Done | Equal-block generator / authoring. This brief does not reopen generator ownership; term save is the follow-up this story owns for Decision scoring |
| [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) | Done | Escalator ADR Accepted. Residual forks here are delivery / Decision-PASS only for extend-term persistence |
| [DRG-632](https://linear.app/drgamtd-workspace/issue/DRG-632) | — | Quote `tenantId` column (V1_13 interim). Sequencing predecessor for schema overlap; not reopened |
| [DRG-716](https://linear.app/drgamtd-workspace/issue/DRG-716) | Open | P04 commercial-audit follow-up for quote term extension. Separate story; this brief does **not** close it or authorize its product work |
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) | In Progress | Schedule / step application on accept. Residual rollout blocker; not a reason to flip the ramp flag from this brief |
| [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336) | In Review | New-quote ramp pricing (flag off). Related; not reopened |
| [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338) | Done | Per-period governance + fingerprint. Not redefined here |
| [DRG-485](https://linear.app/drgamtd-workspace/issue/DRG-485) / [#762](https://github.com/drgaciw/open-crm-cpq/pull/762) | Related | Won requires accepted quote; #772 fixture repair only. Not a lifecycle reopen |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief that reopens product work for DRG-635 (further Java/TS edits, migrations, or enablement) is **blocked** until CPQ Decision scores the open **OQ-635-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-635-* row stays open. Closing #803 OQ2–OQ7, #871 OQ-633-*, or #872 OQ-634-* is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` / `Adopted (DRG-533)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery / Decision-PASS fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-635 residual)

DRG-533 Accepted the escalator procedure. [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) merged atomic extend persistence: paired `lineItemId` + `expectedQuoteVersion` commits the extended schedule and quote `durationInMonths` together; unpaired requests retain preview; optimistic concurrency returns 409 on losers. Review of that merge found dated quotes kept the old inclusive end date; [#772](https://github.com/drgaciw/open-crm-cpq/pull/772) corrected calendar end-date derivation and authoritative response/client reconciliation, and merged to main. CPQ Decision scored the story **80 (PRE-PASS, below the 85 PASS threshold)** (Linear hygiene: corrected scoring tuple for #750; no original failing-first commit claimed for the initial persistence slice) and left the issue **In Review**. Designated scoring and owner merge OK remain pending; Linear status must stay **In Review**. Open follow-up [DRG-716](https://linear.app/drgamtd-workspace/issue/DRG-716) (P04 audit entry) remains separate.

Without a residual Decision brief that separates **already Adopted** ramp / escalator contracts from **still-Proposed delivery / PASS forks**, a follow-up could treat #750/#772 as Decision Done, flip `opencrm.pricing.ramp.enabled`, treat continuation-term extension as authorized, regenerate amendment periods from percent while [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQs stay open, claim further CAM Flyway `V1_16`–`V1_21`, close DRG-716 by implication, or adopt #803 / #871 / #872 recommendations by citation.

Linear DRG-635 (`Linear read` 2026-11-06): **In Review**; blocked by DRG-632; related to DRG-532 / DRG-534 / DRG-716 / DRG-485; attachment #750 (merged). Hygiene comment: PRE-PASS 80; stays In Review until a PASS and drg's OK. **This brief does not change Linear status.**

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), the Accepted DRG-533 escalator ADR (`read`), and the orientation baseline in [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) (`read`). This Decision brief does **not** reopen these rows. [#803] marks its **OQ2–OQ7** rows Proposed — those are **not** Adopted here. [#871] marks **OQ-633-*** Proposed — those are **not** Adopted here. [#872] marks **OQ-634-*** Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | Per-line explicit ramp schedule / whole-month periods / contract-asset hosts the rate schedule | ADR-023 D-01, D-04, D-08 |
| A2 | **`opencrm.pricing.ramp.enabled` defaults false**; flag-off never prices a schedule as flat; turning the flag on is outside Acceptance | ADR-023 D-06; #803 residual blockers; **Ramp hold** |
| A3 | ACV version switch is **not** the ramp flag; default stays `legacy` until separately set | ADR-023 D-03 |
| A4 | Default escalator percent **compounds on the prior stored unit** (`compound = true`) | DRG-533 D-01 / Consequences |
| A5 | **`compound = false`** applies each step's percent to the **period-1 unit** (non-compounding / base-price percent) | DRG-533 D-01 |
| A6 | Amount-mode steps add currency to the prior unit and do **not** invent a second compounding rule | DRG-533 D-01 |
| A7 | Expanded units half-up at **scale 4**; extended amounts half-up at scale 2 | DRG-533 D-03 |
| A8 | DRG-533 D-04 **renewal** equal-block generator refuses a term the step length does not divide (`RAMP_TERM_MISMATCH`) unless a later story owns short tails | DRG-533 D-04; short-tail delivery is DRG-634 / #872, not this brief |
| A9 | Amendments of stored absolute periods (DRG-534) do **not** regenerate from percent in the stored-step slice; elapsed locking stays with DRG-534 | DRG-533 Consequences; #803 AC-1..4 path |
| A10 | Docs / Decision briefs alone authorize **no** product code, flag flip, or deploy | AGENT.md / ADR-023 / #803 |

## What the tree does today (extend-term persistence)

`verified by reading code` at the base SHA.

1. **`RampExtendRequest`** — `steps`, `durationInMonths` (whole term after extension), `escalator`, optional paired `lineItemId` + `expectedQuoteVersion`. Without the pair, extend **previews only**; with both, schedule + term commit atomically (`read` `RampExtendRequest.java`).
2. **`RampScheduleQueryService.extend`** — expands via `RampEscalatorExpander.extend`; when the pair is present, calls `QuoteLineItemService.persistExtendedRamp` and returns flushed `durationInMonths`, `quoteVersion`, `quoteStatus`, and `endDate` (`read` `RampScheduleQueryService.java`).
3. **`persistExtendedRamp`** — optimistic root version check (stale → business 409 path); refuses shrink / missing expected version; advances quote version; when duration grows and `startDate` is set, writes `endDate = startDate.plusMonths(duration).minusDays(1)`; sets `durationInMonths`; attaches schedule; invalidates pricing/approval fingerprint path; marks ramp schedule stale; `saveAndFlush` (`read` `QuoteLineItemService.java`).
4. **Same-term catch-up / optional dates** — growing dated terms recompute end; same-term continuation catch-up preserves inherited dates; no start date leaves optional dates unchanged (DATE-2; `read` plan + save tests).
5. **Client reconciliation** — extend response `endDate` is authoritative before a later header save can send a stale end; omitted field keeps client value; explicit null clears (`read` plan DATE-4; frontend quote service/model).
6. **Continuation terms** — typed continuation validation still rebuilds its own plan and refuses changed continuation terms during calculate/accept; #772 explicitly does **not** add continuation-term extension support (`read` plan).
7. **Flag gate** — `properties.isEnabled()` false → `RAMP_NOT_SUPPORTED`; committed config stays default off (**Ramp hold**).
8. **No new migration for duration** — reuses existing quote duration / date columns; landed quote `V1_16` compounding metadata is historical for DRG-633, not a license to open CAM `V1_16`–`V1_21` from this story.

## Residual open questions (Proposed only)

All rows below are `Status: Proposed`. None are Adopted by this file. Recommended options are recommendations for CPQ Decision scoring, not owner decisions.

| ID | Fork | Recommended (Proposed) | Alternatives (Proposed) | Blocks product? |
|---|---|---|---|---|
| OQ-635-1 | Formal PASS / Done after PRE-PASS 80 | Stay **In Review** until PASS ≥85 + owner OK | Done on merge alone; lower threshold / waive allowlist | Yes — Done / GA claims |
| OQ-635-2 | Preview vs atomic save contract | Keep unpaired = preview; paired line+version = atomic term+schedule | Always persist; drop preview path; persist schedule without term | Soft for UX; hard if term/schedule diverge |
| OQ-635-3 | Inclusive end-date derivation | Keep `start.plusMonths(duration).minusDays(1)` for growing dated terms | Retain old end on extend; exclusive-end calendar; seller-supplied end only | Yes if dates / accept validators disagree |
| OQ-635-4 | Same-term catch-up / undated terms | Preserve inherited / optional dates as landed | Always rewrite end; require start date to extend | Soft; hard if continuation anchors break |
| OQ-635-5 | Optimistic concurrency | Keep expected version; 409 loser; no silent overwrite | Last-write-wins; soft merge of schedules | Yes — data integrity |
| OQ-635-6 | Client authoritative endDate | Apply response endDate before header re-save; omitted keeps; null clears | Ignore response endDate; always re-derive client-side | Soft for UX; hard if stale end returns |
| OQ-635-7 | Coupling to #803 / #871 / #872 | Cite only; wait; no percent regeneration / short-tail reopen here | Treat those Recommended rows as Adopted; regenerate from percent in DRG-635 | Yes — would adopt open OQs |
| OQ-635-8 | Ramp flag / GA (**Ramp hold**) | Keep flag false; no GA from this story | Flip `opencrm.pricing.ramp.enabled=true`; couple ACV switch | Yes — flag / hold |
| OQ-635-9 | Further schema / Flyway | No new migration; no CAM V1_16–V1_21 | Add term-extension metadata columns; reserve/ship CAM band | Yes — schema |
| OQ-635-10 | Implementation brief / further product | NO-GO until forks + PASS | Authorize product PRs on docs merge; reopen #750/#772 churn without PASS | Yes — product NO-GO |
| OQ-635-11 | Scoring tuple freshness | Re-score exact head + current allowlist (incl. #772) | Promote PRE-PASS 80 to PASS; ignore allowlist drift | Yes — Decision reproducibility |
| OQ-635-12 | Sibling 633 / 634 | Separate Decision per story | One PASS closes 633+634+635; block 635 until 633/634 PASS | Soft sequencing; owner words |
| OQ-635-13 | DRG-339 / apply-path residual | Disclose 339 In Progress; no GA / flag flip here | Claim authoring Done = apply Done; enable flag while 339 open | Yes — rollout / GA |
| OQ-635-14 | DRG-716 P04 audit | Keep separate open follow-up; do not close via this brief | Bundle audit into 635 Done; waive P04 | Soft for Done hygiene; hard if audit required for PASS |
| OQ-635-15 | Continuation-term extension | Remain out of scope (standalone quotes only) | Authorize continuation-term extend in this story | Yes — policy / product scope |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-635-1 — Formal PASS / Done after PRE-PASS 80

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Review until PASS ≥85 + owner OK (Recommended, Proposed)** | Matches Linear hygiene. #750/#772 remain merged code under Decision hold. |
| **B. Done on merge alone** | Contradicts PRE-PASS and the returned In Review state. **NO-GO.** |
| **C. Lower threshold / waive allowlist** | Owner words + time required; not implied by this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-2 — Preview vs atomic save

| Option | What the owner would be choosing |
|---|---|
| **A. Unpaired preview; paired atomic term+schedule (Recommended, Proposed)** | Matches landed request contract and #750 browser/unit evidence. |
| **B. Always persist on extend** | Removes preview; breaks old clients that omit the pair. |
| **C. Persist schedule without updating quote duration** | Recreates the inconsistency this story exists to prevent. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-3 — Inclusive end-date derivation

| Option | What the owner would be choosing |
|---|---|
| **A. `start.plusMonths(newDuration).minusDays(1)` for growing dated terms (Recommended, Proposed)** | Matches #772 DATE-1 and accept-path calendar validators. |
| **B. Keep prior inclusive end when only duration grows** | Reopens the #750 dated-term defect. **NO-GO.** |
| **C. Exclusive-end or seller-only end without duration sync** | Different contract; needs new fixtures and Decision. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-4 — Same-term catch-up / undated terms

| Option | What the owner would be choosing |
|---|---|
| **A. Preserve inherited continuation / optional dates as landed (Recommended, Proposed)** | Matches DATE-2; avoids rewriting billing anchors. |
| **B. Always rewrite endDate whenever duration is touched** | Can break same-term catch-up anchors. |
| **C. Require startDate before any extend save** | Narrows undated quote authoring; needs owner cut. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-5 — Optimistic concurrency

| Option | What the owner would be choosing |
|---|---|
| **A. Expected version required on save; 409 loser; no silent overwrite (Recommended, Proposed)** | Matches #750 PostgreSQL race and browser 409 evidence. |
| **B. Last-write-wins** | Loses schedule/term atomicity under concurrency. **NO-GO.** |
| **C. Soft-merge schedules across versions** | Undefined commercial truth; needs a new Decision. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-6 — Client authoritative endDate

| Option | What the owner would be choosing |
|---|---|
| **A. Apply response endDate; omitted keeps; null clears (Recommended, Proposed)** | Matches #772 DATE-4 editor/header-save reconciliation. |
| **B. Ignore response endDate and re-derive locally** | Can reintroduce stale header saves. |
| **C. Block all header edits after extend until reload** | Heavier UX; not required by landed evidence. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-7 — Coupling to #803 / #871 / #872 without adopting open OQs

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803, #871, and #872; wait; no percent regeneration / short-tail reopen here (Recommended, Proposed)** | Preserves A9 stored-step path. Closing this brief does **not** close OQ2–OQ7, OQ-633-*, or OQ-634-*. |
| **B. Treat #803 / #871 / #872 Recommended rows as Adopted** | Out of scope; would adopt open OQs from other drafts. **NO-GO.** |
| **C. Regenerate future periods from percent inside DRG-635** | Contradicts #803 AC-8 / DRG-533 Consequences. **NO-GO** from this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-8 — Ramp flag / GA (Ramp hold)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep flag false; no GA from this story (Recommended, Proposed)** | ADR-023 D-06 / A2. **Product / flag NO-GO.** |
| **B. Flip `opencrm.pricing.ramp.enabled=true` in committed config** | Requires a separate owner un-hold; not this brief. |
| **C. Couple ACV `term-years` to the ramp flag** | Rejected by ADR-023 D-03. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-9 — Further schema / Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration from this brief; no CAM V1_16–V1_21 (Recommended, Proposed)** | Duration/dates reuse existing columns. Landed quote `V1_16` is historical compounding metadata, not a license to open more. |
| **B. Add term-extension metadata / alter date columns now** | Product/schema work; blocked. |
| **C. Reserve or ship CAM V1_16–V1_21 here** | Band reserved by other drafts; **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-10 — Implementation brief / further product edits

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on further product PRs until forks + PASS (Recommended, Proposed)** | Docs PR is not authorization. DRG-716 stays a separate ticket. |
| **B. Authorize persistence/editor follow-ups when this file merges** | Violates hard product NO-GO. |
| **C. Reopen #750/#772 for allowlist-only churn without PASS** | Still a product PR; needs OQ-635-1 / OQ-635-11 first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-11 — Scoring tuple freshness

| Option | What the owner would be choosing |
|---|---|
| **A. Re-score exact head + current allowlist including #772 (Recommended, Proposed)** | Addresses Linear hygiene on stale #750 tuple and dated-term correction evidence. |
| **B. Promote PRE-PASS 80 to PASS** | Below threshold; needs owner words. |
| **C. Ignore allowlist / evidence gaps** | Undermines Decision reproducibility. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-12 — Sibling 633 / 634

| Option | What the owner would be choosing |
|---|---|
| **A. Separate Decision for each story (Recommended, Proposed)** | Same #750 head may be evidence; Done is per-issue. |
| **B. One PASS closes 633+634+635** | Couples unrelated residual forks. |
| **C. Block 635 PASS until 633/634 PASS** | Optional sequencing; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-13 — DRG-339 / apply-path residual

| Option | What the owner would be choosing |
|---|---|
| **A. Disclose 339 In Progress; no GA / flag flip here (Recommended, Proposed)** | Matches #803 residual blockers orientation. |
| **B. Claim authoring Done = schedule apply Done** | False; apply path is DRG-339. |
| **C. Enable ramp flag while 339 open** | Violates A2 / OQ-635-8. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-14 — DRG-716 P04 audit follow-up

| Option | What the owner would be choosing |
|---|---|
| **A. Keep DRG-716 open and separate; do not close via this brief (Recommended, Proposed)** | Matches Linear relation and #772 delivery note. |
| **B. Bundle P04 audit into DRG-635 Done** | Couples unrelated delivery; needs owner words. |
| **C. Waive P04 for this story** | Owner words + time required; not implied here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-635-15 — Continuation-term extension scope

| Option | What the owner would be choosing |
|---|---|
| **A. Standalone quote authoring only; continuation-term extend stays out (Recommended, Proposed)** | Matches #772 explicit non-scope. |
| **B. Authorize continuation-term extension in this story** | Product/policy work; blocked. |
| **C. Silently allow continuation calculate/accept to accept changed terms** | Contradicts landed refusal; **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-635-1` .. `OQ-635-15`).
2. CPQ Decision must score the OQ-635-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes further Java/TS product work, Flyway, gateway routes, or flag flips for DRG-635.
4. Even after forks close: A1–A10 hold; **Ramp hold** (`opencrm.pricing.ramp.enabled=false`) stays; no Jev enablement; #803 OQ2–OQ7, #871 OQ-633-*, and #872 OQ-634-* stay Proposed unless those Decision briefs close them; DRG-716 remains a separate open follow-up; DRG-339 In Progress remains a residual rollout disclose.
5. Linear status for DRG-635 stays **In Review** until the owner and Decision say otherwise. This brief does **not** mark Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or further product code for DRG-635.
- Does not mark any OQ-635-* fork Adopted.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults / OQ8.
- Does not adopt or close [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) OQ-633-*.
- Does not adopt or close [#872](https://github.com/drgaciw/open-crm-cpq/pull/872) OQ-634-*.
- Does not flip `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version`.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any new quote/pricing migration.
- Does not close DRG-633, DRG-634, DRG-716, DRG-339, or DRG-336.
- Does not reopen ADR-023 or Accepted DRG-533 compounding / scale / renewal D-04 decisions.
- Does not authorize continuation-term extension.
- Does not change Linear status (remains **In Review**).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-635-* (especially OQ-635-1, OQ-635-8, OQ-635-10, OQ-635-11). Until then, further product work is **NO-GO**.
2. **PRE-PASS 80:** below PASS threshold; fresh scoring tuple required (include #772 allowlist / DATE evidence).
3. **Ramp hold:** flag stays off; no GA from this wave.
4. **#803 OQ2–OQ7:** still Proposed; no percent regeneration on amendments from this story.
5. **#871 OQ-633-* / #872 OQ-634-*:** still Proposed; sibling Decision residuals.
6. **DRG-716 open:** P04 term-extension audit not delivered by this brief.
7. **DRG-339 In Progress:** apply/accept path incomplete for rollout claims.
8. **DRG-633 / DRG-634 In Review:** sibling Decision still open on the same merged head.
9. **GitNexus index:** MCP `list_repos` empty in this environment; any future code edit needs a live `analyze` + `impact` / `detect_changes` on the editing checkout (`UNKNOWN` until then). CLI fallback may still be used for docs-only diffs.

## Sources read this pass

- Linear: DRG-635 (In Review; comments; #750 attachment; relations DRG-632/532/534/716/485), DRG-633/#871 orientation, DRG-634/#872 orientation, DRG-534/#803 orientation, DRG-339, DRG-716.
- GitHub: #750 (merged; PRE-PASS context), #772 (merged dated-term correction), #803 / #871 / #872 (draft Decision briefs), #644 (DRG-533 Acceptance path).
- ADR-023; DRG-533 Accepted escalator ADR draft on main.
- Landed code: `RampExtendRequest`, `RampScheduleQueryService.extend`, `QuoteLineItemService.persistExtendedRamp`, `RampScheduleDto` endDate/version fields, `RampExtensionSaveTest` / persistence integration orientation.
- Plan evidence cite: `docs/plans/ramp-extension-dated-term-review-20261101.md`, `docs/plans/cpq-parallel-followups-20261101.md` (delivery map only; not a Decision acceptance).
- AGENT.md / `docs/ARCHITECTURE.md` §17 holds (ramp flag treated as ADR-023 D-06 hold; Jev / dual-read holds unchanged).

## Unverified in this pass

- Runtime re-execution of #750/#772 browser journeys or CI on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus MCP analyze / graph coverage beyond docs-only claim (`list_repos` empty → treat code-graph risk as `UNKNOWN` if product files were touched; they were not).
- Whether a future Decision re-score will use #750 hygiene head `50085ff8…`, #772 tested head `5ff98500…`, or a newer main tip that contains both.
