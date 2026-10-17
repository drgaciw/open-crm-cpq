# OQ5 owner decision: partial application of an amendment

**Status:** Option A is adopted for the first slice only, as recorded in section 9. Sections 1 through 8 preserve the original request and analysis, with current-status pointers updated after adoption. This file does not accept the Proposed spec or an ADR. It authorizes no code, schema, migration, flag, topic, gateway route, or deploy.

**Date:** 2026-10-16. **Project:** OPEN-CRM-CPQ. **Backlog:** RA-31 / [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565/partial-application-rules-for-mixed-line-amendment-documents).

**Reading base:** `main` at `dd0f1a84415b37b3102ba5a46b42d397c3237d6f`. Every citation below is a file read at that commit, or the Linear issue DRG-565 read the same day. Claims not opened in this pass are marked **Unverified** in the last section.

**Integration update (#622).** The reading below is pinned to `dd0f1a8`. #622 corrects the quoted spec to unit status BLOCKED, one document status, and unique business identity `(documentId, unitNo)` with a separately checked replay digest. The historical mismatch descriptions below do not reopen those corrections. Section 9 now records the first-slice adoption of OQ5 option A (#632). The broader Proposed spec remains unaccepted, and S4 and S6 are not authorized by that adoption. The reconciliation record moves to `adr-reconcile-adr-024-027.md` under #623; ADR-024 through ADR-027 remain Proposed.

## 1. The question

Should one amendment document be allowed to apply in several atomic units, so that a unit which has already committed stays committed when a later unit fails?

Spike decision 5 asks it this way: "Should one document be allowed to apply in several units, with a PARTIALLY_APPLIED state?" The spike recommends yes, with atomic units and no silent rollback (`amendment-document-design-spike.md`, decisions needed, item 5).

At the pinned reading base, the open-questions register recorded the same question as **OQ5 Partial application**, status **DEFAULT PROPOSED**, blocking **S4**, needed before S4 (Day 2). Section 9 and the current register now record first-slice adoption. The proposed default at the reading base was: one unit per subscription, atomic per unit, no auto rollback, document PARTIALLY_APPLIED, remedy by correcting amendment (`renewals-amendments-open-questions-register.md`).

The Proposed spec `specs/partial-apply.md` states its own status as "Proposal only." It says it does not accept the spike's partial-apply rules, write an ADR, or authorize implementation, and that mid-term OQ5 stays open. Its open question 1 is the owner decision: allow partial apply, or apply the whole document all-or-nothing with no `PARTIALLY_APPLIED` state. It says implementation of its AC-2 through AC-13 waits on a yes.

ADR-035 lists this question as out of scope: "OQ5 Partial application / PARTIALLY_APPLIED (spike recommended yes)." ADR-035 is **Accepted** on 2026-10-16 for the first-slice schema and audit stories only (S1, S2). The catalog row in `docs/adr/README.md` says the same. The acceptance checklist says OQ5 is needed after acceptance, before S4, and was not needed to accept ADR-035 (`adr-035-acceptance-checklist.md`).

## 2. Proposed default

**Original proposed default, now adopted for the first slice in section 9:** confirm the register's OQ5 sentence for the first slice, and use the Proposed spec's cut between the two failure states.

Operational reading of that default:

1. **One unit per target subscription** in the first slice. That is the register sentence and FR-09 in `amendment-first-slice-requirements-and-acceptance.md`. The first slice is mid-term amendment of an ACTIVE SaaS subscription set, action codes Add, Update, and Remove (`amendment-first-slice-requirements-and-acceptance.md`, slice definition).
2. **A unit is atomic.** Spec rule 2: one unit writes its rate periods, end dates, revision bump, and ledger row in one transaction, or it writes nothing. Spine rule V12 says the same: units apply in dependency order, and a unit writes all its rows or none (`mid-term-amendment-policy-spec.md`).
3. **No automatic rollback** of a unit that has committed. Register sentence, FR-12, and AC-S4-3.
4. **PARTIALLY_APPLIED** when at least one unit has committed and a later unit fails. The failed unit is **BLOCKED**. FR-12 and AC-S4-3 state that outcome for a stale `observedRevision` on unit 2 after unit 1 succeeded.
5. **APPLY_BLOCKED** when no unit has committed. That cut is the Proposed spec's open question 2. The register sentence names PARTIALLY_APPLIED and does not name the cut. The proposed default includes the cut so S4 has one rule for the two names.
6. **The first-slice remedy named by the register and by FR-12 is a correcting amendment.** The spike (section 4, rule 5) and the Proposed spec (rule 5, AC-9, AC-12) also describe a rebase: re-observe, re-price, new revision, re-approval when the price changed. The proposed default does not adopt rebase. Spec open questions 5 (task and actor) and 6 (re-approval on rebase) stay open.
7. **Signature stays all-or-nothing.** Spec rule 1 and spike section 4 rule 1: the customer signs one document. Partial apply is an apply outcome, not a partial signature.

A yes to this default adopts those seven points for the first slice. It does not accept `specs/partial-apply.md`, does not close that file's open questions 3 through 8, and does not authorize S4 or S6. ADR-035's acceptance text authorizes S1 and S2 only.

## 3. Options

| Option | What the owner would be choosing | Where it is written |
|---|---|---|
| **A. Partial application (proposed default)** | Units commit independently. A committed unit stays. The document is PARTIALLY_APPLIED or APPLY_BLOCKED on the cut in section 2. First-slice remedy is a correcting amendment. | Register OQ5; spike decision 5; FR-12; AC-S4-3; Proposed spec rules 1 to 7, with rebase left out of the default |
| **B. All-or-nothing apply** | The whole document commits, or none of it does. There is no PARTIALLY_APPLIED state. The Proposed spec says that if the owner rejects partial application, the build stops at its open question 1 and AC-2 through AC-13 are not the specification. | `specs/partial-apply.md`, open question 1 |
| **C. Leave the default unconfirmed** | OQ5 stays DEFAULT PROPOSED. S1 and S2 remain the stories ADR-035 authorizes. S4 does not treat FR-12 or AC-S4-3 as an accepted rule. | Register status values; dev-readiness condition 4; ADR-035 acceptance scope |

Dev-readiness condition 4 says to confirm OQ5 before S4 starts, around Day 2 (`renewals-amendments-dev-readiness.md`). The register's status legend says DEFAULT PROPOSED means work can start on the stated default and the default is confirmed by the date given. The original request asked for that confirmation; section 9 now records it for the first slice only. The other S4 dependencies, including DRG-565's prerequisite first-slice written ordering rules and acceptance conditions, remain open. S4 acceptance remains AC-S4-1 through AC-S4-9; this record adds no scenario tests.

## 4. Consequences for S4

S4 is RA-48 / DRG-582, "Document-driven apply with multi-subscription units." It depends on S1 through S3 and on DRG-565 (`amendment-first-slice-requirements-and-acceptance.md`).

Written S4 rules that already assume a per-unit apply, independent of the failure policy:

- **FR-09.** Ledger changes are applied only after EXECUTED, per ApplicationUnit, one unit per target subscription, atomic per unit, in dependency order. The unique key is (`document_id`, `unit_no`). A stored `apply_key_digest` is checked on replay: the same digest is a no-op, and a different digest is rejected and audited. Source column: V12, V13, OQ2.
- **FR-10.** One document may apply to several subscriptions of one contract. The one-row-per-quote assumption on `applied_quote_acceptances` is removed or bypassed for document-driven applies. Source: DRG-563.
- **AC-S4-1.** An EXECUTED document with units for subscriptions A and B writes both units' rate periods and the document is APPLIED.
- **AC-S4-2.** A second delivery of the same apply message is a no-op (V13). The same key with a different digest is rejected and audited.
- **AC-S4-8.** Apply of a document that is not EXECUTED is refused.

Written S4 rules that are the partial-apply default, marked **[I]** on FR-12:

- **FR-12.** A unit failure marks the unit BLOCKED and the document PARTIALLY_APPLIED or APPLY_BLOCKED. Already applied units are not rolled back. A correcting amendment is the remedy. Source column: spike decision 5, OQ5 default.
- **AC-S4-3.** Unit 1 succeeds and unit 2 fails V3 (`observedRevision` stale). Unit 1 stays applied, unit 2 is BLOCKED, the document is PARTIALLY_APPLIED, unit 1 is not rolled back, and an event records the cause.

**If the owner confirms option A:** AC-S4-3 and FR-12 are the S4 failure contract, with APPLY_BLOCKED reserved for the case where no unit has committed (section 2, point 5). AC-S4-3 does not itself demonstrate APPLY_BLOCKED. The Proposed spec's AC-8 does: a stale unit fails with no rows of that unit written, and the document is APPLY_BLOCKED if it was the only unit, or PARTIALLY_APPLIED if another unit already applied. Those spec criteria become the specification only if the owner adopts partial application. This file does not make them so.

**If the owner chooses option B:** FR-12 and AC-S4-3 are not the specification. The Proposed spec says the build stops at open question 1. The files read here do not specify the all-or-nothing write procedure: whether every unit's rows share one transaction, or how a failure leaves zero ledger rows. That procedure would have to be written before S4 treats option B as a requirement.

**If the owner chooses option C:** S4 stays behind the unconfirmed default. ADR-035 still authorizes S1 and S2 only.

## 5. Consequences for S6

S6 is RA-50 / DRG-584, "Event contract and legacy gating." The requirements file says it depends on S3 and S4. The technical approach says S6 needs S3 and S4 shapes (`amendment-technical-approach-and-dependencies.md`, section 6).

Written S6 surface:

- **FR-16.** `QuoteAcceptedEvent` gains `amendmentDocumentId` and per-line `applicationUnitNo`. CAM routes document-bearing events to the new path and legacy events to the existing path behind a per-tenant feature flag. Source column: spike 6.6 and 6.4.
- **AC-S6-1.** Old consumers ignore unknown fields on a new event. The current consumer still accepts the existing `accepted-v1/*.json` fixtures. Both directions pass.
- **AC-S6-2.** An event with `amendmentDocumentId`, flag on, takes the document path and does not run the legacy apply. Flag off takes the legacy path.
- **AC-S6-4.** The named events are AmendmentDrafted, Submitted, Approved, Executed, UnitApplied, Applied, ApplyBlocked, and Withdrawn. Each has a registered schema and a contract test.

The technical approach section 4 lists the same event names: AmendmentDrafted, Submitted, Approved, Executed, UnitApplied, Applied, ApplyBlocked, Withdrawn. The renewal stub lists `AmendmentUnitApplied`, `AmendmentApplied`, and `AmendmentApplyBlocked` (`renewal-policy-stub.md`, section 5). None of those lists, as read, names a distinct partially-applied event.

**If the owner confirms option A:** a document can have a committed unit and still not be APPLIED (AC-S4-3). UnitApplied reports a committed unit. The document-level signal for PARTIALLY_APPLIED is not defined by those event-name lists and remains an owner/contract decision; ApplyBlocked must not be inferred to mean a partial document outcome. The mapping needs a distinct contract or an explicitly accepted rule before S6 implements it. Applied is the signal when every unit has applied (Proposed spec AC-11; requirements AC-S4-1). S6 must keep the legacy path from also applying a document-bearing event when the flag is on (AC-S6-2). Risk R3 in the technical approach is the double-apply of the same acceptance, mitigated by routing on `amendmentDocumentId`, inbox dedup, and tests AC-S4-9 and AC-S6-2.

The per-tenant flag's granularity is OQ11, status DEFAULT PROPOSED, and it blocks S6. OQ5 does not decide OQ11.

**If the owner chooses option B:** ApplyBlocked can still mean the whole apply failed. A UnitApplied event that is durable while a sibling unit has failed would not match option B. The files read here do not say whether UnitApplied is suppressed until the whole document commits. The draft OpenAPI reserves `PARTIALLY_APPLIED` and `APPLY_BLOCKED` on `AmendmentStatus` for S4, and the `:apply` description says partial application (OQ5) is undecided and that there is no `applyStatus` field (`prereq/DRAFT-openapi-amendments.yaml`). This file does not edit that contract.

**Draft reconciliation note, not a decision:** `prereq/adr-reconcile-adr-024-027.md` says a parked on-prem line leaves the SaaS units applied, and that partial application across units stays OQ5. Its consequences section says operators will see an applied SaaS unit while an on-prem line is still in fulfilment. That draft says it does not close OQ5. Ordering and fulfilment are out of the first slice (`renewals-amendments-dev-readiness.md`, section 6).

## 6. Consequences for the apply ledger (V1_9)

The technical approach, section 3, item 3, proposes **V1_9** as the S4 migration:

> new `document_apply_ledger` with a unique key (`document_id`, `unit_no`) and stored `apply_key_digest`; a replay with the same digest is a no-op and a different digest is rejected and audited; keep `applied_quote_acceptances` unchanged for the legacy path.

That paragraph is marked **[I]**. It says the new table avoids altering a UNIQUE constraint under load and resolves the DRG-563 conflict by bypass, not mutation. The schema addendum of 2026-10-16 states the same order: V1_7 expand, V1_8 contract header backfill, V1_9 `document_apply_ledger` (new, unique on `document_id`, `unit_no`, with `apply_key_digest` stored and checked on replay), legacy `applied_quote_acceptances` untouched (`amendment-entity-field-schema.md`, dev-readiness addendum).

Two earlier passages describe a different home for the same key:

- Schema section 2.6 puts `apply_key_digest` on `amendment_application_units`, and says `applied_quote_acceptances` gains nullable `amendment_document_id` and `application_unit_no` (RA-29). The section is labeled **dep OQ5**, default: partial application allowed, atomic units. Unit `status` there is `PENDING`, `APPLIED`, `BLOCKED`.
- Spike section 5 says `applied_quote_acceptances` stays the idempotency ledger, and that a nullable `amendment_document_id` and `application_unit_no`, or a join table, would let one document map to several acceptance rows. It says today's `accepted_quote_id` UNIQUE with a single `subscription_id` cannot represent one quote applied to several subscriptions, and names that gap RA-29.

The current Proposed spec uses unit state `PENDING`, `APPLIED`, `BLOCKED`, aligned with FR-12 and AC-S4-3. Its business identity is `(documentId, unitNo)`, with contentDigest checked separately: matching replay is a no-op, changed digest is rejected and audited. #622 resolved the earlier FAILED/triple-key mismatch; it is not an open owner choice.

**If the owner confirms option A:** V1_9, as the technical approach writes it, stores one row per committed unit under (`document_id`, `unit_no`). A PARTIALLY_APPLIED document has a row for each committed unit and no row for a unit that wrote nothing. Replay of the same digest does not insert a second row. A different digest on the same unit key is rejected, so a correcting amendment is a new document (new document id, new unit numbers), not a digest change on the failed unit's key. The technical approach marks this ledger shape **[I]**. This file does not choose it over the schema 2.6 columns on `applied_quote_acceptances`.

**If the owner chooses option B:** the per-unit unique key can still exist, and a failure must leave zero rows for that document. The files read here do not specify that procedure. A compensating delete of a committed unit would conflict with the spike's "no silent rollback" rule and with audit rule A1 (executed and applied records are not updated or deleted; corrections are new events or new amendments). Option B needs an explicit write rule before V1_9 is treated as its ledger.

**Unverified in code:** this pass did not open CAM migration files, `applied_quote_acceptances`, or `AcceptedQuoteSubscriptionService`. The technical approach section 10 says the next CAM migration version is V1_7 because files V1_1 through V1_6 existed on a docs-branch checkout. That check was not repeated here.

## 7. Risks

| # | Risk | What was read |
|---|---|---|
| R7 | Partial-apply semantics surprise users. Likelihood and impact in the technical approach: Medium / Medium. Mitigation written there: default FR-12, visible state, correcting-amendment path, confirm OQ5. | `amendment-technical-approach-and-dependencies.md`, section 7 |
| Half-applied renewal | The renewal stub's OQ5 line says co-termed multi-asset renewals may half-apply. Renewal policy is out of the first slice. A yes on the first-slice default does not by itself adopt that renewal outcome. | `renewal-policy-stub.md`, section 6; dev-readiness section 6 |
| Split price-lock segments | The price-lock stub's OQ5 line says a segment set must apply as one unit. Price lock is out of the first slice. A yes on per-subscription units does not say a segment schedule may be split across units. | `price-lock-policy-stub.md`, section 6 |
| Remedy approver | Parking-lot PL-9 item 6 asks who approves the remedy or correcting amendment after PARTIALLY_APPLIED or APPLY_BLOCKED. The approval skeleton maps those states to remedy approval, BLOCK on the correcting amendment, and labels that mapping **[INFERENCE]**. PL-9 is parked (RA-57 / DRG-591). | `renewals-amendments-parking-lot.md`; `approval-requirements-skeleton.md`, sections 3 and 7 |
| Rebase scope | Adopting the register's correcting-amendment remedy leaves the spike's rebase path unspecified for S4. The spike state diagram has `APPLY_BLOCKED --> PARTIALLY_APPLIED: retried after rebase`. The Proposed spec's AC-12 says a rebase that changes a priced amount needs approval again, and leaves the gate to the owner. | Spike section 3 diagram and section 4 rule 5; `specs/partial-apply.md` AC-12 and open questions 5 and 6 |
| Ledger home | Schema 2.6 alters `applied_quote_acceptances`. The V1_9 paragraph leaves that table untouched and adds `document_apply_ledger`. Both were read. Neither was checked in SQL. S4 cannot implement both. | Schema section 2.6 and the 2026-10-16 addendum; technical approach section 3 |
| Unit status word (resolved in #622) | The current schema, FR-12, AC-S4-3 and Proposed spec all use BLOCKED. This is no longer an OQ5 risk or an owner decision. | Schema section 2.6; FR-12; AC-S4-3; corrected `specs/partial-apply.md` |
| Event name for the partial document | The event lists read for S6 name UnitApplied, Applied, and ApplyBlocked. They do not name a partially-applied event. Document state PARTIALLY_APPLIED can exist without a matching event name. | Requirements AC-S6-4; technical approach section 4; renewal stub section 5 |
| Inference labeled as source | Spike section 4 titles the partial-application rules **[INFERENCE]**. The Proposed spec retags those rules **[S spike §4]**. The rules are the spike's proposal. They are not a verified implementation. | Spike section 4; `specs/partial-apply.md` conventions and rules |
| Scope of ADR-035 | Confirming OQ5 does not extend ADR-035. Acceptance authorizes S1 and S2 only. Technical-approach risk R1 says S3 to S8 need their own gates. | ADR-035 acceptance section; technical approach R1 |
| Readiness drift | Dev-readiness section 3 still says placement is "Proposed only; needs acceptance (OQ1)." The ADR file, the catalog row, and the register's OQ1 row say Accepted on 2026-10-16. Both texts were read. Which edit landed last was not re-checked beyond the file contents at `dd0f1a8`. | `renewals-amendments-dev-readiness.md` section 3; ADR-035; `docs/adr/README.md`; register OQ1 |

## 8. What stays open even if the owner confirms the default

The Proposed spec lists these as owner decisions. This default does not close them:

1. Who writes `dependsOnUnitNos`: the seller, or the system from action codes (spec open question 3). AC-3 states create-before-terminate for the restructure grouping.
2. When the restructure revoke unit is submitted to fulfilment (spec open question 4). The first slice excludes fulfilment.
3. Which queue receives the failed-unit task, and whether the seller or a contract admin acts (spec open question 5).
4. Whether a revision-only rebase can proceed on the existing approval (spec open question 6). Rebase is outside the proposed default.
5. Whether NONE units are stored and skipped, or omitted (spec open question 7). Schema `apply_target_type` includes NONE.
6. Signature policy per document (spec open question 8). Slice signature policies are already confirmed as NOT_REQUIRED, NOTICE_ONLY, and CLICK_THROUGH (register OQ10).

DRG-565, as read in Linear, asks for partial-application rules for mixed-line documents: all-or-nothing versus unit by unit, order of operations, a unit that fails after others have committed, and the states PARTIALLY_APPLIED and APPLY_BLOCKED. Its suggested next step is to specify ApplicationUnit grouping and state transitions, and to add test scenarios for restructure, true-up, and future-dated lines. The Proposed spec writes those scenarios. They apply only if the owner adopts partial application. This file does not mark DRG-565 done.

## 9. Owner record

Recorded 2026-10-16. Sections 1 through 8 preserve the request and analysis merged in #626, with current-status pointers reconciled after #632. This section records the confirmation that followed. The record is the first slice only.

Option A for the first slice was delegated by the owner and relayed by Projects Manager. The owner, drg, said, about 5:10 PM CT: "I delegate these decisions to you." Projects Manager confirmed OQ5 option A (#626). CPQ Decision ruled the confirmation valid about 5:11 PM CT. The ruling is the first slice only.

- Decision: **A**, first slice only. Delegated by the owner and relayed by Projects Manager. Projects Manager confirmed option A. CPQ Decision ruled the confirmation valid.
- Date: 2026-10-16. Delegation about 5:10 PM CT. Validity ruling about 5:11 PM CT.
- What option A is: one unit per target subscription, atomic units, no automatic rollback, PARTIALLY_APPLIED when a later unit fails, APPLY_BLOCKED when no unit has committed, correcting amendment as remedy.
- APPLY_BLOCKED means no unit has committed: yes.
- Rebase is a first-slice remedy: no. The remedy is a correcting amendment. The proposed default left rebase out, and this record does not add it.
- Ledger table for S4: not chosen. The proposed default does not choose between `document_apply_ledger` in the V1_9 paragraph and columns on `applied_quote_acceptances` in schema 2.6. This record does not choose either.

`specs/partial-apply.md` is NOT accepted. S6 is NOT authorized. S4 / DRG-582 is not authorized by this edit. S4 / DRG-582 still waits on S3 / DRG-581, OQ6, DRG-562/563, and DRG-565's prerequisite first-slice written ordering rules and acceptance conditions (broader scenario work remains deferred under DRG-565). The owner merges.

This record authorizes no code, schema, migration, flag, topic, gateway route, or deploy.

## 10. Sources read

| Source | Used for |
|---|---|
| `prereq/specs/partial-apply.md` | Proposed status, rules 1 to 7, AC-1 to AC-13, open questions 1 to 8, dependencies |
| `adr-035-amendment-document-placement.md` | Accepted scope (S1, S2); OQ5 out of scope; does-not-authorize paragraph in the header |
| `docs/adr/README.md` | Catalog row for ADR-035 |
| `adr-035-acceptance-checklist.md` | OQ5 after acceptance, before S4; outcome section |
| `renewals-amendments-open-questions-register.md` | OQ5 row and status legend; OQ1, OQ2, OQ10, OQ11 |
| `amendment-document-design-spike.md` | Decision 5; section 3 states and diagram; section 4 rules; section 5 ledger gap; closing status paragraph |
| `amendment-first-slice-requirements-and-acceptance.md` | Slice definition; FR-09, FR-10, FR-12, FR-16; AC-S4-*; AC-S6-*; story ids |
| `amendment-technical-approach-and-dependencies.md` | V1_9; control flow; sequencing; R1, R3, R7; event names; section 10 verification note |
| `amendment-entity-field-schema.md` | Section 2.6; section 4 apply idempotency; V1_9 addendum |
| `mid-term-amendment-policy-spec.md` | V12, V13, A1; section 3 apply row; section 8 OQ5; T4; 2026-10-16 addendum |
| `renewal-policy-stub.md` | OQ5 half-apply line; event names in section 5 |
| `price-lock-policy-stub.md` | OQ5 segment-set line |
| `approval-requirements-skeleton.md` | Remedy gate; BLOCK on the correcting amendment |
| `renewals-amendments-parking-lot.md` | PL-9 item 6 |
| `renewals-amendments-dev-readiness.md` | Condition 4; section 3 placement row; section 6 out of slice |
| `prereq/DRAFT-openapi-amendments.yaml` | `:apply` description; `AmendmentStatus` enum |
| `prereq/adr-reconcile-adr-024-027.md` | Mixed-document paragraph; negative consequence; OQ5 left open |
| Linear DRG-565 | Title, description, related issues as returned by the issue read |

## 11. Unverified

- CAM Java and SQL, including `AcceptedQuoteSubscriptionService`, `applied_quote_acceptances`, and migration files V1_1 through V1_6. The spike and the technical approach cite them. This pass did not open them.
- Whether V1_7 is still the next free CAM version at `dd0f1a8`. The technical approach's [V] note was not repeated.
- TMF622's `partial` order state. DRG-565 and the spike cite it as precedent. The standard text was not opened.
- Approval-routing support for per-gate approver roles. The parking lot says that was not verified for PL-9.
- Full bodies of DRG-563, DRG-582, DRG-551, DRG-536, and DRG-559. DRG-565's relation list was read. Those issues' descriptions were not.
- The remainder of `DRAFT-openapi-amendments.yaml` and of `adr-reconcile-adr-024-027.md` beyond the passages cited above.
