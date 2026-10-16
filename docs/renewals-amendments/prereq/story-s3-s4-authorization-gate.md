# Owner decision: S3–S8 authorization gate

**Status:** Owner-decision draft. This file authorizes nothing.
**Date:** 2026-10-16.
**Read from:** `main` at `dd0f1a8` (Merge pull request #615).

This document asks the project owner to decide three open questions and whether four stories may start. Merging it records the questions. It does not accept a default, and it does not authorize schema, code, a feature flag, a topic, a gateway route, a deploy, or an implementation pull request.

S3, S4, S5, S6, S7, and S8 stay blocked until the owner authorizes each one in a later decision. ADR-035, accepted 2026-10-16, authorizes the first-slice schema and audit stories only (S1 and S2; RA-45 / RA-46; DRG-579 / DRG-580). `amendment-technical-approach-and-dependencies.md` risk R1 says S3 to S8 require their own readiness and authorization gates.

## Stories this gate covers

| Story | RA | Linear | Written scope | Migration the docs assign |
|---|---|---|---|---|
| S3 | RA-47 | [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) | Amendment document API and state machine. Requirements file §2. | None. V1_7 is S1. |
| S4 | RA-48 | [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) | Document-driven apply after EXECUTED. V1_9 apply ledger. | CAM `V1_9`. |
| S6 | RA-50 | [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) | `QuoteAcceptedEvent` fields and per-tenant legacy-path gating. | None named. |
| S7 | RA-51 | [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) | Contract header backfill. V1_8. | CAM `V1_8`. |

Also blocked, and not decided here: S5 (RA-49 / DRG-583) and S8 (RA-52 / DRG-586). The register says OQ9 blocks S5 until pricing-service modes are verified. S8 depends on S3 through S6.

Linear issue status was read on 2026-10-16 from Linear, which is outside this git tree: DRG-581, DRG-582, DRG-584, and DRG-585 were in Backlog. That status is not a fact stored in the repo.

## OQ5 — Partial application

**Question, as written.** ADR-035 open questions: "Partial application / PARTIALLY_APPLIED (spike recommended yes)." Spike decision 5: should one document apply in several units, with a `PARTIALLY_APPLIED` state? Register row: status `DEFAULT PROPOSED`; blocks S4; owner "Product plus architect"; needed before S4. Acceptance checklist: after ADR-035 acceptance, before S4. `prereq/specs/partial-apply.md` says the choice stays open and that its acceptance criteria apply only if the owner adopts partial application. The OpenAPI draft says OQ5 is undecided (`:apply` is `x-opencrm-slice: later`).

**Options that are written.**

1. **Allow partial application.** Spike recommendation, and the register's proposed default: one unit per subscription, atomic per unit, no automatic rollback, document `PARTIALLY_APPLIED`, remedy by a correcting amendment. Spike §4 rule 5 also allows a rebase (re-observe, re-price, new revision, re-approval when the price changed) or a correcting amendment. `partial-apply.md` writes the same pair, and adds a cut: `PARTIALLY_APPLIED` when some units have committed, `APPLY_BLOCKED` when none have. That cut is marked an owner decision inside the proposal.
2. **All-or-nothing apply of the whole document, with no `PARTIALLY_APPLIED` state.** This is the alternative named in `partial-apply.md` open question 1. Consequences of rejecting partial application are not written beyond "the build stops at open question 1 and these tests are not the specification."

**Proposed default, not adopted here.** The register's default is option 1, remedy by correcting amendment. The spike and `partial-apply.md` keep rebase beside that remedy. This file does not choose between correcting amendment only, and rebase or correcting amendment.

**Consequences if the owner later adopts the register default.**

- S4 acceptance criteria AC-S4-1 through AC-S4-3 and FR-12 become the behavior: a failed unit stays blocked, earlier units stay applied, the document is `PARTIALLY_APPLIED` or `APPLY_BLOCKED`, and a correcting amendment is the remedy. Risk R7 says this can surprise users.
- `partial-apply.md` AC-2 through AC-13 would become the unit rules DRG-582 enforces. That file says so, and it also says those criteria wait on a yes.
- Renewal stub: co-termed multi-asset renewals may half-apply. Price-lock stub: a segment set must apply as one unit. Parking lot PL-9 item 6 leaves "who approves the remedy" unset.
- Two proposals disagree with the resolved single-status decision. OQ2 is resolved: one `status` column, `apply_status` removed (`amendment-entity-field-schema.md` §2.3; OpenAPI `AmendmentStatus` description). `partial-apply.md` still shows `AmendmentDocument.applyStatus` and unit state `FAILED`. The field schema and OpenAPI use unit status `PENDING`, `APPLIED`, `BLOCKED`. Adopting `partial-apply.md`'s contract text as written would put back a column OQ2 removed. The OpenAPI draft keeps `PARTIALLY_APPLIED` and `APPLY_BLOCKED` on the single `status` enum and says there is no `applyStatus` field.
- `partial-apply.md` dependencies still say spike OQ1 and OQ2 stay open. The register and the ADR-035 acceptance on this same `main` record OQ1 accepted and OQ2 resolved. Both texts were read. They conflict. This file does not edit either one.

**What stays blocked.** S4 stays blocked until the owner authorizes DRG-582. Confirming OQ5 is a precondition the register and the readiness file name. Confirming it in a later decision is still separate from authorizing the story.

## OQ6 — Revision model when a LifecycleChange is superseded mid-flight

**Question, as written.** ADR-035: "Revision model edge cases when LifecycleChange is superseded mid-flight." Register: status `DEFAULT PROPOSED`; blocks S3; needed before S3. Field schema §2.4: a new LifecycleChange revision creates a new document revision under the same number; at most one `EXECUTED`. Spike decision 6 is that same sentence. The acceptance checklist calls these "revision edge cases" and places them before S3.

ADR-035 names the mid-flight edge and does not list options or define which states count as mid-flight. No file read for this note contains a menu titled for that edge.

**Behaviors that are written (proposals and defaults, not an accepted model).**

1. **Register / schema / spike default.** New document revision under the same amendment number. Drafts edit. At most one revision closes `EXECUTED` (V11, partial unique index in the schema). Revision `status_at_close` includes `SUPERSEDED`. Spike state table: a new revision of the same LifecycleChange freezes the old revision with its digest. `IN_REVIEW` and `OUT_FOR_SIGNATURE` can return to `DRAFT` on a new revision.
2. **OpenAPI draft, `POST /lifecycle-changes/{id}/revisions`, tagged S5, status proposal.** Allowed from `DRAFT`, `PRICED`, `IN_APPROVAL`, `APPROVED`, and `PRESENTED`. The previous revision is frozen `SUPERSEDED`. Status returns to `DRAFT`. Pricing pins on the new revision are cleared. A new revision of an already `EXECUTED` document is refused with `IMMUTABLE_EXECUTED` (AC-S3-5). The correction path named there is a new lifecycle change.
3. **After apply is blocked.** Spike §4 rule 5 and `partial-apply.md` AC-12: rebase opens a new revision. The OpenAPI `:rebase` operation is tagged later, and its description says it is not part of S3 or S5.
4. **Document-level `SUPERSEDED`.** Spike state table: a later `AMENDED_AND_RESTATED` document executes; applied facts are not rewritten; only links are added. That is a different event from a LifecycleChange revision being superseded.

**Proposed default, not adopted here.** The register default is behavior 1. It does not say what happens when the superseding revision arrives after the document is `EXECUTED` and before apply finishes. The OpenAPI draft refuses a new revision of an `EXECUTED` document. Those two texts can be read together only if "drafts only edit" means pre-execution edits, and an executed document is immutable (A1, AC-S3-5). That reading is an inference. The files do not state it as the mid-flight rule.

**Consequences if the owner later adopts the register default for S3.**

- S3 builds the state machine, gapless `{contract_number}-A{nnn}` at `EXECUTED`, and the "drafts only edit / one EXECUTED revision" rule. AC-S3-5 rejects updates to an `EXECUTED` document and audits them. AC-S3-7: a withdrawn draft consumes no number.
- The mid-flight case ADR-035 names is still unspecified: in-flight `IN_REVIEW` or `OUT_FOR_SIGNATURE` versus already `EXECUTED` but not applied. S3's written acceptance criteria do not include a superseded-mid-flight test.
- Price-lock stub: schedule edits in negotiation create revisions. Renewal stub: renegotiation creates revisions under one renewal number. Both assume the same-number model. Neither states the mid-flight rule.
- Digest library location (OQ18) is `DEFAULT PROPOSED` as `backend/libs/common` and is listed as needed before S3. The OpenAPI draft says the canonicalization location is still open and calls the library an S3 dependency. Technical approach §2 assigns the shared canonical digest utility to S6. Three texts, three timings. Unresolved.

**What stays blocked.** S3 stays blocked until the owner authorizes DRG-581. The register says the revision default is needed before S3. This file does not supply that authorization.

## OQ7 — Backfill strategy

**Question, as written.** ADR-035: "Backfill strategy for pre-spine acceptances." Spike decision 7 lists options. Register: `DEFAULT PROPOSED for slice`; blocks column says "S7 history only"; needed before DRG-566. FR-20 and technical approach §3 describe the slice as header-only. Mid-term spec OQ7 still says "Backfill as legacy evidence (RA-32)." The acceptance checklist says "Backfill (slice: header only)" and "before DRG-566."

**Options that are written.**

1. **Synthesize history.** Spike decision 7: an `ORIGINAL` document per subscription plus `BACKFILLED` documents for past non-NEW acceptances, flagged as legacy evidence. Spike §6.3 (marked `[INFERENCE]`) adds the steps: one contract per existing subscription, or group by customer plus original quote (grouping called an open question); synthesize documents; link rate periods; emit no retroactive events; in-flight `ACCEPTED` but unapplied events take the legacy path. The spike recommended synthesize at the top of that file. The 2026-10-16 addendum says the first slice was later reduced.
2. **Start fresh at cutover.** Named as the other bullet in spike decision 7. No further consequences are written under that bullet.
3. **Slice default now in the register, FR-20, and technical approach §3.** CAM V1_8 creates one contract per existing subscription and sets `subscriptions.contract_id`. Idempotent. No amendment documents. No events. History backfill stays DRG-566. Grouping risk R11: the one-contract-per-subscription assumption may be wrong; later regrouping is DRG-554.

**Proposed default, not adopted here.** Option 3 for S7. Option 1 remains the history story (DRG-566) and is not part of S7. The register's "Blocks" cell says "S7 history only," while FR-20 still cites the OQ7 slice default as the source of S7. S7's dependency line in the requirements file names S1 and the OQ3 default, and does not repeat OQ7. Linear DRG-585 (read outside the repo) lists OQ3 and OQ7 defaults as dependencies. The repo texts disagree on whether OQ7 blocks the header job or only the history job.

**Consequences if the owner later adopts option 3.**

- AC-S7-1: N subscriptions yield N contracts, `contract_id` set, re-run creates no duplicates. AC-S7-2: no `AmendmentDocument` rows and no outbox events. AC-S7-3: a subscription created after cutover gets a header at creation. AC-S7-4: contract numbers unique per tenant and stable.
- `evidence_class` `LEGACY` in the field schema stays a history-backfill concern ("LEGACY only for backfill"). S7, as written, creates no documents, so it does not write `LEGACY` rows.
- The OpenAPI draft includes document kind `BACKFILLED`. That enum value belongs to the history path, not to a header-only V1_8.
- Renewal stub: past `RENEWAL` acceptances become `BACKFILLED` documents (DRG-566). Price-lock stub: existing locks have no record and cannot be inferred from `LEGACY_AGGREGATE` periods. Adopting header-only leaves both of those empty.
- Technical approach §3 says rollback is app-level via the flag, not down-migrations. S7's acceptance criteria do not mention a flag. A rollback story for the V1_8 data write is not written in the S7 section.

**What stays blocked.** S7 stays blocked until the owner authorizes DRG-585. DRG-566 stays out of this slice either way.

## Files and migration numbers each slice would own

Owned only if a later owner decision authorizes that story. Names below are the ones the docs assign, plus paths verified in this tree at `dd0f1a8`. New Java type names are not in those docs. This note does not invent filenames for them.

CAM Flyway history on this tree ends at `V1_6`:

- `backend/services/contract-asset-management/src/main/resources/db/migration/V1_1__create_contract_tables.sql`
- `V1_2__outbox_relay_status_and_leasing.sql`
- `V1_3__create_processed_events_inbox.sql`
- `V1_4__outbox_event_envelope_columns.sql`
- `V1_5__outbox_status_skipped.sql`
- `V1_6__subscription_rate_schedule.sql`

Technical approach §3 and the field-schema addendum assign the next three CAM versions: `V1_7` to S1, `V1_8` to S7, `V1_9` to S4. The `__description` suffix is not specified. quote-service has its own history through `V1_11__processed_events_inbox.sql`, including its own `V1_8__outbox_status_skipped.sql` and `V1_9__quote_ramp_steps.sql`. Those files are not the CAM versions in this gate.

### S3 / DRG-581

- Migration: none assigned.
- Service touch the technical approach names for the amendment API: contract-asset-management, package `contractdocuments` (the package name is in §2; the package does not exist in this tree).
- Resources in technical approach §4: `POST/GET /contracts/{id}/amendments`, `GET /amendments/{id}`, `/revisions`, `/lines`, `/decisions`, `/application-units`, `/events`, and actions `:submit`, `:approve`, `:withdraw`, `:accept`, internal `:execute` and `:apply`. The OpenAPI draft marks `:apply`, `:void`, and `:rebase` as later, not S3.
- The proposal file `docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` already exists (`x-opencrm-status: proposal`). Readiness said OpenAPI would be written first in S3. The draft's own header says it does not authorize implementation. This gate does not move ownership of that file onto S3.
- Class files S3 would add: not named. Unspecified.

### S4 / DRG-582 — CAM V1_9 apply ledger

- New CAM migration `V1_9` (suffix unspecified) creating `document_apply_ledger`. Unique key (`document_id`, `unit_no`). Stored `apply_key_digest`. Same digest on replay is a no-op. A different digest is rejected and audited. Technical approach marks this ledger shape `[I]`.
- `applied_quote_acceptances` stays unchanged for the legacy path (same section). The field schema §2.6 still says that table gains nullable `amendment_document_id` and `application_unit_no`. The later technical-approach text says bypass, not mutation. Both were read. The V1_9 paragraph is the one that names S4's migration.
- Existing class whose internals the orchestrator would reuse: `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/service/AcceptedQuoteSubscriptionService.java`. The docs do not say S4 replaces that class.
- Flag-off fixture named by AC-S4-9: `backend/services/contract-asset-management/src/test/java/com/opencrm/contract/service/AcceptedQuoteSubscriptionServiceTest.java`.
- New orchestrator class name: not specified.

### S6 / DRG-584

- Migration: none named.
- Additive fields on `backend/libs/common/src/main/java/com/opencrm/common/event/dto/QuoteAcceptedEvent.java`: optional `amendmentDocumentId` and per-line `applicationUnitNo` (FR-16, AC-S6-1). The spike writes the same path with a shortened `backend/libs/common/.../QuoteAcceptedEvent.java`.
- Flag routing in `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java` (technical approach §2).
- Shared canonical digest utility in `backend/libs/common`. Class name not given. See the OQ18 timing conflict under OQ6.
- Amendment event names in AC-S6-4: Drafted, Submitted, Approved, Executed, UnitApplied, Applied, ApplyBlocked, Withdrawn. Schema file names are not given. `backend/libs/common/src/main/java/com/opencrm/common/event/EventContractRegistry.java` exists. AC-S6-4 requires each event to have a registered schema and a contract test. Assigning that registry file to S6 is an inference from that criterion plus ADR-035's fitness function ("Event contract registry lists document lifecycle events"). The technical approach says "amendment event schemas" live in `backend/libs/common` and does not name the registry class.
- Fixtures AC-S6-1 names: `backend/services/contract-asset-management/src/test/resources/accepted-v1/*.json`. Files present: `hybrid-credit-new.json`, `r4-accepted-event.json`, `ramp-new.json`.
- Quote emission with `amendmentDocumentId` is assigned to S5 and S6 together (technical approach §2). The transcript names `QuoteAcceptedKafkaEventFactory`. Path verified: `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteAcceptedKafkaEventFactory.java`. Exclusive ownership by S6 is not stated.
- Flag-off regression names in technical approach §8, not exclusive S6 ownership: `QuoteAcceptedSubscriptionKafkaIntegrationTest`, `SubscriptionRatePersistenceIntegrationTest`, `ErpSyncJobTest`. Paths verified under `backend/services/contract-asset-management/src/test/java/com/opencrm/contract/`.

### S7 / DRG-585 — CAM V1_8

- New CAM migration `V1_8` (suffix unspecified): one contract per existing subscription, set `contract_id`, idempotent, no documents, no events (technical approach §3, AC-S7-1, AC-S7-2).
- AC-S7-3 requires the NEW subscription path to create a header at creation. The slice docs do not name that class. A search of CAM `main` Java found `subscriptions.save` in `AcceptedQuoteSubscriptionService.java` and `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/service/SubscriptionRateApplyService.java`. Which of those is the NEW path was not established by reading the methods. Treat the class list as unverified ownership.

## Definition of Done

The DoD used here is the Notion page fetched for this note: [CPQ Definition of Done (DRAFT v0.1, pending owner approval)](https://app.notion.com/p/3fbf7cb4e4df81e29b65fa1ae72a27ca). Fetched 2026-10-16. Notion returned `verification.state: unverified`. The page says it is draft v0.1, pending owner approval, CPQ only, and written without inspecting repo CI. A story is Done only when all nine items are true. Items 2 and 7 are owner gates. This draft DoD is not itself accepted.

| # | Draft DoD item | S3 DRG-581 | S4 DRG-582 | S6 DRG-584 | S7 DRG-585 |
|---|---|---|---|---|---|
| 1 | Acceptance criteria met and noted on the Linear issue | AC-S3-1..8 are written. Meeting them waits on authorization. | AC-S4-1..9 written. AC-S4-3 assumes partial apply. | AC-S6-1..4 written. | AC-S7-1..4 written. |
| 2 | PR to this repo, review, CI green, owner merges | Applies when implementation exists. Owner gate. | Same. | Same. | Same. |
| 3 | Automated tests for new or changed behavior | State machine, V1..V14 failures, numbering. | Apply, digest replay, partial outcome, flag-off fixtures. | Both compatibility directions and a contract test per event. | Idempotent backfill, no documents, no events, header on the NEW path. |
| 4 | Quote/pricing correctness, including edge cases | Amendment rules V1..V14 and dates (V6 is AC-S3-8). DealType mapping is not an S3 criterion in the requirements file. | Quantity, co-term, and end-date cases AC-S4-4 and AC-S4-5. | Event compatibility, not a pricing calculation. | No pricing criterion is written on S7. |
| 5 | Migration reversible or rollback documented; existing data compatible | No migration assigned. Writes use S1 tables. | V1_9. Rollback text in technical approach §3 is the flag, not a down-migration. | No migration assigned. Additive event fields. | V1_8 rewrites subscription linkage. Flag rollback is not in the S7 criteria. See OQ7. |
| 6 | ADRs recorded; Proposed ADRs are not treated as accepted; API or behavior changes noted | API. OpenAPI draft and ADR-024..027 stay Proposed. | Behavior of apply. Same Proposed-ADR rule. | Event contract. Same rule. | Data change noted in docs. Same rule. |
| 7 | No feature flag enabled, nothing deployed, nothing external sent | Applies. Owner gate. | Flag-off path is AC-S4-9. Enabling the flag is outside the story. | OQ11's proposed default is per-tenant, default off. Not confirmed. Enabling it fails this item. | No flag in the S7 criteria. No deploy. |
| 8 | Linear links the PR and follow-ups; Notion hub updated if scope changes | Applies when work exists. | Same. | Same. | Follow-ups already named: DRG-566, DRG-554. |
| 9 | Follow-ups filed; no TODO without a ticket | Applies. | DRG-563, DRG-565, DRG-428 are already the named neighbors. | OQ11 and OQ18 if still open. | DRG-566 holds history. |

**Pending DoD.** The task for this note names five extras as pending DoD: Open Host Service, schema-registry events, outbox writes, commercial-decision audit, and event-replay test. They are not items on the Notion draft that was fetched. A search of `docs/` on this `main` found no "Open Host", "schema-registry", "commercial-decision", or "pending DoD". The phrase "event-replay test" appears once, in `prereq/specs/ramp-amendments.md`, as a DRG-534 acceptance item for ramp schedule fields, and that file is itself a proposal. It is not a Definition of Done entry.

If the owner later adds those extras to the DoD, the repo facts that sit nearest each story are:

- **Open Host Service.** The phrase is absent from the files read. Meaning unverified. The published HTTP surface in the docs is the S3 amendment API.
- **Schema-registry events.** AC-S6-4 is the registration requirement. S3, S4, and S7 do not register schemas in their acceptance criteria.
- **Outbox writes.** FR-19 assigns audit publication to S2. Technical approach control-flow step 7 says the outbox publishes after apply. AC-S7-2 forbids outbox events on the backfill. Whether S4 writes the outbox row itself, or only S2's writer does, is not split in the files.
- **Commercial-decision audit.** FR-14 and the pricing decision trail are S5. DRG-564 is the gap for continuation pricing decisions. None of AC-S3, AC-S4, AC-S6, or AC-S7 is a commercial-decision audit criterion. S3 stores `content_digest`. S4 stores `apply_key_digest`.
- **Event-replay test.** Technical approach §8 and AC-S8-3 say replaying the stream twice yields the same ledger. That test is S8. AC-S4-2 is duplicate-apply idempotency. AC-S6-3 is inbox dedupe of an in-flight legacy event. The ramp spec's event-replay sentence is about DRG-534, not these four stories.

## Drafts that stay Proposed

Read on this `main`:

- `docs/adr/ADR-024-order-orchestration-context.md`, `ADR-025-saga-process-manager.md`, `ADR-026-command-messages.md`, and `ADR-027-compensation-and-point-of-no-return.md` each say **Proposed on 2026-10-04. Not accepted.** Each says it does not authorize code. `docs/adr/README.md` catalogs them as Proposed, documentation only.
- `docs/renewals-amendments/prereq/DRAFT-adr-reconcile-adr-024-027.md` is a separate draft. Its status line says Proposed, assigns no ADR number, and leaves ADR-024 through ADR-027 Proposed. It says partial application (OQ5) remains open.
- `docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` is a draft proposal (`x-opencrm-status: proposal`, title "DRAFT proposal"). Its header says it is not an implementation, a gateway route, or a feature flag, and that it does not authorize merging, deploying, or enabling a runtime flag. Nothing in this tree accepts that contract.

S3 through S8 must not treat those drafts as accepted architecture.

## Unverified

- Notion DoD page verification state was `unverified` at fetch time. The nine items above are the page text that came back.
- Linear Backlog status for DRG-581, DRG-582, DRG-584, and DRG-585 is outside the git tree.
- New class file names for the API, the apply orchestrator, the digest utility, and the event schema types.
- Which CAM class is the NEW subscription path for AC-S7-3.
- Whether S6 edits `EventContractRegistry.java` (inferred, not named).
- Pricing-service amendment modes (OQ9). Technical approach §10 says a code search returned nothing. Not re-run for this note. OQ9 gates S5, which stays blocked.
- The five pending-DoD phrases, except the single "event-replay test" sentence in the ramp proposal.
- Any option for OQ6's mid-flight edge that is not quoted above. Absence from the files read is not proof that no other note exists outside `docs/renewals-amendments/` and `docs/adr/`.

## Later owner record

A later decision, not this file, would record: the OQ5 choice, the OQ6 mid-flight rule, the OQ7 slice-versus-history split, and an explicit authorization for each of DRG-581, DRG-582, DRG-584, and DRG-585. Until that record exists, S3 through S8 stay blocked.
