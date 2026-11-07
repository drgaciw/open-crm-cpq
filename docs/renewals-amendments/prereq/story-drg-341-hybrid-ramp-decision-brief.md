# DRG-341 — Hybrid migration ramp with dual-entitlement window: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **delivery / Done / rollout forks that remain after** Accepted ADR-023, the R4 hybrid pricing / catalog / lifecycle merges (#585–#595 and related R3 handoff), and the 2026-10-10 local eight-service acceptance evidence recorded in [`docs/plans/ramp-next-20261010-validation.md`](../../plans/ramp-next-20261010-validation.md). This file does **not** reopen Adopted ADR-023 cells, does **not** reopen the landed hybrid credit / window / mandatory Deal Desk contracts already exercised under the ramp flag off, does **not** adopt open **OQ-336-*** rows from the DRG-336 residual Decision brief draft [#876](https://github.com/drgaciw/open-crm-cpq/pull/876), does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** adopt open **OQ-559-*** rows from the DRG-559 restructure residual Decision brief draft [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) (Wave cite target `#559` → related Linear [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559); do **not** confuse with historical merged approvals PR [#559](https://github.com/drgaciw/open-crm-cpq/pull/559)), does **not** adopt open **OQ-635-*** rows from the DRG-635 residual Decision brief draft [#874](https://github.com/drgaciw/open-crm-cpq/pull/874), does **not** accept [`specs/conversions.md`](specs/conversions.md) or [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize further product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / Jev / dual-read / S4, write dual-entitlement product changes, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed` under id **OQ-341-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp` / R4 hybrid migration. **Story:** [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341/bc-09-07-hybrid-migration-ramp-with-dual-entitlement-window). Linear read this pass: **In Progress**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #876 / #803 / #850 / #874 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted ramp baseline:** per-line schedule, whole-month periods, D-06 ramp flag default **off**, D-03 ACV version switch separate from the ramp flag |
| [REQ-price-ramping.md](../../backlog/REQ-price-ramping.md) FR-26 | Hybrid migration ramp requirement; R4 story sizing |
| [forge/41-price-ramp-sprint.md](../../forge/41-price-ramp-sprint.md) § R4 | Credit once / cap / 12-month window / hybrid approval acceptance. Docs-only origin; not a later Done authorization |
| [docs/plans/ramp-next-20261010-validation.md](../../plans/ramp-next-20261010-validation.md) | Local eight-service acceptance evidence (hybrid TCV/ACV/ARR/exit ARR + first-period credit 3000). Cite only; **not** GA / Done |
| Wave-2 DRG-336 residual Decision brief [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) | Cite-only sibling residual forks (**OQ-336-***). This brief **does not score or close** those rows |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults). Restates stored-step path and **ramp hold**. This brief **does not score or close** those rows |
| Wave-2 DRG-559 residual Decision brief [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) | Cite-only restructure forks (**OQ-559-***). Landed hybrid pair is **not** a Restructure substitute. This brief **does not score or close** those rows |
| Wave-2 DRG-635 residual Decision brief [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) | Cite-only extend-term residual forks (**OQ-635-***). This brief **does not score or close** those rows |
| [`specs/conversions.md`](specs/conversions.md) | Proposal only. Phased hybrid credit vs big-bang conversion. Not accepted by this file |
| R4 merges #585 (pricing hybrid), #588 (catalog authority), #591 (quantity direction), #592 (quote acceptance), #595 (catalog CHECK + lifecycle validation) and related R3 handoff | Landed hybrid code + recorded acceptance evidence. **Not** owner Done; flag stays off |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336) / [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) | In Review / draft | Open **OQ-336-***. Cite only; do **not** adopt |
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | In Progress / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Amendment elapsed lock is **not** R4 hybrid Acceptance |
| [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) / [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) | In Review / draft | Open **OQ-559-***. Hybrid pair ≠ Restructure. Do **not** adopt |
| [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) | In Review / draft | Extend-term residual. Do **not** adopt **OQ-635-*** |
| Historical [#559](https://github.com/drgaciw/open-crm-cpq/pull/559) | Merged | Approval supersede-on-figure-change. Cite only as historical Deal Desk plumbing; **no** open OQs; not a Decision brief |
| [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338) | Done | Per-period governance + fingerprint; hybrid Deal Desk minimum builds on it. Not redefined here |
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) | In Progress | Schedule / step application on accept. Residual rollout blocker; not a reason to flip the ramp flag from this brief |
| [DRG-337](https://linear.app/drgamtd-workspace/issue/DRG-337) / [DRG-340](https://linear.app/drgamtd-workspace/issue/DRG-340) | Related | Usage tiers / prepaid credit draw-down — explicitly out of this story |
| [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) / [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) | Related | Conversion pricing / surrender — Cluster B; not hybrid-pair enablement |
| [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614) | Backlog | Owns non-prod enablement, Entra login, rollback fault injection, broad UAT/chaos. Not closed by this brief |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, dual-entitlement product write, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). `opencrm.pricing.metrics.acv-version` stays committed `legacy` until a separate owner enablement act. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief that reopens product work for DRG-341 (further Java/TS edits, migrations, dual-entitlement product changes, or enablement) is **blocked** until CPQ Decision scores the open **OQ-341-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-341-* row stays open. Closing #876 / #803 / #850 / #874 OQ rows is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` / `Adopted (landed R4)` | Already selected / already shipped under flag-off. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery / Decision-PASS / Done fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-341 residual)

