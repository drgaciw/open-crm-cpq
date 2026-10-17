# DRG-566 — Backfill amendment history: design note

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Base:** `main` at `285ffef8` (merge of [#662](https://github.com/drgaciw/open-crm-cpq/pull/662)).

**Story:** [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566/backfill-amendment-history-for-existing-subscriptions) (RA-32). Linear status read this pass: Backlog. Priority Medium. The ticket stays In Review until the owner merges.

This file is a design note only. It authorizes no code, no migration, no flag, no topic, no gateway route, and no deploy. It designs no backfill run and no apply. No count was taken. No function was called. Accepting this note authorizes no code, migration, or deploy.

The S7 manual apply stays an owner-only step. `contract_header_backfill_apply` is not called, wrapped, or extended here. The owner sequence in [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md) is unchanged.

**Impact:** UNKNOWN (no GitNexus index).

## How to read this note

Every fork has options, a default, and the alternatives beside that default. Each default is **Proposed, unaccepted, owner decision required.** A default is what this note asks the owner to confirm. It is not an acceptance, and it is not a run.

**Read** means the cited file was opened on this `main`. **Unverified** means the claim was not established by a file read, or it was not executed. Nothing in this pass was executed: no SQL, no Maven, no Testcontainer, no HTTP call, no Flyway, no dry-run, no apply.

[#662](https://github.com/drgaciw/open-crm-cpq/pull/662) records the owner’s acceptance of the documentation in [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) through [#661](https://github.com/drgaciw/open-crm-cpq/pull/661). That record says documentation acceptance authorizes no data backfill. Per-fork Proposed labels in those frames remain decision requirements. This note cites those defaults and does not replace them.

## Boundaries that stay

- **OQ7 thin contract, unchanged.** Quoted exactly from [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md). The header comment of [`V1_8__contract_header_backfill.sql`](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql) states the same sentences, wrapped across comment lines:

  > One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup.

  That sentence is the S7 header job. History synthesis stays the later DRG-566 question. This note does not amend the sentence, the V1_8 functions, or the register row. The register still says OQ7 is **DEFAULT PROPOSED for slice**: "Slice: contract header only (S7), no synthetic history. History backfill stays DRG-566." Read: [`renewals-amendments-open-questions-register.md`](../renewals-amendments-open-questions-register.md).

- **OQ5 option A, unchanged.** Quoted exactly from section 9 of [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md):

  > What option A is: one unit per target subscription, atomic units, no automatic rollback, PARTIALLY_APPLIED when a later unit fails, APPLY_BLOCKED when no unit has committed, correcting amendment as remedy.

  History synthesis is not an apply of units. This note adds no application unit and does not reopen that record. `specs/partial-apply.md` stays unaccepted.

- **OQ6, quoted exactly** from section 2 of [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md), merged in [#642](https://github.com/drgaciw/open-crm-cpq/pull/642). The five rules, unchanged:

  > 1. **Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`).** A new revision of the LifecycleChange opens a new document revision on the same document, with the same `provisionalNumber` (OQ4). The previous revision closes with `status_at_close = SUPERSEDED` and keeps its content digest. The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again. The persisted representation of an unpriced revision is not decided here.
  > 2. **Executed, in any apply state (`EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`).** The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED` and an audit event, which is AC-S3-5 applied to revisions. "Executed but not yet applied" gets no special path. The remedy is a new LifecycleChange that produces a new, correcting amendment document, the same remedy OQ5 option A adopted for a blocked unit.
  > 3. **Command and integration boundary.** CAM must own the revision and execution state transitions. The exact CAM command/API, including route and request/response contract, is not decided here; the OpenAPI draft's LifecycleChange revision operation is tagged S5 and is not an accepted CAM API. The S3 CAM-local test boundary versus the S5 LifecycleChange trigger, delivery, acknowledgement and reconciliation boundary requires a separate accepted contract. No cross-service race or delivery protocol is adopted here.
  > 4. **Terminal `REJECTED` and `WITHDRAWN`.** Neither state is reopened by a revision command; CAM returns 409 and preserves the document and revision digest. This retains the terminal rejection and withdrawal transitions in the canonical policy. A different document is needed for a subsequent proposal.
  > 5. **`OUT_FOR_SIGNATURE`, for the later signature story.** A new revision must void the open signature envelope before rule 1 applies. Nothing in the slice builds it.

  Rule 2 is why a later `APPLIED` history document, if one is ever stored, is immutable. A correction is a new LifecycleChange. This note stores no document, so the rule is not exercised. S3 / DRG-581 stays unlaunched. The audit event in rule 2 is an S3 behavior. It is not built here.

- **S6 is not authorized.** This note adds no event field, topic, registry entry, flag, or outbox contract. Cluster G fork F-598-5 stays as written: a null `contract_id` stays on the current acceptance path, and a header does not switch the row onto a document apply path. Read: [`cluster-g-visibility-queues-migration-decision-frame.md`](cluster-g-visibility-queues-migration-decision-frame.md).

- **ADR-017 and ADR-024 through ADR-027 are Proposed.** They are not prerequisites. Read: status lines in `docs/adr/ADR-017-synchronous-inter-service-communication.md` and `docs/adr/ADR-024-order-orchestration-context.md` through `docs/adr/ADR-027-compensation-and-point-of-no-return.md`. This note chooses no transport, saga, command topic, or compensation path.

- **Proration is Accepted and is not reopened.** Read: status line of [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) (Accepted 2026-10-16). This note does not restate the formula and does not prorate a legacy row.

- **Uplift precedence** follows the Accepted record [`adr-renewal-uplift-basis-and-precedence.md`](adr-renewal-uplift-basis-and-precedence.md). D-04, quoted exactly: "Each field is taken from the first rung that sets it: contract clause, then line, then agreement, then account, then the rules default." The order is CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES. This note prices no line, so that order is not applied to a legacy row.

- **Entitlements and conversion price** are pending the DRG-609 design recorded in [`story-drg-609-pricing-service-continuation-design.md`](story-drg-609-pricing-service-continuation-design.md). That note is a design proposal. It is not accepted. This note does not claim that design is merged or accepted. Cluster B and Cluster F already keep synthetic entitlement history and conversion price out of their frames. This note does the same. `apply_target_type = ENTITLEMENT` and `policy_slot = CONVERSION` are not filled here.

- **Migration state on this `main`.** CAM Flyway files present:
  - `V1_7__contract_documents.sql`
  - `V1_7_1__durable_renewal_eligibility_publications.sql`
  - `V1_7_2__freeze_amendment_line_membership.sql`
  - `V1_8__contract_header_backfill.sql` (S7 functions only; Flyway installs them and does not execute them)
  - There is no `V1_9` SQL file. `V1_9` stays reserved for S4 `document_apply_ledger`, as the gate doc designs it in [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md). This note writes no migration file.
  - Any future migration is `V1_10` or higher, next free at build time, with a rollback statement. Never `V1_7_3`.

## What the story asks for

DRG-566, read this pass, says existing subscriptions’ history is `applied_quote_acceptances` rows with a non-NEW operation, plus rate periods, some with `LEGACY_AGGREGATE` provenance (`V1_6`). It asks for a synthesized ORIGINAL document (seq 0) per subscription, a BACKFILLED document in APPLIED state for each historical non-NEW acceptance, flagged as legacy evidence with no signed copy, no retroactive events, and a cutover rule for in-flight continuation quotes. It also asks the grouping question: one contract per subscription, or one per customer plus original quote.

Spike §6.3 in [`amendment-document-design-spike.md`](../amendment-document-design-spike.md) is marked `[INFERENCE]` and lists the same steps. The 2026-10-16 addendum says decision 7 reduced the first slice to header only and left history on DRG-566.

The renewal stub says past `RENEWAL` acceptances become `BACKFILLED` documents. The price-lock stub says existing locks have no record and cannot be inferred from `LEGACY_AGGREGATE` periods. Read: [`renewal-policy-stub.md`](../renewal-policy-stub.md), [`price-lock-policy-stub.md`](../price-lock-policy-stub.md). This note does not infer a lock.

## Already on main

| Fact | Where it was read |
|---|---|
| `applied_quote_acceptances` has `accepted_quote_id VARCHAR(255) NOT NULL UNIQUE`, `subscription_id`, `operation VARCHAR(20) NOT NULL`, `commercial_digest VARCHAR(64) NOT NULL`. No document id, no tenant id, no action date. | `V1_6__subscription_rate_schedule.sql` |
| The same migration’s insert comment: "Keep historical MRR/ARR/date/currency facts without inventing unit price, quantity or historical TCV." And: "Backfilled periods never emit retroactive RampApplied." Inserted provenance is `LEGACY_AGGREGATE`, `accepted_line_uuid` is `legacy-aggregate`, `created_by` is `r3-rate-backfill`. Quantity and unit price are omitted. | Same file |
| `AcceptedQuoteSubscriptionService` sets `operation` from the accepted event’s deal type and allows `NEW`, `AMENDMENT`, `EXPANSION`, `RENEWAL`. `EXTENSION` is not in that allow-list. | `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/service/AcceptedQuoteSubscriptionService.java` |
| `QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`. | `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java` |
| Quote-service `V1_10__quote_subscription_continuation.sql` adds `continuation_request_json`. `V1_12__continuation_operation.sql` adds `continuation_operation` and fills it from that JSON. Its comment says `EXTENSION` and `RENEWAL` both remain `deal_type` `RENEWAL`. Rows with no readable operation stay NULL. | Those two quote-service migrations |
| `amendment_documents.document_kind` allows `ORIGINAL` and `BACKFILLED`. `status` allows `APPLIED`. `signature_policy` allows `NOT_REQUIRED`. `evidence_class` allows `STANDARD` and `LEGACY`. `uq_amendment_document_seq` is `UNIQUE (contract_id, amendment_seq)`. `uq_amendment_document_lifecycle` is `UNIQUE (lifecycle_change_id)`. `lifecycle_change_id` and `lifecycle_change_revision` are `NOT NULL` and have no `REFERENCES` clause in the statements read. `uq_amendment_document_request` is unique on `(tenant_id, request_identifier)` where `request_identifier` is not null. Money columns `tcv_delta`, `arr_delta`, `one_time_total`, `credit_total` are `NOT NULL`. `content_digest` is 64 lowercase hex. `signed_copy_sha256` is nullable. `chk_amendment_number_with_seq` requires `amendment_seq` and `amendment_number` both null or both set. | `V1_7__contract_documents.sql` |
| `amendment_document_lines.unit_price` and `net_amount` are nullable. A non-null price requires `policy_id`, `policy_version`, `policy_decision_id`, and `pricing_trace_id`. `configuration_hash` is 64 lowercase hex and `NOT NULL`. `product_external_id` and `pricebook_version` are `NOT NULL`. A line requires an application unit. `apply_target_type` includes `ENTITLEMENT`. `policy_slot` includes `CONVERSION`. | Same file |
| `amendment_document_events` is append-only (UPDATE, DELETE, and TRUNCATE raise). `event_type` is `varchar(80) NOT NULL` with no value list in the constraints read. | Same file |
| `AmendmentEventWriter.append` stages one hash-chained event and an audit outbox row in the caller’s transaction. | `AmendmentEventWriter.java` class comment and `auditOutbox.stage` |
| S7 functions `contract_header_backfill_count`, `contract_header_backfill_apply`, and `contract_header_backfill_compensate` exist. Count writes nothing. Apply is the manual owner step. Compensate deletes only `created_by = V1_8_HEADER_BACKFILL`. Flyway has no down migration. | `V1_8__contract_header_backfill.sql`; [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md) |
| `subscriptions.quote_id` and `subscriptions.customer_id` are `VARCHAR(255) NOT NULL`. | `V1_1__create_contract_tables.sql` |
| Header fill sets `last_executed_seq = 0` and `created_by = V1_8_HEADER_BACKFILL`. No amendment document. | `V1_8` comment and the S7 companion |

Whether any environment has called `contract_header_backfill_apply` is `unverified`. This note does not call it.

## Owner-decision table

| Fork | Question | Options | Default | Alternatives beside the default |
|---|---|---|---|---|
| F1 | Which history is in scope, and where does it sit relative to a DRG-598 phase? | A. The sets named by DRG-566 and spike §6.3 steps 2–4, as a later story outside the phase. B. ORIGINAL only. C. Start fresh; synthesize nothing. D. The phase writes the history. | A. Proposed, unaccepted, owner decision required. | B, C, and D. D contradicts Cluster G F-598-4 and is not selected. |
| F2 | Which contract does a history document hang on? | A. The subscription’s existing thin-contract header. No regroup. B. Group by customer plus original quote. C. Wait for DRG-554 before any document. | A. Proposed, unaccepted, owner decision required. | B and C. |
| F3 | What provenance is stored, and what price is refused? | A. `evidence_class = LEGACY`. Rate-period provenance stays `LEGACY_AGGREGATE`. Absent prices stay absent. No lock is inferred. B. Infer unit price from `net_monthly`. C. Rewrite provenance to `AMENDMENT_APPLIED`. | A. Proposed, unaccepted, owner decision required. | B and C. Entitlements and conversion price stay pending the DRG-609 design, which is not accepted. |
| F4 | How is intent chosen for a non-NEW acceptance? | A. Map only onto tokens already in `chk_amendment_document_intent`. `document_kind` is `BACKFILLED` or `ORIGINAL`. No quote rewrite. B. Classify from quote `deal_type`. C. Classify by parsing `continuation_request_json`. D. Add intent `EXTEND` and backfill quotes. | A. Proposed, unaccepted, owner decision required. | B, C, and D. D is Cluster E E5-B/E5-C and is not selected. |
| F5 | What makes a second pass insert nothing? | A. Keys already on main: seq 0 per contract, and `request_identifier = accepted_quote_id`. B. A new idempotency table. C. Delete and reinsert. | A. Proposed, unaccepted, owner decision required. | B and C. B would be a future `V1_10` or higher, next free at build time. |
| F6 | What happens to NOT NULL columns the legacy sources do not fill? | A. Invent nothing. No insert until a later owner-authorized migration says which LEGACY columns may be null. B. Store zero money and a synthetic `lifecycle_change_id`. C. Wait until every acceptance has a real LifecycleChange. | A. Proposed, unaccepted, owner decision required. | B and C. C waits on S5, which this note does not start. |
| F7 | Do BACKFILLED documents consume `last_executed_seq`? | A. ORIGINAL is seq 0. BACKFILLED rows do not take seq 1..n and do not change `last_executed_seq`. B. Assign seq 1..n and set `last_executed_seq` to n. C. Store the acceptance as `DRAFT` with a null seq. | A. Proposed, unaccepted, owner decision required. | B and C. |
| F8 | What is the audit trail? | A. The LEGACY document row and its `content_digest`. No `amendment_document_events` row. No outbox row. No retroactive `RampApplied`. B. A hash-chain event and no outbox. C. A `commercial_decision_audit` row. D. A retroactive `AmendmentApplied`. | A. Proposed, unaccepted, owner decision required. | B, C, and D. D contradicts spike §6.3 step 5 and the V1_6 comment. |
| F9 | When would a write be allowed to run? | A. Dry-run first, as a later owner step, separate from `contract_header_backfill_apply`. This note defines no count and no apply. B. Fold history into `contract_header_backfill_apply`. C. Write the rows from Flyway at deploy. | A. Proposed, unaccepted, owner decision required. | B and C. The S7 apply stays owner-only. |
| F10 | What happens to in-flight continuation quotes? | A. They stay out of this history set. No flag. B. Synthesize documents for SENT and APPROVED quotes now. C. Turn on the OQ11 per-tenant flag. | A. Proposed, unaccepted, owner decision required. | B and C. C is S6 and is not authorized. |
| F11 | Which migration version, and what rollback does it state? | A. No file in this note. A later file is `V1_10` or higher, next free at build time, with the rollback statement in F11. Never `V1_7_3`. `V1_9` stays reserved. B. Use `V1_9`. C. Use `V1_7_3`. | A. Proposed, unaccepted, owner decision required. | B and C. |
| F12 | Do `LEGACY_AGGREGATE` periods become document lines? | A. No line insert and no rate-period update. B. Insert lines with a null price. C. Add `amendment_document_line_id` on the rate period and point it at a line. | A. Proposed, unaccepted, owner decision required. | B and C. C would be a future `V1_10` or higher. |

## F1 — What history is backfilled

Spike §6.3 steps 2–4 and the DRG-566 description name two sets:

1. One `ORIGINAL` document, `amendment_seq` 0, per subscription, from `subscriptions.quote_id`.
2. One `BACKFILLED` document per `applied_quote_acceptances` row whose `operation` is not `NEW`, with `status` `APPLIED`, `signature_policy` `NOT_REQUIRED`, `evidence_class` `LEGACY`, and no signed copy (`signed_copy_sha256` null).

`NEW` rows are the original sale. They are the source of the ORIGINAL document, not a second BACKFILLED document. Rate periods with provenance `LEGACY_AGGREGATE` are the third source the spike names. F12 says they are not copied onto lines under this default.

Out of scope under this default: quote-column rewrite (Cluster E fork E5 already left historical quotes as `V1_12` wrote them), price locks, entitlement history, fulfilment rows, and any write inside a DRG-598 phase. Cluster G F-598-4 default is headers only inside a phase, with DRG-566 outside the phase. This note keeps that default. Option D in the table is the phase writing BACKFILLED documents. It is the unchosen F-598-4 option B.

**Default:** A. Proposed, unaccepted, owner decision required. The two document sets above are the history, and they stay outside the DRG-598 phase. Alternatives beside this default: B, ORIGINAL only; C, start fresh and synthesize nothing (spike decision 7’s other bullet); D, the phase writes the history.

## F2 — Grouping

The thin-contract wording is one contract per existing subscription. `uq_amendment_document_seq` is `UNIQUE (contract_id, amendment_seq)`. ORIGINAL is seq 0. Two subscriptions that share a contract cannot both receive seq 0 on that contract. Under one contract per subscription, that collision does not arise. The collision appears if a later regroup puts two subscriptions on one contract and this story then inserts a per-subscription ORIGINAL. That insert was not executed. `unverified` as a database result. The same observation is already in [`oq7-header-backfill-owner-decision.md`](oq7-header-backfill-owner-decision.md).

"Original quote" is not defined in the spike or in the DRG-566 description. `subscriptions.quote_id` exists. Whether that column is the original quote is `unverified`.

DRG-554 remains the later merge. This note does not move `subscriptions.contract_id`.

**Default:** A. Proposed, unaccepted, owner decision required. Hang each history document on the subscription’s existing thin-contract header. Do not regroup. Alternatives beside this default: B, group by customer plus original quote; C, wait for DRG-554 before any document.

## F3 — Provenance

Provenance is the evidence class on the document and the provenance already stored on the rate period.

- Synthesized documents, if a later story is authorized to write them, use `evidence_class = LEGACY`. The field schema says `LEGACY` is only for backfill. Read: [`amendment-entity-field-schema.md`](../amendment-entity-field-schema.md).
- `subscription_rate_periods.provenance` stays `LEGACY_AGGREGATE` where V1_6 wrote that value. This note does not rewrite it.
- V1_6 omitted unit price and quantity on those periods and said it does not invent them. Absent prices stay absent. Zero is not stored as a price.
- `signed_copy_sha256` stays null. There is no signed copy.
- A price lock is not inferred. The price-lock stub already says existing locks have no record and cannot be inferred from `LEGACY_AGGREGATE` periods.
- Uplift order CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES is the Accepted D-04 rule for a priced renewal. It is not a way to fill a missing legacy price.
- An entitlement target and a conversion price are pending the DRG-609 design, which is not accepted. They are not filled.

`created_by` on a future history row must be a value other than `V1_8_HEADER_BACKFILL`, `S7_HEADER_AT_CREATION`, and `r3-rate-backfill`, so the S7 compensate function cannot delete a history row and a history compensate cannot delete a header. The exact string is chosen when a migration is authorized. It is not chosen here.

**Default:** A. Proposed, unaccepted, owner decision required. `LEGACY` on the document, `LEGACY_AGGREGATE` left as stored, absent prices left absent, no lock inferred. Alternatives beside this default: B, infer unit price from `net_monthly`; C, rewrite provenance to `AMENDMENT_APPLIED`.

## F4 — Intent, without a new token

Cluster E fork E5 default, in [`cluster-e-lifecyclechange-model-decision-frame.md`](cluster-e-lifecyclechange-model-decision-frame.md): a future document for a quote whose `continuation_operation` is `EXTENSION` stores intent `RENEW`. The quote column remains the distinction (`V1_12__continuation_operation.sql`). No new token and no backfill. Fork E6 default: quote `EXPANSION` becomes document intent `AMEND`.

`applied_quote_acceptances.operation` does not contain `EXTENSION`. Classifying history from quote `deal_type` leaves `EXTENSION` and `RENEWAL` indistinguishable. That is the DRG-546 collapse, already recorded in [`oq7-header-backfill-owner-decision.md`](oq7-header-backfill-owner-decision.md). A stored `continuation_request_json` sample was not printed this pass. `unverified`.

Under this default, `document_kind` is `ORIGINAL` for seq 0 and `BACKFILLED` for the non-NEW acceptance document. Kind `BACKFILLED` is the spike’s word and the renewal stub’s word. It is already in `chk_amendment_document_kind`. Intent, which is a different column, copies only a token the check already allows:

| `applied_quote_acceptances.operation` | Document intent | `document_kind` |
|---|---|---|
| `RENEWAL` | `RENEW` | `BACKFILLED` |
| `AMENDMENT` | `AMEND` | `BACKFILLED` |
| `EXPANSION` | `AMEND` | `BACKFILLED` |
| `NEW` | no BACKFILLED document; the ORIGINAL document’s intent is a gap under F6, because ORIGINAL is not an intent token | `ORIGINAL` |

`initiation_trigger` is `NOT NULL` and the check allows `MANUAL`, `LEAD_TIME`, `TERM_END_AUTO`, `THRESHOLD`, `TRUE_UP_CYCLE`, `END_OF_LIFE`, `API`. No legacy column was read that stores one of those values. Choosing one would invent it. That choice waits with F6.

**Default:** A. Proposed, unaccepted, owner decision required. Map only onto existing intent tokens, keep `document_kind` as `BACKFILLED` or `ORIGINAL`, and leave quote rows as `V1_12` left them. Alternatives beside this default: B, classify from `deal_type`; C, parse `continuation_request_json`; D, add `EXTEND` and backfill quotes.

## F5 — Idempotency

A later writer, if authorized, treats an existing match as success and inserts nothing.

Keys already on main, and no new unique index in this note:

- ORIGINAL: one row with `amendment_seq = 0` per `contract_id`, enforced by `uq_amendment_document_seq`.
- BACKFILLED: `request_identifier` set to `applied_quote_acceptances.accepted_quote_id`. That column is unique on the acceptance table (`V1_6`). When `request_identifier` is not null, `(tenant_id, request_identifier)` is unique (`uq_amendment_document_request` in `V1_7`).
- A second pass that finds the same seq 0, or the same `request_identifier`, inserts nothing.

`lifecycle_change_id` is unique and `NOT NULL`. Historical acceptances have no LifecycleChange id. Using that column as the idempotency key would mean inventing an id. That invention is option B of F6, and it is not this default. Quote-service has no `LifecycleChange` type. Read: Cluster E’s gap section.

How PostgreSQL treats multiple NULL `amendment_seq` values under `uq_amendment_document_seq` was not executed. `unverified`. The same sentence is already in the OQ7 note.

**Default:** A. Proposed, unaccepted, owner decision required. Idempotency is seq 0 per contract plus `request_identifier = accepted_quote_id`. Alternatives beside this default: B, a new idempotency table on a future `V1_10` or higher; C, delete and reinsert.

## F6 — Columns the legacy sources do not fill

`V1_7` requires, on every document, values the acceptance row and the `LEGACY_AGGREGATE` period do not have. Read from `V1_7__contract_documents.sql`: `lifecycle_change_id`, `lifecycle_change_revision`, `intent`, `initiation_trigger`, `provisional_number`, `action_date`, `contract_effective_date`, `currency_code`, `tcv_delta`, `arr_delta`, `one_time_total`, `credit_total`, `content_digest`, and `current_revision_no`. A revision also requires `target_quote_id` as `uuid`, `inputs_snapshot_ref`, and `status_at_close`. `subscriptions.quote_id` is `varchar(255)`. Whether it parses as a uuid was not executed. `unverified`.

Storing zero in the money columns would invent a commercial total. The V1_6 comment forbids inventing historical TCV. A synthetic `lifecycle_change_id` would satisfy the unique constraint with a value no LifecycleChange owns.

**Default:** A. Proposed, unaccepted, owner decision required. Invent none of those values. No history insert is possible on today’s `V1_7` shape until a later owner-authorized migration states which columns a `LEGACY` row may leave null. This note writes no migration file. Alternatives beside this default: B, store zero money and a synthetic lifecycle id; C, wait until S5 has created a real LifecycleChange for every acceptance. This note does not start S5.

## F7 — Sequence

Field schema: ORIGINAL is seq 0, and `amendment_seq` is null until `EXECUTED`. S7 headers store `last_executed_seq = 0`. Assigning seq 1..n to BACKFILLED documents and raising `last_executed_seq` would present legacy evidence as the executed sequence and would change the header the S7 job wrote.

`chk_amendment_number_with_seq` allows both the seq and the number to be null. Whether any Java writer requires a seq once `status` is `APPLIED` was not read as a validator. `unverified`.

OQ4, as the field schema records it, says the executed seq is `last_executed_seq + 1`. A BACKFILLED row that does not consume that counter leaves the next real execution at seq 1. That is the point of this default.

**Default:** A. Proposed, unaccepted, owner decision required. ORIGINAL is seq 0. BACKFILLED documents do not consume `last_executed_seq` and do not receive seq 1..n in this note. Alternatives beside this default: B, assign seq 1..n in `accepted_quote_id` order and set `last_executed_seq` to the last of them; C, store the acceptance as `DRAFT` with a null seq.

## F8 — Audit trail

The audit fact is the document row itself, once a later story is authorized to store one: `evidence_class = LEGACY`, `content_digest` of the bytes actually stored, `signed_copy_sha256` null, and `created_by` distinct from the header marker (F3).

No retroactive commercial event. Spike §6.3 step 5 says emit no retroactive events, mirroring V1_6, which never emits retroactive `RampApplied`. The V1_6 comment says the same. Cluster F adds no retroactive fulfilment rows. This note adds none.

No outbox row. `AmendmentEventWriter.append` writes the hash-chain event and stages `AmendmentAuditOutbox` in the same transaction. Using that writer would publish. This note does not call it. S6 is not authorized, so no new event contract is registered.

No `commercial_decision_audit` row. That table, as [`story-drg-564-policy-decision-audit-trail.md`](story-drg-564-policy-decision-audit-trail.md) reads it, records the decision to open a renewal draft. A legacy document with a null price has no policy decision. `chk_amendment_line_priced_policy` requires policy ids when a price is present. This note stores no price and no policy ids.

OQ6 rule 2’s audit event on a refused revision stays an S3 behavior. S3 stays unlaunched.

**Default:** A. Proposed, unaccepted, owner decision required. The audit trail is the LEGACY document row and its digest. No event row, no outbox row, no retroactive `RampApplied`. Alternatives beside this default: B, a hash-chain event with no outbox; C, a `commercial_decision_audit` row; D, a retroactive `AmendmentApplied`.

## F9 — Dry-run first, and the S7 apply stays owner-only

The S7 companion already separates count, review, apply, and compensate. Count writes nothing. Apply is the owner’s manual step. No bot runs it. Cluster G F-598-6 default says those steps stay in the S7 companion. This note runs none of them.

A later history story, if the owner authorizes one, follows the same order and is a different step:

1. A count that writes nothing.
2. The owner reads that count.
3. A separate owner apply.

This note does not define that count, does not name a new function, does not add SQL, and does not call `contract_header_backfill_count` or `contract_header_backfill_apply`. History apply is not folded into the S7 function. Flyway does not write the history rows at deploy. V1_8’s comment already says Flyway installs the header functions and does not execute them. History stays on that pattern.

There is no run against real data in this design. There is no tenant argument, no window, and no manifest.

**Default:** A. Proposed, unaccepted, owner decision required. Dry-run first, as a later owner step, separate from `contract_header_backfill_apply`. This note defines no count and no apply. Alternatives beside this default: B, fold history into `contract_header_backfill_apply`; C, write the rows from Flyway at deploy. The S7 manual apply stays the owner-only step in [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md).

## F10 — In-flight continuation quotes

Spike §6.3 step 6 says SENT and APPROVED quotes get a document lazily, and ACCEPTED but unapplied events take the legacy path behind a feature flag. The flag is S6 / OQ11. S6 is not authorized. Cluster G F-598-5 default stands: `contract_id` null stays on the current acceptance path, and a header does not switch the row onto a document apply path.

**Default:** A. Proposed, unaccepted, owner decision required. In-flight quotes stay out of this history set. No flag is introduced or enabled. Alternatives beside this default: B, synthesize documents for SENT and APPROVED quotes now; C, turn on the OQ11 per-tenant flag.

## F11 — Future migration and rollback

No migration file is written here. CAM `V1_7`, `V1_7_1`, `V1_7_2`, and `V1_8` are on main. `V1_9` stays reserved for S4 `document_apply_ledger` as the gate doc designs it. Any future migration is `V1_10` or higher, next free at build time. Never `V1_7_3`.

Rollback statement a later migration must carry, and which this note does not implement: Flyway has no down migration. A compensate function deletes only rows with the history `created_by` marker from F3, and only after it has cleared no header row. It raises and leaves the rows in place when a non-history amendment document, a `prior_contract_id`, or another subscription references a marked row. Restore from backup is the fallback. Header rows (`V1_8_HEADER_BACKFILL`, `S7_HEADER_AT_CREATION`) are not deleted. The S1 empty-table drop does not apply once document rows exist. That is the same limit the S7 companion already states for headers.

**Default:** A. Proposed, unaccepted, owner decision required. No migration file. A later file is `V1_10` or higher, next free at build time, and includes the rollback statement above. Alternatives beside this default: B, use `V1_9`; C, use `V1_7_3`.

## F12 — Lines for `LEGACY_AGGREGATE` periods

Spike §6.3 step 4 says link rate periods via `accepted_quote_id`, and give `LEGACY_AGGREGATE` periods document lines with unknown unit price, without inventing data. The line table on main cannot store that line without inventing other fields: `product_external_id`, `pricebook_version`, `configuration_hash`, `change_line_ref`, quantity identity, and an application unit are `NOT NULL`. V1_6’s legacy line uuid is the literal `legacy-aggregate`. `subscription_rate_periods` has no `amendment_document_line_id` column. Read: the `CREATE TABLE` in `V1_6__subscription_rate_schedule.sql`.

Cluster F’s default adds no backfill of commitment periods. Updating the rate period would be that kind of write.

**Default:** A. Proposed, unaccepted, owner decision required. No document line and no rate-period update. The link the spike describes stays a gap until a later migration states how an unknown price is stored. Alternatives beside this default: B, insert lines with a null price and the other required fields filled; C, add `amendment_document_line_id` on a future `V1_10` or higher and point it at a line. B and C invent or add structure this note does not authorize. Entitlement lines and conversion-priced lines stay pending the DRG-609 design, which is not accepted.

## Frames cited, and not replaced

| PR | Frame | What this note keeps |
|---|---|---|
| [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) | [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) | The five rules, quoted above. S3 stays unlaunched. |
| [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) | [`cluster-c-trueup-and-meters-decision-frame.md`](cluster-c-trueup-and-meters-decision-frame.md) | No true-up meter history is added here. |
| [#649](https://github.com/drgaciw/open-crm-cpq/pull/649) | [`cluster-d-renewal-automation-decision-frame.md`](cluster-d-renewal-automation-decision-frame.md) | No renewal-automation trigger is started. |
| [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) | [`cluster-a-amendment-modes-and-policy-decision-frame.md`](cluster-a-amendment-modes-and-policy-decision-frame.md) | The thin-contract wording Cluster A quotes stays in force. Entitlement resolution stays pending the DRG-609 design. |
| [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) | [`cluster-e-lifecyclechange-model-decision-frame.md`](cluster-e-lifecyclechange-model-decision-frame.md) | E5-A and E6-A. No `EXTEND` token. No further quote backfill. |
| [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) | [`cluster-b-onprem-to-saas-and-maintenance-decision-frame.md`](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) | Conversion price and entitlements stay pending the DRG-609 design. |
| [#653](https://github.com/drgaciw/open-crm-cpq/pull/653) | [`story-drg-616-oq6-design-contract-gaps.md`](story-drg-616-oq6-design-contract-gaps.md) | OQ6 gaps stay open. This note adopts no CAM command. |
| [#654](https://github.com/drgaciw/open-crm-cpq/pull/654) | [`story-drg-572-one-time-retroactive-charge-lines-decision-frame.md`](story-drg-572-one-time-retroactive-charge-lines-decision-frame.md) | No one-time or retroactive charge line is designed. |
| [#655](https://github.com/drgaciw/open-crm-cpq/pull/655) | [`cluster-f-fulfilment-decision-frame.md`](cluster-f-fulfilment-decision-frame.md) | No synthetic entitlement history, no retroactive fulfilment row, no commitment-period backfill. |
| [#656](https://github.com/drgaciw/open-crm-cpq/pull/656) | [`cluster-g-visibility-queues-migration-decision-frame.md`](cluster-g-visibility-queues-migration-decision-frame.md) | F-598-4, F-598-5, and F-598-6. History stays outside the phase. `contract_header_backfill_apply` stays an owner step. |
| [#657](https://github.com/drgaciw/open-crm-cpq/pull/657) | [`s3-reconciliation-and-owner-decision-frame.md`](s3-reconciliation-and-owner-decision-frame.md) | S3 stays unauthorized. |
| [#658](https://github.com/drgaciw/open-crm-cpq/pull/658) | [`drg-549-renewal-pricing-resolution-design-frame.md`](drg-549-renewal-pricing-resolution-design-frame.md) | No renewal price is resolved. |
| [#659](https://github.com/drgaciw/open-crm-cpq/pull/659) | [`story-drg-576-product-relationship-and-eligibility-decision-frame.md`](story-drg-576-product-relationship-and-eligibility-decision-frame.md) | No priced relationship mapping. |
| [#660](https://github.com/drgaciw/open-crm-cpq/pull/660) | [`story-drg-575-arr-bridge-per-change-line-decision-frame.md`](story-drg-575-arr-bridge-per-change-line-decision-frame.md) | No ARR bridge figure. Conversion price stays pending the DRG-609 design. |
| [#662](https://github.com/drgaciw/open-crm-cpq/pull/662) | [`documentation-acceptance-2026-10-17.md`](documentation-acceptance-2026-10-17.md) | Documentation acceptance authorizes no data backfill. |

## Unverified

- Whether any environment has called `contract_header_backfill_apply`.
- Multiple NULL `amendment_seq` values under `uq_amendment_document_seq`. Not executed.
- Whether a Java writer rejects `status = APPLIED` when `amendment_seq` is null.
- Whether `subscriptions.quote_id` is the "original quote", and whether its `varchar` value parses as the revision’s `target_quote_id` uuid.
- A sample `continuation_request_json` payload.
- Row counts. No query was run.
- DRG-554’s effect on `subscriptions.contract_id`. The OQ7 note already marks that move `unverified`.
- Linear status of issues other than DRG-566. DRG-566 was Backlog when read this pass.

## What this note does not do

- It does not run a backfill, design a run against real data, or apply anything.
- It does not call, wrap, or change `contract_header_backfill_count`, `contract_header_backfill_apply`, or `contract_header_backfill_compensate`.
- It does not write a migration, a flag, a topic, a gateway route, or code.
- It does not launch S3 or authorize S6.
- It does not change OQ5 option A, the OQ7 thin contract, or the OQ6 record.
- It does not accept ADR-017 or ADR-024 through ADR-027, and it does not reopen the Accepted proration record.
- It does not price a line, infer a price lock, or decide an entitlement or a conversion price. Those prices stay pending the DRG-609 design, which is not accepted.
- It does not select the per-fork defaults in [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) through [#660](https://github.com/drgaciw/open-crm-cpq/pull/660). It cites them.

Accepting this note authorizes no code, migration, or deploy. The ticket stays In Review until the owner merges.
