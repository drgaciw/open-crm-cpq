# First slice: requirements and acceptance criteria

**Documentation acceptance (2026-10-17):** The additions and corrections in PRs #653 and #661 are [accepted as documentation](prereq/documentation-acceptance-2026-10-17.md). Individual proposed criteria and unresolved contract decisions retain the statuses stated below; no implementation story is authorized.

*open-crm-cpq, universal renewals-and-amendments. Documentation only. Date: 2026-10-16. Companion files: `renewals-amendments-dev-readiness.md`, `amendment-technical-approach-and-dependencies.md`, `renewals-amendments-open-questions-register.md`.*

**Slice definition.** Mid-term amendment of an ACTIVE SaaS subscription set under one contract, using the spine in CAM (contract-documents module) and a minimal LifecycleChange (intent Amend) in quote-service. Action codes in slice: **Add, Update (quantity, Delta or Absolute), Remove**. Swap, Upgrade and Downgrade are out of slice because the catalog has no product relationships (RA-42 / DRG-576, adjacent assessment). Signature policies in slice: NOT_REQUIRED, NOTICE_ONLY, CLICK_THROUGH (auto-execute). Out of slice: e-signature adapter (DRG-457), on-prem and capacity lines (DRG-556, 557), ordering, fulfilment, renewal, price lock, history backfill.

**Conventions.** [S] sourced from a project file; [I] proposal made here and subject to review. Rule ids V1..V14 and A1..A8 are from `mid-term-amendment-policy-spec.md`. Story ids are new: S1..S8 map to RA-45..RA-52 and DRG-579..DRG-586.

**OQ6 reconciliation (2026-10-17, DRG-616).** The canonical record is `prereq/oq6-revision-model-owner-decision.md` as merged by #642. Its pre-execution revision rule, executed immutability, and terminal-state rule are recorded under delegation. The CAM write API, cleared-pin storage, local concurrency protocol, supersession event contract, and S5/S8 delivery contract remain proposals. AC-S3-9 through AC-S3-11, AC-S5-7, and AC-S8-6 below are explicitly Proposed, not adopted as complete contracts; their behavioral overlap with OQ6 does not adopt their extra details. See `prereq/story-drg-616-oq6-design-contract-gaps.md`. OQ18 remains Proposed and gates digest-library work only. No story is authorized here.

## 1. Numbered requirements

