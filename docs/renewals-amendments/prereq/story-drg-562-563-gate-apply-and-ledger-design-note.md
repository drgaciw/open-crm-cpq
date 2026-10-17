# DRG-562 and DRG-563 — gate apply on an executed amendment, and the apply ledger across subscriptions

**Status:** Proposed, unaccepted, owner decision required.

*open-crm-cpq (drgaciw/open-crm-cpq). S4 design note only. Date: 2026-10-17. Base: `main` at `63e6ec8d` (merge of #663).*

**Stories.** [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562/gate-subscription-apply-on-executed-amendment-not-quote-acceptance) (RA-28) asks to gate subscription apply on an executed amendment. [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563/apply-ledger-cannot-record-one-amendment-across-multiple-subscriptions) (RA-29) asks for a ledger that can record one amendment across several subscriptions. DRG-562 and DRG-563 stay **In Review** until the owner merges. This note does not transition them. S4 is [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582). This note is the design those two stories are waiting on. It does not build S4.

**What accepting this note does.** It records proposed defaults for a later owner decision. Accepting it authorizes no code, no migration, no flag, no topic, no gateway route, no deploy, and no story. S4 / DRG-582 stays unauthorized. S3 / DRG-581 stays unlaunched. S5 / DRG-583 and S6 / DRG-584 stay blocked. Apply stays flag-off. No apply runs. No backfill runs.

## Verification

Nothing in this pass was executed. No Maven run, no Flyway run, no Testcontainer, no HTTP call, no apply, and no backfill.

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at `63e6ec8d`. Not executed. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `unverified` | Not established by a file or issue read in this pass. |

GitNexus has no index for this tree. Impact is **UNKNOWN**. An empty graph is not evidence that a symbol is unused.

## Question

When may CAM write subscription state for an amendment, and how does one executed document record a separate committed outcome for each target subscription?

## Already decided, and not a fork of this note

These records stand. This note cites them and does not reopen them.

| Record | What it already says | Where |
|---|---|---|
| OQ5 option A | First slice only: one unit per target subscription, atomic units, no automatic rollback, `PARTIALLY_APPLIED` when a later unit fails, `APPLY_BLOCKED` when no unit has committed, correcting amendment as remedy. `specs/partial-apply.md` is not accepted. S4 is not authorized by that cell. | `docs/renewals-amendments/renewals-amendments-open-questions-register.md` |
| OQ6 | Executed documents are immutable, including every apply outcome. The rule is quoted in the next section. | `docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md` (#642, as updated on this `main`) |
| OQ7 thin contract | One thin contract per existing subscription. No regrouping and no synthetic history. This note does not change that contract. | Register row OQ7; [delegated record](delegated-renewals-amendments-decisions-2026-10-17.md#migration-and-access) (#663) |
| OQ10 | `NOT_REQUIRED`, `NOTICE_ONLY`, and `CLICK_THROUGH` only. The DRG-457 adapter is later. | Register row OQ10 |
| OQ18 | Confirmed under delegation: one shared digest utility in `backend/libs/common`, format `CAM-COMMERCIAL-1`, built once in S3. S5 and S6 may reuse it and may not fork it. Confirmation is not implementation authorization. S3 stays unauthorized. S5 and S6 stay blocked. | Delegated record D-DIGEST; OQ6 current decision |
| V1_9 and `document_apply_ledger` | **RESERVED and PROPOSED. Not existing.** The gate names the future shape. No CAM `V1_9` file is on main. This note writes no migration. | Gate, section "S4 / DRG-582 — CAM V1_9 apply ledger"; CAM migration directory, checked this pass |
| Later CAM versions | Any additive CAM migration other than that reserved V1_9 is the next free `V1_10` or higher at build time. Rollback to `NOT NULL` refuses while any affected row is null and never fabricates values. Existing migrations stay immutable. Never `V1_7_3`. | Delegated record D-STORAGE; [#657](https://github.com/drgaciw/open-crm-cpq/pull/657) `s3-reconciliation-and-owner-decision-frame.md` |
| Proration | The proration record is Accepted. This note does not reopen it and does not restate its formula. Apply, when a later story builds it, writes periods the executed document already carries. | `docs/renewals-amendments/prereq/adr-amendment-proration-formula.md` |
| Uplift precedence | Accepted D-04, quoted below. Apply does not merge rungs. | `docs/renewals-amendments/prereq/adr-renewal-uplift-basis-and-precedence.md` |
| ADR-017, ADR-024 through ADR-027 | **Proposed.** They are not prerequisites of this design. | `docs/adr/README.md` |
| DRG-609 | Not accepted. Entitlements and conversion price in this note stay pending that design, as #663 records: older DRG-609 proposals are not accepted wholesale. | `story-drg-609-pricing-service-continuation-design.md`; delegated record, authority and scope |
| Cluster B apply shape | Three units (create SaaS, end maintenance, revoke entitlement) are named and are not built. That option adds no S4 acceptance criterion. | [#652](https://github.com/drgaciw/open-crm-cpq/pull/652), adopted by #663 |
| ONE_TIME lines | Later scope. They do not widen the first-slice AMEND / Add / Update / Remove commercial scope. A one-time charge is not a subscription rate period. | [#654](https://github.com/drgaciw/open-crm-cpq/pull/654), adopted by #663 |
| Fulfilment request | A CAM durable per-unit request is recorded only after successful commercial unit application. No order-service scaffold and no ADR-026 topic. | Delegated record, fulfilment |
| S3 authorization | S3 stays unauthorized. A merge of #657 was not S3 authorization, and #663 adopts that default. | `s3-reconciliation-and-owner-decision-frame.md` fork 3 |

Accepted uplift precedence, D-04, quoted:

> Each field is taken from the first rung that sets it: contract clause, then line, then agreement, then account, then the rules default. Unset fields fall through. The defaults in D-01, D-02, and D-03 apply only when every rung leaves the field unset.

That order is CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES.

## OQ6, quoted

The following is the current decision and the first-slice rule from `docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md`, copied as written. It is not paraphrased.

```text
## Current decision

The pre-execution revision rule, stable provisional number, terminal refusal, executed immutability and at-most-one executed revision remain as recorded below. The [CAM contracts](delegated-renewals-amendments-decisions-2026-10-17.md#cam-transactions-and-delivery) now select the CAM API/version/idempotency behavior, audit types, local concurrency criterion and durable S5 delivery. The [storage/digest contract](delegated-renewals-amendments-decisions-2026-10-17.md#storage-and-digests) now selects nullable unpriced values, a CAM-owned immutable envelope store and genuine close-time hashing. AC-S3-9..11, AC-S5-7 and AC-S8-6 are adopted criteria, with execution evidence still required.

OQ18 is confirmed under delegation: one shared utility in `backend/libs/common`, delivered with S3, reused without forks by S5/S6, using the versioned commercial envelope and canonicalization contract in the linked record. The old owner-confirmation gate is satisfied by this decision. S3's separate implementation-authorization gate remains.
```

The file then says, exactly: "The material below preserves the original reading, delegation evidence and rationale. References to unconfirmed OQ18 or open boundary contracts describe that earlier state and are superseded by the current decision above."

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

The source file labels section 2 as the historical ruling and says references in that historical material to unconfirmed OQ18 or to open boundary contracts are superseded by the current decision. Rule 2 is the executed-state rule this note uses. This note does not amend it.

## Already on main

CAM migrations present in `backend/services/contract-asset-management/src/main/resources/db/migration/`:

| File | What this note relies on |
|---|---|
| `V1_6__subscription_rate_schedule.sql` | `applied_quote_acceptances.accepted_quote_id VARCHAR(255) NOT NULL UNIQUE` and one `subscription_id`. `commercial_digest VARCHAR(64) NOT NULL`. `subscriptions.commercial_revision`. `subscription_rate_periods.provenance VARCHAR(30) NOT NULL` with no check constraint in this file. |
| `V1_7__contract_documents.sql` | Document status check includes `EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`. `signature_policy` check is `WET`, `E_SIGN`, `CLICK_THROUGH`, `NOTICE_ONLY`, `NOT_REQUIRED`. Partial unique index `uq_amendment_revision_one_executed` allows one revision with `status_at_close = EXECUTED`. `amendment_application_units` primary key is `(document_id, unit_no)`, with nullable `apply_key_digest`, `target_type` in `SUBSCRIPTION`, `ENTITLEMENT`, `ONE_TIME`, `NONE`, and unit status `PENDING`, `APPLIED`, `BLOCKED`. `amendment_document_events` is append-only (`reject_amendment_document_event_mutation`). `subscription_rate_periods` gains nullable `amendment_document_line_id` and `revision_no`, both null or both set. |
| `V1_7_1__durable_renewal_eligibility_publications.sql` | On main. Not an apply ledger. |
| `V1_7_2__freeze_amendment_line_membership.sql` | On main. Not an apply ledger. |
| `V1_8__contract_header_backfill.sql` | Header says V1_9 is reserved for `document_apply_ledger` (S4 / DRG-582) and says not to use V1_9 here. The file installs the header backfill and does not execute it. It does not create `document_apply_ledger`. |

**V1_9 and `document_apply_ledger` are RESERVED and PROPOSED. They do not exist.** Proposed, unaccepted, owner decision required. A listing of `backend/services/contract-asset-management/src/main/resources/db/migration/` on this `main` shows `V1_6__subscription_rate_schedule.sql`, `V1_7__contract_documents.sql`, `V1_7_1__durable_renewal_eligibility_publications.sql`, `V1_7_2__freeze_amendment_line_membership.sql`, and `V1_8__contract_header_backfill.sql`. No file in that directory is named `V1_9`. No file creates `document_apply_ledger`. `verified by reading code`. This note writes no migration file. A live database was not queried. `unverified` whether any database outside this tree already has that table.

quote-service has its own `V1_9__quote_ramp_steps.sql` (`backend/services/quote-service/src/main/resources/db/migration/V1_9__quote_ramp_steps.sql`). The filename was seen. The SQL text was not opened. That file is not the CAM version. `unverified` as a reading of its statements.

`spring.flyway` in `backend/services/contract-asset-management/src/main/resources/application.yml` sets `enabled`, `baseline-on-migrate: false`, `default-schema`, and `schemas`. It does not set `out-of-order`. `verified by reading code`. A Flyway run was not executed. [#657](https://github.com/drgaciw/open-crm-cpq/pull/657) records that `V1_7_3` sorts below `V1_8`. This note does not use `V1_7_3`.

### `applied_quote_acceptances` checked against main

**Default:** Proposed, unaccepted, owner decision required. For DRG-563, `applied_quote_acceptances` stays **unchanged**.

Checked on this `main`:

| Evidence | What it shows |
|---|---|
| `backend/services/contract-asset-management/src/main/resources/db/migration/V1_6__subscription_rate_schedule.sql` | The only `CREATE TABLE applied_quote_acceptances`. Columns: `accepted_quote_id VARCHAR(255) NOT NULL UNIQUE`, one `subscription_id BIGINT NOT NULL`, `operation`, `commercial_digest VARCHAR(64) NOT NULL`. No `amendment_document_id`. No `application_unit_no`. |
| `V1_7__contract_documents.sql` | Header comment: "no change to applied_quote_acceptances (ledger is V1_9 / DRG-582)." A search of that file finds the name only in that comment. `ContractDocumentsMigrationScriptTest.migrationDeclaresS1SchemaAndOmitsLaterSliceColumns` strips line comments and asserts the remaining script does not contain `applied_quote_acceptances` or `document_apply_ledger`. That test was read, not run. |
| `V1_7_1__durable_renewal_eligibility_publications.sql`, `V1_7_2__freeze_amendment_line_membership.sql`, `V1_8__contract_header_backfill.sql` | A search of `backend/services/contract-asset-management/src/main/resources/db/migration/` finds no `applied_quote_acceptances` statement in these three files. |
| `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/AppliedQuoteAcceptance.java` | Maps `accepted_quote_id` (unique), `subscription_id`, `operation`, and `commercial_digest`. No amendment document field and no unit number. |

`verified by reading code`. Later migrations do not alter the table. The schema 2.6 sentence that the table gains nullable `amendment_document_id` and `application_unit_no` is not on main. This note does not add those columns.

Apply path on main, `verified by reading code`:

- `QuoteAcceptedEventConsumer` calls `acceptedQuotes.apply(event)` (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java`).
- `AcceptedQuoteSubscriptionService.apply` locks on the quote id, loads `applied_quote_acceptances` by `accepted_quote_id`, returns when the stored `commercial_digest` matches, and throws `NonRetryableEventException` when the quote id is reused with a different digest (`AcceptedQuoteSubscriptionService.java`, `apply`).
- `mutate` loads one subscription by target number, requires `observedRevision` to equal `commercial_revision`, end-dates replaced lines, writes rate periods, increments `commercial_revision` by one, sets `erpSyncStatus` to `QUEUED`, clears the ERP claim token and claim revision, and inserts one `applied_quote_acceptances` row for that quote and that subscription (`mutate`).
- `QuoteAcceptedEvent` (`backend/libs/common/src/main/java/com/opencrm/common/event/dto/QuoteAcceptedEvent.java`) has no `amendmentDocumentId` and no `applicationUnitNo`. A search of that file found neither name.
- Provenance strings written in CAM Java on this tree are `LEGACY_AGGREGATE`, `ACCEPTED_PRICED`, and `ACCEPTED_PRORATED`. No `AMENDMENT_APPLIED` assignment was found.

The proration record's context says `AcceptedQuoteSubscriptionService` reads the accepted quote's priced snapshot and does not calculate days or money. This note does not restate the formula. `verified by reading code` for the service. The ADR sentence was read in `adr-amendment-proration-formula.md`.

## V1_9 stays the gate paragraph

**Status of the table:** RESERVED and PROPOSED. Not existing. Proposed, unaccepted, owner decision required. Nothing in this note is a migration.

The gate, on this `main`, still says:

> New CAM migration `V1_9` (suffix unspecified) creating `document_apply_ledger`. Unique key (`document_id`, `unit_no`). Stored `apply_key_digest`. Same digest on replay is a no-op. A different digest is rejected and audited. Technical approach marks this ledger shape `[I]`.
>
> `applied_quote_acceptances` stays unchanged for the legacy path (same section). The field schema §2.6 still says that table gains nullable `amendment_document_id` and `application_unit_no`. The later technical-approach text says bypass, not mutation. Both were read. The V1_9 paragraph is the one that names S4's migration.

Technical approach section 3 item 3:

> new `document_apply_ledger` with a unique key (`document_id`, `unit_no`) and stored `apply_key_digest`; a replay with the same digest is a no-op and a different digest is rejected and audited; keep `applied_quote_acceptances` unchanged for the legacy path [I] (avoids altering a UNIQUE constraint under load; resolves the DRG-563 conflict by bypass, not mutation).

This note does not add a column, a key, or a suffix to that paragraph. It does not write `V1_9__*.sql` or any other migration file. `document_apply_ledger` is a reserved name in that paragraph. It is not a table in the CAM migration directory. `amendment_application_units` already has the same key and a nullable `apply_key_digest` (`V1_7__contract_documents.sql`). That table stays the planned unit row. It is not substituted for the reserved ledger, and this note does not create the reserved ledger.

`prereq/partial-apply-owner-decision.md` section 9 says the ledger home was not chosen between the V1_9 paragraph and the schema 2.6 columns. This note does not pretend that file chose. Fork 3 below is the proposed default for this design, and it keeps the gate paragraph.

Schema addendum in `amendment-entity-field-schema.md` states the same V1_9 shape and says legacy `applied_quote_acceptances` stays untouched. Schema section 2.6 still says that table gains nullable `amendment_document_id` and `application_unit_no`. Both sentences were read. Fork 3 keeps the gate sentence.

Rollback, if a later authorized story writes the reserved file: this note writes no down-migration and no flag. Technical approach section 3 says rollback is the flag, not a down-migration. S6 stays blocked, so this note does not make that flag the rollback, and apply stays flag-off. The rollback statement for any later additive change that is not the reserved V1_9 table is D-STORAGE: next free `V1_10` or higher, and a `NOT NULL` rollback refuses while any affected row is null and never fabricates values. `V1_7_3` is not available. No such migration is written here.

## Owner decisions

Every default in this table is **Proposed, unaccepted, owner decision required.**

| Fork | Default | Alternatives |
|---|---|---|
| 1. Executed-state gate | Proposed, unaccepted, owner decision required. A first write is allowed only when `amendment_documents.status` is `EXECUTED` and exactly one revision has `status_at_close = EXECUTED`. `QuoteAccepted` does not write the reserved ledger. The predicate is a proposal. It is not an adopted gate. | Apply while the document is `APPROVED`. Keep today's `QuoteAccepted` path as the document path. |
| 2. Replay after status leaves `EXECUTED` | Proposed, unaccepted, owner decision required. The same `(document_id, unit_no)` and the same `apply_key_digest` are a no-op even when status is already `APPLIED` or `PARTIALLY_APPLIED`. Any other command on a document whose status is not `EXECUTED` is refused and audited. `PARTIALLY_APPLIED` and `APPLY_BLOCKED` are not retried. | Treat AC-S4-8 as absolute, so a second delivery after status leaves `EXECUTED` is a refusal. Retry a unit that has no ledger row while the document is `PARTIALLY_APPLIED`. |
| 3. Ledger home | Proposed, unaccepted, owner decision required. Keep the gate paragraph as RESERVED and PROPOSED. `document_apply_ledger` does not exist and is not created here. `applied_quote_acceptances` stays unchanged, as checked against main above. `amendment_application_units` stays the V1_7 plan row. | Add nullable `amendment_document_id` and `application_unit_no` on `applied_quote_acceptances` (schema 2.6). That alternative is not on main, and this note writes no migration for it. Use only `amendment_application_units.apply_key_digest` and leave the reserved table uncreated. |
| 4. Per-subscription rows | Proposed, unaccepted, owner decision required. One ledger row per `(document_id, unit_no)`. The first slice already has one unit per target subscription (OQ5 A, unchanged). Several subscriptions of one document are several rows. The gate key does not gain `subscription_id`. | Add `subscription_id` to the ledger (a later `V1_10` or higher, next free at build time, with the D-STORAGE rollback; never `V1_7_3`; V1_9 stays the gate table). Drop `accepted_quote_id` uniqueness and insert one legacy row per subscription. |
| 5. Legacy path and flag | Proposed, unaccepted, owner decision required. Apply stays flag-off. `AcceptedQuoteSubscriptionService.apply` and `QuoteAcceptedEventConsumer` stay as they are on main. This note adds no flag and enables no flag. | Gate the consumer in this design. Add or enable the S6 per-tenant flag in this note. |
| 6. Digest bytes and algorithm | Proposed, unaccepted, owner decision required. Owner fork for which bytes are hashed. The algorithm is the one OQ18 utility in `backend/libs/common`, format `CAM-COMMERCIAL-1`, built once in S3 (#663 D-DIGEST). S4 reuses that utility and does not duplicate it. The proposed bytes are that unit's slice of the executed envelope. This note does not build the utility. S3 stays unlaunched. S5 and S6 stay blocked. | Hash the whole-document envelope with that same utility. Hash each line with that same utility. A second algorithm, a CAM-local hasher, or a copy of the utility is not an alternative. |
| 7. Audit record for a different digest | Proposed, unaccepted, owner decision required. **OPEN FORK.** The gate sentence stands: a different digest is rejected and audited. This note names no audit-record shape and invents no event type. | A later row on the existing `amendment_document_events` table, with the `event_type` left unnamed here. Reuse the revision-refusal type `AMENDMENT_REVISION_REJECTED` that D-RACE already named, which this note does not retarget. A new audit table, which this note does not create. |
| 8. Signature and auto-execute | Proposed, unaccepted, owner decision required. Apply does not read `signature_policy` and does not move a document to `EXECUTED`. | Apply auto-executes `CLICK_THROUGH`, `NOTICE_ONLY`, and `NOT_REQUIRED`. Require `OUT_FOR_SIGNATURE` before apply. |
| 9. ERP queue | Proposed, unaccepted, owner decision required. A committed subscription unit sets `erp_sync_status` to `QUEUED` and clears the claim token and claim revision, once. A replay does not queue again. No ERP call is made. | Queue ERP when the document becomes `EXECUTED`, before any unit commits. |
| 10. Provenance | Proposed, unaccepted, owner decision required. The rate period keeps `ACCEPTED_PRICED` or `ACCEPTED_PRORATED`. The amendment link is `amendment_document_line_id` and `revision_no`, already on the table from `V1_7__contract_documents.sql`. | Set `provenance` to `AMENDMENT_APPLIED`. |
| 11. Entitlement and conversion price | Proposed, unaccepted, owner decision required. A unit with `target_type` `ENTITLEMENT`, and any conversion price, stay pending the DRG-609 design. #663 does not accept that design. This note defines no entitlement write and no conversion price. Cluster B's three units stay named and unbuilt. | Specify the revoke-entitlement unit and a conversion price in this note. |
| 12. `ONE_TIME` ledger row | Proposed, unaccepted, owner decision required. A `ONE_TIME` line does not insert `document_apply_ledger` and does not insert `subscription_rate_periods`. [#654](https://github.com/drgaciw/open-crm-cpq/pull/654) owns the line and keeps it later scope. | Insert a ledger row for a `ONE_TIME` unit and write no rate period. |
| 13. `commercial_revision` | Proposed, unaccepted, owner decision required. Each committed subscription unit increments that subscription's `commercial_revision` by one inside the unit's atomic write. A blocked unit increments nothing. A replay increments nothing. | Increment once per document. Increment when the document becomes `EXECUTED`, before a unit commits. |
| 14. Apply receipt table | Proposed, unaccepted, owner decision required. Replay identity for apply is the gate key plus `apply_key_digest`. V1_9 does not add a second receipt table. D-IDEMPOTENCY stays the revision and priced-write receipt #663 already adopted. | Also store an apply operation receipt. That table would be `V1_10` or higher, next free at build time, with the D-STORAGE rollback, and is not written here. |
| 15. S4, S5, and S6 | Proposed, unaccepted, owner decision required. S4 / DRG-582 stays unauthorized. S3 stays unlaunched. S5 / DRG-583 and S6 / DRG-584 stay blocked. Apply stays flag-off. DRG-562 and DRG-563 stay In Review. | A later owner record that explicitly authorizes DRG-582. That record is not this note. |

## 1. Executed-state gate

**Default:** Proposed, unaccepted, owner decision required. Apply's first write requires an executed document, and `QuoteAccepted` is not that write. The default is a proposal. It is not an adopted rule.

The proposed predicate is both of these, read from tables already created by `V1_7__contract_documents.sql`:

- `amendment_documents.status = EXECUTED`
- one row in `amendment_document_revisions` for that document with `status_at_close = EXECUTED` (index `uq_amendment_revision_one_executed`)

`DRAFT`, `IN_REVIEW`, `APPROVED`, `REJECTED`, `WITHDRAWN`, and `VOID` fail the predicate. AC-S4-8 is that refusal for a document that is not `EXECUTED`. OQ6 rule 2, quoted above, gives "executed but not yet applied" no special revision path. The forward move after `EXECUTED` is apply of that one revision, or a new correcting document. This note does not add `:rebase`.

Closing `EXECUTED` is an S3 transition. S3 is not launched. This note does not add the transition.

**Alternatives beside the default.** Proposed, unaccepted, owner decision required. Apply while status is `APPROVED`, which would write subscription state before execution. Keep `QuoteAcceptedEventConsumer` as the document path, which is the behavior DRG-562 names: rate periods, end-dated lines, `commercial_revision`, and an ERP queue with no execution step between acceptance and apply. Neither alternative is selected.

## 2. Replay after the status string changes

**Default:** Proposed, unaccepted, owner decision required. Same-digest replay is decided from the ledger before the status refusal.

AC-S4-2 says a second delivery of the same apply message is a no-op, and the same key with a different digest is rejected and audited. AC-S4-8 says apply of a document that is not `EXECUTED` is refused. A successful unit moves the document off the string `EXECUTED` (`APPLIED`, `PARTIALLY_APPLIED`, or `APPLY_BLOCKED`). The steps below are a proposed procedure for the reserved ledger. That table is not on main, and these steps do not create it.

1. Look up `(document_id, unit_no)` on the reserved `document_apply_ledger`.
2. A row whose stored `apply_key_digest` equals the command digest is a no-op. No second rate period, no second `commercial_revision` increment, no second ERP queue, no second audit success.
3. A row whose stored digest differs is rejected and audited. The audit-record shape is the open fork in section 6. This note invents no event type. No update and no delete of the stored row.
4. No row, and the executed-state predicate in fork 1 holds: insert the ledger row and apply that unit.
5. No row, and the predicate does not hold: refuse and audit. This includes `PARTIALLY_APPLIED` and `APPLY_BLOCKED`. OQ5 A, unchanged, makes the remedy a correcting amendment, which is a new `document_id`. The new document's unit keys do not collide with the executed document's keys.

A unit that committed has a ledger row. A unit that wrote nothing has no ledger row. OQ5 A's cut is unchanged: `PARTIALLY_APPLIED` when at least one unit has committed and a later unit fails; `APPLY_BLOCKED` when no unit has committed.

**Alternatives beside the default.** Treat AC-S4-8 as absolute, so a replay that arrives after status has left `EXECUTED` is a refusal. That alternative collides with AC-S4-2. Retry an uncommitted unit while status is `PARTIALLY_APPLIED`. That alternative is a second recovery path. OQ6 rule 2, quoted above, keeps one remedy.

## 3. Ledger home

**Default:** Proposed, unaccepted, owner decision required. The gate paragraph is RESERVED and PROPOSED. It is not a table on main, and this note does not write it.

The reserved shape is unique `(document_id, unit_no)`, stored `apply_key_digest`, same digest no-op, different digest rejected and audited. `applied_quote_acceptances` stays unchanged, as the check against main records. The legacy path keeps one row per `accepted_quote_id`. Document-driven apply does not insert into that table. `amendment_application_units` remains the plan row from V1_7, including its own nullable `apply_key_digest` and its `PENDING` / `APPLIED` / `BLOCKED` status. This note does not move the replay rule onto that column and does not drop the column.

DRG-563's gap is the unique `accepted_quote_id` plus one `subscription_id`. The gate resolves it by bypass. One document with units for subscriptions A and B (AC-S4-1) is two ledger rows under one `document_id`, not two rows in `applied_quote_acceptances`.

**Alternatives beside the default.** Proposed, unaccepted, owner decision required. Schema 2.6: nullable `amendment_document_id` and `application_unit_no` on `applied_quote_acceptances`. Those columns are not in `V1_6__subscription_rate_schedule.sql` or in `AppliedQuoteAcceptance.java`, and this note writes no migration that adds them. The partial-apply record left this unchosen. The other alternative is to treat `amendment_application_units.apply_key_digest` as the only replay store and leave the reserved table uncreated. That alternative drops the migration the gate names. This note selects neither, and it writes neither.

## 4. Per-subscription rows

**Default:** Proposed, unaccepted, owner decision required. The subscription grain is the unit grain OQ5 A already fixed, stored under the gate key.

One target subscription is one `unit_no`. The V1_7 unit row's `target_type = SUBSCRIPTION` and `target_ref` are the pointer. The ledger row does not copy `subscription_id` into a new column. Two subscriptions on one contract are two units, two ledger rows, and one document. A second document, including a correcting amendment, is a new `document_id`.

Dependency order stays the unit's `dependency_order` (`V1_7__contract_documents.sql`). DRG-565's first-slice written ordering rules remain the prerequisite named in `prereq/partial-apply-rules-oq5-option-a.md`. This note does not write those rules and does not add an S4 acceptance test.

**Alternatives beside the default.** Add `subscription_id` to `document_apply_ledger`. That is a different shape from the gate paragraph. If a later authorized build adds it, the file is `V1_10` or higher, next free at build time, with the D-STORAGE rollback, and V1_9 remains the gate table. Never `V1_7_3`. The other alternative is to drop `accepted_quote_id` uniqueness and write one legacy acceptance row per subscription. The gate text says bypass, and this note does not adopt the drop.

## 5. Idempotency

**Default:** Proposed, unaccepted, owner decision required. Two identities stay side by side, and this note changes neither implementation.

Legacy identity, already on main: `accepted_quote_id` plus `commercial_digest`, enforced in `AcceptedQuoteSubscriptionService.apply` against `applied_quote_acceptances` (`V1_6__subscription_rate_schedule.sql`). Same digest returns. Different digest throws. One row, one subscription. This note does not edit that class. AC-S4-9, the flag-off fixture, is a later test. The fixture class exists. This note does not run it. Apply stays flag-off: no flag is added and no flag is enabled.

Document identity, reserved and proposed: `(document_id, unit_no)` plus `apply_key_digest`, as the gate wrote it. The table that would hold it does not exist. Fork 2 is the procedure. Fork 6 is the byte recipe.

**Digest bytes and algorithm. Default:** Proposed, unaccepted, owner decision required. This is an owner fork for the bytes, and the algorithm is fixed to the one OQ18 utility.

#663 D-DIGEST, copied from the merged record:

> Confirm a single pure canonicalization/hash utility in `backend/libs/common`, delivered with S3 and reusable by S5/S6. No CAM-local interim implementation. Its format id is `CAM-COMMERCIAL-1`:

The same paragraph later says: "S3 owns shared commercial-digest fixtures; S5/S6 may not fork the algorithm. This confirmation satisfies the OQ18 design-confirmation gate, not implementation authorization."

S4 reuses that utility. S4 does not duplicate it, does not place a second copy in contract-asset-management, and does not define another hash. S3 builds it once and is not launched here. S5 and S6 stay blocked; their reuse is later, and this note does not unblock them.

The proposed bytes, still an owner choice, are that one unit's slice of the executed envelope (the unit and its lines), passed through `CAM-COMMERCIAL-1`, 64 lowercase hex. Two units of one document have two digests. A change that does not touch a unit's slice does not change that unit's stored digest. The compare rule is still the gate sentence. The closed DTO and golden fixtures are the delivery gap #663 names. This note does not publish that DTO. S2 event-chain hashing stays its own format, as D-DIGEST says.

**`commercial_revision`. Default:** Proposed, unaccepted, owner decision required. The increment stays inside the committed unit. One successful subscription unit increments that subscription by one, together with its rate-period writes and its ledger insert, or the unit writes nothing. A blocked unit does not increment. A same-digest replay does not increment. The next document that names the same subscription observes the new revision. A stale `observed_commercial_revision` is the V3 failure AC-S4-3 already states.

**Receipt table. Default:** Proposed, unaccepted, owner decision required. Apply does not add the D-IDEMPOTENCY receipt table. That receipt is the revision and priced-write contract #663 adopted. Apply's replay row is the ledger row.

**Alternatives beside these defaults.** Proposed, unaccepted, owner decision required. Hash the whole-document envelope with the same `backend/libs/common` utility, so a change to unit 2 makes a replay of unit 1 a different digest. Hash each line with that same utility. A second algorithm is not an alternative. Increment `commercial_revision` once for the document, or increment it at `EXECUTED` before a unit commits. Add an apply receipt table at `V1_10` or higher. Gate `QuoteAcceptedEventConsumer` here, or turn a flag on. This note selects none of those, and apply stays flag-off.

No run against stored rows is designed. The V1_8 function is not invoked. No digest is computed over a real document.

## 6. Audit

**Default:** Proposed, unaccepted, owner decision required. **OPEN FORK.** A different digest is rejected and audited. This note does not choose the audit-record shape, and it invents no event type.

The gate sentence is the requirement. The record that satisfies "audited" is not named. No new `event_type` string is introduced. AC-S6-4's names are not adopted here. S6 stays blocked, so this note does not register a schema and does not add a topic.

What is already on main, and is not selected by this fork:

- `amendment_document_events` in `V1_7__contract_documents.sql`, appended by `AmendmentEventWriter` (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java`). Columns include `event_type varchar(80)`, `unit_no`, `payload_digest`, and `payload_ref`. UPDATE, DELETE, and TRUNCATE are rejected by `reject_amendment_document_event_mutation`.
- D-RACE already names local audit types `AMENDMENT_SUPERSEDED` and `AMENDMENT_REVISION_REJECTED` for revision success and revision refusal. Those names are not retargeted onto a digest mismatch.
- The gate's Definition of Done row says S4 stores `apply_key_digest` and re-checks pins, and does not write the pricing decision. This note does not write `commercial_decision_audit` or a PolicyDecision row. DRG-564 and DRG-612 are among the proposals #663 does not accept wholesale.

**Alternatives beside the open fork.** Proposed, unaccepted, owner decision required.

- A later row on the existing `amendment_document_events` table. The `event_type` stays unnamed in this note.
- Reuse `AMENDMENT_REVISION_REJECTED` for the digest rejection. That type is the revision refusal #663 adopted. This note does not retarget it.
- A new audit table. That would be a migration. This note writes none, and the reserved V1_9 paragraph does not include an audit table.

The business write of a different digest does not update or delete a stored ledger row. Where the audit row is persisted, and under which existing type, stays open. A failed audit does not report a successful apply. D-RACE says local audit does not suppress the existing S2 outbox obligation. This note adds no outbox payload.

The fulfilment request #663 places after successful commercial unit application is not redesigned here. This note does not design the worker, the adapter, or a call under a database lock.

## 7. Signature policy

**Default:** Proposed, unaccepted, owner decision required. Apply does not read `signature_policy` and does not auto-execute.

OQ10 already limits the slice to `NOT_REQUIRED`, `NOTICE_ONLY`, and `CLICK_THROUGH`. The column check also allows `WET` and `E_SIGN` (`V1_7__contract_documents.sql`). OQ6, quoted above, says `OUT_FOR_SIGNATURE` cannot be reached because `E_SIGN` and `WET` return 422 `SIGNATURE_NOT_SUPPORTED_IN_SLICE`. D-REVISION keeps `OUT_FOR_SIGNATURE` unsupported. The renewal-automation contract in #663 says no unattended path reaches `EXECUTED` in that slice. DRG-562's issue text proposes auto-execute for click-through and notice-only. This default leaves that transition on S3, which is not launched, and keeps apply as a reader of the executed revision.

**Alternatives beside the default.** Apply itself auto-executes `CLICK_THROUGH`, `NOTICE_ONLY`, and `NOT_REQUIRED`. That moves an S3 transition into S4, and for a renewal it collides with the #663 sentence that no unattended path reaches `EXECUTED`. Require `OUT_FOR_SIGNATURE` before apply. That collides with OQ6 and with D-REVISION.

## 8. ERP queue

**Default:** Proposed, unaccepted, owner decision required. ERP is queued when a subscription unit commits, which is after `EXECUTED`, and a replay does not queue it again.

The fields are the ones `mutate` already sets: `erp_sync_status = QUEUED`, `erp_sync_claim_token` null, `erp_sync_claim_revision` null (`AcceptedQuoteSubscriptionService.java`). No external ERP call is added. The legacy path still queues inside `mutate` on `QuoteAccepted`. This note does not change that method, so the timing change exists only on the document path, which is not built.

**Alternatives beside the default.** Queue ERP at the `EXECUTED` transition, before any unit commits. A document that then ends `APPLY_BLOCKED` would already have been queued.

## 9. Provenance

**Default:** Proposed, unaccepted, owner decision required. No new provenance token.

DRG-563 asks for an `AMENDMENT_APPLIED` provenance value and an amendment-line reference. The line reference is already on main: `subscription_rate_periods.amendment_document_line_id` and `revision_no` (`V1_7__contract_documents.sql`). The provenance values CAM writes today are `ACCEPTED_PRICED`, `ACCEPTED_PRORATED`, and `LEGACY_AGGREGATE`. The default keeps those strings. The document link is the two V1_7 columns, set together when a later apply writes the period.

**Alternatives beside the default.** Set `provenance` to `AMENDMENT_APPLIED`. The column is `VARCHAR(30)` with no check in `V1_6__subscription_rate_schedule.sql`, so the token alone is not a new migration. This note does not adopt the token.

## 10. Entitlements, conversion price, and one-time lines

**Default:** Proposed, unaccepted, owner decision required. Entitlement units and conversion price stay pending the DRG-609 design. #663 records that the older DRG-609 proposal is not accepted wholesale. This note does not claim that design is merged or accepted.

`amendment_application_units.target_type` may be `ENTITLEMENT` (`V1_7__contract_documents.sql`). Cluster B's apply-shape default, adopted by #663, names three units and does not build them, and it adds no S4 acceptance criterion. The fulfilment contract's `REVOKE` request is recorded only after a committed commercial unit and is not a price. This note defines no entitlement write, no licence-server call, and no conversion price.

**`ONE_TIME`. Default:** Proposed, unaccepted, owner decision required. No ledger row and no rate period. [#654](https://github.com/drgaciw/open-crm-cpq/pull/654), adopted by #663, keeps one-time lines later scope and says they do not widen the first slice. A one-time charge is not a subscription rate period. Credits stay in their versioned policy. This note does not price them.

**Alternatives beside these defaults.** Specify the revoke unit and a conversion price here. Insert a `document_apply_ledger` row for a `ONE_TIME` unit with no rate period. This note selects neither.

## 11. What a later S4 build would still have to be given

This list is not an authorization. Fork 15's default is that S4 stays unauthorized, S5 and S6 stay blocked, and apply stays flag-off.

- A separate owner record that authorizes DRG-582. This note is not that record.
- S3 launched and able to close the one `EXECUTED` revision, and able to deliver the one OQ18 utility. This note does not launch S3.
- S5 and S6 remain blocked. Reuse of `CAM-COMMERCIAL-1` does not unblock them.
- DRG-565's first-slice written ordering rules and acceptance conditions, still named as an S4 wait by the OQ5 register cell and by `partial-apply-rules-oq5-option-a.md`.
- The closed commercial-envelope DTO and fixtures #663 requires before an implementation slice is ready. Fork 6 feeds bytes to the OQ18 utility and does not write the DTO.
- AC-S4-1 through AC-S4-9 stay the written S4 criteria in `amendment-first-slice-requirements-and-acceptance.md`. This note adds none. AC-S4-4 and AC-S4-5 stay the quantity and co-term examples. They do not reopen the proration record.
- AC-S4-7 re-checks the pinned pricebook version. The pin is stored earlier. Apply does not recompute uplift and does not call the renewal resolver. Precedence stays D-04.

## Unverified

- The full Linear body of DRG-582. The gate's written scope was read. The issue description was not opened in this pass.
- Whether any database outside this tree contains `document_apply_ledger`.
- Flyway's reaction to a `V1_7_3` file. Not executed. The version is unused here.
- The SQL text of quote-service `V1_9__quote_ramp_steps.sql`. Not opened in this pass.
- A live `commercial_revision` value, a live digest, or a live ERP queue. No data was read.

## What this note does not do

- It does not write a migration, a flag, a topic, a gateway route, or code. `V1_9` and `document_apply_ledger` stay RESERVED and PROPOSED. They are not created.
- It does not change the gate's V1_9 paragraph, and it does not change `applied_quote_acceptances`.
- It does not run apply or the V1_8 backfill. Apply stays flag-off.
- It does not launch S3. S5 and S6 stay blocked.
- It invents no event type. The audit-record shape for a different digest stays an open fork.
- It does not transition DRG-562 or DRG-563. They stay In Review until the owner merges.
- It does not change OQ5 A or the OQ7 thin contract.
- It does not reopen the Accepted proration record or Accepted uplift D-04.
- It does not treat ADR-017 or ADR-024 through ADR-027 as prerequisites.
- It does not accept the DRG-609 design.
