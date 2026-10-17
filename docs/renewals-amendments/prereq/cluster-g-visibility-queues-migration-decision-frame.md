# Cluster G — visibility, queues, and in-flight migration

**Status:** Decisions adopted under owner delegation on 2026-10-17; effective on merge of the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md). Implementation remains separately gated.

**Current authority:** The linked record assigns a disposition to every fork in this frame and supplies overriding contracts. All proposal labels, owner-confirmation requests, defaults and open-question wording below are the historical pre-decision frame at `285ffef8e08274600090d8ba077a0c92f489c062`. They are retained as rationale/alternatives, not additional current owner gates. Evidence and implementation claims retain their original dates and limits.

## Historical decision frame


**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Base:** `main` at `0d71251d`. **File:** this note only.

**Stories:** [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595) (RA-61), [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) (RA-63), [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598) (RA-64). Read on 2026-10-17 with relations. Comments on all three were empty. Linear status of each at the recorded read was Backlog. This note performs no issue-status update and does not claim the tickets are currently In Review. The intended review workflow is to move them to In Review, then retain that state until the owner merge; whether that transition occurred must be verified separately.

Each default below names its alternatives beside the default, and each default says Proposed, unaccepted, owner decision required.

**What this file is.** One cluster decision frame. The call decisions on the three tickets are the problem statements. Every fork below is still open. Accepting this file authorizes no code, migration, flag, topic, gateway route, or deploy.