| Req | Statement | Source | Story |
|---|---|---|---|
| FR-01 | CAM has a `contracts` header (thin) and nullable `subscriptions.contract_id`; every subscription in scope resolves to exactly one contract. | ADR-035 D-02; schema 2.2 [S] | S1, S7 |
| FR-02 | CAM stores AmendmentDocument, revisions, lines, application units and events in a contract-documents module with its own schema; no foreign keys to other services. | ADR-035 D-01; schema 2 [S] | S1 |
| FR-03 | `amendment_document_events` is append-only, per-document gapless `seq_no`, hash chained (`prev_hash`, `content_hash`), written in the same transaction as the change; UPDATE and DELETE are rejected. | A1, A2, A6 [S] | S2 |
| FR-04 | A verifier job recomputes the chain and reports gaps or broken links. | A2 [I] | S2 |
| FR-05 | A draft document is created idempotently from a LifecycleChange (unique `lifecycle_change_id`; unique `source_event_id`; headless `request_identifier`). | schema 4, T2 [S] | S3 |
| FR-06 | Document states and guards follow spec section 3; no `apply_status`; a provisional number is assigned at DRAFT and the final `amendment_seq` (last executed + 1, under contract row lock) at EXECUTED, format `{contract_number}-A{nnn}`. | OQ2, OQ4 resolved [S] | S3 |
| FR-07 | Validation rules V1..V14 are enforced at the named transitions and each failure emits an audited rejection event. | spec 4 [S] | S3, S5 |
| FR-08 | Signature policies NOT_REQUIRED, NOTICE_ONLY and CLICK_THROUGH auto-execute after APPROVED; other policies stay unexecutable in this slice (rejected with a clear error). | spec 3; scope cut [I] | S3 |
| FR-09 | Ledger changes are applied only after EXECUTED, per ApplicationUnit, one unit per target subscription, atomic per unit, in dependency order; uniquely keyed by (`document_id`, `unit_no`), with stored `apply_key_digest` checked on replay (same digest is a no-op; different digest is rejected and audited). | V12, V13, OQ2 [S] | S4 |
| FR-10 | One document may apply to several subscriptions of one contract; `applied_quote_acceptances` one-row-per-quote assumption is removed or bypassed for document-driven applies. | DRG-563 [S] | S4 |
| FR-11 | Each applied rate period references its document line (`amendment_document_line_id`) and the revision; amended lines co-term to the existing end date. | A4, V5 [S] | S4 |
| FR-12 | Mixed outcomes: a unit failure marks the unit BLOCKED and the document PARTIALLY_APPLIED or APPLY_BLOCKED; already applied units are not rolled back; a correcting amendment is the remedy. | spike decision 5, OQ5 default [I] | S4 |
| FR-13 | quote-service exposes `lifecycle-changes` (create, revise, submit, withdraw) for intent Amend; the existing continuation endpoint becomes a facade for Amend. | spike 6.6 [S] | S5 |
| FR-14 | Every priced line carries PolicyDecision id, policy id and version and pricing-service trace id; no pricing occurs in CAM or quote-service. | V8, A5 [S] | S5 |
| FR-15 | Product external id, pricebook version and configuration hash are pinned at pricing and re-checked at apply. The configuration hash is an interim non-null pin (SHA-256 of product external id, pricebook version and sorted option codes) until DRG-428 replaces it. | V9, spike 6.2 [S] | S5, S4 |
| FR-16 | `QuoteAcceptedEvent` gains `amendmentDocumentId` and per-line `applicationUnitNo`; CAM routes document-bearing events to the new path and legacy events to the existing path behind a per-tenant feature flag. | spike 6.6, 6.4 [S] | S6 |
| FR-17 | Action date and effective date are stored separately; backdating needs reason code and approval. | V6 [S] | S3, S5 |
| FR-18 | Manual price overrides record actor, reason code, overridden value and route to approval (`CONTRACT_MODIFICATION`). | V14 [I] | S5 |
| FR-19 | Amendment events publish through the outbox to the audit trail (DRG-309) with `amendmentId` and correlation id. | A7 [S] | S2 |
| FR-20 | Contract header backfill creates one contract per existing subscription, with no synthetic amendment history and no retroactive events. | OQ7 default for slice [I] | S7 |

Non-functional: p95 apply latency per unit under 500 ms on a 50-line subscription [I, target to confirm]; no cross-service synchronous call inside the apply transaction (V-rules from pinned data); every migration backward compatible with the running version (expand then contract).

## 2. Acceptance criteria per story

### S1 (RA-45 / DRG-579): CAM schema migration for contract-documents
Depends on: ADR-035 accepted (OQ1); DRG-535; DRG-561.
- **AC-S1-1.** Given a CAM database at V1_6, When migration V1_7 runs, Then tables `contracts`, `amendment_documents`, `amendment_document_revisions`, `amendment_document_lines`, `amendment_application_units`, `amendment_document_events` exist with the columns, nullability and uniqueness rules of `amendment-entity-field-schema.md` sections 2 and 4, and `subscriptions.contract_id` exists as nullable.
- **AC-S1-2.** Given the migration ran, When the previous application version starts, Then it boots and passes its existing tests (expand-only change).
- **AC-S1-3.** Given a second EXECUTED revision is inserted for the same document, When the insert commits, Then the partial unique index rejects it (V11).
- **AC-S1-4.** Given two executed amendments for one contract with the same `amendment_seq`, When inserted, Then the unique (`contract_id`, `amendment_seq`) constraint rejects the second (A3).
- **AC-S1-5.** Given the app DB role, When it issues UPDATE or DELETE on `amendment_document_events`, Then permission is denied and the trigger also rejects it.

