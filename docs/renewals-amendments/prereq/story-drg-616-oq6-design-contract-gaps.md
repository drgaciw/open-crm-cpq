# DRG-616 — Three OQ6 design-contract gaps

**Status of this note:** Proposed. Not accepted. This note is not an ADR. It does not accept a spec, a schema, an event contract, or a story.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Story:** [DRG-616](https://linear.app/drgamtd-workspace/issue/DRG-616/close-three-oq6-design-contract-gaps-before-s3-starts-revision-write). **Reading base:** `main` at `0d71251d`. The OQ6 record on that commit is the text merged by [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) (`cursor/oq6-mid-flight-revision-fc70`).

Accepting this note authorizes no code, migration, flag, topic, gateway route, or deploy. S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) is not started by this note and still needs a separate owner authorization. S6 is not authorized. OQ18 remains Proposed until the owner confirms it. ADR-017 and ADR-024 through ADR-027 remain Proposed. The DRG-609 design is not accepted. No Accepted ADR is edited.

## Statuses used below

| Item | Status in this note |
|---|---|
| This note, every fork, and the OpenAPI additions | **Proposed.** Each fork has options, a recommendation, and a default. None is accepted. |
| OQ6 | **Adopted only as recorded in #642.** Decided under delegation by CPQ Decision and relayed by Projects Manager. The delegation quotes in that record are "I delegate these decisions to you." (5:10 PM CT) and "Proceed" (5:52 PM CT). Those quotes are the grant. They are not an owner-authored choice of the rule. |
| OQ18 | **Proposed** until the owner confirms it. S3 cannot start building the digest library before that confirmation. |
| ADR-017, ADR-024, ADR-025, ADR-026, ADR-027 | **Proposed.** Not assumed accepted. |
| DRG-609 design | Not accepted. |
| S6 / DRG-584 | Not authorized. |

The adopted OQ6 rule, which this note follows and does not re-decide: before execution, superseding opens a new revision under the same document number; the old revision is frozen `SUPERSEDED`; the new revision is `DRAFT`; pricing pins are cleared; approvals and signatures are void. After `EXECUTED`, no new revision. At most one revision may be `EXECUTED`. `REJECTED` and `WITHDRAWN` stay terminal. The remedy after execution is a new LifecycleChange or a correcting amendment.

Canonical record: `oq6-revision-model-owner-decision.md`. The register row summarizes it. Section 4 of that record leaves the CAM command, the persisted shape of an unpriced revision, the supersession event contract, and CAM-local concurrency open. Draft acceptance criteria elsewhere are proposals. This note does not adopt them.

## What this pass stopped

DRG-616 is labeled docs-only. These description sentences ask for work outside that scope or outside the allowlist (`prereq/DRAFT-openapi-amendments.yaml` and this note). They were not done.

1. **Gap 2 file edits.** The ticket says to update `amendment-entity-field-schema.md` and a V1_7 follow-up migration if needed. A migration is not docs-only. The schema file is outside the allowlist. Neither file was edited. No migration was added. The pin choice is recorded here as a Proposed default, and the OpenAPI draft shows that default on the HTTP shape only.
2. **Gap 3 S6 work.** The ticket says to add the supersession event to S6's owned schemas and contract tests. S6 is not authorized. Schema-registry and contract-test edits are not docs-only. None were added.
3. **Item 4, added on the ticket 2026-10-17.** Reconcile `amendment-first-slice-requirements-and-acceptance.md`, `story-s5-lifecyclechange-readiness.md`, and `story-s3-s4-authorization-gate.md` with the #642 record. Those files are outside the allowlist. They were not edited. AC-S3-9, AC-S3-10, AC-S3-11, AC-S5-7, and AC-S8-6 stay draft criteria in the requirements file. This note does not mark them Proposed inside that file and does not adopt them.
4. **Done-when on the ticket.** "Merged as docs changes on `main`" and "DRG-581's acceptance criteria reference them" are not done here. This note is a draft proposal. It does not merge, and it does not edit DRG-581.

## Gap 1 — CAM revision write

### Problem

S3 needs one CAM command that opens a document revision, and S5 needs to call that command. The draft HTTP file on main exposes the document revisions collection as a read. The write that exists is a quote-service operation. The OQ6 record says CAM owns the state transition and that the quote-service operation is not an accepted CAM API.

### Text on main before this proposal

`prereq/DRAFT-openapi-amendments.yaml` at `0d71251d` defines `GET /amendments/{amendmentId}/revisions` (`listAmendmentRevisions`) and no write on that path. Its description is "At most one revision has statusAtClose EXECUTED."