No story was skipped. Each description is a design scope (access and visibility, queue definitions, phase rules). None asks this change to write code.

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
| F-595-1 | DRG-595 | Who is the contract owner? Security and business-policy fork. | A named user id, distinct from `created_by`. B CRM account owner of `customer_account_id`, resolved on read. C `contracts.created_by`. | A | Option A. Alternatives beside this default: B CRM account owner resolved on read; C `contracts.created_by`. Proposed, unaccepted, owner decision required. |
| F-595-2 | DRG-595 | Where is that owner stored? Security and business-policy fork. | A A fact outside the ADR-035 thin-header list. B A new thin-header column. C Derived on every read, with no stored owner. | A | Option A. Alternatives beside this default: B a new thin-header column; C derived on every read. Proposed, unaccepted, owner decision required. |
| F-595-3 | DRG-595 | How does ownership transfer? Security and business-policy fork. | A An explicit transfer that appends an audit row and leaves executed documents unchanged. B The owner follows the CRM account owner automatically. C A stored owner plus optional CRM sync. | A | Option A. Alternatives beside this default: B follow the CRM account owner automatically; C stored owner plus optional CRM sync. Proposed, unaccepted, owner decision required. |
| F-595-4 | DRG-595 | What may the owner do? Security and business-policy fork. | A Create, edit, and submit a draft once those APIs exist. Approve stays with DRG-591. Execute stays outside this note. B Act includes approve and execute. C Nobody edits until S3 is authorized. | A | Option A. Alternatives beside this default: B act includes approve and execute; C nobody edits until S3 is authorized. Proposed, unaccepted, owner decision required. |
| F-595-5 | DRG-595 | How are sales ops and finance named? Security and business-policy fork. No such app role is cited below. | A Labels in this note only. The cited ADR-016 names stay unchanged. B Map those ticket words onto `DealDesk` and `CFO` and treat the map as accepted now. C Add Entra roles in this note. | A | Option A. Alternatives beside this default: B map the ticket words onto `DealDesk` and `CFO` now; C add Entra roles in this note. Proposed, unaccepted, owner decision required. |
| F-595-6 | DRG-595 | What may the ticket's read-only access see inside a tenant? Security and business-policy fork. | A Every contract in the caller's tenant, including that contract's `amendment_document_events`. B Only assigned contracts. C Contracts in other tenants. | A | Option A. Alternatives beside this default: B assigned contracts only; C other tenants. Proposed, unaccepted, owner decision required. |
| F-595-7 | DRG-595 | When does the owner check arrive relative to S3? | A It is a named prerequisite. This note does not start S3. B Accepting this note starts S3. C S3 ships the read-only access first and skips the owner check. | A | Option A. Alternatives beside this default: B accepting this note starts S3; C read-only access ships first. Proposed, unaccepted, owner decision required. |
| F-597-1 | DRG-597 | Where does queue membership live? | A A filter on the shared spine. Assignment and due date, if stored, are a side row keyed by `lifecycle_change_id`. B Two tables that copy the document. C One undifferentiated list. | A | Option A. Alternatives beside this default: B two copying tables; C one undifferentiated list. Proposed, unaccepted, owner decision required. |
| F-597-2 | DRG-597 | Which intents are in which queue? | A Renewal queue is `RENEW` only. Amendment queue is `AMEND` only. Other intents wait. B Renewal queue also includes EXTENSION because `DealType` stores it as RENEWAL. C The amendment queue includes every intent other than `RENEW`. | A | Option A. Alternatives beside this default: B renewal queue also includes EXTENSION; C every non-`RENEW` intent is an amendment-queue row. Proposed, unaccepted, owner decision required. |
| F-597-3 | DRG-597 | Who may assign and set a due date? | A Sales ops may assign under a separately accepted write permission, expanding the read-only scope. B Only the owner may assign and set due dates on owned contracts. C Assignment waits for DRG-591. | B | Option B. Alternatives beside this default: A explicitly grant sales ops write access; C assignment waits for DRG-591. Proposed, unaccepted, owner decision required. |
| F-597-4 | DRG-597 | Is there an SLA duration? | A An optional due date only. B Ninety days before end date, taken from the renewal stub. C No due date in this frame. | A | Option A. Alternatives beside this default: B end date minus 90 days; C no due date. Proposed, unaccepted, owner decision required. |
| F-597-5 | DRG-597 | What HTTP surface do the queues use? | A Two filters on the draft list `GET /api/v1/lifecycle-changes`. No new route. B New `/queues/renewals` and `/queues/amendments` resources in this note. C The quotes list filtered by deal type. | A | Option A. Alternatives beside this default: B new queue resources in this note; C the quotes list filtered by deal type. Proposed, unaccepted, owner decision required. |
| F-598-1 | DRG-598 | What is a phase? Business-policy fork. Authorizes no backfill. | A A written manifest of subscription ids. It is not a call to `contract_header_backfill_apply`. B One owner call of that function is the phase. C This note edits V1_8 to take an end-date window. | A | Option A. Alternatives beside this default: B one owner call of `contract_header_backfill_apply` is the phase; C this note edits V1_8. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required. |
| F-598-2 | DRG-598 | How wide is the first end-date window? Business-policy fork. | A The owner names the window on the manifest. B A fixed 90-day window. C Every future end date in one phase. | A | Option A. Alternatives beside this default: B a fixed 90-day window; C every future end date. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required. |
| F-598-3 | DRG-598 | Who approves an expansion, and where do metrics live? Business-policy fork. | A The project owner approves each manifest. Metric values sit on the manifest. No new column. B The people the ticket calls sales ops approve it. C The people the ticket calls finance approve it. | A | Option A. Alternatives beside this default: B sales ops, as named on the ticket, approve it; C finance, as named on the ticket, approves it. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required. |
| F-598-4 | DRG-598 | Does a phase synthesize amendment history? Business-policy fork. | A Headers only, and only if a later owner decision authorizes a write. This note authorizes none. B The phase also writes BACKFILLED documents. C History is a later single cutover. | A | Option A. Alternatives beside this default: B the phase writes BACKFILLED documents; C a later single history cutover. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required. |
| F-598-5 | DRG-598 | How do migrated and unmigrated subscriptions coexist? Business-policy fork. | A `contract_id` null stays on the current acceptance path. A header does not switch the row onto a document apply path. B The OQ11 per-tenant flag turns on. C A new per-contract flag. | A | Option A. Alternatives beside this default: B turn on the OQ11 flag; C a new per-contract flag. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required. |
| F-598-6 | DRG-598 | What validation, rollback, and reconciliation exist for a phase? Business-policy fork. | A Count, apply, and compensate stay owner steps in the S7 companion. This note runs none of them. B This note defines new SQL. C Backup restore replaces the compensate function. | A | Option A. Alternatives beside this default: B new phase SQL in this note; C backup restore replaces compensate. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required. |
| F-598-7 | DRG-598 | Which date orders the phase? Business-policy fork. | A `subscriptions.end_date`, the date S7 copies to `contracts.end_date`. B `subscriptions.renewal_date`. C The earlier of the two. | A | Option A. Alternatives beside this default: B `subscriptions.renewal_date`; C the earlier of the two. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required. |

