# Cluster G — visibility, queues, and in-flight migration

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Base:** `main` at `0d71251d`. **File:** this note only.

**Stories:** [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595) (RA-61), [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) (RA-63), [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598) (RA-64). Read on 2026-10-17 with relations. Comments on all three were empty. Linear status of each was Backlog. This note does not move a ticket.

**What this file is.** One cluster decision frame. The call decisions on the three tickets are the problem statements. Every fork below is still open. Accepting this file authorizes no code, migration, flag, topic, gateway route, or deploy.

No story was skipped. Each description is a design scope (role model, queue definitions, phase rules). None asks this change to write code.

## Constraints this frame keeps

These are inputs. They are not forks, and this note does not reopen them.

| Input | Status used here | Where it is written |
|---|---|---|
| ADR-035 | Accepted 2026-10-16. Authorizes S1 and S2 only. Thin contract header. | `docs/renewals-amendments/adr-035-amendment-document-placement.md` |
| ADR-016 | Accepted 2026-09-23. Record-level authorization stays deferred (decision 7). | `docs/adr/ADR-016-identity-authentication-service-trust.md` |
| ADR-017 | Proposed 2026-10-04. | `docs/adr/ADR-017-synchronous-inter-service-communication.md`; catalog row in `docs/adr/README.md` |
| ADR-024, ADR-025, ADR-026, ADR-027 | Proposed 2026-10-04. | `docs/adr/ADR-024-order-orchestration-context.md`, `docs/adr/ADR-025-saga-process-manager.md`, `docs/adr/ADR-026-command-messages.md`, `docs/adr/ADR-027-compensation-and-point-of-no-return.md` |
| Proration formula | Accepted 2026-10-16. | `docs/renewals-amendments/prereq/adr-amendment-proration-formula.md` |
| OQ5 option A | Adopted 2026-10-16, first slice only. | `docs/renewals-amendments/renewals-amendments-open-questions-register.md` |
| OQ6 | Adopted 2026-10-16, as recorded on `main` by merged PR #642 (`3ee3ca0f`). | Register row, and `docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md` |
| OQ7 thin contract | Header-job wording below. Register row remains **DEFAULT PROPOSED for slice**. | `docs/renewals-amendments/s7-v1-8-header-backfill.md`; register row |
| OQ18 | Proposed until the owner confirms it. | Register row |
| S3 / DRG-581 | Todo. Blocked by DRG-616, DRG-605, DRG-579, and DRG-580. Unstarted. The issue text says nothing there authorizes S3. | Linear, read 2026-10-17 |
| S6 / DRG-584 | Todo. Blocked by DRG-581 and DRG-582. Unauthorized. | Linear, read 2026-10-17; OQ5 register cell |
| DRG-609 design | In Review. The design note is a proposal. Unaccepted. | `docs/renewals-amendments/prereq/story-drg-609-pricing-service-continuation-design.md` |

OQ6, quoted exactly from the register cell written by merged #642:

> Decided under delegation by CPQ Decision and relayed by Projects Manager, not the owner's personal choice. Before execution, superseding a LifecycleChange in DRAFT, IN_REVIEW, or APPROVED creates a revision under the same document number; the old revision is frozen SUPERSEDED, the new revision returns to DRAFT, pricing pins are cleared, and approvals and signatures are void. REJECTED and WITHDRAWN stay terminal. After EXECUTED, no revision is allowed (IMMUTABLE_EXECUTED); the remedy is a new LifecycleChange or correcting amendment. At most one revision may be EXECUTED. The CAM command/API, representation of cleared pins, supersession event contract, and S3/S5 integration boundary remain open; see `prereq/oq6-revision-model-owner-decision.md`. This decision authorizes no code or story. S3 / DRG-581 needs separate owner authorization; OQ18 gates digest-library work only.

OQ7 thin-contract wording, quoted exactly from `s7-v1-8-header-backfill.md`:

> One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup.

OQ18, quoted exactly from the register cell:

> Unconfirmed proposal: canonical digest library in `backend/libs/common`, built once in S3; S5 and S6 may reuse it. Not adopted. S3 cannot start building the library before owner confirmation. This row does not authorize S6.

OQ5 option A, quoted exactly from the opening of the register cell: "Option A for the first slice only, delegated by the owner and relayed by Projects Manager: one unit per target subscription, atomic units, no automatic rollback, PARTIALLY_APPLIED when a later unit fails, APPLY_BLOCKED when no unit has committed, correcting amendment as remedy." `specs/partial-apply.md` stays unaccepted. This frame displays those statuses when they already exist on a document. It does not apply a unit.

The Accepted proration ADR stays the formula. This cluster does not restate it, replace it, or add a migration-time reprice.

ADR-029 (`docs/adr/ADR-029-durable-agent-work-queue.md`) is a Proposed `ai-service` agent queue. It is a different queue from DRG-597.

Impact: UNKNOWN (no GitNexus index). This checkout has no `.gitnexus/run.cjs`. The change is one new markdown file.

## Owner-decision table

Every fork is listed. The recommendation is the proposed default. Each default is Proposed, unaccepted, owner decision required.

| Fork | Ticket | Question | Options | Recommendation | Default |
|---|---|---|---|---|---|
| F-595-1 | DRG-595 | Who is the contract owner? | A named user id, distinct from `created_by`. B CRM account owner of `customer_account_id`, resolved on read. C `contracts.created_by`. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-595-2 | DRG-595 | Where is that owner stored? | A A fact outside the ADR-035 thin-header list. B A new thin-header column. C Derived on every read, with no stored owner. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-595-3 | DRG-595 | How does ownership transfer? | A An explicit transfer that appends an audit row and leaves executed documents unchanged. B The owner follows the CRM account owner automatically. C A stored owner plus optional CRM sync. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-595-4 | DRG-595 | What may the owner do, given parked approvals? | A Create, edit, and submit a draft once those APIs exist. Approve stays with DRG-591. Execute stays outside this note. B Act includes approve and execute. C Nobody edits until S3 is authorized. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-595-5 | DRG-595 | How are sales ops and finance named? | A Permission labels only (`contract.owner`, `sales-ops.read`, `finance.read`). ADR-016's role list stays as accepted. B Map sales ops to `DealDesk` and finance to `CFO` as an accepted mapping. C Add Entra roles in this note. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-595-6 | DRG-595 | What may read-only roles see inside a tenant? | A Every contract in the caller's tenant, including that contract's `amendment_document_events`. B Only assigned contracts. C Contracts in other tenants. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-595-7 | DRG-595 | When does the owner check arrive relative to S3? | A It is a named prerequisite. This note does not start S3, and read-only roles follow later. B Accepting this note starts S3. C S3 ships read-only roles first and skips the owner check. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-597-1 | DRG-597 | Where does queue membership live? | A A filter on the shared spine. Assignment and due date, if stored, are a side row keyed by `lifecycle_change_id`. B Two tables that copy the document. C One undifferentiated list. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-597-2 | DRG-597 | Which intents are in which queue? | A Renewal queue is `RENEW` only. Amendment queue is `AMEND` only. Other intents wait. B Renewal queue also includes EXTENSION because `DealType` stores it as RENEWAL. C The amendment queue includes every intent other than `RENEW`. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-597-3 | DRG-597 | Who may assign and set a due date? | A Sales ops may assign and set a due date. Finance may not. The owner may assign on owned contracts. Assignment is outside edit, submit, approve, and execute. B Only the owner may assign. C Assignment waits for DRG-591. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-597-4 | DRG-597 | Is there an SLA duration? | A An optional due date only. B Ninety days before end date, taken from the renewal stub. C No due date in this frame. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-597-5 | DRG-597 | What HTTP surface do the queues use? | A Two filters on the draft list `GET /api/v1/lifecycle-changes`. No new route. B New `/queues/renewals` and `/queues/amendments` resources in this note. C The quotes list filtered by deal type. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-598-1 | DRG-598 | What is a phase, given the S7 function? | A An owner-approved subscription set. The existing apply function is not the subset writer, and this note authorizes no new writer. B One call of `contract_header_backfill_apply` is the phase. C This note changes V1_8 to take an end-date window. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-598-2 | DRG-598 | How wide is the first end-date window? | A The owner names the window when approving the phase. B A fixed 90-day window. C Every future end date in one phase. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-598-3 | DRG-598 | Who approves an expansion, and where do metrics live? | A The project owner approves each manifest. Metric values are written on the manifest. No new column. B Sales ops approve the expansion. C Finance approves the expansion. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-598-4 | DRG-598 | Does a phase synthesize amendment history? | A Headers only. DRG-566 stays outside the phase. B The phase also writes BACKFILLED documents. C History is a later single cutover. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-598-5 | DRG-598 | How do migrated and unmigrated subscriptions coexist? | A `contract_id` null stays on the current acceptance path. A header does not switch the row onto a document apply path. B The OQ11 per-tenant flag turns on for a migrated tenant. C A new per-contract flag. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-598-6 | DRG-598 | What validation, rollback, and reconciliation exist for a phase? | A The existing whole-set count and compensate stay as documented. A subset phase has no count, apply, compensate, or report in this note. B This note defines new SQL. C Backup restore is the only undo, and the compensate function is ignored. | A | Option A. Proposed, unaccepted, owner decision required. |
| F-598-7 | DRG-598 | Which date orders the phase? | A `subscriptions.end_date`, the date S7 copies to `contracts.end_date`. B `subscriptions.renewal_date`. C The earlier of the two. | A | Option A. Proposed, unaccepted, owner decision required. |