ADR-023 Accepted the ramp model with the flag default off. R4 hybrid pricing (`HybridMigrationPolicy` / `MigrationCreditPolicy` / `MigrationWindowProperties`), catalog CHECK widening (`V1_8__allow_canonical_ramp_product_types.sql`), quote acceptance handoff, and local eight-service acceptance evidence landed in October 2026. Linear checkpoints record hybrid golden figures (TCV 43800 / ACV 14600 / ARR 18000 / exit ARR 14400 / first-period credit 3000) and mandatory Deal Desk review — while rollout gaps (Entra, CRM/ERP delivery, catalog→pricing replication, chaos, RampApplied publication) and the **Ramp hold** remain.

Without a residual Decision brief that separates **already Adopted / landed** hybrid contracts from **still-Proposed Done / enablement / dual-entitlement / restructure-coupling forks**, a follow-up could treat the 2026-10-10 validation note as automatic Done, flip `opencrm.pricing.ramp.enabled`, open dual-entitlement product writes or CAM Flyway `V1_16`–`V1_21`, treat the hybrid pair as the Adopted Restructure shape while [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) OQ-559-* stay open, or adopt #876 / #803 / #874 recommendations by implication.

Linear DRG-341 (`Linear read` 2026-11-06): **In Progress** (started 2026-10-10); blocked by DRG-338 / DRG-336; related to DRG-340 / DRG-337 / DRG-574 / DRG-573 / DRG-565 / DRG-559 among others; attachments ADR-023, REQ, Notion BC-09-07. **This brief does not change Linear status.**

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), forge/41 R4 acceptance (`read`), landed hybrid code (`verified by reading code`), and the orientation baselines in [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) / [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) (`read`). This Decision brief does **not** reopen these rows. Those drafts mark their OQ rows Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | Coordinated pair: one **DECLINING** recurring line (`ONPREM_SUPPORT`) + one **RISING** recurring line (`HYBRID_MIGRATION_RAMP`), each with its own explicit schedule | forge/41 R4; `HybridMigrationPolicy` |
| A2 | Migration credit = **30% of book value**, applied **once** to the declining line's **first period**, never to the rising SaaS line | `MigrationCreditPolicy`; forge/41 |
| A3 | Effective credit capped by requested 30%, declining-line gross TCV, and first-period gross; unused credit is auditable; period amount does not go negative; recurring rates / ARR / exit ARR are not the credit allocation basis | `MigrationCreditPolicy`; Linear owner-delegated checkpoint |
| A4 | Dual-entitlement window property `opencrm.pricing.ramp.migration-window-months` defaults **12**, validated **1–12**; declared window must equal contiguous positive-quantity entitlement overlap; longer overlap → `RAMP_SCHEDULE_INVALID` | `MigrationWindowProperties`; `HybridMigrationPolicy` |
| A5 | Hybrid pair always routes for approval at least **DEAL_DESK** with reason **`RAMP_HYBRID_WINDOW`**; configured rules cannot waive that minimum; margin `40.0` is not the hybrid approval floor | `PeriodGovernance`; ADR-023 D-06 orientation; forge/41 |
| A6 | **`opencrm.pricing.ramp.enabled` defaults false**; flag-off is 422 `RAMP_NOT_SUPPORTED` and never prices a schedule as flat; turning the flag on is outside Acceptance | ADR-023 D-06; **Ramp hold** |
| A7 | ACV version switch is **`opencrm.pricing.metrics.acv-version`**, not the ramp flag; committed default **`legacy`** | ADR-023 D-03 |
| A8 | Catalog CHECK widened additively for `ONPREM_SUPPORT` / `HYBRID_MIGRATION_RAMP` (`product` `V1_8`); legacy types preserved | #595 |
| A9 | Hybrid path is **not** Restructure: no `priorAgreementId`, no perpetual surrender, no substitute for DRG-559 A1–A5 | #850 orientation; `specs/conversions.md` proposal |
| A10 | Usage tiers (DRG-337) and prepaid credit draw-down (DRG-340) stay out of this story | Linear description; forge/41 |
| A11 | Docs / Decision briefs alone authorize **no** product code, flag flip, dual-entitlement product write, or deploy | AGENT.md / ADR-023 / #803 |

