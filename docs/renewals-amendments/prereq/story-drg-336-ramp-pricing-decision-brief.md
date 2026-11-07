# DRG-336 — Ramp pricing per term period: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **delivery / Done / rollout forks that remain after** Accepted ADR-023, the R1 implementation merges (#566–#577), lifecycle evidence (#583), and the 2026-10-16 acceptance-verification note that parked rollout on [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614). This file does **not** reopen Adopted ADR-023 cells, does **not** reopen Accepted DRG-533 D-01..D-06 recommendations already recorded as the escalator procedure, does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** adopt open **OQ-633-*** / **OQ-634-*** / **OQ-635-*** rows from residual Decision brief drafts [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) / [#872](https://github.com/drgaciw/open-crm-cpq/pull/872) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874), does **not** accept [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize further product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed` under id **OQ-336-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp` / R1 per-term pricing. **Story:** [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336/bc-03-09-ramp-pricing-per-term-period). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #803 / #871–#874 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted ramp baseline:** per-line schedule, whole-month periods, waterfall per period, D-03 ACV version switch separate from the ramp flag, D-06 ramp flag default **off**, D-07 period-own quantity for tiers |
| [REQ-price-ramping.md](../../backlog/REQ-price-ramping.md) | Acceptance AC-01..AC-04, AC-07..AC-09 scoped to R1; rollout section is not story Acceptance |
| [forge/41-price-ramp-sprint.md](../../forge/41-price-ramp-sprint.md) | R1 plan / PR DAG. Docs-only origin; not a later Done authorization |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults / OQ1 / OQ8). Restates stored-step AC-1..4 path and **ramp hold**. This brief **does not score or close** those rows |
| Wave-2 DRG-633 / 634 / 635 residual Decision briefs [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) / [#872](https://github.com/drgaciw/open-crm-cpq/pull/872) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) | Cite-only sibling residual forks (**OQ-633-*** / **OQ-634-*** / **OQ-635-***). Later authoring/extend delivery on #750/#772. This brief **does not score or close** those rows |
| [`specs/ramp-amendments.md`](specs/ramp-amendments.md) | Proposal only (DRG-639). Open questions stay Proposed. Not accepted by this file |
| R1 merges #566–#577, gateway #581, lifecycle evidence [#583](https://github.com/drgaciw/open-crm-cpq/pull/583) | Landed R1 code + recorded acceptance evidence. **Not** owner Done; flag stays off |
| [docs/plans/ramp-lifecycle-20261010-evidence.md](../../plans/ramp-lifecycle-20261010-evidence.md) | Recorded two-run lifecycle evidence and remaining rollout gaps. Cite only; not Decision Done |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | In Progress / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Mid-term amendment elapsed lock is **not** R1 pricing Acceptance |
| [DRG-633](https://linear.app/drgamtd-workspace/issue/DRG-633) / [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) | In Review / draft | Non-compounding percent residual. Do **not** adopt **OQ-633-*** |
| [DRG-634](https://linear.app/drgamtd-workspace/issue/DRG-634) / [#872](https://github.com/drgaciw/open-crm-cpq/pull/872) | In Review / draft | Short final period residual. Do **not** adopt **OQ-634-*** |
| [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) | In Review / draft | Extend-term persistence residual. Do **not** adopt **OQ-635-*** |
| [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614) | Backlog | Owns non-prod enablement, Entra login, rollback fault injection, broad UAT/chaos, spreadsheet reconciliation. Not closed by this brief |
| [DRG-311](https://linear.app/drgamtd-workspace/issue/DRG-311) | Related | Calculation trace / price-book version replay. Owns the AC-09 FR-07 literal gap called out on Linear |
| [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338) | Done | Per-period governance + fingerprint. Not redefined here |
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) | In Progress | Schedule / step application on accept. Residual rollout blocker; not a reason to flip the ramp flag from this brief |
| [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532) / [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) | Done | Generator / escalator ADR. Later than R1 core; not reopened |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). `opencrm.pricing.metrics.acv-version` stays committed `legacy` until a separate owner enablement act (DRG-614 / finance sign-off path). No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief that reopens product work for DRG-336 (further Java/TS edits, migrations, or enablement) is **blocked** until CPQ Decision scores the open **OQ-336-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-336-* row stays open. Closing #803 OQ2–OQ7 or #871–#874 OQ rows is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery / Decision-PASS / Done fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-336 residual)

ADR-023 Accepted the R1 schedule model. R1 product PRs (#566 DTO, #567 waterfall, #568 ACV, #570 quote authoring, #569 UI, #565 CI, #574 period engine, #577 quote wiring) merged with the ramp flag off. Lifecycle evidence [#583](https://github.com/drgaciw/open-crm-cpq/pull/583) recorded authenticated gateway acceptance (synthetic JWT/JWKS) and browser pending-approval edits. On 2026-10-16 a DoD v0.2 acceptance-verification comment mapped AC-01..AC-04 / AC-07..AC-09 and ADR-023 D-03 floors to named unit tests, parked remaining rollout on [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614), and said the story was **ready for the owner to mark Done** — while Linear status stayed **In Review**.

Without a residual Decision brief that separates **already Adopted** R1 contracts from **still-Proposed Done / enablement / amendment-coupling forks**, a follow-up could treat the 2026-10-16 note as automatic Done, flip `opencrm.pricing.ramp.enabled` or `acv-version`, claim CAM Flyway `V1_16`–`V1_21`, reopen R1 pricing while [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) amendment OQs stay open, or adopt #871–#874 recommendations by implication.

Linear DRG-336 (`Linear read` 2026-11-06): **In Review** (started 2026-10-09); blocks DRG-338/339/341 historically; related to DRG-534 / DRG-614 / DRG-311 among others; attachments ADR-023, REQ, Notion BC-03-09. Owner sign-off 2026-10-15 (finance ACV definition; flags stay off). **This brief does not change Linear status.**

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), REQ-price-ramping (`read`), and the orientation baseline in [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) (`read`). This Decision brief does **not** reopen these rows. [#803] / [#871]–[#874] mark their OQ rows Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | Per-line explicit ramp schedule; line with no schedule prices as today | ADR-023 D-01 |
| A2 | Whole-month periods on anniversary dates from term start | ADR-023 D-04 |
| A3 | TCV / ARR / exitArr definitions for ramps; ACV uses the D-03 function | ADR-023 D-02 |
| A4 | ACV version switch is **`opencrm.pricing.metrics.acv-version`**, not the ramp flag; committed default **`legacy`** | ADR-023 D-03 |
| A5 | **`opencrm.pricing.ramp.enabled` defaults false**; flag-off is 422 `RAMP_NOT_SUPPORTED` and never prices a schedule as flat; turning the flag on is outside Acceptance | ADR-023 D-06; **Ramp hold** |
| A6 | Pricing refuses to start with ramp on and ACV still `legacy` | ADR-023 D-03 coupling |
| A7 | Volume tiers use **that period's own quantity** (AC-02 TCV 135,600) | ADR-023 D-07 |
| A8 | One-time / fee / tax / discount lines reject a schedule | ADR-023 D-01; REQ |
| A9 | Worked AC-01: TCV 81,000 / ACV 27,000 (term-years) / ARR 24,000 / exit ARR 30,000 | ADR-023 D-02; REQ AC-01 |
| A10 | Docs / Decision briefs alone authorize **no** product code, flag flip, or deploy | AGENT.md / ADR-023 / #803 |
| A11 | Amendments of stored absolute periods (DRG-534) are **out of R1 Acceptance**; elapsed locking stays with DRG-534 | DRG-534; #803; this story's related links |

## What the tree does today (R1 pricing delivery)

`verified by reading code` at the base SHA.

1. **Flag** — `opencrm.pricing.ramp.enabled: false` in committed pricing-service and quote-service `application.yml` (`read`). Pricing returns 422 `RAMP_NOT_SUPPORTED` while false (`PricingExecutionService` / `PricingRampEngineTest#ac08FlagOff`).
2. **ACV version** — `opencrm.pricing.metrics.acv-version: legacy` committed (`read`). Startup guard refuses ramp-on + legacy (`RampPricingStartup`).
3. **Schedule persistence** — quote Flyway `V1_9__quote_ramp_steps.sql` hosts `quote_ramp_steps` (historical R1). Later quote `V1_16__ramp_escalator_compounding.sql` is **DRG-633/#750** delivery, not a license to reopen R1 schema from this brief.
4. **Engine / authoring** — period waterfall, escalator expand, quote wiring, and quote UI exist behind the flag (`read` inventory via tests named on the 2026-10-16 Linear note).
5. **Acceptance mapping** — Linear comment 2026-10-16 maps AC-01..04 / AC-07..09 and D-03 floors to named unit tests; AC-09 notes FR-07 price-book-version replay is **partial** and owned by DRG-311 (`Linear read`).
6. **Lifecycle evidence** — [#583](https://github.com/drgaciw/open-crm-cpq/pull/583) / `ramp-lifecycle-20261010-evidence.md` record synthetic-JWT gateway acceptance; Entra login and production enablement remain out (`read`).
7. **Migration tips on this `main`:** quote tip includes `V1_16__ramp_escalator_compounding.sql`; CAM tip observed through `V1_8__…` band (no claim). Draft CAM PRs reserve **V1_16–V1_21**. **This brief claims no migration number and opens no Flyway.**

### GitNexus (this checkout)

Docs-only change: **no production symbol is edited** by this brief. Full `gitnexus analyze` was unavailable in this pass (`No indexed repositories` from MCP). Treat any later code edit of pricing/quote ramp symbols as requiring a fresh `impact` before edit. `UNKNOWN` / `partial` / `truncated` graph results are unresolved, not low risk.

## Owner-decision table (remaining delivery / Done / rollout forks)

Every row is `Status: Proposed` under id **OQ-336-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time). Do **not** adopt open OQs from [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) or [#871](https://github.com/drgaciw/open-crm-cpq/pull/871)–[#874](https://github.com/drgaciw/open-crm-cpq/pull/874) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-336-1 | Formal Linear Done after In Review | Keep **In Review** until owner words+time mark Done under DoD v0.2; do not auto-Done from the 2026-10-16 verification note alone | Mark Done from AC unit-map alone; bot/GitHub auto-Done; leave forever In Review without owner act | Yes — Done transition |
| OQ-336-2 | Ramp flag / GA enablement | **NO-GO.** Keep `opencrm.pricing.ramp.enabled=false` (A5 / Ramp hold). Enablement lives on DRG-614 | Flip flag true in shared config; overload ACV version with the ramp flag | Yes — AC-08 / ADR-023 D-06 |
| OQ-336-3 | ACV `term-years` in shared config | Keep committed `legacy`; any shared-config `term-years` is a separate owner/DRG-614 act after finance path | Set `term-years` in committed yml with this brief; couple to ramp-on | Yes — shared-config / D-03 |
| OQ-336-4 | AC-09 / FR-07 price-book version replay | Treat AC-09 fingerprint replay as satisfied for R1 unit scope; disclose FR-07 literal gap owned by **DRG-311**; do not reopen R1 pricing schema for traces here | Block Done until DRG-311 ships; expand pricing schema now for price-book version | Soft for Done; hard if claiming full FR-07 |
| OQ-336-5 | Rollout ownership vs story Acceptance | Keep Entra / non-prod enablement / rollback fault / broad UAT / reconciliation on **DRG-614**; not R1 AC | Re-attach rollout gates to DRG-336 before Done; claim GA from lifecycle evidence alone | Yes — GA / flag |
| OQ-336-6 | Synthetic JWT evidence vs Entra | Accept synthetic JWT lifecycle as story evidence only; require real Entra under DRG-614 before enablement claims | Treat synthetic JWT as production identity proof; block Done until Entra | Soft for Done; hard for enablement |
| OQ-336-7 | Coupling to #803 / DRG-534 without adopting | Cite [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) + DRG-534 hold; **do not** adopt OQ2–OQ7; **do not** implement amendment elapsed-lock from this story | Treat #803 Recommended rows as Adopted; start DRG-534 product from R1 Done | Yes — amendment path / A11 |
| OQ-336-8 | Coupling to #871–#874 without adopting | Cite sibling residual briefs; **do not** adopt **OQ-633/634/635-***; R1 Done does not close later authoring residuals | Bundle Done for 336+633+634+635; treat #750 as R1 reopen | Soft for 336 Done; hard for sibling claims |
| OQ-336-9 | Further schema / Flyway | **No** new migration from this Decision brief. Historical `V1_9` (and later `V1_16` compounding) stay as-is. Claim **no** CAM `V1_16`–`V1_21` | Alter ramp_steps; add CAM ramp tables under V1_16–V1_21; invent new quote tip as reserved here | Yes — any new migration |
| OQ-336-10 | Implementation brief / further product edits | **NO-GO** on further Java/TS product work for DRG-336 until OQ-336-* blocking rows close (or owner defers) | Authorize pricing/quote follow-ups when this docs file merges; reopen waterfall without owner Done | Yes — any code PR |
| OQ-336-11 | DRG-339 / apply-path residual | Disclose DRG-339 **In Progress** on any rollout claim; do not use this brief to close apply-path gaps or flip the flag | Claim R1 Done = schedule apply Done; enable flag while 339 open | Yes — rollout / GA |
| OQ-336-12 | Downstream CRM / subscription delivery | Keep out of R1 Acceptance (R3 / DRG-339); disclose on rollout | Block 336 Done until CRM/subscription delivery proven | Soft for Done; hard for GA |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-336-1 — Formal Done after In Review

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Review until owner words+time under DoD v0.2 (Recommended, Proposed)** | Matches current Linear status and DoD rule that no bot sets Done alone. |
| **B. Done from the 2026-10-16 AC unit-map alone** | Note said "ready for owner"; it did not itself change status. |
| **C. Auto-Done via GitHub / bot** | Contradicts DoD v0.2 owner rule. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-2 — Ramp flag / GA (Ramp hold)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep flag false; no GA from this story (Recommended, Proposed)** | ADR-023 D-06 / A5. **Product / flag NO-GO.** |
| **B. Flip `opencrm.pricing.ramp.enabled=true` in committed config** | Requires separate owner un-hold (DRG-614 path); not this brief. |
| **C. Couple ACV `term-years` to the ramp flag** | Rejected by ADR-023 D-03. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-3 — ACV `term-years` in shared config

| Option | What the owner would be choosing |
|---|---|
| **A. Keep committed `legacy`; enablement is a separate act (Recommended, Proposed)** | Matches ADR-023 "does not authorize" shared-config `term-years`; finance sign-off allows env set later, not this brief. |
| **B. Commit `term-years` now** | Shared-config product change; blocked here. |
| **C. Require `term-years` before marking 336 Done** | Optional sequencing; owner words required (DoD does not require it for Acceptance). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-4 — AC-09 / FR-07 price-book version replay

| Option | What the owner would be choosing |
|---|---|
| **A. R1 fingerprint replay OK; FR-07 literal gap → DRG-311 (Recommended, Proposed)** | Matches 2026-10-16 Linear caveat. |
| **B. Block Done until DRG-311** | Stronger gate; needs owner words. |
| **C. Add price-book version fields in pricing now** | Product/schema; **NO-GO** from this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-5 — Rollout ownership (DRG-614)

| Option | What the owner would be choosing |
|---|---|
| **A. Rollout stays on DRG-614; not R1 AC (Recommended, Proposed)** | Matches Linear parking and DoD item 7 (flags/deploys out of stories). |
| **B. Re-attach enablement gates to DRG-336 before Done** | Widens story beyond Acceptance. |
| **C. Claim GA from #583 lifecycle evidence** | Evidence used synthetic JWT and process-only flag-on; not GA. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-6 — Synthetic JWT vs Entra

| Option | What the owner would be choosing |
|---|---|
| **A. Synthetic JWT = story evidence only; Entra under DRG-614 (Recommended, Proposed)** | Matches evidence doc disclaimer. |
| **B. Treat synthetic JWT as production identity proof** | Overclaims. **NO-GO** for enablement. |
| **C. Block Done until Entra** | Optional; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-7 — Coupling to #803 / DRG-534 without adopting OQ2–OQ7

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803 + DRG-534 hold; wait; no amendment product here (Recommended, Proposed)** | Preserves A11. Closing this brief does **not** close OQ2–OQ7. |
| **B. Treat #803 Recommended rows as Adopted** | Out of scope; would adopt open OQs from another draft. **NO-GO.** |
| **C. Implement elapsed-period lock inside DRG-336** | Wrong story; contradicts R1 scope. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-8 — Coupling to #871–#874 without adopting sibling OQs

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #871/#872/#874; score separately; do not adopt (Recommended, Proposed)** | R1 Done does not close later authoring residuals. |
| **B. One PASS/Done closes 336+633+634+635** | Couples unrelated residual forks. |
| **C. Reopen R1 waterfall to absorb #750 behavior** | Product reopen; blocked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-9 — Further schema / Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration from this brief; no CAM V1_16–V1_21 (Recommended, Proposed)** | Historical `V1_9` / later `V1_16` are facts, not a license to open more. |
| **B. Alter / extend `quote_ramp_steps` now** | Product/schema work; blocked. |
| **C. Reserve or ship CAM V1_16–V1_21 here** | Band reserved by other drafts; **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-10 — Implementation brief / further product edits

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on further product PRs until forks + owner Done path (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Authorize pricing/quote follow-ups when this file merges** | Violates hard product NO-GO. |
| **C. Reopen R1 for allowlist-only churn without owner Done** | Still a product PR; needs OQ-336-1 first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-11 — DRG-339 / apply-path residual

| Option | What the owner would be choosing |
|---|---|
| **A. Disclose 339 In Progress; no GA / flag flip here (Recommended, Proposed)** | Matches #803 residual blockers orientation. |
| **B. Claim R1 pricing Done = schedule apply Done** | False; apply path is DRG-339. |
| **C. Enable ramp flag while 339 open** | Violates A5 / OQ-336-2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-336-12 — Downstream CRM / subscription delivery

| Option | What the owner would be choosing |
|---|---|
| **A. Keep out of R1; disclose on rollout (Recommended, Proposed)** | Matches DRG-614 "not in scope" and R3 ownership. |
| **B. Block 336 Done until CRM/subscription delivery** | Optional; owner words required. |
| **C. Claim delivery proven by quote-side lifecycle** | Overclaims consumers. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-336-1` .. `OQ-336-12`).
2. CPQ Decision must score the OQ-336-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes further Java/TS product work, Flyway, gateway routes, or flag flips for DRG-336.
4. Even after forks close: A1–A11 hold; **Ramp hold** (`opencrm.pricing.ramp.enabled=false`) stays; committed ACV stays `legacy` until a separate enablement act; no Jev enablement; #803 OQ2–OQ7 and #871–#874 OQ rows stay Proposed unless those Decision briefs close them; DRG-339 In Progress and DRG-614 Backlog remain residual rollout discloses.
5. Linear status for DRG-336 stays **In Review** until the owner and Decision say otherwise. This brief does **not** mark Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or further product code for DRG-336.
- Does not mark any OQ-336-* fork Adopted.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults / OQ8.
- Does not adopt or close [#871](https://github.com/drgaciw/open-crm-cpq/pull/871) / [#872](https://github.com/drgaciw/open-crm-cpq/pull/872) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) OQ-633/634/635-* rows.
- Does not flip `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version`.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any new quote/pricing migration.
- Does not close DRG-614, DRG-339, DRG-311, DRG-534, DRG-633, DRG-634, or DRG-635.
- Does not reopen ADR-023 Adopted cells.
- Does not change Linear status (remains **In Review**).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-336-* (especially OQ-336-1, OQ-336-2, OQ-336-7, OQ-336-9, OQ-336-10). Until then, further product work is **NO-GO**.
2. **Ramp hold:** flag stays off; no GA from this wave.
3. **ACV shared-config:** stays `legacy` until a separate owner/DRG-614 act.
4. **#803 OQ2–OQ7 / DRG-534:** still Proposed / In Progress; no amendment product from this story.
5. **#871–#874 sibling residuals:** still Proposed; not adopted.
6. **DRG-339 In Progress / DRG-614 Backlog:** apply-path and rollout readiness incomplete for enablement claims.
7. **AC-09 FR-07 caveat:** price-book version replay owned by DRG-311.
8. **GitNexus index:** not refreshed in this pass; any future code edit needs a live `impact` / `detect_changes` on the editing checkout.

## Sources read this pass

- Linear: DRG-336 (In Review; comments including 2026-10-15 owner sign-off and 2026-10-16 AC verification), DRG-534/#803 orientation, DRG-614, DRG-339, related links.
- GitHub: draft Decision briefs #803, #871, #872, #874 (cite-only); lifecycle evidence #583.
- ADR-023; REQ-price-ramping; forge/41; `docs/plans/ramp-lifecycle-20261010-evidence.md`; `docs/plans/ramp-wave-20261009-r1-verification.md`.
- Landed config/code: pricing/quote `application.yml` ramp flag + ACV version; `V1_9__quote_ramp_steps.sql`; `RampPricingStartup`; `PricingRampEngineTest` AC-08.
- AGENT.md / `docs/ARCHITECTURE.md` holds orientation (Jev / dual-read / asset-write unchanged; ramp flag treated as ADR-023 D-06 hold).

## Unverified in this pass

- Runtime re-execution of lifecycle acceptance or full backend unit stage on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus analyze / `detect_changes` graph coverage beyond docs-only claim (MCP reported no indexed repositories until analyze).
- Whether the owner will mark Done from the 2026-10-16 note without a separate Decision PASS score for OQ-336-*.
