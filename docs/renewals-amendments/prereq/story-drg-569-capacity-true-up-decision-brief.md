# DRG-569 — Capacity true-up policy: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept [`specs/capacity-meters.md`](specs/capacity-meters.md), amend [cluster-c-trueup-and-meters-decision-frame.md](cluster-c-trueup-and-meters-decision-frame.md), rewrite [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md), authorize an implementation brief, authorize product code, flip a flag, or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `capacity-meters`. **Story:** [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569/capacity-true-up-policy-measurement-tolerance-pack-rounding-overage) (RA-35). Linear read this pass: **In Review**. Notion: [RA-35 backlog](https://app.notion.com/p/3fbf7cb4e4df8145b792c117361bb794). Sanity-check write-up: [Pricing policy sanity check](https://app.notion.com/p/3fbf7cb4e4df816bba1fe1de4b180a60).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (still not Linear Done for DRG-569):**

| Artifact | Role |
|---|---|
| [`specs/capacity-meters.md`](specs/capacity-meters.md) | Proposal. DRG-641 worked-example section records Status: Proposed defaults with blank `Owner decision: ___ (drg, time)` slots. |
| [cluster-c-trueup-and-meters-decision-frame.md](cluster-c-trueup-and-meters-decision-frame.md) | Historical F1–F10 / G* / H* frame. Status line cites 2026-10-17 delegation; proposal labels retained as rationale. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) §C | Disposition cells say `Adopt baseline default` for F1–F10. That record **does not** fill the DRG-641 owner slots and **does not** mark Linear DRG-569 Done. |
| Fixture [`specs/fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json`](specs/fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json) | Proposed dispute-after-freeze check (DRG-570 / DRG-641). Status Proposed. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** An implementation brief for DRG-569 (or product work that prices a true-up from these knobs) is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time). Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Status: Proposed` | Option or default only. Not adopted. Slot stays `Owner decision: ___ (drg, time)`. |
| Prior frame baseline | The Cluster C / DRG-641 proposed default named for scoring. **Not** Adopted by this brief. |

## Why this Decision brief exists

RA-35: capacity-meter policy (P2) has no Linear-Done owner close with words and a time. Illustrative Scenario 2 (`read` `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md`): 64 cores committed, monthly peaks 60→80 over nine months, licence $2,500/core, maintenance $500/core/yr. Peak excess 16 ($40,000 licence) versus average excess 6 ($15,000). The charge moves about **2.8×** when back-maintenance is also on the peak path. Missing knobs named on the issue: aggregation, measurement window, tolerancePct, packSize, trueDown, overageRate, backdate. Proration method is **not** a fork here (Accepted day-stub already owns partial periods).

[DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) owns the meter model and TrueUp intent. [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) owns provenance, dispute, and frozen snapshot. [DRG-641](https://linear.app/drgamtd-workspace/issue/DRG-641) (Done) landed Proposed worked examples; it did not close DRG-569. Linear comment on DRG-569 (2026-10-17): a Proposed frame merge is not Done while the policy decision or code is open.

**This brief does not pick a commercial policy.** It lists open owner forks as OQx with Proposed options for CPQ Decision to score. Prior frame baselines are cited so Decision has a concrete row to accept, replace, or defer — not so this PR adopts them.

### Related tickets (Linear read 2026-11-06)

| Ticket | Linear status | Role for this Decision brief |
|---|---|---|
| [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) | In Review | This story. Owner forks below. |
| [DRG-641](https://linear.app/drgamtd-workspace/issue/DRG-641) | Done | Worked examples + Proposed defaults; blank owner slots remain. Prerequisite docs, not Linear Done for 569. |
| [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) | In Review | Meter model + TrueUp intent (H forks). Out of this brief's scoring set except as dependency. |
| [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) | In Review | Provenance / freeze (G forks). Dry-run freeze couples to OQ9; not re-scored here. |
| [DRG-667](https://linear.app/drgamtd-workspace/issue/DRG-667) | Backlog (ADR Accepted placeholder) | S-TX2 meter readings / rating. Blocked on DRG-569 / DRG-641 among others. No product start from this brief. |
| [DRG-471](https://linear.app/drgamtd-workspace/issue/DRG-471) | related | Period usage rating. Not this policy. |
| [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) | related | One-time / retroactive charge line — required only if OQ6 ≠ None. |
| [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539) | related | Price-book read — required only if OQ5 selects List. |
| [DRG-587](https://linear.app/drgamtd-workspace/issue/DRG-587) | Parked | On-prem self-report entry. Stays parked. |
| PR #648 | merged docs frame | Cluster C frame. Does not mark DRG-569 Done. |

## Current code / docs facts (`read`)

Facts verified against `main` SHA above. **No product true-up pricing path exists.**

| Fact | Evidence |
|---|---|
| `QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION` only — no `TRUE_UP` operation | `backend/services/quote-service/.../QuoteContinuationRequest.java` |
| CAM document vocabulary already names `TRUE_UP` intent, `TRUE_UP_CYCLE` trigger, `TRUE_UP` policy slot, `TRUE_UP_NOTICE` kind | `AmendmentIntent`, `InitiationTrigger`, `PolicySlot`, `DocumentKind`, `BusinessCategory`; `V1_7__contract_documents.sql` check constraints |
| No `MeterReading` / `TrueUpPolicy` Java type or SQL table on `main` | `rg` over `backend/**/*.java` and `backend/**/*.sql` this pass |
| Accepted proration for partial billing periods is the day-stub in `QuoteContinuationPolicy.plan` | Accepted proration record; Cluster C "Proration, stated so it is not a fork" |
| Scenario 2 figures are illustrative examples, not customer data | `cpq-pricing-policy-sanity-check.md` Scenario 2 |
| Amendment spike sketch `delta_qty = max(0, peak − entitled)` implicitly picks Peak with no tolerance/backdating | `amendment-document-design-spike.md` — sketch only |
| Self-report / attestation UI stays parked | `renewals-amendments-parking-lot.md` PL-1..PL-5; DRG-587 |

GitNexus MCP had no indexed repo in this environment (`No indexed repositories`); `.gitnexus/run.cjs` absent. Code claims above are text/`rg` evidence only — treat graph impact as unresolved (`UNKNOWN`), not as a clean blast-radius check.

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Options are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-569 / capacity true-up pricing.
4. Even after forks close: no flag flip; no dual-read / Jev / FE hold lift; no merge authority in this brief; DRG-557 / DRG-570 / DRG-667 remain residual product-path blockers to disclose on any later implementation brief.
5. Choosing OQ6 B/C does not start DRG-572. Choosing OQ5 List does not start DRG-539. Choosing OQ9 does not start DRG-551.

---

## Owner-decision table (summary)

Every row is `Status: Proposed`. Prior frame baseline is the Cluster C / DRG-641 proposed default for scoring. Alternatives are listed. **No Adopted cells. This brief does not pick policy.**

| ID | Fork | Prior frame baseline (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ1 | Aggregation method | Peak | P95; Average; EndOfPeriod | Yes — Scenario 2 licence $40k vs $15k |
| OQ2 | Measurement window | Nine calendar months ending last day of month before `contractEffective` (ex.: 2026-01-01–2026-09-30 for CE 2026-10-01) | Owner-named bounds; cycle-supplied bounds only; other fixed length | Yes — which readings enter the aggregate |
| OQ3 | Tolerance | `tolerancePct = 0` (exceedance gate; not a deduction from billable excess) | Positive percent by clause only (ex. 5% → threshold 67.2 on 64) | Yes — zero vs non-zero excess near commitment |
| OQ4 | Pack rounding | `packSize` unset (no pack) | Round excess up to clause `packSize` (ex. 13 → 16 at pack 8) | Yes — billed cores |
| OQ5 | Overage rate | Contracted (commitment-line unit rate) | List; Multiplier on contracted | Yes — unit price source |
| OQ6 | Backdating / arrears | None (forward only; arrears $0) | FromFirstExceedance; FromWindowStart (monthly qty still `max(0, reading−commitment)` after tolerance) | Yes — arrears line; couples DRG-572 if not None |
| OQ7 | True-down | Never | AtRenewal (shortfall reduces next renewal qty; no credit on this true-up); Allowed (mid-term credit) | Yes — credit / renewal hook |
| OQ8 | Rounding (average + arrears money) | Whole metric unit half-up before excess; arrears extended amount scale 2 half-up (no pre-rounded core-month rate) | Keep fractional aggregate; pre-round core-month rate then multiply | Yes — AC-3 / AC-2 money fixture |
| OQ9 | Dry-run before customer-visible | Required for that snapshot id, content hash, and policy version | Optional preview | Soft for internal draft; hard for presentation |
| OQ10 | Policy rung order | Clause → line → agreement → account → these defaults (mirror Accepted uplift D-04 shape; does not amend D-04) | Account → agreement → line; account-only | Yes — which rung wins each knob |
| OQ11 | Where resolved policy is stored | Pins on change line + document ids; no `TrueUpPolicy` table (ADR-035 D-05) | `TrueUpPolicy` table in contract-documents (needs ADR amending D-05) | Soft for first pricing; hard for persistence shape |
| — | Proration method | **Not a fork** — Accepted day-stub | — | Consumed; do not re-open |

`Owner decision: ___ (drg, time)` for every OQ row above.

---

## Fork detail

Numbering is this Decision brief's OQx set for DRG-569 policy knobs. Cluster C labels F1–F10 are cited as prior-frame aliases. G* (DRG-570) and H* (DRG-557) are dependencies, not scored here.

### OQ1 — Aggregation (Cluster C F1)

| Option | What the owner would be choosing (Scenario 2 readings) |
|---|---|
| **A. Peak (prior frame baseline, Proposed)** | High-water 80. Excess 16. Licence $40,000 at contracted $2,500. |
| **B. P95** | Vocabulary legal; no worked fixture on main. Needs owner-supplied example before AC. |
| **C. Average** | Mean 69.8 → (with OQ8-A) 70. Excess 6. Licence $15,000. |
| **D. EndOfPeriod** | Last reading in window; collides with Peak on this particular window. |

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ2 — Measurement window (DRG-641 Proposed default; no Cluster C fork letter)

| Option | What the owner would be choosing |
|---|---|
| **A. Nine calendar months before `contractEffective` (prior frame / DRG-641 baseline, Proposed)** | Inclusive bounds; drop readings outside. Scenario 2: 2026-01-01–2026-09-30. |
| **B. Cycle supplies both bounds** | No implicit nine-month default when cycle is silent — refuse or wait. |
| **C. Other fixed length** | Owner names N months / calendar year / term-to-date. |

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ3 — Tolerance (Cluster C F2)

| Option | What the owner would be choosing |
|---|---|
| **A. `tolerancePct = 0` (prior frame baseline, Proposed)** | Any aggregate above commitment is excess. Gate, not a post-gate deduction. |
| **B. Positive percent by clause only** | Ex. 5% on 64 → threshold 67.2. Peak 66 bills 0; peak 80 still bills 16. |

**Prior frame baseline (Proposed):** A, with B available as clause. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ4 — Pack rounding (Cluster C F3)

| Option | What the owner would be choosing |
|---|---|
| **A. No pack (prior frame baseline, Proposed)** | Excess stays the integer aggregation produced. |
| **B. Round excess up to `packSize`** | Sanity-check variant: 13 excess, pack 8 → bill 16. |

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ5 — Overage rate (Cluster C F5)

| Option | What the owner would be choosing |
|---|---|
| **A. Contracted (prior frame baseline, Proposed)** | Unit rate on the commitment line (Scenario 2: $2,500). |
| **B. List** | Price-book unit at a date. Needs DRG-539. Missing list → no price (no silent fallback). |
| **C. Multiplier** | Contracted × clause factor. |

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ6 — Backdating / arrears (Cluster C F6)

| Option | What the owner would be choosing |
|---|---|
| **A. None (prior frame baseline, Proposed)** | Forward from contract effective only. No back-maintenance line. Scenario 2 leaves $2,416.67 illustration unbilled. |
| **B. FromFirstExceedance** | Arrears service period from first reading over commitment after tolerance (ex. April–September; 58 excess core-months). Charge-line type is DRG-572 — not started by this choice alone. |
| **C. FromWindowStart** | Service period from window start; monthly quantity still `max(0, reading − commitment)` after tolerance. |

Any partial billing period still uses the Accepted proration day-stub. Choosing B/C does not invent a second proration formula.

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ7 — True-down (Cluster C F4)

| Option | What the owner would be choosing |
|---|---|
| **A. Never (prior frame baseline, Proposed)** | Aggregate ≤ commitment → `deltaQty` 0; no credit. |
| **B. AtRenewal** | Shortfall carried to next renewal quantity; this true-up issues neither reduction nor credit. Renewal hook unset. |
| **C. Allowed** | Mid-term credit on this true-up. Size/rate/cap unset; credits/fees are DRG-550. |

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ8 — Rounding of average and arrears money (Cluster C F7)

| Option | What the owner would be choosing |
|---|---|
| **A. Whole unit half-up before excess; arrears extended amount scale 2 half-up (prior frame baseline, Proposed)** | 69.8 → 70. 58 × ($500/12) → $2,416.67. |
| **B. Fractional aggregate; pre-rounded core-month rate** | Excess 5.8 possible; arrears $2,416.86 if rate pre-rounded to $41.67. |

PVU/TB/session may be fractional by clause metric scale. Renewal unit scale 4 (Accepted uplift D-05) is not this core count.

**Prior frame baseline (Proposed):** A for a core metric. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ9 — Dry-run before customer-visible (Cluster C F9)

| Option | What the owner would be choosing |
|---|---|
| **A. Required (prior frame baseline, Proposed)** | No customer-visible state until dry-run completes for snapshot id + content hash + policy version. Preview shows excess, rate source, maintenance trace, snapshot hash, policy version. |
| **B. Optional** | Presented quote may be the first run. |

Couples to DRG-570 freeze-at-dry-run (G4). Does not start DRG-551 platform dry-run/revert.

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ10 — Policy rung order (Cluster C F8)

| Option | What the owner would be choosing |
|---|---|
| **A. Clause → line → agreement → account → defaults (prior frame baseline, Proposed)** | Same shape as Accepted uplift D-04 for renewal fields; does **not** claim D-04 already covers true-up; does not amend D-04. Snapshot names the winning rung per field. |
| **B. Account → agreement → line** | Sanity-check / capacity-meters sketch phrase; opposite specificity if read as first-match. |
| **C. Account only** | No line override for peak vs average. |

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ11 — Where the resolved policy is stored (Cluster C F10)

| Option | What the owner would be choosing |
|---|---|
| **A. Pins on change line + document ids; no policy table (prior frame baseline, Proposed)** | Matches ADR-035 D-03 / D-05. |
| **B. `TrueUpPolicy` table in contract-documents** | Conflicts with Accepted D-05; needs a later ADR. |

**Prior frame baseline (Proposed):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### Proration — not a fork

The sanity-check field `prorationMethod { Daily | MonthDay | None }` is not offered. Partial billing periods use Accepted proration D-01 in `QuoteContinuationPolicy.plan`. The Scenario 2 forward-maintenance $2,016.44 figure is a 92/365 illustration, not a second method. Perpetual licence amount is excess × OQ5 rate with no day fraction.

---

## Worked Scenario 2 check (illustrative only)

If every prior-frame baseline were later Accepted with words+time, on the example readings: excess **16**, licence **$40,000**, no pack, no tolerance effect, no true-down credit, no arrears line. Forward maintenance is whatever the Accepted day-stub returns for that maintenance line's billing period — **not** defined as $2,016.44 inside this brief. Replacing OQ1 with Average (and OQ8-A) moves licence to **$15,000**. Turning OQ6 to FromFirstExceedance adds the ~$2,416.67 arrears illustration and produces the sanity check's about-2.8× spread.

These numbers are fixtures for Decision, not an adoption claim.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-569 (RA-35) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ1–OQ11 |

Example shape once scored (illustrative only, **not** a claim): `DRG-569 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-569 / true-up pricing / meter ingestion.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not amend the 2026-10-17 delegated disposition index or rewrite Cluster C.
- Does not accept `specs/capacity-meters.md` or flip any fixture from Proposed to accepted.
- Does not start DRG-557, DRG-570, DRG-572, DRG-539, DRG-551, DRG-667, or un-park DRG-587 / PL-1..PL-5.
- Does not add `TRUE_UP` to `QuoteContinuationRequest.Operation`, create a `MeterReading` table, or register events.
- Does not mark Linear DRG-569 Done.
- Does not merge or enable auto-merge on any PR.
- Does not flip dual-read, Jev, asset-write, or ramp flags.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ1–OQ11. Until then, implementation brief is blocked (**NO-GO** for product Java/TS on true-up pricing semantics).
2. **Blank DRG-641 slots:** capacity-meters.md owner decision cells remain `___ (drg, time)` until words+time are written.
3. **DRG-557 In Review:** meter model / TrueUp intent not Closed for product.
4. **DRG-570 In Review:** provenance / freeze not Closed for product.
5. **DRG-667 Backlog:** S-TX2 placeholder; no migration numbers; blocked on this policy close among others.
6. **No MeterReading / TrueUpPolicy types** on `main`; CAM has intent vocabulary only.
7. **DRG-572 / DRG-539:** disclose if Decision replaces OQ6-A or OQ5-A.
8. **AGENT.md holds:** dual-read, NgRx, Jev enablement beyond local, contract-asset FE — untouched.

## Sources read this pass

- Linear: DRG-569 (full description + comments + relations), DRG-557, DRG-570, DRG-641, DRG-667.
- Spec: `docs/renewals-amendments/prereq/specs/capacity-meters.md`.
- Frame: `docs/renewals-amendments/prereq/cluster-c-trueup-and-meters-decision-frame.md` (DRG-569 F1–F10).
- Delegation: `docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md` §C.
- Sanity check Scenario 2: `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md`.
- Fixture: `docs/renewals-amendments/prereq/specs/fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json`.
- Pattern: draft PRs #803 / #809 Decision briefs.
- Code: `QuoteContinuationRequest.java`; CAM `AmendmentIntent` / `PolicySlot` / `InitiationTrigger` / `V1_7__contract_documents.sql`; `rg` for `MeterReading` / `TrueUpPolicy` (none).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17.