The same file defines `POST /lifecycle-changes/{lifecycleChangeId}/revisions` (`createLifecycleChangeRevision`), tagged S5. Its description allows LifecycleChange statuses `DRAFT`, `PRICED`, `IN_APPROVAL`, `APPROVED`, and `PRESENTED`, freezes the previous revision `SUPERSEDED`, and refuses an executed document with `IMMUTABLE_EXECUTED`.

`oq6-revision-model-owner-decision.md` section 2, command boundary: "The exact CAM command/API, including route and request/response contract, is not decided here; the OpenAPI draft's LifecycleChange revision operation is tagged S5 and is not an accepted CAM API." Section 4: "The CAM command/API and expected-version contract ... remain open."

`amendment-first-slice-requirements-and-acceptance.md` still states AC-S3-9, AC-S3-10, AC-S3-11, and AC-S5-7 as criteria. Those sentences were not edited. They remain proposals relative to the #642 record.

### Fork — where the write lives

| Option | Meaning |
|---|---|
| A | `POST /amendments/{amendmentId}/revisions` on CAM. S5 calls it. The quote-service POST stays a quote-service revision and does not insert `amendment_document_revisions`. |
| B | Quote-service keeps the only write and inserts the CAM revision row. |
| C | `POST /amendments/{amendmentId}:revise` with the same body as A. |

**Recommendation:** A. **Default:** A. Option B conflicts with the adopted rule that CAM owns the revision transition. Option C is the same behavior with a different URL.

**Owner decision needed:** confirm A. Confirming C instead changes only the path.

### Fork — expected-version field name

| Option | Meaning |
|---|---|
| A | JSON property `version`, the same property `VersionedCommand` uses on every other mutation. Semantics: the document row version the caller read. |
| B | JSON property `expectedVersion`, the ticket's words, as a distinct name. |

**Recommendation:** A. **Default:** A.

**Owner decision needed:** confirm the property name `version`.

### Fork — line payload

| Option | Meaning |
|---|---|
| A | No lines on the request. CAM reads quote-service storage during the command. |
| B | The request carries commercial lines and no pin or price fields. CAM inserts the new revision's lines from that body and stores pins null. |
| C | The command writes a revision header only. Lines arrive later. |

**Recommendation:** B. **Default:** B. CAM should not read quote-service tables to insert its own rows, and the new revision needs lines in the same transaction that freezes the old one.

Each line in that default also carries `applicationUnitNo`. The line column is not null on main. The proposal does not create or retarget application units. What happens to existing `PENDING` units is still open in the mid-flight analysis and is not one of these three gaps.

**Owner decision needed:** confirm that S5 sends line structure, including the existing unit number, on this command.

### Fork — idempotency and replay

| Option | Meaning |
|---|---|
| A | `Idempotency-Key` is required. The same key and the same body return 200 `Idempotent-Replayed: true` and do not open a second revision. The same key with a different body returns 409 `IDEMPOTENCY_CONFLICT`. A later call for a `lifecycleChangeRevision` that already exists returns 200 when the body matches and 409 `IDEMPOTENCY_CONFLICT` when it does not. |
| B | Header only. A new key for an existing `lifecycleChangeRevision` returns 409 even when the body matches. |
| C | Natural key only. This command does not require `Idempotency-Key`. |

**Recommendation:** A. **Default:** A. The draft already requires the header on mutations, and the field schema has a unique pair (`document_id`, `lifecycle_change_revision`).

**Owner decision needed:** confirm replay when the natural key matches and the header is new.

### Fork — not-null revision columns the caller must send

On main, `amendment_document_revisions.target_quote_id` and `inputs_snapshot_ref` are not null (`amendment-entity-field-schema.md` §2.4). This proposal adds no migration.

| Option | Meaning |
|---|---|
| A | The request requires `targetQuoteId` and `inputsSnapshotRef`. The server stores them. Pins inside the snapshot are not copied onto the new lines. |
| B | The server invents a placeholder ref until price. |
| C | Those columns become nullable. That is a migration. It was not written. |

**Recommendation:** A. **Default:** A.

**Owner decision needed:** confirm quote-service supplies both values. `lifecycleChangeRevision` is also required. The first document revision remains `POST /contracts/{contractId}/amendments`. This command is the supersede, so `lifecycleChangeRevision` is at least 2, and a gap in the sequence returns 422 `VALIDATION_REJECTED`.

### Fork — terminal 409 code

The adopted rule uses 409 `IMMUTABLE_EXECUTED` after execution. It says CAM returns 409 for `REJECTED` and `WITHDRAWN` and does not name that code.