### S2 (RA-46 / DRG-580): Hash-chained event writer and verifier
Depends on: S1.
- **AC-S2-1.** Given a document with N events, When a new event is appended, Then `seq_no = N+1`, `prev_hash` equals the previous `content_hash`, and the row is committed in the same transaction as the state change.
- **AC-S2-2.** Given two concurrent appends to one document, When both commit, Then sequence numbers are gapless and unique (one retries).
- **AC-S2-3.** Given a state change that rolls back, When the transaction ends, Then no event row remains.
- **AC-S2-4.** Given a tampered row (hash altered in a test database), When the verifier runs, Then it reports the document id and first broken seq_no; given a deleted middle row, Then it reports a gap.
- **AC-S2-5.** Given an appended event, When the outbox relay runs, Then the audit event carries `amendmentId`, `correlation_id` and a stable `eventId` across at-least-once deliveries. Given a relay crash after broker acknowledgement but before the outbox row is marked published, When the duplicate is delivered, Then consumer inbox deduplication on (`consumer_group`, `eventId`) produces exactly one committed audit-side effect. `source_event_id`, when present, continues to deduplicate the originating input.

### S3 (RA-47 / DRG-581): Amendment document API and state machine
Depends on: S1, S2; DRG-562 design.
- **AC-S3-1.** Given a valid LifecycleChange id, When `POST /contracts/{id}/amendments` is called twice with the same `lifecycle_change_id`, Then one document exists and both calls return it (idempotent).
- **AC-S3-2.** Given a DRAFT document that is priced and valid (V1..V14), When submitted, Then state is IN_REVIEW and a digest is stored; given any rule failing, Then state is unchanged and an audited rejection event names the rule id.
- **AC-S3-3.** Given an APPROVED document with signature policy CLICK_THROUGH, When acceptance is recorded, Then it moves to EXECUTED in one transaction, `amendment_seq = last_executed_seq + 1` and the number is `{contract_number}-A{nnn}`.
- **AC-S3-4.** Given an APPROVED document with signature policy E_SIGN or WET, When execution is requested, Then the API returns 422 `SIGNATURE_NOT_SUPPORTED_IN_SLICE` and state is unchanged.
- **AC-S3-5.** Given EXECUTED, When any attempt updates lines, revisions or the document body, Then the request is rejected and an event is written (A1).
- **AC-S3-6.** Given two drafts for one contract executing concurrently, When both execute, Then sequences are N+1 and N+2 with no gap.
- **AC-S3-7.** Given a DRAFT, When withdrawn, Then state is WITHDRAWN (terminal), no number is consumed, and no gap appears in executed numbering.
- **AC-S3-8.** Given action date later than today and effective date earlier than action date without a reason code, When submitted, Then V6 fails.
- **AC-S3-9 (Proposed, not adopted).** Given a CAM document in DRAFT, IN_REVIEW or APPROVED, When CAM accepts a revision command against its expected document row version, Then a new document revision exists on the same document with the same provisional number, the previous revision is closed SUPERSEDED with its digest kept, the document is DRAFT, approvals and product, pricebook, configuration and policy-decision pins bound to the previous digest are invalidated, the new revision cannot submit or execute until repriced and reapproved as required, and an event is written in the CAM transaction (OQ6).
- **AC-S3-10 (Proposed, not adopted).** Given a CAM document that is EXECUTED, APPLIED, PARTIALLY_APPLIED or APPLY_BLOCKED, When CAM receives a revision command, Then it returns 409 IMMUTABLE_EXECUTED, the document and revision digest are unchanged, and a rejection event is written. REJECTED and WITHDRAWN are also terminal: revision commands return 409 without reopening them or changing their stored digest (OQ6).
- **AC-S3-11 (Proposed, not adopted).** Given an APPROVED CAM document, When revision and execution commands target the same expected document row version concurrently, Then exactly one revision-or-execution state transition commits within CAM and the other returns 409, and an executed document's recorded digest equals the digest of its executed revision. This guarantee is local to CAM; the LifecycleChange integration is tested under S5 and S8 (OQ6).

