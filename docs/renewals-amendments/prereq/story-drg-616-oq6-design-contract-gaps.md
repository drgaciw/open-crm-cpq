# DRG-616 — Three OQ6 design-contract gaps

**Status of this note:** Proposed, unaccepted, owner decision required. This note is not an ADR. It does not accept a spec, a schema, an event contract, or a story. No flag. No deploy.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Story:** [DRG-616](https://linear.app/drgamtd-workspace/issue/DRG-616/close-three-oq6-design-contract-gaps-before-s3-starts-revision-write). **Reading base:** `main` at `0d71251d`. The OQ6 record on that commit is the text merged by [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) (`cursor/oq6-mid-flight-revision-fc70`).

Accepting this note authorizes no code, migration, flag, topic, gateway route, or deploy. S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) is not started by this note and still needs a separate owner authorization. S6 is not authorized. OQ18 remains Proposed until the owner confirms it. ADR-017 and ADR-024 through ADR-027 remain Proposed. The DRG-609 design is not accepted. No Accepted ADR is edited.

## Statuses used below

| Item | Status in this note |
|---|---|
| This note, every fork, and every default below | **Proposed, unaccepted, owner decision required.** Alternatives are named beside each default. |
| OQ6 | **Adopted only as recorded in #642.** Decided under delegation by CPQ Decision and relayed by Projects Manager. The delegation quotes in that record are "I delegate these decisions to you." (5:10 PM CT) and "Proceed" (5:52 PM CT). Those quotes are the grant. They are not an owner-authored choice of the rule. |
| OQ18 | **Proposed, unaccepted, owner decision required.** S3 cannot start building the digest library before the owner confirms it. |
| ADR-017, ADR-024, ADR-025, ADR-026, ADR-027 | **Proposed.** Not assumed accepted. |
| DRG-609 design | Not accepted. |
| S6 / DRG-584 | Not authorized. |

The adopted OQ6 rule, which this note follows and does not re-decide: before execution, superseding opens a new revision under the same document number; the old revision is frozen `SUPERSEDED`; the new revision is `DRAFT`; pricing pins are cleared; approvals and signatures are void. After `EXECUTED`, no new revision. At most one revision may be `EXECUTED`. `REJECTED` and `WITHDRAWN` stay terminal. The remedy after execution is a new LifecycleChange or a correcting amendment.

Canonical record: `oq6-revision-model-owner-decision.md`. The register row summarizes it. Section 4 of that record leaves the CAM command, the persisted shape of an unpriced revision, the supersession event contract, and CAM-local concurrency open. Draft acceptance criteria elsewhere are proposals. This note does not adopt them.

## What this pass does not apply

CPQ Decision on #653. Docs only. No flag. No deploy. Linear status was not changed.

1. **No V1_7 migration.** Nullable pins are described in the PROPOSED addendum appended to `amendment-entity-field-schema.md`. That addendum does not apply the change and does not rewrite the §2.5 row. If a migration is needed, it is a new migration in the S3 build. Its number is the next free at build time. V1_9 is reserved for S4, so this note assigns no number. The rollback statement is in that addendum.
2. **No S6 files.** The ticket's request to add the event to S6-owned schemas and contract tests was not done. S6 is not authorized. No schema, registry entry, or contract test was added. The OpenAPI file is not an input to code generation and defines no contract test.
3. **Requirements and S3/S5 status wording reconciled.** The requirements now label AC-S3-9..11, AC-S5-7 and AC-S8-6 Proposed, not adopted. The S3/S5 notes point to #642 and distinguish recorded OQ6 from unconfirmed OQ18. This reconciliation adopts none of the proposed API, storage, event, or delivery contracts.
4. **Done-when on the ticket is not met.** This note is not merged. Repository acceptance criteria are status-marked; the Linear DRG-581 record was not edited. DRG-616 remains Backlog. DRG-581, DRG-583, and DRG-584 remain Todo.

