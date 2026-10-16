# OQ6 and OQ18 owner decision: revisions superseded mid-flight, and where the digest library lives

**Status:** Owner decision, first slice only. **It is adopted when the owner merges this file to `main`, and not before.** Merging adopts the two rules below and the three S3 acceptance criteria in section 4. It does not authorize S3 / DRG-581 or any other story; authorization stays a separate owner act, as `story-s3-s4-authorization-gate.md` requires. It authorizes no code, schema, migration, flag, topic, gateway route or deploy.

**Date:** 2026-10-16. **Project:** OPEN-CRM-CPQ. **Stories:** S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (blocked by OQ6 and OQ18), S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) (waits on OQ6 through S3).

**Reading base:** `main` at `9998a66`. Sources: `renewals-amendments-open-questions-register.md` (OQ4, OQ6, OQ18), `prereq/story-s3-s4-authorization-gate.md` (section "OQ6"), `amendment-entity-field-schema.md` (V11, `status_at_close`), `prereq/DRAFT-openapi-amendments.yaml` (`/lifecycle-changes/{id}/revisions`, `IMMUTABLE_EXECUTED`), `amendment-first-slice-requirements-and-acceptance.md` (AC-S3-1 to AC-S3-8), `prereq/partial-apply-owner-decision.md` (OQ5 option A).

## 1. What was open

The register default for OQ6 says: new revision under the same amendment number, at most one `EXECUTED` revision (V11), drafts only edit. ADR-035 names one edge the default does not settle: a LifecycleChange superseded **mid-flight**. The authorization gate records that no file defines which states count as mid-flight, and that S3's acceptance criteria contain no superseded-mid-flight test.

OQ18 (digest canonicalization library) is `DEFAULT PROPOSED` as `backend/libs/common`, needed before S3, but three texts give three timings: the register (before S3), the OpenAPI draft (an S3 dependency) and technical approach §2 (S6). S3 is the first story that stores a digest (AC-S3-2), so the timing has to be settled before S3.

## 2. Rule for OQ6 (first slice)

Document states in the first slice: `DRAFT`, `IN_REVIEW`, `APPROVED`, `EXECUTED`, then the apply outcomes `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, and the terminal `WITHDRAWN`. `OUT_FOR_SIGNATURE` cannot be reached in the slice, because E_SIGN and WET execution return 422 `SIGNATURE_NOT_SUPPORTED_IN_SLICE` (AC-S3-4).

1. **Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`).** A new revision of the LifecycleChange opens a new document revision on the same document, with the same `provisionalNumber` (OQ4). The previous revision closes with `status_at_close = SUPERSEDED` and keeps its content digest. The document returns to `DRAFT`. Approvals and pricing pins bound to the previous revision's digest do not carry over; the new revision must be priced and submitted again. The supersession writes an AmendmentEvent in the same transaction (A6).
2. **Executed, in any apply state (`EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`).** The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED` and an audit event, which is AC-S3-5 applied to revisions. "Executed but not yet applied" gets no special path. The remedy is a new LifecycleChange that produces a new, correcting amendment document, the same remedy OQ5 option A adopted for a blocked unit.
3. **Revise and execute racing on one document.** The document row version serializes them; exactly one commits. If the revision commits first, the execute request fails because the document is back in `DRAFT`. If execution commits first, the revision fails with `IMMUTABLE_EXECUTED`. Execution records the digest of the revision it executed, and that digest must equal the revision the acceptance was given against.
4. **`OUT_FOR_SIGNATURE`, for the later signature story.** A new revision must void the open signature envelope before rule 1 applies. Recorded so that story inherits it; nothing in the slice builds it.

Why this rule: it keeps the reading the gate called an inference ("drafts only edit" means pre-execution edits, and an executed document is immutable) and makes it explicit; it matches the OpenAPI draft, which already refuses a new revision of an `EXECUTED` document; it keeps one remedy for every post-execution correction (a correcting amendment), so S3, S4 and OQ5 need no second recovery path; and it keeps V11 (one executed revision) and gapless numbering at `EXECUTED` unchanged.

Rejected alternatives: allowing a revision of an executed-but-unapplied document (it would edit a document the customer already accepted, and breaks A1 and AC-S3-5); a new amendment number per revision (conflicts with OQ4, where the provisional number is stable across revisions).

## 3. Rule for OQ18

The canonical digest utility lives in `backend/libs/common` and is delivered **with S3**, its first consumer (AC-S3-2 stores a digest at submit; rule 1 above keeps it at supersession). S5 and S6 reuse it. Technical approach §2's assignment to S6 is superseded for the utility itself; S6 keeps the event-contract work.

## 4. S3 acceptance additions

These join AC-S3-1 to AC-S3-8 in `amendment-first-slice-requirements-and-acceptance.md` when this file is merged:

- **AC-S3-9.** Given a document in `DRAFT`, `IN_REVIEW` or `APPROVED`, When its LifecycleChange gets a new revision, Then a new document revision exists on the same document with the same provisional number, the previous revision is closed `SUPERSEDED` with its digest kept, the document is `DRAFT`, approvals bound to the previous revision no longer count, and an event is written.
- **AC-S3-10.** Given a document that is `EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED` or `APPLY_BLOCKED`, When a new revision is requested, Then the API returns 409 `IMMUTABLE_EXECUTED`, nothing changes, and an event is written.
- **AC-S3-11.** Given an `APPROVED` document, When a new revision and an execution are requested concurrently, Then exactly one commits and the other returns 409, and an executed document's recorded digest equals the digest of its executed revision.

## 5. What this does not do

- It does not authorize S3, S4, S5, S6, S7 or S8.
- It does not change OQ5 (adopted for the first slice), OQ7 (still `DEFAULT PROPOSED` in the register) or any ADR status.
- It does not cover document-level `SUPERSEDED` by a later `AMENDED_AND_RESTATED` document, which is a different event (spike state table).
