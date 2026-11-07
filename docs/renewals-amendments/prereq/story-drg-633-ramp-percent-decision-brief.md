# DRG-633 — Ramp: non-compounding percent ramp option: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **delivery forks that remain after** the Accepted escalator procedure (DRG-533 / [#644](https://github.com/drgaciw/open-crm-cpq/pull/644)) and the merged authoring slice ([#750](https://github.com/drgaciw/open-crm-cpq/pull/750)). This file does **not** reopen Adopted ADR-023 cells, does **not** reopen Accepted DRG-533 D-01..D-06 recommendations already recorded as the escalator procedure, does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** accept [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize further product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed` under id **OQ-633-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp` / percent escalator authoring. **Story:** [DRG-633](https://linear.app/drgamtd-workspace/issue/DRG-633/ramp-non-compounding-percent-ramp-option). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #803 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted ramp baseline:** schedule model, whole-month periods, D-06 ramp flag default **off**, D-03 ACV version switch separate from the ramp flag |
| [DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md](DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md) (Accepted via DRG-533 / [#644](https://github.com/drgaciw/open-crm-cpq/pull/644), 2026-10-17) | **Adopted escalator procedure:** default `compound = true` (percent on prior stored unit); `compound = false` applies each step's percent to period-1 unit; unit scale 4 half-up; amount mode does not compound |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults / OQ1 / OQ8). Restates stored-step AC-1..4 path and **ramp hold**. This brief **does not score or close** those rows |
| [`specs/ramp-amendments.md`](specs/ramp-amendments.md) | Proposal only (DRG-639). Open questions stay Proposed. Not accepted by this file |
| [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) (merged) | Authoring delivery for DRG-633/634/635: `ramp_escalator_compounding` column, expander/editor/tests. **CPQ Decision PRE-PASS 82** (below 85). Not owner Done |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Backlog / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Regeneration from percent stays out of the amendment stored-step slice |
| [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532) | Done | Equal-block generator / authoring. This brief does not reopen generator ownership |
| [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) | Done | Escalator ADR Accepted. Compounding default already decided; residual forks here are delivery / Decision-PASS only |
| [DRG-634](https://linear.app/drgamtd-workspace/issue/DRG-634) / [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) | In Review | Short final period. Same merged head; separate Decision scoring. Do not close via this brief |
| [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635) / [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) | In Review | Persist extended term. Same merged head; separate Decision scoring. Do not close via this brief |
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) | In Progress | Schedule / step application on accept. Residual rollout blocker; not a reason to flip the ramp flag from this brief |
| [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336) | In Review | New-quote ramp pricing (flag off). Related; not reopened |
| [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338) | Done | Per-period governance + fingerprint. Not redefined here |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief that reopens product work for DRG-633 (further Java/TS edits, migrations, or enablement) is **blocked** until CPQ Decision scores the open **OQ-633-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-633-* row stays open. Closing #803 OQ2–OQ7 is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` / `Adopted (DRG-533)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery / Decision-PASS fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-633 residual)

DRG-533 Accepted the escalator procedure: percent compounds on the prior stored unit by default, and `compound = false` applies each step's percent to the period-1 unit. [#750](https://github.com/drgaciw/open-crm-cpq/pull/750) merged authoring support (`Boolean compounding` on the escalator DTO, quote `V1_16__ramp_escalator_compounding.sql`, expander arithmetic, editor option, regression and browser evidence). CPQ Decision scored the story **82 (PRE-PASS, below the 85 PASS threshold)** on a stale scoring tuple and moved the issue back to **In Review**. Designated scoring and owner merge OK remain pending; Linear status must stay **In Review**.

Without a residual Decision brief that separates **already Adopted** ramp / escalator contracts from **still-Proposed delivery / PASS forks**, a follow-up could treat #750 as Decision Done, flip `opencrm.pricing.ramp.enabled`, regenerate amendment periods from percent while [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQs stay open, claim further CAM Flyway `V1_16`–`V1_21`, or adopt #803 recommendations by implication.

Linear DRG-633 (`Linear read` 2026-11-06): **In Review**; related to DRG-532; attachment #750 (merged). Hygiene comment: PRE-PASS 82; stays In Review until a PASS and drg's OK. **This brief does not change Linear status.**

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), the Accepted DRG-533 escalator ADR (`read`), and the orientation baseline in [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) (`read`). This Decision brief does **not** reopen these rows. [#803] itself marks its **OQ2–OQ7** rows Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | Per-line explicit ramp schedule / whole-month periods / contract-asset hosts the rate schedule | ADR-023 D-01, D-04, D-08 |
| A2 | **`opencrm.pricing.ramp.enabled` defaults false**; flag-off never prices a schedule as flat; turning the flag on is outside Acceptance | ADR-023 D-06; #803 residual blockers; **Ramp hold** |
| A3 | ACV version switch is **not** the ramp flag; default stays `legacy` until separately set | ADR-023 D-03 |
| A4 | Default escalator percent **compounds on the prior stored unit** (`compound = true`) | DRG-533 D-01 / Consequences |
| A5 | **`compound = false`** applies each step's percent to the **period-1 unit** (non-compounding / base-price percent) | DRG-533 D-01 |
| A6 | Amount-mode steps add currency to the prior unit and do **not** invent a second compounding rule | DRG-533 D-01 |
| A7 | Expanded units half-up at **scale 4**; extended amounts half-up at scale 2 | DRG-533 D-03 |
| A8 | Equal-block generator refuses a term the step length does not divide (`RAMP_TERM_MISMATCH`) unless a later story owns short tails | DRG-533 D-04; short-tail delivery is DRG-634, not this brief |
| A9 | Amendments of stored absolute periods (DRG-534) do **not** regenerate from percent in the stored-step slice; elapsed locking stays with DRG-534 | DRG-533 Consequences; #803 AC-1..4 path |
| A10 | Docs / Decision briefs alone authorize **no** product code, flag flip, or deploy | AGENT.md / ADR-023 / #803 |

## What the tree does today (percent / compounding delivery)

`verified by reading code` at the base SHA.

1. **`RampEscalatorDto.compounding`** — `Boolean`; omitted/null retains compound growth; `false` selects non-compounding percent (`read` `RampEscalatorDto.java`).
2. **`RampEscalatorExpander.expand`** — PERCENT + `compounding == false` uses the unrounded base increment × period index added to the period-1 base, then half-up scale 4 per output (avoids cumulative rounding drift). Otherwise `nextPrice` compounds on the prior unit (`read` `RampEscalatorExpander.java`).
3. **`RampEscalatorExpander.extend`** — non-compounding percent requires `startPrice`; extension anchors new periods from the last authored absolute using the selected arithmetic (`read` same class).
4. **Persistence** — quote Flyway `V1_16__ramp_escalator_compounding.sql` adds nullable `quote.quote_line_items.ramp_escalator_compounding`. NULL = legacy compound. No backfill required (`read` migration).
5. **Flag** — `opencrm.pricing.ramp.enabled: false` in committed `quote-service` and `pricing-service` `application.yml` (`read`). Pricing refuses to price a schedule while false.
6. **Decision score** — Linear comment on DRG-633: PRE-PASS **82** on #750; scoring tuple cited as stale vs head `50085ff8…`; issue returned to **In Review** (`Linear read`).
7. **Migration tips on this `main`:** quote tip `V1_16__ramp_escalator_compounding.sql` (already landed for this story); pricing tip beyond current tip is observation-only; CAM tip `V1_15__…`. Draft CAM PRs reserve **V1_16–V1_21**. **This brief claims no migration number and opens no further Flyway.**

### GitNexus (this checkout)

Docs-only change: **no production symbol is edited** by this brief. Full `gitnexus analyze` was unavailable in this pass (`No indexed repositories` from MCP). Treat any later code edit of `RampEscalatorExpander` / `RampEscalatorDto` as requiring a fresh `impact` before edit; prior #750 pre-edit notes recorded HIGH upstream risk on `expand` — that warning is **not** waived by this docs PR. `UNKNOWN` / `partial` / `truncated` graph results are unresolved, not low risk.

## Owner-decision table (remaining delivery / PASS forks)

Every row is `Status: Proposed` under id **OQ-633-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time). Do **not** adopt open OQs from [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-633-1 | Formal CPQ Decision PASS / Done after PRE-PASS 82 | Keep Linear **In Review** until a **PASS (≥85)** on a fresh scoring tuple **and** owner OK; do not treat #750 merge as Decision Done | Mark Done on merge alone; lower the PASS threshold for this story; auto-Done via GitHub integration | Yes — Done transition |
| OQ-633-2 | Landed null/omit = compound contract | Treat omitted/`NULL` compounding as **A4 compound** and `false` as **A5 non-compound**; freeze that as the Decision-accepted authoring contract after PASS | Reinterpret NULL as non-compound; require explicit `true`; backfill NULL → true | Soft for flag-off authoring; hard before GA |
| OQ-633-3 | Non-compounding arithmetic shape | Keep landed **base + n × unrounded base-increment, round each output to scale 4** as the A5 delivery shape | Round the increment first then multiply; require seller to enter cumulative percents (RCA-style) without a flag; full-precision then round once | Soft while flag off; hard for money parity claims |
| OQ-633-4 | Extension under non-compounding | Keep landed extend: preserve authored steps; new periods use selected arithmetic from last absolute + authored base increment | Regenerate the whole schedule on extend; forbid extend when compounding is false | Soft for authoring; hard for DRG-635 interaction |
| OQ-633-5 | Coupling to #803 without adopting | Cite [#803](https://github.com/drgaciw/open-crm-cpq/pull/803); **do not** regenerate amendment periods from percent; **do not** adopt OQ2–OQ7 | Treat #803 Recommended rows as Adopted; allow percent regeneration inside DRG-633 | Yes — amendment path / A9 |
| OQ-633-6 | Ramp flag / GA enablement | **NO-GO.** Keep `opencrm.pricing.ramp.enabled=false` (A2 / Ramp hold). No committed flip from this story | Flip flag true in shared config; overload ACV version with the ramp flag | Yes — AC-9 / ADR-023 D-06 |
| OQ-633-7 | Further schema after landed `V1_16` | **No** new migration from this Decision brief. Landed quote `V1_16` stays as-is. Claim **no** CAM `V1_16`–`V1_21`. Would-be next tip integers are observations only — **not reserved** | Alter compounding column nullability; add CAM ramp tables under V1_16–V1_21; invent quote `V1_17` as reserved here | Yes — any new migration |
| OQ-633-8 | Implementation brief / further product edits | **NO-GO** on further Java/TS product work until OQ-633-* blocking rows close (or owner defers) **and** Decision PASS exists | Authorize follow-up product PRs when this docs file merges; reopen expander without PASS | Yes — any code PR |
| OQ-633-9 | Scoring tuple / allowlist freshness | Re-score only against an exact head + current file allowlist; stale tuple cited on Linear is not PASS evidence | Reuse PRE-PASS 82 as PASS; ignore allowlist drift | Yes — Decision PASS |
| OQ-633-10 | Sibling DRG-634 / DRG-635 on same #750 | Score and Done those stories **separately**; this brief does not close them | Bundle Done for 633/634/635 from one PASS; block 633 PASS on 634/635 | Soft for 633 PASS; hard for sibling Done claims |
| OQ-633-11 | UI default for new percent ramps | Default selector / omitted wire value stays **compound (A4)**; non-compound is an explicit seller choice | Default new ramps to non-compound; hide the option behind a second flag | Soft for UX; hard if default contradicts A4 |
| OQ-633-12 | DRG-339 / apply-path residual | Disclose DRG-339 **In Progress** on any later rollout claim; do not use this brief to close apply-path gaps or flip the flag | Claim GA when authoring tests pass; enable flag while 339 open | Yes — rollout / GA |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-633-1 — Formal PASS / Done after PRE-PASS 82

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Review until PASS ≥85 + owner OK (Recommended, Proposed)** | Matches Linear hygiene. #750 remains merged code under Decision hold. |
| **B. Done on merge alone** | Contradicts PRE-PASS and the returned In Review state. **NO-GO.** |
| **C. Lower threshold / waive allowlist** | Owner words + time required; not implied by this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-2 — Null/omit = compound

| Option | What the owner would be choosing |
|---|---|
| **A. NULL/omit = A4 compound; false = A5 (Recommended, Proposed)** | Matches DRG-533 and landed DTO/migration comments. |
| **B. NULL means non-compound** | Breaks legacy compound quotes and migration "no backfill" claim. |
| **C. Require explicit true** | Forces a rewrite of omitted wire values and clones. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-3 — Non-compounding arithmetic shape

| Option | What the owner would be choosing |
|---|---|
| **A. Keep landed base + n×increment, per-output scale-4 round (Recommended, Proposed)** | Matches #750 expander and browser 100/110/120 evidence shape. |
| **B. Round increment first** | Different year-N units; needs new fixtures. |
| **C. No flag; seller enters cumulative percents** | Rejects A5 control surface already Accepted in DRG-533. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-4 — Extension under non-compounding

| Option | What the owner would be choosing |
|---|---|
| **A. Preserve authored; extend with selected arithmetic (Recommended, Proposed)** | Matches landed `extend` and DRG-635 authoring surface without adopting 635 Done. |
| **B. Full regenerate on extend** | Can rewrite historical absolute cells; conflicts with amendment elapsed-lock spirit (A9). |
| **C. Forbid extend when non-compounding** | Narrows DRG-635; needs explicit owner cut. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-5 — Coupling to #803 without adopting OQ2–OQ7

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803; wait; no percent regeneration here (Recommended, Proposed)** | Preserves A9 stored-step path. Closing this brief does **not** close OQ2–OQ7. |
| **B. Treat #803 Recommended rows as Adopted** | Out of scope; would adopt open OQs from another draft. **NO-GO.** |
| **C. Regenerate future periods from percent inside DRG-633** | Contradicts #803 AC-8 / DRG-533 Consequences. **NO-GO** from this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-6 — Ramp flag / GA (Ramp hold)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep flag false; no GA from this story (Recommended, Proposed)** | ADR-023 D-06 / A2. **Product / flag NO-GO.** |
| **B. Flip `opencrm.pricing.ramp.enabled=true` in committed config** | Requires a separate owner un-hold; not this brief. |
| **C. Couple ACV `term-years` to the ramp flag** | Rejected by ADR-023 D-03. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-7 — Further schema / Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration from this brief; no CAM V1_16–V1_21 (Recommended, Proposed)** | Landed quote `V1_16` is historical fact, not a license to open more. |
| **B. Alter / backfill compounding column now** | Product/schema work; blocked. |
| **C. Reserve or ship CAM V1_16–V1_21 here** | Band reserved by other drafts; **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-8 — Implementation brief / further product edits

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on further product PRs until forks + PASS (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Authorize expander/editor follow-ups when this file merges** | Violates hard product NO-GO. |
| **C. Reopen #750 for allowlist-only churn without PASS** | Still a product PR; needs OQ-633-1 / OQ-633-9 first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-9 — Scoring tuple freshness

| Option | What the owner would be choosing |
|---|---|
| **A. Re-score exact head + current allowlist (Recommended, Proposed)** | Addresses Linear hygiene on stale tuple / missing allowlist files. |
| **B. Promote PRE-PASS 82 to PASS** | Below threshold; needs owner words. |
| **C. Ignore allowlist drift** | Undermines Decision reproducibility. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-10 — Sibling 634 / 635

| Option | What the owner would be choosing |
|---|---|
| **A. Separate Decision for each story (Recommended, Proposed)** | Same #750 head may be evidence; Done is per-issue. |
| **B. One PASS closes 633+634+635** | Couples unrelated residual forks. |
| **C. Block 633 PASS until 634/635 PASS** | Optional sequencing; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-11 — UI default

| Option | What the owner would be choosing |
|---|---|
| **A. Default compound; non-compound explicit (Recommended, Proposed)** | Matches A4 and omitted-wire behavior. |
| **B. Default non-compound** | Contradicts Adopted default without owner words. |
| **C. Second feature flag for the option** | Extra hold surface; not required by A5. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-633-12 — DRG-339 / apply-path residual

| Option | What the owner would be choosing |
|---|---|
| **A. Disclose 339 In Progress; no GA / flag flip here (Recommended, Proposed)** | Matches #803 residual blockers orientation. |
| **B. Claim authoring Done = schedule apply Done** | False; apply path is DRG-339. |
| **C. Enable ramp flag while 339 open** | Violates A2 / OQ-633-6. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-633-1` .. `OQ-633-12`).
2. CPQ Decision must score the OQ-633-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes further Java/TS product work, Flyway, gateway routes, or flag flips for DRG-633.
4. Even after forks close: A1–A10 hold; **Ramp hold** (`opencrm.pricing.ramp.enabled=false`) stays; no Jev enablement; #803 OQ2–OQ7 stay Proposed unless that Decision brief closes them; DRG-339 In Progress remains a residual rollout disclose.
5. Linear status for DRG-633 stays **In Review** until the owner and Decision say otherwise. This brief does **not** mark Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or further product code for DRG-633.
- Does not mark any OQ-633-* fork Adopted.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults / OQ8.
- Does not flip `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version`.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any new quote/pricing migration.
- Does not close DRG-634, DRG-635, DRG-339, or DRG-336.
- Does not reopen ADR-023 or Accepted DRG-533 compounding / scale decisions.
- Does not change Linear status (remains **In Review**).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-633-* (especially OQ-633-1, OQ-633-6, OQ-633-8, OQ-633-9). Until then, further product work is **NO-GO**.
2. **PRE-PASS 82:** below PASS threshold; fresh scoring tuple required.
3. **Ramp hold:** flag stays off; no GA from this wave.
4. **#803 OQ2–OQ7:** still Proposed; no percent regeneration on amendments from this story.
5. **DRG-339 In Progress:** apply/accept path incomplete for rollout claims.
6. **DRG-634 / DRG-635 In Review:** sibling Decision still open on the same merged head.
7. **GitNexus index:** not refreshed in this pass; any future code edit needs a live `impact` / `detect_changes` on the editing checkout.

## Sources read this pass

- Linear: DRG-633 (In Review; comments; #750 attachment), DRG-534/#803 orientation, DRG-339, DRG-634, DRG-635, DRG-532 relation.
- GitHub: #750 (merged; PRE-PASS context), #803 (draft Decision brief for ramp amendments), #644 (DRG-533 Acceptance path).
- ADR-023; DRG-533 Accepted escalator ADR draft on main.
- Landed code: `RampEscalatorDto`, `RampEscalatorExpander`, `V1_16__ramp_escalator_compounding.sql`, quote/pricing `application.yml` ramp flag.
- Plan evidence cite: `docs/plans/cpq-parallel-followups-20261101.md` (delivery map only; not a Decision acceptance).
- AGENT.md / `docs/ARCHITECTURE.md` §17 holds (ramp flag treated as ADR-023 D-06 hold; Jev / dual-read holds unchanged).

## Unverified in this pass

- Runtime re-execution of #750 browser journeys or CI on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus analyze / `detect_changes` graph coverage beyond docs-only claim (MCP reported no indexed repositories until analyze).
- Whether a future Decision re-score will use head `50085ff8…` or a newer main tip that contains #750.
