# DRG-565 — Partial application rules for mixed-line amendment documents: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565/partial-application-rules-for-mixed-line-amendment-documents) (RA-31).

**Status:** Proposed residual Decision brief. This file **restates Adopted OQ5 Option A** (first slice only) without reopening it, **cites** the merged owner-decision and rules notes, and surfaces **remaining owner forks as Proposed OQ-565-\***. **It authorizes no production code path, no schema change, no Flyway migration (including CAM slots V1_16–V1_21 reserved by #805–#812), no inventing of product `PARTIALLY_APPLIED` / `APPLY_BLOCKED` persistence beyond what Option A already named for the first slice, no feature flag, no Kafka consumer registration, no S4 apply enablement, no S7 backfill run, no asset-write path, no dual-read / Jev / ramp flip, and no deploy.** Product / schema / flag work that widens partial-apply behavior is **NO-GO** until the owner closes the residual OQ-565-\* forks below (or explicitly defers a named fork with words and a time).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen Adopted OQ5 Option A)

| Artifact | Relation to this brief |
|---|---|
| [#626](https://github.com/drgaciw/open-crm-cpq/pull/626) → [partial-apply-owner-decision.md](partial-apply-owner-decision.md) | **Adopted** owner-decision frame for OQ5 Option A (first slice). Cite; **do not reopen**. |
| [#631](https://github.com/drgaciw/open-crm-cpq/pull/631) → [partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md) | **Adopted** first-slice rules note (unit-by-unit apply, no silent rollback, status cut). Cite; **do not reopen**. |
| [#632](https://github.com/drgaciw/open-crm-cpq/pull/632) | **Adopted** register / section-9 record that Option A is confirmed for the first slice only. Cite; **do not reopen**. |
| Register **OQ5** in [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) | **ADOPTED 2026-10-16, first slice only.** Same Option A sentence. |
| [specs/partial-apply.md](specs/partial-apply.md) | **Proposal only; not accepted.** Includes DRG-637 open items (`Status: Proposed`). Cite; **do not accept** as ADR or product contract. |
| [story-drg-536-multi-line-amendment-design-note.md](story-drg-536-multi-line-amendment-design-note.md) | Multi-line design note; records create-before-terminate **GAP** (who writes `dependsOnUnitNos`). Cite interaction; do not close 536 forks here. |
| [story-drg-551-dry-run-and-revert-design-note.md](story-drg-551-dry-run-and-revert-design-note.md) | Dry-run / revert note; reuses Option A cut for projected statuses and correcting-amendment remedy. Cite; do not accept as apply authorization. |
| Related Wave-2 Decision drafts (cite only; **do not adopt** their open OQs) | Fulfilment [#821](https://github.com/drgaciw/open-crm-cpq/pull/821) (DRG-578); capacity true-up [#818](https://github.com/drgaciw/open-crm-cpq/pull/818) (DRG-569); ChangeLine [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) (DRG-552); effective vs action dates [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) (DRG-553). |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Adopted / Proposed docs cited in Alignment. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had **no** indexed repository (`list_repos` empty; no `.gitnexus/` / `run.cjs`). Graph impact for this docs-only change is **UNKNOWN**, not an all-clear. Text search confirms the schema tokens and migration inventory below.

## Adopted OQ5 Option A — restated (do not re-open)

`verified by reading docs` ([partial-apply-owner-decision.md](partial-apply-owner-decision.md) §9; [partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md) §1; register OQ5; merged [#626](https://github.com/drgaciw/open-crm-cpq/pull/626) / [#631](https://github.com/drgaciw/open-crm-cpq/pull/631) / [#632](https://github.com/drgaciw/open-crm-cpq/pull/632)).

First slice only (ACTIVE SaaS subscription set under one contract; action codes Add / Update / Remove):

| ID | Contract | Source |
|---|---|---|
| **A1** | **Signature / execution is all-or-nothing.** The customer signs one document. There is no partial signature. Partial application is an **apply** outcome, not a partial execute. | Option A point 7; spike §4 rule 1 |
| **A2** | **Apply is per `ApplicationUnit`.** First-slice grouping: **one unit per target subscription**. A unit is atomic (all of its commercial rows in one transaction, or none). Units run in stored dependency order. | Option A points 1–2; FR-09 |
| **A3** | **No silent rollback.** A unit that has committed stays committed when a later unit fails. | Option A point 3; FR-12; AC-S4-3 |
| **A4** | Document **`PARTIALLY_APPLIED`** when ≥1 unit has committed and a later unit fails (failed unit **`BLOCKED`**). Document **`APPLY_BLOCKED`** when **no** unit has committed. All units committed → document **`APPLIED`**. | Option A points 4–5; rules note §1 table |
| **A5** | First-slice remedy is a **correcting amendment** (new document). **Rebase is not** a first-slice remedy. | Option A point 6; owner §9 |
| **A6** | `specs/partial-apply.md` is **not** accepted. Adoption of Option A **does not** by itself authorize S4 / DRG-582, S6, a ledger-table choice, or broader restructure / true-up / future-dated scenario sets. | Owner §9; rules note Decision condition |

**Cite; do not reopen.** This residual brief does not re-litigate A1–A6.

## What Option A deliberately left open (still residual)

`verified by reading docs` (owner-decision §8; rules note §5 / Decision condition; Linear hygiene comments 2026-10-16).

After the merges, DRG-565 remains **In Review** because first-slice **written create-before-terminate / `dependsOnUnitNos` authorship** stayed **UNMET**, and the broader **restructure / true-up / future-dated** scenario set stayed **DEFERRED** under DRG-565 and related stories. Persisting unit transitions as product apply work still belongs to S4 / DRG-582 (separately gated). This brief inventories those residuals as **Proposed OQ-565-\*** — it does not invent product states, run backfills, or start S4.

## What the tree does today (orientation only)

`verified by reading code` / `verified by reading docs` at the base SHA.

1. **Status names already exist** on `AmendmentDocumentStatus` / `chk_amendment_document_status` (`PARTIALLY_APPLIED`, `APPLY_BLOCKED`, …) and unit statuses `PENDING` / `APPLIED` / `BLOCKED` (`V1_7__contract_documents.sql`). Option A named how those labels cut for the first slice. That is **not** authorization to invent new product state machines or to treat Proposed DRG-637 open items as closed.
2. **CAM migrations present through V1_15** (including `V1_12__create_document_apply_ledger.sql` and `V1_15__document_apply_replay_evidence.sql`). Historical docs that reserved “V1_9 for `document_apply_ledger`” describe an earlier plan; this brief writes **no** new migration and does **not** claim Free Flyway slots. **CAM V1_16–V1_21 are reserved (#805–#812) — NO-GO.**
3. **Apply / asset paths and readiness holds** exist in CAM (`DocumentApplyOrchestrator` and related tests / DRG-637 reconciliation rows in `specs/partial-apply.md`). Those rows stay **`Status: Proposed`**. This brief does **not** authorize further S4 apply enablement, asset-write expansion, or closing DRG-637 OIs.
4. **S7 header backfill** (`contract_header_backfill_apply` / [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md)) remains a **manual owner step**. This brief does **not** authorize running it.

## Related Wave-2 drafts (cite only — do not adopt open OQs)

| PR / story | Role for this brief |
|---|---|
| [#821](https://github.com/drgaciw/open-crm-cpq/pull/821) / DRG-578 fulfilment decompose | Cite for revoke / fulfilment timing coupling on restructure units. **Do not adopt** its open OQs. |
| [#818](https://github.com/drgaciw/open-crm-cpq/pull/818) / DRG-569 capacity true-up | Cite for true-up commercial knobs that would feed true-up ApplicationUnits. **Do not adopt** its open OQs. |
| [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) / DRG-552 ChangeLine | Cite for action-code vocabulary that dependency derivation may read. **Do not adopt** its open OQs; do not reopen Adopted L1–L7 / OQ12. |
| [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) / DRG-553 dates | Cite for future-dated / effective vs action clocks that future-dated unit scenarios read. **Do not adopt** its open OQs; do not reopen Adopted D1–D6. |

## Remaining owner forks (Proposed OQ-565-\*)

Every row below is **Proposed, unaccepted, owner decision required**. Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, S4 enablement, S7 backfill, asset-write, inventing product `PARTIALLY_APPLIED` persistence beyond A4, or deploy. Product work that depends on these forks stays **NO-GO** until they are closed (or explicitly deferred).

| ID | Question | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|
| **OQ-565-1** | **Grouping `ApplicationUnit`s beyond first-slice A2** for mixed-line documents (ENTITLEMENT / ONE_TIME / NONE / multi-target) | Keep A2 for first-slice SaaS. Broader grouping stays the Proposed table in `specs/partial-apply.md` (true-up / restructure / mixed) until owner scores scenario forks OQ-565-5..7. Do **not** silently widen “one unit per subscription” to entitlement/one-time packs in product. | Adopt spike groupings as product now; one document = one unit always (reopens A2); invent a second grouping API beside ChangeLines | DRG-536; DRG-552/#834; DRG-572; specs/partial-apply (not accepted) |
| **OQ-565-2** | **Who writes `dependsOnUnitNos` / `dependency_order`** (create-before-terminate) | System derives order from action codes for first-slice Add/Update/Remove (create before terminate when both appear); seller may not reorder past safety. Written rule + acceptance conditions are a **docs prerequisite to S4**, not an apply-engine story by themselves. | Seller authors all edges; hybrid (system suggests, seller overrides); leave column unenforced | 536 GAP; rules note Decision condition 1; #834 action semantics |
| **OQ-565-3** | **Failure after partial commit — product persistence & recovery UX** | Keep A3/A4 cut. Do **not** invent additional product document/unit states. Durable apply that writes `PARTIALLY_APPLIED` / unit `BLOCKED` stays behind S4 authorization + closed residual forks. No compensating delete of committed units. | Auto-rollback committed units (forbidden by A3); new `PARTIALLY_APPLIED_*` subtypes; treat DRG-637 OI-rule5-zero (EXECUTED vs APPLY_BLOCKED) as closed without owner words | A3/A4; S4 / DRG-582; DRG-637 Proposed OIs |
| **OQ-565-4** | **Rebase vs correcting amendment** (post-first-slice) | Keep A5 for first slice (**correcting amendment only**). Rebase (re-observe / re-price / new revision of same document) stays **out** until a later owner fork with words + time; if ever allowed, re-approval on price change stays a separate sub-decision (spec OQ6). | Allow revision-only rebase on existing approval; allow priced rebase with re-approval; replace correcting amendment entirely with rebase | A5; OQ6 immutability of executed; DRG-551 |
| **OQ-565-5** | **Future-dated line scenarios** | Unit applies (records) at apply time; in-effect follows period start (Proposed AC-6). Ownership of executable tests stays deferred with date clocks from DRG-553/#835 — **cite only**, do not adopt 553 OQs. No product date-writer from this brief. | Collapse apply time = in-effect; refuse future starts at apply; invent a fifth header trigger | DRG-553/#835; DRG-646 fixtures (Proposed); A2 |
| **OQ-565-6** | **True-up scenarios** (capacity raise + one-time charge units) | Keep scenario **DEFERRED**. Grouping sketch (ENTITLEMENT then ONE_TIME) stays Proposed in `specs/partial-apply.md`. Commercial knobs live on DRG-569/#818; meter intent on DRG-557; ONE_TIME routing on DRG-572. **Do not adopt** #818 open OQs here. | Bundle entitlement+charge as one unit; price true-up inside SaaS UPDATE unit; start product true-up apply now | DRG-569/#818; DRG-557; DRG-572; A2/OQ-565-1 |
| **OQ-565-7** | **Restructure / dual-entitlement scenarios** (on-prem→SaaS) | Keep scenario **DEFERRED**. Create-before-terminate / dual-use window stay Proposed. Fulfilment revoke timing couples to DRG-559 / DRG-341 and fulfilment [#821](https://github.com/drgaciw/open-crm-cpq/pull/821) — **cite only**. First slice still excludes fulfilment. | Collapse restructure to one unit; revoke at create; start conversion apply now | DRG-559; DRG-341; DRG-578/#821; OQ-565-2 |
| **OQ-565-8** | **Interaction with DRG-536** (multi-line amendments) | Multi-line SaaS lines that share one target subscription remain **one** ApplicationUnit (A2). DRG-536 owns multi-line commercial shape; this brief does not reopen 536-F\* / 536-C\*. Create-before-terminate writer stays OQ-565-2 (was 536 GAP). | One unit per ChangeLine always; document-level all-or-nothing apply (reopens A2) | DRG-536 design note; A2 |
| **OQ-565-9** | **Interaction with DRG-551** (dry-run / revert) | Dry-run may **project** `APPLIED` / `PARTIALLY_APPLIED` / `APPLY_BLOCKED` labels using the A4 cut without updating the status column (551 design note). After any unit commits, recovery is **correcting amendment** (A5), not silent revert. DRG-551 does not authorize durable apply. | Allow revert of committed units; treat dry-run projection as persisted state; rebase-as-default recovery | DRG-551 design note; A3/A5; OQ6 |
| **OQ-565-10** | **Interaction with DRG-559** (+ fulfilment) | On-prem→SaaS restructure in one LifecycleChange stays a **later** mixed-document consumer of OQ-565-1/2/7. No dual-entitlement product path from this brief. Park fulfilment submission timing with #821 / conversions — cite only. | Force restructure into first-slice S4 acceptance now; dual-write assets without fulfilment evidence | DRG-559; DRG-341; #821; asset-lifecycle hold |
| **OQ-565-11** | **Failed-unit task queue / actor** | Raise a task on unit failure; queue and actor (seller vs contract admin) unset — park with work-queue / PL-9 style decisions; do **not** invent a queue in this brief. | Seller-only; admin-only; no task (status only) | Parking-lot PL-9; DRG-597 work queues (if present); A4 |
| **OQ-565-12** | **`NONE` units** (referenced perpetual / unchanged lines) | Omit from the persisted unit list in first slice (no no-op unit rows). If a later slice needs NONE for audit, store-and-skip under a separate owner fork. | Store and skip; require NONE for every referenced entitlement | Schema `apply_target_type`; specs OQ7 |
| **OQ-565-13** | **Document-level partial event / S6 surface** | No new S6 topic from this brief. Local CAM audit / existing apply decision events may record unit blocks; mapping of document `PARTIALLY_APPLIED` to a domain event name stays open and does **not** authorize S6. | Add `AmendmentPartiallyApplied` to S6 now; overload `ApplyBlocked` to mean partial | S6 / DRG-584; AC-S6-4 lists; A4 |
| **OQ-565-14** | **DRG-637 Proposed open items** (recovery replay, 409 width, asset call in unit tx, zero-applied EXECUTED vs APPLY_BLOCKED, …) | Leave every OI in `specs/partial-apply.md` § DRG-637 as **`Status: Proposed`**. Closing them needs owner words + time on that table — **not** implied by merging this residual brief. | Treat merged orchestrator behavior as de-facto Option A extension; rewrite AC1 without owner | DRG-637; #718/#741/…; specs/partial-apply |
| **OQ-565-IMPL** | **Implementation / migration / flag / backfill authorization** | Separate implementation story **after** OQ-565-1..14 (or a deferred subset) close. Additive CAM migrations only if needed; **never** consume reserved **V1_16–V1_21**; never edit landed V1_7 / V1_8 in place; **do not** run S7 backfill; **do not** invent product `PARTIALLY_APPLIED` states beyond A4; **do not** enable S4 apply / asset-write / dual-read / Jev / ramp from this brief. | Piggy-back migrations in V1_16–V1_21; run S7 now; flip apply flags before forks close | Owner release; S3/S4 gates; AGENT.md holds; #805–#812 reservation |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-565-1 — ApplicationUnit grouping beyond first slice

| Option | What the owner would be choosing |
|---|---|
| **A. Keep A2; defer broader groupings to scored scenario forks (Recommended, Proposed)** | Mixed ENTITLEMENT / ONE_TIME / restructure packs stay Proposed text until OQ-565-5..7 close. |
| **B. Adopt spike grouping table as product now** | Accepts unaccepted `specs/partial-apply.md` scenario rows without scenario owners. |
| **C. Whole-document single unit** | Reopens A2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-2 — `dependsOnUnitNos` authorship (create-before-terminate)

| Option | What the owner would be choosing |
|---|---|
| **A. System-derived safe order; written AC before S4 (Recommended, Proposed)** | Closes the UNMET prerequisite called out by #631 / rules note without shipping an apply engine in this story. |
| **B. Seller authors all edges** | Operator burden; still needs validation AC. |
| **C. Leave unenforced** | Blocks honest S4 acceptance for ordering. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-3 — Failure after partial commit (product)

| Option | What the owner would be choosing |
|---|---|
| **A. Persist A4 cut only under authorized S4; no new states (Recommended, Proposed)** | Honors A3/A4; refuses compensating deletes; leaves DRG-637 OIs Proposed. |
| **B. Auto-rollback committed units** | Forbidden by Adopted A3. |
| **C. New subtype statuses now** | Invents product states without owner fork close — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-4 — Rebase vs correcting amendment

| Option | What the owner would be choosing |
|---|---|
| **A. Correcting amendment only in first slice; rebase later (Recommended, Proposed)** | Restates A5; keeps executed immutability (OQ6 / DRG-551). |
| **B. Revision-only rebase on existing approval** | Spec open question 6. |
| **C. Priced rebase with mandatory re-approval** | Spec AC-12 path. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-5 — Future-dated scenarios

| Option | What the owner would be choosing |
|---|---|
| **A. Record-at-apply / in-effect-at-start; defer executable ownership (Recommended, Proposed)** | Couples clocks to DRG-553/#835 by cite only. |
| **B. Refuse future starts at apply** | Conflicts with Proposed AC-6 unless owner supersedes. |
| **C. Apply time = commercial in-effect** | Collapses dates; conflicts with Adopted separate clocks unless 553 owner supersedes. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-6 — True-up scenarios

| Option | What the owner would be choosing |
|---|---|
| **A. Defer; cite #818/#557/#572; do not adopt their OQs (Recommended, Proposed)** | Matches rules note DEFERRED set. |
| **B. Single combined true-up unit** | Weakens charge-failure PARTIALLY_APPLIED fixture (Proposed AC-5). |
| **C. Start product true-up apply in this lane** | **NO-GO** — commercial policy still open on DRG-569. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-7 — Restructure / dual-entitlement scenarios

| Option | What the owner would be choosing |
|---|---|
| **A. Defer; cite DRG-559 / DRG-341 / #821 (Recommended, Proposed)** | First slice excludes fulfilment. |
| **B. First-slice S4 must include restructure ACs** | Expands S4 beyond SaaS Add/Update/Remove. |
| **C. Revoke in the create unit** | Breaks dual-use window (Proposed AC-4). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-8 — DRG-536 interaction

| Option | What the owner would be choosing |
|---|---|
| **A. Shared-target lines share one unit; 536 owns multi-line shape (Recommended, Proposed)** | Preserves A2; leaves 536 forks on that story. |
| **B. One unit per ChangeLine** | Multiplies partial outcomes; needs explicit owner yes. |
| **C. Document all-or-nothing apply** | Reopens A2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-9 — DRG-551 interaction

| Option | What the owner would be choosing |
|---|---|
| **A. Projected labels only; committed units need correcting amendment (Recommended, Proposed)** | Aligns 551 design note with A3/A5. |
| **B. Dry-run writes durable status** | Contradicts 551 “status column not updated”. |
| **C. Revert committed units from 551** | Silent rollback — forbidden by A3. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-10 — DRG-559 interaction

| Option | What the owner would be choosing |
|---|---|
| **A. Later consumer of OQ-565-1/2/7; no product path here (Recommended, Proposed)** | Keeps conversions / fulfilment outside first slice. |
| **B. Require restructure ACs before any S4 start** | Over-gates SaaS first slice. |
| **C. Asset dual-write without fulfilment evidence** | Conflicts with fulfilment / asset holds. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-11 — Failed-unit task / actor

| Option | What the owner would be choosing |
|---|---|
| **A. Task required; queue/actor deferred (Recommended, Proposed)** | Spec open question 5 stays open without inventing a queue. |
| **B. Seller owns the queue now** | Needs work-queue Decision. |
| **C. Status-only; no task** | Weaker operator recovery. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-12 — NONE units

| Option | What the owner would be choosing |
|---|---|
| **A. Omit in first slice (Recommended, Proposed)** | Spec open question 7 default for slice. |
| **B. Store and skip** | Extra rows / replay surface. |
| **C. Require NONE for every referenced entitlement** | Heavy authoring. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-13 — Partial document event / S6

| Option | What the owner would be choosing |
|---|---|
| **A. No new S6 topic; local audit only for now (Recommended, Proposed)** | Matches “S6 not authorized” from Option A record. |
| **B. New partially-applied domain event now** | Starts S6 surface without S6 auth. |
| **C. Overload ApplyBlocked** | Collides with A4 cut. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-14 — DRG-637 Proposed open items

| Option | What the owner would be choosing |
|---|---|
| **A. Leave OIs Proposed; owner scores that table separately (Recommended, Proposed)** | Merging this brief does not close recovery/409/asset/zero-applied forks. |
| **B. Treat current orchestrator as Adopted Option A extension** | Silent product adoption — **NO-GO**. |
| **C. Rewrite AC1 expectations in product without owner words** | Same. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-565-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl after residual OQx close; no reserved slots; no S7; no inventing states (Recommended, Proposed)** | Hard NO-GO on product/schema/flag until then. |
| **B. Use CAM V1_16–V1_21 now** | Violates #805–#812 reservation. |
| **C. Run S7 / enable S4 apply / asset-write / flags now** | Explicitly out of scope. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

## Explicit product / schema / flag NO-GO

Until OQ-565-1..14 (or an owner-deferred subset) and OQ-565-IMPL close with words and a time:

1. **No** Java/TypeScript product change for partial-apply grouping, ordering writers, or new apply state machines.
2. **No** Flyway / schema change. **CAM V1_16–V1_21 reserved (#805–#812).** No edit in place of landed V1_7 / V1_8.
3. **No** inventing product `PARTIALLY_APPLIED` / `APPLY_BLOCKED` behaviors beyond Adopted A4 without a scored fork.
4. **No** feature-flag flip for S4 apply enablement, asset-write, dual-read, Jev (`opencrm.jev.*`), or ramp.
5. **No** running S7 `contract_header_backfill_apply` (or any environment backfill) from this story.
6. **No** accepting `specs/partial-apply.md` or closing DRG-637 OIs by side effect.
7. **No** adopting open OQs from Wave-2 drafts #821 / #818 / #834 / #835.
8. **No** marking Linear DRG-565 Done from this brief alone.

## Out of scope (explicit)

- Reopening Adopted OQ5 Option A (A1–A6) or merged [#626](https://github.com/drgaciw/open-crm-cpq/pull/626) / [#631](https://github.com/drgaciw/open-crm-cpq/pull/631) / [#632](https://github.com/drgaciw/open-crm-cpq/pull/632).
- Starting or “finishing” S4 / DRG-582, S6 / DRG-584, S3 / DRG-581, or S7 apply.
- Asset lifecycle enablement / write paths; fulfilment registry changes.
- Dual-read flip, NgRx, Jev enablement, ramp flag.
- Rewriting DRG-536 / DRG-551 design notes or Cluster frames.
- Merge authority for this file into `main` beyond draft PR review.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-565-1..14** and **OQ-565-IMPL** as the residual owner menu without re-litigating Adopted Option A. Accepting / merging this file **does not** open an implementation story, does not enable apply, does not run S7, and does not mark DRG-565 Done.

## References

- Linear: [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565/partial-application-rules-for-mixed-line-amendment-documents), [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536), [DRG-551](https://linear.app/drgamtd-workspace/issue/DRG-551), [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559), [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557), [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341), [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582), [DRG-637](https://linear.app/drgamtd-workspace/issue/DRG-637).
- GitHub (Adopted): [#626](https://github.com/drgaciw/open-crm-cpq/pull/626), [#631](https://github.com/drgaciw/open-crm-cpq/pull/631), [#632](https://github.com/drgaciw/open-crm-cpq/pull/632).
- GitHub (cite-only Wave-2 drafts): [#821](https://github.com/drgaciw/open-crm-cpq/pull/821), [#818](https://github.com/drgaciw/open-crm-cpq/pull/818), [#834](https://github.com/drgaciw/open-crm-cpq/pull/834), [#835](https://github.com/drgaciw/open-crm-cpq/pull/835).
- Docs: [partial-apply-owner-decision.md](partial-apply-owner-decision.md); [partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md); [specs/partial-apply.md](specs/partial-apply.md); register OQ5; [story-drg-536-multi-line-amendment-design-note.md](story-drg-536-multi-line-amendment-design-note.md); [story-drg-551-dry-run-and-revert-design-note.md](story-drg-551-dry-run-and-revert-design-note.md); [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md).
- Schema (read-only): `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql` (and inventory through V1_15).
- Holds: [AGENT.md](../../../AGENT.md); dual-read / Jev / ramp / S4; CAM Flyway V1_16–V1_21 reservation (#805–#812).
