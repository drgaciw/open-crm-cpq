# S3 reconciliation and owner-decision frame

**Status:** Proposed, unaccepted, owner decision required.

**Standing conditions.** These three sentences govern this note:

1. S3 stays UNAUTHORIZED.
2. A merge of #657 is NOT S3 authorization.
3. Recommending default (a) is NOT a migration approval.

The gate-doc correction in section 6 stays a proposed edit. It is not applied.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Story:** S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) stays UNAUTHORIZED and stays unlaunched. This note does not authorize S3. A merge of #657 is NOT S3 authorization.

**Reading base:** `main` at `0d71251d`. This note follows open draft [PR #653](https://github.com/drgaciw/open-crm-cpq/pull/653) (DRG-616). That pull request is open, targets `main`, and touches `docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml`. This note does not edit that file, the OQ6 record, `story-s3-s4-authorization-gate.md`, or any ADR.

**What accepting this note does.** It records questions and proposed defaults for a later owner decision. Accepting it authorizes no code, schema, migration, flag, topic, gateway route, deploy, or story. S3 must wait on #653 or rebase over it. DRG-616 must be merged first. Every default below is **Proposed, unaccepted, owner decision required**, with the alternatives named beside it. No outcome is recorded.

OQ6's status line, quoted from merged [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) (`docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md`):

> **Status:** OQ6 was decided under delegation by CPQ Decision and relayed by Projects Manager; it is not the owner's personal choice. OQ18 remains **Proposed until the owner confirms it**. OQ6 is recorded for the first slice, but this record authorizes no code, schema, migration, flag, topic, gateway route, deploy, or story. S3 / DRG-581 requires separate owner authorization. OQ18 confirmation is required before building the proposed digest library, not authorization to start S3.

## 1. The migration question

The authorization gate, `docs/renewals-amendments/prereq/story-s3-s4-authorization-gate.md`, assigns S3 this migration cell: "None. V1_7 is S1." That sentence holds only if S3 never stores an unpriced or digest-less revision.

On this `main`, `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql` (read) defines:

- `amendment_documents.content_digest varchar(64) NOT NULL`, and `chk_amendment_document_digests` requires `content_digest ~ '^[0-9a-f]{64}$'`.
- `amendment_document_revisions.content_digest varchar(64) NOT NULL`, `inputs_snapshot_ref varchar(1024) NOT NULL`, and `target_quote_id uuid NOT NULL`. `chk_amendment_revision_digest` requires `content_digest ~ '^[0-9a-f]{64}$'`.
- `amendment_document_lines.product_external_id varchar(255) NOT NULL`, `pricebook_version varchar(64) NOT NULL`, and `configuration_hash varchar(64) NOT NULL`. `chk_amendment_line_configuration_hash` requires `configuration_hash ~ '^[0-9a-f]{64}$'`.

The same check constraint lists document status `VOID`. The document-status check also lists `OUT_FOR_SIGNATURE`. Those two tokens are schema values. Section 3 leaves their S3 treatment open.

CAM migration files present in that directory on this `main` (names read): `V1_7__contract_documents.sql`, `V1_7_1__durable_renewal_eligibility_publications.sql`, `V1_7_2__freeze_amendment_line_membership.sql`, `V1_8__contract_header_backfill.sql`. The V1_8 file header says it is DRG-585 / RA-51 (S7) and that V1_9 is reserved for `document_apply_ledger` (S4 / DRG-582). No `V1_9` file is in that directory. No `V1_10` file is in that directory.

OQ6 section 2, quoted exactly, leaves the stored shape of a cleared-pin revision undecided:

> 1. **Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`).** A new revision of the LifecycleChange opens a new document revision on the same document, with the same `provisionalNumber` (OQ4). The previous revision closes with `status_at_close = SUPERSEDED` and keeps its content digest. The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again. The persisted representation of an unpriced revision is not decided here.

Draft PR #653 (body read; the branch was not checked out) proposes nullable pins on the open revision only, and `contentDigest` null until submit. Its body says that proposal is not a migration, assigns no migration number, and keeps V1_9 reserved for S4. If a pin migration is later accepted, #653 says it is a new migration in the S3 build, numbered the next free at build time, with the rollback statement in the schema addendum.

### Options

| Option | What it would mean | Status |
|---|---|---|
| (a) | Nullable product/pricebook/configuration pins, both document and revision `content_digest` columns, and all four current document totals (`tcv_delta`, `arr_delta`, `one_time_total`, `credit_total`), plus a new CAM migration at `V1_10` or higher. The number is the next free version at build time. The migration carries a rollback statement that stops while any affected row remains null and never fabricates historical values. `V1_9` stays reserved for the S4 apply ledger. `V1_7_3` is forbidden: it sorts below `V1_8` (Flyway versions `1.7.3` < `1.8`), and `spring.flyway` in `backend/services/contract-asset-management/src/main/resources/application.yml` (read) does not set `out-of-order`. Flyway's default rejects an out-of-order version. A live Flyway run was not executed for this note. | Proposed, unaccepted, owner decision required |
| (b) | A sentinel digest with no defined meaning. Flag: **UNDEFINED**. No accepted document defines that sentinel. | Proposed, unaccepted, owner decision required |
| (c) | Require priced revisions only. This conflicts with adopted OQ6, which immediately opens an unpriced DRAFT with pins cleared. It is not a compatible no-migration implementation: choosing it would require an explicit amendment of OQ6 and a redesigned supersession flow, neither authorized here. | Proposed, unaccepted, owner decision required |

**Recommended default:** option (a). **Proposed, unaccepted, owner decision required.** Recommending default (a) is NOT a migration approval.

**Alternatives beside that default:** option (b), flagged UNDEFINED; option (c), priced revisions only.

No outcome. The owner chooses. This note does not add a migration. Recommending default (a) is NOT a migration approval.

**Complete nullability scope.** The proposed unpriced representation also clears `amendment_documents.tcv_delta`, `arr_delta`, `one_time_total`, and `credit_total`, all NOT NULL on the reading base. Option (a) includes those four columns, the two digest columns, and the three pricing-pin columns together. A pin-only migration cannot implement that representation. No migration is approved or written here.

**Remaining priced-state delivery gap.** The draft OpenAPI has no CAM synchronization write/event that restores the repriced totals, pins, and trace. Quote-service's `:price` does not perform a CAM write. S3 and S5 remain blocked until an accepted contract specifies the revision/version binding, full payload, atomic CAM update, replay and stale-result refusal. This frame records the missing contract, not a complete supersession/reprice flow.

## 2. OQ18, or a CAM-local interim digest

OQ18 is only Proposed. The register row (`docs/renewals-amendments/renewals-amendments-open-questions-register.md`, read) says: unconfirmed proposal, canonical digest library in `backend/libs/common`, built once in S3; S5 and S6 may reuse it; not adopted; S3 cannot start building the library before owner confirmation.

Three documents disagree on timing:

| Document | What it says about the digest library | Read |
|---|---|---|
| Open-questions register, OQ18 | Built once in S3, in `backend/libs/common`. | Yes |
| `amendment-technical-approach-and-dependencies.md` §2 | `backend/libs/common` change "shared canonical digest utility" is assigned to **S6**. | Yes |
| `prereq/DRAFT-openapi-amendments.yaml` (this `main`, lines 51–54) | "Canonical JSON for `contentDigest` is the shared digest library (S3 dependency, location still open). This proposal does not define the canonicalization algorithm." | That paragraph only. The rest of the file was not re-read. |

**Recommended default:** a CAM-local interim digest function inside `backend/services/contract-asset-management`, used only if the owner approves it. It does not land in `backend/libs/common`. **Proposed, unaccepted, owner decision required.**

**Alternative beside that default:** wait for owner confirmation of OQ18. Until that confirmation, S3 builds no digest function, and it does not build the `backend/libs/common` library.

The canonical form is not defined by an accepted document. The OpenAPI sentence quoted above says the proposal does not define the algorithm. This note does not define it. A sentinel value remains UNDEFINED (option (b) above).

## 3. VOID and OUT_FOR_SIGNATURE — OPEN ITEM

`AmendmentDocumentStatus` (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/AmendmentDocumentStatus.java`, read) has eleven values: `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID`.

