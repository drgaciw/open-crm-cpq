# Cluster A — Amendment modes and commercial policy (decision frame)

**Status:** Accepted as documentation on 2026-10-17. See the [owner acceptance record](documentation-acceptance-2026-10-17.md) for scope; individual forks and implementation gates retain their stated statuses.

**Date:** 2026-10-17
**Base:** `main` at `3ee3ca0f` (merge of [#642](https://github.com/drgaciw/open-crm-cpq/pull/642), merged 2026-10-17T07:40:40Z).
**Project:** OPEN-CRM-CPQ, Linear team DRG, milestone Renewals & Amendments.
**Stories:** [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592) (RA-58), [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) (RA-20), [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (RA-60), [DRG-599](https://linear.app/drgamtd-workspace/issue/DRG-599) (RA-65), [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) (RA-16).

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No flag is enabled. Nothing is deployed. Tickets stay In Review until the owner merges.

Descriptions and relations for the five stories were read on Linear on 2026-10-17. Each description is a policy or design gap. None was skipped. Linear status in this note is that read. It is not a git fact.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or migration named in the same sentence was opened on this `main`. |
| `unverified` | Not opened, not executed, or not queried. A search that found no row is stated as a search, not as a live database result. |

No database was queried. Whether Flyway has applied any migration in an environment is `unverified`.

## Standing constraints

| Constraint | Record on this `main` |
|---|---|
| ADR-035 acceptance scope is S1 and S2 | `read` [`docs/renewals-amendments/adr-035-amendment-document-placement.md`](../adr-035-amendment-document-placement.md) status line and "Acceptance 2026-10-16". Later stories need their own gates. |
| ADR-017 and ADR-024 through ADR-027 stay Proposed | `read` [`docs/adr/ADR-017-synchronous-inter-service-communication.md`](../../adr/ADR-017-synchronous-inter-service-communication.md), [`ADR-024-order-orchestration-context.md`](../../adr/ADR-024-order-orchestration-context.md), [`ADR-025-saga-process-manager.md`](../../adr/ADR-025-saga-process-manager.md), [`ADR-026-command-messages.md`](../../adr/ADR-026-command-messages.md), [`ADR-027-compensation-and-point-of-no-return.md`](../../adr/ADR-027-compensation-and-point-of-no-return.md). `read` [`docs/renewals-amendments/prereq/adr-reconcile-adr-024-027.md`](adr-reconcile-adr-024-027.md) header: that record does not accept ADR-024 through ADR-027. |
| Proration stays in the Accepted ADR | `read` [`docs/renewals-amendments/prereq/adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md). Its number line is quoted in section 5: **Number:** none. This note does not restate the formula. |
| S6 is not authorized | `read` [`docs/renewals-amendments/prereq/partial-apply-owner-decision.md`](partial-apply-owner-decision.md) and OQ5 in [`docs/renewals-amendments/renewals-amendments-open-questions-register.md`](../renewals-amendments-open-questions-register.md): S6 is not authorized. ADR-035 does not authorize S6. |
| S3 is not started | Merged #642 text, quoted below. [DRG-616](https://linear.app/drgamtd-workspace/issue/DRG-616) is named on the DRG-581 issue (Linear read 2026-10-17). |
| DRG-609 design is not accepted | `read` [`docs/renewals-amendments/prereq/story-drg-609-pricing-service-continuation-design.md`](story-drg-609-pricing-service-continuation-design.md): the note is a design proposal and is not an ADR. |
| Entitlements and conversion price | Pending the DRG-609 design (not accepted). This note does not specify them. |
| OQ7 is adopted | One thin contract per subscription. `read` [`docs/renewals-amendments/s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md): "Adopted default, first slice only". The same wording is the header comment of [`V1_8__contract_header_backfill.sql`](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql). Regrouping is a follow-up option only (554-F6). #642 left the register cell as `DEFAULT PROPOSED`; that sentence is quoted below and is not a second grouping rule. |
| First-slice action and signature cut | `read` OQ12 and OQ10 in the register; `read` slice definition in [`docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md`](../amendment-first-slice-requirements-and-acceptance.md). |

Cluster files [`specs/co-termination.md`](specs/co-termination.md), [`specs/catalog-model.md`](specs/catalog-model.md), [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md), and [`specs/partial-apply.md`](specs/partial-apply.md) stay proposals (`read` status lines). This note does not accept them.

### Merged #642 text this note relies on

Source file on `main`: [`docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md). The paragraphs below are copied from that file.

> **Status:** OQ6 was decided under delegation by CPQ Decision and relayed by Projects Manager; it is not the owner's personal choice. OQ18 remains **Proposed until the owner confirms it**. OQ6 is recorded for the first slice, but this record authorizes no code, schema, migration, flag, topic, gateway route, deploy, or story. S3 / DRG-581 requires separate owner authorization. OQ18 confirmation is required before building the proposed digest library, not authorization to start S3.

> 1. **Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`).** A new revision of the LifecycleChange opens a new document revision on the same document, with the same `provisionalNumber` (OQ4). The previous revision closes with `status_at_close = SUPERSEDED` and keeps its content digest. The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again. The persisted representation of an unpriced revision is not decided here.

> 2. **Executed, in any apply state (`EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`).** The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED` and an audit event, which is AC-S3-5 applied to revisions. "Executed but not yet applied" gets no special path. The remedy is a new LifecycleChange that produces a new, correcting amendment document, the same remedy OQ5 option A adopted for a blocked unit.

> - It does not authorize S3, S4, S5, S6, S7 or S8. S3 / DRG-581 needs separate owner authorization.
> - It does not change OQ5 (adopted for the first slice), OQ7 (still `DEFAULT PROPOSED` in the register) or any ADR status.

## Owner-decision table

Every row is Proposed, unaccepted, owner decision required. The default is the recommendation. The alternatives are the other options in that fork.

| Fork | Story | Default | Alternatives |
|---|---|---|---|
| 592-F1 Flag shape | DRG-592 | Boolean `precedence_flag` | Integer rank across overlapping contracts; a customer-level `prevailing_contract_id` and no boolean column |
| 592-F2 Who may change the flag | DRG-592 | Initial winner is established at executed apply; a later correcting amendment transfers the contract-level winner atomically, with an audit row and historical document snapshots preserved | A product or admin in-place flip with an audit event; a sales flip with no new amendment |
| 592-F3 Overlap for pricing and visibility | DRG-592 | Precedence-true contract is the pricing source; both contracts stay visible; two true flags fail closed | Visibility split off to DRG-595 while the flag still wins pricing; each reader picks its own contract. Entitlement resolution is pending the DRG-609 design (not accepted) and is not in this default |
| 592-F4 Original at its own end date | DRG-592 | Stays `ACTIVE` as a reference until its own end date, then `EXPIRED` | Stays `ACTIVE` with no expiry; set to `TERMINATED` when the new contract is created |
| 592-F5 Where the mode is stored | DRG-592 | A column on the amendment document, added in a later increment, not in S1 | Infer the mode from whether a new `contracts` row was inserted; store the mode only on the LifecycleChange |
| 592-F6 Which contract owns the document | DRG-592 | The new contract owns the one document; `prior_contract_id` points at the original | The original owns the document; both contracts get a copy |
| 554-F1 Co-term mode set | DRG-554 | DRG-554's four modes plus `AlignToDate` | The four modes only (`LatestEnd`, `EarliestEnd`, `None`, `AlignToAgreement`); a second customer-level coterm engine |
| 554-F2 Missing policy | DRG-554 | Single-agreement amendment keeps V5; a multi-agreement change with no mode is refused | Missing policy means `None`; missing policy means `AlignToAgreement` |
| 554-F3 Which lines move | DRG-554 | Only lines in the change | Every line on the target agreements |
| 554-F4 Shortening | DRG-554 | The date may move earlier; this policy writes no credit | Co-term itself emits a credit; shortening is forbidden. Conversion price for that credit is pending the DRG-609 design (not accepted) |
| 554-F5 Lengthening past the governing end | DRG-554 | Mid-term co-term refuses an end after the governing contract end | Allowed as a mid-term amendment; allowed only when the mode is `AlignToDate` |
| 554-F6 Merge versus co-term, OQ7 regrouping | DRG-554 | Adopted OQ7: one thin contract per subscription. The backfill does not regroup. Regrouping is not selected | Follow-up options only: a later merge that retargets `contract_id`; a DRG-592 new contract as the survivor; never regroup |
| 554-F7 Source after merge | DRG-554 | Source stays active as a reference until a later executed close | Source is cancelled when the survivor pointer is written; source row is deleted |
| 554-F8 Home versus DRG-461 | DRG-554 | DRG-461 stays the capability item; this note is the policy frame | DRG-554 replaces DRG-461 |
| 594-F1 Seats versus Add and Remove | DRG-594 | Business policy, owner option, not adopted: a same-SKU quantity `UPDATE` is seats; `ADD` of a SKU and `REMOVE` of a product line require review | Same-SKU Add and Remove count as seats; any quantity change, including a new SKU, is free |
| 594-F2 Where the gate sits | DRG-594 | A guard on `DRAFT` to `IN_REVIEW`, no new state | A new `LEGAL_REVIEW` state; the document may enter `IN_REVIEW` and stops before `APPROVED` |
| 594-F3 Override | DRG-594 | A named legal role may override, and until that role is named the guard refuses; sales cannot override | No override ever; the seller may override |
| 594-F4 Mixed edits | DRG-594 | Business policy, owner option, not adopted: one edit outside the free set makes the whole document require review; free header fields are price and term | Per-line skip; governing agreement and clauses are free as well |
| 594-F5 First-slice timing | DRG-594 | The gate is a later increment; S1–S8 keep the current signature cut | The gate is required before S3 starts |
| 599-F1 Where the mapping lives | DRG-599 | Business policy, owner option, not adopted: one effective-dated catalog relationship; dispute status points at that version | A separate amendment SKU mapping table; both a catalog row and a commercial overlay |
| 599-F2 Dispute status and resolver | DRG-599 | `Active`, `Contested`, `Resolved`; product resolves; sales may open and may not clear | Add `Rejected`; sales clears the hold |
| 599-F3 Where the hold bites | DRG-599 | Whole document, before `IN_REVIEW`, immediately before `EXECUTED`, and again before apply | Apply only; per-line partial apply |
| 599-F4 Which lines need a mapping | DRG-599 | Only when source SKU and target SKU differ | Every line, including same-SKU edits |
| 599-F5 Pin versus live row | DRG-599 | Pin the relationship version on the line at pricing | Read the live row at apply |
| 550-F1 Price mode | DRG-550 | `RetainContracted`, `List`, and `Override`, with `RetainContracted` when the mode is omitted | `RetainContracted` only; a fourth flat-adjustment mode |
| 550-F2 Credit policy | DRG-550 | `NoCredit` | `ProratedUnused`; `FullRemaining`; the seller must pick on every line and there is no tenant default. Any conversion price is pending the DRG-609 design (not accepted) |
| 550-F3 Termination fee | DRG-550 | `NoFee` | A flat amount; a percent of remaining contract value; a cut-off window |
| 550-F4 Where the three policies are stored | DRG-550 | Tenant default plus a per-line override, snapshotted when priced | One set of policies for the whole document; pricing-service owns the store, which is pending the DRG-609 design (not accepted) |

Proration is not a row. DRG-550 cites [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) and does not decide it.

---

## 1. DRG-592 — Amendment modes

**Issue:** [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592) (RA-58). Linear read 2026-10-17: High, Backlog. Relations read: DRG-586, DRG-535, DRG-561, DRG-579, DRG-581, DRG-607.

### Problem

An amendment needs two modes. New-contract mode creates a new contract, both stay `ACTIVE`, a stored precedence flag on the new contract says it takes precedence, and the original stays open as a reference. Amend-in-place changes the existing contract. The first slice builds amend-in-place only. Still open: flag shape, who may change it, how overlapping active contracts resolve, and what happens to the original at its own end date. Entitlement resolution is pending the DRG-609 design (not accepted).

### Already on main

- **Sequencing is a register row, not a column.** `read` OQ26 in [`renewals-amendments-open-questions-register.md`](../renewals-amendments-open-questions-register.md): both modes, amend-in-place in S1–S8, new-contract mode as the next increment. The same sequencing is in [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) (clarification 2026-10-16, C1) and [`renewals-amendments-dev-readiness.md`](../renewals-amendments-dev-readiness.md). `cpq-project-transcript.md` was not re-opened this pass (`unverified` as a second source).
- **Flag placement is a register row, not a migration.** `read` OQ27 in the same register, and section 2.2a of [`amendment-entity-field-schema.md`](../amendment-entity-field-schema.md). The field schema sketches a boolean. That sketch is not a landed column.
- **S1 migration has `prior_contract_id` and no precedence column.** `read` [`V1_7__contract_documents.sql`](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql) `CREATE TABLE contracts`: `prior_contract_id uuid`, `chk_contracts_status` allows `ACTIVE`, `EXPIRED`, `TERMINATED`. The migration comment says "no precedence_flag (DRG-592)". A search of `backend/services/contract-asset-management/src/main/resources/db/migration` for `amendment_mode` and `precedence_flag` found `precedence_flag` only in that comment. Companion: [`s1-v1-7-contract-documents.md`](../s1-v1-7-contract-documents.md).
- **Document ownership column.** `read` the same migration, `CREATE TABLE amendment_documents`: `contract_id uuid NOT NULL REFERENCES contracts (id)`. One document has one `contract_id`.
- **Event table columns, not a precedence event type.** `read` the same migration, `CREATE TABLE amendment_document_events`: `seq_no`, `prev_hash`, `content_hash`, `event_type varchar(80) NOT NULL`. No check constraint enumerates `event_type`. Schema-registry registration of a precedence event was not opened (`unverified`).
- **S2 writer class.** `read` [`AmendmentEventWriter.java`](../../../backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java) class comment: it appends one hash-chained event and its audit outbox row in the caller's transaction. The writer was not executed (`unverified` as runtime).
- **Apply after EXECUTED is a document decision.** `read` OQ2 in the register (RESOLVED 2026-10-16, apply only after EXECUTED, single status column) and the ADR-035 context sentence that records the same resolution. A search of the CAM migration directory found `apply_status` only in the `V1_7__contract_documents.sql` comment that says there is no `apply_status`. A live database catalog was not queried (`unverified`).

### Forks

**592-F1. Flag shape.**

| Option | Meaning |
|---|---|
| A. Boolean | `precedence_flag` true means this contract takes precedence over `prior_contract_id`. |
| B. Rank | An integer orders every overlapping active contract for the same customer. |
| C. Exclusive winner pointer | `prevailing_contract_id` on the customer. No boolean column. |

**Recommendation:** A. OQ27 names one indexable column, `precedence_flag`, outside the thin header (`read` register).

**Default:** Proposed, unaccepted, owner decision required. Boolean `precedence_flag`, not null, default false (option A). Amend-in-place leaves it false. New-contract mode establishes the initial true flag on the new contract only at executed commercial apply; later changes use the audited transfer in 592-F2. Alternatives written out: an integer rank that orders every overlapping active contract (option B); a single `prevailing_contract_id` on the customer and no boolean column (option C).

**592-F2. Who may change the flag.**

| Option | Meaning |
|---|---|
| A. Audited winner transfer, on the #642 path | Establish the initial winner at executed commercial apply. A later executed correcting document atomically clears the old contract-level flag and sets the new winner; historical executed documents are unchanged. |
| B. Product or admin flip | A privileged actor updates the column and writes an audit event. |
| C. Sales flip | The seller changes precedence with no new amendment. |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Establish the initial contract-level winner only at executed commercial apply (option A). A later change uses the #642 revision path before execution or a new correcting document after execution. At apply of that correcting document, CAM locks the affected precedence chain in one transaction, verifies the expected current winner, clears its contract-level flag, sets the replacement winner, and appends an audit record linking old winner, new winner, actor and correcting document. A stale winner or two competing transfers rejects the whole transfer; it cannot leave two true flags. Previously executed documents and their pinned precedence evidence are immutable and are not rewritten. The clear/set operation changes current contract authority, not an executed document. This mechanism is itself a proposal, requires separate owner acceptance and implementation authorization, and is not supplied by OQ6 alone. Alternatives: a privileged in-place product/admin flip (B); a sales flip with no correcting document (C). Approval roles stay on parked DRG-591.

**592-F3. Overlapping active contracts.**

| Option | Meaning |
|---|---|
| A. One commercial rule, both visible | The precedence-true contract is the pricing source. Both stay visible. |
| B. Flag for pricing; visibility elsewhere | Visibility stays on DRG-595. |
| C. Reader-specific rules | Pricing, entitlement, and visibility each pick a contract. |

Entitlement resolution under every option is pending the DRG-609 design (not accepted). This fork does not decide it.

**Recommendation:** A for pricing and visibility of the contract row. Two true flags on one `prior_contract_id` chain fail closed.

**Default:** Proposed, unaccepted, owner decision required. Pricing reads the precedence-true contract; both contracts stay visible; two true flags fail closed (option A). Zero true flags and one active contract: that contract is the pricing source. Alternatives written out: the flag wins pricing and DRG-595 owns visibility (option B); pricing, entitlement, and visibility each use their own rule (option C). Entitlement resolution is pending the DRG-609 design (not accepted) and is not part of this default. DRG-595 is named from [`renewals-amendments-dev-readiness.md`](../renewals-amendments-dev-readiness.md) (`read`). The DRG-595 issue body was not re-opened this pass (`unverified`).

**592-F4. Original contract at its own end date.**

| Option | Meaning |
|---|---|
| A. Follows its own end date | The amendment does not close it. `status` can become `EXPIRED`. The row stays a reference. |
| B. Stays `ACTIVE` | No expiry. |
| C. Terminated at creation | The new-contract amendment sets the original to `TERMINATED`. |

**Recommendation:** A. `read` field-schema clarification 2026-10-16: the original stays open as a reference. `read` `chk_contracts_status` in `V1_7__contract_documents.sql`.

**Default:** Proposed, unaccepted, owner decision required. New-contract creation leaves the original `ACTIVE` and open as a reference; its own end date is the path to `EXPIRED` (option A). Alternatives written out: the original stays `ACTIVE` with no expiry (option B); the amendment sets the original to `TERMINATED` at creation (option C). No new status value is in this default. A job that flips `EXPIRED` was not found in `V1_7__contract_documents.sql` or `V1_8__contract_header_backfill.sql` (`unverified` as a scheduler outside those files).

**592-F5. Where the mode is stored.**

| Option | Meaning |
|---|---|
| A. Column on the amendment document | `amendment_mode` is `NEW_CONTRACT` or `AMEND_IN_PLACE`. |
| B. Inferred | Mode is inferred from an insert into `contracts`. |
| C. LifecycleChange only | Quote-service stores the mode. CAM does not. |

**Recommendation:** A. `read` ADR-035 D-01: the document lives in CAM.

**Default:** Proposed, unaccepted, owner decision required. The column is a later increment, not S1 (option A). Until that increment, documents are amend-in-place because OQ26 says so (`read` register), not because a mode column exists. A mode value already stored on an executed document is not edited in place: "The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED`". Alternatives written out: infer the mode from whether a new `contracts` row was inserted (option B); store the mode only on the LifecycleChange (option C). S3 is not started. Merged #642 text: "S3 / DRG-581 requires separate owner authorization."

**592-F6. Which contract owns the document.**

| Option | Meaning |
|---|---|
| A. The new contract | `amendment_documents.contract_id` is the new contract. `prior_contract_id` points at the original. |
| B. The original | The new contract is only a result row. |
| C. Both | Two documents. |

**Recommendation:** A. `read` `contract_id` on `amendment_documents` in `V1_7__contract_documents.sql`. `read` OQ4 in the register: final number at EXECUTED, format recorded in ADR-035 context as `{contract_number}-A{nnn}`.

**Default:** Proposed, unaccepted, owner decision required. One document on the new contract, `prior_contract_id` set, no second document on the original (option A). This is the first document for that new contract. It is not a revision of an executed document. After execution the #642 remedy is "The remedy is a new LifecycleChange that produces a new, correcting amendment document, the same remedy OQ5 option A adopted for a blocked unit." Alternatives written out: the original owns the document and the new contract is only a result row (option B); each contract gets its own copy (option C).

### Dependencies

| Dependency | Why it is named |
|---|---|
| `V1_7__contract_documents.sql` (DRG-579) | Header and `prior_contract_id` exist. `precedence_flag` does not. |
| `AmendmentEventWriter.java` (DRG-580) | Hash-chain append exists in source. A precedence `event_type` is not enumerated. |
| ADR-035 | Thin-header list stays. A later column sits beside that list. |
| DRG-581 (S3) | Not started. Quoted #642 text: "S3 / DRG-581 requires separate owner authorization." |
| DRG-554 | OQ7 regrouping is section 2, not a second new-contract design. |
| DRG-591 | Parked approvals. `read` OQ25. |
| DRG-609 | Entitlements in 592-F3. Design not accepted. |

### What it unblocks

An owner decision on 592-F1 through 592-F6 is the input to a later schema increment. It does not start S3, authorize S6, or change `V1_7__contract_documents.sql`. DRG-607 can cite this frame. The frame stays Proposed, unaccepted, owner decision required.

---

## 2. DRG-554 — Co-termination, agreement merge, and the OQ7 regrouping follow-up

**Issue:** [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) (RA-20). Linear read 2026-10-17: Medium, Backlog. Relations read: DRG-591, DRG-461, DRG-607, DRG-585, DRG-547.

OQ7 is adopted: one thin contract per existing subscription. Regrouping is a follow-up option only. This section does not rewrite the backfill and does not select a regrouping.

### Problem

There is no accepted co-term policy and no accepted merge of agreements. Multi-agreement changes need a date rule and, separately, a way to fold agreements into one survivor. [DRG-461](https://linear.app/drgamtd-workspace/issue/DRG-461) is the existing BC-07-04 item (Linear read 2026-10-17: acceptance criteria unchecked). This frame links to that item.

### Already on main

- **V5 and FR-11.** `read` [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) V5: effective date on or after contract start and on or before contract end; amended lines co-term to the existing end date unless a co-term policy says otherwise. `read` FR-11 in [`amendment-first-slice-requirements-and-acceptance.md`](../amendment-first-slice-requirements-and-acceptance.md): amended lines co-term to the existing end date.
- **Term hook.** `read` the same mid-term spec, extension point 2: "renewal policy may extend `effective_to` and the contract end date; the amendment policy forbids it."
- **Date column, no policy table.** `read` `co_term_date date` on `amendment_documents` in `V1_7__contract_documents.sql`. A search of the CAM migration directory for `merged_from` found no match. There is no `CoTermPolicy` table in that directory (`read` as a filename and text search, not a catalog query of a live database).
- **OQ7 adopted.** `read` [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md): "Adopted default, first slice only, wording unchanged" and the block quote "One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup." `read` the same sentences in the header comment of [`V1_8__contract_header_backfill.sql`](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql). Functions named in that file: `contract_header_backfill_count`, `contract_header_backfill_apply`, `contract_header_backfill_compensate`. The comment says Flyway installs the functions and does not execute them. Whether any environment has called apply is `unverified`. `prior_contract_id` and `governing_agreement_ref` are null in the fill rules in that comment. The sentence "a later merge, tracked on DRG-554, is the place to regroup" names where a follow-up could be decided. It does not adopt a regrouping.
- **Register cell #642 did not change.** `read` OQ7 in the register: status `DEFAULT PROPOSED for slice`. Merged #642 text, quoted above: "It does not change OQ5 (adopted for the first slice), OQ7 (still `DEFAULT PROPOSED` in the register) or any ADR status." That quote records the register cell. The adopted rule this note keeps is the S7 companion and `V1_8__contract_header_backfill.sql`, not a rewrite of that cell.
- **Co-term spec is a proposal.** `read` [`specs/co-termination.md`](specs/co-termination.md) status line and open questions 1–9. `AlignToDate` is proposed there and is not accepted. Open question 6 leaves merge versus co-term unset.
- **Renewal stub coterm note.** `read` [`renewal-policy-stub.md`](../renewal-policy-stub.md) clarification 2026-10-16: blended or rates-carry-forward, carry-forward named as default, shared end date, all-or-nothing per customer, optional renewal group, approver deferred to DRG-591. That note is not an accepted policy.
- **RN1 deferral.** `read` [`renewal-policy-spec.md`](renewal-policy-spec.md) RN1: a co-term anchor supplied by DRG-554 replaces the day-after-end date. The anchor's meaning stays DRG-554's.
- **Proration.** Cited only by the Accepted ADR number line, quoted in section 5. This section does not restate the formula. [`specs/co-termination.md`](specs/co-termination.md) open question 8 points at that ADR and says the slice must not add another formula (`read`). The formula text is not copied here.
- **DRG-461 event.** The Linear issue names a `ContractAmended` event (Linear read). No event contract is added here. S6 is not authorized (`read` register OQ5).

### Forks

**554-F1. Mode set.**

| Option | Meaning |
|---|---|
| A. Four modes | `LatestEnd`, `EarliestEnd`, `None`, `AlignToAgreement`, as named on DRG-554. |
| B. Four plus `AlignToDate` | An explicit anchor date, from the co-term proposal. |
| C. Second engine | The renewal-stub customer coterm is a separate date engine. |

**Recommendation:** B for the agreement date rule. The renewal-stub note supplies an anchor into that rule.

**Default:** Proposed, unaccepted, owner decision required. The proposed set is the four DRG-554 modes plus `AlignToDate` (option B). A single-agreement mid-term amendment with no policy row still uses V5 (`read` mid-term spec). Alternatives written out: only `LatestEnd`, `EarliestEnd`, `None`, and `AlignToAgreement` (option A); a second customer-level coterm engine with its own dates (option C).

**554-F2. Missing policy.**

| Option | Meaning |
|---|---|
| A. Means `None` | Each line keeps its end. |
| B. Means `AlignToAgreement` | Every line takes the agreement end. |
| C. Split | Single-agreement amendment uses V5. A multi-agreement change with no mode is refused. |

**Recommendation:** C.

**Default:** Proposed, unaccepted, owner decision required. Single-agreement amendments keep V5, and a multi-agreement change with no mode is refused (option C). Alternatives written out: a missing policy means `None` (option A); a missing policy means `AlignToAgreement` (option B).

**554-F3. Which lines move.**

| Option | Meaning |
|---|---|
| A. All lines on the target agreements | Untouched lines move. |
| B. Only lines in the change | A line the seller did not include keeps its end. |

**Recommendation:** B. This matches co-term spec open question 3's boundary (`read` [`specs/co-termination.md`](specs/co-termination.md)). That question is still an owner decision there.

**Default:** Proposed, unaccepted, owner decision required. Only lines in the change move (option B). Alternatives written out: every line on the target agreements moves (option A).

**554-F4. Shortening.**

| Option | Meaning |
|---|---|
| A. Credit emitted here | Co-term calculates a credit. |
| B. Date only | Credit, if any, is DRG-550. |
| C. Forbidden | `EarliestEnd` and an earlier anchor are rejected. |

**Recommendation:** B. This section does not calculate money.

**Default:** Proposed, unaccepted, owner decision required. The end date may move earlier only when the seller chose `EarliestEnd` or an earlier anchor, and this policy writes no credit (option B). Alternatives written out: co-term itself emits a credit (option A); shortening is forbidden (option C). Conversion price for any such credit is pending the DRG-609 design (not accepted).

**554-F5. Lengthening past the governing end.**

| Option | Meaning |
|---|---|
| A. Allowed mid-term | The end date moves out on an amendment. |
| B. Refused mid-term | Lengthening is a Renew, or new-contract mode under DRG-592. |
| C. `AlignToDate` may pass the end | An explicit anchor is the exception. |

**Recommendation:** B. `read` mid-term spec extension point 2: the amendment policy forbids extending the contract end date.

**Default:** Proposed, unaccepted, owner decision required. A mid-term co-term refuses an end after the governing contract end (option B). Alternatives written out: the end date may move out on the amendment (option A); `AlignToDate` may pass the current end (option C).

**554-F6. Merge versus co-term, including OQ7 regrouping.**

| Option | Meaning |
|---|---|
| A. Co-term only | Dates align. S7 headers stay one per subscription. No regrouping. |
| B. Later merge retargets `contract_id` | After an executed merge, subscriptions point at the survivor. Source headers stay as references. The backfill is not rerun. |
| C. Regroup through DRG-592 | A new contract is the survivor. Backfilled headers stay and are linked with `prior_contract_id`. |

**Recommendation:** Keep the adopted OQ7 rule. Do not select B or C. Co-term (554-F1) still aligns dates across agreements that remain separate. A merge, if the owner later chooses one, is a follow-up option only.

**Default:** Proposed, unaccepted, owner decision required. Adopted OQ7 stays: one thin contract per existing subscription, and the backfill does not regroup (`read` `V1_8__contract_header_backfill.sql` and [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md)). Regrouping is not selected. Alternatives, follow-up options only: never regroup, and use co-term dates only (option A); after a later executed merge, retarget `subscriptions.contract_id` onto a survivor (option B); create a DRG-592 new contract as the survivor and leave the backfilled headers in place (option C). No statement in `V1_8__contract_header_backfill.sql` updates `contract_id` from one contract to another (`read` header and fill rules). [`oq7-header-backfill-owner-decision.md`](oq7-header-backfill-owner-decision.md) records the same gap (`read`).

**554-F7. Source after merge.**

| Option | Meaning |
|---|---|
| A. Stays a reference | Active until a later executed close. |
| B. Cancelled with the pointer | Commercial close is a side effect. |
| C. Deleted | The source row is removed. |

**Recommendation:** A. It matches the DRG-592 record that the original stays open (`read` field-schema clarification).

**Default:** Proposed, unaccepted, owner decision required. The source stays active as a reference until a later executed close (option A). Alternatives written out: the source is cancelled when the survivor pointer is written (option B); the source row is deleted (option C).

**554-F8. Home versus DRG-461.**

| Option | Meaning |
|---|---|
| A. Link | DRG-461 remains the capability item. This note is the policy frame. |
| B. Replace | DRG-554 closes DRG-461 as a duplicate. |

**Recommendation:** A. The DRG-554 description says extend or link rather than duplicate (Linear read 2026-10-17).

**Default:** Proposed, unaccepted, owner decision required. DRG-461 stays the capability item and this note does not adopt its `ContractAmended` event (option A). Alternatives written out: DRG-554 replaces DRG-461 and BC-07-04 is closed as a duplicate (option B). S6 remains unauthorized (`read` register OQ5).

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-461 | BC-07-04 home. Link. |
| `V1_8__contract_header_backfill.sql` (DRG-585) | Adopted OQ7: one header per subscription. Regrouping is a follow-up option only (554-F6) and is not selected. |
| [`specs/co-termination.md`](specs/co-termination.md) | Proposal. Not accepted. |
| [`specs/catalog-model.md`](specs/catalog-model.md) | Proposed `coTermable` flag. Not accepted. A missing flag is not a refusal in this note. |
| DRG-576 | Relationships and eligibility. Backlog on Linear (not re-read this pass, `unverified` beyond the 2026-10-17 read of DRG-576). |
| DRG-591 | Approver. Parked. `read` OQ25. |
| [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) | Cited by the number line in section 5. Not reopened. Not restated. |
| DRG-550 | Credit if a shortened term should pay one. 550-F2 default is `NoCredit`. |
| DRG-609 | Conversion price on 554-F4. Design not accepted. |

### What it unblocks

An owner decision is the input to a later co-term policy and a later merge design. It does not rewrite `V1_8__contract_header_backfill.sql`, start S3, or authorize S6.

---

## 3. DRG-594 — Legal-review-free amendments

**Issue:** [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (RA-60). Linear read 2026-10-17: High, Backlog. Relations read: DRG-560, DRG-552, DRG-591, DRG-581, DRG-607, DRG-601.

### Problem

The on-main clarification says an amendment skips legal review only when it changes pricing, term, or seats, and nothing else. Clauses, products, special terms, and the governing agreement need legal review before execution. Add and Remove of a product line are outside that set unless product treats them as seats. General approvals stay parked on DRG-591 and DRG-601 (`read` OQ25; DRG-601 body was read 2026-10-17 as a relation title on DRG-594, body not re-opened this pass, `unverified`).

### Already on main

- **Free-set sentence, not an ADR.** `read` [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) clarification 2026-10-16: only pricing, term, or seats are legal-review-free. Adding or removing a product line is outside that set unless product treats it as seats.
- **Signature tokens and the first-slice cut.** `read` `chk_amendment_document_signature` in `V1_7__contract_documents.sql`: `WET`, `E_SIGN`, `CLICK_THROUGH`, `NOTICE_ONLY`, `NOT_REQUIRED`. `read` OQ10 in the register and FR-08 in the requirements file: `NOT_REQUIRED`, `NOTICE_ONLY`, and `CLICK_THROUGH` auto-execute after `APPROVED`; other policies stay unexecutable in the slice. The S3 criterion `SIGNATURE_NOT_SUPPORTED_IN_SLICE` is text in the DRG-581 issue (Linear read) and in the requirements file. S3 has not started, so that rejection is not a running gate (`unverified` as runtime).
- **Action tokens.** `read` `chk_amendment_line_action` in `V1_7__contract_documents.sql`: `ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE`. `read` OQ12: slice action codes are Add, Update, Remove. [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) is a proposal (`read` status line).
- **No legal-review state in the spec table.** `read` mid-term spec section 3: `DRAFT` to `IN_REVIEW` when priced and V1 to V12 pass. The table has no legal-review state.
- **OQ6, quoted, not paraphrased.** Merged #642 text: "The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again." The full paragraph is in the standing section.
- **Partial apply is a different hold.** `read` OQ5 in the register: option A for the first slice; `specs/partial-apply.md` is not accepted; S4 and S6 are not authorized.

Legal-review-free classes are business policy. 594-F1 and 594-F4 stay options for the owner. This note does not adopt a class list.

### Forks

**594-F1. Seats versus Add and Remove.** Business policy. Stays an option for the owner.

| Option | Meaning |
|---|---|
| A. Same-SKU quantity Update | `UPDATE` of quantity, price, or term on the existing SKU is free. `ADD` of a SKU and `REMOVE` of a product line require review. |
| B. Same-SKU Add and Remove are seats | A different SKU requires review. |
| C. Any quantity change is free | Includes a new SKU. |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Business policy, and it stays an option for the owner: a same-SKU quantity `UPDATE` is seats; `ADD` of a SKU and `REMOVE` of a product line require review (option A). Alternatives written out: same-SKU Add and Remove count as seats (option B); any quantity change, including a new SKU, is free (option C). This default does not edit the first-slice text and does not add a gate to S3. It does not adopt the class list.

**594-F2. Where the gate sits.**

| Option | Meaning |
|---|---|
| A. Guard on `DRAFT` to `IN_REVIEW` | No new state. |
| B. New `LEGAL_REVIEW` state | Inserted before `APPROVED`. |
| C. Block before `APPROVED` | The document may enter `IN_REVIEW`. |

**Recommendation:** A. The DRG-594 description asks for a gate on `DRAFT` to `IN_REVIEW` and a V-rule (Linear read).

**Default:** Proposed, unaccepted, owner decision required. A guard on `DRAFT` to `IN_REVIEW` and no new state (option A). Alternatives written out: a new `LEGAL_REVIEW` state before `APPROVED` (option B); entry to `IN_REVIEW` is allowed and the stop is before `APPROVED` (option C). The guard is not added to S3. It does not revise an executed document. Merged #642 text: "The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED`". On a pre-execution revision, "The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again." A legal-review result from the previous revision does not carry over. The guard runs on the new `DRAFT` revision.

**594-F3. Override.**

| Option | Meaning |
|---|---|
| A. No override | The document stays in `DRAFT` until the classification changes. |
| B. Named legal role | The actor is recorded. Sales cannot override. |
| C. Sales override | The seller attests and continues. |

**Recommendation:** B. The role name stays on DRG-591.

**Default:** Proposed, unaccepted, owner decision required. A named legal role may override, and until that role is named the guard refuses (option B, with option A's refusal in force until the name exists). Sales cannot override. Alternatives written out: no override ever (option A); the seller may override (option C). The override does not survive a pre-execution revision and does not edit an executed document. Merged #642 text: "The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again." And: "The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED`". An event type for the override is not enumerated in `V1_7__contract_documents.sql` (`read` `event_type varchar(80)` with no value list). #642 also says: "The delegated ruling does not choose a supersession event type, payload/schema, topic, or outbox contract." This default does not choose one either.

**594-F4. Mixed edits.** Business policy. Stays an option for the owner. The class split is not adopted.

| Option | Meaning |
|---|---|
| A. Whole document | One edit outside the free set requires review. |
| B. Per line | Free lines skip review. |
| C. Header split | Price and term are free. Governing agreement, clauses, and special terms require review. |

**Recommendation:** A, using C's header split.

**Default:** Proposed, unaccepted, owner decision required. Business policy, and it stays an option for the owner: one line or header edit outside the free set makes the whole document require review; free header fields are price and term (option A with the field split in option C). Alternatives written out: free lines skip review while other lines wait (option B); governing agreement and clauses are free as well (the opposite of option C). This is distinct from OQ5 partial apply (`read` register). The class split is not adopted.

**594-F5. First-slice timing.**

| Option | Meaning |
|---|---|
| A. Before S3 | FR-08 changes before S3 starts. |
| B. Later increment | S1–S8 keep the current signature cut. |

**Recommendation:** B.

**Default:** Proposed, unaccepted, owner decision required. The gate is a later increment and S1–S8 keep the current signature cut (option B). Alternatives written out: the gate is required before S3 starts (option A). FR-08 is not rewritten. This default does not start S3. Merged #642 text: "OQ6 is recorded for the first slice, but this record authorizes no code, schema, migration, flag, topic, gateway route, deploy, or story. S3 / DRG-581 requires separate owner authorization."

### Dependencies

| Dependency | Why it is named |
|---|---|
| `V1_7__contract_documents.sql` `chk_amendment_line_action` | Stored action tokens. |
| [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) | Proposal. Not the classifier. |
| DRG-581 (S3) | Not started. Quoted #642 text applies. |
| DRG-580 writer | Existing append path. No new `event_type` list. |
| DRG-591, DRG-601, DRG-560 | Approval design stays parked. |
| OQ6 file | Quoted rule 1. A pre-execution revision returns the document to `DRAFT` and voids approvals and signatures. |

### What it unblocks

An owner decision is the input to a later V-rule. It does not start S3, unpark DRG-591, or change FR-08. `read` [`story-drg-537-ui-spec.md`](story-drg-537-ui-spec.md): the legal-review gate is DRG-594 and is not a field on the draft create body.

---

## 4. DRG-599 — Amendment SKU mapping table

**Issue:** [DRG-599](https://linear.app/drgamtd-workspace/issue/DRG-599) (RA-65). Linear read 2026-10-17: High, Backlog. Relations read: DRG-565, DRG-576, DRG-591, DRG-590, DRG-607.

SKU mapping is business policy. Every fork in this section stays an option for the owner. This note does not adopt a mapping table, a dispute flow, or a hold.

Any price produced by a mapping, a swap, an upgrade, or a conversion is pending the DRG-609 design (not accepted). This section does not set that price.

### Problem

Upgrade, swap, and replacement need a mapping from a SKU on the original contract to the SKU or SKUs on the amendment. The DRG-599 description gives the table to the product team, lets sales dispute a mapping, and holds execution and apply while a line uses a contested mapping (Linear read). Same-SKU Add, Update, and Remove do not use a mapping. Conditional rules stay parked on DRG-590 (`read` OQ24).

### Already on main

- **No relationship table in product-service.** `read` `CREATE TABLE` names in [`V1__create_product_schema.sql`](../../../backend/services/product-service/src/main/resources/db/migration/V1__create_product_schema.sql): `products`, `product_bundles`, `product_bundle_items`, `product_option_groups`, `product_options`, `product_attributes`, `product_attribute_values`, `configuration_rules`. Later product-service migrations on this tree are `V1_3__create_outbox_events.sql`, `V1_4__add_product_retirement_fields.sql`, `V1_5__outbox_relay_status_and_leasing.sql`, `V1_6__outbox_event_envelope_columns.sql`, `V1_7__outbox_status_skipped.sql`, `V1_8__allow_canonical_ramp_product_types.sql`. A search of `backend/services/product-service` for `ProductRelationship` and `product_relationship` returned no matches. A live database catalog was not queried (`unverified`).
- **Catalog proposal.** `read` [`specs/catalog-model.md`](specs/catalog-model.md): proposal only. It names relationship types and says the dispute-and-hold flow is DRG-599 and is out of that slice. Open question 8 leaves publish rights as DRG-599's subject.
- **Slice action cut versus stored tokens.** `read` OQ12 and the requirements slice definition: Swap, Upgrade, and Downgrade wait for DRG-576. `read` `chk_amendment_line_action` in `V1_7__contract_documents.sql`: those tokens are allowed by the check. The check is not a mapping table.
- **Partial apply.** `read` [`specs/partial-apply.md`](specs/partial-apply.md): SKU-mapping holds are out of that slice. `read` OQ5: unit outcome `BLOCKED` is the first-slice partial-apply adoption. A contested mapping is not that outcome.
- **Executed document.** Merged #642 text: "The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED` and an audit event, which is AC-S3-5 applied to revisions."
- **DRG-609.** `read` the design note's status: a design proposal, not an ADR. Mapping validation is not routed through it.

### Forks

**599-F1. Where the mapping lives.**

| Option | Meaning |
|---|---|
| A. Catalog relationship | One effective-dated row. Dispute status points at that version. |
| B. Separate table | Source SKU, target SKUs, effective dates, version. |
| C. Both | Catalog row plus a commercial overlay sales can dispute. |

**Recommendation:** A. The DRG-599 description allows either home (Linear read). Two stores for the same pair will diverge.

**Default:** Proposed, unaccepted, owner decision required. Business policy; stays an option for the owner and is not adopted. One effective-dated catalog relationship, with dispute status on that version (option A). This note does not accept [`specs/catalog-model.md`](specs/catalog-model.md). Alternatives written out: a separate amendment SKU mapping table (option B); both a catalog row and a commercial overlay (option C). Conversion price on the relationship is pending the DRG-609 design (not accepted).

**599-F2. Dispute status and who resolves it.**

| Option | Meaning |
|---|---|
| A. Three statuses, product resolves | `Active`, `Contested`, `Resolved`. Sales may open a dispute and may not clear it. |
| B. Add `Rejected` | Product can refuse the mapping. The hold remains. |
| C. Sales resolves | The disputing seller clears the hold. |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Business policy; stays an option for the owner and is not adopted. Statuses `Active`, `Contested`, and `Resolved`; product resolves; sales may open and may not clear (option A). Alternatives written out: add `Rejected` (option B); sales clears the hold (option C). No SLA and no notification sender are in this default. DRG-591 stays parked (`read` OQ25).

**599-F3. Where the hold bites.**

| Option | Meaning |
|---|---|
| A. Before `IN_REVIEW`, immediately before `EXECUTED`, and before apply | Recheck the dispute status of the pinned mapping version at all three gates; the whole document waits when contested. |
| B. Apply only | The document can execute while contested. |
| C. Per line | Contested lines are partial-apply units. |

**Recommendation:** A. The DRG-599 description says the amendment cannot proceed to execution or apply while any line uses a contested mapping (Linear read).

**Default:** Proposed, unaccepted, owner decision required. Business policy; stays an option for the owner and is not adopted. The whole document waits before `IN_REVIEW`, immediately before the transition to `EXECUTED`, and again before apply (option A). Each gate rechecks dispute status for the pinned mapping version; a contest raised during review blocks execution. The execution check must be serialized with dispute changes so a concurrent contest cannot pass a stale check. Alternatives written out: the hold is apply-only (option B); contested lines become partial-apply units and other lines apply (option C). S3 and S4 are not authorized to implement the hold. S3 text is the #642 quotation. S4 is not authorized (`read` register OQ5).

**599-F4. Which lines need a mapping.**

| Option | Meaning |
|---|---|
| A. SKU change only | Same-SKU Add, Update, and Remove need no row. |
| B. Every line | Including same-SKU quantity edits. |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Business policy; stays an option for the owner and is not adopted. A mapping is required only when the source SKU and the target SKU differ (option A). Alternatives written out: every line requires a mapping, including same-SKU edits (option B).

**599-F5. Pin versus live row.**

| Option | Meaning |
|---|---|
| A. Pin at pricing | A later contest holds documents that pinned that version and are not yet executed. |
| B. Live row at apply | A mapping edit after pricing changes what apply reads. |

**Recommendation:** A, together with the quoted #642 immutability sentence for an executed document.

**Default:** Proposed, unaccepted, owner decision required. Business policy; stays an option for the owner and is not adopted. The line pins the relationship version at pricing (option A). Alternatives written out: apply reads the live row (option B). For an executed document, the #642 text quoted in this section says the document is immutable.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-576 and [`specs/catalog-model.md`](specs/catalog-model.md) | Proposed types. Not accepted. |
| DRG-421 | Named by DRG-576 as the version pin (Linear read of DRG-576 on 2026-10-17). Not landed as that pin in the product migrations listed above. |
| `chk_amendment_line_action` | Stored action tokens a type would match. |
| DRG-565 / OQ5 | A different hold. |
| DRG-590 | Conditional rules. Parked. `read` OQ24. |
| DRG-591 | Wider approval design. Parked. |
| DRG-609 | Conversion price and any priced mapping. Design not accepted. |

### What it unblocks

An owner decision is the input to a later relationship-and-dispute design. It does not add a product migration, start S3, authorize S4 or S6, or accept the DRG-609 design.

---

## 5. DRG-550 — Price policy, credit, and termination fee

**Issue:** [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) (RA-16). Linear read 2026-10-17: Medium, Backlog. Relations read: DRG-583, DRG-589, DRG-542, DRG-534, DRG-603, DRG-572, DRG-564, DRG-547.

Proration is the Accepted ADR [`docs/renewals-amendments/prereq/adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md). `read` these two header lines, quoted exactly:

> **Status:** Accepted 2026-10-16 (owner approval in chat, 4:26 PM CT)
> **Number:** none. Do not assign one in this draft. Do not copy this file into `docs/adr/` and do not add a row to `docs/adr/README.md` until the owner accepts it and assigns a number.

The number on that Accepted ADR is `none`. This section does not assign a different number and does not restate the formula. The issue title and description still name proration (Linear read). Those words are not a fork here.

### Problem

DRG-550 asks for an amendment price policy, a credit policy, and a termination fee (Linear read). The mode names `RetainContracted`, `List`, and `Override` appear in [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) section 1 item 4 (`read`), in the same sentence as a proration-method list. The proration-method list is not decided in this note. OQ9 in the register still says pricing-service support is unverified and blocks S5 (`read`). This pass did not re-read `QuoteContinuationPolicy.java` (`unverified`).

### Already on main

- **Proration ADR.** Number quoted above: `none`. Body not restated. `docs/adr/README.md` has no proration row (`read` search of that file).
- **Money columns, no policy columns.** `read` `credit_total`, `tcv_delta`, `arr_delta`, and `one_time_total` on `amendment_documents` in `V1_7__contract_documents.sql` (column list in that `CREATE TABLE`). A search of `backend/services/contract-asset-management/src/main/resources/db/migration` for `price_mode`, `credit_policy`, `termination_fee`, `amendment_mode`, and `merged_from` found none of those tokens. `apply_status` appears only in the V1_7 comment that says the column is absent. A live database catalog was not queried (`unverified`).
- **DRG-609.** `read` [`story-drg-609-pricing-service-continuation-design.md`](story-drg-609-pricing-service-continuation-design.md): design proposal, not an ADR. S5 stays outside this note. Conversion price is pending that design.
- **Audit trail.** `read` [`story-drg-564-policy-decision-audit-trail.md`](story-drg-564-policy-decision-audit-trail.md) is a separate note. This frame does not accept it.
- **Parked cancellation.** `read` OQ23: mid-term customer cancellation is parked (DRG-589). This section does not unpark it.
- **Ramps.** DRG-534 was read on Linear on 2026-10-17 as a relation. Its body was not re-opened this pass (`unverified`). This section does not decide ramp locks.
- **OQ10 on the mid-term spec.** `read` mid-term spec open question OQ10: whether a mid-term downsell is permitted before a minimum commitment ends, and its credit or fee policy, remains open (the spec points that question at RA-16). The defaults below do not close it.

### Forks

**550-F1. Price mode.**

| Option | Meaning |
|---|---|
| A. `RetainContracted` only | `List` and `Override` are refused. |
| B. Three modes | `RetainContracted`, `List`, `Override`. Omitted mode means `RetainContracted`. |
| C. Fourth mode | Flat adjustment as its own mode. |

**Recommendation:** B. Flat adjustment is recorded as `Override` or as a delta on `RetainContracted`, not as a fourth mode name.

**Default:** Proposed, unaccepted, owner decision required. The line may be `RetainContracted`, `List`, or `Override`, and an omitted mode means `RetainContracted` (option B). Alternatives written out: `RetainContracted` only (option A); a fourth mode named flat adjustment (option C). `Override` still has to meet mid-term V14 when that rule is implemented (`read` V14 in the mid-term spec: actor, reason code, overridden value, and approval). This note does not implement V14. The proration ADR is not edited.

**550-F2. Credit policy.**

| Option | Meaning |
|---|---|
| A. `NoCredit` | No credit line from this policy. |
| B. `ProratedUnused` | A policy name only. Any arithmetic stays in the Accepted ADR whose number is quoted above. |
| C. `FullRemaining` | A policy name only. Any arithmetic stays in the Accepted ADR whose number is quoted above. |
| D. Seller picks, no tenant default | Every downgrade states a policy or is refused. |

**Recommendation:** The set is A, B, and C. The value in force is A.

**Default:** Proposed, unaccepted, owner decision required. `NoCredit` (option A). A shortened co-term under 554-F4 also writes no credit under this default. Alternatives written out: `ProratedUnused` (option B); `FullRemaining` (option C); the seller must pick on every line and there is no tenant default (option D). Any conversion price is pending the DRG-609 design (not accepted). Mid-term OQ10 stays open (`read`). The proration ADR is cited and not restated.

**550-F3. Termination fee.**

| Option | Meaning |
|---|---|
| A. `NoFee` | No fee. |
| B. Flat amount | A configured amount, snapshotted on the decision. |
| C. Percent of remaining contract value | A configured percent. How that value is counted stays in the Accepted ADR whose number is quoted above. |
| D. Cut-off window | A fee applies only before a named date. |

**Recommendation:** A now. B and C are configured values an owner can turn on in a later decision. D is not in the recommended set.

**Default:** Proposed, unaccepted, owner decision required. `NoFee` (option A). Alternatives written out: a flat amount (option B); a percent of remaining contract value (option C); a cut-off window (option D). DRG-589 stays parked (`read` OQ23). The proration ADR is cited and not restated.

**550-F4. Where the three policies are stored.**

| Option | Meaning |
|---|---|
| A. Tenant default and per-line override | Snapshotted when priced. Apply reads the snapshot. |
| B. Per document | Every line shares one mode, one credit policy, and one fee. |
| C. Pricing-service store | Pending the DRG-609 design (not accepted). |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. A tenant default plus a per-line override, snapshotted when priced (option A). Alternatives written out: one mode, one credit policy, and one fee for the whole document (option B); pricing-service owns the store (option C), which is pending the DRG-609 design (not accepted). No pricing-service contract is added. DRG-564 remains the separate audit-trail note.

### Dependencies

| Dependency | Why it is named |
|---|---|
| [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) | Cited. Not reopened. Not restated. |
| DRG-542, DRG-603 | Linear read 2026-10-17: Done. That status is not a restatement of the formula. |
| DRG-609, DRG-583 | Design not accepted. Conversion price pending that design. S5 is not started by this note. |
| DRG-564 | Separate audit note. Not accepted here. |
| DRG-534 | Ramps. Out of this section. Body `unverified` this pass. |
| DRG-572 | One-time lines. Relation title read 2026-10-17. Body not re-opened (`unverified`). |
| DRG-589 | Parked. `read` OQ23. |
| DRG-554 | Date choice. Credit stays `NoCredit` under 550-F2. |
| Mid-term OQ10 | Minimum-commitment downsell. Still open. `read`. |

### What it unblocks

An owner decision is the input to a later price, credit, and termination-fee policy. It does not change the proration ADR, edit Drools, add a pricing-service migration, start S5, or authorize S6.

---

## What an owner decision on this note still does not do

A decision on any row in the owner-decision table:

- accepts no ADR and edits no ADR;
- leaves ADR-017 and ADR-024 through ADR-027 Proposed;
- leaves [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) Accepted and unchanged;
- does not start S3 / DRG-581 (merged #642 text: "S3 / DRG-581 requires separate owner authorization.");
- does not authorize S6 / DRG-584;
- does not accept the DRG-609 design;
- does not specify entitlements or conversion price;
- keeps the adopted OQ7 rule in `V1_8__contract_header_backfill.sql` and [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md): one thin contract per existing subscription, and does not select a regrouping;
- authorizes no code, migration, flag, or deploy.

Tickets stay In Review until the owner merges.
