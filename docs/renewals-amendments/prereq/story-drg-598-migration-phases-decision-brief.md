# DRG-598 — In-flight contract migration phases: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted call 2026-10-16 (#14, #15), reopen Adopted Cluster G cells F-598-1..F-598-7 (including the delegated F-598-2 90-day window contract), amend the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md), accept [`specs/spine-dependent.md`](specs/spine-dependent.md), authorize an implementation brief, authorize product code, open a Flyway migration, run any S7 / S7-class backfill, flip a flag, register a gateway route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster G migration. **Story:** [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598/in-flight-contract-migration-phased-by-end-date-upcoming-renewals) (RA-64). Linear read this pass: **In Review**. Notion: [RA-64 backlog row](https://app.notion.com/p/3fbf7cb4e4df815289ccc6a3f59a8a79) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| Call 2026-10-16 **(#14, #15)** / Linear DRG-598 | **Adopted baseline:** in-flight contracts migrate in **phases ordered by contract end date**; first phase = **upcoming renewals** (nearest end dates); scope expands **incrementally** via **sales/marketing metrics** (not big-bang) |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Migration and access + Cluster G disposition F-598-1..7 | **Adopted baseline** for written phase manifests, initial **execution-date through +90 days inclusive** window, project-owner expansion approval with metrics on the manifest, headers-only / no synthetic history, coexistence without OQ11 flip, S7 count/apply/compensate as owner steps (not redefined here), order by `subscriptions.end_date`, and the rule that **unfiltered V1_8 is not a manifest selector** |
| [cluster-g-visibility-queues-migration-decision-frame.md](cluster-g-visibility-queues-migration-decision-frame.md) | Historical F-598-* options/rationale; dispositions already indexed. Do not re-litigate Adopted cells |
| [specs/spine-dependent.md](specs/spine-dependent.md) § DRG-598 + fixtures AC-598-1..3 | Proposed acceptance criteria / fixtures; authorizes no code and invokes no backfill (`backfill_invoked` false on every fixture) |
| [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) + `V1_8__contract_header_backfill.sql` | S7 companion: functions only at deploy; `contract_header_backfill_apply` / `count` / `compensate` stay **manual owner steps**. No bot runs them against real data |
| [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md) + open-questions register OQ7 | Thin-contract / header-only default is recorded; **grouping remains open** — cite, do not invent |
| [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) (S7) / [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566) | Header backfill and history backfill have **no phasing of their own**; this story adds the phase plan **on top**. S7 can run per phase once a later owner GO authorizes a real run. History stays on DRG-566 |

**In-flight alignment (do not contradict Accepted / draft semantics):**

