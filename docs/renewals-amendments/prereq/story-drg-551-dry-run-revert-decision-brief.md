# DRG-551 — Dry run and revert for lifecycle changes: Decision brief (owner forks)

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** accept or reopen closed cells in [`story-drg-551-dry-run-and-revert-design-note.md`](story-drg-551-dry-run-and-revert-design-note.md) ([#667](https://github.com/drgaciw/open-crm-cpq/pull/667)), does **not** reopen Adopted Cluster C **F9** / **G4** / **G5**, Adopted OQ5 option A, Adopted OQ6, Adopted OQ7, D-REVISION (no `VOID` mutation), or the delegated pricing contract that pure preview is not a confirmed commercial price, does **not** adopt open OQs from in-flight Wave-2 residual Decision briefs (DRG-562 / 563 / 565 / 569 / 564 / 560 / 594), does **not** authorize S3 / S4 / S5 / S6 product landing, does **not** enable the S4 apply endpoint ([DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625)), does **not** bypass the asset-write hold, authorize product code, open a Flyway migration (CAM `V1_16`–`V1_21` stay reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), flip a flag, register a topic, enable dual-read, enable Jev, enable ramp, or deploy. Every residual fork below is `Status: Proposed` and labeled **Proposed OQ-551-***. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / dry-run and revert (RA-17). **Story:** [DRG-551](https://linear.app/drgamtd-workspace/issue/DRG-551/dry-run-and-revert-for-lifecycle-changes-versioned-subscriptions) (RA-17). Linear read this pass: **In Review**. Notion: [RA-17](https://app.notion.com/p/3fbf7cb4e4df8138b578db73b5e9643a) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen closed / Adopted cells):**

| Artifact | Role |
|---|---|
| [`story-drg-551-dry-run-and-revert-design-note.md`](story-drg-551-dry-run-and-revert-design-note.md) ([#667](https://github.com/drgaciw/open-crm-cpq/pull/667), merged) | **Cite-only design note.** Proposed defaults for platform dry-run output, revert-without-deletion, and audit rows (forks D1–D14). Status on that file remains Proposed / unaccepted / owner decision required. This residual brief inventories still-open forks as **Proposed OQ-551-***; it does **not** reopen Adopted cells the note cites (Cluster C F9/G4/G5, OQ5 A, OQ6, OQ7, Accepted proration, Accepted uplift D-04, D-REVISION, delegated pricing preview rule). |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Cluster C F9 / G4 / G5 | **Adopted (delegated):** true-up dry-run required before customer-visible; freeze at dry-run completion; correction follows OQ6. F9 text says DRG-551 is the platform story and that default does **not** start it. Cite; do **not** reopen. |
| [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) (#642) | **Adopted** mid-flight / executed immutability. Cite for post-`EXECUTED` remedy; do **not** reopen. |
| [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) / register OQ5 A | **Adopted** first-slice partial-apply cut: no automatic rollback; correcting amendment as remedy; rebase not first-slice. Cite; do not reopen. |
| Register OQ7 thin contract / [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md) | **Adopted** header-only first slice. Header-backfill count is **not** the lifecycle dry-run. Cite; do not reopen. |
| Delegated D-REVISION / pricing preview rule | **Adopted:** no `VOID` mutation in the slice; pure preview cannot be presented as a confirmed commercial price; no rewrite of earlier digests / executed commercial facts. Cite; do not reopen. |
| [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md) | S3–S8 remain implementation-gated; S4 / DRG-582 stays owner-authorized separately. |
| [`DRAFT-openapi-amendments.yaml`](DRAFT-openapi-amendments.yaml) | Proposal only (`x-opencrm-status: proposal`). OpenAPI sentence "A dry run is not persisted and has no amendment document" is orientation for OQ-551-2 — not an accepted API. |

**In-flight alignment (cite only — do not adopt open OQs):**

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) residual Decision brief | [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) draft | Executed-state apply gate. **Cite only.** Do not adopt open OQ-562-*. |
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) residual Decision brief | [#840](https://github.com/drgaciw/open-crm-cpq/pull/840) draft | Apply-ledger home / digests. **Cite only.** Do not adopt open OQ-563-*. |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) residual Decision brief | [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) draft | Partial-apply ordering. Cite OQ5 A; do not reopen. |
| [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) / Cluster C true-up | [#818](https://github.com/drgaciw/open-crm-cpq/pull/818) draft | True-up policy dry-run pins. F9/G4 adopted at cluster level; do not start F9 implementation here. |
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) residual Decision brief | [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) draft | Continuation pricing audit. Cite; do not widen `commercial_decision_audit` for dry-run. |
| [DRG-560](https://linear.app/drgamtd-workspace/issue/DRG-560) / [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) | [#855](https://github.com/drgaciw/open-crm-cpq/pull/855) / [#833](https://github.com/drgaciw/open-crm-cpq/pull/833) drafts | Approvals / legal-review. Cite; do not invent approval dry-run shape here. |
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) (S4) | In Review | Document-driven apply. Remains **owner-gated**. Landed `V1_12` ledger is not S4 Done. |
| [DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) | Related | Apply endpoint enablement. **NO-GO** here. |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) | Related | Pricing continuation design — not wholesale-accepted; entitlement / conversion price stay omitted on the platform dry-run shape. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply endpoint enablement (DRG-625), asset-write hold bypass, or deploy.** CAM Flyway `V1_16`–`V1_21` remain reserved (#805–#812). An implementation brief that ships a lifecycle `dryRun` route, a `/reversible` or `/{version}/revert` endpoint, a subscription version column, rate-period close-on-revert, or a deletion/compensate run is **blocked** until CPQ Decision scores and the owner closes the Proposed OQ-551-* forks below (or explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-551-* row stays open.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted` | Already selected outside this file (delegated record / OQ6 / OQ5 A / Cluster C F9·G4·G5 / …). Restated; **not** re-opened. |
| `#667 Proposed default` | Default written in the merged design note. Still Proposed / unaccepted there. Cited for scoring; **not** adopted by this residual brief. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Problem (RA-17)

There is still no owner-scored residual Decision brief that (a) separates **already Adopted** true-up dry-run / freeze / OQ6 / OQ5 / no-`VOID` / preview-is-not-confirm contracts from **remaining platform delivery forks**, and (b) keeps the #667 design-note defaults (output shape, non-persistence, revert-without-deletion, proposed refusal audit token) as **Proposed OQ-551-*** without shipping DealHub `/reversible` / `/{version}/revert`, without closing applied rate periods by rewrite, and without claiming CAM Flyway slots in the reserved `V1_16`–`V1_21` band.

Linear DRG-551 (`Linear read` 2026-11-06): Medium; **In Review**; attachment design note [#667](https://github.com/drgaciw/open-crm-cpq/pull/667). Related: DRG-562, DRG-560, DRG-566, DRG-594, DRG-563, DRG-536, DRG-565, DRG-547. Linear comments (`Linear read`) corrected Done→**In Review** after the docs-frame merge: a merged design note is not the story deliverable.

## Why this residual Decision brief exists

[#667](https://github.com/drgaciw/open-crm-cpq/pull/667) merged the DRG-551 design note. Wave-2 refill (`Linear read` 2026-11-06) assigns this docs-only residual brief to inventory still-open **DRG-551** forks as **Proposed OQ-551-*** for CPQ Decision — without reopening #667 closed already-decided citations, without adopting sibling Wave-2 open OQs, and without authorizing product / schema / flag work.

## Already Adopted (do not re-decide)

Restated from the delegated record, OQ register, and #667 citations (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 (F9) | True-up cannot enter a customer-visible state until a dry-run has completed for that snapshot id, content hash, and policy version; same pins return the same amounts. F9 does **not** start DRG-551 | **Adopted (delegated)** Cluster C F9 |
| A2 (G4) | Snapshot freezes when that dry-run completes | **Adopted (delegated)** Cluster C G4 |
| A3 (G5) | Correction after freeze follows OQ6 (new revision pre-execute, or new correcting amendment post-execute) | **Adopted (delegated)** Cluster C G5 |
| A4 (OQ5 A) | First-slice: one unit per target subscription, atomic units, **no automatic rollback**, `PARTIALLY_APPLIED` / `APPLY_BLOCKED` cut, **correcting amendment** as remedy; rebase is not first-slice | **Adopted** register OQ5 / `partial-apply-owner-decision.md` §9 |
| A5 (OQ6) | Pre-execution: new revision under same provisional number, prior `SUPERSEDED`. Post-`EXECUTED` (any apply state): immutable; 409 `IMMUTABLE_EXECUTED`; remedy is a new LifecycleChange / correcting document. Terminals stay terminal | **Adopted** OQ6 / #642 |
| A6 (D-REVISION) | No `VOID` mutation is introduced in the slice | **Adopted (delegated)** D-REVISION |
| A7 (pricing preview) | Pure preview output cannot be presented as a confirmed commercial price | **Adopted (delegated)** pricing contract |
| A8 (authority) | No earlier digest or executed commercial fact may be rewritten to fit a new design | **Adopted (delegated)** authority section |
| A9 (OQ7) | Header-only thin contract backfill; S7 count/apply is not the lifecycle dry-run | **Adopted** OQ7 |
| A10 | Holds: no product code / schema / flag from docs alone; CAM **V1_16–V1_21** reserved (#805–#812); dual-read / S4 enablement / S7 phased run / asset-write / Jev / ramp holds untouched; S3–S8 need separate implementation authorization | AGENT.md; delegated record; #805–#812 |

## What #667 already proposed (cite — do not reopen Adopted cells; do not adopt these defaults here)

From `read` `story-drg-551-dry-run-and-revert-design-note.md`. Every row below remains **Proposed, unaccepted, owner decision required** on that note. This residual brief does **not** flip any of them to Adopted. Residual scoring uses **OQ-551-*** ids.

| #667 fork | Proposed default (still unaccepted) | Residual id |
|---|---|---|
| D1 Output shape | Field table: `dryRun`/`persisted`/`confirmedPrice`/subject/pins/`trueUp` (F9 fields when true-up)/projection units & rate-period labels/`audit` | OQ-551-1 |
| D2 Persistence | Not persisted; no amendment document | OQ-551-2 |
| D3 S2 chain on dry-run | No chain row and no outbox row | OQ-551-3 |
| D4 Repeat | Same snapshot id / policy version / content hash → same amounts; no CAM idempotency receipt | OQ-551-4 |
| D5 Entitlements / conversion price | Omit both; other amounts stay `confirmedPrice: false`; pending DRG-609 | OQ-551-5 |
| D6 True-up overlap | Include adopted F9 fields on true-up subjects; leave G4/G5; do **not** start F9 implementation | OQ-551-6 |
| D7 Revert meaning | Read-only reversible check + correcting LifecycleChange after `EXECUTED` / committed unit; leave rows in place; deletion owner-only; no rate-period rewrite | OQ-551-7 |
| D8 Data deletion | Leave rows in place; no `contract_header_backfill_compensate()` as lifecycle revert | OQ-551-8 |
| D9 Unapplied change | OQ6 rule 1 pre-execute; OQ6 rule 2 post-`EXECUTED`; no `VOID`; no delete | OQ-551-9 |
| D10 After a unit commits | Correcting amendment only; committed units stay | OQ-551-10 |
| D11 Version identity | Cite existing `subscriptions.commercial_revision`; no new column | OQ-551-11 |
| D12 Refusal audit | `AMENDMENT_REVISION_REJECTED` stays **PROPOSED**; S2 writer does not define it; reversible check writes nothing | OQ-551-12 |
| D13 Dry-run audit | No audit row; response carries `audit.chainAppended: false` | OQ-551-13 |
| D14 S4 inside dry-run | Dry-run does not call apply; testable no-write property over listed tables | OQ-551-14 |

**Closed / Adopted cells #667 cites and this brief does not reopen:** Cluster C F9 / G4 / G5; OQ5 option A; OQ6; OQ7; Accepted proration formula; Accepted uplift D-04; D-REVISION no `VOID`; delegated preview≠confirm; authority no-rewrite.

## Landed evidence on `main` (not new authorization)

Facts `verified by reading code` / directory listing at the base SHA. They show orientation already partially present. They do **not** close Proposed OQ-551-* and do **not** authorize a lifecycle dry-run route, revert endpoint, or S4 Done.

| Fact | Evidence |
|---|---|
| `subscriptions.commercial_revision` exists (`BIGINT NOT NULL DEFAULT 0`) | `V1_6__subscription_rate_schedule.sql` |
| `subscription_rate_periods` has `effective_end_date`, `applied`, `retired`, `is_deleted`, `version` | Same file |
| Amendment documents / revisions / lines / application units / append-only `amendment_document_events` | `V1_7__contract_documents.sql` |
| S2 writer appends hash-chained events + audit outbox in caller txn; unknown document throws | `AmendmentEventWriter` |
| Outbox publication type `AMENDMENT_AUDIT_RECORDED_V1` (relay not enabled by class comment) | `AmendmentAuditOutbox` / `EventContractRegistry` |
| OpenAPI draft: `dryRun` defaults false; "A dry run is not persisted and has no amendment document" | `DRAFT-openapi-amendments.yaml` (proposal) |
| `document_apply_ledger` **now exists** on this `main` (unique `(document_id, unit_no)`, digest replay) | `V1_12__create_document_apply_ledger.sql` — **newer than #667's "no V1_9 / no ledger" wording**; table presence is not S4 Done and does not authorize dry-run to write it |
| Apply replay evidence migration present | `V1_15__document_apply_replay_evidence.sql` |
| CAM tip on this `main` ends at / through `V1_15`; **V1_16–V1_21 reserved** by sibling CAM drafts | migration directory listing; #805–#812 |
| Search of `backend/` `*.java` / `*.yml` / `*.yaml` / `*.sql` for lifecycle `dryRun` / `isDryRun` / `reversible` / `/revert` hits AI policy dry-run test and dead-letter `--dry-run` only — no lifecycle dry-run or revert type | `rg` this pass; differently named methods can miss. **Unverified** beyond those tokens |

### GitNexus (this checkout)

GitNexus MCP reports **no indexed repositories** this pass (`impact` / `query` unavailable — error: "No indexed repositories. Run: gitnexus analyze"). Local CLI fallback `.gitnexus/run.cjs` is **absent**. Treat blast radius as **UNKNOWN** — not a clean check (`partial` / empty / UNKNOWN must not be read as unaffected). Docs-only change: one new markdown file under `docs/renewals-amendments/prereq/`. A later implementation must run GitNexus `impact` before adding a dry-run route, reversible/revert command, `AMENDMENT_REVISION_REJECTED` constant, subscription version column, or any rate-period close-on-revert writer.

## Implementation brief gate (explicit) — product / schema / flag NO-GO

1. This Decision brief closes **nothing** by itself. Options are Proposed **OQ-551-***.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, CAM Flyway (including any use of reserved `V1_16`–`V1_21`), dual-read flips, Jev flags, ramp changes, S4 hold lifts, DRG-625 apply-endpoint enablement, asset-write hold bypass, lifecycle dry-run persistence, or revert-by-deletion / rate-period rewrite for DRG-551.
4. Even after forks close: S3 / S4 / S5 / S6 still need their **own** owner authorization records. Landed `V1_12` ledger / orchestrator fragments are not S4 Done and do not authorize dry-run writers.
5. Citing Cluster C F9 does **not** start F9 implementation. Citing OQ6 / OQ5 A does **not** reopen them. Citing #841 / #840 / #839 / #818 / #822 does **not** adopt those open OQs.
6. Linear status for DRG-551 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

---

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)** and matches the #667 proposed default where one exists. Alternatives are listed. **No Adopted cells in this table.** Rows refine **platform delivery** against A1–A10; they do **not** reopen those contracts.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| **OQ-551-1** | **Platform dry-run output shape** | #667 D1-A field table (lifecycle projection + optional F9 `trueUp` block) | DealHub `isDryRun` fields; true-up-only; persist an amendment document | Yes — response / OpenAPI AC |
| **OQ-551-2** | **Persistence** | Not persisted; no amendment document (#667 D2-A; OpenAPI draft sentence) | Preview row outside events (`V1_22+` next free — never reserved `V1_16`–`V1_21`); `DRAFT` document | Yes — schema / write path |
| **OQ-551-3** | **S2 chain on dry-run** | No chain row and no outbox row (#667 D3-A) | Append chain row when a document already exists | Yes — audit / outbox AC |
| **OQ-551-4** | **Repeatability** | Same snapshot id / policy version / content hash → same amounts; no CAM idempotency receipt (#667 D4-A; aligns with Adopted F9 for true-up pins) | Live recompute that can move; store a committed-operation receipt | Soft for preview; hard if amounts change after customer-visible true-up (A1) |
| **OQ-551-5** | **Entitlements / conversion price** | Omit both; `confirmedPrice: false` for any other amount (#667 D5-A); pending DRG-609 | Fill via unaccepted DRG-609 route sketch | Yes if amounts invent prices |
| **OQ-551-6** | **True-up / Cluster C overlap** | Include Adopted F9 fields on true-up subjects; leave G4/G5; do **not** start F9 / DRG-569 implementation (#667 D6-A) | Second true-up field list; implement F9 inside DRG-551 now | Yes if C starts F9 product work |
| **OQ-551-7** | **What revert means** | Read-only reversible check + new correcting LifecycleChange after `EXECUTED` / any committed unit; leave rows in place; deletion owner-only; no `effective_end_date` rewrite (#667 D7-A) | Close rate periods by update; DealHub `/{version}/revert`; delete / soft-delete | Yes — commercial history / A8 |
| **OQ-551-8** | **Data deletion** | Leave rows in place; deletion needs a separate owner step (rows, backup, rollback) (#667 D8-A) | Call `contract_header_backfill_compensate()` as lifecycle revert | Yes — data destruction |
| **OQ-551-9** | **Unapplied change path** | Affirm Adopted OQ6: rule 1 pre-execute; rule 2 post-`EXECUTED`; no `VOID`; no delete (#667 D9-A) | `VOID` executed-but-unapplied; delete draft rows | Yes if B/C — reopen A5/A6 |
| **OQ-551-10** | **After a unit commits** | Affirm Adopted OQ5 A: correcting amendment only; committed units stay; no automatic rollback; no rebase (#667 D10-A) | Automatic rollback; rebase-or-correct per unaccepted `specs/partial-apply.md` | Yes if B/C — reopen A4 |
| **OQ-551-11** | **Version identity** | Cite existing `commercial_revision`; projection label = observed+1; column not updated by dry-run (#667 D11-A) | New subscription version column (next free CAM after reserved band) | Yes — schema if B |
| **OQ-551-12** | **Refusal audit token** | Keep `AMENDMENT_REVISION_REJECTED` **PROPOSED**; reversible check writes nothing; no schema-registry / S6 (#667 D12-A) | Treat as existing S2 constant (no defining path found); new `REVERTED` + S6 | Soft until refusal command; hard if S6 claimed |
| **OQ-551-13** | **Dry-run audit row** | No audit row; `audit.chainAppended: false` (#667 D13-A) | `commercial_decision_audit` row; pricing confirmation snapshot | Yes if C — violates A7 |
| **OQ-551-14** | **S4 / ledger inside dry-run** | Dry-run does not call apply and does not write `document_apply_ledger` / `applied_quote_acceptances` / rate periods; future test asserts no-write including **now-landed** `document_apply_ledger` (#667 D14-A, updated for `V1_12`) | Apply inside a rolling-back transaction | Yes — would run apply against stored rows |
| **OQ-551-15** | **Flyway / reserved band** | Any later authorized migration is **next free after tip**, never CAM `V1_16`–`V1_21`, never `V1_7_3`; number settled only on an authorized migration PR | Claim reserved CAM band; ship schema before OQs close | Yes — any migration |
| **OQ-551-16** | **Sibling Wave-2 coupling** | Cite #841 / #840 / #839 / #818 / #822 only; do not adopt their open OQs; do not invent a second audit store or apply-gate here | Merge sibling OQs into this brief; enable DRG-625 from dry-run scoring | Soft here; hard on those tickets |
| **OQ-551-IMPL** | **Implementation brief authorization** | Blocked until blocking OQ-551-* close or are explicitly deferred with owner words+time | Authorize implementation from this file alone | Yes |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-551-1 — Platform dry-run output shape

| Option | What the owner would be choosing |
|---|---|
| **A. #667 field table (Recommended, Proposed)** | One read response: `dryRun: true`, `persisted: false`, `confirmedPrice: false`, optional `documentId` echo, `subject` (`LIFECYCLE_CHANGE_PREVIEW` \| `TRUE_UP_SNAPSHOT`), pins, optional F9 `trueUp` block, projection labels (observed/projected revision, units `WOULD_APPLY`/`WOULD_BLOCK`, projected document status, rate-period descriptors), `audit` no-write flags. |
| **B. DealHub `isDryRun` fields** | Research evidence only — not an API to copy. |
| **C. True-up fields only** | Drops lifecycle projection. |
| **D. Return a persisted amendment document** | Contradicts the OpenAPI draft persistence sentence and OQ-551-2-A. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-2 — Persistence

| Option | What the owner would be choosing |
|---|---|
| **A. Not persisted; no amendment document (Recommended, Proposed)** | Matches #667 D2-A and the OpenAPI draft sentence. |
| **B. Preview row outside `amendment_document_events`** | New store. Later migration = next free after tip; **never** reserved `V1_16`–`V1_21`; never `V1_7_3`. |
| **C. A `DRAFT` amendment document** | Turns dry-run into a document command. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-3 — S2 chain on dry-run

| Option | What the owner would be choosing |
|---|---|
| **A. No chain / outbox row (Recommended, Proposed)** | Writer requires a document; response reports `NO_DOCUMENT_CHAIN_ROW`. |
| **B. Append when a document already exists** | Stages S2 outbox; event type would be an unregistered string; S6 not authorized. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-4 — Repeatability

| Option | What the owner would be choosing |
|---|---|
| **A. Same pins → same amounts; no receipt (Recommended, Proposed)** | Aligns with Adopted F9 for true-up; dry-run commits nothing so no D-IDEMPOTENCY receipt. |
| **B. Live recompute** | Amounts can move after the customer has seen them (conflicts with A1 for true-up). |
| **C. Store a committed-operation receipt** | Wrong grain for a non-committing read. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-5 — Entitlements / conversion price

| Option | What the owner would be choosing |
|---|---|
| **A. Omit; `confirmedPrice: false` (Recommended, Proposed)** | Pending unaccepted DRG-609 design. Does not reopen Accepted uplift D-04 or proration. |
| **B. Fill via DRG-609 route sketch** | Uses a design this brief does not accept. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-6 — True-up / Cluster C overlap

| Option | What the owner would be choosing |
|---|---|
| **A. Carry F9 fields; do not start F9 (Recommended, Proposed)** | Restates A1–A3 orientation without launching DRG-569 / meter snapshot product work. |
| **B. Second true-up field list** | Splits platform shape from Adopted preview. |
| **C. Implement F9 inside DRG-551 now** | Product work — **NO-GO** from this docs brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-7 — What revert means

| Option | What the owner would be choosing |
|---|---|
| **A. Reversible check + correcting LifecycleChange; leave rows (Recommended, Proposed)** | Matches #667 D7-A, A4, A5, A6, A8. No DealHub endpoint copy. |
| **B. Close applied rate periods via `effective_end_date`** | Issue suggested spike; updates commercial rows; conflicts with A8 without a successor-period write under authorized apply. |
| **C. DealHub `/{version}/revert`** | Vendor endpoint; not in this tree. |
| **D. `DELETE` / `TRUNCATE` / `is_deleted`** | Data deletion — OQ-551-8. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-8 — Data deletion

| Option | What the owner would be choosing |
|---|---|
| **A. Leave rows; deletion owner-only (Recommended, Proposed)** | No backfill compensate as lifecycle revert. |
| **B. Call `contract_header_backfill_compensate()`** | Deletes `V1_8_HEADER_BACKFILL` headers — not a lifecycle revert. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-9 — Unapplied change path

| Option | What the owner would be choosing |
|---|---|
| **A. Affirm Adopted OQ6 (Recommended, Proposed)** | Pre-execute revision; post-`EXECUTED` correcting document; no `VOID`. |
| **B. `VOID` executed-but-unapplied** | Rejected by A6 / spike mapping. |
| **C. Delete draft rows** | Data deletion. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-10 — After a unit commits

| Option | What the owner would be choosing |
|---|---|
| **A. Correcting amendment only (Recommended, Proposed)** | Affirms Adopted OQ5 A; rebase not first-slice. |
| **B. Automatic rollback** | Rejected by A4. |
| **C. Rebase or correcting amendment** | Unaccepted `specs/partial-apply.md` sentence. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-11 — Version identity

| Option | What the owner would be choosing |
|---|---|
| **A. Existing `commercial_revision` (Recommended, Proposed)** | No new column; dry-run does not increment. |
| **B. New subscription version column** | Next free CAM after reserved band, with rollback statement. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-12 — Refusal audit token

| Option | What the owner would be choosing |
|---|---|
| **A. Keep PROPOSED; check writes nothing (Recommended, Proposed)** | Token appears in draft OpenAPI / D-RACE prose only; writer has no constant. |
| **B. Treat as existing S2 value** | Requires a defining path — search found none. |
| **C. New `REVERTED` + S6** | S6 not authorized. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-13 — Dry-run audit row

| Option | What the owner would be choosing |
|---|---|
| **A. No audit row (Recommended, Proposed)** | Preview is not confirm (A7). |
| **B. `commercial_decision_audit` row** | Wrong grain; cite #822 only. |
| **C. Pricing confirmation snapshot** | Violates A7. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-14 — S4 / ledger inside dry-run

| Option | What the owner would be choosing |
|---|---|
| **A. Do not call apply; assert no-write including `document_apply_ledger` (Recommended, Proposed)** | Updates #667 D14 for landed `V1_12`. Table presence ≠ S4 Done. |
| **B. Apply inside a rolling-back transaction** | Runs against stored rows — out of this docs brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-15 — Flyway / reserved band

| Option | What the owner would be choosing |
|---|---|
| **A. Next free after tip; never `V1_16`–`V1_21` (Recommended, Proposed)** | Number settled only on an authorized migration PR. |
| **B. Claim reserved CAM band** | Conflicts with #805–#812. **NO-GO.** |
| **C. Ship schema before OQs close** | Violates product/schema NO-GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-16 — Sibling Wave-2 coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; adopt none (Recommended, Proposed)** | Keep apply-gate / ledger / partial-apply / true-up / audit OQs on their tickets. |
| **B. Merge sibling OQs / enable DRG-625 here** | Scope creep; flag enablement. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-551-IMPL — Implementation brief authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Blocked until blocking OQ-551-* close or deferred with words+time (Recommended, Proposed)** | Docs PR alone is not authorization. |
| **B. Authorize implementation from this file** | Conflicts with A10 / NO-GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Explicit NO-GO

Until blocking **OQ-551-*** rows close (or the owner defers them with words and a time):

- No product code, Flyway/schema, feature flags, topics, gateway routes, or deploys for lifecycle dry-run / reversible / revert.
- No CAM migration number in **V1_16–V1_21**.
- No S3 / S4 / S5 / S6 / S7 / dual-read / Jev / ramp / asset-write hold bypass; no DRG-625 enablement.
- Do **not** reopen Adopted F9 / G4 / G5 / OQ5 A / OQ6 / OQ7 / D-REVISION / preview≠confirm.
- Do **not** adopt open OQs from #841 / #840 / #839 / #818 / #822 / #855 / #833.
- Do **not** copy DealHub `/reversible` or `/{version}/revert` as a parity commitment.
- Do **not** rewrite `effective_end_date` / digests / executed commercial facts as “revert”.
- Do **not** mark Linear DRG-551 Done from this draft PR.

## Out of scope

- Editing `AmendmentEventWriter`, OpenAPI draft, Cluster C frame, gate doc, or sibling residual briefs.
- Accepting DRG-609, starting F9 product work, or unparking S4 / DRG-582.
- Assigning an ADR number or copying this file into `docs/adr/`.
- Enabling `opencrm.amendment.routing.document-path-tenants`, ramp, or any `opencrm.jev.*.enabled` flag.
- Claiming CAM Flyway V1_16–V1_21.
- Running `contract_header_backfill_count` / `_apply` / `_compensate`, or any apply.

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Design note #667 cited; Adopted A1–A10 restated; residual forks Proposed only as **OQ-551-***
- [x] Linear left **In Review** (orchestrator / agent must not mark Done)
- [x] GitNexus unavailable / **UNKNOWN** — not treated as clean
- [x] CAM V1_16–V1_21 reserved; product / schema / flag = **NO-GO**
- [x] Secret scan: no credentials in this file

## Owner decision slots

For each **OQ-551-*** row scored: `Owner decision: ___ (drg, time)`.
