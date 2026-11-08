# DRG-560 — Approvals and revisions for lifecycle changes: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-560](https://linear.app/drgamtd-workspace/issue/DRG-560/approvals-and-revisions-for-lifecycle-changes) (RA-26).

**Status:** Proposed residual Decision brief. This file inventories still-open **commercial approval / revision** forks for lifecycle changes as **Proposed OQ-560-***. It **cites** the joint governance-routing decision frame and the #651-adjacent Cluster E lifecycle-change frame without rewriting them or treating them as the final Decision brief. It **cites** the in-flight [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) residual Decision brief ([#833](https://github.com/drgaciw/open-crm-cpq/pull/833)) for the legal / commercial boundary and **does not adopt** that brief’s open OQs. **It authorizes no production code path, no schema change, no Flyway migration, no new LifecycleChange / document status, no `InApproval` state, no `supersedesChangeId` column, no approval-routing writer that assigns `CONTRACT_MODIFICATION`, no dual-read flip of `approvals.legacy-local-read-enabled`, no feature flag, no Kafka consumer retarget, and no deploy.** S3 / DRG-581 stays unstarted. Dual-read, Jev, and ramp holds are untouched. [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) / OQ25 and [DRG-601](https://linear.app/drgamtd-workspace/issue/DRG-601) stay **parked** (this brief does not unpark them). Product code / schema / flag / state-machine work for commercial amendment approvals is **NO-GO** until the owner closes the residual OQx below (or explicitly defers a named fork with words and a time). CAM Flyway `V1_16`–`V1_21` remain reserved (#805–#812).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not accept frames as final)