| Ticket / PR | Linear / GH | What this brief must not contradict |
|---|---|---|
| [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) (S7) | Related header backfill | Fill rules unchanged. Phasing is selection/policy only. This brief does **not** authorize `contract_header_backfill_apply` against any real environment |
| [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566) | Related history | Outside the phase under Adopted F-598-4. No `ORIGINAL` / `BACKFILLED` documents from this story |
| [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) (S6) / OQ11 | Related legacy-path gating | Unauthorized / DEFAULT PROPOSED. Approving a phase does **not** enable the per-tenant legacy-path flag |
| [DRG-579](https://linear.app/drgamtd-workspace/issue/DRG-579) (S1) | Related schema | Thin header + nullable `subscriptions.contract_id` already on `main` (V1_7). This brief adds no schema |
| [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595) / [#826](https://github.com/drgaciw/open-crm-cpq/pull/826) | Related visibility | Cite only. Do **not** adopt OQ-595-* here. Expansion approval stays project owner (Adopted F-598-3), not ticket "sales ops" |
| [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) / [#828](https://github.com/drgaciw/open-crm-cpq/pull/828) | Related queues | Cite only. Do **not** adopt OQ-597-* here. Queues are not the migration selector |
| [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) / [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) | Related S7-class NO-GO tracking | Asset lineage / installed-base migrations are **separate** S7-class tracks. Same execution gates; this brief does not authorize them and does not merge their manifests into RA-64 phases |
| [DRG-648](https://linear.app/drgamtd-workspace/issue/DRG-648) / spine-dependent fixtures | Prereq | AC-598-* fixtures stay Proposed labels; `backfill_invoked` stays false |
| [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) | Later regroup | Thin-contract sentence: the backfill does not regroup. Regroup waits on DRG-554 |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read / NgRx / Activities / ramp / S4 holds in `AGENT.md` stay untouched. An implementation brief for DRG-598 is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time). **Even after forks close, any real backfill remains S7-class NO-GO** until named env/tenant, dry-run counts, restore point, and an explicit owner GO are recorded on that run.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (#14/#15)` / `Adopted (delegated)` | Already selected by the 2026-10-16 call and/or the 2026-10-17 delegated record / Cluster G disposition. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/run.cjs` / no indexed repo. Graph impact is **UNKNOWN** (unresolved), not a clean check. |

## Problem (RA-64)

Call decisions 2026-10-16 (#14, #15): in-flight contracts migrate to the new model in **phases ordered by contract end date**. The first phase is **upcoming renewals** (nearest end dates). Migration scope expands **incrementally**, driven by **sales and marketing metrics** (segment, region, or product-line priority as named on the issue), rather than a single big-bang cutover.

S7 (DRG-585) backfills the contract header for existing subscriptions with no synthetic history, and DRG-566 backfills amendment history, **with no phasing or ordering of their own**. This story adds the **phase plan and selection rules on top**. S7 itself is unchanged and **can run per phase**. Open question **OQ7 grouping remains open** — this brief cites that fact and does not invent a grouping rule.

Cluster G + the delegated record already adopted the high-level F-598-* defaults (written manifest; initial execution-date→+90d window; owner-approved expansion with metrics on the manifest; headers-only; coexistence without OQ11; S7 owner steps for count/apply/compensate; order by `end_date`; V1_8 is not a subset selector). There is still no owner-scored Decision brief that (a) cites Adopted #14/#15 and those Adopted cells without reopening them, and (b) lists **residual delivery forks** (manifest artifact shape, metric input sources, selector/wrapper vs unfiltered V1_8, per-phase dry-run/reconcile report contents, coexistence evidence vs S6, relation to DRG-660/657 NO-GO tracks, OQ7 grouping citation boundary, fixture freeze, sequencing before any real run) that must close before product code, schema, flags, gateway routes, or any backfill execution.

Without that split, a later story could treat one tenant-wide `contract_header_backfill_apply` call as a "phase", invent a big-bang window, let ticket "sales ops" approve expansions, synthesize history inside a phase, flip OQ11 on phase approval, or claim DRG-660/657 GO from this file.

Linear DRG-598 (`Linear read` 2026-11-06): Medium; **In Review**; related DRG-597 / DRG-579 / DRG-585 / DRG-566 / DRG-584 / DRG-660 / DRG-657 / DRG-648 / DRG-607. Attachments: Notion backlog row; Cluster G draft PR [#656](https://github.com/drgaciw/open-crm-cpq/pull/656) (frame later adopted via the delegated record). Wave-2 lane refill comment records docs-only Decision brief scope with **no backfill execution**.

## Already Adopted (do not re-decide)

Restated from call **#14/#15**, the delegated record § Migration and access, and Cluster G disposition (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | In-flight contracts migrate in **phases ordered by contract end date** | Call 2026-10-16 **(#14)** |
| A2 | First phase is **upcoming renewals** (nearest end dates) | Call **(#15)** |
| A3 | Scope expands **incrementally** via **sales/marketing metrics**, not a big-bang cutover | Call **(#15)** |
| A4 | A phase is a **written manifest** of subscription ids (ordered nearest-end first, then narrowed by metrics). It is **not** a call to `contract_header_backfill_apply` | F-598-1 A |
| A5 | Initial manifest window is **finite**: subscription `end_date` from **execution date through execution date + 90 days, inclusive**, ordered by `end_date` / id. Writing the window selects no rows and authorizes no run | F-598-2 (delegated replacement of Cluster G "owner names window"); § Migration and access |
| A6 | **Project owner** approves each new / expanded manifest. Metric values (segment, region, product line) sit **on the manifest**. No new `contracts` column. Empty metric filter means the end-date window only | F-598-3 A |
| A7 | Phase content is **headers only** (when a later owner decision authorizes a write). No synthetic `ORIGINAL` / `BACKFILLED` documents, no retroactive events. DRG-566 stays outside the phase. OQ7 register grouping stays open | F-598-4 A; issue clarification; OQ7 |
| A8 | `contract_id` null stays on the **current acceptance path**. A header does **not** switch a row onto a document apply path. Approving a phase does **not** enable OQ11 / S6 | F-598-5 A |
| A9 | `contract_header_backfill_count` / `apply` / `compensate` stay the **S7 companion owner steps**. This story runs none of them and authorizes no real-env run | F-598-6 A; S7 companion |
| A10 | Ordering date is **`subscriptions.end_date`** (the date S7 copies to `contracts.end_date`), not nullable `renewal_date` | F-598-7 A |
| A11 | Unfiltered V1_8 `contract_header_backfill_apply(p_tenant_id)` is **not** a manifest selector. Never use it to claim subset / phased migration. A separately delivered selector / guarded wrapper with exact-manifest validation, idempotency, and rollback evidence is required **before** a phased run | Delegated § Migration and access |
| A12 | Each manifest records ids, expected versions/counts, and observed dry-run results; expansion and execution retain existing operational approval gates. **No backfill runs in the decision record or this brief** | Delegated § Migration and access |
| A13 | Holds: no product code / Flyway / flag / gateway route / deploy from docs PRs alone; dual-read / NgRx / Jev / ramp / S4 apply holds in `AGENT.md` untouched; S7-class data execution stays NO-GO without named env, dry-run counts, restore point, and owner GO | Cluster holds; AGENT.md; DRG-660/657 class gates |

## What the tree does today (migration / S7)

`verified by reading code` / docs at the base SHA.

1. S1 / DRG-579 is Done on `main`: `contracts` thin header and nullable `subscriptions.contract_id` exist in `V1_7__contract_documents.sql`. `contracts` has **no** segment / region / product-line column.
2. `V1_8__contract_header_backfill.sql` ships **functions only**. Eligibility for `contract_header_backfill_apply(p_tenant_id uuid)` is tenant-wide among eligible subscriptions (`contract_id` null, not deleted, date/status/customer predicates). The `WHERE` clause has **no** end-date window and **no** metric predicate. S7 companion: running apply against any real environment is an **owner decision**; no bot runs it.
3. Compensate is `contract_header_backfill_compensate()`: deletes only `V1_8_HEADER_BACKFILL` rows and raises when amendment documents / `prior_contract_id` / other subscriptions reference a marked row.
4. `subscriptions.end_date` is `NOT NULL` (V1_1). `subscriptions.renewal_date` is nullable. S7 copies `end_date` onto the header.
5. S6 / DRG-584 is unauthorized. OQ11 remains **DEFAULT PROPOSED** (per tenant, default off).
6. Spine-dependent fixtures AC-598-1..3 stay **Proposed** and expect `backfill_invoked: false` / `header_written: false` on every case.
7. OQ7 thin-contract wording (one thin contract per existing subscription; no synthetic history; later regroup on DRG-554) is recorded; **grouping choice beyond that thin-contract default remains open** in the register / owner-decision note. This brief does not invent a grouping.

### Clarification boundary (cite, do not invent)

| Topic | What is true here | What stays open |
|---|---|---|
| S7 / DRG-585 | Header-only fill rules; no built-in phase plan | Per-phase **execution** still needs S7-class GO |
| DRG-566 | History backfill is a separate story | Whether / when history runs after headers |
| OQ7 grouping | Thin-contract default is recorded; spike alternatives (customer+original quote) exist as text | Owner confirmation of grouping beyond "one header per subscription"; do **not** invent a rule in this brief |
| Phased selection | Policy Adopted (#14/#15, F-598-*) | Residual OQ-598-* delivery forks below |

### Relation to DRG-660 / DRG-657 (S7-class NO-GO tracking)

| Track | What it is | Relation to DRG-598 |
|---|---|---|
| DRG-598 (this story) | Phase **policy** + residual delivery forks for in-flight header migration selection | Docs / decision only in this PR |
| [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) | Asset (g): lineage backfill of `subscriptions.contract_id` — **tracking only, S7-class, NO-GO** | Separate track. Same class of execution gates. Not a RA-64 phase manifest |
| [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) | Asset (e): installed-base migration with reconciliation — **S7-class, NO-GO** | Separate track. Reconciliation there is asset-diff; not this story's phase report |

Shared gate language (cite only; this brief does **not** authorize any of these runs): named env/tenant, dry-run counts, restore point recorded, explicit owner GO. Missing evidence is a **delivery gap**, not a remaining policy choice on the Adopted cells.

### GitNexus (this checkout)

No indexed repository (no `.gitnexus/run.cjs`). Graph impact for any future CAM phase-selector / backfill-wrapper symbol is **UNKNOWN** — unresolved, not low. A later implementation must run impact before editing `contract_header_backfill_*`, CAM migrations, or any selector wrapper.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Do not use these rows to reopen A1–A13.

| ID | Question | Recommended (Proposed) | Reject / avoid | Blocks implementation? |
|---|---|---|---|---|
| OQ-598-1 | Manifest artifact shape | Versioned **decision-record document** (repo or ops vault): tenant id, inclusive end-date window, ordered subscription ids, metric filters, expected counts/versions, dry-run observations, owner approval slot + time. Exact storage table is **would-be only** — **NO-GO** in this brief | Treat unfiltered V1_8 apply as the phase (reopens A4/A11); edit V1_8 in this brief | Yes — AC-598-1 / AC-598-2 |
| OQ-598-2 | End-date window selection beyond A5 | Keep Adopted A5 for the **first** phase. Later phases: owner writes a new inclusive window on a new approved manifest; still ordered by `end_date` ascending. No automatic rolling window job in this brief | Big-bang "every future end date"; silent auto-expand without owner slot (breaks AC-598-2) | Yes — AC-598-1 |
| OQ-598-3 | Metric inputs (segment / region / product line) | Metrics are **filter values written on the manifest** (A6). Source of truth for those values is a **cited CRM/catalog export or owner-supplied list** attached to the manifest; this brief adds **no** schema column and no live join | New `contracts` columns now; sales-ops approve expansions (reopens A6); invent metric dimensions beyond the ticket's examples without owner words | Soft for first window-only phase; hard for metric expansion ACs |
| OQ-598-4 | Expansion approval workflow | Project owner fills `Owner decision: ___ (drg, time)` on the expansion manifest **before** any `APPLY` / selector run. Unapproved expansion ⇒ refuse (`MANIFEST_UNAPPROVED` spirit of AC-598-2). Approval does **not** itself write headers | Ticket sales ops / finance approve; approval implies apply | Yes — AC-598-2 |
| OQ-598-5 | Selector / guarded wrapper vs V1_8 | Deliver a **later** exact-manifest selector / guarded wrapper (A11) that validates id set, versions/counts, idempotency, and rollback evidence. Unfiltered `contract_header_backfill_apply(tenant)` remains available only as the S7 owner tool for **full eligible population**, never as proof of a subset phase | Claim subset migration via V1_8; edit V1_8 WHERE in a docs PR; run apply from this brief | Yes — before any phased real run |
| OQ-598-6 | Per-phase validation / dry-run / reconciliation report | Before any real write: dry-run count (eligible vs manifest), unexplained-delta threshold (zero unexplained), restore-point id, env/tenant name, owner GO stamp. After write (when separately authorized): reconcile headers created / `contract_id` set / compensate candidates. Report is an **ops artifact**; this brief defines no new SQL | Skip dry-run; invent phase SQL here (reopens A9 option B); treat backup-only undo as replacing compensate without owner choice | Yes — S7-class execution gate |
| OQ-598-7 | Rollback / compensate boundary | Keep S7 `contract_header_backfill_compensate` as the documented undo for `V1_8_HEADER_BACKFILL` rows (A9). Phase rollback evidence must show compensate eligibility **or** restore-point restore. This brief runs neither | Redefine compensate in this brief; claim backup restore silently replaces compensate without owner decision | Soft until first authorized phase write |
| OQ-598-8 | Coexistence + S6 / OQ11 gating | Keep Adopted A8: unmigrated (`contract_id` null) and header-present rows stay on **current acceptance path** until S6 is separately authorized. Phase approval **never** flips OQ11. Evidence = AC-598-3 spirit (`oq11_flag_changed` false) | Enable OQ11 on phase approve; invent per-contract path flag here | Yes — AC-598-3 |
| OQ-598-9 | OQ7 grouping citation boundary | **Cite only:** thin-contract default + "grouping remains open". Phase manifests list **subscription ids** under that thin-contract assumption. Do **not** invent customer+quote grouping or regroup-during-backfill | Close OQ7 grouping here; regroup inside a phase (conflicts with DRG-554 / thin-contract sentence) | Soft for phase policy; hard if someone tries history/group merge in-phase |
| OQ-598-10 | Relation to DRG-660 / DRG-657 | Keep tracks **separate**. Shared S7-class NO-GO gates only. Do not fold asset lineage / installed-base ids into a RA-64 phase manifest. Do not treat scoring this brief as GO for 660/657 | One mega-backfill across 598+660+657; inherit GO across tracks | Soft for docs; hard for execution safety |
| OQ-598-11 | Sequencing vs real backfill | Decision brief → scored OQ-598-* → implementation brief (selector/wrapper + report format) → **separate** S7-class GO per env for any real run. This file is step one only | Treat merge of this brief as backfill authorization; run apply in CI | Yes — release / data gate |
| OQ-598-12 | Fixture / error-code freeze | Keep Proposed fixture expectations (`backfill_invoked` false, `MANIFEST_UNAPPROVED`) until an implementation brief; do not invent new public migration codes here | Rename codes now; add gateway-only migration routes in this brief | Soft (docs) |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-598-1 — Manifest artifact shape

| Option | What the owner would be choosing |
|---|---|
| **A. Versioned decision-record document (Recommended, Proposed)** | Delivers A4/A12 without a schema claim. Exact table/migration number is **would-be only** and **NO-GO** here. |
| **B. One tenant-wide V1_8 apply call is the phase** | Reopens A4/A11. Cannot prove nearest-end ordering or metric narrowing. |
| **C. Edit V1_8 to take an end-date window in this brief** | Reopens F-598-1 C. Docs PR must not mutate the migration. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-2 — Window selection beyond the first phase

| Option | What the owner would be choosing |
|---|---|
| **A. Keep A5 for phase 1; later phases are new owner-named inclusive windows (Recommended, Proposed)** | Preserves delegated 90-day first window without inventing an auto-roller. |
| **B. Every future end date in one phase** | Big-bang the call declined. |
| **C. Silent rolling job expands the window** | Breaks AC-598-2 owner-slot rule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-3 — Metric inputs

| Option | What the owner would be choosing |
|---|---|
| **A. Manifest-written filters from cited export / owner list (Recommended, Proposed)** | Delivers A3/A6 without schema. |
| **B. Add segment/region/product-line columns now** | Schema NO-GO in this brief. |
| **C. Sales ops approve metric expansions** | Reopens F-598-3 B / A6. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-4 — Expansion approval

| Option | What the owner would be choosing |
|---|---|
| **A. Project owner slot required before APPLY (Recommended, Proposed)** | Matches AC-598-2 / A6. Approval ≠ write. |
| **B. Approval implies header write** | Collapses policy into unauthorized execution. |
| **C. Finance approves expansions** | Reopens F-598-3 C. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-5 — Selector / wrapper

| Option | What the owner would be choosing |
|---|---|
| **A. Later exact-manifest guarded wrapper; V1_8 stays non-selector (Recommended, Proposed)** | Restates A11. Implementation brief owns the wrapper design after forks close. |
| **B. Use V1_8 apply and filter in app memory** | Unsafe / unverifiable as subset migration. |
| **C. Change V1_8 in this PR** | Forbidden. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-6 — Per-phase validation and reconciliation reports

| Option | What the owner would be choosing |
|---|---|
| **A. Ops dry-run + zero unexplained delta + restore point + GO stamp; post-write reconcile artifact (Recommended, Proposed)** | Aligns with S7-class gates and A12. No new SQL in this brief. |
| **B. Define new phase SQL here** | Reopens F-598-6 B / A9. |
| **C. Skip dry-run for "small" windows** | Violates S7-class NO-GO language shared with DRG-660/657. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-7 — Rollback / compensate

| Option | What the owner would be choosing |
|---|---|
| **A. Keep S7 compensate; require eligibility evidence or restore-point restore (Recommended, Proposed)** | Restates A9. Runs neither here. |
| **B. Replace compensate with backup-only undo without owner choice** | Reopens F-598-6 C as a silent default. |
| **C. Invent phase-local undo SQL in this brief** | Schema / execution NO-GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-8 — Coexistence and S6

| Option | What the owner would be choosing |
|---|---|
| **A. Current path until S6 authorized; no OQ11 flip (Recommended, Proposed)** | Restates A8 / AC-598-3. |
| **B. Phase approval enables OQ11** | Reopens F-598-5 B. |
| **C. New per-contract path flag in this brief** | Reopens F-598-5 C; schema NO-GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-9 — OQ7 grouping

| Option | What the owner would be choosing |
|---|---|
| **A. Cite open grouping; manifests list subscription ids under thin-contract default (Recommended, Proposed)** | Honors issue clarification: OQ7 grouping remains open — do not invent. |
| **B. Close OQ7 grouping in this brief** | Out of scope; belongs on the OQ7 owner-decision path. |
| **C. Regroup during phase** | Conflicts with thin-contract / DRG-554. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-10 — DRG-660 / DRG-657

| Option | What the owner would be choosing |
|---|---|
| **A. Separate tracks; shared gate language only (Recommended, Proposed)** | Prevents accidental mega-backfill authorization. |
| **B. One combined manifest across 598+660+657** | Couples unrelated NO-GO tracks. |
| **C. Scoring this brief = GO for 660/657** | Explicitly forbidden. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-11 — Sequencing vs real backfill

| Option | What the owner would be choosing |
|---|---|
| **A. Brief → scored forks → implementation brief → separate S7-class GO per env (Recommended, Proposed)** | Keeps A9/A11/A13. |
| **B. Merge of this brief authorizes apply** | Forbidden. |
| **C. CI runs apply against a shared DB** | Forbidden. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-598-12 — Fixture / error-code freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposed AC-598-* codes until implementation brief (Recommended, Proposed)** | Soft docs gate. |
| **B. Rename or add gateway migration routes now** | Premature public contract; gateway NO-GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-598-1..OQ-598-12 (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (Java / TypeScript) for phase selectors, migration jobs, or coexistence path switches.
2. **NO-GO:** Flyway / schema for a phase-manifest table, metric columns, or V1_8 edits.
3. **NO-GO:** backfill **execution** — no call to `contract_header_backfill_apply`, `count`, or `compensate` against any real environment from this story or its PR.
4. **NO-GO:** feature flags (including OQ11 / S6 legacy-path), outbox / relay / Jev enablement.
5. **NO-GO:** new or changed gateway routes / filters for migration apply.
6. **NO-GO:** treating this file, Cluster G, spine-dependent fixtures, or DRG-660/657 tracking issues as authorization to run S7 or S7-class data jobs.
7. Even after forks close: S7 / DRG-566 / S6 remain separately authorized; each real run still needs named env/tenant, dry-run counts, restore point, and explicit owner GO; dual-read / NgRx / ramp / S4 / Jev holds stay as in `AGENT.md`.

Linear status for DRG-598 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No manifest table / metric column / V1_8 edit here |
| Backfill execution | **Forbidden** — S7-class NO-GO without separate owner GO |
| Flag / deploy | No flag flip; no deploy |
| Gateway | No migration apply route or filter |
| Jev / ramp / dual-read / NgRx / S4 | Untouched |
| S6 / OQ11 | Not authorized; not flipped by phase approval |
| OQ7 grouping | Remains open — cited, not invented |
| DRG-660 / DRG-657 | Remain their own S7-class NO-GO tracks |

## Out of scope

- Reopening F-598-1..F-598-7 or call #14/#15.
- Editing the delegated decision record, S7 companion, OpenAPI, or UI spec.
- Implementing AC-598-1..3 or running any fixture against a database.
- Closing OQ7 grouping.
- Authorizing DRG-566 history synthesis, S6 legacy-path work, or asset migrations on DRG-660/657.
- DRG-595 visibility or DRG-597 queue residual forks (cite sibling drafts #826 / #828 only).

## References

- Linear: DRG-598, DRG-585, DRG-566, DRG-584, DRG-579, DRG-595, DRG-597, DRG-660, DRG-657, DRG-648, DRG-607, DRG-554.
- GitHub: Cluster G frame [#656](https://github.com/drgaciw/open-crm-cpq/pull/656); visibility Decision brief [#826](https://github.com/drgaciw/open-crm-cpq/pull/826); queues Decision brief [#828](https://github.com/drgaciw/open-crm-cpq/pull/828); delegated record on `main`.
- Docs: `cluster-g-visibility-queues-migration-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `specs/spine-dependent.md`, `s7-v1-8-header-backfill.md`, `oq7-header-backfill-owner-decision.md`.
- Fixtures: `specs/fixtures/spine-dependent/drg-598-*.json`.
