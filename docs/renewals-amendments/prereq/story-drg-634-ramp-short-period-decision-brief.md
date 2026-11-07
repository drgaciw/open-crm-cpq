# DRG-634 — Ramp: short final period when the term doesn't divide evenly: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **delivery forks that remain after** the Accepted escalator procedure (DRG-533 / [#644](https://github.com/drgaciw/open-crm-cpq/pull/644)) and the merged authoring slice ([#750](https://github.com/drgaciw/open-crm-cpq/pull/750)). This file does **not** reopen Adopted ADR-023 cells, does **not** reopen Accepted DRG-533 D-01..D-06 recommendations already recorded as the escalator procedure (including the historical equal-block / `RAMP_TERM_MISMATCH` wording in D-04), does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** adopt open **OQ-633-*** rows from the DRG-633 residual Decision brief draft [#871](https://github.com/drgaciw/open-crm-cpq/pull/871), does **not** accept [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize further product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed` under id **OQ-634-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp` / short-tail escalator authoring. **Story:** [DRG-634](https://linear.app/drgamtd-workspace/issue/DRG-634/ramp-short-final-period-when-the-term-doesnt-divide-evenly). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #803 / #871 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted ramp baseline:** schedule model, whole-month periods, D-06 ramp flag default **off**, D-03 ACV version switch separate from the ramp flag |
| [DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md](DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md) (Accepted via DRG-533 / [#644](https://github.com/drgaciw/open-crm-cpq/pull/644), 2026-10-17) | **Adopted escalator procedure:** equal-block renewal expansion historically refused a non-divisible term (`RAMP_TERM_MISMATCH`); short-tail **new-quote authoring** is the residual delivery this brief scores — it does **not** rewrite D-04 renewal lock-shape rejection by citation |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults / OQ1 / OQ8). Restates stored-step AC-1..4 path and **ramp hold**. This brief **does not score or close** those rows |
| Wave-2 DRG-633 residual Decision brief [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) | Cite-only sibling residual forks (**OQ-633-***). Same #750 head; compounding / percent arithmetic. This brief **does not score or close** those rows |
| [`specs/ramp-amendments.md`](specs/ramp-amendments.md) | Proposal only (DRG-639). Open questions stay Proposed. Not accepted by this file |
| [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) (merged) | Authoring delivery for DRG-633/634/635: short-final expand/extend, ceilings, editor copy, regression and browser evidence. **CPQ Decision PRE-PASS 82** (below 85). Not owner Done |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Backlog / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Regeneration from percent stays out of the amendment stored-step slice |
| [DRG-633](https://linear.app/drgamtd-workspace/issue/DRG-633) / [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) | In Review / draft | Non-compounding percent residual. Same merged head; separate Decision scoring. Do **not** close via this brief; do **not** adopt open **OQ-633-*** |
| [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532) | Done | Equal-block generator / authoring. This brief does not reopen generator ownership; short-tail is the follow-up this story owns for Decision scoring |
| [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) | Done | Escalator ADR Accepted. D-04 equal-block refusal remains the renewal procedure text; residual forks here are delivery / Decision-PASS only for short-tail authoring |
| [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635) / [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) | In Review | Persist extended term. Same merged head; separate Decision scoring. Do not close via this brief |
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) | In Progress | Schedule / step application on accept. Residual rollout blocker; not a reason to flip the ramp flag from this brief |
| [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336) | In Review | New-quote ramp pricing (flag off). Related; not reopened |
| [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338) | Done | Per-period governance + fingerprint. Not redefined here |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief that reopens product work for DRG-634 (further Java/TS edits, migrations, or enablement) is **blocked** until CPQ Decision scores the open **OQ-634-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-634-* row stays open. Closing #803 OQ2–OQ7 or #871 OQ-633-* is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` / `Adopted (DRG-533)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery / Decision-PASS fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-634 residual)

DRG-533 Accepted the escalator procedure, including D-04 equal-block expansion that refused a term `everyMonths` does not divide (`RAMP_TERM_MISMATCH`). [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) merged short-final authoring: `RampEscalatorExpander.expand` / `extend` emit a shorter last period that covers the remainder without overlap or gaps; ceilings and contiguous short-tail extension are covered by regression tests; browser UAT showed 26 = 12+12+2 and extension preserving an existing short tail. CPQ Decision scored the story **82 (PRE-PASS, below the 85 PASS threshold)** (Linear hygiene: corrected scoring tuple `50085ff8` / base `5ccfe79c`; no original failing-first commit for the initial short-tail arithmetic) and left the issue **In Review**. Designated scoring and owner merge OK remain pending; Linear status must stay **In Review**.

Without a residual Decision brief that separates **already Adopted** ramp / escalator contracts from **still-Proposed delivery / PASS forks**, a follow-up could treat #750 as Decision Done, flip `opencrm.pricing.ramp.enabled`, treat short tails as rewriting DRG-533 D-04 renewal lock refusal, regenerate amendment periods from percent while [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQs stay open, claim further CAM Flyway `V1_16`–`V1_21`, or adopt #803 / #871 recommendations by implication.

Linear DRG-634 (`Linear read` 2026-11-06): **In Review**; related to DRG-532; attachment #750 (merged). Hygiene comment: PRE-PASS 82; stays In Review until a PASS and drg's OK. **This brief does not change Linear status.**

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), the Accepted DRG-533 escalator ADR (`read`), and the orientation baseline in [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) (`read`). This Decision brief does **not** reopen these rows. [#803] itself marks its **OQ2–OQ7** rows Proposed — those are **not** Adopted here. [#871] marks **OQ-633-*** Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | Per-line explicit ramp schedule / whole-month periods / contract-asset hosts the rate schedule | ADR-023 D-01, D-04, D-08 |
| A2 | **`opencrm.pricing.ramp.enabled` defaults false**; flag-off never prices a schedule as flat; turning the flag on is outside Acceptance | ADR-023 D-06; #803 residual blockers; **Ramp hold** |
| A3 | ACV version switch is **not** the ramp flag; default stays `legacy` until separately set | ADR-023 D-03 |
| A4 | Default escalator percent **compounds on the prior stored unit** (`compound = true`) | DRG-533 D-01 / Consequences |
| A5 | **`compound = false`** applies each step's percent to the **period-1 unit** (non-compounding / base-price percent) | DRG-533 D-01 |
| A6 | Amount-mode steps add currency to the prior unit and do **not** invent a second compounding rule | DRG-533 D-01 |
| A7 | Expanded units half-up at **scale 4**; extended amounts half-up at scale 2 | DRG-533 D-03 |
| A8 | DRG-533 D-04 **renewal** equal-block generator refuses a term the step length does not divide (`RAMP_TERM_MISMATCH`); short-tail **new-quote authoring** is owned by DRG-634 Decision scoring and does **not** silently rewrite that renewal lock-shape text | DRG-533 D-04; this brief's OQ-634-* rows |
| A9 | Amendments of stored absolute periods (DRG-534) do **not** regenerate from percent in the stored-step slice; elapsed locking stays with DRG-534 | DRG-533 Consequences; #803 AC-1..4 path |
| A10 | Docs / Decision briefs alone authorize **no** product code, flag flip, or deploy | AGENT.md / ADR-023 / #803 |

## What the tree does today (short-final delivery)

`verified by reading code` at the base SHA.

1. **`RampEscalatorExpander.expand`** — period count is `ceil(durationInMonths / everyMonths)`; each period's `months` is `min(everyMonths, remaining)`; the last period may be shorter than `everyMonths`. Terms shorter than one interval become a single short period. `maxPeriods` ceiling raises `TOO_MANY_PERIODS` without integer overflow (`read` `RampEscalatorExpander.java`).
2. **`RampEscalatorExpander.extend`** — existing step objects are returned unchanged (including an existing short final). New periods start after covered months; the new final period may also be short. Contiguous short-tail extension is covered by unit tests (e.g. 14 → 24 yields 12+2+10) (`read` expander + `RampEscalatorExpanderTest`).
3. **Mismatch residual** — `TERM_MISMATCH` still applies when `durationInMonths < 1` or when extend is asked for a term not longer than already covered; it is **not** the rejection for a non-divisible expand (`read` expander; contrast historical DRG-533 D-04 wording).
4. **Preview / persist** — query preview of a non-divisible term returns the short-final shape without writing (`RampScheduleQueryServiceTest`); persisted 12+2 coverage is asserted in line/DTO tests cited by the delivery plan (`read` plan map; not re-executed).
5. **Editor** — UI copy states a short final period covers remaining months; generation is allowed when the final period is shorter than the interval (`read` `quote-ramp-editor.component.html` / `.spec.ts`).
6. **Schema** — short-tail shape uses existing period `months` columns; #750's quote `V1_16` is the compounding metadata column for DRG-633, **not** a short-tail schema. This brief claims **no** new migration.
7. **Flag** — `opencrm.pricing.ramp.enabled` remains default false in committed config (`read` orientation from ADR-023 / #803; **Ramp hold**).

GitNexus: MCP `list_repos` returned **zero** indexed repositories this pass (`UNKNOWN` — not a clean blast-radius check). No product symbol was edited. Text search confirmed expander / editor / test callers for short-final behavior. Future product edits need a live `analyze` + `impact` / `detect_changes` on the editing checkout.

## Residual open forks (all `Status: Proposed`)

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-634-1 | Formal CPQ Decision PASS / Done after PRE-PASS 82 | Keep Linear **In Review** until a **PASS (≥85)** on a fresh scoring tuple **and** owner OK; do not treat #750 merge as Decision Done | Mark Done on merge alone; lower the PASS threshold for this story; auto-Done via GitHub integration | Yes — Done transition |
| OQ-634-2 | Short-final generation vs historical D-04 refusal | Keep landed **ceil + short last period** for **new-quote authoring** expand/extend; do **not** treat that as rewriting DRG-533 D-04 **renewal** equal-block / lock-shape `RAMP_TERM_MISMATCH` without owner words | Revert authoring to hard `RAMP_TERM_MISMATCH` on non-divisible terms; rewrite D-04 renewal text to require short tails everywhere | Soft for flag-off authoring; hard before GA / renewal claims |
| OQ-634-3 | Short period placement | Short period is **final only** (and whole-term when term < interval); never invent a short middle period from the escalator | Allow short middle periods from the generator; pad with zero-month stubs | Soft while flag off; hard for schedule integrity |
| OQ-634-4 | Unit price on a short final | Keep landed behavior: short final carries the **same absolute unit** the escalator would have produced for that step index; months shorten; do not invent a separate day-proration of the unit | Prorate the unit by months/everyMonths; charge a full-interval TCV for a short tail | Soft for authoring; hard for money / TCV claims |
| OQ-634-5 | Extension with existing short tails | Keep landed extend: preserve authored steps (including short tails); append contiguous coverage; new final may be short | Full regenerate on extend; forbid extend when a short tail already exists; stretch the prior short tail instead of appending | Soft for authoring; hard for DRG-635 interaction |
| OQ-634-6 | Period-count ceiling / overflow | Keep landed `maxPeriods` / `TOO_MANY_PERIODS` checks that count the short final and avoid overflow | Ignore short finals in the ceiling; allow unbounded ceil | Soft for authoring; hard for abuse / DoS bounds |
| OQ-634-7 | Coupling to #803 / #871 without adopting | Cite [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) and [#871](https://github.com/drgaciw/open-crm-cpq/pull/871); **do not** regenerate amendment periods from percent; **do not** adopt OQ2–OQ7 or OQ-633-* | Treat #803 / #871 Recommended rows as Adopted; allow percent regeneration inside DRG-634 | Yes — amendment path / A9 |
| OQ-634-8 | Ramp flag / GA enablement | **NO-GO.** Keep `opencrm.pricing.ramp.enabled=false` (A2 / Ramp hold). No committed flip from this story | Flip flag true in shared config; overload ACV version with the ramp flag | Yes — AC-9 / ADR-023 D-06 |
| OQ-634-9 | Further schema after landed quote `V1_16` | **No** new migration from this Decision brief. Short tails need no new column. Claim **no** CAM `V1_16`–`V1_21`. Would-be next tip integers are observations only — **not reserved** | Add short-tail metadata columns; invent quote `V1_17`; ship CAM tables under V1_16–V1_21 | Yes — any new migration |
| OQ-634-10 | Implementation brief / further product edits | **NO-GO** on further Java/TS product work until OQ-634-* blocking rows close (or owner defers) **and** Decision PASS exists | Authorize follow-up product PRs when this docs file merges; reopen expander without PASS | Yes — any code PR |
| OQ-634-11 | Scoring tuple / allowlist freshness | Re-score only against an exact head + current file allowlist; stale tuple / missing failing-first evidence cited on Linear is not PASS evidence | Reuse PRE-PASS 82 as PASS; ignore allowlist / process-evidence gaps | Yes — Decision PASS |
| OQ-634-12 | Sibling DRG-633 / DRG-635 on same #750 | Score and Done those stories **separately**; this brief does not close them | Bundle Done for 633/634/635 from one PASS; block 634 PASS on 633/635 | Soft for 634 PASS; hard for sibling Done claims |
| OQ-634-13 | DRG-339 / apply-path residual | Disclose DRG-339 **In Progress** on any later rollout claim; do not use this brief to close apply-path gaps or flip the flag | Claim GA when authoring tests pass; enable flag while 339 open | Yes — rollout / GA |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-634-1 — Formal PASS / Done after PRE-PASS 82

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Review until PASS ≥85 + owner OK (Recommended, Proposed)** | Matches Linear hygiene. #750 remains merged code under Decision hold. |
| **B. Done on merge alone** | Contradicts PRE-PASS and the returned In Review state. **NO-GO.** |
| **C. Lower threshold / waive allowlist / waive missing RED** | Owner words + time required; not implied by this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-2 — Short-final authoring vs D-04 renewal refusal

| Option | What the owner would be choosing |
|---|---|
| **A. Landed short-final authoring; D-04 renewal text unchanged without owner words (Recommended, Proposed)** | Matches #750 expand/extend and keeps A8. |
| **B. Revert authoring to hard mismatch on non-divisible terms** | Undoes DRG-634 delivery; needs explicit owner cut. |
| **C. Rewrite D-04 so renewals must also emit short tails** | Reopens Accepted DRG-533; **out of scope** for this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-3 — Short period placement

| Option | What the owner would be choosing |
|---|---|
| **A. Final-only (and whole-term when term < interval) (Recommended, Proposed)** | Matches landed ceil loop. |
| **B. Allow generator-emitted short middles** | Breaks contiguous equal-block intuition; needs new fixtures. |
| **C. Pad remainder into prior period instead of a new row** | Different schedule shape than #750 evidence (12+2). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-4 — Unit price on a short final

| Option | What the owner would be choosing |
|---|---|
| **A. Same absolute unit for the short final; months shorten (Recommended, Proposed)** | Matches expander prices on 14→12+2 (40.0000 / 42.0000) and browser 26-month evidence shape. |
| **B. Prorate the unit by months/everyMonths** | Different money; needs new Decision fixtures. |
| **C. Charge full-interval commercial value for a short tail** | Over-charges remainder months. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-5 — Extension with existing short tails

| Option | What the owner would be choosing |
|---|---|
| **A. Preserve authored; append contiguous; new final may be short (Recommended, Proposed)** | Matches landed `extend` and short-tail contiguous tests without adopting DRG-635 Done. |
| **B. Full regenerate on extend** | Can rewrite historical absolute cells; conflicts with amendment elapsed-lock spirit (A9). |
| **C. Forbid extend when a short tail exists** | Narrows DRG-635; needs explicit owner cut. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-6 — Ceiling / overflow

| Option | What the owner would be choosing |
|---|---|
| **A. Keep landed maxPeriods including short final; no overflow (Recommended, Proposed)** | Matches `ceilingPeriodCountIsCheckedWithoutOverflow`. |
| **B. Exclude short finals from the ceiling** | Undercounts periods; weakens the guard. |
| **C. Remove the ceiling** | Abuse / DoS surface. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-7 — Coupling to #803 / #871 without adopting open OQs

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803 and #871; wait; no percent regeneration here (Recommended, Proposed)** | Preserves A9 stored-step path. Closing this brief does **not** close OQ2–OQ7 or OQ-633-*. |
| **B. Treat #803 / #871 Recommended rows as Adopted** | Out of scope; would adopt open OQs from other drafts. **NO-GO.** |
| **C. Regenerate future periods from percent inside DRG-634** | Contradicts #803 AC-8 / DRG-533 Consequences. **NO-GO** from this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-8 — Ramp flag / GA (Ramp hold)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep flag false; no GA from this story (Recommended, Proposed)** | ADR-023 D-06 / A2. **Product / flag NO-GO.** |
| **B. Flip `opencrm.pricing.ramp.enabled=true` in committed config** | Requires a separate owner un-hold; not this brief. |
| **C. Couple ACV `term-years` to the ramp flag** | Rejected by ADR-023 D-03. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-9 — Further schema / Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration from this brief; no CAM V1_16–V1_21 (Recommended, Proposed)** | Short tails reuse `months`. Landed quote `V1_16` is historical compounding metadata, not a license to open more. |
| **B. Add short-tail metadata / alter period columns now** | Product/schema work; blocked. |
| **C. Reserve or ship CAM V1_16–V1_21 here** | Band reserved by other drafts; **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-10 — Implementation brief / further product edits

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on further product PRs until forks + PASS (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Authorize expander/editor follow-ups when this file merges** | Violates hard product NO-GO. |
| **C. Reopen #750 for allowlist-only churn without PASS** | Still a product PR; needs OQ-634-1 / OQ-634-11 first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-11 — Scoring tuple freshness

| Option | What the owner would be choosing |
|---|---|
| **A. Re-score exact head + current allowlist (Recommended, Proposed)** | Addresses Linear hygiene on stale tuple / process-evidence gap (no original short-tail RED). |
| **B. Promote PRE-PASS 82 to PASS** | Below threshold; needs owner words. |
| **C. Ignore allowlist / evidence gaps** | Undermines Decision reproducibility. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-12 — Sibling 633 / 635

| Option | What the owner would be choosing |
|---|---|
| **A. Separate Decision for each story (Recommended, Proposed)** | Same #750 head may be evidence; Done is per-issue. |
| **B. One PASS closes 633+634+635** | Couples unrelated residual forks. |
| **C. Block 634 PASS until 633/635 PASS** | Optional sequencing; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-634-13 — DRG-339 / apply-path residual

| Option | What the owner would be choosing |
|---|---|
| **A. Disclose 339 In Progress; no GA / flag flip here (Recommended, Proposed)** | Matches #803 residual blockers orientation. |
| **B. Claim authoring Done = schedule apply Done** | False; apply path is DRG-339. |
| **C. Enable ramp flag while 339 open** | Violates A2 / OQ-634-8. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-634-1` .. `OQ-634-13`).
2. CPQ Decision must score the OQ-634-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes further Java/TS product work, Flyway, gateway routes, or flag flips for DRG-634.
4. Even after forks close: A1–A10 hold; **Ramp hold** (`opencrm.pricing.ramp.enabled=false`) stays; no Jev enablement; #803 OQ2–OQ7 and #871 OQ-633-* stay Proposed unless those Decision briefs close them; DRG-339 In Progress remains a residual rollout disclose.
5. Linear status for DRG-634 stays **In Review** until the owner and Decision say otherwise. This brief does **not** mark Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or further product code for DRG-634.
- Does not mark any OQ-634-* fork Adopted.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults / OQ8.
- Does not adopt or close [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) OQ-633-*.
- Does not flip `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version`.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any new quote/pricing migration.
- Does not close DRG-633, DRG-635, DRG-339, or DRG-336.
- Does not reopen ADR-023 or Accepted DRG-533 compounding / scale / renewal D-04 decisions.
- Does not change Linear status (remains **In Review**).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-634-* (especially OQ-634-1, OQ-634-8, OQ-634-10, OQ-634-11). Until then, further product work is **NO-GO**.
2. **PRE-PASS 82:** below PASS threshold; fresh scoring tuple required; original short-tail RED was not captured.
3. **Ramp hold:** flag stays off; no GA from this wave.
4. **#803 OQ2–OQ7:** still Proposed; no percent regeneration on amendments from this story.
5. **#871 OQ-633-*:** still Proposed; compounding residual is a sibling Decision.
6. **DRG-339 In Progress:** apply/accept path incomplete for rollout claims.
7. **DRG-633 / DRG-635 In Review:** sibling Decision still open on the same merged head.
8. **GitNexus index:** not present in this environment (`list_repos` empty); any future code edit needs a live `analyze` + `impact` / `detect_changes` on the editing checkout (`UNKNOWN` until then).

## Sources read this pass

- Linear: DRG-634 (In Review; comments; #750 attachment), DRG-633/#871 orientation, DRG-534/#803 orientation, DRG-339, DRG-635, DRG-532 relation.
- GitHub: #750 (merged; PRE-PASS context), #803 (draft Decision brief for ramp amendments), #871 (draft residual Decision brief for DRG-633), #644 (DRG-533 Acceptance path).
- ADR-023; DRG-533 Accepted escalator ADR draft on main (D-04 equal-block / `RAMP_TERM_MISMATCH` wording).
- Landed code: `RampEscalatorExpander` expand/extend short-final arithmetic, `RampEscalatorExpanderTest` short-tail / ceiling / contiguous extension cases, `RampScheduleQueryServiceTest` preview, `quote-ramp-editor` short-final UI/tests.
- Plan evidence cite: `docs/plans/cpq-parallel-followups-20261101.md` (delivery map only; not a Decision acceptance).
- AGENT.md / `docs/ARCHITECTURE.md` §17 holds (ramp flag treated as ADR-023 D-06 hold; Jev / dual-read holds unchanged).

## Unverified in this pass

- Runtime re-execution of #750 browser journeys or CI on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus analyze / `detect_changes` graph coverage beyond docs-only claim (MCP reported no indexed repositories).
- Whether a future Decision re-score will use head `50085ff8…` or a newer main tip that contains #750.