| Option | Meaning |
|---|---|
| A | `IMMUTABLE_EXECUTED` for `EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, and for any document that already has an `EXECUTED` revision. `ILLEGAL_TRANSITION` for `REJECTED`, `WITHDRAWN`, `OUT_FOR_SIGNATURE`, `EXPIRED`, `SUPERSEDED`, and `VOID`. |
| B | `IMMUTABLE_EXECUTED` for every refusal, including terminal states. |
| C | A new code `TERMINAL_STATE` for `REJECTED` and `WITHDRAWN`. |

**Recommendation:** A. **Default:** A. Executed immutability stays distinct from a terminal document. Both codes already exist on the draft `ErrorCode` enum.

**Owner decision needed:** confirm `ILLEGAL_TRANSITION` for terminal documents.

Stale `version` is 409 `VERSION_CONFLICT` and writes no revision. If execute commits first, a retry that observes an executed document returns 409 `IMMUTABLE_EXECUTED`. That is the Proposed default for the concurrency sentence left open in the OQ6 record. It does not adopt AC-S3-11.

`OUT_FOR_SIGNATURE` follows the adopted rule: the slice does not void an envelope, and the command returns 409.

### What the draft OpenAPI now proposes

`POST /amendments/{amendmentId}/revisions` (`openAmendmentRevision`) is marked `x-opencrm-status: proposal`. Success is 201: same `provisionalNumber`, document status `DRAFT`, previous revision `statusAtClose` `SUPERSEDED` with its digest kept, new revision `OPEN`, pins cleared, `approvalRef` and `signatureEnvelopeRef` null, `contentDigest` null until a later submit, `amendmentSeq` still null. Replay is 200. The quote-service POST description now says it is not this command.

### What this unblocks for S3

After the owner accepts this proposal and separately authorizes DRG-581, S3 has one CAM write to build and S5 has one command to call. This note does not authorize either build. OQ18 still blocks the digest library. Gap 2's column nullability still blocks inserting an unpriced row on the current not-null pin columns.

## Gap 2 — Unpriced pins

### Problem

The adopted rule clears pricing pins on the new revision and leaves that revision unpriced until it is priced again. The line schema stores the product, pricebook, and configuration pins in columns that are not null and have no validity flag. A superseding revision cannot drop those pins and remain a valid row.

### Text on main before this proposal

`amendment-entity-field-schema.md` §2.5, the pin row (around line 129):

> `product_external_id`, `pricebook_version`, `configuration_hash` | text | not null | pinned at pricing; equal at apply. `configuration_hash` is an INTERIM pin: SHA-256 hex of `product_external_id`, `pricebook_version` and sorted option codes; DRG-428 replaces it, column stays not null | V9 [S]; owner decision B 2026-10-16

§2.4 requires `content_digest` not null on every revision row.

The OpenAPI draft on main lists `productExternalId`, `pricebookVersion`, and `configurationHash` in `AmendmentLine.required`. The descriptions on the first two already say "Null until pricing pins" the value. `configurationHash` is a required SHA-256. `AmendmentRevision.contentDigest` and `AmendmentDocument.contentDigest` are required SHA-256 values. Policy-decision fields (`policyId`, `policyVersion`, `policyDecisionId`, `pricingTraceId`) are already nullable. §2.5 says those are "not null when priced."

AC-S3-9, still an unedited draft criterion, says a superseding revision invalidates product, pricebook, configuration, and policy-decision pins bound to the previous digest.

### Fork — how a cleared pin is stored

| Option | Meaning |
|---|---|
| A | Nullable pins on the open revision only. The frozen previous revision keeps its pins. Submit and apply still require non-null pins (V9). |
| B | An explicit invalidation flag. The three columns stay not null and keep the old values, plus a marker that they are void. |
| C | Staging columns that are nullable. Price promotes them into the not-null columns. |

**Recommendation:** A. **Default:** A. The adopted rule says the pins are cleared, which option B does not do. Option C adds a second set of columns and a promotion step this slice does not need.

**Owner decision needed:** accept or reject nullable pins before any migration. This note does not edit the field schema and does not add a V1_7 migration.

The OpenAPI draft records default A on `AmendmentLine`: the three fields are nullable and are not in `required`. That HTTP shape is Proposed. It is not a column change. Policy-decision fields stay nullable and the proposed command stores them null on the new revision. `policySlot` stays the line's hook. It is not a pricing pin.

### Fork — content digest of the open revision

| Option | Meaning |
|---|---|
| A | `contentDigest` is null on the open unpriced revision until submit. The superseded revision keeps its digest. |
| B | The server computes a digest of the unpriced content immediately. |
| C | The new revision copies the previous digest. |

**Recommendation:** A. **Default:** A. Option C would keep the digest the voided approvals were bound to. Option B needs the canonical digest library. OQ18 is Proposed, and S3 cannot start that library until the owner confirms OQ18. This note does not define canonicalization.

**Owner decision needed:** the field schema on main says `content_digest` is not null. Default A cannot be inserted until the owner accepts a null and a migration, or rejects A. The OpenAPI draft shows null as the Proposed HTTP default on `AmendmentDocument.contentDigest` and `AmendmentRevision.contentDigest`. That is not a migration.

### What this unblocks for S3

After the owner accepts default A (pins and digest) and authorizes the migration separately, S3 can persist the unpriced revision the adopted rule describes. Until that acceptance, S3 must not alter those columns. This note does not authorize that migration.

## Gap 3 — Supersession event

### Problem

The adopted rule writes an audit event when a revision is refused after execution (AC-S3-5 applied to revisions). Draft AC-S3-9 also says the successful supersede writes an event in the CAM transaction. The OQ6 record does not choose a type, payload, schema, topic, or outbox contract, and it does not authorize S6. AC-S6-4's event list has no supersession event.

### Text on main before this proposal

AC-S6-4 in `amendment-first-slice-requirements-and-acceptance.md`:

> Given AmendmentDrafted, Submitted, Approved, Executed, UnitApplied, Applied, ApplyBlocked, Withdrawn events, When emitted, Then each has an event schema registered and a contract test.

The OpenAPI `EventType` enum already contains `AMENDMENT_SUPERSEDED`. Its description says the values are for the document event log and that bus payload names are S6, not the HTTP contract. Spike §6.6 lists `AmendmentSuperseded`. The technical-approach event list that matches AC-S6-4 does not include it.

`oq6-revision-model-owner-decision.md`: "The delegated ruling does not choose a supersession event type, payload/schema, topic, or outbox contract. Event requirements in draft acceptance criteria remain proposals and do not authorize an event contract or S6 work."

### Fork — where the success fact is recorded

| Option | Meaning |
|---|---|
| A | One local document-log row in the same CAM transaction, type name `AMENDMENT_SUPERSEDED`. No bus schema, no schema-registry entry, no outbox publish, no S6 contract test. |
| B | Add the event to the AC-S6-4 list and to S6-owned schemas and contract tests. |
| C | Reuse `AMENDMENT_DRAFTED` for the new revision and write no distinct supersession type. |

**Recommendation:** A. **Default:** A. The name is already on the proposal enum. Option B is S6 work. S6 is not authorized, and that option was stopped. Option C drops the frozen previous revision as its own fact.

**Owner decision needed:** confirm the local type name `AMENDMENT_SUPERSEDED`. A later decision to register a bus schema is a separate S6 authorization. This note does not make it. The OpenAPI `EventType` description now says the name is a Proposed local-log name and is not an S6 contract.

### Fork — refusal audit type name

The adopted rule requires an audit event on `IMMUTABLE_EXECUTED`. It does not name the type. Draft AC-S3-10 also asks for a rejection event. Those draft sentences stay proposals.

| Option | Meaning |
|---|---|
| A | The 409 path appends a local log row in the same transaction. This proposal does not choose the type string and adds no enum value. |
| B | Reuse `VALIDATION_REJECTED`. |
| C | Add a new local name such as `AMENDMENT_REVISION_REJECTED`. |

**Recommendation:** A. **Default:** A. `VALIDATION_REJECTED` is the rule-failure event. A new name would be another unaccepted contract.

**Owner decision needed:** name the refusal type before S3 writes that row. Payload, topic, and outbox stay unchosen for both the success row and the refusal row.

### What this unblocks for S3

After the owner accepts default A, S3 can append one local log row on a successful supersede. The refusal row waits on the type name. Neither default unblocks S6, a schema-registry subject, or an outbox publish. v0.2 items for schema-registry events, outbox writes, and event-replay tests stay N/A until a story that emits a bus event is authorized.

## Owner decisions still required

1. Gap 1 route A, property name `version`, line payload on the request, idempotency option A, caller-supplied `targetQuoteId` and `inputsSnapshotRef`, and `ILLEGAL_TRANSITION` for terminal documents.
2. Gap 2 nullable pins on the open revision, and a null `contentDigest` until submit, including the migration those two require. This note does not contain that migration.
3. Gap 3 local type `AMENDMENT_SUPERSEDED` on success, and a name for the refusal audit row.
4. Separate authorization before S3 / DRG-581 starts. Separate confirmation before anyone builds the OQ18 digest library.
5. Any move of AC-S3-9, AC-S3-10, AC-S3-11, AC-S5-7, or AC-S8-6 from draft criteria to an accepted contract. Item 4 of the ticket, which would edit the requirements file and two story notes, was stopped.

## What this note does not do

- It does not authorize S3, S4, S5, S6, S7, or S8.
- It does not confirm OQ18.
- It does not accept ADR-017 or ADR-024 through ADR-027.
- It does not accept the DRG-609 design.
- It does not edit an Accepted ADR, the field schema, the requirements file, or a migration.
- It does not register an event schema or add a contract test.
- It does not enable a flag, deploy, or send anything external.