### S4 (RA-48 / DRG-582): Document-driven apply with multi-subscription units
Depends on: S1..S3; DRG-562, DRG-563, DRG-565; DRG-421 for the pricebook version pin; the interim configuration pin (FR-15) removes the DRG-428 block, and DRG-428 later replaces it.
- **AC-S4-1.** Given an EXECUTED document with units for subscriptions A and B, When apply runs, Then both units write their rate periods and the document is APPLIED.
- **AC-S4-2.** Given the same apply message delivered twice, When processed, Then the second is a no-op (V13); given the same key with a different digest, Then it is rejected and audited.
- **AC-S4-3.** Given unit 1 succeeds and unit 2 fails V3 (`observedRevision` stale), When apply completes, Then unit 1 stays applied, unit 2 is BLOCKED, document is PARTIALLY_APPLIED, no rollback of unit 1, and an event records the cause.
- **AC-S4-4.** Given an Update line reducing quantity from 100 to 80 effective on day d with contract end e, When applied, Then the old rate period is end-dated at d-1, a new period starts on d with quantity 80 and ends at e, and `amendment_document_line_id` is set.
- **AC-S4-5.** Given an Add line, When applied, Then a new typed rate period co-terms to e; given a Remove line, Then the source period is end-dated and no new period is created.
- **AC-S4-6.** Given a unit whose source line is already retired, When applied, Then the unit is blocked (V4).
- **AC-S4-7.** Given the pinned pricebook version differs from the pinned value at apply, When applied, Then the unit is blocked (V9).
- **AC-S4-8.** Given a document that is not EXECUTED, When apply is invoked, Then it is refused.
- **AC-S4-9.** Given a legacy QuoteAccepted AMENDMENT event, When the flag is off for the tenant, Then the existing path behaves exactly as before (existing `AcceptedQuoteSubscriptionServiceTest` fixtures pass unchanged).

### S5 (RA-49 / DRG-583): quote-service LifecycleChange (Amend) and pricing integration
Depends on: S3 contract; pricing-service amendment modes (OQ9 verify); DRG-421 for the pricebook version pin; configuration pin is the interim non-null hash (FR-15), replaced by DRG-428 later.
- **AC-S5-1.** Given an ACTIVE contract, When a seller creates a LifecycleChange with intent Amend and two ChangeLines, Then it is stored with action codes, quantity semantics and the observed revision of each target.
- **AC-S5-2.** Given a ChangeLine with AmendmentPolicy RetainContracted and proration Daily, When priced, Then the line has PolicyDecision id, policy id and version, trace id and pinned product, pricebook and configuration data (V8, V9), where the configuration pin is the interim non-null hash until DRG-428 replaces it; the same option set in any order yields the same pin.
- **AC-S5-3.** Given a Swap, Upgrade or Downgrade line, When submitted, Then rejected with `ACTION_NOT_SUPPORTED_IN_SLICE`.
- **AC-S5-4.** Given a price override, When saved, Then actor, reason and overridden value are stored and an approval request of type CONTRACT_MODIFICATION is created.
- **AC-S5-5.** Given the legacy continuation endpoint is called for an amendment with the flag on, When processed, Then it creates a LifecycleChange and returns the same response shape as before.
- **AC-S5-6.** Given a target subscription not ACTIVE, When created, Then V2 rejects it.

- **AC-S5-7 (Proposed, not adopted).** Given a LifecycleChange revision request, When quote-service invokes the CAM revision contract, Then completion is reported only after a matching CAM revision is confirmed; rejection or an interrupted call cannot leave an operation reported complete with an unmatched revision. S5 must specify durable correlation, retry/idempotency and reconciliation for that service boundary before implementing this flow (OQ6).

### S6 (RA-50 / DRG-584): Event contract and legacy gating
Depends on: S3, S4.
- **AC-S6-1.** Given the new `QuoteAcceptedEvent` schema with optional `amendmentDocumentId` and `applicationUnitNo`, When old consumers read a new event, Then they ignore unknown fields (preserve the pre-extension consumer/parser fixture and feed it the newly extended event). Separately, When the current consumer reads the existing `accepted-v1/*.json` fixtures, Then it accepts the old payloads. Both compatibility directions must pass.
- **AC-S6-2.** Given an event with `amendmentDocumentId`, When CAM consumes it with the flag on, Then it routes to the document path and does not run the legacy apply; given the flag off, Then it follows the legacy path.
- **AC-S6-3.** Given an in-flight ACCEPTED legacy event at cutover, When consumed, Then it takes the legacy path and is deduplicated by the Kafka inbox.
- **AC-S6-4.** Given AmendmentDrafted, Submitted, Approved, Executed, UnitApplied, Applied, ApplyBlocked, Withdrawn events, When emitted, Then each has an event schema registered and a contract test.