OQ6 section 2, quoted exactly, names the first-slice states and defers `OUT_FOR_SIGNATURE`:

> Document states in the first slice: `DRAFT`, `IN_REVIEW`, `APPROVED`, `EXECUTED`, then the apply outcomes `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, and the terminal states `REJECTED` and `WITHDRAWN`. `OUT_FOR_SIGNATURE` cannot be reached in the slice, because E_SIGN and WET execution return 422 `SIGNATURE_NOT_SUPPORTED_IN_SLICE` (AC-S3-4).

> 5. **`OUT_FOR_SIGNATURE`, for the later signature story.** A new revision must void the open signature envelope before rule 1 applies. Nothing in the slice builds it.

AC-S3-4 in `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` (read) returns 422 `SIGNATURE_NOT_SUPPORTED_IN_SLICE` and leaves state unchanged when an `APPROVED` document with signature policy `E_SIGN` or `WET` is asked to execute.

The OQ6 record does not say how `VOID` is treated. `mid-term-amendment-policy-spec.md` §3 (read) has a row "EXECUTED (unapplied) → VOID" labelled "Authorised legal void (terminal)". This note does not adopt that row.

**OPEN ITEM. Not decided.** No default treatment of `VOID`. No default that builds `OUT_FOR_SIGNATURE` inside S3. **Proposed, unaccepted, owner decision required.**

**Alternatives beside that non-decision:** adopt the policy-spec void row; build the signature envelope inside S3. Neither alternative is accepted here.

## 4. S3 owner-decision table

The OQ6 record says "S3 / DRG-581 requires separate owner authorization." The register's OQ6 cell says "S3 / DRG-581 needs separate owner authorization." `prereq/mid-flight-revision-owner-decision.md` says "S3 / DRG-581 requires separate owner authorization." The Linear ticket body was not opened this pass. **Unverified** for the ticket text.

OQ6 section 4's two S3 tests, quoted exactly. They are criteria only. The record says they do not accept a spec or authorize implementation:

> These two S3 tests express the OQ6 ruling. They are criteria only; this record does not accept a spec or authorize implementation.
>
> 1. **Supersede before execution.** Given a document in DRAFT, IN_REVIEW, or APPROVED, when its LifecycleChange is superseded, then a new revision exists under the same document number, the old revision is frozen SUPERSEDED, the new revision is DRAFT, pricing pins are cleared, and approvals and signatures are void. REJECTED and WITHDRAWN are terminal and are not eligible for supersession.
> 2. **No revision after EXECUTED, and at most one EXECUTED.** Given a revision that has closed EXECUTED, when a further revision is requested, then no new revision is stored and the result is IMMUTABLE_EXECUTED. A second EXECUTED revision of that document is rejected. REJECTED and WITHDRAWN remain terminal.

| # | Question | Recommended default | Status | Alternatives |
|---|---|---|---|---|
| 1 | Unpriced storage | Option (a): nullable pins, both digest columns, and all four current totals, plus a new CAM migration, `V1_10` or higher, next free at build time, with a rollback statement. `V1_9` stays reserved for S4. Never `V1_7_3`. Recommending default (a) is NOT a migration approval. | Proposed, unaccepted, owner decision required | (b) UNDEFINED sentinel digest. (c) Priced revisions only: conflicts with adopted OQ6 and requires its explicit amendment plus redesign; not a compatible no-migration option. |
| 2 | OQ18 or interim digest | CAM-local interim digest function inside contract-asset-management, only if the owner approves it. | Proposed, unaccepted, owner decision required | Wait for owner confirmation of OQ18. Do not build the `backend/libs/common` library in S3 before that confirmation. |
| 3 | S3 authorization | S3 stays UNAUTHORIZED. S3 / DRG-581 stays unlaunched. A merge of #657 is NOT S3 authorization. Separate owner authorization is still required. | Proposed, unaccepted, owner decision required | A later owner record that explicitly authorizes DRG-581. |
| 4 | Order versus #653 | S3 waits on draft PR #653 or rebases over it. DRG-616 must be merged first. | Proposed, unaccepted, owner decision required | Start an S3 slice on current `main` and reconcile the OpenAPI draft afterwards. |
| 5 | `VOID` and `OUT_FOR_SIGNATURE` | No default. OPEN ITEM. Not decided. | Proposed, unaccepted, owner decision required | Adopt the policy-spec void row. Build `OUT_FOR_SIGNATURE` in S3. |

No row above is an outcome.

## 5. Proposed allowlist for a later S3 code slice

**Label: draft, not approved.** **Proposed, unaccepted, owner decision required.**

This list is a boundary a later slice would stay inside if the owner authorizes S3 after the decisions in section 4. It is not permission to write the files.

All paths are under `backend/services/contract-asset-management/`.

New classes in package `com.opencrm.contract.documents`:

- `AmendmentStateMachine`
- `AmendmentDocumentService`
- `AmendmentRevisionService`
- `AmendmentValidationRules` for V1 through V14. The identifiers are cited in `docs/renewals-amendments/amendment-entity-field-schema.md` (read). The pass/fail sentences are in `docs/renewals-amendments/mid-term-amendment-policy-spec.md` §4 (read). The field schema does not restate every sentence.
- `AmendmentController`, plus DTOs (DTO type names are not assigned here)
- repositories (repository type names are not assigned here)
- an optional CAM-local digest helper, only if the owner approves the section 2 default

Additive edits to existing entities. Those entities already live in `com.opencrm.contract.domain.documents` (directory listing read), including `AmendmentDocument`, `AmendmentDocumentRevision`, `AmendmentDocumentLine`, and `AmendmentDocumentStatus`.

Optional migration `V1_10` or higher only if option (a) is accepted, covering the three pricing-pin columns, both `content_digest` columns, and the four current document total columns. Its rollback must stop while any affected row remains null; it must not fabricate historical pins, digests, or amounts. The number is the next free version at build time. The file includes a rollback statement. This note does not write that file.

Tests under `src/test/java/com/opencrm/contract/documents/` for AC-S3-1 through AC-S3-8, plus the two OQ6 tests quoted in section 4. The concurrent-execute test for gapless numbering is AC-S3-6 (`amendment-first-slice-requirements-and-acceptance.md`, read): two drafts for one contract execute as sequences N+1 and N+2 with no gap.

The requirements now mark AC-S3-9, AC-S3-10, and AC-S3-11 Proposed, not adopted. The two quoted OQ6 tests do not establish concurrent revision/execution safety. S3 remains explicitly blocked until AC-S3-11 or an equivalent CAM-local concurrency criterion is accepted: competing revision and execution commands have exactly one committed winner, and an executed document's digest matches that executed revision. The later test allowlist must include that accepted race/digest criterion; it cannot treat the two sequential OQ6 cases as equivalent coverage. This frame adopts no new criterion and authorizes no S3 work.

**Avoid**, even if S3 is later authorized by a different record:

- `backend/libs/common`
- `quote-service`
- `pricing-service`
- `V1_7`, `V1_7_1`, `V1_7_2`, and `V1_8` migrations
- the OQ6 record (`docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md`)
- `backend/services/contract-asset-management/src/main/resources/application.yml`

Existing tests a later slice keeps green (paths verified present; not executed for this note):

- `src/test/java/com/opencrm/contract/documents/ContractDocumentsSchemaIntegrationTest.java`
- `src/test/java/com/opencrm/contract/documents/ContractDocumentsMigrationScriptTest.java`
- `src/test/java/com/opencrm/contract/documents/AmendmentEventChainIntegrationTest.java`
- `src/test/java/com/opencrm/contract/documents/events/AmendmentEventHasherTest.java`
- `src/test/java/com/opencrm/contract/documents/audit/AmendmentChainVerifierTest.java`

## 6. PROPOSED EDIT to the authorization gate — not applied

**Proposed, unaccepted, owner decision required.**

File not edited: `docs/renewals-amendments/prereq/story-s3-s4-authorization-gate.md`.

The S3 row's migration cell today reads: "None. V1_7 is S1."

A later edit, applied only after the owner accepts it, would state that this holds only if S3 never stores an unpriced or digest-less revision, and would point at section 1 of this note. If option (a) is the accepted choice, that cell would name a new CAM migration at `V1_10` or higher (next free at build time, with a rollback statement), would keep `V1_9` reserved for the S4 apply ledger, and would forbid `V1_7_3`. This correction stays a proposed edit. It is not applied. A merge of #657 does not apply it. Recommending default (a) is NOT a migration approval.

## 7. Not in scope — not built

The following are not built by this note and are not authorized by it:

- **S6.** No `QuoteAcceptedEvent` fields. No schema-registry events.
- **S4.** No apply ledger. No `V1_9`.
- **S5.** Not started.
- **S7 backfill.** Not started. `V1_8__contract_header_backfill.sql` is already on `main` and is not edited.
- **DRG-609.** Not accepted and not built.
- **Flags.** `opencrm.outbox.relay.enabled` and `opencrm.pricing.ramp.enabled` are not set, flipped, or discussed as a change. No flag is involved.
- **Supersession domain event versus S2 audit publication.** A later S3 slice still has no accepted supersession domain-event payload, schema, topic, or outbox contract, as OQ6 section 4 records. This does not disable the existing S2 audit publication. `AmendmentEventWriter.append` persists each new hash-chained document event and calls `auditOutbox.stage(document, event)` in the same transaction (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java`, read during PR processing). It cannot be reused as a local-log-only writer while suppressing that audit outbox row. The local-log option in #653 must distinguish that existing audit obligation from a new supersession domain event; any exception needs a separate accepted audit decision. This note authorizes neither an exception nor S6 work.

