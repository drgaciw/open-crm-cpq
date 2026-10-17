# DRG-551 — Dry-run and revert: design note

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Base:** `main` at `63e6ec8d` (merge of [#663](https://github.com/drgaciw/open-crm-cpq/pull/663)).

**Story:** [DRG-551](https://linear.app/drgamtd-workspace/issue/DRG-551/dry-run-and-revert-for-lifecycle-changes-versioned-subscriptions) (RA-17, Medium). Title: Dry run and revert for lifecycle changes (versioned subscriptions). Linear status when read on 2026-10-17: Backlog. Labels: `renewals-amendments`, `Open-CRM-CPQ`. Comments were not read. **Unverified** outside Linear.

**Relations read the same day:** [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) (related), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) (related). Not re-fetched after that read.

This file is a design note. It is not an ADR, takes no number, and is not copied into `docs/adr/`. Accepting this note authorizes no code, migration, flag, topic, gateway route, or deploy. It authorizes no backfill run and no apply. S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) is **not built**. The recorded DRG-551 read was Backlog. The intended move to In Review belongs to the issue owner and is not performed or claimed by this note. The ticket stays In Review until the owner merges. This file does not make DRG-551 Done.

## How to read this note

Every fork has options, a recommendation, and a default. Each default is **Proposed, unaccepted, owner decision required.** The alternatives sit beside that default. The owner-decision table lists every fork. Adoption is a later act by the owner. This file does not perform it.

**Read** means the cited file was opened on this base. **Search** means a text search, not a running process. **Unverified** means the claim was not opened, not executed, or the search can miss it. Nothing in this pass was executed: no Maven run, no database session, no HTTP call, no Flyway run, no backfill, and no apply.

## Boundaries that stay as they are

- [ADR-035](../../adr/ADR-035-amendment-document-placement.md) is **Accepted** for placement and for the first-slice schema and audit stories only (S1, S2). Later stories still need their own gates. This note does not extend that acceptance and does not start S3 or S4.
- [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) and [ADR-024](../../adr/ADR-024-order-orchestration-context.md) through [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) stay **Proposed** (status line of each file: "Proposed on 2026-10-04. Not accepted."). They are not prerequisites. This note does not choose a transport, an order saga, a command topic, or a compensation path.
- The amendment proration formula in [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) is **Accepted** (status line: "Accepted 2026-10-16"). This note does not restate the formula and does not reopen it.
- Uplift precedence follows the Accepted record [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md), D-04: contract clause, then line, then agreement, then account, then the rules default (CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES). This note does not merge rungs and does not price a renewal.
- OQ5 option A is unchanged. The register cell opens with this sentence, quoted exactly: "Option A for the first slice only, delegated by the owner and relayed by Projects Manager: one unit per target subscription, atomic units, no automatic rollback, PARTIALLY_APPLIED when a later unit fails, APPLY_BLOCKED when no unit has committed, correcting amendment as remedy." Source: [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md). [partial-apply-owner-decision.md](partial-apply-owner-decision.md) section 9 records the same remedy and records that rebase is not a first-slice remedy. `specs/partial-apply.md` stays not accepted. S6 is not authorized.
- OQ7's thin contract is unchanged. Quoted exactly from [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md):

> One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup.

  The register row now says **ADOPTED, header-only first slice** and points at the delegated record for phased manifest selection. This note does not edit that row, does not regroup contracts, and does not run `contract_header_backfill_count` or `contract_header_backfill_apply`.
- S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) code stays unlaunched. S6 / [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) is not authorized. This note defines no schema-registry entry, no new bus topic, and no outbox contract.
- Entitlement quantities and conversion price are pending the [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) design in [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md). That note is a design proposal. It is not an ADR. This note does not claim that design is merged or accepted. The delegated record says older DRG-609 proposals are not accepted wholesale. This note does not specify a pricing call.
- [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) through [#660](https://github.com/drgaciw/open-crm-cpq/pull/660) are on `main`. [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) adopts each baseline default named in [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md), including Cluster C F9 (dry-run), G4 (freeze), and G5 (the OQ6 correction text). This note cites those defaults. It does not replace them.

The delegated record's authority section says, quoted exactly: "No earlier digest or executed commercial fact may be rewritten to fit the new design." Its D-REVISION contract says, quoted exactly: "No VOID mutation is introduced in the slice." Its pricing contract says, quoted exactly: "Pure preview output cannot be presented as a confirmed commercial price."

## OQ6, quoted exactly

The rule below is quoted exactly from [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md), section "2. Rule for OQ6 (first slice)", as merged by [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) and still present on this `main`. Nothing in the fence is a paraphrase.

```text
## 2. Rule for OQ6 (first slice)

Document states in the first slice: `DRAFT`, `IN_REVIEW`, `APPROVED`, `EXECUTED`, then the apply outcomes `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, and the terminal states `REJECTED` and `WITHDRAWN`. `OUT_FOR_SIGNATURE` cannot be reached in the slice, because E_SIGN and WET execution return 422 `SIGNATURE_NOT_SUPPORTED_IN_SLICE` (AC-S3-4).

1. **Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`).** A new revision of the LifecycleChange opens a new document revision on the same document, with the same `provisionalNumber` (OQ4). The previous revision closes with `status_at_close = SUPERSEDED` and keeps its content digest. The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again. The persisted representation of an unpriced revision is not decided here.
2. **Executed, in any apply state (`EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`).** The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED` and an audit event, which is AC-S3-5 applied to revisions. "Executed but not yet applied" gets no special path. The remedy is a new LifecycleChange that produces a new, correcting amendment document, the same remedy OQ5 option A adopted for a blocked unit.
3. **Command and integration boundary.** CAM must own the revision and execution state transitions. The exact CAM command/API, including route and request/response contract, is not decided here; the OpenAPI draft's LifecycleChange revision operation is tagged S5 and is not an accepted CAM API. The S3 CAM-local test boundary versus the S5 LifecycleChange trigger, delivery, acknowledgement and reconciliation boundary requires a separate accepted contract. No cross-service race or delivery protocol is adopted here.
4. **Terminal `REJECTED` and `WITHDRAWN`.** Neither state is reopened by a revision command; CAM returns 409 and preserves the document and revision digest. This retains the terminal rejection and withdrawal transitions in the canonical policy. A different document is needed for a subsequent proposal.
5. **`OUT_FOR_SIGNATURE`, for the later signature story.** A new revision must void the open signature envelope before rule 1 applies. Nothing in the slice builds it.

The delegated ruling does not choose a supersession event type, payload/schema, topic, or outbox contract. Event requirements in draft acceptance criteria remain proposals and do not authorize an event contract or S6 work.

Why this rule: it keeps the reading the gate called an inference ("drafts only edit" means pre-execution edits, and an executed document is immutable) and makes it explicit; it matches the OpenAPI draft, which already refuses a new revision of an `EXECUTED` document; it keeps one remedy for every post-execution correction (a correcting amendment), so S3, S4 and OQ5 need no second recovery path; and it keeps V11 (one executed revision) and gapless numbering at `EXECUTED` unchanged.

Rejected alternatives: allowing a revision of an executed-but-unapplied document (it would edit a document the customer already accepted, and breaks A1 and AC-S3-5); a new amendment number per revision (conflicts with OQ4, where the provisional number is stable across revisions).
```

The same file's current-decision paragraph is quoted exactly, links included:

> The pre-execution revision rule, stable provisional number, terminal refusal, executed immutability and at-most-one executed revision remain as recorded below. The [CAM contracts](delegated-renewals-amendments-decisions-2026-10-17.md#cam-transactions-and-delivery) now select the CAM API/version/idempotency behavior, audit types, local concurrency criterion and durable S5 delivery. The [storage/digest contract](delegated-renewals-amendments-decisions-2026-10-17.md#storage-and-digests) now selects nullable unpriced values, a CAM-owned immutable envelope store and genuine close-time hashing. AC-S3-9..11, AC-S5-7 and AC-S8-6 are adopted criteria, with execution evidence still required.

The historical-section lead-in is quoted exactly:

> The material below preserves the original reading, delegation evidence and rationale. References to unconfirmed OQ18 or open boundary contracts describe that earlier state and are superseded by the current decision above.

D-RACE in [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) says, quoted exactly: "Success `AMENDMENT_SUPERSEDED` and refusal `AMENDMENT_REVISION_REJECTED` are local audit types. Existing S2 hash-chain and audit outbox obligations remain; “local” never means suppressing its outbox. No new S6 domain-event topic/schema is adopted here." This note does not implement those commands.

## Problem

DRG-551 says there is no dry run beyond preview/validate and no way to revert an applied change. The issue's suggested next step is an **option only**: a subscription version number, a reversible check, and a revert that closes the applied rate periods, plus a `dryRun` flag that returns projected state without persisting. That option is fork D7's alternative and fork D11's alternative. It is not the default.

The research file, read, records DealHub `isDryRun`, `/reversible`, and `/{version}/revert` ([cpq-renewals-amendments-research.md](../cpq-renewals-amendments-research.md) section 2.3), and records that Salesforce CPQ and Zuora keep history by new ledger rows or charge segments (section 5). Those sentences are evidence. They are not an API to copy. The live vendor pages were not re-fetched. **Unverified** against those pages.

This note describes the platform dry-run output, the meaning of revert, and the audit entries, over the S2 chain that is on `main` and over the S4 apply that is **not built**.

## Already on main

| Fact | File path | Migration |
|---|---|---|
| `subscriptions.commercial_revision` is `BIGINT NOT NULL DEFAULT 0` | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_6__subscription_rate_schedule.sql` line 2 | `V1_6__subscription_rate_schedule.sql` |
| `subscription_rate_periods.effective_end_date` is `DATE NOT NULL`. The table also has `applied`, `retired`, `is_deleted`, and `version` | Same file, lines 24, 36–37, 44–45 | `V1_6__subscription_rate_schedule.sql` |
| `applied_quote_acceptances` exists, unique on `accepted_quote_id`. It is the legacy acceptance row | Same file, lines 52–65 | `V1_6__subscription_rate_schedule.sql` |
| Amendment documents, revisions, lines, application units, and `amendment_document_events` exist. The event table is append-only: triggers reject UPDATE, DELETE, and TRUNCATE | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`, `CREATE TABLE amendment_document_events` at line 305, trigger at lines 380–388. Header comment line 3: no `apply_status`, and the ledger is V1_9 / DRG-582 | `V1_7__contract_documents.sql` |
| Application units store `unit_no`, `target_type` (`SUBSCRIPTION`, `ENTITLEMENT`, `ONE_TIME`, `NONE`), `observed_commercial_revision`, nullable `applied_commercial_revision`, nullable `apply_key_digest`, and status `PENDING`, `APPLIED`, `BLOCKED` | Same file, lines 189–208 | `V1_7__contract_documents.sql` |
| Nullable `subscription_rate_periods.amendment_document_line_id` and `revision_no` (both null, or both set) | Same file, lines 357–363 | `V1_7__contract_documents.sql` |
| Durable renewal-publication receipts | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7_1__durable_renewal_eligibility_publications.sql` | `V1_7_1__durable_renewal_eligibility_publications.sql` |
| Frozen amendment-line membership | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7_2__freeze_amendment_line_membership.sql` | `V1_7_2__freeze_amendment_line_membership.sql` |
| Header-backfill functions only. The file header reserves V1_9. Flyway does not execute the backfill | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql` lines 1–11 | `V1_8__contract_header_backfill.sql` (S7) |
| No CAM `V1_9` file and no `V1_10` file. A directory listing of `backend/services/contract-asset-management/src/main/resources/db/migration/` on this base ends at `V1_8` | That directory | No `V1_9` migration. `document_apply_ledger` is not created |
| S2 writer appends one hash-chained event and its audit outbox row in the caller's transaction. It requires an existing document, locks that row, assigns a gapless `seq_no`, and returns the original row on a duplicate `source_event_id`. `payloadDigest` must be 64 lowercase hex characters. An unknown document throws | `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java` class comment and `append` | No new migration. The table is `V1_7` |
| The outbox publication is `AMENDMENT_AUDIT_RECORDED_V1`. The class comment says this does not enable the relay. The payload carries document id, amendment sequence, amendment number, `contentHash`, correlation id, and event id | `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/audit/AmendmentAuditOutbox.java` | No new migration |
| OpenAPI draft: `dryRun` defaults to false. Description, quoted exactly: "A dry run is not persisted and has no amendment document." | `docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` lines 1806–1809 | No migration. The file is a proposal (`x-opencrm-status: proposal`) |
| Search of `backend/` `*.java`, `*.yml`, `*.yaml`, and `*.sql` for `dryRun`, `isDryRun`, `reversible`, and `/revert` found the AI policy dry-run test and the dead-letter replay `--dry-run`. No lifecycle dry-run or revert type was in those hits | Search on 2026-10-17. A differently named method would miss this search. **Unverified** beyond those tokens. A database catalog was not queried | No migration adds a lifecycle dry-run table |

`contract_header_backfill_count` is the S7 count. [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) calls it a dry run because it writes nothing. It counts subscriptions that would receive a header. It is not the lifecycle dry-run in this note. This note does not call it.

## S4 apply is not built

S4 / DRG-582 is the document-driven apply after `EXECUTED`. It is not on this `main`.

The gate doc designs the migration and does not create it. [story-s3-s4-authorization-gate.md](story-s3-s4-authorization-gate.md), section "S4 / DRG-582 — CAM V1_9 apply ledger", says a new CAM migration `V1_9` (suffix unspecified) would create `document_apply_ledger`, unique on (`document_id`, `unit_no`), storing `apply_key_digest`. The same digest on replay is a no-op. A different digest is rejected and audited. The technical approach marks that shape `[I]` ([amendment-technical-approach-and-dependencies.md](../amendment-technical-approach-and-dependencies.md) section 3). `applied_quote_acceptances` stays unchanged for the legacy path in that paragraph. The field schema §2.6 still says that table gains columns. Both texts were read by the gate. This note does not choose between them.

[partial-apply-owner-decision.md](partial-apply-owner-decision.md) section 9 says the ledger table for S4 is **not chosen**. This note does not choose it either. V1_9 stays reserved for that S4 ledger as the gate doc designs it. No migration file is written here. Any later migration other than that reserved V1_9 is `V1_10` or higher, next free at build time, with a rollback statement. `V1_7_3` is forbidden.

The gate also says S4 stays blocked until the owner authorizes DRG-582. The OQ5 register cell says S4 is not authorized by that record. [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) says the delegation does not release implementation stories. There is no apply orchestrator class name in the gate ("not specified"). This note does not invent one, does not call apply, and does not write a ledger row.

## Dry-run output shape

**Proposed default:** the response below. Proposed, unaccepted, owner decision required.

The dry-run is a read. It returns one object and writes nothing: no amendment document, no revision, no line, no application unit, no rate period, no `applied_quote_acceptances` row, no `document_apply_ledger` row, no `amendment_document_events` row, and no outbox row. S4 is not invoked, because S4 is not built.

| Field | Meaning |
|---|---|
| `dryRun` | `true` |
| `persisted` | `false` |
| `confirmedPrice` | `false`. A preview is not a confirmed commercial price |
| `documentId` | Null when the caller has no document. When the caller names an existing document, the id is echoed and the document is not modified |
| `subject.kind` | `LIFECYCLE_CHANGE_PREVIEW` or `TRUE_UP_SNAPSHOT` |
| `subject.snapshotId` | Present for a true-up. Null otherwise |
| `subject.policyVersion` | The policy version the preview used. Null when no policy version was supplied |
| `subject.contentHash` | 64 lowercase hex when the caller supplied a content hash. Null when the preview is unpriced |
| `pins` | The same three values again, so a later presentation can cite them. This note does not store them |
| `trueUp` | Present only when `subject.kind` is `TRUE_UP_SNAPSHOT`. Fields are the adopted Cluster C F9 set: `excess`, `rateSource`, `maintenanceTrace`, `snapshotHash`, `policyVersion`. Omitted for every other subject |
| `projection.observedCommercialRevision` | The `subscriptions.commercial_revision` value that was read. Not incremented |
| `projection.projectedCommercialRevision` | A label equal to the observed value plus one. Not written to the subscription |
| `projection.units[]` | `unitNo`, `targetType`, `targetRef`, `outcome` (`WOULD_APPLY` or `WOULD_BLOCK`), `reasonCode`. These are labels. They are not `amendment_application_units` rows |
| `projection.projectedDocumentStatus` | A label: `APPLIED`, `PARTIALLY_APPLIED`, or `APPLY_BLOCKED`, using the OQ5 option A cut (blocked when no unit would commit, partial when a later unit would fail after an earlier one would commit). The document status column is not updated. S4, which would persist those statuses, is not built |
| `projection.ratePeriods[]` | Projected successor descriptors: `effectiveFrom`, `effectiveTo`, `quantity`, `closesObservedPeriod` (a boolean label). No `effective_end_date` is updated. Entitlement quantity and conversion price are absent. They are pending the DRG-609 design, which is not accepted |
| `projection.upliftRung` | Null in this note. A later accepted pricing contract that names a rung uses CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES. This note does not merge rungs |
| `audit.chainAppended` | `false` |
| `audit.outboxStaged` | `false` |
| `audit.reason` | `NO_DOCUMENT_CHAIN_ROW`. The S2 writer requires an amendment document and throws when the id is unknown |

Same `snapshotId`, `policyVersion`, and `contentHash` return the same amounts. That is the adopted Cluster C F9 rule ([cluster-c-trueup-and-meters-decision-frame.md](cluster-c-trueup-and-meters-decision-frame.md) F9; [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) disposition "F9 Dry-run: Adopt baseline default"). F9 also says the dry-run is a quote-service preview that records the pins, that it does not adopt the DRG-609 route, and that DRG-551 is the platform story which that default does not start. This shape is the platform description. It does not start F9, does not build the meter snapshot, and does not change G4 (the snapshot freezes when that dry-run completes). G5 stays the quoted OQ6 text.

The OpenAPI sentence "A dry run is not persisted and has no amendment document" is the persistence rule of this default. The draft file stays a proposal. This note does not edit it and does not add a route.

[approval-requirements-skeleton.md](../approval-requirements-skeleton.md) section 8.4 describes a different dry-run, for approval rules. This note does not use that shape. Whether that skeleton is accepted was not re-read. **Unverified.**

Illustrative shape, with no amounts and no customer data. It is a picture of the fields. It is not a request to send and not a fixture to load.

```text
dryRun: true
persisted: false
confirmedPrice: false
documentId: null
subject: { kind: LIFECYCLE_CHANGE_PREVIEW, snapshotId: null, policyVersion: null, contentHash: null }
trueUp: omitted
projection:
  observedCommercialRevision: <read>
  projectedCommercialRevision: <label only>
  units: [{ unitNo, targetType, targetRef, outcome: WOULD_APPLY | WOULD_BLOCK, reasonCode }]
  projectedDocumentStatus: <label only>
  ratePeriods: [{ effectiveFrom, effectiveTo, quantity, closesObservedPeriod }]
  upliftRung: null
audit: { chainAppended: false, outboxStaged: false, reason: NO_DOCUMENT_CHAIN_ROW }
```

## What revert means

**Proposed default:** revert leaves every stored row in place. Proposed, unaccepted, owner decision required.

Revert, in this note, is two descriptions and no writer.

1. **Reversible check.** A read. It returns whether a later correcting LifecycleChange is the available remedy, and the reason. It does not insert, update, or delete.
2. **After any committed unit, and after `EXECUTED` with no unit committed.** The remedy is a new LifecycleChange that produces a new, correcting amendment document. That is OQ6 rule 2, quoted above, and OQ5 option A. There is no automatic rollback. "Executed but not yet applied" has no special path. The correcting document is not created by this note. S3, which would own the document command, stays unlaunched. S4, which would record the commit on a ledger, is not built.

Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`), the edit path is OQ6 rule 1: a new revision under the same provisional number, the previous revision frozen `SUPERSEDED`, pins cleared. That path is a new revision. It is not a deletion of the old revision, and it is not a `VOID` mutation. D-REVISION already says no `VOID` mutation is introduced in the slice. Terminal `REJECTED` and `WITHDRAWN` stay terminal (OQ6 rule 4).

**No data deletion without an owner step.** The described check and the described remedy do not `DELETE`, do not `TRUNCATE`, do not set `is_deleted`, and do not call `contract_header_backfill_compensate`. `amendment_document_events` already rejects UPDATE, DELETE, and TRUNCATE for every role (`V1_7__contract_documents.sql`). An owner step that later authorizes deletion has to name the rows, the backup, and a rollback statement. This note does not take that step and does not design that run. No backfill runs here. No apply runs here.

Closing an applied rate period by writing `effective_end_date` is the issue's suggested next step. It is an update of a commercial row. It is an alternative in fork D7. It is not the default, because S4 is not built and no ledger names which rows an apply wrote. The delegated record forbids rewriting an earlier digest or an executed commercial fact. A successor period, when a later authorized apply writes one, is new history. It is not a deletion of the period it follows.

DealHub `/{version}/revert` and `/reversible` stay research evidence. They are not endpoints in this tree (search above).

`specs/partial-apply.md` says DRG-551 is "Revert of an unapplied change" and that after a unit commits the recovery is rebase or a correcting amendment. That spec is not accepted. Where it names rebase, this note follows section 9 of the partial-apply owner decision: rebase is not a first-slice remedy. The remedy is a correcting amendment. Committed units stay.

The spike mapping table calls `VOID` a pre-apply revert and points at DRG-551 for a post-apply reversing amendment ([amendment-document-design-spike.md](../amendment-document-design-spike.md)). D-REVISION leaves `VOID` unreachable by a first-slice mutation. This note does not add that mutation.

## Audit entries

**Proposed default:** the rows in this section. Proposed, unaccepted, owner decision required.

| Command | `amendment_document_events` | Outbox | Other store |
|---|---|---|---|
| Dry-run | No row. `audit.chainAppended` is false. The writer is not called. A dry-run has no document to lock, and the writer throws when the document id is unknown | No row. `audit.outboxStaged` is false | No `commercial_decision_audit` row. No pricing confirmation snapshot. A preview is not a confirmed price |
| Reversible check | No row. The check is a read | No row | No row |
| Revision of a document that is `EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, or `APPLY_BLOCKED` | The adopted refusal type `AMENDMENT_REVISION_REJECTED`, appended by the existing S2 writer in the caller's transaction, with `payload_digest` of 64 lowercase hex and a `payload_ref`. The prior chain rows stay. The writer also stages `AMENDMENT_AUDIT_RECORDED_V1` | Staged by that same writer. The relay is not enabled by the writer class, and this note does not enable it | No rate-period write. No ledger write. S4 is not built |
| Successful pre-execution supersede, when a later authorized S3 command runs | The adopted local type `AMENDMENT_SUPERSEDED`, through the same writer and the same outbox obligation | Staged with the chain row | The envelope archive is the delegated D-SNAPSHOT contract. This note does not create that table |
| Correcting LifecycleChange after execute or after a committed unit | The new document's own chain, starting at its own `seq_no` 1, when a later authorized story creates the document. The old document's chain is unchanged | The new document's own outbox rows, when that story appends | No delete of the old document, its events, or its rate periods |
| S4 apply replay, when S4 is later authorized and built | The gate doc says a different `apply_key_digest` is rejected and audited. The same digest is a no-op | Not specified as a new topic in this note. S6 is not authorized | `document_apply_ledger` as designed in the gate doc. The table is not created here. The owner decision still leaves the table unchosen |

`AMENDMENT_REVISION_REJECTED` appears in the OpenAPI draft `EventType` list. That list is a proposal. This note does not register it in the schema registry and does not authorize S6. The S2 writer stores `event_type` as `varchar(80)` with no check constraint enumerating the values (`V1_7__contract_documents.sql` line 310). A later command can pass the adopted local type through that column. Passing it is not a bus schema.

A dry-run does not use that column. Recording the pins for a true-up is the F9 preview result inside the response. G4's freeze of a meter snapshot is Cluster C's adopted default. This note does not build the snapshot table and does not append a chain row to stand in for it.

## Forks

### D1 — Output shape

| Option | Meaning |
|---|---|
| A. The field table in "Dry-run output shape" | One read response. True-up subjects also carry the F9 fields. Other subjects omit `trueUp` |
| B. DealHub `isDryRun` fields (`subscriptionTargetVersion`, replacement `lineItemsNetEffect`) | Copies a vendor response. Research evidence only |
| C. True-up fields only | Drops the lifecycle projection |
| D. Return a persisted amendment document | Contradicts the OpenAPI draft sentence that a dry run is not persisted and has no amendment document |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D2 — Persistence

| Option | Meaning |
|---|---|
| A. Not persisted. No amendment document | Matches the OpenAPI draft sentence |
| B. A preview row outside `amendment_document_events` | A new store. A later migration would be `V1_10` or higher, next free at build time, with a rollback statement. Never `V1_7_3`. V1_9 stays reserved |
| C. An amendment document in `DRAFT` | The dry-run becomes a document command |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D3 — S2 chain row for a dry-run

| Option | Meaning |
|---|---|
| A. No chain row and no outbox row | The writer requires a document. The response says why |
| B. When a document already exists, append one chain row whose `payload_digest` is the dry-run body | Still stages the S2 outbox. The event type would be an unregistered string. S6 is not authorized |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D4 — Repeating a dry-run

| Option | Meaning |
|---|---|
| A. The same snapshot id, policy version, and content hash return the same amounts | Adopted Cluster C F9. Nothing is committed, so no CAM idempotency receipt is written |
| B. Recompute from live rows and allow the amounts to move | The presented number can change after the customer has seen it |
| C. Store a D-IDEMPOTENCY receipt | That contract is for a committed CAM operation. A dry-run commits nothing |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D5 — Entitlements, conversion price, and other amounts

| Option | Meaning |
|---|---|
| A. The shape carries no entitlement quantity and no conversion price. Any other amount a later accepted pricing contract supplies is labeled `confirmedPrice: false` | Pending the DRG-609 design. That design is not claimed merged or accepted. [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) does not accept it wholesale |
| B. Fill prices by the DRG-609 route sketch | Uses a design this note does not accept |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

Uplift precedence, if a later contract names a rung, stays CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES. This fork does not reopen D-04. Proration stays the Accepted formula and is not restated.

### D6 — True-up fields and Cluster C

| Option | Meaning |
|---|---|
| A. A true-up subject includes the adopted F9 fields. G4 and G5 stay as [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) adopted them. This note does not start F9's implementation | F9's own text says DRG-551 is not started by that default |
| B. A second true-up field list | Splits the platform shape from the adopted preview |
| C. Implement F9 inside DRG-551 now | Starts a story F9 left unstarted, and would be code |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D7 — What revert means

| Option | Meaning |
|---|---|
| A. A read-only reversible check, plus a new correcting LifecycleChange after `EXECUTED` or after any committed unit. No deletion. No `VOID` mutation. No rewrite of an executed commercial fact. No rate-period update. S4 is not called | The meaning in "What revert means" |
| B. Close applied rate periods by setting `effective_end_date` | The issue's suggested next step. An update of applied rows. S4 has no ledger yet to name those rows |
| C. DealHub `/{version}/revert` | Vendor endpoint. Not in this tree |
| D. `DELETE`, `TRUNCATE`, or `is_deleted = true` | Data deletion. Requires a separate owner step. This note does not take it |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D8 — Data deletion

| Option | Meaning |
|---|---|
| A. The commands in this note delete nothing. A later deletion needs its own owner step naming the rows, the backup, and a rollback statement | No backfill run and no apply |
| B. Call `contract_header_backfill_compensate()` | That function deletes header rows marked `V1_8_HEADER_BACKFILL` under the rules in `V1_8__contract_header_backfill.sql`. It is not a lifecycle revert. This note does not run it |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D9 — An unapplied change

| Option | Meaning |
|---|---|
| A. Before execution, OQ6 rule 1 (new revision, old revision frozen). After `EXECUTED` and before any unit commits, OQ6 rule 2 (409 `IMMUTABLE_EXECUTED`, new correcting LifecycleChange). No delete. No `VOID` | Follows the quoted rule. `specs/partial-apply.md` is not accepted where it would add another unapplied path |
| B. `VOID` an executed-but-unapplied document | The spike mapping table. D-REVISION says no `VOID` mutation is introduced in the slice |
| C. Delete the draft document and its rows | Data deletion. Fork D8 |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D10 — After a unit commits

| Option | Meaning |
|---|---|
| A. Correcting amendment only. Committed units stay | OQ5 option A. Section 9 of the partial-apply owner decision: rebase is not a first-slice remedy. S4 is not built, so the commit is not recorded on `document_apply_ledger` by this note |
| B. Automatic rollback of committed units | Rejected by the OQ5 sentence quoted above |
| C. Rebase or a correcting amendment | The unaccepted `specs/partial-apply.md` sentence. This note does not add rebase |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D11 — Version identity

| Option | Meaning |
|---|---|
| A. Cite `subscriptions.commercial_revision` from `V1_6__subscription_rate_schedule.sql`. No new column | The projection label is observed plus one. The column is not updated |
| B. Add a subscription version column | A future CAM migration at `V1_10` or higher, next free at build time, with a rollback statement. Never `V1_7_3`. V1_9 stays reserved for S4. This note writes no migration file |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D12 — Audit entry when a revert request is a revision of an immutable document

| Option | Meaning |
|---|---|
| A. `AMENDMENT_REVISION_REJECTED` through the existing S2 writer, which also stages the audit outbox. The reversible check, being a read, writes nothing | Adopted D-RACE local type. No schema-registry entry. S6 is not authorized. This note does not implement the command |
| B. A new `REVERTED` event type and an S6 contract | S6 is not authorized |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D13 — Audit entry for the dry-run itself

| Option | Meaning |
|---|---|
| A. No audit row. The response carries `audit.chainAppended: false` | Section "Audit entries" |
| B. A `commercial_decision_audit` row | [story-drg-564-policy-decision-audit-trail.md](story-drg-564-policy-decision-audit-trail.md) records that table as the renewal-draft decision. That note is Proposed. This note does not widen the table |
| C. A pricing-service confirmation snapshot | The delegated pricing contract separates confirmation from pure preview. A dry-run does not confirm a price |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

### D14 — S4 apply inside the dry-run

| Option | Meaning |
|---|---|
| A. The dry-run does not call apply, does not write `document_apply_ledger`, and does not write `applied_quote_acceptances`. The gate doc's V1_9 design is cited. The ledger table stays unchosen | S4 is not built |
| B. Apply inside a transaction that rolls back | A run against stored rows. This note does not design it |

**Recommendation:** A. **Proposed default:** A. Proposed, unaccepted, owner decision required.

## Owner-decision table

Every default in this table is **Proposed, unaccepted, owner decision required.**

| Fork | Proposed default | Alternatives beside the default |
|---|---|---|
| D1 Output shape | A. The field table in this note. Proposed, unaccepted, owner decision required | B. DealHub `isDryRun` fields. C. True-up fields only. D. A persisted amendment document |
| D2 Persistence | A. Not persisted. No amendment document. Proposed, unaccepted, owner decision required | B. A preview row outside the event table (`V1_10` or higher, next free at build time, with a rollback statement; never `V1_7_3`; V1_9 reserved). C. A `DRAFT` document |
| D3 S2 chain on dry-run | A. No chain row and no outbox row. Proposed, unaccepted, owner decision required | B. Append one chain row when a document already exists |
| D4 Repeat | A. Same snapshot id, policy version, and content hash return the same amounts. No receipt. Proposed, unaccepted, owner decision required | B. Live recompute. C. A committed-operation receipt |
| D5 Entitlements and conversion price | A. Omit both. Other amounts, if a later accepted contract supplies them, stay `confirmedPrice: false`. Pending DRG-609. Proposed, unaccepted, owner decision required | B. The DRG-609 route sketch |
| D6 True-up overlap | A. Include adopted F9 fields. Leave G4 and G5 in place. Do not start F9. Proposed, unaccepted, owner decision required | B. A second field list. C. Implement F9 now |
| D7 Revert meaning | A. Read-only check, then a new correcting LifecycleChange. No deletion, no `VOID`, no rate-period update. Proposed, unaccepted, owner decision required | B. Set `effective_end_date`. C. DealHub `/{version}/revert`. D. Delete or soft-delete |
| D8 Data deletion | A. No deletion. A later deletion is a separate owner step. Proposed, unaccepted, owner decision required | B. `contract_header_backfill_compensate()` |
| D9 Unapplied change | A. OQ6 rule 1 before execution. OQ6 rule 2 after `EXECUTED`. Proposed, unaccepted, owner decision required | B. `VOID`. C. Delete the draft |
| D10 After a unit commits | A. Correcting amendment only. Committed units stay. Proposed, unaccepted, owner decision required | B. Automatic rollback. C. Rebase or a correcting amendment |
| D11 Version identity | A. Existing `commercial_revision`. No new column. Proposed, unaccepted, owner decision required | B. A new column at `V1_10` or higher, next free at build time, with a rollback statement. Never `V1_7_3`. V1_9 reserved for S4 |
| D12 Refusal audit | A. `AMENDMENT_REVISION_REJECTED` through the S2 writer and its outbox. The check writes nothing. Proposed, unaccepted, owner decision required | B. A new `REVERTED` type and an S6 contract. S6 is not authorized |
| D13 Dry-run audit | A. No audit row. Proposed, unaccepted, owner decision required | B. `commercial_decision_audit`. C. A pricing confirmation snapshot |
| D14 S4 inside the dry-run | A. Do not call apply. Do not write the ledger. S4 is not built. Proposed, unaccepted, owner decision required | B. Apply inside a rolling-back transaction |

## Migration, flags, and runs

This note writes no migration file, no flag, and no deploy. V1_9 stays reserved for S4 `document_apply_ledger` as designed in the gate doc. The ledger table remains unchosen by the partial-apply owner decision. Any other future CAM migration is `V1_10` or higher, next free at build time, and carries a rollback statement. `V1_7_3` is forbidden: Flyway versions `1.7.3` < `1.8`, and this note does not set `out-of-order`. A live Flyway run was not executed. **Unverified** as a runtime check.

No run against real data is designed. The dry-run in this note is a response shape. It is not `contract_header_backfill_count`, not `contract_header_backfill_apply`, and not S4 apply.

## What this note does not do

- It does not authorize S3, S4, S5, S6, S7, or S8. S3 and S6 code stay unlaunched. S6 is not authorized. S4 is not built.
- It does not change OQ5 option A or the OQ7 thin contract.
- It does not reopen the Accepted proration ADR or the Accepted uplift precedence.
- It does not treat ADR-017 or ADR-024 through ADR-027 as prerequisites.
- It does not accept the DRG-609 design, and it does not price an entitlement or a conversion.
- It does not edit the OpenAPI draft, the gate doc, or the frames in [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) through [#660](https://github.com/drgaciw/open-crm-cpq/pull/660).
- It does not enable a relay, a flag, or a gateway route.

## Unverified

- Linear Backlog status for DRG-551, and the related-issue titles, live outside the git tree.
- DealHub, Salesforce, and Zuora pages named on the issue were not re-fetched. The research file was read.
- The search for a lifecycle dry-run or revert type can miss a differently named method. No database catalog was queried.
- GitNexus has no index in this tree (no `.gitnexus` directory). Impact is **UNKNOWN**. An empty graph is not evidence that a symbol is unused.
- Whether [approval-requirements-skeleton.md](../approval-requirements-skeleton.md) is accepted was not re-read.
- A live Flyway ordering check was not executed.