## Gap 1 — CAM revision write

### Problem

S3 needs one CAM command that opens a document revision, and S5 needs to call that command. The draft HTTP file on main exposes the document revisions collection as a read. The write that exists is a quote-service operation. The OQ6 record says CAM owns the state transition and that the quote-service operation is not an accepted CAM API.

### Text on main before this proposal

`prereq/DRAFT-openapi-amendments.yaml` at `0d71251d` defines `GET /amendments/{amendmentId}/revisions` (`listAmendmentRevisions`) and no write on that path. Its description is "At most one revision has statusAtClose EXECUTED."

The same file defines `POST /lifecycle-changes/{lifecycleChangeId}/revisions` (`createLifecycleChangeRevision`), tagged S5. Its description allows LifecycleChange statuses `DRAFT`, `PRICED`, `IN_APPROVAL`, `APPROVED`, and `PRESENTED`, freezes the previous revision `SUPERSEDED`, and refuses an executed document with `IMMUTABLE_EXECUTED`.

`oq6-revision-model-owner-decision.md` section 2, command boundary: "The exact CAM command/API, including route and request/response contract, is not decided here; the OpenAPI draft's LifecycleChange revision operation is tagged S5 and is not an accepted CAM API." Section 4: "The CAM command/API and expected-version contract ... remain open."

`amendment-first-slice-requirements-and-acceptance.md` still states AC-S3-9, AC-S3-10, AC-S3-11, and AC-S5-7 as criteria. Those sentences are now labeled Proposed, not adopted relative to the #642 record; their behavior was not adopted by this reconciliation.

### Fork — where the write lives

| Option | Meaning |
|---|---|
| A | `POST /amendments/{amendmentId}/revisions` on CAM. S5 calls it. The quote-service POST stays a quote-service revision and does not insert `amendment_document_revisions`. |
| B | Quote-service keeps the only write and inserts the CAM revision row. |
| C | `POST /amendments/{amendmentId}:revise` with the same body as A. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, quote-service inserts the CAM row; C, `POST /amendments/{amendmentId}:revise`. Option B conflicts with the adopted rule that CAM owns the revision transition. Option C is the same behavior with a different URL.

**Owner decision needed:** confirm A. Confirming C instead changes only the path.

### Fork — expected-version field name

| Option | Meaning |
|---|---|
| A | JSON property `version`, the same property `VersionedCommand` uses on every other mutation. Semantics: the document row version the caller read. |
| B | JSON property `expectedVersion`, the ticket's words, as a distinct name. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, JSON property `expectedVersion`.

**Owner decision needed:** confirm the property name `version`.

### Fork — line payload

| Option | Meaning |
|---|---|
| A | No lines on the request. CAM reads quote-service storage during the command. |
| B | The request carries commercial lines and no pin or price fields. CAM inserts the new revision's lines from that body and stores pins null. |
| C | The command writes a revision header only. Lines arrive later. |

**Recommendation:** B. **Default:** B. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** A, no lines and CAM reads quote-service storage; C, revision header only and lines arrive later. CAM should not read quote-service tables to insert its own rows, and the new revision needs lines in the same transaction that freezes the old one.

Each line in default B also carries `applicationUnitNo`. **Default:** the caller sends the existing unit number. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** the server assigns new unit numbers; the field is omitted and unit handling stays unspecified. The line column is not null on main. The proposal does not create or retarget application units. What happens to existing `PENDING` units is still open in the mid-flight analysis and is not one of these three gaps.

**Owner decision needed:** confirm that S5 sends line structure, including the existing unit number, on this command.

### Fork — idempotency and replay

