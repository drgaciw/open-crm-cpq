# OQ6 owner decision: amendment revision when a LifecycleChange is superseded mid-flight

**Status:** OPEN. Awaiting the owner. This file does not decide OQ6, does not accept a Proposed spec, and does not accept an ADR. It authorizes no code, schema, migration, flag, topic, gateway route, or deploy. It does not authorize S3, S4, or S6.

**Date:** 2026-10-16. **Project:** OPEN-CRM-CPQ. **Backlog:** S3 is RA-47 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581/s3-amendment-document-api-and-state-machine-draft-to-executed-gapless). S4 is RA-48 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582/s4-document-driven-apply-after-executed-with-multi-subscription).

**Reading base:** `main` at `e3ee211b4d748c9a5421becaec37d629fedf174d`. Citations below are files read at that commit, or Linear issues DRG-581 and DRG-582 read the same day. Claims not opened in this pass are marked **Unverified**.

**Register.** The OQ6 row is left as it stands on this `main`: status **DEFAULT PROPOSED**. This file does not edit `renewals-amendments-open-questions-register.md`.

**What this frame is.** [PR #621](https://github.com/drgaciw/open-crm-cpq/pull/621) merged the S3–S8 authorization gate (`prereq/story-s3-s4-authorization-gate.md`). That note names OQ6 and says no file it read contains a menu titled for the mid-flight edge. The labels A–D below are this frame's grouping of behaviors that are already written. They are not a menu copied from the gate, the spike, or the register.

## 1. The question

When a LifecycleChange is superseded mid-flight, what happens to the amendment document revision that already exists for that change?

ADR-035 asks it as an out-of-scope edge: "Revision model edge cases when LifecycleChange is superseded mid-flight" (`adr-035-amendment-document-placement.md`). The acceptance checklist calls these "revision edge cases" and places them after ADR-035 acceptance and before S3 (`adr-035-acceptance-checklist.md`).

The open-questions register records the question as **OQ6 Revision model**, status **DEFAULT PROPOSED**, blocking **S3**, owner **Architect**, needed before S3. The proposed default in that row is: new revision under the same amendment number; one EXECUTED revision (V11); drafts only edit (`renewals-amendments-open-questions-register.md`).

Spike decision 6 is the same sentence: a new LifecycleChange or quote revision creates a new document revision under the same amendment number, and only one revision can be executed (`amendment-document-design-spike.md`). Field schema §2.4 is marked **dep OQ6**, with that default (`amendment-entity-field-schema.md`).

ADR-035 names the edge. It does not define which states count as mid-flight, and it does not list options. The gate note records that absence. This file uses **mid-flight** for a new revision of a LifecycleChange that already has an amendment document, while that document is not yet in a terminal non-executed state and has not yet closed a revision as `EXECUTED`. The window after `EXECUTED` and before apply finishes is the case the gate says the register default does not cover. That window is included in the options because it is the unspecified edge, not because ADR-035 defined it as mid-flight.

## 2. Proposed default

**Proposed default, not adopted by this file:** option A. Read the register sentence as a cut at `EXECUTED`.

Operational reading of that default:

1. **One document per LifecycleChange id.** `V1_7__contract_documents.sql` already has `uq_amendment_document_lifecycle` on `lifecycle_change_id`. A new revision of the same id stays on that document. A new LifecycleChange id is a different document.
2. **Before any revision has closed `EXECUTED`, a new LifecycleChange revision opens a new document revision** under the same document and the same provisional number. The previous revision's `status_at_close` becomes `SUPERSEDED` and `frozen_at` is set, so the freeze trigger rejects later updates and deletes of that revision and of its lines. The document status returns to `DRAFT`. `current_revision_no` and the header `lifecycle_change_revision` move to the new revision. `amendment_seq` and `amendment_number` stay null. Pricing pins on the new revision are cleared.
3. **At most one revision closes `EXECUTED`.** That is V11. The partial unique index `uq_amendment_revision_one_executed` already rejects a second `EXECUTED` row. `executed_revision_no`, `executed_at`, `amendment_seq`, and `amendment_number` are write-once in `reject_amendment_document_write_once()`.
4. **Once a revision has closed `EXECUTED`, a further revision of that same LifecycleChange does not open a document revision and does not change the executed rows.** The command is refused and an audit event is written (AC-S3-5, A1). This covers `EXECUTED` before apply, and the later statuses `APPLIED`, `PARTIALLY_APPLIED`, and `APPLY_BLOCKED`. The correction is a new LifecycleChange id, which creates a new document. It is not `:rebase`, and it is not a second revision of the executed document.
5. **Drafts only edit** means points 2 and 4 together. The gate note says that reading is an inference. This file proposes it as the default. It does not record it as decided.
6. **The first-slice remedy after `EXECUTED` stays the correcting amendment already recorded for OQ5.** On this `main` the OQ5 register cell is **ADOPTED 2026-10-16, first slice only**, and `prereq/partial-apply-rules-oq5-option-a.md` says the remedy is a new amendment document and that rebase is not a first-slice remedy. This default uses that remedy for the post-`EXECUTED` window. It does not reopen OQ5, and it does not accept `specs/partial-apply.md`.
7. **Document-level `SUPERSEDED` is a different event.** The spike state table uses it when a later `AMENDED_AND_RESTATED` document executes. Applied facts are not rewritten; only links are added. That is not a LifecycleChange revision closing `SUPERSEDED`.

A yes to this default adopts those seven points for the first slice. It does not accept `prereq/DRAFT-openapi-amendments.yaml`. It does not authorize DRG-581, DRG-582, or DRG-584. ADR-035's acceptance text authorizes S1 and S2 only.

Whether the new revision requires a new approval is not one of the seven points. The OpenAPI revise operation returns status `DRAFT` and clears pricing pins. The spike mapping says a re-delivered approval for the same revision is a no-op. No sentence read here says the header `approval_ref` is cleared, or that a new approval is required before the new revision can be submitted. That choice stays in section 10.

## 3. Options

| Option | What the owner would be choosing | Where the behavior is written |
|---|---|---|
| **A. Cut at EXECUTED (proposed default)** | Before `EXECUTED`, a new LifecycleChange revision freezes the previous document revision as `SUPERSEDED` and opens a new one under the same number, status `DRAFT`. At and after `EXECUTED`, the executed revision stays. The new LifecycleChange revision is refused and audited. The correction is a new LifecycleChange id. | Register OQ6 sentence; schema §2.4; spike decision 6 and the revision row of the state table; spec §3 transition back to `DRAFT`; OpenAPI `POST /lifecycle-changes/{id}/revisions` refusal `IMMUTABLE_EXECUTED`; AC-S3-5; A1; V11 |
| **B. New document revision after `EXECUTED` as well, until apply finishes** | The same-number model continues through `EXECUTED` while `applied_at` is still null. The executed revision would have to stop being the executed revision, or a second revision would also have to be able to execute. | Not written as a rule. It is the gap the gate names. The schema, A1, AC-S3-5, and the OpenAPI refusal were read and reject this unless those constraints change |
| **C. No new document revision once a document exists** | A LifecycleChange revision after create does not open a document revision, including while the document is `DRAFT` or `IN_REVIEW`. The seller withdraws or starts a new LifecycleChange id. | Not the register default. The spec §3 transition and spike decision 6 were read and say the opposite for the pre-execution states |
| **D. Leave the default unconfirmed** | OQ6 stays **DEFAULT PROPOSED**. S3 does not treat the mid-flight rule as an accepted requirement. | Register status legend; gate note "What stays blocked" for OQ6 |

The register legend says DEFAULT PROPOSED means work can start on the stated default and the default is confirmed by the date given. This file asks for that confirmation. It does not record it.

## 4. What S1 and S2 already store

Read in CAM on this `main`. These rows are the schema and the event writer. They are not a state machine. `AmendmentDocument` says state transitions are DRG-581 and that the mapping only stores the row.

| Fact | Where it was read |
|---|---|
| One document per `lifecycle_change_id` | `uq_amendment_document_lifecycle` in `V1_7__contract_documents.sql` |
| One document revision per (`document_id`, `lifecycle_change_revision`) | `uq_amendment_revision_lifecycle` |
| `status_at_close` may be `OPEN`, `SUPERSEDED`, `EXECUTED`, `REJECTED`, `WITHDRAWN`, `EXPIRED` | `chk_amendment_revision_status`; Java `RevisionCloseStatus` |
| A second `EXECUTED` revision for one document is rejected | Partial unique index `uq_amendment_revision_one_executed`. Test `acS13_secondExecutedRevisionIsRejected` expects that index name |
| A revision with `frozen_at` set cannot be updated or deleted. Its lines cannot be inserted, updated, or deleted | `reject_frozen_amendment_revision_mutation()`; `V1_7_2__freeze_amendment_line_membership.sql` |
| `executed_revision_no`, `executed_at`, `amendment_seq`, and `amendment_number` are write-once. Header `lifecycle_change_revision` is not in that trigger | `reject_amendment_document_write_once()` |
| Document `status` check list has no `SUPERSEDED` | `chk_amendment_document_status`. Values include `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID`. Java `AmendmentDocumentStatus` matches that list |
| Application units are keyed by (`document_id`, `unit_no`). The unit table has no `revision_no` | `pk_amendment_application_units` |
| Events are append-only. `event_type` is text. `revision_no` is optional. The writer does not choose a supersede transition | `AmendmentDocumentEvent`; `AmendmentEventWriter` |

No production Java type under `contract-asset-management` constructs an `AmendmentDocumentRevision` except the entity and its repository. The schema integration test and the event-chain integration test save rows. A search of `backend/services/contract-asset-management` for `AmendmentDocumentRevision` returned those files and no transition service. **Unverified** outside that service tree.

## 5. Consequences for S3 / DRG-581

S3 is the amendment document API and state machine. Linear DRG-581, read 2026-10-16, was in **Todo** (`statusType` unstarted). Its description lists the OQ6 revision default as a dependency and names "revision semantics (OQ6)" as a risk. It blocks DRG-582 and DRG-584. It is blocked by DRG-605, DRG-579, and DRG-580. The gate file, written earlier the same day, says the Linear status then read was Backlog. Both sentences were read. The Todo value is the later Linear read. It is not a fact stored in git.

Written S3 rules that already exist and do not mention a superseded LifecycleChange:

- **FR-05.** Idempotent create from a LifecycleChange, unique `lifecycle_change_id`.
- **FR-06.** States follow spec §3. Provisional number at `DRAFT`. Final `amendment_seq` at `EXECUTED`.
- **AC-S3-1.** The same `lifecycle_change_id` posted twice creates one document.
- **AC-S3-5.** Any update to an `EXECUTED` document is rejected and audited (A1).
- **AC-S3-7.** A withdrawn draft consumes no number.

The OpenAPI draft, still `x-opencrm-status: proposal`, says a second create with the same `lifecycleChangeId` and a different `lifecycleChangeRevision` or a different content digest is `409 IDEMPOTENCY_CONFLICT`, and that a further edit is a new revision, not a second create. `GET /amendments/{id}/revisions` is tagged S3. The operation that opens the new revision is `POST /lifecycle-changes/{id}/revisions`, tagged S5. Its description says the previous revision is frozen `SUPERSEDED`, status returns to `DRAFT`, pins are cleared, and a new revision of an already `EXECUTED` document is `IMMUTABLE_EXECUTED`. Which service writes the new `amendment_document_revisions` row is not named. **Unverified** as an assigned class.

**If the owner confirms option A:** S3's state machine has two answers. Before `EXECUTED`, it freezes the current revision as `SUPERSEDED` and stores the new one as the current `DRAFT`. At and after `EXECUTED`, it refuses and writes the audit event AC-S3-5 already requires. AC-S3-1 stays the create rule: a different `lifecycleChangeRevision` does not create a second document. None of AC-S3-1 through AC-S3-8 is a superseded-mid-flight test. Confirming option A does not add that test to the requirements file. S3 stays unauthorized until a later decision says DRG-581 may start.

**If the owner chooses option B:** AC-S3-5 is not the rule for an `EXECUTED` document that has not been applied. The partial unique index, the write-once trigger, and the freeze trigger were read in S1. They reject a second `EXECUTED` row and reject mutation of a frozen revision. Option B needs a schema change before S3 can implement it. This file does not authorize that migration. S3 stays unauthorized either way.

**If the owner chooses option C:** The spec §3 transition from `IN_REVIEW` or `OUT_FOR_SIGNATURE` back to `DRAFT` on a new revision is not the S3 contract. S3's written acceptance criteria still do not mention a revise command. The register default would be unconfirmed in the other direction. S3 stays unauthorized.

**If the owner chooses option D:** S3 stays behind the unconfirmed default. ADR-035 still authorizes S1 and S2 only. The gate note says the same.

## 6. Consequences for S4 / DRG-582

S4 is document-driven apply after `EXECUTED`. Linear DRG-582, read 2026-10-16, was in **Todo**. It is blocked by DRG-581. Its description says the OQ5 partial-apply default must be confirmed before start. On this `main` the register records OQ5 as adopted for the first slice, and that same cell says S4 may not start: it still waits on S3 / DRG-581, OQ6, and DRG-562/563. This file does not edit that cell and does not treat it as an authorization of S4.

Written S4 rules that assume one executed document:

- **AC-S4-8.** Apply of a document that is not `EXECUTED` is refused.
- **V10.** The content digest at apply equals the digest of the executed revision. A mismatch blocks apply.
- **FR-09 / technical approach §3 item 3.** Apply runs per unit after `EXECUTED`, with a ledger row per (`document_id`, `unit_no`).

**If the owner confirms option A:** S4 applies the one revision that closed `EXECUTED`. A LifecycleChange revision that arrived after that close was refused, so it does not change the digest V10 checks. A `PARTIALLY_APPLIED` or `APPLY_BLOCKED` outcome is repaired by a new document, which is the OQ5 remedy already recorded. `:rebase` stays tagged later in the OpenAPI draft and is not an S4 command under this default. S4 stays unauthorized.

**If the owner chooses option B:** S4's apply target is unspecified. V10 names the executed revision. Option B would have two candidates if the executed close could move. The files read here do not say which digest apply checks in that window. AC-S4-2 rejects the same unit key with a different digest. A new revision that reused `unit_no` on the same document would hit that rejection after any unit had committed. S4 cannot treat option B as a requirement until that write rule exists. This file does not write it.

**If the owner chooses option C:** S4 still applies one executed revision, because no second document revision was stored. The pre-execution revise path is absent. S4 stays unauthorized, and it still waits on S3.

**If the owner chooses option D:** S4 stays behind DRG-581 and the unconfirmed OQ6 default. The OQ5 cell's "waits on OQ6" sentence remains the register text.

## 7. Consequences for the apply ledger (V1_9)

The technical approach, section 3, item 3, assigns **V1_9** to S4:

> new `document_apply_ledger` with a unique key (`document_id`, `unit_no`) and stored `apply_key_digest`; a replay with the same digest is a no-op and a different digest is rejected and audited; keep `applied_quote_acceptances` unchanged for the legacy path.

That paragraph is marked **[I]**. The schema addendum of 2026-10-16 states the same shape. Schema §2.6 still says `applied_quote_acceptances` gains nullable `amendment_document_id` and `application_unit_no`. Both texts were read. This file does not choose between them.

On this `main`, CAM migrations run through `V1_8__contract_header_backfill.sql`. There is no `V1_9` SQL file. The gate note's inventory, pinned to `dd0f1a8`, stopped at `V1_6`. That inventory is earlier than this base. `V1_7`, `V1_7_1`, `V1_7_2`, and `V1_8` are on this commit. `V1_9` is still only the documented assignment.

Apply is after `EXECUTED` (OQ2, resolved). Under option A the ledger is written for units of the single executed revision. A refused post-execution LifecycleChange revision inserts no ledger row. A correcting amendment is a new `document_id`, so its unit keys do not collide with the executed document's keys.

Under option B the V1_9 key has no `revision_no`. A second apply of the same `unit_no` on the same document is the different-digest rejection the paragraph already states. Option B needs a different key, or a rule that the executed revision is the only one that ever writes the ledger. Neither rule was read. Application units in `V1_7` are also keyed only by (`document_id`, `unit_no`). A pre-execution revise under option A happens before apply, so no V1_9 row exists yet. What happens to `PENDING` unit rows when the document revision changes is not specified. The freeze triggers cover revisions and lines. They do not mention `amendment_application_units`.

Option C writes the ledger once, for the only revision. Option D leaves the ledger assignment unchanged and unimplemented.

## 8. Risks

| # | Risk | What was read |
|---|---|---|
| Mid-flight unnamed | ADR-035 names the edge and does not define the states. The cut at `EXECUTED` in option A is the inference the gate already flagged. | ADR-035 open questions; gate OQ6 section |
| Second EXECUTED row | The partial unique index and `acS13_secondExecutedRevisionIsRejected` reject it. Option B cannot store two `EXECUTED` revisions on this schema. | `V1_7__contract_documents.sql`; `ContractDocumentsSchemaIntegrationTest` |
| Frozen executed revision | Once `frozen_at` is set, the revision and its lines cannot change. The spike says a revision is immutable once Superseded or Executed. The trigger keys off `frozen_at`, not off `status_at_close`. Whether execute sets `frozen_at` is the technical approach control-flow step 5 ("freeze revision"). The trigger does not set `frozen_at` by itself. | Spike revision paragraph; technical approach §5 step 5; freeze functions |
| Document status `SUPERSEDED` | The OpenAPI `AmendmentStatus` enum includes `SUPERSEDED` and says it is reserved for later restatement. The SQL check and the Java enum do not include it. Option A sets the revision to `SUPERSEDED` and the document back to `DRAFT`. It does not set document status `SUPERSEDED`. | OpenAPI `AmendmentStatus`; `chk_amendment_document_status`; `AmendmentDocumentStatus` |
| Create versus revise | A different `lifecycleChangeRevision` on create is `409 IDEMPOTENCY_CONFLICT`. The revise operation is tagged S5, and the document list operation is tagged S3. The call from quote-service to CAM that inserts the new revision row is not named. | OpenAPI create and revise operations |
| Units are not revision-scoped | Lines belong to a revision. Units belong to the document. A new pre-execution revision can freeze the old lines and leave the unit rows in place. No rule read here says whether those rows are replaced. | `V1_7` unit primary key; line freeze trigger |
| Ledger key | V1_9, as written, is (`document_id`, `unit_no`). Option B collides with a committed unit. The paragraph is **[I]** and the SQL file is absent. | Technical approach §3; schema addendum; migration list on this `main` |
| Event name | Spike §6.6 lists `AmendmentSuperseded`. The OpenAPI `EventType` enum includes `AMENDMENT_SUPERSEDED`. The gate's AC-S6-4 list does not include a superseded event. S2 stores whatever `event_type` string the caller passes. No caller for a revision supersede was found in CAM main sources. | Spike §6.6; OpenAPI `EventType`; gate AC-S6-4; `AmendmentEventWriter` |
| Approval on the new revision | Returning to `DRAFT` and clearing pins is written. Clearing `approval_ref` is not. A stale approval ref on the header would still be stored. | OpenAPI revise description; spike approval row; `amendment_documents.approval_ref` |
| OQ5 rebase | The adopted first-slice remedy is a new document. Spike §4 rule 5 and `specs/partial-apply.md` AC-12 still describe a rebase revision. `:rebase` is tagged later. Option A does not make rebase the mid-flight path. | Register OQ5 cell; `partial-apply-rules-oq5-option-a.md`; OpenAPI `:rebase` |
| Renewal and price lock | The renewal stub says renegotiation creates revisions under one renewal number. The price-lock stub says schedule edits in negotiation create revisions. Neither states the `EXECUTED` cut. Both policies are outside the first slice. | `renewal-policy-stub.md` §6; `price-lock-policy-stub.md` §6 |
| Approval-routing `SUPERSEDED` | approval-routing supersedes an `ApprovalRequest`. That status is a different table from `amendment_document_revisions.status_at_close`. | `ApprovalStatus` and `DiscountApprovalEventConsumer` were opened far enough to see the request-level status. The amendment revision table is not that table |
| OQ18 timing | The gate records three timings for the canonical digest library: register OQ18 before S3 in `backend/libs/common`, the OpenAPI draft calling the location still open, and technical approach §2 assigning the utility to S6. This decision does not choose a location. | Gate OQ6 consequences; register OQ18 row was read |
| Scope of ADR-035 | Confirming OQ6 does not extend ADR-035. Acceptance authorizes S1 and S2 only. | ADR-035 acceptance section |
| Register sentences about S4 | The OQ6 row says OQ6 blocks S3. The OQ5 cell says S4 waits on OQ6. Both were read on this commit. This file does not reconcile the cells. | Register rows OQ5 and OQ6 |
| Rules-note drift | `partial-apply-rules-oq5-option-a.md` still says the OQ5 register row is `DEFAULT PROPOSED` and that the note does not edit the register. The register cell on this same commit says **ADOPTED 2026-10-16**. This file uses the register cell for status and the rules note for the seven adopted points. It does not edit either file. | Both files at `e3ee211b` |

## 9. What stays open even if the owner confirms the default

1. Whether header `approval_ref` is cleared when the document returns to `DRAFT` (section 2, after the seven points).
2. Which command and which service insert the new document revision. The OpenAPI revise path is tagged S5. S3 lists a read of revisions.
3. What happens to `PENDING` application units, which are not revision-scoped, when a pre-execution revision is superseded.
4. Whether a `WITHDRAWN` or `REJECTED` document can be revised, or whether those states stay terminal. Spec §3 lists both as terminal. The OpenAPI revise list is LifecycleChange statuses `DRAFT`, `PRICED`, `IN_APPROVAL`, `APPROVED`, and `PRESENTED`, not those document statuses. The mapping between the two enums is the spike table. A line-by-line match of `PRICED` and `PRESENTED` onto document statuses was not established beyond that table.
5. The event type written for a pre-execution supersede, and whether S6 must register it. AC-S6-4, as quoted in the gate, does not name it.
6. Rebase after `APPLY_BLOCKED` or `PARTIALLY_APPLIED`. Out of this default. OQ5's adopted remedy is a new document.
7. Document-level `SUPERSEDED` for `AMENDED_AND_RESTATED` (register OQ16, not blocking).
8. OQ18, the digest library location.
9. Authorization of DRG-581, DRG-582, and DRG-584. A yes to option A is not that authorization.

DRG-581, as read in Linear, asks for the API and the state machine from draft to executed, including the OQ6 dependency. This file does not mark DRG-581 done and does not move it.

## 10. Owner decision needed

Blank until the owner writes it. This file does not fill it in. Filling it in a later change is the decision. This section, left blank, keeps OQ6 **OPEN**.

- Decision: A, B, C, or D.
- Date:
- The cut is `EXECUTED`: a new LifecycleChange revision opens a document revision only before that close. Yes or no. The proposed default is yes.
- After `EXECUTED`, the correction is a new LifecycleChange id and a new document. Yes or no. The proposed default is yes.
- Rebase is a first-slice answer to this edge. Yes or no. The proposed default is no.
- Header `approval_ref` is cleared on the new pre-execution revision. Yes or no. The proposed default does not choose.
- Authorize DRG-581 (S3) in that later record. This file is not that authorization.
- Authorize DRG-582 (S4) in that later record. This file is not that authorization.

## 11. Sources read

| Source | Used for |
|---|---|
| `prereq/story-s3-s4-authorization-gate.md` | OQ6 section; the statement that no titled menu was found; S3/S4 blocked; V1_9 assignment as the gate recorded it; AC-S6-4 event list; OQ18 timing |
| `renewals-amendments-open-questions-register.md` | OQ6 row, left unchanged; OQ5 cell as adopted on this `main`; OQ16; OQ18 |
| `adr-035-amendment-document-placement.md` | Open-question wording; acceptance scope S1 and S2 |
| `adr-035-acceptance-checklist.md` | OQ6 before S3 |
| `amendment-document-design-spike.md` | Decision 6; revision immutability; state table including both `SUPERSEDED` rows; §4 rule 5 rebase; §6.6 event names |
| `amendment-entity-field-schema.md` | §2.4; §2.6; §4 V11; V1_9 addendum |
| `mid-term-amendment-policy-spec.md` | §3 transition; V10; V11; A1; OQ6 line in §8 |
| `amendment-first-slice-requirements-and-acceptance.md` | FR-05, FR-06, AC-S3-1, AC-S3-5, AC-S3-7, AC-S4-8, S3 and S4 dependency lines |
| `amendment-technical-approach-and-dependencies.md` | V1_9 paragraph; control-flow step 5; §2 digest utility on S6 |
| `prereq/DRAFT-openapi-amendments.yaml` | Create idempotency; revise operation; `:rebase`; `AmendmentStatus`; `RevisionCloseStatus`; `EventType`. Header still says proposal |
| `prereq/partial-apply-rules-oq5-option-a.md` | Adopted remedy is a new document; rebase is not a first-slice remedy; the file's own register sentence, which does not match the register cell |
| `prereq/specs/partial-apply.md` | Status line still says "Proposal only" and that mid-term OQ5 stays open. Open question 6 and AC-12 describe rebase. This default does not adopt that file. The rest of the file was not re-read. **Unverified** beyond the status line, open question 6, and AC-12 |
| `renewal-policy-stub.md` | OQ6 line |
| `price-lock-policy-stub.md` | OQ6 line |
| `V1_7__contract_documents.sql` | Document, revision, unit, and event constraints; write-once trigger; revision freeze trigger |
| `V1_7_2__freeze_amendment_line_membership.sql` | Line freeze on insert, update, and delete |
| CAM migration directory | Files present through `V1_8`; no `V1_9` |
| `AmendmentDocument`, `AmendmentDocumentRevision`, `AmendmentDocumentStatus`, `RevisionCloseStatus`, `AmendmentDocumentEvent`, `AmendmentEventWriter` | Stored shape; no transition |
| `ContractDocumentsSchemaIntegrationTest` | `acS13_secondExecutedRevisionIsRejected` |
| Linear DRG-581, DRG-582 | Title, description, status Todo, relations, read 2026-10-16 |

## 12. Unverified

- Any production writer that inserts `amendment_document_revisions` outside `contract-asset-management`. The search was limited to that service.
- The quote-service class that would call CAM when `POST /lifecycle-changes/{id}/revisions` succeeds. No `class LifecycleChange` was part of this pass. The S5 readiness note, read only for its OQ6 sentence, already says a workspace search found no such Java type. That search was not repeated.
- Whether execute sets `frozen_at`. The technical approach says the execute transaction freezes the revision. The trigger does not set the column.
- Whether `PENDING` units are replaced, left in place, or forbidden while a revision is `OPEN`.
- The rest of `specs/partial-apply.md` beyond its status line, open question 6, and AC-12.
- DRG-562 and DRG-563 bodies. The register's OQ5 cell names them as remaining S4 waits. Their descriptions were not opened.
- Linear status as a git fact. Todo was returned by Linear on 2026-10-16. The gate file's Backlog sentence is the earlier read stored in git.
- GitNexus impact for this file. `list_repos` returned no indexed repository. Impact is **UNKNOWN**. An empty caller set was not treated as evidence that the new file is unused.
- Any option for this edge that lives outside `docs/renewals-amendments/`, `docs/adr/`, the CAM files named above, and the two Linear issues. Absence from those reads is not proof that no other note exists.