| Artifact | Relation to this brief |
|---|---|
| [story-drg-560-594-governance-routing-decision-frame.md](story-drg-560-594-governance-routing-decision-frame.md) / [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) | Joint DRG-560 + DRG-594 governance-routing frame (G1..G9). **Cite; do not rewrite; do not accept as final.** Residual **OQ-560-*** below map the commercial / revision rows of that frame. |
| [cluster-e-lifecyclechange-model-decision-frame.md](cluster-e-lifecyclechange-model-decision-frame.md) / [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) | #651-adjacent Cluster E lifecycle-change frame (document statuses, revision surface). **Cite; do not reopen Cluster E closed citations; do not invent `InApproval`.** |
| [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) (#642) + [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | **Adopted** pre-execution supersession / executed immutability (OQ6) and D-STORAGE / D-SNAPSHOT / D-RACE / D-PRICED-WRITE. Cite; **do not reopen**. |
| OQ17 / OQ25 in [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) | Commercial trigger sentence stays **DEFAULT PROPOSED** (OQ17). General renewals-amendments approvals stay **parked** on DRG-591 / PL-9 (OQ25). |
| [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) residual Decision brief / [#833](https://github.com/drgaciw/open-crm-cpq/pull/833) | Legal-review gate residual (OQ-594-*). **Cite only. Do not adopt open OQs.** Legal stays a classification guard, not this story’s commercial row. |
| Related: [DRG-443](https://linear.app/drgamtd-workspace/issue/DRG-443), [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581), [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591), [DRG-601](https://linear.app/drgamtd-workspace/issue/DRG-601), [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) | Approval matrix backlog; S3; parked general approvals; approval skeleton; apply gate. This brief does not start them and does not unpark DRG-591 / DRG-601. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Accepted / adopted / Proposed docs cited in Alignment. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had no usable indexed repo (`.gitnexus/` absent; MCP `list_repos` / `query` returned no indexed repositories). Graph impact for this docs-only change is **UNKNOWN**, not an all-clear. Text search confirms the schema and enum tokens below.

## Issue restatement (RA-26) — cite; do not treat research sketch as adopted

`verified by reading the issue` (Linear DRG-560 description; comments correcting Done → In Review after docs-frame merge).

Continuation changes have **no** landed approval step or revision/supersede handling for commercial governance. Market research (QuoteWerks revision supersession; Chargebee editable renewal quotes) informed a **research sketch** that proposed `InApproval` / `Approved` states and a `supersedesChangeId` on LifecycleChange. The issue’s suggested next step is to **route lifecycle changes through the existing deal-governance engine**, related to DRG-443 — not to ship that sketch.

This residual brief does **not** adopt the research sketch. Frame default G1-A (#666) keeps document states on the `V1_7` list and rejects a second engine / `InApproval` / `supersedesChangeId`.

## Adopted (do not re-open)

| ID | Contract | Source |
|---|---|---|
| **A1** | Pre-execution revision: new revision on the same document, prior closes `SUPERSEDED`, document returns to `DRAFT`, approvals and signatures are void, pricing pins do not carry over | OQ6 rule 1 |
| **A2** | Executed (any apply state): document immutable; new revision refused `409 IMMUTABLE_EXECUTED` | OQ6 rule 2 |
| **A3** | On new pre-execution revision, clear old policy-decision / approval / signature refs (D-STORAGE); approval evidence binds to digest separately (D-SNAPSHOT); revision / submit / approval mutation / priced install / execute serialize on the CAM document row (D-RACE); installing a priced result invalidates prior approval binding (D-PRICED-WRITE) | Delegated 2026-10-17 |
| **A4** | No new Entra role or approval/execute permission is silently granted | Delegated migration-and-access paragraph |
| **A5** | Legal-review free-set / gate / classification remain on DRG-594 (call **#6** + residual OQ-594-*); this story does not redefine them | Call #6; #833 cite-only |
| **A6** | `approvals.legacy-local-read-enabled` stays at its committed default; flipping it is ADR-005 and is **not** this decision | ARCHITECTURE § holds; environment-ladder EL-8 / FB-7 |

## Existing frames (cite; Proposed / not final)

Do **not** rewrite [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) or [#651](https://github.com/drgaciw/open-crm-cpq/pull/651). The tables below are pointers only. Recommendations in those frames are **frame defaults**, not closed owner decisions for this residual brief.

### #666 governance routing — G1..G9 (cite only)

`verified by reading docs`. Joint frame with [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594). Defaults there keep commercial approvals on existing approval-routing with type `CONTRACT_MODIFICATION` (**G1**), fire only under OQ17 (**G2**), use existing tiers (**G3**), keep legal as the separate `DRAFT`→`IN_REVIEW` guard (**G4**), reuse existing columns (**G5**), clear `approval_ref` + supersede `PENDING` on revision (**G6**), treat the approval row as the commercial decision (**G7**), leave uplift precedence off the route (**G8**), and leave the later classification event type **OPEN** (**G9**). Those rows stay Proposed in that frame; this residual brief maps commercial/revision rows to **OQ-560-*** and does not close them.

### #651 Cluster E (cite only)

`verified by reading docs`. Document statuses stay the `V1_7` check list; quote-service having no `LifecycleChange` type remains a GAP in that frame, not a decision to invent one here. This brief does not add LifecycleChange states for approvals.

### #833 DRG-594 residual (cite only — do not adopt open OQs)

`verified by reading docs` / GitHub [#833](https://github.com/drgaciw/open-crm-cpq/pull/833). Legal classification, gate locus, override role, Add/Remove-as-seats, and legal audit naming stay **OQ-594-***. This file must not contradict those open forks and must not close them. Boundary default here (**OQ-560-4**) matches #666 G4-A / #833 OQ-594-4 recommendation without adopting the rest of OQ-594-*.

## What the tree does today

`verified by reading code` at the base SHA.

1. **Document approval pin exists; no amendment commercial writer.** `amendment_documents.approval_ref varchar(255)` in `V1_7__contract_documents.sql`. No Java writer of that column for amendment commercial routing was opened beyond directory/search claims (`unverified` as a full call path).
2. **No `InApproval` / no `supersedesChangeId`.** `chk_amendment_document_status` lists `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID`. Research-sketch states are absent.
3. **`CONTRACT_MODIFICATION` is declared; production writer absent.** `ApprovalType.CONTRACT_MODIFICATION` exists in approval-routing. A search of `backend/**/*.java` for `CONTRACT_MODIFICATION` finds the enum declaration; no production assigner for amendment documents was found (`unverified` as a second-pass over every service).
4. **Supersede path exists for `PENDING` only.** `ApprovalStatus.SUPERSEDED`; approval-routing `V1_8__approval_superseded_status.sql`; `supersede` requires `PENDING`. An `APPROVED` row is not updated by that method (`verified by reading code` on the enum / migration names; method body runtime `unverified`).
5. **BFF quote mapping is not amendment routing.** `QuoteApprovalTierMapper` maps Deal Desk / Sales VP → `QUOTE`, CFO → `GOVERNANCE`. `ApprovalService.submit` requires `quoteId`.
6. **CAM migrations through `V1_15` on this base; `V1_16`–`V1_21` absent (reserved).** Directory listing ends at `V1_15__document_apply_replay_evidence.sql`. This note writes none.
7. **Dual-read hold untouched.** No edit to `approvals.legacy-local-read-enabled` / ADR-005.

## Remaining owner forks (OQx)

Every row below is **Proposed, unaccepted, owner decision required** (`Status: Proposed`). Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, dual-read flip, state-machine change, or deploy. Product code for commercial amendment approvals stays **NO-GO** until they are closed (or explicitly deferred).

| ID | Question | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|
| **OQ-560-1** | **Route home** for an amendment commercial approval | One `CONTRACT_MODIFICATION` row in existing approval-routing. No second engine, no `InApproval`, no `supersedesChangeId` (#666 G1-A) | Map via `QUOTE`/`GOVERNANCE` as BFF does (G1-B); host DRG-601 chain tables (G1-C); research sketch `InApproval` + `supersedesChangeId` (G1-D) | DRG-443 backlog; parked DRG-601; Cluster E statuses |
| **OQ-560-2** | **When** the commercial row fires | OQ17 unchanged: override, backdating, or total ARR delta above Finance-set threshold; otherwise no row and `approval_ref` null (#666 G2-A). Invent **no** threshold number here | Every submit opens a row (G2-B); `SYSTEM` approved row below threshold (G2-C) | OQ17 DEFAULT PROPOSED; Finance threshold; DRG-609 entitlements/conversion pending |
| **OQ-560-3** | **Who decides** | Person ≠ requester; override → `SALES_VP`; backdating / ARR-delta-only → `DEAL_DESK`; no new Entra role; legal override stays refuse-until-named on DRG-594 (#666 G3-A) | `CFO` on every amendment (G3-B); seller self-approval (G3-C); name/grant legal role here (G3-D) | `ApprovalAuthority` / `authorizeDecision` as read; A4; #833 OQ-594-3 (cite only) |
| **OQ-560-4** | **Boundary** vs legal (DRG-594 / #833) and parked DRG-591 | Legal = `DRAFT`→`IN_REVIEW` classification guard only (**not** an `approval_requests` row). Commercial row, when OQ-560-2 fires, blocks `IN_REVIEW`→`APPROVED`. Do **not** unpark DRG-591. Do **not** adopt open OQ-594-* (#666 G4-A; #833 OQ-594-4 cite) | Legal as `TERMS` / combined `CONTRACT_MODIFICATION` row (G4-B/C); fold legal into this story; unpark DRG-591 | #833 open OQs (cite-only); OQ25; DRG-591 park |
| **OQ-560-5** | **Fields** the route uses | Existing columns only. `quote_id` = continuation quote; `line_id` null (whole-document); `approval_ref` holds request id when a row exists, else null (#666 G5-A). No CAM / approval-routing migration in this story | New document-id column before any route (G5-B); leave `approval_ref` empty and keep id only on the quote (G5-C) | `ApprovalService.submit` requires `quoteId`; D-RACE binding; CAM `V1_16`–`V1_21` reserved |
| **OQ-560-6** | **Void on pre-execution revision** | Clear `approval_ref` on the new `DRAFT`. Map prior `PENDING` approval row to `SUPERSEDED` (`superseded_by` / `superseded_at`). Do **not** update an `APPROVED` row (#666 G6-A; OQ6 + D-STORAGE) | `cancel()` the pending row (G6-B); add `VOIDED` status via later approval-routing migration (G6-C) | OQ6 A1; ApprovalStatus.SUPERSEDED; D-PRICED-WRITE |
| **OQ-560-7** | **Commercial audit home** vs price snapshot | Approval row is the commercial decision. Price snapshot stays the #663 pricing-owned store. Classification event type stays open with G9 / #833 OQ-594-5 — **not closed here** (#666 G7-A) | Classification only in `reason` (G7-B); approval columns on DRG-564 sketch (G7-C); new CAM classification table now (G7-D) | #663 pricing paragraph; DRG-564 stays proposal; G9 OPEN |
| **OQ-560-8** | **Uplift / discount** vs this route | Precedence CLAUSE→LINE→AGREEMENT→ACCOUNT→RULES is not a route. Resolved uplift is not a trigger. Manual override is OQ17. Discount Kafka path stays `DISCOUNT` (#666 G8-A) | Every non-zero uplift opens `GOVERNANCE` (G8-B); route overrides as `DISCOUNT` (G8-C); hold price until approval row exists (G8-D) | Accepted uplift D-04; DRG-549 OVERRIDE out of first slice; discount consumer |
| **OQ-560-9** | **Later local classification event** whose type is not chosen | **OPEN FORK.** No event name. No schema. No S6 topic. Aligns with #666 G9 and leaves naming to a later decision / #833 OQ-594-5 without adopting that OQ | Later named type + payload (G9-B); S6 domain topic now (G9-C) | D-RACE “no new S6 topic”; #833 OQ-594-5 cite-only |
| **OQ-560-IMPL** | Implementation / migration / writer authorization | Separate implementation story **after** OQ-560-1..9 (or deferred subset) close; additive only; never edit landed V1_7/V1_8; never consume CAM `V1_16`–`V1_21`; never flip `approvals.legacy-local-read-enabled` | Piggy-back writer before forks close; alter V1_7 in place (forbidden); flip dual-read as a side effect (forbidden) | Owner implementation release; S3 / S5 timing; holds in [AGENT.md](../../../AGENT.md); ADR-005 |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-560-1 — Route home

| Option | What the owner would be choosing |
|---|---|
| **A. Existing approval-routing, type `CONTRACT_MODIFICATION` (Recommended, Proposed)** | Matches issue “reuse existing governance engine”; rejects research sketch. |
| **B. BFF `QUOTE` / `GOVERNANCE` mapping** | Couples amendments to quote BFF tier mapper. |
| **C. DRG-601 skeleton tables** | Unparks parked chain/threshold design. |
| **D. `InApproval` + `supersedesChangeId`** | New states / columns; contradicts Cluster E status list and OQ6 revision model. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-2 — When the commercial row fires

| Option | What the owner would be choosing |
|---|---|
| **A. OQ17 as written (Recommended, Proposed)** | Override / backdating / ARR-delta above Finance threshold only. Null `approval_ref` valid when classification said no row required. |
| **B. Every submit** | Always creates `approval_requests`. |
| **C. System-approved row below threshold** | Forces non-null `approval_ref`; no such writer was read. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-3 — Who decides

| Option | What the owner would be choosing |
|---|---|
| **A. Existing roles, split by trigger (Recommended, Proposed)** | Override → `SALES_VP`; backdating / ARR-delta → `DEAL_DESK`; self-approval refused. |
| **B. CFO always** | Heavier than OQ17. |
| **C. Seller self-approval** | Contradicts `SELF_APPROVAL` refuse. |
| **D. Name legal role here** | Out of scope; belongs to DRG-594 / refuse-until-named. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-4 — Boundary vs DRG-594 / parked DRG-591

| Option | What the owner would be choosing |
|---|---|
| **A. Legal guard ≠ commercial approval row; DRG-591 stays parked (Recommended, Proposed)** | Cite #833 OQ-594-4 without adopting open OQ-594-*. |
| **B. Legal as approval-routing row** | Couples legal to this engine / tiers. |
| **C. Unpark DRG-591** | Forbidden by delivery holds for this residual brief. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-5 — Fields

| Option | What the owner would be choosing |
|---|---|
| **A. Existing columns only (Recommended, Proposed)** | `approval_ref` + digest binding; no migration; CAM `V1_16`–`V1_21` untouched. |
| **B. Document id on the request first** | Blocks route on a new column. |
| **C. Quote only** | Leaves `approval_ref` empty. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-6 — Void on revision

| Option | What the owner would be choosing |
|---|---|
| **A. Clear pin; supersede only `PENDING` (Recommended, Proposed)** | Matches OQ6 void + D-STORAGE + existing `SUPERSEDED` contract. |
| **B. Cancel pending** | Uses `CANCELLED` instead of supersession history link. |
| **C. New `VOIDED` status** | Extra approval-routing migration; not written here. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-7 — Audit entries

| Option | What the owner would be choosing |
|---|---|
| **A. Approval row + pricing-owned snapshot; classification event not named here (Recommended, Proposed)** | Commercial decision ≠ legal classification audit. |
| **B. `reason` text only** | Weak when no row exists under OQ-560-2-A. |
| **C. DRG-564 sketch columns** | Takes a proposal that stays unaccepted. |
| **D. New CAM classification table now** | Migration; reserved band risk. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-8 — Uplift and discount waivers

| Option | What the owner would be choosing |
|---|---|
| **A. Precedence is not a route (Recommended, Proposed)** | Manual override is the OQ17 path. |
| **B. Every uplift → `GOVERNANCE`** | Over-triggers. |
| **C. Overrides as `DISCOUNT`** | Retargets the discount consumer. |
| **D. Hold price until approval exists** | DRG-549 alternative not adopted for first slice. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-9 — Classification event type (OPEN)

| Option | What the owner would be choosing |
|---|---|
| **A. OPEN FORK — no name, no schema (Recommended, Proposed)** | Leaves G9 / #833 OQ-594-5 open without adopting them. |
| **B. Later named type** | Separate naming decision. |
| **C. S6 topic now** | Contradicts delegated “no new S6 topic” unless a later story authorizes it. |

**Recommended (Proposed):** A (OPEN). `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### OQ-560-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl story after residual OQx close (Recommended, Proposed)** | Additive only; dual-read hold; reserved CAM versions. |
| **B. Fold into S3/S5 before forks close** | Couples unstarted delivery to open product forks. |
| **C. Edit V1_7 / flip dual-read** | Forbidden. |

**Recommended (Proposed):** A. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

## Out of scope (explicit)

- Any Java/TypeScript change, Flyway migration, feature flag, workflow, gateway route, or deploy.
- Adding `InApproval`, `supersedesChangeId`, a classification column, or a `CONTRACT_MODIFICATION` writer.
- Flipping `approvals.legacy-local-read-enabled` or accepting ADR-005.
- Consuming CAM Flyway `V1_16`–`V1_21` (reserved #805–#812) or editing landed V1_7 / V1_8.
- Starting S3 / DRG-581, authorizing S5/S6, or rewriting OQ6 / OQ17’s sentence.
- Unparking [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) or [DRG-601](https://linear.app/drgamtd-workspace/issue/DRG-601).
- Closing or rewriting [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) G1..G9, [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) Cluster E forks, or adopting [#833](https://github.com/drgaciw/open-crm-cpq/pull/833) open OQ-594-*.
- Re-opening OQ6 executed immutability, D-STORAGE / D-SNAPSHOT / D-RACE / D-PRICED-WRITE, or Accepted uplift D-04.
- Dual-read flip, Jev enablement, ramp flag, or S4 hold changes.
- Marking Linear DRG-560 Done.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-560-1..9** and **OQ-560-IMPL** as the residual owner menu without re-litigating OQ6 or inventing a second approval engine. Accepting / merging this file **does not** open an implementation story, does not add a writer, does not change the state machine, does not flip dual-read, and does not unpark DRG-591.

## References

- Linear: DRG-560, DRG-594, DRG-443, DRG-581, DRG-591, DRG-601, DRG-562, DRG-547.
- GitHub: [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) (governance-routing frame), [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) (Cluster E frame), [#833](https://github.com/drgaciw/open-crm-cpq/pull/833) (DRG-594 residual — cite only), [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) (delegated record).
- Schema (read-only): `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`; approval-routing `V1_1` / `V1_2` / `V1_8` / `V1_9`.
- Code (read-only): `ApprovalType.java`, `ApprovalStatus.java`, `ApprovalAuthority.java`, `ApprovalService.java`, `QuoteApprovalTierMapper.java`.
- Docs: #666 governance frame; #651 Cluster E; OQ6; delegated 2026-10-17; OQ register OQ17/OQ25; environment-ladder dual-read hold.
- Holds: [AGENT.md](../../../AGENT.md); [docs/ARCHITECTURE.md](../../ARCHITECTURE.md) dual-read; parked DRG-591; CAM `V1_16`–`V1_21` reserved.
