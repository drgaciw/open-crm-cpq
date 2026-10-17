# OQ6 revision rule and confirmed OQ18 digest ownership

**Status:** OQ6 remains adopted under delegation; its boundary contracts and OQ18 are decided under the owner's renewed delegation on 2026-10-17. See [the delegated record](delegated-renewals-amendments-decisions-2026-10-17.md). No implementation story is authorized here.

## Current decision

The pre-execution revision rule, stable provisional number, terminal refusal, executed immutability and at-most-one executed revision remain as recorded below. The [CAM contracts](delegated-renewals-amendments-decisions-2026-10-17.md#cam-transactions-and-delivery) now select the CAM API/version/idempotency behavior, audit types, local concurrency criterion and durable S5 delivery. The [storage/digest contract](delegated-renewals-amendments-decisions-2026-10-17.md#storage-and-digests) now selects nullable unpriced values, a CAM-owned immutable envelope store and genuine close-time hashing. AC-S3-9..11, AC-S5-7 and AC-S8-6 are adopted criteria, with execution evidence still required.

OQ18 is confirmed under delegation: one shared utility in `backend/libs/common`, delivered with S3, reused without forks by S5/S6, using the versioned commercial envelope and canonicalization contract in the linked record. The old owner-confirmation gate is satisfied by this decision. S3's separate implementation-authorization gate remains.

## Historical 2026-10-16 ruling and then-open questions

The material below preserves the original reading, delegation evidence and rationale. References to unconfirmed OQ18 or open boundary contracts describe that earlier state and are superseded by the current decision above.

**Date:** 2026-10-16. **Project:** OPEN-CRM-CPQ. **Stories:** S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (requires separate owner authorization), S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) (waits on S3 and its other prerequisites).

**Delegation:** the owner, drg, said "I delegate these decisions to you." (5:10 PM CT) and "Proceed" (5:52 PM CT). CPQ Decision made the OQ6 ruling and Projects Manager relayed it. The scope was flagged as somewhat ambiguous. These quotes record the delegation, not an owner-authored choice of this rule.

**Reading base:** `main` at `9998a66`. Sources: `renewals-amendments-open-questions-register.md` (OQ4, OQ6, OQ18), `prereq/story-s3-s4-authorization-gate.md` (section "OQ6"), `amendment-entity-field-schema.md` (V11, `status_at_close`), `prereq/DRAFT-openapi-amendments.yaml` (`/lifecycle-changes/{id}/revisions`, `IMMUTABLE_EXECUTED`), `amendment-first-slice-requirements-and-acceptance.md` (AC-S3-1 to AC-S3-8), `prereq/partial-apply-owner-decision.md` (OQ5 option A).

## 1. What was open

The register default for OQ6 says: new revision under the same amendment number, at most one `EXECUTED` revision (V11), drafts only edit. ADR-035 names one edge the default does not settle: a LifecycleChange superseded **mid-flight**. The authorization gate records that no file defines which states count as mid-flight, and that S3's acceptance criteria contain no superseded-mid-flight test.

OQ18 remains Proposed. The unconfirmed proposal is a canonical digest library in `backend/libs/common`, built once in S3 and reused by S5 and S6. S3 cannot start building that library until the owner confirms OQ18. This does not gate all S3 work or authorize it.

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

## 3. Rule for OQ18

The unconfirmed proposal is to put the canonical digest utility in `backend/libs/common`, deliver it with S3, and reuse it in S5 and S6. It remains Proposed until the owner confirms it. S3 cannot start building the library before that confirmation. No S6 event-contract work is authorized here.

## 4. Acceptance tests and unresolved contracts

These two S3 tests express the OQ6 ruling. They are criteria only; this record does not accept a spec or authorize implementation.

1. **Supersede before execution.** Given a document in DRAFT, IN_REVIEW, or APPROVED, when its LifecycleChange is superseded, then a new revision exists under the same document number, the old revision is frozen SUPERSEDED, the new revision is DRAFT, pricing pins are cleared, and approvals and signatures are void. REJECTED and WITHDRAWN are terminal and are not eligible for supersession.
2. **No revision after EXECUTED, and at most one EXECUTED.** Given a revision that has closed EXECUTED, when a further revision is requested, then no new revision is stored and the result is IMMUTABLE_EXECUTED. A second EXECUTED revision of that document is rejected. REJECTED and WITHDRAWN remain terminal.

The CAM command/API and expected-version contract, persisted representation of an unpriced revision, supersession event type/schema/topic/outbox contract, and CAM-local concurrency behavior remain open. The LifecycleChange trigger and cross-service delivery, acknowledgement, retry/idempotency and reconciliation belong on the S5 side, but their contract is not decided here; cross-service acceptance belongs with S8. Other event, race, and S5/S8 criteria in the draft requirements are proposals, not requirements accepted by this record.

## 5. What this does not do

- It does not authorize S3, S4, S5, S6, S7 or S8. S3 / DRG-581 needs separate owner authorization.
- It does not change OQ5 (adopted for the first slice), OQ7 (still `DEFAULT PROPOSED` in the register) or any ADR status.
- It does not settle the CAM command/API, unpriced-pin representation, supersession event contract, or S3/S5 integration details.
- It does not cover document-level `SUPERSEDED` by a later `AMENDED_AND_RESTATED` document, which is a different event (spike state table).
