# DRG-534 — Ramp amendments: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept [`specs/ramp-amendments.md`](specs/ramp-amendments.md), amend ADR-023, authorize an implementation brief, authorize product code, flip a flag, or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp-amendments`. **Story:** [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534/ramp-amendments-lock-elapsed-periods-amend-future-periods-re-govern-on). Linear read this pass: Backlog. Notion mirror: [DRG-534 backlog row](https://app.notion.com/p/3fbf7cb4e4df81f08315f3eb1bb8e415) (Status Not started; Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec authority (still Proposal):** [`specs/ramp-amendments.md`](specs/ramp-amendments.md) aligned under [DRG-639](https://linear.app/drgamtd-workspace/issue/DRG-639) (Done; #694). Locked-elapsed fixture: [`specs/fixtures/ramp-amendments/locked-elapsed-period.json`](specs/fixtures/ramp-amendments/locked-elapsed-period.json) (`"status": "provisional"`).

**Scout:** [bc-18c97248-de22-57c9-bcb8-f00e72085a37](https://cursor.com/agents/bc-18c97248-de22-57c9-bcb8-f00e72085a37) — Decision brief to close owner forks OQ2–OQ7; do not authorize product code yet.

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off. An implementation brief for DRG-534 is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Status: Proposed` | Recommendation or default only. Not adopted. Slot stays `Owner decision: ___ (drg, time)`. |

## Prerequisite and AC path (stored-step)

DRG-534's first product slice amends **stored explicit periods**. It does not regenerate future periods from a percent escalator (spec AC-8; DRG-533 Consequences). The Linear acceptance core maps to spec AC-1..4 on that stored-step path.

| Spec AC | Claim | Path readiness against landed / in-flight work |
|---|---|---|
| AC-1 Elapsed period locked | Year-2 amendment cannot change year-1 stored row; byte-identity fixture | Depends on stored `subscription_rate_periods` shape from ramp accept/apply. **Does not** need escalator forks. |
| AC-2 Future period editable | Replace of a future period's unit price accepted; elapsed rows unchanged | Same stored-step path. `exitArr` recomputation default remains Proposed (Defaults table). |
| AC-3 Year-3 change re-approves | Fingerprint change supersedes prior approval; per-period governance; worst period | Calls landed DRG-338 fingerprint + worst-period outcome. Fork OQ7 still decides human-vs-auto when inside policy. |
| AC-4 Delete only at the end | `DeleteEnd` last period only; no gap | Schedule shape only. Fork OQ4 still decides credit / renewal coupling. |

### Related tickets (Linear read 2026-11-06)

| Ticket | Linear status | Role for this Decision brief |
|---|---|---|
| [DRG-639](https://linear.app/drgamtd-workspace/issue/DRG-639) | Done | Spec + fixture aligned to DRG-533 ADR. Prerequisite for this brief. |
| [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532) | Done | Ramp schedule authoring / generate-from-term. Landed for new-quote generation; this slice does not reopen generator semantics. |
| [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) | Done | Escalator ADR on main. Recommendations cited; forks stay Proposed. Stored-step AC-1..4 do not apply percent regeneration. |
| [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338) | Done | Per-period governance + fingerprint binding. AC-3 / OQ6 call that outcome; this brief does not redefine the comparison key. |
| [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635) | In Review | Quote term persist on ramp extend. Related authoring surface; not a blocker to scoring amendment forks. Formal CPQ Decision Done not claimed. |
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) | In Progress | Ramp schedule / step application on accept. **Still open.** Contract consumer apply path is incomplete relative to rollout. Residual blocker for product implementation after forks close; not a reason to skip this Decision brief. |
| [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336) | In Review | New-quote ramp pricing (flag off). Related landed-scope story; this slice does not reopen it. |
| Flag `opencrm.pricing.ramp.enabled` | Default off (`read` ADR-023 / spec AC-9) | Stays off. No part of this brief authorizes turning it on. |

**Verdict on AC-1..4 stored-step path:** Spec text and provisional fixture exist (DRG-639). Governance and fingerprint primitives exist (DRG-338 Done). Escalator ADR exists (DRG-533 Done) without forcing regeneration into this slice. Authoring/extend work is Done or In Review (DRG-532 / DRG-635). **Product lock/amend/re-govern code does not exist.** Implementation brief remains blocked on owner fork closure **and** remains gated by DRG-339 In Progress plus ramp flag off.

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Recommendations are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-534.
4. Even after forks close: no flag flip; no merge authority in this brief; DRG-339 In Progress remains a residual product-path blocker to disclose on any later implementation brief.

---

## Owner-decision table (summary)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ2 | Current period containing `asOfDate` | Immutable; next period is first editable | Split into elapsed stub + editable remainder; treat current as editable | Yes — AC-5 pending until chosen |
| OQ3 | Quantity edits on future Replace | Price and quantity both allowed; volume tier uses that period's own quantity | Price-only; quantity-only; quantity change without re-tier | Yes — Replace payload shape |
| OQ4 | `DeleteEnd` / term shorten / credit | Allow `DeleteEnd` for schedule shape (AC-4); emit **no credit** in this slice | Forbid shorten without renewal; emit prorated credit; require DRG-550 credit policy before any DeleteEnd | Shape: AC-4. Credit: money path |
| OQ5 | Append a period / extend term on amendment | Reject append | Allow append (term extension); allow only with separate Renew intent | Yes — term extension |
| OQ6 | Governance "worst period" key | Call DRG-338 outcome; do not redefine | Redefine comparison key in this story | Soft — AC-3 needs a defined call |
| OQ7 | Re-approval vs auto-bind when fingerprint changes but inside policy | New binding always; auto-bind allowed when worst period is inside policy (no mandatory human) | Mandatory human re-approval on every fingerprint change; silent edit of prior binding (rejected by AC-3) | Yes — AC-3 human/auto |
| D-elapsed | Elapsed = `end_date` ≤ `asOfDate` | Adopt that definition | Elapsed = fully billed; elapsed = start before asOf | Yes — AC-1 boundary |
| D-regen | Future regeneration start | Last elapsed stored absolute; **not in this slice** | Regenerate from percent in this slice | No for AC-1..4 stored-step |
| D-exitArr | `exitArr` after period-3 → 55.0000 | 33000.00 | Other rounding / ARR definition | Secondary to AC-2 |
| OQ1 | Escalator semantics (DRG-533 D-01..D-03) | Leave to DRG-533 owner forks; AC-8 keeps amendments as stored steps | Force regeneration here | No for AC-1..4 |
| ADR-F1..F7 | Compound, scale, lock uplift, segments, named-seat, month locks, protection marker | Keep as Proposed in Defaults table; not selected here | See DRG-533 ADR | No for AC-1..4 |
| OQ8 | Ramp / ACV flags | Stay off / legacy | Enable ramp; change ACV version | Yes — AC-9; out of slice |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (OQ2–OQ7)

Numbering matches [`specs/ramp-amendments.md`](specs/ramp-amendments.md) Open questions 2–7. Open question 1 (escalator) and 8 (flag) are listed in the summary table and in Residual blockers; they are not scored as product-slice choices here.

### OQ2 — Period that contains `asOfDate` (current period)

| Option | What the owner would be choosing |
|---|---|
| **A. Immutable current (Recommended, Proposed)** | Period containing `asOfDate` cannot be Replace'd or DeleteEnd'd. First editable period is the next future period. Matches proposed AC-5. |
| **B. Split current** | Cut at `asOfDate` into an elapsed stub (immutable) and an editable remainder. Creates a mid-period boundary ADR-023 D-04 does not currently create. |
| **C. Editable current** | Allow Replace of remaining commercial terms on the in-progress period (day stub / proration path). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** Treat current as immutable in design text; AC-5 tests stay pending. `Owner decision: ___ (drg, time)`.

### OQ3 — Quantity edits on a future period

| Option | What the owner would be choosing |
|---|---|
| **A. Price and quantity (Recommended, Proposed)** | `Replace` may change quantity and/or `priceBasis`. Volume tier (ADR-023 D-07) uses that period's own quantity. |
| **B. Price only** | Quantity locked for the term; only unit price / discount basis changes. |
| **C. Quantity only** | Price locked; quantity may change with re-tier. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** Spec proposal allows both on future Replace. `Owner decision: ___ (drg, time)`.

### OQ4 — Shortening via `DeleteEnd` and credit

| Option | What the owner would be choosing |
|---|---|
| **A. Shape yes, credit no (Recommended, Proposed)** | AC-4 schedule rules stand. `DeleteEnd` shortens term to new last period end. This slice writes **no** credit memo and no termination fee. Credit/fee stay with DRG-550 / parked cancellation policy if needed later. |
| **B. Forbid DeleteEnd without Renew** | Reject term shorten on amendment; only edit prices/quantities of remaining future periods. |
| **C. DeleteEnd emits prorated credit** | Shorten and create unused-period credit under a named policy (requires DRG-550 credit fork). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** AC-4 shape only; money/credit unset and not implemented. `Owner decision: ___ (drg, time)`.

### OQ5 — Append a period (term extension on amendment)

| Option | What the owner would be choosing |
|---|---|
| **A. Reject append (Recommended, Proposed)** | Seller may DeleteEnd and Replace future periods only. Append / term lengthen on amendment is rejected until a later owner allow. |
| **B. Allow append** | Seller may add a trailing period and extend term on the amendment path (overlaps DRG-635 extend-on-quote semantics; must not silent-flip ramp flag). |
| **C. Append only via Renew intent** | Term extension is a Renew lifecycle intent, not an Amend of stored ramp steps. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** Reject append. `Owner decision: ___ (drg, time)`.

### OQ6 — Governance "worst period"

| Option | What the owner would be choosing |
|---|---|
| **A. Call DRG-338 (Recommended, Proposed)** | This slice invokes the landed ramp-governance worst-period outcome and fingerprint supersession. It does not invent a second comparison key. |
| **B. Redefine here** | Pick deepest discount / lowest price / failed rule anew inside DRG-534. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** Call DRG-338; do not redefine. `Owner decision: ___ (drg, time)`.

### OQ7 — Re-approval vs auto-bind when still inside policy

| Option | What the owner would be choosing |
|---|---|
| **A. New binding; auto-bind OK inside policy (Recommended, Proposed)** | Fingerprint change always supersedes the prior approval (AC-3). If worst period is inside policy, the new binding may auto-approve without a human. Outside policy still routes per DRG-338. |
| **B. Mandatory human on every fingerprint change** | Even an inside-policy year-3 price edit requires a human approver. |
| **C. Silent mutate prior binding** | Forbidden by AC-3 / ADR-013 supersession model. Listed only to reject. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** AC-3 requires new binding + superseded prior; does not require human in every case. `Owner decision: ___ (drg, time)`.

---

## Other blank Owner decision slots (from spec Defaults)

These remain blank in [`specs/ramp-amendments.md`](specs/ramp-amendments.md). This brief does not fill them as Adopted.

| Slot | Spec default text | Recommended handling in this brief (Proposed) |
|---|---|---|
| Elapsed = end on or before `asOfDate` | Proposed | Same as D-elapsed; needed for AC-1 |
| Current period immutable | Proposed | Same as OQ2-A |
| Regeneration from last elapsed absolute; not in slice | Proposed | Keep out of slice (AC-8) |
| `compound = true` (ADR fork 1) | Proposed | Defer to DRG-533 / OQ1; not AC-1..4 |
| Unit scale 4 / amount scale 2 (ADR fork 2) | Proposed | Column scales already used in fixture; escalator procedure deferred |
| One uplift after lock (ADR fork 3) | Proposed | Defer |
| Explicit segments when lengths differ (ADR fork 4) | Proposed | Defer |
| No second named-seat tier on renewal net (ADR fork 5) | Proposed | Defer |
| Month-boundary locks only (ADR fork 6) | Proposed | Aligns with AC-6; still Proposed |
| `ENABLE_12M_PRICE_PROTECTION` marker (ADR fork 7) | Proposed | Defer |
| `exitArr` 33000.00 after period-3 replace | Proposed | Recommended secondary assert for AC-2 |

Each: `Status: Proposed`. `Owner decision: ___ (drg, time)`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-534.
- Does not mark any fork Adopted.
- Does not flip `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version`.
- Does not close DRG-339, DRG-635, or DRG-336.
- Does not accept escalator compounding/base/rounding as in force beyond what DRG-533 already recorded as recommendations.
- Does not edit the provisional fixture status to accepted.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ2–OQ7 (and any Defaults slots Decision chooses to bind for AC-1..4). Until then, implementation brief is blocked.
2. **DRG-339 In Progress:** schedule application / accept consumer path incomplete for rollout claims.
3. **Ramp flag off:** AC-9; no enablement in this wave.
4. **DRG-635 In Review:** not a fork blocker; disclose until Decision Done if extend-term interaction is in scope of a later implementation brief.
5. **No product amend/re-govern code** on main for DRG-534 (scout + this pass).
6. **Credit/termination money** for DeleteEnd remains tied to DRG-550 / parked policies if owner rejects OQ4-A.

## Sources read this pass

- Linear: DRG-534, DRG-639, DRG-532, DRG-533, DRG-338, DRG-635, DRG-339, DRG-336.
- Notion: DRG-534 backlog row.
- Spec: `docs/renewals-amendments/prereq/specs/ramp-amendments.md`.
- Fixture: `docs/renewals-amendments/prereq/specs/fixtures/ramp-amendments/locked-elapsed-period.json`.
- Scout comment on DRG-534 (2026-11-05).
- ADR draft cited by spec: `docs/renewals-amendments/prereq/DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md` (status wording not adopted as fork acceptance).