### S7 (RA-51 / DRG-585): Contract header backfill (no history)
Depends on: S1; OQ3 default.
- **AC-S7-1.** Given N existing subscriptions, When the backfill runs, Then N contracts exist, each subscription has `contract_id`, and the job can be re-run without duplicates.
- **AC-S7-2.** Given the backfill, When complete, Then no AmendmentDocument rows and no outbox events are created.
- **AC-S7-3.** Given a subscription created after cutover, When created by the NEW path, Then it gets a contract header at creation.
- **AC-S7-4.** Given the contract number scheme, When generated, Then it is unique per tenant and stable.

### S8 (RA-52 / DRG-586): Slice end-to-end acceptance suite and fixtures
Depends on: S3..S6.
- **AC-S8-1.** Given a seeded SaaS contract with two subscriptions, When a two-line Amend flows from LifecycleChange to APPLIED in an integration test (Testcontainers, Kafka), Then rate periods, events, numbering and audit publication match expected fixtures.
- **AC-S8-2.** Given the chain verifier after the end-to-end run, Then it passes; given an injected tamper, Then it fails.
- **AC-S8-3.** Given a replay of the whole event stream, When processed twice, Then the final ledger state is identical.
- **AC-S8-4.** Given the V1..V14 rules, Then each has at least one failing-input test named after the rule id.
- **AC-S8-5.** Given the CI pipeline, Then the suite runs on every PR touching CAM or quote-service amendment code.
- **AC-S8-6 (Proposed, not adopted).** Given the S5 LifecycleChange revision flow, When revision races execution and calls or acknowledgements are interrupted, Then integration tests verify the CAM winner, the executed digest, and eventual confirmed or rejected source-operation state under S5's delivery protocol; no unmatched source revision is reported complete. This is cross-service coverage, beyond S3's CAM-local race test (OQ6).

## 3. Definition of ready and done for each story
Ready: dependencies met, open question defaults accepted (register), contract test fixtures drafted. Done: acceptance criteria automated, migration reviewed for expand-contract, docs updated, no change to legacy path behaviour when flag is off, audit events visible.


## 4. Story ids and links (created 2026-10-16; highest IDs checked first: DRG-578 / RA-44)
| Story | RA | DRG | Notion | Priority |
|---|---|---|---|---|
| S1 schema migration | RA-45 | DRG-579 | https://app.notion.com/p/3fbf7cb4e4df816b80dfdb5973aa0734 | High |
| S2 event chain | RA-46 | DRG-580 | https://app.notion.com/p/3fbf7cb4e4df81cabacbcbee2200b220 | High |
| S3 API and state machine | RA-47 | DRG-581 | https://app.notion.com/p/3fbf7cb4e4df81b1a804e98a90efa55b | High |
| S4 document-driven apply | RA-48 | DRG-582 | https://app.notion.com/p/3fbf7cb4e4df81d49ec3d2402b0d55fd | High |
| S5 quote-service LifecycleChange | RA-49 | DRG-583 | https://app.notion.com/p/3fbf7cb4e4df81739db9c60d4f332f1f | High |
| S6 event contract and flag | RA-50 | DRG-584 | https://app.notion.com/p/3fbf7cb4e4df81faa02de7193e9f4319 | High |
| S7 contract header backfill | RA-51 | DRG-585 | https://app.notion.com/p/3fbf7cb4e4df818d9b9df3e96c226f48 | Medium |
| S8 e2e suite | RA-52 | DRG-586 | https://app.notion.com/p/3fbf7cb4e4df81928cf4c755443fe2e3 | Medium |
Linear URL pattern `https://linear.app/drgamtd-workspace/issue/DRG-###`. Linear blocking relations set: 580<-579; 581<-579,580; 582<-581; 584<-581,582; 585<-579; 586<-582,583,584; 583 related to 581. Comments added (no edits) to existing DRG-535, DRG-561, DRG-562, DRG-563. Nothing renumbered, renamed, moved or deleted.