## DRG-595 — Renewal and amendment visibility

**Linear.** [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595). Status read: Backlog. Priority Medium. Labels `renewals-amendments`, `Open-CRM-CPQ`. Relations: related to DRG-537, DRG-591, DRG-581, DRG-607, and DRG-597. No blocks and no blockers.

### Problem

Call decision 2026-10-16 (#8), as written on the issue: contracts, renewals, and amendments are visible to the contract owner, who can act, plus read-only access for sales ops and finance. Read-only means no edit, submit, approve, or execute. Read-only roles also see audit history. The issue asks who the contract owner is and how ownership transfers. It says the first slice (S3) needs at least the owner check, and that read-only roles can follow. Approver roles stay parked on DRG-591.

### What is already settled on main

- The mid-term policy clarification records the same visibility sentence and says it is not yet in that spec (`docs/renewals-amendments/mid-term-amendment-policy-spec.md`, "Audit and visibility").
- The dev-readiness addendum places visibility outside the first slice (`docs/renewals-amendments/renewals-amendments-dev-readiness.md`, addendum 2026-10-16).
- The UI spec defines no role-specific screens. It records that the OpenAPI draft lists DRG-595 role enforcement as out of this slice (`docs/renewals-amendments/prereq/story-drg-537-ui-spec.md`, section 2). The draft says the same (`docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml`, `info.description`).
- ADR-016 decision 7 lists Entra app roles `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, and `Admin`, and defers record-level and territory authorization. Actor fields come from the verified token.
- The thin header on `main` is `contracts` in `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`: `tenant_id`, `contract_number`, `customer_account_id`, `term_model`, `start_date`, `end_date`, `governing_agreement_ref`, `status`, `last_executed_seq`, `prior_contract_id`, plus audit columns. There is no owner column. OQ3 and ADR-035 D-02 keep that header thin. OQ27 puts `precedence_flag` outside it (`docs/renewals-amendments/amendment-entity-field-schema.md`, section 2.2a).
- `created_by` on a backfilled header is `V1_8_HEADER_BACKFILL` or `S7_HEADER_AT_CREATION` (`docs/renewals-amendments/s7-v1-8-header-backfill.md`). That marker is not a person.
- Audit rows for documents live in `amendment_document_events` (V1_7). S2 / DRG-580 is the hash-chain writer. This note does not publish those rows.
- Approvals are parked: `docs/renewals-amendments/renewals-amendments-parking-lot.md` PL-9, register OQ25, DRG-591. OQ10 and OQ17 stand until that item is unparked.
- S3 / DRG-581 is unstarted. The OpenAPI file is still `x-opencrm-status: proposal`.

### Options, recommendation, and default

**F-595-1. Who the owner is.**

- Option A. A named user id, distinct from `created_by`.
- Option B. The CRM account owner of `customer_account_id`, resolved when the record is read.
- Option C. `contracts.created_by`.

**Recommendation.** Option A. Option C collides with the backfill marker. Option B changes visibility when the CRM owner changes, with no transfer row.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-595-2. Where the owner is stored.**

- Option A. A fact outside the ADR-035 thin-header list, the same separation OQ27 uses for `precedence_flag`.
- Option B. A new column inside the thin header.
- Option C. No stored owner. Every read derives one.

**Recommendation.** Option A. The thin header stays the OQ3 list.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-595-3. Transfer.**

- Option A. An explicit transfer appends an audit row. Executed documents stay as OQ6 left them.
- Option B. The stored owner follows the CRM account owner with no separate transfer.
- Option C. A stored owner, plus an optional sync from CRM.

**Recommendation.** Option A. The OQ6 record quoted above refuses a revision after EXECUTED (`IMMUTABLE_EXECUTED`). A transfer does not open a revision and does not clear pricing pins.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-595-4. What "can act" includes.**

- Option A. Once a draft API exists, the owner may create, edit, and submit. Approve stays on DRG-591. Execute is outside this note because S3 is unstarted and S4 is not authorized by OQ5.
- Option B. Act includes approve and execute.
- Option C. No edit by anyone until S3 is authorized.

**Recommendation.** Option A. The call already lists approve among the acts read-only roles lack. It does not unpark who approves.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-595-5. Role names.**

- Option A. Labels `contract.owner`, `sales-ops.read`, and `finance.read` in this note only. ADR-016's accepted role list is unchanged. A later owner-accepted amendment of ADR-016 would be required before any Entra role is added. Record-level checks stay deferred under ADR-016 decision 7.
- Option B. Treat `DealDesk` as sales ops and `CFO` as finance, and treat that mapping as accepted now.
- Option C. Add SalesOps and Finance to the ADR-016 list in this note.

**Recommendation.** Option A. This note does not edit an Accepted ADR.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-595-6. Read-only scope.**

- Option A. Inside the caller's `tenant_id`, sales ops and finance see every contract, amendment document, and renewal document, and the `amendment_document_events` rows for those contracts. The owner sees the contracts they own. No other tenant is visible.
- Option B. Read-only roles see only contracts assigned to them.
- Option C. Finance may read across tenants.

**Recommendation.** Option A. The call gives those roles read-only access. Tenant isolation is already the key on `contracts.tenant_id`.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-595-7. S3 timing.**

- Option A. The owner check is a prerequisite the S3 story can cite later. Accepting this note does not authorize DRG-581, does not edit the OpenAPI draft, and does not add a check. Read-only enforcement is a later increment after that check exists.
- Option B. Accepting this note authorizes S3 to start.
- Option C. S3 ships the read-only roles and omits the owner check.

**Recommendation.** Option A. The issue says the owner check comes first. DRG-581 is Todo and blocked.

**Default.** Option A. Proposed, unaccepted, owner decision required.

Visibility checks, when an owner later authorizes them, run in the service that holds the row, on the caller token ADR-016 already describes. This note adds no synchronous edge. ADR-017 stays Proposed.

### Owner decision needed

F-595-1 through F-595-7. The call's headline (owner acts; sales ops and finance are read-only and can see audit history) is the problem statement. The seven defaults are the unsettled design under it.

### Dependencies

- ADR-016 for the token and the deferred record-level decision.
- ADR-035 and V1_7 for the thin header and `amendment_document_events`.
- DRG-591 / OQ25 so approve stays parked.
- DRG-537 and the OpenAPI proposal, both still short of an accepted contract. DRG-537's own description asks for a continuation client; that implementation is outside this note.
- S3 / DRG-581, unstarted, for any later owner check on the amendment API.
- OQ6, so a transfer does not revise an executed document.
- OQ18 stays Proposed. Reading a stored event does not choose a digest library and does not authorize S6.

### What it unblocks

An owner acceptance of these defaults lets a later story specify the owner check and the read-only audit read. It does not start that story, DRG-537, or DRG-581. DRG-609's pricing design is independent of this visibility frame and stays unaccepted.

## DRG-597 — Separate work queues on one data model

**Linear.** [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597). Status read: Backlog. Priority Medium. Same labels. Relations: related to DRG-537, DRG-548, DRG-535, DRG-595, and DRG-607. No blocks and no blockers. The issue says the queues are outside the first slice.

### Problem

Call decision 2026-10-16 (#13): renewals and amendments are two work queues, with separate views, assignment, and SLAs, over one shared data model (the LifecycleChange spine, AmendmentDocument, and contract records). No duplicate tables per queue. Filters are by intent (Renew versus Amend and others), assignment, status, and due dates. Visibility comes from DRG-595. The UI is DRG-537.

### What is already settled on main

- V1_7 stores one `amendment_documents` row per `lifecycle_change_id` (`uq_amendment_document_lifecycle`). `intent` is constrained to `RENEW`, `AMEND`, `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT`. `document_kind` includes `RENEWAL` and `AMENDMENT`. There is no second queue table.
- The same migration indexes `(contract_id, status)`, `(status, expires_at)`, and `(tenant_id, legal_hold, retention_until)`. The field schema marks those indexes as work-queue support and marks the row `[I]` (`docs/renewals-amendments/amendment-entity-field-schema.md`, section 4). `expires_at` is a column. It is not an SLA duration.
- `QuoteContinuationRequest.Operation` on main is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`. `QuoteContinuationService` stores both `RENEWAL` and `EXTENSION` as `DealType.RENEWAL` (`docs/renewals-amendments/prereq/specs/lifecycle-intents.md`, "Landed today"). That collapse is a stored deal type. It is not a queue.
- `lifecycle-intents.md` is a proposal. DRG-548 is Backlog and asks for an ADR. That ADR is not this file. DRG-535's description still says there is no amendment document; S1 / DRG-579 is Done and V1_7 is on `main`. The schema is the settled record.
- The UI spec section 5.5 sketches one `GET /api/v1/lifecycle-changes` filter by `contractId`, `status`, and `intent`, and says DRG-597 is not specified there (`docs/renewals-amendments/prereq/story-drg-537-ui-spec.md`). The quotes list is a different list. The spec's build gate still requires an accepted OpenAPI and an implemented S3.
- Document statuses in V1_7 include `PARTIALLY_APPLIED` and `APPLY_BLOCKED`. OQ5 option A, quoted above, is the first-slice meaning of those words. Queue labels may show them. A queue does not apply a unit.
- OQ6, quoted above, is the revision rule for items still in draft and for items already executed. A queue view does not supersede a document.
- Dev-readiness places separate queues outside the first slice.

### Options, recommendation, and default

**F-597-1. Membership storage.**

- Option A. Membership is a filter. The renewal queue selects `intent = RENEW`. The amendment queue selects `intent = AMEND`. Assignment and an optional due date, if a later story stores them, are a side row keyed by `lifecycle_change_id`. That side row does not copy the document.
- Option B. A renewals table and an amendments table, each holding its own copy of the document.
- Option C. One list with no Renew versus Amend split.

**Recommendation.** Option A. The call requires two views and forbids a second copy. The unique lifecycle key is already on `amendment_documents`.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-597-2. Intent membership.**

- Option A. Renewal queue: `RENEW` only. Amendment queue: `AMEND` only. `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT`, and the continuation operations `EXTENSION` and `EXPANSION`, sit in neither queue until the owner accepts an intent model. DRG-548 and `lifecycle-intents.md` stay proposals.
- Option B. The renewal queue also contains EXTENSION, because today's deal type stores EXTENSION as RENEWAL.
- Option C. Every intent other than `RENEW` is an amendment-queue row.

**Recommendation.** Option A. The call names Renew versus Amend. Option B would decide DRG-546 inside this frame.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-597-3. Assignment.**

- Option A. Sales ops may assign either queue and may set the due date. Finance may not. The contract owner may assign on contracts they own under F-595-1. Assignment is not an edit, submit, approve, or execute.
- Option B. Assignment is an act, so only the owner may assign.
- Option C. No assignment until DRG-591 is unparked.

**Recommendation.** Option A. The call's read-only list is edit, submit, approve, and execute. Assignment is a queue fact. Approve stays on DRG-591.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-597-4. SLA.**

- Option A. The queue stores an optional due date. It adopts no duration.
- Option B. The due date is end date minus 90 days, the auto-kickoff figure in `docs/renewals-amendments/renewal-policy-stub.md`.
- Option C. This frame has no due date.

**Recommendation.** Option A. The 90-day figure belongs to renewal automation (DRG-596 in the dev-readiness addendum), which this cluster does not adopt.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-597-5. Surface.**

- Option A. Two saved filters on the list the UI spec already sketches. No new route is named as accepted. The screens wait on the DRG-537 build gate (accepted OpenAPI, then S3). This note edits neither the OpenAPI nor the UI spec.
- Option B. This note adds `/queues/renewals` and `/queues/amendments` to the contract.
- Option C. The quotes list, filtered by `DealType`, is the queue.

**Recommendation.** Option A. Section 5.5 of the UI spec already separates the quotes list from the lifecycle list.

**Default.** Option A. Proposed, unaccepted, owner decision required.

Queue reads do not call order-service. ADR-024 through ADR-027 stay Proposed. They also do not price a line. The DRG-609 design stays unaccepted, and proration stays the Accepted ADR.

### Owner decision needed

F-597-1 through F-597-5. The call's headline (two queues, one model, no duplicate tables) is the problem statement.

### Dependencies

- F-595-1, F-595-4, F-595-5, and F-595-6 for who can see a row and who can assign it.
- V1_7 intent constraint, document statuses, and the three indexes cited above.
- DRG-548 / `lifecycle-intents.md`, still proposals, for any intent outside `RENEW` and `AMEND`.
- DRG-537 build gate and the OpenAPI proposal.
- OQ5 option A for the meaning of `PARTIALLY_APPLIED` and `APPLY_BLOCKED` on a row the queue only displays.
- OQ6 for which draft items can still be revised. The CAM command for that revision stays open in the quoted record.
- S3 unstarted. S6 unauthorized. OQ18 Proposed.

### What it unblocks

An owner acceptance lets a later DRG-537 increment specify the two filters and the assignment side row. It does not add a route, does not accept the OpenAPI, and does not start S3 or S5. DRG-607 lists this cluster on its spine-dependent line. This note does not mark that umbrella done.

## DRG-598 — Phased migration of in-flight contracts

**Linear.** [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598). Status read: Backlog. Priority Medium. Same labels. Relations: related to DRG-579, DRG-585, DRG-566, DRG-584, and DRG-607. No blocks and no blockers.

### Problem

Call decisions 2026-10-16 (#14, #15): in-flight contracts migrate in phases ordered by contract end date. The first phase is upcoming renewals, meaning the nearest end dates. Scope can grow by sales and marketing metrics (the issue names segment, region, and product line) rather than one cutover. The issue says S7 (DRG-585) and DRG-566 had no phase plan, and that this story adds the plan on top. It says S7 itself is unchanged and can run per phase, and that OQ7 remains open. It also asks for per-phase validation, rollback, reconciliation, coexistence, and legacy-path gating (S6 / DRG-584).

### What is already settled on main

- S1 / DRG-579 is Done. `contracts` and nullable `subscriptions.contract_id` exist in V1_7. S7's companion says the data backfill itself is a manual owner step and that no bot runs it (`docs/renewals-amendments/s7-v1-8-header-backfill.md`).
- `contract_header_backfill_apply(p_tenant_id uuid)` in `backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql` inserts one header per eligible subscription and sets `subscriptions.contract_id`. Eligibility in that function is: not deleted, `contract_id` null, `end_date >= start_date`, a canonical customer UUID, and a mapped status. The `WHERE` clause has no end-date window and no metric predicate. `subscriptions` has no `tenant_id`. The function stamps every eligible row with the tenant argument the caller passes. `contract_header_backfill_count` uses the same population, without the status-map predicate.
- Fill rules match the thin-contract wording quoted above: `TERMED`, dates copied, `last_executed_seq = 0`, `created_by = V1_8_HEADER_BACKFILL`, no amendment document, no outbox row (`s7-v1-8-header-backfill.md`). History synthesis stays on DRG-566. DRG-566 is Backlog. Its description still asks for ORIGINAL and BACKFILLED documents and names grouping as an open choice. This frame keeps the thin-contract wording and does not accept the register's OQ7 row, which remains **DEFAULT PROPOSED for slice**.
- Compensate is `contract_header_backfill_compensate()`, documented in the S7 companion: it deletes only `V1_8_HEADER_BACKFILL` rows, and it raises when an amendment document, a `prior_contract_id`, or another subscription references a marked row.
- `subscriptions.end_date` is `NOT NULL` in V1_1. `subscriptions.renewal_date` is nullable (`V1_1__create_contract_tables.sql`). S7 copies `end_date` onto the header.
- `contracts` has no segment, region, or product-line column (V1_7).
- S6 / DRG-584 is unauthorized. OQ11 in the register is **DEFAULT PROPOSED**: per tenant, default off, internal tenant first, before S6. This note does not enable a flag.
- ADR-024 through ADR-027 are Proposed. Migration here is a CAM header population. It is not an order, a saga, a command topic, or a compensation step from those ADRs.
- Proration stays the Accepted ADR. A header backfill writes no price.
- DRG-554 remains the later merge. The thin-contract sentence says the backfill does not regroup.
- DRG-609's pricing design is unaccepted and is not an input to selecting a header.

### Options, recommendation, and default

**F-598-1. Phase versus the existing function.**

- Option A. A phase is an owner-approved set of subscription ids, ordered by end date, nearest first, then narrowed by the metrics on the manifest. `contract_header_backfill_apply` writes every eligible subscription. It is not a subset writer. This note does not change it and does not authorize a replacement. A partial phase is not executable on the function that is on `main`. A later selector, if the owner authorizes one, still writes one thin contract per selected subscription and no synthetic history.
- Option B. One call of `contract_header_backfill_apply` is the phase. That call stamps every eligible subscription with the supplied tenant.
- Option C. This note changes V1_8 so the function takes an end-date window.

**Recommendation.** Option A. The call orders phases by end date and keeps S7's fill rules. Option B is one cutover of every eligible row. Option C would edit a migration this note is not allowed to edit.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-598-2. Window width.**

- Option A. The owner writes the inclusive end-date window on the phase manifest. This note names no day count.
- Option B. The window is 90 days, taken from the renewal stub's auto-kickoff.
- Option C. The first phase is every subscription whose end date is still in the future.

**Recommendation.** Option A. The 90-day figure is DRG-596's automation default, not a migration window.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-598-3. Expansion approval and metrics.**

- Option A. The project owner approves each new manifest. Sales ops do not approve it (F-595-4 and F-595-6). Segment, region, and product line are filter values the owner writes on the manifest. This note adds no column. An empty metric filter means the end-date window only.
- Option B. Sales ops approve the expansion.
- Option C. Finance approves the expansion.

**Recommendation.** Option A. Those metrics are not columns on `contracts`. Inventing them would be a schema change.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-598-4. History.**

- Option A. A phase writes headers only. No `ORIGINAL` document, no `BACKFILLED` document, no retroactive event. DRG-566 does not run inside the phase. The register's OQ7 row stays **DEFAULT PROPOSED**. The thin-contract wording quoted above is the constraint.
- Option B. The same phase writes the history DRG-566 describes for the selected subscriptions.
- Option C. History waits, then runs once for every subscription.

**Recommendation.** Option A. The issue says OQ7 remains open and S7 stays header-only. Option C is the single history cutover the call declined for the migration as a whole.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-598-5. Coexistence.**

- Option A. A subscription with `contract_id` null stays on the acceptance path that runs today. A subscription with a header is still on that path. The document apply path is S6, which stays unauthorized. OQ11 stays **DEFAULT PROPOSED**. No flag is introduced.
- Option B. Approving a phase enables the OQ11 per-tenant flag for that tenant.
- Option C. A new per-contract flag selects the path.

**Recommendation.** Option A. The issue names S6 as the gating story. S6 is unauthorized, so coexistence here is the presence or absence of `contract_id` only.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-598-6. Validation, rollback, reconciliation.**

- Option A. For a population the existing function actually writes, the owner dry-run remains `contract_header_backfill_count`, and the undo remains `contract_header_backfill_compensate()` as the S7 companion describes it. A subset phase has no count, no apply, no compensate, and no reconciliation report in this note. No outbox row and no replay are added.
- Option B. This note specifies new phase SQL.
- Option C. Restore from backup is the only undo, and the compensate function is set aside.

**Recommendation.** Option A. The compensate function is already the documented undo for `V1_8_HEADER_BACKFILL` rows, and it refuses to delete a header that documents already reference.

**Default.** Option A. Proposed, unaccepted, owner decision required.

**F-598-7. Which date.**

- Option A. `subscriptions.end_date`, which S7 copies to `contracts.end_date`.
- Option B. `subscriptions.renewal_date`.
- Option C. The earlier of `end_date` and `renewal_date`.

**Recommendation.** Option A. The call says contract end date. `renewal_date` is nullable and is not the header end.

**Default.** Option A. Proposed, unaccepted, owner decision required.

A phase does not reprice. The Accepted proration ADR is untouched. A phase does not call order-service. ADR-024 through ADR-027 stay Proposed. OQ18 stays Proposed: a header insert does not build the digest library and does not authorize S6.

### Owner decision needed

F-598-1 through F-598-7. The call's headline (phases by end date, nearest renewals first, metric expansion, S7 fill rules unchanged, OQ7 still open) is the problem statement. The gap this frame records is that the function on `main` cannot select a phase.

### Dependencies

- S1 / DRG-579, Done, for the header table.
- S7 / DRG-585 and V1_8, for the only writer that exists. DRG-585 was In Progress when read. This note does not run it.
- DRG-566, Backlog, for any later history. Out of the phase under F-598-4.
- DRG-554, for a later regroup. The backfill does not regroup.
- S6 / DRG-584 and OQ11, both still short of acceptance, for any legacy-path flag.
- OQ6, so a migrated header does not invent an executed amendment revision. The quoted record allows no revision after EXECUTED, and a header-only phase writes no revision at all.
- OQ5 option A, so a phase does not invent a partial-apply outcome.
- F-595-3, so a backfilled header has no owner until F-595-1 is stored by a later story. `created_by = V1_8_HEADER_BACKFILL` is not that owner.

### What it unblocks

An owner acceptance lets a later story propose a subset selector and a phase-manifest record. It does not run `contract_header_backfill_apply`, does not start DRG-566, does not authorize S6, and does not deploy. Until that later story exists, the only header writer on `main` remains the owner-run function, which is not a phase writer.

## What this note does not do

- It does not edit an ADR, the open-questions register, the OpenAPI draft, the UI spec, or any migration.
- It does not accept ADR-017 or ADR-024 through ADR-027.
- It does not reopen the Accepted proration formula.
- It does not start S3 / DRG-581, authorize S6 / DRG-584, or accept the DRG-609 design.
- It does not confirm OQ18, accept OQ7's register row, or change OQ5 option A or the OQ6 record.
- It does not enable a flag, add an outbox write, register a schema, or add a replay test.
- Accepting this file authorizes no code, migration, or deploy.
