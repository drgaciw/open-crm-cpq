# DRG-571 — Maintenance pricing policy (percent of licence net, recalc, uplift, reinstatement): Decision brief (owner forks)

**Status:** Proposal only. Residual Decision brief for CPQ Decision scoring. This file does **not** accept [`specs/maintenance.md`](specs/maintenance.md), amend [cluster-b-onprem-to-saas-and-maintenance-decision-frame.md](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) beyond a one-line pointer, rewrite [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md), reopen Adopted Cluster B DRG-571 disposition cells, implement [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) linkage, invent a landed `PercentOfRelatedLine` schema, authorize an implementation brief, authorize product code, flip a flag, run a CAM Flyway migration in the reserved `V1_16`–`V1_21` band ([#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted by this brief. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `maintenance` / Cluster B. **Story:** [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571/maintenance-pricing-policy-percent-of-licence-net-recalculation-and) (RA-37). Linear read this pass: **In Review**. Notion: [RA-37 backlog](https://app.notion.com/p/3fbf7cb4e4df81a49d66e37d731304bd). Sanity-check write-up: [Pricing policy sanity check](https://app.notion.com/p/3fbf7cb4e4df816bba1fe1de4b180a60).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (still not Linear Done for DRG-571):**

| Artifact | Role |
|---|---|
| [`specs/maintenance.md`](specs/maintenance.md) | Proposal. `MaintenancePolicy` shape, AC-3..AC-9, and open questions for percent / recalc / uplift / reinstatement remain Status: Proposed with blank owner slots. |
| [cluster-b-onprem-to-saas-and-maintenance-decision-frame.md](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) | Historical Cluster B frame (merged [#652](https://github.com/drgaciw/open-crm-cpq/pull/652)). DRG-571 section lists six pricing forks. Status line cites 2026-10-17 delegation; proposal labels retained as rationale. **This brief cites Adopted DRG-571 cells; it does not reopen them.** |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) §B + Catalog and policy | Disposition cells say `Adopt baseline default` for the six DRG-571 forks; narrative chooses configured `LicenceNet` percentage and recalc against linked perpetual quantity with accepted precedence. That record **does not** invent 20% / 5%, **does not** land `PercentOfRelatedLine`, and **does not** mark Linear DRG-571 Done. |
| Fixtures [`specs/fixtures/maintenance/`](specs/fixtures/maintenance/) | Provisional renewal / licence-count / reinstatement JSON (`status: provisional`). Inputs are illustrative; not platform defaults. |
| [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) | Linkage story (`licenseModel` / `relatedPerpetualLineId`). **Cite only.** This brief does **not** implement linkage. |
| Accepted uplift / proration records | `adr-renewal-uplift-basis-and-precedence.md`, `adr-amendment-proration-formula.md`. Cited; not amended. Forward stub is not a DRG-571 fork. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** An implementation brief for DRG-571 (or product work that adds `PercentOfRelatedLine` / `MaintenancePolicy` persistence, retires `rule-003`, or prices reinstatement) is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time). Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force. CAM Flyway slots **`V1_16`–`V1_21`** are reserved by open Wave-2 CAM PRs [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812); this docs PR writes **no** migration in that band or any other.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Status: Proposed` | Option or default only. Not adopted by this brief. Slot stays `Owner decision: ___ (drg, time)`. |
| Adopted Cluster B cell | Disposition `Adopt baseline default` for that DRG-571 fork in the 2026-10-17 delegated record. **Cited, not reopened.** Prior frame baseline text is kept for Decision scoring context only. |
| Prior frame baseline | The Cluster B / `specs/maintenance.md` proposed default named for scoring. **Not** Adopted by this brief. |
| Cite-only | Named related story or PR is evidence/context; this brief must not implement or close it. |

## Why this Decision brief exists

**RA-37.** Maintenance is sold as a percentage of the related licence net (illustrative Scenario 2: 20% of a $2,500/core net → $500/core/year). PricingPolicy has no derived mode (`PercentOfRelatedLine` is a **proposed** name only — not a landed schema on `main`). When a true-up adds cores (Scenario 2: +16), the maintenance line must be recomputed from the licence line. At renewal (Scenario 4), maintenance carries its **own** uplift (illustrative 5% on $40,000 → $42,000) separate from a SaaS account uplift (illustrative 7%). Lapsed-maintenance reinstatement (back-maintenance for the gap plus an optional fee) is also unmodelled. [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) covers licence–maintenance **linkage**, not this pricing policy — **cite only; do not implement linkage here.**

Cluster B ([#652](https://github.com/drgaciw/open-crm-cpq/pull/652)) and the 2026-10-17 delegated disposition index recorded `Adopt baseline default` for the six DRG-571 forks as design disposition. Linear comment on DRG-571 (2026-10-17): a Proposed frame merge is not Done while the policy decision or code is open. This residual brief re-lists the owner-close surface as **Proposed `OQ-571-*`** with blank `Owner decision: ___ (drg, time)` slots for CPQ Decision — without reopening the Adopted cells, without inventing a landed percent-of-related-line schema, and without starting DRG-556 product work.

**This brief does not pick a commercial maintenance price.** It inventories Adopted disposition cells versus residual owner forks. Prior frame baselines are cited so Decision has a concrete row to accept, replace, or defer — not so this PR adopts them. Missing configured percent stays a refused/unresolved **request input** (delegated Catalog and policy contract); 20% and 5% are never silently filled in.

### Related tickets (Linear read 2026-11-06)

| Ticket / PR | Linear / GitHub status | Role for this Decision brief |
|---|---|---|
| [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571) | In Review | This story. Owner forks `OQ-571-*` below. |
| [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) | In Review | Linkage. **Cite only** — recalc quantity and `relatedPerpetualLineId` depend on it; this brief does not implement it. |
| [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) | related | One-time / retroactive charge line — required only if reinstatement is turned on (`OQ-571-5`). |
| [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539) | related | Price-book read — required only if `OQ-571-1` selects list basis. |
| [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) / [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) | In Review | True-up measurement / meter intent. This policy prices the **maintenance consequence** of a quantity delta; it does not choose the meter. |
| [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567) | related | Precedence when clause / line / agreement disagree — `OQ-571-4` cites Accepted uplift D-04 shape without amending it. |
| PR [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) | merged docs frame | Cluster B frame. Does not mark DRG-571 Done. |
| PRs [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | open (Wave-2 CAM) | Reserve CAM Flyway **`V1_16`–`V1_21`**. This brief writes **no** migration. |

## Current code / docs facts (`read`)

Facts verified against `main` SHA above. **No landed `PercentOfRelatedLine` / linked-line `MaintenancePolicy` path exists.**

| Fact | Evidence |
|---|---|
| No `PercentOfRelatedLine`, `relatedPerpetualLineId`, `licenseModel`, `pctOfLicenceNet`, or `MaintenancePolicy` type in backend Java/SQL on `main` | `rg` this pass over `backend/**/*.{java,sql}` (no product matches; names appear in docs/proposals only) |
| Legacy fixed 22%-of-**list** seed is distinct from this proposed linked-line net policy | `pricing-service` `V2__seed_default_rules.sql` ACTIVE `rule-003`; `rules/perpetual-maintenance.drl` (`maintenancePct = 0.22` of list for `ONPREM_PERPETUAL`). Cluster B requires reconcile/retire before a second policy can price the same charge; applied V2 history is not rewritten |
| Accepted renewal uplift D-01 / D-04; absent uplift on `UpliftPct` is unresolved; continuation still substitutes zero when null | `adr-renewal-uplift-basis-and-precedence.md`; `QuoteContinuationPolicy.java` — this brief does not copy that zero onto maintenance |
| Accepted proration day-stub owns partial billing periods; Scenario 2 `92/365` is illustration only | `adr-amendment-proration-formula.md` D-01; Cluster B "forward stub is not a fork" |
| `ONPREM_SUPPORT` can be a continuation source; `ONPREM_PERPETUAL` is outside `AcceptedRampSnapshots` | Cluster B / quote continuation facts on `main` |
| CAM migrations on `main` reach `V1_15`; open Wave-2 PRs claim `V1_16`–`V1_21` | CAM `db/migration/` listing; PR bodies #805–#812 |
| Scenario 2 / 4 figures are illustrative, not customer data | `cpq-pricing-policy-sanity-check.md` |

GitNexus MCP `list_repos` returned **zero** indexed repositories this pass; `.gitnexus/run.cjs` is absent. Code claims above are text/`rg` evidence only — treat graph impact / `detect_changes` as unresolved (`UNKNOWN` / unavailable), **not** as a clean blast-radius check.

## Implementation brief gate (explicit) — product / schema / flag **NO-GO**

1. This Decision brief closes **nothing** by itself. Residual forks are `Status: Proposed`.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-571 / `PercentOfRelatedLine` / linked `MaintenancePolicy`.
4. **NO-GO:** no product code; no Flyway/schema (including any CAM slot, and especially reserved **`V1_16`–`V1_21`**); no feature flag; no dual-read / Jev / ramp / S4 enablement; no asset-write hold bypass; no DRG-556 linkage implementation from this PR.
5. Choosing list basis does not start DRG-539. Turning reinstatement on does not start DRG-572. Closing these OQs does not start S3 / S4 / S6.
6. Legacy `rule-003` / `perpetual-maintenance.drl` must be reconciled or retired under a **separate** owner-reviewed migration story before both policies can price the same charge — not in this docs PR.

---

## Adopted vs residual inventory

### Adopted Cluster B cells (cited, not reopened)

From `delegated-renewals-amendments-decisions-2026-10-17.md` §B (`read`): each DRG-571 fork disposition is `Adopt baseline default`. Narrative Catalog and policy contract: configured `LicenceNet` percentage; recalc against linked perpetual quantity; accepted precedence; missing percent is refused input (never invent 20%); seeded 22%-of-list rule remains legacy until an explicitly versioned replacement is validated.

| Cluster B fork | Adopted disposition (cite) | Prior frame baseline (for scoring context only) |
|---|---|---|
| Basis | Adopt baseline default | `LicenceNet`; percent required; 20% not filled in |
| Recalc when licence quantity or net changes | Adopt baseline default | `recalcOnLicenceChange = Yes` |
| Uplift field | Adopt baseline default | Separate maintenance `upliftPct`; unset → unresolved (do not apply continuation zero) |
| Precedence for maintenance fields | Adopt baseline default | Clause → line → agreement → account → rules default (D-04 shape; does not amend D-01 basis enum) |
| Reinstatement | Adopt baseline default | Off; `backMaintenance` / `feePct` / `maxLapseMonths` unset |
| Quantity used when recalc is Yes | Adopt baseline default | Equals linked perpetual quantity (depends on DRG-556 equal-quantity option — cite-only) |

**This brief does not reopen those disposition cells.** Linear DRG-571 stays **In Review** until CPQ Decision / owner words+time close the residual `OQ-571-*` surface below (and any deferred named fork).

### Residual owner forks — Proposed `OQ-571-*`

Every row is `Status: Proposed`. Prior frame baseline is the Cluster B proposed default for scoring. Alternatives sit beside it. **No Adopted cells created by this brief. Product/schema/flag NO-GO until closed.**

| ID | Fork | Prior frame baseline (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-571-1 | Price basis | `LicenceNet`; configured percent required; 20% not filled in | Percent of licence list; flat amount | Yes — unit maintenance price source |
| OQ-571-2 | Recalc on licence quantity/net change | `recalcOnLicenceChange = Yes` | No (keep stored annual) | Yes — true-up maintenance recompute |
| OQ-571-3 | Maintenance uplift field | Own `upliftPct`; unset unresolved; SaaS lines keep Accepted renewal uplift | Reuse Accepted renewal uplift percent | Yes — Scenario 4 split uplift |
| OQ-571-4 | Precedence stack for maintenance fields | Clause → line → agreement → account → rules default | Maintenance-only stack | Yes — which rung supplies percent/uplift |
| OQ-571-5 | Lapsed reinstatement | Off; three knobs unset | Include reinstatement (`backMaintenance`, `feePct`, `maxLapseMonths` + DRG-572 charge type) | Yes — lapse path; couples DRG-572 if on |
| OQ-571-6 | Quantity when recalc is Yes | Equals linked perpetual quantity | Separately entered maintenance quantity | Yes — true-up maintenance qty; cite-only DRG-556 |
| OQ-571-7 | Legacy `rule-003` / 22%-of-list vs linked-line policy | Reconcile or retire legacy before dual pricing (Cluster B gate; residual) | Leave both active (refused); rewrite applied V2 (refused) | Yes — cannot ship linked-line policy beside unreconciled seed |

---

## Residual owner forks (detail)

### OQ-571-1 — Price basis (Cluster B DRG-571 Basis)

| Option | What the owner would be choosing |
|---|---|
| **A. `LicenceNet`; percent required; 20% not filled in (prior frame baseline, Proposed)** | Annual maintenance derives from related licence **net** × configured percent × quantity. Missing percent → unresolved/refused input. |
| **B. Percent of licence list** | Needs price-book read (DRG-539). Missing list → fail the line; no silent fallback. |
| **C. Flat amount** | No percent-of-related derivation. |

**Adopted Cluster B cell:** Basis `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ-571-2 — Recalc when the licence quantity or net changes

| Option | What the owner would be choosing |
|---|---|
| **A. `recalcOnLicenceChange = Yes` (prior frame baseline, Proposed)** | A true-up that changes licence quantity/net recomputes maintenance in the same change. |
| **B. No** | Stored annual maintenance amount is kept; decision records that policy forbade recompute. |

**Adopted Cluster B cell:** Recalc `Adopt baseline default` — **cited, not reopened.**  
Forward partial-period money uses the Accepted proration day-stub; this OQ does **not** adopt `92/365` as a second formula.  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ-571-3 — Maintenance uplift field (own percent vs SaaS uplift)

| Option | What the owner would be choosing |
|---|---|
| **A. Separate maintenance `upliftPct`; unset → unresolved (prior frame baseline, Proposed)** | Scenario 4 can run 5% on maintenance beside 7% SaaS account uplift. Does **not** amend Accepted uplift D-01. Does **not** apply continuation's null→zero substitution to this field. |
| **B. Reuse Accepted renewal uplift percent** | One uplift percent for maintenance and SaaS in the same change. |

**Adopted Cluster B cell:** Uplift field `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ-571-4 — Precedence for maintenance fields

| Option | What the owner would be choosing |
|---|---|
| **A. Clause → line → agreement → account → rules default (prior frame baseline, Proposed)** | Same shape as Accepted uplift D-04 for field resolution; does **not** claim D-04 already covers maintenance; does not amend D-04 or D-01's renewal-unit basis enum. |
| **B. Maintenance-only stack** | Separate order owned only by maintenance policy. |

**Adopted Cluster B cell:** Precedence `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ-571-5 — Lapsed-maintenance reinstatement

| Option | What the owner would be choosing |
|---|---|
| **A. Off; knobs unset (prior frame baseline, Proposed)** | No auto-reinstatement. `backMaintenance`, `feePct`, `maxLapseMonths` stay unset. |
| **B. Include lapse reinstatement** | Requires the three knobs **and** DRG-572 for one-time back-maintenance / fee lines. Choosing B does **not** start DRG-572. |

**Adopted Cluster B cell:** Reinstatement `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ-571-6 — Quantity used when recalc is Yes (cite-only DRG-556)

| Option | What the owner would be choosing |
|---|---|
| **A. Equals linked perpetual quantity (prior frame baseline, Proposed)** | Depends on DRG-556 equal-quantity option / `relatedPerpetualLineId`. **Cite only — this brief does not implement linkage.** |
| **B. Separately entered maintenance quantity** | Allows subset-of-cores maintenance without forcing equal quantity. |

**Adopted Cluster B cell:** Quantity `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ-571-7 — Legacy 22%-of-list seed vs linked-line policy (residual gate)

| Option | What the owner would be choosing |
|---|---|
| **A. Reconcile or retire `rule-003` / `perpetual-maintenance.drl` before linked-line policy prices the same charge (Cluster B gate, Proposed)** | New owner-reviewed migration later; applied V2 history not rewritten; `ManagedSeedOptionalInputsIntegrationTest` / seed expectations updated in that story. |
| **B. Leave both active** | Refused — dual pricing of the same charge. |
| **C. Rewrite applied V2 history** | Refused — migrations immutable. |

**This is residual to the Adopted six cells**, not a reopening of Basis. Closing OQ-571-7 does not authorize a migration in this PR and does not consume CAM `V1_16`–`V1_21`.  
**Prior frame / Cluster B gate (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

---

## Relation map: DRG-556, true-up, one-time charges (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| DRG-556 linkage (`licenseModel`, `relatedPerpetualLineId`, quantity match) | Prerequisite for OQ-571-2 / OQ-571-6 | Implement linkage; land those columns; mark DRG-556 Done |
| DRG-569 / DRG-557 true-up / meters | Supplies quantity delta that OQ-571-2 may reprice | Choose aggregation / meter / freeze policy |
| DRG-572 one-time charges | Charge type for reinstatement amounts if OQ-571-5 = B | Start DRG-572; invent charge schema |
| DRG-539 price-book read | List basis if OQ-571-1 = B | Start DRG-539 |
| Accepted uplift / proration ADRs | Precedence shape and forward stub | Amend D-01 / D-04 / day-stub |
| CAM `V1_16`–`V1_21` (#805–#812) | Reserved migration band | Write or renumber any CAM Flyway file |
| `#652` Cluster B frame | Historical options + Adopted disposition path | Treat merge as Linear Done for DRG-571 |

---

## Worked Scenario 2 / 4 check (illustrative only)

If every prior-frame baseline and OQ-571-7-A were later Accepted with words+time, on the example numbers: 20% of $2,500 net → $500/core/year; true-up +16 cores with recalc Yes recomputes maintenance for 80 cores; forward stub uses Accepted day-stub (not a second `92/365` method claimed here); renewal uplift 5% on $40,000 → $42,000 while SaaS lines keep their own uplift; reinstatement stays off unless OQ-571-5 is replaced. These are fixtures for Decision, not an adoption claim, and not a landed `PercentOfRelatedLine` schema.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-571 (RA-37) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-571-1 … OQ-571-7 |

Example shape once scored (illustrative only, **not** a claim): `DRG-571 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-571 / `PercentOfRelatedLine` / linked `MaintenancePolicy`.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not amend the 2026-10-17 delegated disposition index or rewrite Cluster B beyond a pointer.
- Does not accept `specs/maintenance.md` or flip provisional fixtures to accepted.
- Does not implement DRG-556 linkage; does not start DRG-572, DRG-539, DRG-569, DRG-557, S3, S4, or S6.
- Does not add CAM/pricing Flyway, including reserved **`V1_16`–`V1_21`** (#805–#812).
- Does not retire or edit `rule-003` / `perpetual-maintenance.drl`.
- Does not mark Linear DRG-571 Done.
- Does not merge or enable auto-merge on any PR.
- Does not flip dual-read, Jev, asset-write, ramp, or S4 flags.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close **Proposed `OQ-571-1` … `OQ-571-7`**. Until then, implementation brief is blocked (**NO-GO** for product Java/TS on maintenance percent-of-licence pricing).
2. **DRG-556 In Review:** linkage not Closed for product — cite-only dependency for OQ-571-6 / recalc quantity.
3. **No `PercentOfRelatedLine` / `MaintenancePolicy` types** on `main`; do not invent schema in this brief.
4. **Legacy `rule-003` 22%-of-list** still seeded; OQ-571-7 must close before dual pricing.
5. **DRG-572 / DRG-539:** disclose if Decision replaces OQ-571-5-A or OQ-571-1-A.
6. **CAM `V1_16`–`V1_21` reserved** by #805–#812 — any later maintenance schema uses a free slot **after** that band, never these numbers from this docs PR.
7. **AGENT.md holds:** dual-read, NgRx, Jev enablement beyond local, contract-asset FE, S4 — untouched.
8. **GitNexus:** index unavailable this pass (`UNKNOWN`) — not a clean change check.

## Sources read this pass

- Linear: DRG-571 (full description + comments + attachments), DRG-556 (cite-only).
- Frame: `docs/renewals-amendments/prereq/cluster-b-onprem-to-saas-and-maintenance-decision-frame.md` (DRG-571 section + owner table).
- Delegation: `docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md` §B + Catalog and policy.
- Spec / fixtures: `docs/renewals-amendments/prereq/specs/maintenance.md`; `specs/fixtures/maintenance/*.json`.
- Sanity check Scenarios 2 and 4: `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md`.
- Pattern: draft residual Decision briefs DRG-569 / DRG-570 (Wave-2).
- Code: `pricing-service` `V2__seed_default_rules.sql` `rule-003`; `rules/perpetual-maintenance.drl`; `rg` for `PercentOfRelatedLine` / `relatedPerpetualLineId` / `MaintenancePolicy` (none in product code); CAM migration directory through `V1_15`.
- GitHub: merged #652; open #805–#812 Flyway slot map.