No deploy.

## 8. ADRs

Read on this `main`:

- `docs/adr/ADR-017-synchronous-inter-service-communication.md` status line: Proposed on 2026-10-04. Not accepted. `docs/adr/README.md` catalogs it as Proposed.
- `docs/adr/ADR-024-order-orchestration-context.md`, `ADR-025-saga-process-manager.md`, `ADR-026-command-messages.md`, and `ADR-027-compensation-and-point-of-no-return.md` each say Proposed on 2026-10-04. Not accepted. The catalog lists ADR-024 as Proposed; ADR-025 through ADR-027 were read at the status line.
- `docs/renewals-amendments/adr-035-amendment-document-placement.md` status line: Accepted on 2026-10-16. Scope: authorizes the first-slice schema and audit stories only (S1, S2). The acceptance paragraph repeats that scope (S1, S2; RA-45 / RA-46; DRG-579 / DRG-580) and says it does not authorize later stories.

ADR-017 and ADR-024 through ADR-027 stay Proposed. S3 does not need them. S3 sits on ADR-035, and that acceptance is scope S1 and S2 only. ADR-035 does not authorize S3.

## 9. Already on main

Citations are paths and migration names read at `0d71251d`, unless marked unverified.

| Claim | Path |
|---|---|
| Contract-document tables, NOT NULL digests and pins, hex checks, status `VOID` and `OUT_FOR_SIGNATURE` | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql` |
| Renewal publication receipts | `V1_7_1__durable_renewal_eligibility_publications.sql` (header read) |
| Frozen line membership | `V1_7_2__freeze_amendment_line_membership.sql` (header read; function body not reviewed) |
| S7 header backfill; V1_9 reserved in the file header | `V1_8__contract_header_backfill.sql` (header read; procedure body not reviewed) |
| Eleven-value status enum | `.../domain/documents/AmendmentDocumentStatus.java` |
| Flyway block has no `out-of-order` key | `backend/services/contract-asset-management/src/main/resources/application.yml` |
| Document tests named in section 5 | `src/test/java/com/opencrm/contract/documents/` as listed |

## 10. Unverified

- Linear issue text and the live status of DRG-581. This note does not change Linear. DRG-581 stays as it is until the owner merges an authorizing record.
- The file diff of draft PR #653. Nullable pins and a null digest until submit are taken from that pull request's body, not from a checkout of its branch. On this `main`, `DRAFT-openapi-amendments.yaml` still says the digest-library location is open (lines 51–54, read).
- A live Flyway migrate that would reject `V1_7_3` after `V1_8`. The ordering claim follows from Flyway version sorting and the absence of `out-of-order: true` in the CAM `application.yml` that was read.
- Any part of `DRAFT-openapi-amendments.yaml` outside lines 51–54.
- The procedure body of `V1_8__contract_header_backfill.sql` and the function body of `V1_7_2__freeze_amendment_line_membership.sql`.
- Whether the five existing tests listed in section 5 are green. They were not executed.
- GitNexus impact. `list_repos` returned no indexed repository. Impact is UNKNOWN.