## What the tree does today (hybrid / dual-entitlement delivery)

`verified by reading code` at the base SHA.

1. **`HybridMigrationPolicy`** — parses typed `hybridMigration` metadata (`pairId`, `role`, `startMonth`, `months`, `bookValue` on declining only); validates authoritative product types/currency/schedules; requires exact two-member pairs with agreeing windows; requires declared window = contiguous entitlement overlap; applies first-period credit via `MigrationCreditPolicy` and writes `migrationCreditAudit` (`read` `HybridMigrationPolicy.java`).
2. **`MigrationCreditPolicy`** — `requested = 0.30 × book`; `effective = min(requested, grossLineTcv, firstGross)`; unused and adjusted period amount recorded (`read` `MigrationCreditPolicy.java`).
3. **`MigrationWindowProperties`** — `@Min(1) @Max(12)` on `opencrm.pricing.ramp.migration-window-months`, default 12; constructor of `HybridMigrationPolicy` also rejects outside 1–12 (`read`).
4. **`PeriodGovernance`** — built-in hybrid rule sets Deal Desk + `RAMP_HYBRID_WINDOW`; re-asserts Deal Desk minimum after configured rules; margin placeholder policy excluded (`read` `PeriodGovernance.java`).
5. **Ramp flag** — `RampPricingProperties.enabled` default `false`; committed `application.yml` on pricing-service and quote-service keep `opencrm.pricing.ramp.enabled: false` (`read`).
6. **ACV** — pricing `opencrm.pricing.metrics.acv-version: legacy` committed (`read`).
7. **Catalog** — product Flyway `V1_8__allow_canonical_ramp_product_types.sql` landed; quote tip includes later `V1_16__ramp_escalator_compounding.sql` (DRG-633 lane); CAM tip `V1_15__…`. Draft CAM PRs reserve **V1_16–V1_21**. **This brief claims no migration number and opens no further Flyway.**
8. **Acceptance evidence** — local eight-service hybrid run recorded in `ramp-next-20261010-validation.md`; process-only flag-off 422 verified there. Real Entra / CRM / ERP / chaos remain open (`read`).

### GitNexus (this checkout)

Docs-only change: **no production symbol is edited** by this brief. GitNexus MCP reported **no indexed repositories** this pass (`impact` unavailable on `HybridMigrationPolicy`). Treat blast radius as **UNKNOWN** — not a clean check. A later implementation must run GitNexus `impact` before touching `HybridMigrationPolicy`, `MigrationCreditPolicy`, `PeriodGovernance`, catalog CHECK migrations, or any dual-entitlement product write. `UNKNOWN` / `partial` / `truncated` graph results are unresolved, not low risk.

## Owner-decision table (remaining delivery / Done / enablement forks)