| Option | Meaning |
|---|---|
| A | `Idempotency-Key` is required. The same key and the same body return 200 `Idempotent-Replayed: true` and do not open a second revision. The same key with a different body returns 409 `IDEMPOTENCY_CONFLICT`. A later call for a `lifecycleChangeRevision` that already exists returns 200 when the body matches and 409 `IDEMPOTENCY_CONFLICT` when it does not. |
| B | Header only. A new key for an existing `lifecycleChangeRevision` returns 409 even when the body matches. |
| C | Natural key only. This command does not require `Idempotency-Key`. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, header only; C, natural key only and no `Idempotency-Key`. The draft already requires the header on mutations, and the field schema has a unique pair (`document_id`, `lifecycle_change_revision`).

**Owner decision needed:** confirm replay when the natural key matches and the header is new.

### Fork — not-null revision columns the caller must send

On main, `amendment_document_revisions.target_quote_id` and `inputs_snapshot_ref` are not null (`amendment-entity-field-schema.md` §2.4). This proposal adds no migration.

| Option | Meaning |
|---|---|
| A | The request requires `targetQuoteId` and `inputsSnapshotRef`. The server stores them. Pins inside the snapshot are not copied onto the new lines. |
| B | The server invents a placeholder ref until price. |
| C | Those columns become nullable. That is a migration. It was not written. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, the server writes a placeholder ref; C, those columns become nullable. Option C would be a migration. No migration is in this pull request.

**Owner decision needed:** confirm quote-service supplies both values. `lifecycleChangeRevision` is also required. The first document revision remains `POST /contracts/{contractId}/amendments`. This command is the supersede, so `lifecycleChangeRevision` is at least 2, and a gap in the sequence returns 422 `VALIDATION_REJECTED`.

### Fork — terminal 409 code

The adopted rule uses 409 `IMMUTABLE_EXECUTED` after execution. It says CAM returns 409 for `REJECTED` and `WITHDRAWN` and does not name that code.

