# DRG-339 — Ramp schedule and step application: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **delivery / Done / rollout forks that remain after** Accepted ADR-023, the R3 implementation merges (#584, #589–#593, #595 and supporting quote/pricing PRs), local eight-service acceptance evidence ([`docs/plans/ramp-next-20261010-validation.md`](../../plans/ramp-next-20261010-validation.md)), and the 2026-10-16 DoD v0.2 hygiene that keeps Linear off Done until the owner merges and every v0.2 item passes. This file does **not** reopen Adopted ADR-023 cells, does **not** reopen Accepted DRG-533 D-01..D-06 recommendations already recorded as the escalator procedure, does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** adopt open **OQ-336-*** rows from the DRG-336 residual Decision brief draft [#876](https://github.com/drgaciw/open-crm-cpq/pull/876), does **not** adopt open **OQ-635-*** rows from the DRG-635 residual Decision brief draft [#874](https://github.com/drgaciw/open-crm-cpq/pull/874), does **not** accept [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize further product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed` under id **OQ-339-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp` / R3 schedule + step application. **Story:** [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339/bc-09-04-ramp-schedule-and-step-application). Linear read this pass: **In Progress**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #876 / #803 / #874 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted ramp baseline:** per-line schedule, whole-month periods, contract-asset hosts the rate schedule, D-06 ramp flag default **off**, D-05 exit-rate renewal base, D-03 ACV version switch separate from the ramp flag |
| [REQ-price-ramping.md](../../backlog/REQ-price-ramping.md) | Acceptance AC-10..AC-14 scoped to R3; rollout section is not story Acceptance |
| [forge/41-price-ramp-sprint.md](../../forge/41-price-ramp-sprint.md) | R3 plan / PR DAG. Docs-only origin; not a later Done authorization |
| [docs/plans/ramp-next-20261010-r3-plan.md](../../plans/ramp-next-20261010-r3-plan.md) | Expanded 21-point R3 lane decisions (deal-type branches, target identity, ledger, cancel guard). Cite only; not Decision Done |
| [docs/plans/ramp-next-20261010-validation.md](../../plans/ramp-next-20261010-validation.md) | Local eight-service acceptance evidence and remaining rollout gates. Cite only; **does not** certify scheduled `RampApplied` publication or every renewal/amendment/race path |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults / OQ1 / OQ8). Restates stored-step AC-1..4 path and **ramp hold**. This brief **does not score or close** those rows |
| Wave-2 DRG-336 residual Decision brief [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) | Cite-only R1 pricing residual forks (**OQ-336-***). This brief **does not score or close** those rows |
| Wave-2 DRG-635 residual Decision brief [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) | Cite-only extend-term residual forks (**OQ-635-***). This brief **does not score or close** those rows |
| [`specs/ramp-amendments.md`](specs/ramp-amendments.md) | Proposal only (DRG-639). Open questions stay Proposed. Not accepted by this file |
| R3 merges #584 (accepted snapshots), #589 (rate/consumer/ledger/scheduler/ERP), #590–#593, #595 (catalog CHECK) | Landed R3 code + recorded local acceptance. **Not** owner Done; pricing ramp flag stays off |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | In Progress / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Mid-term amendment elapsed lock is **not** R3 apply Acceptance |
| [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336) / [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) | In Review / draft | R1 pricing residual. Do **not** adopt **OQ-336-*** |
| [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) | In Review / draft | Extend-term persistence residual. Do **not** adopt **OQ-635-*** |
| [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338) | Done | Per-period governance + fingerprint. Predecessor; not redefined here |
| [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341) | Related | Hybrid migration ramp (R4). File-disjoint after R2; not closed by this brief |
| [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614) | Backlog | Owns non-prod enablement, Entra login, rollback fault injection, broad UAT/chaos, spreadsheet reconciliation. Not closed by this brief |
| [DRG-340](https://linear.app/drgamtd-workspace/issue/DRG-340) | Related | Credit draw-down. Out of R3 |
| [DRG-633](https://linear.app/drgamtd-workspace/issue/DRG-633) / [DRG-634](https://linear.app/drgamtd-workspace/issue/DRG-634) | In Review | Later authoring residuals. Cite via sibling wave; do **not** adopt their OQs here |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). `opencrm.pricing.metrics.acv-version` stays committed `legacy` until a separate owner enablement act (DRG-614 / finance sign-off path). No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief that reopens product work for DRG-339 (further Java/TS edits, migrations, or enablement) is **blocked** until CPQ Decision scores the open **OQ-339-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-339-* row stays open. Closing #803 OQ2–OQ7, #876 OQ-336-*, or #874 OQ-635-* is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery / Decision-PASS / Done fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-339 residual)

ADR-023 Accepted the schedule model and named ApplyRamp / RampApplied / GetRampSchedule / RampScheduler. R3 product work merged: additive accepted-v1 ramp snapshots (#584), contract-asset rate persistence / consumer / ledger / scheduler / ERP claim safety (#589), quote acceptance wiring and Kafka test lifecycle repairs (#590–#593), and catalog CHECK forward migration (#595). Local eight-service acceptance at `b180052d` recorded process-only ramp flag-off, target-linked expansion, hybrid migration numbers, and outbox/Kafka period/ledger consumption ([validation note](../../plans/ramp-next-20261010-validation.md), `read`). That note explicitly **does not certify scheduled RampApplied publication** or every renewal/amendment/actual-day/cancellation/race path. Linear comments (2026-10-16, `Linear read`) record: code already merged; Delivery agent A not launching; DoD v0.2 approved; **Done only when the owner merges to main and every v0.2 item passes; no bot sets Done alone**; status ceiling stays short of Done.

Without a residual Decision brief that separates **already Adopted** R3 contracts from **still-Proposed Done / enablement / certification / amendment-coupling forks**, a follow-up could treat the validation note or merged PRs as automatic Done, flip `opencrm.pricing.ramp.enabled`, treat `opencrm.subscription-ramp.enabled` as a GA substitute, claim CAM Flyway `V1_16`–`V1_21`, reopen apply-path product work while [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) / [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) OQs stay open, or claim scheduled apply certified by unit/PostgreSQL alone.

Linear DRG-339 (`Linear read` 2026-11-06): **In Progress** (started 2026-10-10); blocked by DRG-338 (Done) and DRG-336 (In Review); related to DRG-614 / DRG-534 / DRG-340; attachments ADR-023, REQ, Notion BC-09-04, PR #575. Estimate 21. **This brief does not change Linear status.**

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), REQ-price-ramping (`read`), forge/41 (`read`), and the orientation baseline in [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) / [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) (`read`). This Decision brief does **not** reopen these rows. [#803] / [#876] / [#874] mark their OQ rows Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | Per-line explicit ramp schedule; contract-asset hosts the runtime rate schedule | ADR-023 D-01, D-08 |
| A2 | Whole-month periods; anniversary dates from term start; boundary date belongs to the new period | ADR-023 D-04; REQ FR-21/FR-22 |
| A3 | `RampScheduler` applies due steps; `ApplyRamp` is idempotent per subscription + accepted line UUID + sequence; emits `RampApplied` with **effective date**, not run time; catch-up in order after downtime | ADR-023 Consequences; REQ FR-23; AC-12 |
| A4 | GetRampSchedule returns stored periods (no subscriptions UI in this story) | ADR-023; REQ AC-11 |
| A5 | Amendment at a whole-month boundary inherits remaining periods (not day-prorated in that AC); renewal uplift uses exit rate (AC-14 / $54.00) | ADR-023 D-05; REQ AC-13/AC-14 |
| A6 | **`opencrm.pricing.ramp.enabled` defaults false**; flag-off is 422 `RAMP_NOT_SUPPORTED` and never prices a schedule as flat; turning the flag on is outside Acceptance | ADR-023 D-06; **Ramp hold** |
| A7 | ACV version switch is **`opencrm.pricing.metrics.acv-version`**, not the ramp flag; committed default **`legacy`** | ADR-023 D-03 |
| A8 | QuoteAccepted stays **v1** with additive optional ramp / exitArr; one per-line `ramp` list only | ADR-023; forge/41 R3; #575/#584 |
| A9 | Docs / Decision briefs alone authorize **no** product code, flag flip, or deploy | AGENT.md / ADR-023 / #803 |
| A10 | Amendments of stored absolute periods (DRG-534) are **out of R3 Acceptance**; elapsed locking stays with DRG-534 | DRG-534; #803 |
| A11 | Cancellation **event contract** stays outside R3; allowed-state cancel guard is in-lane | R3 plan decision 12; Linear scope |

## What the tree does today (R3 apply delivery)

`verified by reading code` at the base SHA.

1. **Pricing / quote ramp flag** — `opencrm.pricing.ramp.enabled: false` in committed pricing-service and quote-service `application.yml` (`read`). **Ramp hold** unchanged.
2. **Subscription apply job** — `SubscriptionRampJob` is `@ConditionalOnProperty(name = "opencrm.subscription-ramp.enabled", havingValue = "true", matchIfMissing = true)` (`read`). Polls due subscriptions, calls `SubscriptionRateApplyService.applyDue`, gauges `opencrm.subscription.ramp.overdue.seconds`. This property is **not** the pricing ramp flag and is **not** GA authorization.
3. **Apply transaction** — `SubscriptionRateApplyService.applyLocked` only advances **ACTIVE**, non-deleted subscriptions; due unapplied periods ordered by start date / accepted line UUID / sequence; publishes `RampApplied` v1 via outbox on `cpq.subscription.ramp.applied` with `effectiveDate = period.startDate`; marks applied; recomputes MRR/ARR from covering periods; queues ERP when amounts change (`read`).
4. **Schema** — CAM Flyway `V1_6__subscription_rate_schedule.sql` hosts `subscription_rate_periods` + commercial revision / ERP claim columns and legacy aggregate backfill that never emits retroactive `RampApplied` (`read`). CAM tip on this `main` reaches through `V1_15__document_apply_replay_evidence.sql`. **This brief claims no new migration and opens no Flyway.** Draft CAM PRs reserve **V1_16–V1_21**.
5. **Read API** — `GET /api/v1/subscriptions/{id}/ramp-schedule` and by-number / internal variants (`RampScheduleController`, `read`).
6. **Accept consumer** — `QuoteAcceptedEventConsumer` → `AcceptedQuoteSubscriptionService` with deal-type / target-subscription branching covered by unit and integration tests (`read` inventory). Live validation exercised target-linked expansion; not every mutation race.
7. **Acceptance mapping** — Validation note: 12 groups / 83 HTTP / two browser checks; process-only flag-off; **scheduled RampApplied not certified** in that live run; unit/PostgreSQL/Kafka replay evidence retained separately (`read`).
8. **ACV version** — committed `legacy` (`read`). Startup guard refuses ramp-on + legacy (`RampPricingStartup`).

### GitNexus (this checkout)

Docs-only change: **no production symbol is edited** by this brief. Full `gitnexus analyze` may be unavailable or stale in this pass. Treat any later code edit of contract-asset apply / consumer / scheduler symbols as requiring a fresh `impact` before edit. `UNKNOWN` / `partial` / `truncated` graph results are unresolved, not low risk.

## Owner-decision table (remaining delivery / Done / rollout forks)

Every row is `Status: Proposed` under id **OQ-339-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time). Do **not** adopt open OQs from [#876](https://github.com/drgaciw/open-crm-cpq/pull/876), [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), or [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-339-1 | Formal Linear Done after In Progress | Keep **In Progress** until owner words+time mark Done under DoD v0.2; do not auto-Done from merged PRs or the validation note alone | Mark Done from unit/PostgreSQL map alone; bot/GitHub auto-Done; leave forever In Progress without owner act | Yes — Done transition |
| OQ-339-2 | Pricing ramp flag / GA enablement | **NO-GO.** Keep `opencrm.pricing.ramp.enabled=false` (A6 / Ramp hold). Enablement lives on DRG-614 | Flip flag true in shared config; overload ACV version with the ramp flag | Yes — AC-08 / ADR-023 D-06 |
| OQ-339-3 | `opencrm.subscription-ramp.enabled` vs pricing flag | Treat subscription-ramp job property as **ops scheduling only**, not GA; do not claim enablement from `matchIfMissing=true` | Flip pricing ramp on because the job defaults on; disable job in committed yml from this brief | Yes — GA / hold confusion |
| OQ-339-4 | Scheduled `RampApplied` certification | Keep scheduled apply **uncertified** by the 2026-10-10 live run; require an explicit owner-accepted certification gate (unit/PostgreSQL AC-12 retained; live scheduler proof under DRG-614 or a named follow-up) before GA claims | Treat unit/fake-clock AC-12 as production scheduler proof; claim live run certified RampApplied | Soft for story Done; hard for GA |
| OQ-339-5 | Rollout ownership vs story Acceptance | Keep Entra / non-prod enablement / rollback fault / broad UAT / reconciliation / real ERP+CRM delivery on **DRG-614**; not R3 AC | Re-attach rollout gates to DRG-339 before Done; claim GA from local acceptance alone | Yes — GA / flag |
| OQ-339-6 | Synthetic JWT / placeholder ERP evidence | Accept local JWT + ERP placeholder as story evidence only; require real Entra and external ERP under DRG-614 before enablement claims | Treat synthetic identity / placeholder ERP as production delivery proof; block Done until Entra | Soft for Done; hard for enablement |
| OQ-339-7 | Coupling to #803 / DRG-534 without adopting | Cite [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) + DRG-534 hold; **do not** adopt OQ2–OQ7; **do not** implement amendment elapsed-lock from this story | Treat #803 Recommended rows as Adopted; start DRG-534 product from R3 Done | Yes — amendment path / A10 |
| OQ-339-8 | Coupling to #876 / #874 without adopting | Cite [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874); **do not** adopt **OQ-336-*** / **OQ-635-***; R3 Done does not close R1 or extend-term residuals | Bundle Done for 339+336+635; treat R1 Done as apply Done | Soft for 339 Done; hard for sibling claims |
| OQ-339-9 | Further schema / Flyway | **No** new migration from this Decision brief. Historical `V1_6` (and later CAM tips through `V1_15`) stay as-is. Claim **no** CAM `V1_16`–`V1_21` | Alter rate-period columns; add CAM tables under V1_16–V1_21; invent new tip as reserved here | Yes — any new migration |
| OQ-339-10 | Implementation brief / further product edits | **NO-GO** on further Java/TS product work for DRG-339 until OQ-339-* blocking rows close (or owner defers) | Authorize contract-asset/quote follow-ups when this docs file merges; reopen apply path without owner Done | Yes — any code PR |
| OQ-339-11 | Deal-type mutation / AC-13–14 completeness | Treat landed consumer branches + local expansion evidence as story scope for Decision scoring; disclose unexercised renewal/amendment/race paths on any Done claim; do not reopen product to widen AC here | Block Done until every AC-13/14 path has live HTTP proof; expand mutation product now | Soft for Done; hard if claiming full live AC-13/14 |
| OQ-339-12 | Cancellation event contract | Keep **out of R3** (A11); allowed-state guard stays; no new registry/topic from this brief | Publish `SubscriptionCancelled` in this story; couple Done to a cancel event PR | Soft for Done; hard if claiming cancel event |
| OQ-339-13 | ACV `term-years` in shared config | Keep committed `legacy`; any shared-config `term-years` is a separate owner/DRG-614 act | Set `term-years` in committed yml with this brief; couple to ramp-on | Yes — shared-config / D-03 |
| OQ-339-14 | DRG-336 In Review as blocker | Disclose DRG-336 still **In Review**; do not use this brief to close R1 Done or flip the flag; sequencing of 336 Done vs 339 Done is an owner call | Claim 339 Done forces 336 Done; enable flag while 336 open | Yes — rollout / GA |
| OQ-339-15 | Downstream CRM / external ERP delivery | Keep out of R3 Acceptance; disclose on rollout (DRG-614) | Block 339 Done until CRM/external ERP proven; claim delivery from outbox publish alone | Soft for Done; hard for GA |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-339-1 — Formal Done after In Progress

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Progress until owner words+time under DoD v0.2 (Recommended, Proposed)** | Matches Linear comments: code merged ≠ Done; no bot sets Done alone. |
| **B. Done from merged #589 / validation note alone** | Evidence says rollout gates remain; note does not change status. |
| **C. Auto-Done via GitHub / bot** | Contradicts DoD v0.2 owner rule. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-2 — Pricing ramp flag / GA (Ramp hold)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep flag false; no GA from this story (Recommended, Proposed)** | ADR-023 D-06 / A6. **Product / flag NO-GO.** |
| **B. Flip `opencrm.pricing.ramp.enabled=true` in committed config** | Requires separate owner un-hold (DRG-614 path); not this brief. |
| **C. Couple ACV `term-years` to the ramp flag** | Rejected by ADR-023 D-03. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-3 — Subscription-ramp job property vs pricing flag

| Option | What the owner would be choosing |
|---|---|
| **A. Job property = ops scheduling only; not GA (Recommended, Proposed)** | `matchIfMissing=true` must not be read as pricing enablement. |
| **B. Treat default-on job as GA of ramp** | Conflates flags; violates A6. **NO-GO.** |
| **C. Change committed default of `opencrm.subscription-ramp.enabled` from this brief** | Product/config change; blocked here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-4 — Scheduled `RampApplied` certification

| Option | What the owner would be choosing |
|---|---|
| **A. Live run did not certify scheduler; keep uncertified for GA (Recommended, Proposed)** | Matches validation note disclaimer; unit/PostgreSQL AC-12 retained as story evidence. |
| **B. Promote unit/fake-clock AC-12 to production scheduler proof** | Overclaims ops catch-up. **NO-GO** for GA. |
| **C. Block story Done until a live scheduler certification run** | Optional stronger gate; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-5 — Rollout ownership (DRG-614)

| Option | What the owner would be choosing |
|---|---|
| **A. Rollout stays on DRG-614; not R3 AC (Recommended, Proposed)** | Matches Linear related link and validation remaining gates. |
| **B. Re-attach enablement gates to DRG-339 before Done** | Widens story beyond Acceptance. |
| **C. Claim GA from eight-service local acceptance** | Evidence used process-only flag-off and placeholders; not GA. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-6 — Synthetic JWT / placeholder ERP

| Option | What the owner would be choosing |
|---|---|
| **A. Story evidence only; Entra + external ERP under DRG-614 (Recommended, Proposed)** | Matches validation note. |
| **B. Treat synthetic JWT / placeholder ERP as production delivery** | Overclaims. **NO-GO** for enablement. |
| **C. Block Done until Entra** | Optional; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-7 — Coupling to #803 / DRG-534 without adopting OQ2–OQ7

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803 + DRG-534 hold; wait; no amendment product here (Recommended, Proposed)** | Preserves A10. Closing this brief does **not** close OQ2–OQ7. |
| **B. Treat #803 Recommended rows as Adopted** | Out of scope; would adopt open OQs from another draft. **NO-GO.** |
| **C. Implement elapsed-period lock inside DRG-339** | Wrong story; contradicts R3 scope. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-8 — Coupling to #876 / #874 without adopting sibling OQs

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #876/#874; score separately; do not adopt (Recommended, Proposed)** | R3 Done does not close R1 or extend-term residuals. |
| **B. One PASS/Done closes 339+336+635** | Couples unrelated residual forks. |
| **C. Reopen apply path to absorb R1/extend authoring** | Product reopen; blocked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-9 — Further schema / Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration from this brief; no CAM V1_16–V1_21 (Recommended, Proposed)** | Historical `V1_6` / later tips are facts, not a license to open more. |
| **B. Alter / extend `subscription_rate_periods` now** | Product/schema work; blocked. |
| **C. Reserve or ship CAM V1_16–V1_21 here** | Band reserved by other drafts; **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-10 — Implementation brief / further product edits

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on further product PRs until forks + owner Done path (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Authorize contract-asset/quote follow-ups when this file merges** | Violates hard product NO-GO. |
| **C. Reopen R3 for allowlist-only churn without owner Done** | Still a product PR; needs OQ-339-1 first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-11 — Deal-type mutation / AC-13–14 completeness

| Option | What the owner would be choosing |
|---|---|
| **A. Landed branches + expansion evidence OK for scoring; disclose unexercised paths (Recommended, Proposed)** | Matches validation note limits. |
| **B. Block Done until every AC-13/14 live HTTP path** | Stronger gate; needs owner words. |
| **C. Expand mutation product now for missing races** | Product work; **NO-GO** from this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-12 — Cancellation event contract

| Option | What the owner would be choosing |
|---|---|
| **A. Keep cancel event outside R3 (Recommended, Proposed)** | Matches A11 / R3 plan decision 12. |
| **B. Publish cancel event in this story** | Scope expansion; blocked. |
| **C. Couple Done to a cancel-event follow-up PR** | Optional sequencing; owner words. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-13 — ACV `term-years` in shared config

| Option | What the owner would be choosing |
|---|---|
| **A. Keep committed `legacy`; enablement is a separate act (Recommended, Proposed)** | Matches ADR-023 D-03; finance path later, not this brief. |
| **B. Commit `term-years` now** | Shared-config product change; blocked. |
| **C. Require `term-years` before marking 339 Done** | Optional sequencing; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-14 — DRG-336 In Review as blocker

| Option | What the owner would be choosing |
|---|---|
| **A. Disclose 336 In Review; no GA / flag flip here (Recommended, Proposed)** | Historical blocker remains open for Decision; this brief does not close it. |
| **B. Claim 339 Done forces 336 Done** | Couples separate Decision scoring. |
| **C. Enable ramp flag while 336 open** | Violates A6 / OQ-339-2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-339-15 — Downstream CRM / external ERP delivery

| Option | What the owner would be choosing |
|---|---|
| **A. Keep out of R3; disclose on rollout (Recommended, Proposed)** | Matches DRG-614 and validation gaps. |
| **B. Block 339 Done until CRM/external ERP delivery** | Optional; owner words required. |
| **C. Claim delivery proven by outbox publish / local placeholder** | Overclaims consumers. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-339-1` .. `OQ-339-15`).
2. CPQ Decision must score the OQ-339-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes further Java/TS product work, Flyway, gateway routes, or flag flips for DRG-339.
4. Even after forks close: A1–A11 hold; **Ramp hold** (`opencrm.pricing.ramp.enabled=false`) stays; committed ACV stays `legacy` until a separate enablement act; no Jev enablement; #803 OQ2–OQ7, #876 OQ-336-*, and #874 OQ-635-* rows stay Proposed unless those Decision briefs close them; DRG-336 In Review and DRG-614 Backlog remain residual rollout discloses.
5. Linear status for DRG-339 stays **In Progress** until the owner and Decision say otherwise. This brief does **not** mark Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or further product code for DRG-339.
- Does not mark any OQ-339-* fork Adopted.
- Does not adopt or close [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) OQ-336-*.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults / OQ8.
- Does not adopt or close [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) OQ-635-* rows.
- Does not flip `opencrm.pricing.ramp.enabled`, `opencrm.subscription-ramp.enabled`, or `opencrm.pricing.metrics.acv-version`.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any new contract-asset/quote/pricing migration.
- Does not close DRG-614, DRG-336, DRG-534, DRG-341, DRG-340, DRG-635, or DRG-633/634.
- Does not reopen ADR-023 Adopted cells.
- Does not change Linear status (remains **In Progress**).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-339-* (especially OQ-339-1, OQ-339-2, OQ-339-3, OQ-339-4, OQ-339-7, OQ-339-9, OQ-339-10). Until then, further product work is **NO-GO**.
2. **Ramp hold:** pricing flag stays off; no GA from this wave.
3. **Scheduled RampApplied:** live certification still open for enablement claims.
4. **ACV shared-config:** stays `legacy` until a separate owner/DRG-614 act.
5. **#803 OQ2–OQ7 / DRG-534:** still Proposed / In Progress; no amendment product from this story.
6. **#876 / #874 sibling residuals:** still Proposed; not adopted.
7. **DRG-336 In Review / DRG-614 Backlog:** R1 Done and rollout readiness incomplete for enablement claims.
8. **GitNexus index:** refresh before any future code edit; needs a live `impact` / `detect_changes` on the editing checkout.

## Sources read this pass

- Linear: DRG-339 (In Progress; comments including 2026-10-16 DoD v0.2 / Delivery hygiene), relations to DRG-336 / DRG-338 / DRG-534 / DRG-614 / DRG-340.
- GitHub: draft Decision briefs #876, #803, #874 (cite-only); merged R3 #589 and supporting wave PRs named in the Linear description.
- ADR-023; REQ-price-ramping; forge/41; `docs/plans/ramp-next-20261010-r3-plan.md`; `docs/plans/ramp-next-20261010-validation.md`.
- Landed config/code: pricing/quote `application.yml` ramp flag; `SubscriptionRampJob`; `SubscriptionRateApplyService`; `V1_6__subscription_rate_schedule.sql`; `RampScheduleController`; `EventContractRegistry.RAMP_APPLIED_V1`.
- AGENT.md / `docs/ARCHITECTURE.md` holds orientation (Jev / dual-read / asset-write unchanged; ramp flag treated as ADR-023 D-06 hold).

## Unverified in this pass

- Runtime re-execution of eight-service acceptance, scheduler catch-up, or full backend unit stage on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus analyze / `detect_changes` graph coverage beyond docs-only claim.
- Whether the owner will mark Done from merged R3 PRs without a separate Decision PASS score for OQ-339-*.