Every row is `Status: Proposed` under id **OQ-341-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time). Do **not** adopt open OQs from [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) / [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) / [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-341-1 | Formal Done after In Progress | Keep Linear **In Progress** until owner words+time after Decision scores blocking OQ-341-*; do not treat #595 / validation note as automatic Done | Mark Done from local acceptance alone; auto-Done via GitHub / bot | Yes — Done transition |
| OQ-341-2 | Ramp flag / GA (Ramp hold) | **NO-GO.** Keep `opencrm.pricing.ramp.enabled=false` (A6). No committed flip from this story | Flip flag true in shared config; couple ACV `term-years` to the ramp flag | Yes — ADR-023 D-06 |
| OQ-341-3 | Dual-entitlement product / fulfilment writes | **NO-GO.** No new entitlement-overlap product writes, fulfilment REVOKE/PROVISION scheduling, or key-server calls from this brief; window length stays A4 | Ship dual-entitlement fulfilment from this docs PR; widen window beyond 1–12 in product | Yes — product / hold |
| OQ-341-4 | Coupling to #850 / DRG-559 without adopting OQ-559-* | Cite [#850](https://github.com/drgaciw/open-crm-cpq/pull/850); hybrid pair stays A9 path; **do not** treat hybrid as Restructure; **do not** adopt OQ-559-* | Treat #850 Recommended rows as Adopted; allow hybrid pair under Restructure intent | Yes — commercial shape |
| OQ-341-5 | Coupling to #876 without adopting OQ-336-* | Cite [#876](https://github.com/drgaciw/open-crm-cpq/pull/876); score R1 Done separately; do not adopt OQ-336-* | One PASS/Done closes 336+341; reopen R1 waterfall for hybrid | Soft for 341 PASS; hard for sibling Done |
| OQ-341-6 | Coupling to #803 without adopting OQ2–OQ7 | Cite [#803](https://github.com/drgaciw/open-crm-cpq/pull/803); no amendment product / percent regeneration here; do not adopt OQ2–OQ7 | Treat #803 Recommended rows as Adopted; implement elapsed-period lock inside DRG-341 | Yes — amendment path |
| OQ-341-7 | Coupling to #874 without adopting OQ-635-* | Cite [#874](https://github.com/drgaciw/open-crm-cpq/pull/874); extend-term residual scored separately; do not adopt OQ-635-* | Bundle Done for 341+635; reopen hybrid schedules on extend without 635 Decision | Soft for 341 PASS |
| OQ-341-8 | Further schema / Flyway | **No** new migration from this Decision brief. Claim **no** CAM `V1_16`–`V1_21`. Landed product `V1_8` / quote `V1_16` stay historical facts only | Alter hybrid metadata schema now; reserve or ship CAM V1_16–V1_21 here | Yes — any new migration |
| OQ-341-9 | Implementation brief / further product edits | **NO-GO** on further Java/TS product work until OQ-341-* blocking rows close (or owner defers) **and** owner Done path exists | Authorize hybrid/quote/catalog follow-ups when this docs file merges | Yes — any code PR |
| OQ-341-10 | Rollout ownership (DRG-614) | Rollout stays on DRG-614; not R4 Acceptance. Local validation ≠ GA | Re-attach Entra/CRM/ERP/chaos gates as 341 Acceptance; claim GA from validation note | Yes — enablement |
| OQ-341-11 | Synthetic / process-only evidence vs Entra | Synthetic JWT + process-only flag-on = story evidence only; Entra under DRG-614 | Treat synthetic identity as production proof; block Done until Entra without owner words | Soft for Done; hard for GA |
| OQ-341-12 | DRG-339 / apply-path residual | Disclose DRG-339 **In Progress** on any later rollout claim; do not flip the flag while apply-path open | Claim GA when hybrid unit/acceptance tests pass; enable flag while 339 open | Yes — rollout / GA |
| OQ-341-13 | Conversion credit stack (DRG-573) vs hybrid credit | Keep A2/A3 as the phased hybrid credit; conversion stack / Scenario 3 trade-in stay with DRG-573 — cite only | Merge ConversionPolicy into hybrid path here; invent additional credit types in this brief | Soft until conversion AC; hard if product changes |
| OQ-341-14 | Historical approvals PR #559 | Cite only as Deal Desk supersede plumbing already merged; no open OQs to adopt; not a Decision authority for hybrid Done | Reopen supersede semantics from this brief; treat #559 as residual Decision | Soft |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-341-1 — Formal Done after In Progress

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Progress until owner words+time after Decision scores blocking forks (Recommended, Proposed)** | Matches current Linear status and DoD rule that no bot sets Done alone. |
| **B. Done from the 2026-10-10 validation note alone** | Note is local acceptance evidence, not owner Done. |
| **C. Auto-Done via GitHub / bot** | Contradicts DoD owner rule. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-2 — Ramp flag / GA (Ramp hold)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep flag false; no GA from this story (Recommended, Proposed)** | ADR-023 D-06 / A6. **Product / flag NO-GO.** |
| **B. Flip `opencrm.pricing.ramp.enabled=true` in committed config** | Requires separate owner un-hold (DRG-614 path); not this brief. |
| **C. Couple ACV `term-years` to the ramp flag** | Rejected by ADR-023 D-03. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-3 — Dual-entitlement product / fulfilment writes

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on dual-entitlement product writes from this brief (Recommended, Proposed)** | Window validation stays A4; fulfilment REVOKE/PROVISION timing stays Cluster F / DRG-558/574. |
| **B. Ship dual-entitlement fulfilment APIs in this story** | Product work; blocked. |
| **C. Widen window beyond 1–12 in committed config** | Changes A4; needs owner words + separate product PR after forks close. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-4 — Coupling to #850 / DRG-559 without adopting OQ-559-*

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #850; hybrid ≠ Restructure; do not adopt OQ-559-* (Recommended, Proposed)** | Preserves A9. Closing this brief does **not** close OQ-559-*. |
| **B. Treat #850 Recommended rows as Adopted** | Out of scope; would adopt open OQs from another draft. **NO-GO.** |
| **C. Allow hybrid pair under Restructure intent** | Contradicts #850 OQ-559-4 orientation. **NO-GO** from this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-5 — Coupling to #876 without adopting OQ-336-*

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #876; score separately; do not adopt (Recommended, Proposed)** | R4 Done does not close R1 residual forks. |
| **B. One PASS/Done closes 336+341** | Couples unrelated residual forks. |
| **C. Reopen R1 waterfall to absorb hybrid credit** | Product reopen; blocked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-6 — Coupling to #803 without adopting OQ2–OQ7

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803; wait; no amendment product here (Recommended, Proposed)** | Closing this brief does **not** close OQ2–OQ7. |
| **B. Treat #803 Recommended rows as Adopted** | Out of scope. **NO-GO.** |
| **C. Implement elapsed-period lock inside DRG-341** | Wrong story. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-7 — Coupling to #874 without adopting OQ-635-*

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #874; score separately; do not adopt (Recommended, Proposed)** | Extend-term residual stays on DRG-635. |
| **B. Bundle Done for 341+635** | Couples unrelated residuals. |
| **C. Reopen hybrid schedules on extend without 635 Decision** | Product/authoring reopen; blocked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-8 — Further schema / Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration from this brief; no CAM V1_16–V1_21 (Recommended, Proposed)** | Landed product `V1_8` / quote `V1_16` are facts, not a license to open more. |
| **B. Alter hybrid metadata / catalog CHECK now** | Product/schema work; blocked. |
| **C. Reserve or ship CAM V1_16–V1_21 here** | Band reserved by other drafts; **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-9 — Implementation brief / further product edits

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on further product PRs until forks + owner Done path (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Authorize hybrid/quote/catalog follow-ups when this file merges** | Violates hard product NO-GO. |
| **C. Reopen R4 for allowlist-only churn without owner Done** | Still a product PR; needs OQ-341-1 first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-10 — Rollout ownership (DRG-614)

| Option | What the owner would be choosing |
|---|---|
| **A. Rollout stays on DRG-614; not R4 AC (Recommended, Proposed)** | Matches validation report remaining gates and DoD item on flags/deploys. |
| **B. Re-attach enablement gates to DRG-341 before Done** | Widens story beyond Acceptance. |
| **C. Claim GA from the 2026-10-10 local acceptance** | Evidence used synthetic JWT and process-only flag-on; not GA. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-11 — Synthetic JWT vs Entra

| Option | What the owner would be choosing |
|---|---|
| **A. Synthetic JWT = story evidence only; Entra under DRG-614 (Recommended, Proposed)** | Matches validation report disclaimer. |
| **B. Treat synthetic JWT as production identity proof** | Overclaims. **NO-GO** for enablement. |
| **C. Block Done until Entra** | Optional; owner words required. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-12 — DRG-339 / apply-path residual

| Option | What the owner would be choosing |
|---|---|
| **A. Disclose 339 In Progress; no GA / flag flip here (Recommended, Proposed)** | Matches #803 residual blockers orientation. |
| **B. Claim hybrid pricing Done = schedule apply Done** | False; apply path is DRG-339. |
| **C. Enable ramp flag while 339 open** | Violates A6 / OQ-341-2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-13 — Conversion credit stack vs hybrid credit

| Option | What the owner would be choosing |
|---|---|
| **A. Keep A2/A3 hybrid credit; conversion stack stays DRG-573 (Recommended, Proposed)** | Matches `specs/conversions.md` orientation and #850 A4. |
| **B. Fold ConversionPolicy into hybrid path here** | Wrong story; product. **NO-GO.** |
| **C. Invent additional credit types in this brief** | Product/schema; blocked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-341-14 — Historical approvals PR #559

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; no open OQs; not Decision authority (Recommended, Proposed)** | Wave cite `#559` resolves to related DRG-559 / #850 for open OQs; merged #559 is supersede plumbing. |
| **B. Reopen supersede semantics from this brief** | Wrong story. **NO-GO.** |
| **C. Treat merged #559 as residual Decision PASS for hybrid** | Category error. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-341-1` .. `OQ-341-14`).
2. CPQ Decision must score the OQ-341-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes further Java/TS product work, Flyway, gateway routes, dual-entitlement product writes, or flag flips for DRG-341.
4. Even after forks close: A1–A11 hold; **Ramp hold** (`opencrm.pricing.ramp.enabled=false`) stays; committed ACV stays `legacy` until a separate enablement act; no Jev enablement; #876 OQ-336-*, #803 OQ2–OQ7, #850 OQ-559-*, and #874 OQ-635-* stay Proposed unless those Decision briefs close them; DRG-339 In Progress and DRG-614 Backlog remain residual rollout discloses.
5. Linear status for DRG-341 stays **In Progress** until the owner and Decision say otherwise. This brief does **not** mark Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or further product code for DRG-341.
- Does not mark any OQ-341-* fork Adopted.
- Does not adopt or close [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) OQ-336-*.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults / OQ8.
- Does not adopt or close [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) OQ-559-* (Wave cite `#559` → DRG-559 residual).
- Does not adopt or close [#874](https://github.com/drgaciw/open-crm-cpq/pull/874) OQ-635-*.
- Does not flip `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version`.
- Does not open dual-entitlement product writes or fulfilment REVOKE/PROVISION from this story.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any new quote/pricing/product migration.
- Does not close DRG-614, DRG-339, DRG-336, DRG-534, DRG-559, DRG-635, DRG-573, or DRG-574.
- Does not reopen ADR-023 Adopted cells.
- Does not change Linear status (remains **In Progress**).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-341-* (especially OQ-341-1, OQ-341-2, OQ-341-3, OQ-341-4, OQ-341-8, OQ-341-9). Until then, further product work is **NO-GO**.
2. **Ramp hold:** flag stays off; no GA from this wave.
3. **Dual-entitlement product writes:** blocked (OQ-341-3).
4. **#876 / #803 / #850 / #874 sibling residuals:** still Proposed; not adopted.
5. **DRG-339 In Progress / DRG-614 Backlog:** apply-path and rollout readiness incomplete for enablement claims.
6. **GitNexus index:** not refreshed in this pass; any future code edit needs a live `impact` / `detect_changes` on the editing checkout.

## Sources read this pass

- Linear: DRG-341 (In Progress; description checkpoints through 2026-10-10; relations including DRG-559 / DRG-338 / DRG-336).
- GitHub: draft Decision briefs #876, #803, #850, #874 (cite-only); historical merged #559 (approvals supersede); hybrid merges #585/#595 orientation via Linear + validation note.
- ADR-023; REQ-price-ramping FR-26; forge/41 R4; `docs/plans/ramp-next-20261010-validation.md`; cluster-b / `specs/conversions.md` orientation.
- Landed code: `HybridMigrationPolicy`, `MigrationCreditPolicy`, `MigrationWindowProperties`, `PeriodGovernance`, `RampPricingProperties`, pricing/quote `application.yml` ramp flag + ACV version; product `V1_8__allow_canonical_ramp_product_types.sql`.
- AGENT.md / `docs/ARCHITECTURE.md` holds orientation (Jev / dual-read / asset-write unchanged; ramp flag treated as ADR-023 D-06 hold).

## Unverified in this pass

- Runtime re-execution of eight-service acceptance or full backend unit stage on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus analyze / `detect_changes` graph coverage beyond docs-only claim (MCP reported no indexed repositories until analyze).
- Whether the owner will mark Done from the 2026-10-10 note without a separate Decision PASS score for OQ-341-*.

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Residual forks Proposed only under **OQ-341-***; A1–A11 restated, not reopened
- [x] Cite-only #876 / #803 / #850 (DRG-559) / #874 — open OQs not adopted
- [x] Linear left **In Progress** (orchestrator / agent must not mark Done)
- [x] Product / schema / flag / dual-entitlement writes / Flyway V1_16–V1_21 = **NO-GO**
- [x] GitNexus unavailable / UNKNOWN — not treated as clean
- [x] Secret scan: no credentials in this file