| Option | Meaning |
|---|---|
| A | `IMMUTABLE_EXECUTED` for `EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, and for any document that already has an `EXECUTED` revision. `ILLEGAL_TRANSITION` for `REJECTED`, `WITHDRAWN`, `OUT_FOR_SIGNATURE`, `EXPIRED`, `SUPERSEDED`, and `VOID`. |
| B | `IMMUTABLE_EXECUTED` for every refusal, including terminal states. |
| C | A new code `TERMINAL_STATE` for `REJECTED` and `WITHDRAWN`. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, `IMMUTABLE_EXECUTED` for every refusal; C, a new code `TERMINAL_STATE` for `REJECTED` and `WITHDRAWN`. Executed immutability stays distinct from a terminal document. Both codes in default A already exist on the draft `ErrorCode` enum.

**Owner decision needed:** confirm `ILLEGAL_TRANSITION` for terminal documents.

### Fork — stale version when revision races execute

| Option | Meaning |
|---|---|
| A | Stale `version` returns 409 `VERSION_CONFLICT` and writes no revision. If execute commits first, a retry that observes an executed document returns 409 `IMMUTABLE_EXECUTED`. |
| B | Every loser of the race returns only `VERSION_CONFLICT`. |
| C | Every loser returns `IMMUTABLE_EXECUTED`. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, `VERSION_CONFLICT` only; C, `IMMUTABLE_EXECUTED` for every loser. This does not adopt AC-S3-11. That criterion is a proposed AC, not adopted. Its target file is `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md`.

**Owner decision needed:** confirm the two-code race result.

`OUT_FOR_SIGNATURE` follows the adopted rule: the slice does not void an envelope, and the command returns 409.

### Required commercial state on the proposed CAM request

The request carries resolved `actionDate`, `contractEffectiveDate`, and `backdatingReasonCode` (the property is required, and its value may be null to clear it). S5 resolves its optional source patch before calling CAM. CAM replaces those header values in the same revision transaction, validates the revised backdating state, and uses that header for the later digest. Other header fields that the source revision API cannot edit stay unchanged.

Each line also requires `businessCategory`, `applyTargetType`, `chargeType`, `priceAsOf`, and `policySlot`, matching the NOT NULL commercial columns with no SQL defaults. These are line structure and hooks, not the pricing pins that are cleared. Existing action, category, source-line and target constraints still apply. No price is accepted from the caller and no undocumented server defaults are invented. All of this remains Proposed, unaccepted, owner decision required.

### What the draft OpenAPI now proposes

`POST /amendments/{amendmentId}/revisions` (`openAmendmentRevision`) is marked `x-opencrm-status: proposal`. Success is 201: same `provisionalNumber`, document status `DRAFT`, previous revision `statusAtClose` `SUPERSEDED` with its digest kept, new revision `OPEN`, pins cleared, `approvalRef` and `signatureEnvelopeRef` null, `contentDigest` null until a later submit, `amendmentSeq` still null. Replay is 200. The quote-service POST description now says it is not this command.

### Immutable evidence before replacing current header state

**Proposed, unaccepted, owner decision required:** the previous revision must have a complete immutable commercial envelope bound through `inputs_snapshot_ref` before its current dates/reason/totals can be replaced. The envelope records every commercial header field, all totals, lines and pins/trace, the source input-snapshot reference, and every other canonical digest input. The superseded revision must be reconstructable from that envelope alone. Binding may change an OPEN row in the same freeze transaction; frozen references and evidence never change, and archive failure aborts the entire revision. Further OPEN-row writes create new immutable envelopes. The separately accepted durable snapshot-store and canonicalization contracts are still required; a digest plus a pointer of undefined contents is not evidence preservation.

### Materialized source hooks before CAM handoff

The proposed ChangeLine revision response requires the five commercial fields CAM needs, including while unpriced. S5 explicitly materializes first-slice `priceAsOf = EFFECTIVE_DATE`, `policySlot = AMENDMENT`, `applyTargetType = SUBSCRIPTION`, and `chargeType = RECURRING` when absent; supported explicit price-as-of selections are retained. `businessCategory` must be resolved by the separately accepted action/category policy before handoff. An unresolved category refuses the handoff rather than being invented by CAM. These proposal defaults do not accept Cluster E or authorize code. They remove the assumption that a nullable/missing source hook can satisfy the required CAM payload.

### Remaining CAM priced-state synchronization gap

No CAM write or delivery event in the draft persists the repriced totals, pins and trace after supersession. Quote-service's LifecycleChange `:price` only changes quote-service state; the revision-opening command accepts no prices or pins. Desired atomic replacement is not an existing contract. S3/S5 implementation stays blocked until a separately accepted command/event contract specifies the expected CAM document version and revision, complete priced payload, atomic write, idempotency/replay, stale-result refusal and confirmed delivery. The choice of command versus event remains open; this note adds no route or event and does not claim the flow complete.

### What this unblocks for S3

Only after that missing delivery contract is accepted, the owner accepts this proposal and separately authorizes DRG-581, S3 has one CAM write to build and S5 has one command to call. This note does not authorize either build. OQ18 still blocks the digest library. Gap 2's column nullability still blocks inserting an unpriced row on the current not-null pin columns.

## Gap 2 — Unpriced pins

### Problem

The adopted rule clears pricing pins on the new revision and leaves that revision unpriced until it is priced again. The line schema stores the product, pricebook, and configuration pins in columns that are not null and have no validity flag. A superseding revision cannot drop those pins and remain a valid row.

### Text on main before this proposal

`amendment-entity-field-schema.md` §2.5, the pin row (around line 129):

> `product_external_id`, `pricebook_version`, `configuration_hash` | text | not null | pinned at pricing; equal at apply. `configuration_hash` is an INTERIM pin: SHA-256 hex of `product_external_id`, `pricebook_version` and sorted option codes; DRG-428 replaces it, column stays not null | V9 [S]; owner decision B 2026-10-16

§2.4 requires `content_digest` not null on every revision row.

The OpenAPI draft on main lists `productExternalId`, `pricebookVersion`, and `configurationHash` in `AmendmentLine.required`. The descriptions on the first two already say "Null until pricing pins" the value. `configurationHash` is a required SHA-256. `AmendmentRevision.contentDigest` and `AmendmentDocument.contentDigest` are required SHA-256 values. Policy-decision fields (`policyId`, `policyVersion`, `policyDecisionId`, `pricingTraceId`) are already nullable. §2.5 says those are "not null when priced."

AC-S3-9, explicitly marked Proposed in the requirements, says a superseding revision invalidates product, pricebook, configuration, and policy-decision pins bound to the previous digest.

### Fork — how a cleared pin is stored

| Option | Meaning |
|---|---|
| A | Nullable pins while unpriced; frozen revisions preserve their original pin values. The frozen previous revision preserves its actual pins, including nulls if it was never priced. Submit and apply still require non-null pins (V9). |
| B | An explicit invalidation flag. The three columns stay not null and keep the old values, plus a marker that they are void. |
| C | Staging columns that are nullable. Price promotes them into the not-null columns. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, an invalidation flag while the columns stay not null; C, nullable staging columns that price promotes. The adopted rule says the pins are cleared, which option B does not do. Option C adds a second set of columns and a promotion step this slice does not need.

**Owner decision needed:** accept or reject nullable pins before any migration. The PROPOSED addendum on `amendment-entity-field-schema.md` describes default A and does not apply it. The §2.5 row is unchanged. There is no V1_7 migration. If the owner later accepts default A, the S3 build adds a new migration whose number is the next free at build time. V1_9 is reserved for S4. This note assigns no number.

The OpenAPI draft records default A on `AmendmentLine`: the three fields are nullable and are not in `required`. That HTTP shape is Proposed, unaccepted, owner decision required. It is not a column change and it is not code generation. Policy-decision fields stay nullable and the proposed command stores them null on the new revision. `policySlot` stays the line's hook. It is not a pricing pin.

### Fork — content digest of the open revision

| Option | Meaning |
|---|---|
| A | `contentDigest` is null on the open unpriced revision until submit. The superseded revision retains its digest; freezing a never-submitted draft still needs a digest-at-close contract. |
| B | The server computes a digest of the unpriced content immediately. |
| C | The new revision copies the previous digest. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, compute a digest of the unpriced content immediately; C, copy the previous digest. Option C would keep the digest the voided approvals were bound to. Option B needs the canonical digest library. OQ18 stays Proposed, unaccepted, owner decision required, and S3 cannot start that library until the owner confirms OQ18. This note does not define canonicalization.

**Owner decision needed:** the field schema on main says `content_digest` is not null. Default A cannot be inserted until the owner accepts a null and a later migration, or rejects A. That migration, if any, is a new one in the S3 build, numbered the next free at build time. It is not V1_7. V1_9 stays reserved for S4. The OpenAPI draft shows null as the HTTP shape on `AmendmentDocument.contentDigest` and `AmendmentRevision.contentDigest`. That shape is Proposed, unaccepted, owner decision required. It is not a migration.

**Remaining storage gap.** A revision can be superseded while still unpriced DRAFT. Frozen null pins must remain historical facts rather than being filled from the new revision. Freezing a never-submitted draft also requires an accepted digest-at-close contract, since the proposed open digest is null. This proposal does not choose that contract, and the API is not implementation-ready until it is resolved.

### What this unblocks for S3

After the owner resolves that storage gap, accepts default A for pins and for the digest, and separately authorizes DRG-581, S3 can persist the unpriced revision the adopted rule describes. The column change, if accepted, is a new migration in that build. Its number is the next free at build time. This note does not authorize that build or that migration.

### Unpriced current totals

**Proposed default, unaccepted, owner decision required:** set current-header `tcvDelta`, `arrDelta`, `oneTimeTotal`, and `creditTotal` to null when opening an unpriced revision. Preserve the previous frozen revision's evidence. A successful later pricing operation atomically replaces every total with that revision's computed value, pins and trace. Submit and execute require current non-null totals. The OpenAPI response exposes the four fields as required but nullable. The schema addendum records the matching future nullability change; no migration is implemented here.

**Alternative:** an explicit validity state, under its own accepted contract, that prevents stale totals from being used as current. Copying old totals or filling zeroes without validity is not a representation of an unpriced revision. The existing V1_7 NOT NULL columns cannot implement the null default unchanged. This is another S3 design prerequisite, not implementation authorization.

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

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, add the event to the AC-S6-4 list and to S6 schemas and contract tests; C, reuse `AMENDMENT_DRAFTED` and write no distinct supersession type. The name is already on the proposal enum. Option B is S6 work. No S6 file is added. Option C drops the frozen previous revision as its own fact.

**Owner decision needed:** confirm the local type name `AMENDMENT_SUPERSEDED`. A later decision to register a bus schema is a separate S6 authorization. This note does not make it. The OpenAPI `EventType` description says the name is a Proposed local-log name and is not an S6 contract. The file defines no contract test.

### Fork — refusal audit type name

The adopted rule requires an audit event on `IMMUTABLE_EXECUTED`. It does not name the type. Draft AC-S3-10 also asks for a rejection event. Those draft sentences stay proposals.

| Option | Meaning |
|---|---|
| A | The 409 path appends a local log row in the same transaction. This proposal does not choose the type string and adds no enum value. |
| B | Reuse `VALIDATION_REJECTED`. |
| C | Add a new local name such as `AMENDMENT_REVISION_REJECTED`. |

**Recommendation:** A. **Default:** A. **Status:** Proposed, unaccepted, owner decision required. **Alternatives:** B, reuse `VALIDATION_REJECTED`; C, add a local name such as `AMENDMENT_REVISION_REJECTED`. `VALIDATION_REJECTED` is the rule-failure event. A new name would be another unaccepted contract.

**Owner decision needed:** name the refusal type before S3 writes that row. Payload, topic, and outbox stay unchosen for both the success row and the refusal row.

### What this unblocks for S3

After the owner accepts default A, S3 can append one local log row on a successful supersede. The refusal row waits on the type name. Neither default unblocks S6, a schema-registry subject, or an outbox publish. v0.2 items for schema-registry events, outbox writes, and event-replay tests stay N/A until a story that emits a bus event is authorized.

## Proposed ACs, not adopted

These criteria are proposed ACs, not adopted. The requirements now mark each one Proposed, and the S3/S5 notes reference the canonical #642 record and unconfirmed OQ18. This changes status wording, not the proposed behavior.

| ID | Status | Target file |
|---|---|---|
| AC-S3-9 | proposed ACs, not adopted | `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` |
| AC-S3-10 | proposed ACs, not adopted | `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` |
| AC-S3-11 | proposed ACs, not adopted | `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` |
| AC-S5-7 | proposed ACs, not adopted | `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` |
| AC-S8-6 | proposed ACs, not adopted | `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` |

## Owner decisions still required

| # | Decision | Default | Status | Alternatives |
|---|---|---|---|---|
| 1 | Where the CAM write lives | A. `POST /amendments/{amendmentId}/revisions` | Proposed, unaccepted, owner decision required | B. Quote-service inserts the CAM row. C. `POST /amendments/{amendmentId}:revise`. |
| 2 | Expected-version property | A. `version` | Proposed, unaccepted, owner decision required | B. `expectedVersion`. |
| 3 | Line payload | B. Commercial lines, no pins | Proposed, unaccepted, owner decision required | A. No lines; CAM reads quote-service. C. Header only; lines arrive later. |
| 4 | Unit number on each new line | Caller sends the existing `applicationUnitNo` | Proposed, unaccepted, owner decision required | Server assigns new unit numbers. Field omitted. |
| 5 | Idempotency and replay | A. Header plus natural key | Proposed, unaccepted, owner decision required | B. Header only. C. Natural key only. |
| 6 | `targetQuoteId` and `inputsSnapshotRef` | A. Caller supplies both | Proposed, unaccepted, owner decision required | B. Server placeholder. C. Nullable columns. No migration is written. |
| 7 | Terminal 409 code | A. `ILLEGAL_TRANSITION` for terminal documents; `IMMUTABLE_EXECUTED` after execution | Proposed, unaccepted, owner decision required | B. `IMMUTABLE_EXECUTED` for every refusal. C. New code `TERMINAL_STATE`. |
| 8 | Stale version when revision races execute | A. `VERSION_CONFLICT`, then `IMMUTABLE_EXECUTED` if a retry sees an executed document | Proposed, unaccepted, owner decision required | B. `VERSION_CONFLICT` only. C. `IMMUTABLE_EXECUTED` for every loser. |
| 9 | Cleared pins | A. Nullable while unpriced; frozen values preserved | Proposed, unaccepted, owner decision required | B. Invalidation flag; columns stay not null. C. Staging columns. |
| 10 | Digest of the open revision | A. Null until submit | Proposed, unaccepted, owner decision required | B. Compute immediately. That needs OQ18, which stays Proposed. C. Copy the previous digest. |
| 11 | Success supersession fact | A. Local log row `AMENDMENT_SUPERSEDED` | Proposed, unaccepted, owner decision required | B. S6 schema and contract test. No S6 file is added. C. Reuse `AMENDMENT_DRAFTED`. |
| 12 | Refusal audit type | A. Local row; type name not chosen | Proposed, unaccepted, owner decision required | B. `VALIDATION_REJECTED`. C. New name `AMENDMENT_REVISION_REJECTED`. |
| 13 | Pin migration, only if default 9 is later accepted | New migration in the S3 build. Number is the next free at build time. Rollback is in the schema addendum. | Proposed, unaccepted, owner decision required | No migration. Reuse V1_7. That alternative is rejected: V1_7 is the existing expand. Reuse V1_9. That alternative is rejected: V1_9 is reserved for S4. This note assigns no number. |
| 14 | OQ18 digest library | Unconfirmed | Proposed, unaccepted, owner decision required | Confirm `backend/libs/common`, built in S3. Place the library somewhere else. |
| 15 | S3 / DRG-581 | Not authorized by this note | Separate owner act | Authorize DRG-581. Leave it unstarted. |
| 16 | Unpriced current totals | Null until successful pricing atomically replaces all four totals; requires a later accepted migration | Proposed, unaccepted, owner decision required | Explicit totals-validity state under a separate accepted contract. No placeholder zeroes or stale prior totals. |
| 17 | CAM priced-state synchronization | Not defined; S3/S5 implementation blocked until a complete revision/version-bound payload and replay/stale-result contract is accepted | Unresolved, owner decision required | A separately accepted CAM command or delivery event. Quote-service :price alone is not a CAM write. |

## What this note does not do

- It does not authorize S3, S4, S5, S6, S7, or S8.
- It does not confirm OQ18. OQ18 stays Proposed, unaccepted, owner decision required.
- It does not accept ADR-017 or ADR-024 through ADR-027.
- It does not accept the DRG-609 design.
- It does not edit an Accepted ADR.
- It appends a PROPOSED addendum to the field schema. That addendum describes nullable pins while unpriced, with frozen values preserved. It does not apply them and it does not rewrite earlier text.
- It does not add a V1_7 migration. It assigns no migration number. V1_9 stays reserved for S4.
- It reconciles proposal status in the requirements and S3/S5 notes; it adopts no additional acceptance contract.
- It does not add an S6 file, a schema-registry entry, generated code, or a contract test.
- It does not enable a flag, deploy, or send anything external.
- It does not change Linear status.