## DRG-595 — Renewal and amendment visibility

**Linear.** [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595). Status read: Backlog. Priority Medium. Labels `renewals-amendments`, `Open-CRM-CPQ`. Relations: related to DRG-537, DRG-591, DRG-581, DRG-607, and DRG-597. No blocks and no blockers.

### Problem

Call decision 2026-10-16 (#8), as written on the issue: contracts, renewals, and amendments are visible to the contract owner, who can act, plus read-only access for sales ops and finance. Read-only means no edit, submit, approve, or execute. The issue also says read-only access sees audit history. The issue asks who the contract owner is and how ownership transfers. It says the first slice (S3) needs at least the owner check, and that the read-only access can follow. Approver questions stay parked on DRG-591. The words "sales ops" and "finance" in that issue are the problem statement. They are not evidence that an app role with those names exists.

### What is already settled on main

- The mid-term policy clarification records the same visibility sentence and says it is not yet in that spec (`docs/renewals-amendments/mid-term-amendment-policy-spec.md`, "Audit and visibility").
- The dev-readiness addendum places visibility outside the first slice (`docs/renewals-amendments/renewals-amendments-dev-readiness.md`, addendum 2026-10-16).
- The UI spec defines no role-specific screens. It records that the OpenAPI draft lists DRG-595 role enforcement as out of this slice (`docs/renewals-amendments/prereq/story-drg-537-ui-spec.md`, section 2). The draft says the same (`docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml`, `info.description`).
- App-role names this note relies on are written in `docs/adr/ADR-016-identity-authentication-service-trust.md` (decision 7): `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, and `Admin`. The same five names are in `docs/runbooks/identity-go-live.md` (section "People"). `backend/libs/security/src/main/java/com/opencrm/security/web/EntraRoleAuthorities.java` maps each non-blank `roles` claim to a Spring authority. Its class comment examples are `CPQ.User`, `DealDesk`, `SalesVP`, and `Internal.Invoke`. That class does not list a closed set, and it does not name sales ops or finance.
- No app role named sales ops, SalesOps, finance, or `finance.read` appears in those three files, or in a search of `backend/libs/security` and `backend/services/contract-asset-management` for those strings and for `@PreAuthorize`. The words "sales ops" and "finance" in this note are the DRG-595 ticket's access description. They are not a role model on `main`. Whether a live tenant token carries some other role is unverified. The runbook row for `CFO` says "finance leadership". That sentence describes who might hold `CFO`. It does not define a finance role. ADR-016 decision 7, same ADR file, defers record-level authorization ("who may see or change whose quotes and configuration sessions"). A contract-owner visibility check in contract-asset-management was not found in that search. Behavior outside those paths is unverified.
- The thin header on `main` is `contracts` in `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`: `tenant_id`, `contract_number`, `customer_account_id`, `term_model`, `start_date`, `end_date`, `governing_agreement_ref`, `status`, `last_executed_seq`, `prior_contract_id`, plus audit columns. There is no owner column. OQ3 and ADR-035 D-02 keep that header thin. OQ27 puts `precedence_flag` outside it (`docs/renewals-amendments/amendment-entity-field-schema.md`, section 2.2a).
- `created_by` on a backfilled header is `V1_8_HEADER_BACKFILL` or `S7_HEADER_AT_CREATION` (`docs/renewals-amendments/s7-v1-8-header-backfill.md`). That marker is not a person.
- Audit rows for documents live in `amendment_document_events` (V1_7). S2 / DRG-580 is the hash-chain writer. This note does not publish those rows.
- Approvals are parked: `docs/renewals-amendments/renewals-amendments-parking-lot.md` PL-9, register OQ25, DRG-591. OQ10 and OQ17 stand until that item is unparked.
- S3 / DRG-581 is unstarted. The OpenAPI file is still `x-opencrm-status: proposal`.

### Options, recommendation, and default

**F-595-1. Who the owner is.** Security and business-policy fork.

- Option A. A named user id, distinct from `created_by`.
- Option B. The CRM account owner of `customer_account_id`, resolved when the record is read.
- Option C. `contracts.created_by`.

**Recommendation.** Option A. Option C collides with the backfill marker. Option B changes visibility when the CRM owner changes, with no transfer row.

**Default.** Option A. Alternatives beside this default: B CRM account owner of `customer_account_id`, resolved on read; C `contracts.created_by`. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Security and business-policy fork.

**F-595-2. Where the owner is stored.** Security and business-policy fork.

- Option A. A fact outside the ADR-035 thin-header list, the same separation OQ27 uses for `precedence_flag`.
- Option B. A new column inside the thin header.
- Option C. No stored owner. Every read derives one.

**Recommendation.** Option A. The thin header stays the OQ3 list.

**Default.** Option A. Alternatives beside this default: B a new column inside the thin header; C no stored owner, derived on every read. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Security and business-policy fork.

**F-595-3. Transfer.** Security and business-policy fork.

- Option A. An explicit transfer appends an audit row. Executed documents stay as OQ6 left them.
- Option B. The stored owner follows the CRM account owner with no separate transfer.
- Option C. A stored owner, plus an optional sync from CRM.

**Recommendation.** Option A. The OQ6 record quoted above refuses a revision after EXECUTED (`IMMUTABLE_EXECUTED`). A transfer does not open a revision and does not clear pricing pins.

**Default.** Option A. Alternatives beside this default: B the stored owner follows the CRM account owner with no separate transfer; C a stored owner plus an optional sync from CRM. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Security and business-policy fork.

**F-595-4. What "can act" includes.** Security and business-policy fork. "Sales ops" and "finance" here are the ticket's words. No cited file defines them as app roles.

- Option A. Once a draft API exists, the owner may create, edit, and submit. Approve stays on DRG-591. Execute is outside this note because S3 is unstarted and S4 is not authorized by OQ5.
- Option B. Act includes approve and execute.
- Option C. No edit by anyone until S3 is authorized.

**Recommendation.** Option A. The ticket lists approve among the acts that read-only access lacks. Who approves stays on DRG-591.

**Default.** Option A. Alternatives beside this default: B act includes approve and execute; C nobody edits until S3 is authorized. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Security and business-policy fork.

**F-595-5. How the ticket's "sales ops" and "finance" are named.** Security and business-policy fork.

- Option A. Labels `contract.owner`, `sales-ops.read`, and `finance.read` exist only as words in this note. The five app-role names in `docs/adr/ADR-016-identity-authentication-service-trust.md` (decision 7) stay as written there. This note does not add an Entra role. Record-level checks stay deferred in that same decision.
- Option B. Treat `DealDesk` as the ticket's sales ops and `CFO` as the ticket's finance, and treat that mapping as accepted now. That mapping is not in ADR-016 or in `docs/runbooks/identity-go-live.md`. Unverified as an existing rule.
- Option C. Add SalesOps and Finance app roles in this note.

**Recommendation.** Option A. This note does not edit an Accepted ADR, and it does not claim a sales-ops or finance role exists.

**Default.** Option A. Alternatives beside this default: B map the ticket words onto `DealDesk` and `CFO` and treat that map as accepted now; C add Entra roles in this note. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Security and business-policy fork.

**F-595-6. Scope of the ticket's read-only access.** Security and business-policy fork.

- Option A. Inside the caller's `tenant_id`, the access the ticket calls sales ops and the access it calls finance see every contract, amendment document, and renewal document, and the `amendment_document_events` rows for those contracts. The owner sees the contracts they own. No other tenant is visible. This is a proposed scope. It is not an implemented check.
- Option B. That read-only access sees only contracts assigned to the caller.
- Option C. The access the ticket calls finance may read other tenants.

**Recommendation.** Option A. `contracts.tenant_id` is the isolation column in `V1_7__contract_documents.sql`. No cited file grants sales ops or finance a read.

**Default.** Option A. Alternatives beside this default: B assigned contracts only; C the ticket's finance access reads other tenants. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Security and business-policy fork.

**F-595-7. S3 timing.**

- Option A. The owner check is a prerequisite the S3 story can cite later. Accepting this note does not authorize DRG-581, does not edit the OpenAPI draft, and does not add a check. The ticket's read-only access is a later increment after that check exists.
- Option B. Accepting this note authorizes S3 to start.
- Option C. S3 ships the ticket's read-only access and omits the owner check.

**Recommendation.** Option A. The issue says the owner check comes first. DRG-581 is Todo and blocked.

**Default.** Option A. Alternatives beside this default: B accepting this note starts S3; C the ticket's read-only access ships first and the owner check is skipped. Proposed, unaccepted, owner decision required.

**Owner decision.** Required.

This note adds no visibility check and no synchronous edge. ADR-017 stays Proposed. Actor fields, as written in `docs/adr/ADR-016-identity-authentication-service-trust.md` (decision 6), come from the verified token. A contract-owner check in contract-asset-management was not found in the search named above. Unverified outside that search.

### Owner decision needed

F-595-1 through F-595-6 are security and business-policy forks (who may see a contract, who may act, and how the ticket's sales ops and finance access is named). F-595-7 is the sequencing fork. Each has options, a default, and an owner decision. The ticket's headline is the problem statement. No cited file implements that headline.

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

**Default.** Option A. Alternatives beside this default: B a renewals table and an amendments table, each copying the document; C one list with no Renew versus Amend split. Proposed, unaccepted, owner decision required.

**F-597-2. Intent membership.**

- Option A. Renewal queue: `RENEW` only. Amendment queue: `AMEND` only. `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT`, and the continuation operations `EXTENSION` and `EXPANSION`, sit in neither queue until the owner accepts an intent model. DRG-548 and `lifecycle-intents.md` stay proposals.
- Option B. The renewal queue also contains EXTENSION, because today's deal type stores EXTENSION as RENEWAL.
- Option C. Every intent other than `RENEW` is an amendment-queue row.

**Recommendation.** Option A. The call names Renew versus Amend. Option B would decide DRG-546 inside this frame.

**Default.** Option A. Alternatives beside this default: B the renewal queue also contains EXTENSION; C every intent other than `RENEW` is an amendment-queue row. Proposed, unaccepted, owner decision required.

**F-597-3. Assignment.**

- Option A. Sales ops may assign and set due dates only under a separately accepted write permission. This expands DRG-595's read-only scope; storing assignment in a side row does not make it a read. The ticket names are not app roles in the cited files.
- Option B. Only the contract owner may assign and set due dates on contracts they own under F-595-1. Sales ops and finance remain read-only. The service holding the assignment row enforces tenant and owner checks.
- Option C. No assignment until DRG-591 is unparked.

**Recommendation.** Option B. Assignment and due-date updates are writes, so this preserves the call's read-only policy. Approve stays on DRG-591.

**Default.** Option B. Alternatives beside this default: A explicitly grant sales ops write access; C no assignment until DRG-591 is unparked. Proposed, unaccepted, owner decision required.

**F-597-4. SLA.**

- Option A. The queue stores an optional due date. It adopts no duration.
- Option B. The due date is end date minus 90 days, the auto-kickoff figure in `docs/renewals-amendments/renewal-policy-stub.md`.
- Option C. This frame has no due date.

**Recommendation.** Option A. The 90-day figure belongs to renewal automation (DRG-596 in the dev-readiness addendum), which this cluster does not adopt.

**Default.** Option A. Alternatives beside this default: B the due date is end date minus 90 days; C this frame has no due date. Proposed, unaccepted, owner decision required.

**F-597-5. Surface.**

- Option A. Two saved filters on the list the UI spec already sketches. No new route is named as accepted. The screens wait on the DRG-537 build gate (accepted OpenAPI, then S3). This note edits neither the OpenAPI nor the UI spec.
- Option B. This note adds `/queues/renewals` and `/queues/amendments` to the contract.
- Option C. The quotes list, filtered by `DealType`, is the queue.

**Recommendation.** Option A. Section 5.5 of the UI spec already separates the quotes list from the lifecycle list.

**Default.** Option A. Alternatives beside this default: B this note adds `/queues/renewals` and `/queues/amendments`; C the quotes list, filtered by `DealType`, is the queue. Proposed, unaccepted, owner decision required.

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

- S1 / DRG-579 is Done. `contracts` and nullable `subscriptions.contract_id` exist in V1_7. The S7 companion says the data backfill is a manual owner step (`docs/renewals-amendments/s7-v1-8-header-backfill.md`). The same file says: "Running `contract_header_backfill_apply` against any real environment is an owner decision, and no bot runs it." This note leaves that owner step where it is. It does not authorize a backfill and it does not run the function against real data.
- `contract_header_backfill_apply(p_tenant_id uuid)` in `backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql` inserts one header per eligible subscription and sets `subscriptions.contract_id`. Eligibility in that function is: not deleted, `contract_id` null, `end_date >= start_date`, a canonical customer UUID, and a mapped status. The `WHERE` clause has no end-date window and no metric predicate. `subscriptions` has no `tenant_id`. The function stamps every eligible row with the tenant argument the caller passes. `contract_header_backfill_count` uses the same population, without the status-map predicate.
- Fill rules match the thin-contract wording quoted above: `TERMED`, dates copied, `last_executed_seq = 0`, `created_by = V1_8_HEADER_BACKFILL`, no amendment document, no outbox row (`s7-v1-8-header-backfill.md`). History synthesis stays on DRG-566. DRG-566 is Backlog. Its description still asks for ORIGINAL and BACKFILLED documents and names grouping as an open choice. This frame keeps the thin-contract wording and does not accept the register's OQ7 row, which remains **DEFAULT PROPOSED for slice**.
- Compensate is `contract_header_backfill_compensate()`, documented in the S7 companion: it deletes only `V1_8_HEADER_BACKFILL` rows, and it raises when an amendment document, a `prior_contract_id`, or another subscription references a marked row.
- `subscriptions.end_date` is `NOT NULL` in V1_1. `subscriptions.renewal_date` is nullable (`V1_1__create_contract_tables.sql`). S7 copies `end_date` onto the header.
- `contracts` has no segment, region, or product-line column (V1_7).
- S6 / DRG-584 is unauthorized. OQ11 in the register is **DEFAULT PROPOSED**: per tenant, default off, internal tenant first, before S6. This note does not enable a flag.
- ADR-024 through ADR-027 are Proposed. A later header population, if the owner ever runs it, would be a CAM header write under the S7 companion. It is not an order, a saga, a command topic, or a compensation step from those ADRs. This note performs no population.
- Proration stays the Accepted ADR. This note writes no price.
- DRG-554 remains the later merge. The thin-contract sentence says the backfill does not regroup.
- DRG-609's pricing design is unaccepted and is not an input to selecting a header.

### Options, recommendation, and default

F-598-1 through F-598-7 are business-policy forks. Each has options, a default, and an owner decision. None of them authorizes a backfill or a run against real data. `contract_header_backfill_apply` stays the manual owner step in `docs/renewals-amendments/s7-v1-8-header-backfill.md`.

**F-598-1. Phase versus the existing function.** Business-policy fork.

- Option A. A phase is a written manifest of subscription ids, ordered by end date, nearest first, then narrowed by the metrics the owner writes on the manifest. The manifest is a decision record. It is not a command to `contract_header_backfill_apply`. That function, in `V1_8__contract_header_backfill.sql`, selects every eligible subscription. This note does not change it, does not authorize a subset writer, and does not authorize a run.
- Option B. One owner call of `contract_header_backfill_apply` is the phase. Listed so the owner can reject it. This note does not choose it and does not authorize the call.
- Option C. This note changes V1_8 so the function takes an end-date window. This note does not edit the migration.

**Recommendation.** Option A. The call orders phases by end date and leaves S7's fill rules unchanged. Option B is one cutover of every eligible row. Option C would edit a migration.

**Default.** Option A. Alternatives beside this default: B one owner call of `contract_header_backfill_apply` is the phase; C this note edits V1_8 to take an end-date window. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Business-policy fork.

**F-598-2. Window width.** Business-policy fork.

- Option A. The owner writes the inclusive end-date window on the phase manifest. This note names no day count. Writing the window does not select rows in a database and does not call apply.
- Option B. The window is 90 days, taken from the renewal stub's auto-kickoff.
- Option C. The first phase is every subscription whose end date is still in the future.

**Recommendation.** Option A. The 90-day figure is DRG-596's automation default, not a migration window.

**Default.** Option A. Alternatives beside this default: B a fixed 90-day window; C every future end date in one phase. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Business-policy fork.

**F-598-3. Expansion approval and metrics.** Business-policy fork.

- Option A. The project owner approves each new manifest. The people the ticket calls sales ops do not approve it (F-595-4 and F-595-6). Segment, region, and product line are filter values the owner writes on the manifest. This note adds no column. An empty metric filter means the end-date window only. Approval of a manifest does not run a function.
- Option B. The people the ticket calls sales ops approve the expansion.
- Option C. The people the ticket calls finance approve the expansion.

**Recommendation.** Option A. Those metrics are not columns on `contracts` in `V1_7__contract_documents.sql`. Adding them would be a schema change this note does not make.

**Default.** Option A. Alternatives beside this default: B the ticket's sales ops approve the expansion; C the ticket's finance approves the expansion. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Business-policy fork.

**F-598-4. History.** Business-policy fork.

- Option A. If a later, separate owner decision ever authorizes a write, that write is headers only: no `ORIGINAL` document, no `BACKFILLED` document, no retroactive event. DRG-566 stays outside the phase. This note authorizes no write. The register's OQ7 row stays **DEFAULT PROPOSED**. The thin-contract wording quoted above is the constraint.
- Option B. The same phase writes the history DRG-566 describes for the selected subscriptions.
- Option C. History waits, then runs once for every subscription.

**Recommendation.** Option A. The issue says OQ7 remains open and S7 stays header-only. Option C is the single history cutover the call declined for the migration as a whole.

**Default.** Option A. Alternatives beside this default: B the phase writes BACKFILLED documents; C history runs later as one cutover. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Business-policy fork.

**F-598-5. Coexistence.** Business-policy fork.

- Option A. A subscription with `contract_id` null stays on the acceptance path that runs today. A subscription that already has a header stays on that same path. The document apply path is S6, which stays unauthorized. OQ11 stays **DEFAULT PROPOSED**. No flag is introduced. This option describes rows that already exist. It does not create headers.
- Option B. Approving a phase enables the OQ11 per-tenant flag for that tenant.
- Option C. A new per-contract flag selects the path.

**Recommendation.** Option A. The issue names S6 as the gating story. S6 is unauthorized, so coexistence here is the presence or absence of `contract_id` only.

**Default.** Option A. Alternatives beside this default: B approving a phase enables the OQ11 flag; C a new per-contract flag. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Business-policy fork.

**F-598-6. Validation, rollback, reconciliation.** Business-policy fork.

- Option A. `contract_header_backfill_count`, `contract_header_backfill_apply`, and `contract_header_backfill_compensate` stay the owner steps documented in `docs/renewals-amendments/s7-v1-8-header-backfill.md`. This note does not run them against real data and does not authorize anyone to run them. A subset phase has no count, no apply, no compensate, and no reconciliation report in this note.
- Option B. This note specifies new phase SQL.
- Option C. Restore from backup is the only undo, and the compensate function is set aside.

**Recommendation.** Option A. The S7 companion already records the owner sequence. This frame does not add a second sequence and does not execute the first one.

**Default.** Option A. Alternatives beside this default: B this note specifies new phase SQL; C backup restore replaces `contract_header_backfill_compensate`. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Business-policy fork.

**F-598-7. Which date.** Business-policy fork.

- Option A. `subscriptions.end_date`, which S7 copies to `contracts.end_date`.
- Option B. `subscriptions.renewal_date`.
- Option C. The earlier of `end_date` and `renewal_date`.

**Recommendation.** Option A. The call says contract end date. `renewal_date` is nullable in `V1_1__create_contract_tables.sql` and is not the header end. Choosing the column does not query a database.

**Default.** Option A. Alternatives beside this default: B `subscriptions.renewal_date`; C the earlier of `end_date` and `renewal_date`. This default authorizes no backfill and no run against real data. `contract_header_backfill_apply` stays an owner step. Proposed, unaccepted, owner decision required.

**Owner decision.** Required. Business-policy fork.

The Accepted proration ADR is untouched. This note does not reprice. ADR-024 through ADR-027 stay Proposed. OQ18 stays Proposed. This note builds no digest library and authorizes no S6 work.

### Owner decision needed

F-598-1 through F-598-7. Each is a business-policy fork with options, a default, and an owner decision. The call's headline (phases by end date, nearest renewals first, metric expansion, S7 fill rules unchanged, OQ7 still open) is the problem statement. The function on `main` cannot select a phase. This note does not authorize a backfill to close that gap.

### Dependencies

- S1 / DRG-579, Done, for the header table.
- S7 / DRG-585 and `V1_8__contract_header_backfill.sql`, which define `contract_header_backfill_apply`. DRG-585 was In Progress when read. This note does not run that function and does not authorize a run against real data. The apply stays an owner step.
- DRG-566, Backlog, for any later history. Out of the phase under F-598-4.
- DRG-554, for a later regroup. The backfill does not regroup.
- S6 / DRG-584 and OQ11, both still short of acceptance, for any legacy-path flag.
- OQ6, so a migrated header does not invent an executed amendment revision. The quoted record allows no revision after EXECUTED, and a header-only phase writes no revision at all.
- OQ5 option A, so a phase does not invent a partial-apply outcome.
- F-595-3, so a backfilled header has no owner until F-595-1 is stored by a later story. `created_by = V1_8_HEADER_BACKFILL` is not that owner.

### What it unblocks

An owner acceptance records the phase policy. It lets a later story propose a manifest format. It authorizes no backfill, no run of `contract_header_backfill_apply` against real data, no start of DRG-566, no S6 work, and no deploy. The manual apply stays the owner step in `docs/renewals-amendments/s7-v1-8-header-backfill.md`.

## What this note does not do

- It does not edit an ADR, the open-questions register, the OpenAPI draft, the UI spec, or any migration.
- It does not accept ADR-017 or ADR-024 through ADR-027.
- It does not reopen the Accepted proration formula.
- It does not start S3 / DRG-581, authorize S6 / DRG-584, or accept the DRG-609 design.
- It does not confirm OQ18, accept OQ7's register row, or change OQ5 option A or the OQ6 record.
- It does not enable a flag, add an outbox write, register a schema, or add a replay test.
- It does not authorize a backfill or a run against real data. `contract_header_backfill_apply`, `contract_header_backfill_count`, and `contract_header_backfill_compensate` stay owner steps in `docs/renewals-amendments/s7-v1-8-header-backfill.md`. This note does not call them.
- Accepting this file authorizes no code, migration, or deploy.
