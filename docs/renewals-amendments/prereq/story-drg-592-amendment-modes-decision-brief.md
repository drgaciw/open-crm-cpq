# DRG-592 — Amendment modes: new-contract (preferred) and amend-in-place (Decision brief)

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592/amendment-modes-new-contract-preferred-and-amend-in-place-stored) (RA-58). Call decisions 2026-10-16 (#1, #2, #3).

**Status:** Proposed Decision brief. This file records what the 2026-10-16 call and the 2026-10-17 delegated record already adopted for amendment modes, maps the landed schema on `main`, and surfaces **remaining owner forks as OQx**. **It authorizes no production code path, no schema change, no Flyway migration, no `precedence_flag` column, no `amendment_mode` column, no flag flip, no route, no Kafka consumer registration, and no deploy.** Apply stays after `EXECUTED` (OQ2). S1–S8 stay amend-in-place only. S3 / DRG-581 stays unstarted. Dual-read, Jev, and ramp holds are untouched. Product code / schema / flag / apply for new-contract mode is **NO-GO** until the owner closes the residual OQx below (or explicitly defers a named fork with words and a time).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (do not contradict)

| Artifact | Relation to this brief |
|---|---|
| Call 2026-10-16 (#1–#3) on [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592) | **Adopted** policy: two modes; new-contract preferred/default; stored precedence flag; original stays open as reference. Cite; do not reopen. |
| [cluster-a-amendment-modes-and-policy-decision-frame.md](cluster-a-amendment-modes-and-policy-decision-frame.md) §1 / [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) | Historical forks **592-F1..F6** and recommended defaults. Retained as rationale. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) Cluster A disposition | **Adopted** 592-F1..F6 baseline defaults. Do not re-litigate as blank owner slots. |
| OQ26 / OQ27 in [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) | **RESOLVED 2026-10-16**: amend-in-place first (S1–S8); new-contract next increment; `precedence_flag` is a separate indexable column outside the ADR-035 thin header. |
| [adr-035-amendment-document-placement.md](../adr-035-amendment-document-placement.md) | **Accepted** for S1/S2 placement. Thin header list unchanged. Later stories need their own gates. Apply-after-EXECUTED remains. |
| [amendment-entity-field-schema.md](../amendment-entity-field-schema.md) §2.2a + clarification 2026-10-16 | Documents stored-flag vs derived-precedence conflict; sketches `precedence_flag` (shape note below). Not a landed column. |
| [specs/spine-dependent.md](specs/spine-dependent.md) § DRG-592 + fixtures | Proposed ACs AC-592-1..4. Blank `Owner decision` slots there are **stale relative to the delegated disposition** for 592-F1..F6; this brief does not accept that spec as an ADR. |
| [s1-v1-7-contract-documents.md](../s1-v1-7-contract-documents.md) / `V1_7__contract_documents.sql` | Landed: `prior_contract_id`, no `precedence_flag`, no `amendment_mode`. |
| Related: DRG-535, DRG-561, DRG-579, DRG-581, DRG-648 | Document entity / placement / S1 / S3 / spine-dependent umbrella. This brief does not start them. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Accepted / adopted / Proposed docs cited in Alignment. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had no usable local index (`.gitnexus/run.cjs` absent; MCP `list_repos` empty). Graph impact for docs-only is **UNKNOWN**, not an all-clear. Text search confirms the schema tokens below.

## Call decision (already on the issue)

`verified by reading the issue` (Linear DRG-592 description and comments of 2026-10-16).

1. An amendment supports two modes: **new contract** (preferred, default) and **amend in place**.
2. New-contract mode: the amendment creates a new contract. Both the original and the new contract stay **ACTIVE**. A **stored precedence flag on the new contract** says it takes precedence over the original (flag is **persisted** on the contract record, **not derived** from amendment history).
3. The original contract stays **open as a reference**; it is not closed out or terminated by the amendment.

## Clarifications / conflicts with earlier docs (cite, do not reopen)

`verified by reading docs` and `verified by reading the issue`.

| Conflict | Resolution already on record |
|---|---|
| Spec / schema / spike / S1–S8 model amend-in-place only; RCA research inferred **derived** precedence | Call #2 replaces derived with a **stored** flag. Field-schema clarification 2026-10-16 records the same. |
| First slice vs preferred new-contract | **OQ26 RESOLVED**: both modes eventually coexist; S1–S8 (DRG-579..586) stay amend-in-place; new-contract is the **next increment**, not a first-slice blocker. Pulling new-contract into slice 1 remains a **product call** residual (**OQ-592-SLICE** below) — default stays “next increment”. |
| Thin header vs flag column | **OQ27 / C2 RESOLVED**: `precedence_flag` is a **separate, indexable column** on the contract record, **not** part of the ADR-035 thin header. Arrives with this story, not S1. |
| Spine-dependent blank owner slots for 592-F1..F6 | Delegated Cluster A disposition **adopted** those baselines. This brief restates them under Adopted; it does not reset them to open. |

## Adopted (do not re-open as owner forks)

### A. Call + register (2026-10-16)

| ID | Contract | Source |
|---|---|---|
| A1 | Two amendment modes: `NEW_CONTRACT` (preferred/default) and `AMEND_IN_PLACE` | Call #1 |
| A2 | New-contract creates a new contract; both stay `ACTIVE`; stored precedence flag on the **new** contract; original stays open as reference (not closed/terminated by the amendment) | Call #2, #3 |
| A3 | First slice (SaaS add/update/remove) stays **amend-in-place**; new-contract is next increment, not S1–S8 | OQ26 |
| A4 | `precedence_flag` is a separate indexable column outside the ADR-035 thin header; thin header list unchanged | OQ27 / C2 |
| A5 | Apply remains **after `EXECUTED`** (single status column; no `apply_status` in v1) | OQ2; ADR-035 context |

### B. Delegated Cluster A baselines (2026-10-17)

From the [delegated record](delegated-renewals-amendments-decisions-2026-10-17.md) Cluster A disposition index (`verified by reading docs`). Linear “Open” bullets that named these topics are **closed as open questions** by that disposition; this brief does not reopen them.

| Fork | Adopted disposition (summary) |
|---|---|
| **592-F1** Flag shape | Boolean `precedence_flag`, not null, default false. Amend-in-place leaves it false. New-contract establishes the initial true flag on the new contract only at executed commercial apply. |
| **592-F2** Who may change the flag | Initial winner at executed apply. Later change = audited correcting amendment: lock chain, verify expected winner, clear old / set new, audit row; two true flags refuse. No sales flip without a correcting document. |
| **592-F3** Overlap for pricing and visibility | Precedence-true contract is the pricing source within the `prior_contract_id` chain; both contracts stay visible; two true flags in one chain fail closed (`PRECEDENCE_CONFLICT`). Entitlement resolution is **not** decided here (pending DRG-609). |
| **592-F4** Original at its own end date | Stays `ACTIVE` as a reference until its own end date, then may become `EXPIRED`. Amendment does not set `TERMINATED` at creation. |
| **592-F5** Where the mode is stored | Column on the amendment document (`amendment_mode`), in a **later increment**, not S1. Until then, documents are amend-in-place because OQ26 says so. |
| **592-F6** Which contract owns the document | The **new** contract owns the one document; `prior_contract_id` points at the original. No second document on the original. |

## What the tree does today

`verified by reading code` at the base SHA.

1. **`V1_7__contract_documents.sql`.** Creates `contracts` with `prior_contract_id uuid` and `chk_contracts_status` ∈ {`ACTIVE`, `EXPIRED`, `TERMINATED`}. Migration header comment: expand-only; **no `precedence_flag` (DRG-592)**. `amendment_documents.contract_id` is required FK to `contracts`. No `amendment_mode` column. Companion: [s1-v1-7-contract-documents.md](../s1-v1-7-contract-documents.md).
2. **No precedence writers.** A search of `backend/services/contract-asset-management/src/main/resources/db/migration` for `precedence_flag` / `amendment_mode` finds the V1_7 comment only (`verified by reading code`). No landed Flyway adds the flag.
3. **Event writer.** `AmendmentEventWriter` appends hash-chained events; `event_type` is unconstrained varchar. No precedence-transfer event type is enumerated (`verified by reading docs` / Cluster A frame).
4. **Fixtures (Proposed only).** Four spine-dependent JSON fixtures under `prereq/specs/fixtures/spine-dependent/drg-592-*.json` encode AC-592-1..4 shapes. They authorize no runtime behavior.

## Remaining owner forks (OQx)

Every row below is **Proposed, unaccepted, owner decision required**. Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, or deploy. These are the open questions that **survive** Adopted A1–A5 and 592-F1..F6. Product code for new-contract mode stays **NO-GO** until they are closed (or explicitly deferred).

| ID | Question | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|
| **OQ-592-1** | **Entitlement** resolution when two `ACTIVE` contracts overlap in a precedence chain | Leave entitlement outside this story until the DRG-609 design is accepted; pricing/visibility keep 592-F3. Do not invent a second winner rule for fulfilment grants. | Define entitlement = precedence-true now; or reader-specific entitlement (592-F3 option C); or always original until EXPIRED. | DRG-609 design acceptance; Cluster F fulfilment. |
| **OQ-592-2** | **LifecycleChange / authoring wire** for mode selection (quote-service) vs CAM `amendment_mode` | Mode chosen on LifecycleChange at create; CAM stores the durable `amendment_mode` on the document (592-F5). Quote-service does not become the system of record for mode after EXECUTED. | Mode only on LifecycleChange (no CAM column); or mode only inferred from insert (592-F5-B); or UI-only until apply. | S3 / DRG-581 auth; ADR-035 D-03 (LC in quote-service). |
| **OQ-592-3** | **When** the new `contracts` row and initial `precedence_flag=true` are written relative to EXECUTED / apply | Insert new contract + set flag in the **same commercial-apply transaction** after EXECUTED (matches 592-F1 “at executed commercial apply”); refuse if document is not EXECUTED. | Create contract at DRAFT; set flag at EXECUTED; or set flag in a post-apply job. | Apply gate (A5); S4/S6 not authorized by this brief. |
| **OQ-592-4** | Effect on **V-rules** for `NEW_CONTRACT` (beyond amend-in-place V1–V14) | Keep V1–V14 for the document spine; add a narrow gate set later: mode present, `prior_contract_id` target ACTIVE, no second true flag in chain, document `contract_id` = new contract. Do not rewrite ADR-035 placement. | Fork a parallel V-rule table for new-contract; or reuse amend-in-place V-rules with zero deltas. | Mid-term policy spec; S3 V-rule delivery. |
| **OQ-592-5** | **Audit / event** contract for precedence establishment and transfer | Append CAM hash-chain + audit-outbox events (`PRECEDENCE_ESTABLISHED` / `PRECEDENCE_TRANSFERRED` names provisional) with old/new winner, actor, document id; no schema-registry topic until a separate event story. | Admin column flip with a single audit row (contradicts 592-F2); or silent flag write. | DRG-580 writer; schema-registry / outbox stories; 592-F2. |
| **OQ-592-6** | **`subscriptions.contract_id` retarget** when new-contract mode applies | Retarget affected subscription headers to the new contract in the same apply transaction that sets precedence; original header stays as reference (OQ7 one-thin-contract rule stays; this is not S7 regrouping). | Leave subscriptions on the original until a later merge (DRG-554); or dual-point both contracts. | OQ7 / S7; DRG-554 merge follow-ups; apply authorization. |
| **OQ-592-7** | Who / what flips the original to **`EXPIRED`** at its own end date | Separate CAM term-end / expiry job (or existing term-end clock); amendment path never writes EXPIRED. Job is delivery work, not authorized here. | Never expire (592-F4-B); terminate at new-contract creation (contradicts A2 / 592-F4). | Term-end / renewal clocks (DRG-555); no new status enum. |
| **OQ-592-8** | **Index** shape for `precedence_flag` | Partial index on `(tenant_id)` where `precedence_flag = true` (plus chain reads via `prior_contract_id`). Exact DDL in the later migration story. | Full `(tenant_id, precedence_flag)` btree; or no index until measured. | Later Flyway (not V1_7); OQ27. |
| **OQ-592-9** | Visibility detail with **DRG-595** when both contracts stay visible | Keep 592-F3 “both visible”; DRG-595 owns queue/list filters and read-only roles. This brief does not design CRM list UX. | Hide original from sales lists; or show only precedence-true in all UIs. | DRG-595 Decision; Cluster G. |
| **OQ-592-SLICE** | Pull **new-contract into first slice** (S1–S8)? | **No** — keep OQ26: next increment after amend-in-place. Product may reopen only with explicit words and a time. | Yes — block S8 until new-contract + flag ship; or optional flag behind a feature switch in slice 1. | Product call; S1–S8 scope; DoD. |
| **OQ-592-IMPL** | Implementation / migration authorization for flag + mode columns | Separate implementation story after OQ-592-1..9 (or deferred subset) close; additive Flyway **after** reserved S4 slot rules in the delegated storage contract; never edit landed V1_7/V1_8. | Piggy-back on an S3 migration; or alter V1_7 in place (forbidden). | Owner implementation release; D-STORAGE migration rules. |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-592-1 — Entitlement under overlap

| Option | What the owner would be choosing |
|---|---|
| **A. Defer to DRG-609 (Recommended, Proposed)** | Pricing/visibility follow 592-F3. Fulfilment / entitlement grants wait on accepted DRG-609 design. |
| **B. Entitlement = precedence-true now** | Fulfilment reads the same winner as pricing before 609 lands. |
| **C. Reader-specific** | Pricing, entitlement, and visibility each pick independently (Cluster A option C). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-2 — Mode selection surface

| Option | What the owner would be choosing |
|---|---|
| **A. LC chooses; CAM stores (Recommended, Proposed)** | Matches ADR-035 D-03 (LC in quote-service) and 592-F5 (column on document). |
| **B. CAM-only column** | Authoring has no mode until document create in CAM. |
| **C. Infer from insert** | No durable mode token (592-F5-B). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-3 — Timing of new contract + flag write

| Option | What the owner would be choosing |
|---|---|
| **A. At commercial apply after EXECUTED (Recommended, Proposed)** | Aligns with A5 and 592-F1. Draft/EXECUTED without apply creates no second ACTIVE winner. |
| **B. At EXECUTED** | Flag before apply; risk of ACTIVE winner with unapplied commercial change. |
| **C. At DRAFT** | Two ACTIVE contracts before legal execution. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-4 — V-rules / ADR-035 placement

| Option | What the owner would be choosing |
|---|---|
| **A. Spine V-rules + narrow new-contract gates (Recommended, Proposed)** | ADR-035 D-01/D-02 stay. No thin-header change. Apply still after EXECUTED. |
| **B. Parallel V-rule set** | Duplicate maintenance cost. |
| **C. Zero deltas** | Rely only on amend-in-place rules; miss mode/chain invariants. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-5 — Precedence audit events

| Option | What the owner would be choosing |
|---|---|
| **A. Hash-chain + outbox types, names provisional (Recommended, Proposed)** | Matches 592-F2 audited transfer; schema-registry later. |
| **B. Column flip + single audit** | Weakens correcting-document rule. |
| **C. Silent write** | Fails commercial-decision audit expectations. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-6 — Subscription retarget

| Option | What the owner would be choosing |
|---|---|
| **A. Retarget in apply transaction (Recommended, Proposed)** | One thin pointer to the precedence winner; original remains reference via `prior_contract_id`. |
| **B. Leave on original** | Pointer lag vs commercial winner; needs later merge. |
| **C. Dual pointers** | Breaks OQ7 one-header-per-subscription shape. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-7 — Original → EXPIRED

| Option | What the owner would be choosing |
|---|---|
| **A. Separate expiry / term-end path (Recommended, Proposed)** | Amendment never expires the original. |
| **B. Never expire** | Reference stays ACTIVE forever. |
| **C. Terminate at creation** | Contradicts call #3 / A2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-8 — Index DDL

| Option | What the owner would be choosing |
|---|---|
| **A. Partial index on true rows (Recommended, Proposed)** | Cheap “who wins” filter; exact DDL in impl story. |
| **B. Full composite btree** | Simpler DDL, larger index. |
| **C. Defer index** | Accept full scans until measured. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-9 — Visibility with DRG-595

| Option | What the owner would be choosing |
|---|---|
| **A. Both visible; 595 owns filters (Recommended, Proposed)** | Keeps 592-F3. |
| **B. Hide original in sales lists** | Product UX call on 595. |
| **C. Only precedence-true everywhere** | Stronger than 592-F3 “both visible”. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-SLICE — First-slice pull-in

| Option | What the owner would be choosing |
|---|---|
| **A. Keep next increment (Recommended, Proposed)** | OQ26 stands. |
| **B. Pull into S1–S8** | Blocks slice closure on flag + mode + apply. |
| **C. Feature-flagged optional in slice 1** | Still a schema + apply change inside the slice. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-592-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl story after residual OQx close (Recommended, Proposed)** | Additive migration only; V1_7/V1_8 immutable. |
| **B. Fold into S3 migration** | Couples unstarted S3 to new-contract. |
| **C. Edit V1_7** | Forbidden expand-only rule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

## Out of scope (explicit)

- Any Java/TypeScript change, Flyway migration, feature flag, workflow, or gateway route.
- Adding `precedence_flag` or `amendment_mode` to a migration.
- Starting S3 / DRG-581, authorizing S4/S6, or changing S1–S8 to new-contract.
- Accepting [specs/spine-dependent.md](specs/spine-dependent.md) as an ADR or filling its blank owner slots in that file.
- Re-opening call #1–#3, OQ2, OQ26, OQ27, ADR-035 D-01/D-02, or delegated 592-F1..F6.
- Dual-read flip, Jev enablement, ramp flag, or S7 regrouping.
- Marking Linear DRG-592 Done.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-592-1..9**, **OQ-592-SLICE**, and **OQ-592-IMPL** as the residual owner menu without re-litigating call #1–#3 or 592-F1..F6. Accepting / merging this file **does not** open an implementation story, does not add a column, does not change apply, and does not pull new-contract into the first slice.

## References

- Linear: DRG-592, DRG-535, DRG-561, DRG-579, DRG-581, DRG-595, DRG-609, DRG-648, DRG-554.
- GitHub: [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) (Cluster A frame, merged).
- Schema (read-only): `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`.
- Docs: Cluster A frame; delegated 2026-10-17 record; field-schema §2.2a; ADR-035; OQ register OQ2/OQ26/OQ27; spine-dependent § DRG-592 + fixtures.
- Holds: [AGENT.md](../../../AGENT.md); first-slice amend-in-place; dual-read / Jev / ramp.
