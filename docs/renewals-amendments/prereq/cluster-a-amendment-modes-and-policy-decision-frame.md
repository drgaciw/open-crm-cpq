# Cluster A — Amendment modes and commercial policy (decision frame)

**Status:** Proposed. This note is not an ADR. No fork below is accepted. Recording a recommendation and a default does not accept either one.
**Date:** 2026-10-17
**Base:** `main` at `3ee3ca0f` (merge of [#642](https://github.com/drgaciw/open-crm-cpq/pull/642)).
**Project:** OPEN-CRM-CPQ, Linear team DRG, milestone Renewals & Amendments.
**Stories:** [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592) (RA-58), [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) (RA-20), [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (RA-60), [DRG-599](https://linear.app/drgamtd-workspace/issue/DRG-599) (RA-65), [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) (RA-16).

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** Accepting this note would still authorize none of those. S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) is not started. S6 / [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) is not authorized. The [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) pricing-service design is not accepted.

Descriptions and relations for the five stories were read on 2026-10-17. Each description is a policy or design gap. None asks this change to ship an implementation, so none is skipped.

## Standing constraints

These apply to every section.

| Constraint | Where it stands on this `main` |
|---|---|
| ADR-035 is Accepted for S1 and S2 only | [`adr-035-amendment-document-placement.md`](../adr-035-amendment-document-placement.md). Acceptance authorizes the first-slice schema and audit stories (S1 / DRG-579, S2 / DRG-580). Later stories need their own gates. |
| ADR-017 and ADR-024 through ADR-027 stay Proposed | [`docs/adr/ADR-017-synchronous-inter-service-communication.md`](../../adr/ADR-017-synchronous-inter-service-communication.md), [`ADR-024`](../../adr/ADR-024-order-orchestration-context.md), [`ADR-025`](../../adr/ADR-025-saga-process-manager.md), [`ADR-026`](../../adr/ADR-026-command-messages.md), [`ADR-027`](../../adr/ADR-027-compensation-and-point-of-no-return.md). The reconcile record [`adr-reconcile-adr-024-027.md`](adr-reconcile-adr-024-027.md) is a separate status-change. Its own header says that record does not accept ADR-024 through ADR-027. This note does not treat those four ADRs, or ADR-017, as accepted. |
| Proration formula stays the Accepted day-stub | [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md), Accepted 2026-10-16, D-01 and D-02. This note does not reopen the formula, the rejected full-term ratio, or the DRG-542 delete list. |
| S6 is not authorized | [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) records that S6 is not authorized. ADR-035 does not authorize it. |
| S3 is not started | [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) merged to `main` at 2026-10-17T07:40:40Z. That merge records OQ6 under delegation and leaves OQ18 Proposed ([`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md)). DRG-581 remains Todo and says that merge authorizes no S3 work. Design-contract gaps stay on [DRG-616](https://linear.app/drgamtd-workspace/issue/DRG-616). |
| DRG-609 design is not accepted | [`story-drg-609-pricing-service-continuation-design.md`](story-drg-609-pricing-service-continuation-design.md) is a design proposal. It is not an ADR. S5 / [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) stays blocked on that capability. |
| OQ7 thin default stays | [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md): one thin contract per existing subscription. No synthetic history. The backfill does not regroup. Regrouping is the DRG-554 follow-up in section 2. |
| First slice stays amend-in-place | SaaS only; action codes Add, Update, Remove; signature policies `NOT_REQUIRED`, `NOTICE_ONLY`, `CLICK_THROUGH`. [`amendment-first-slice-requirements-and-acceptance.md`](../amendment-first-slice-requirements-and-acceptance.md). OQ26 in [`renewals-amendments-open-questions-register.md`](../renewals-amendments-open-questions-register.md). |

Cluster proposals under `prereq/specs/` (`co-termination.md`, `catalog-model.md`, `lifecycle-intents.md`, `partial-apply.md`) stay proposals. This note does not accept them.

---

## 1. DRG-592 — Amendment modes

**Issue:** [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592) (RA-58), High, Backlog. Relations read: DRG-586, DRG-535, DRG-561, DRG-579, DRG-581, DRG-607.

### Problem

An amendment needs two modes. New-contract mode is the preferred mode: it creates a new contract, both contracts stay `ACTIVE`, a stored precedence flag on the new contract says it takes precedence, and the original stays open as a reference. Amend-in-place changes the existing contract. The first slice builds amend-in-place only. The open design is the flag shape, who may change it, how overlapping active contracts resolve for pricing, entitlement, and visibility, and what happens to the original at its own end date.

### Settled on main

- **Sequencing.** OQ26 is resolved: both modes are supported. Amend-in-place ships in S1–S8. New-contract mode is the next increment (DRG-592), not slice 1. Once both exist they coexist. Register row OQ26; clarification in [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) (2026-10-16, C1) and [`renewals-amendments-dev-readiness.md`](../renewals-amendments-dev-readiness.md).
- **Flag placement.** OQ27 is resolved: `precedence_flag` is a separate indexable column on the contract record. It is not part of the ADR-035 thin header (id, number, tenant, customer reference, start, end, status, `last_executed_seq`, `prior_contract_id`). Register row OQ27; [`amendment-entity-field-schema.md`](../amendment-entity-field-schema.md) section 2.2a.
- **S1 schema has no flag and no mode.** [`V1_7__contract_documents.sql`](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql) creates `contracts` with `prior_contract_id` and `status` in `ACTIVE`, `EXPIRED`, `TERMINATED`. The migration comment says there is no `precedence_flag`. There is no amendment-mode column on `amendment_documents`. Companion: [`s1-v1-7-contract-documents.md`](../s1-v1-7-contract-documents.md).
- **S2 chain can carry a later precedence audit, and this note does not add an event.** S2 / DRG-580 appends hash-chained `amendment_document_events` (`seq_no`, `prev_hash`, `content_hash`) in the caller transaction: `AmendmentEventWriter`, table defined in the same V1_7 migration. ADR-035 authorizes that audit story. A precedence-change event type is not registered. S6 is not authorized to register one.
- **Apply stays after EXECUTED.** OQ2 is resolved. ADR-035 and the first-slice requirements keep a single `status`. New-contract mode does not move the apply gate.
- **Field-schema shape is provisional.** Section 2.2a sketches `precedence_flag` as boolean, not null, default false, and says boolean versus rank is open on DRG-592. That sketch is not a landed column.

### Forks

**F1. Flag shape.**

| Option | Meaning |
|---|---|
| A. Boolean | `precedence_flag` true means this contract takes precedence over the contract named by `prior_contract_id`. |
| B. Rank | An integer rank orders every overlapping active contract for the same customer. |
| C. Exclusive winner pointer | A single `prevailing_contract_id` lives on the customer, and the boolean column is not added. |

**Recommendation:** A. OQ27 already names one indexable column, `precedence_flag`, outside the thin header. Rank is only needed if more than two contracts must be ordered against each other. A customer-level pointer duplicates `prior_contract_id` and is not the column OQ27 named.

**Default:** boolean, not null, default false. Amend-in-place leaves it false. New-contract mode sets it true on the new contract only. False on every contract means the single active contract is the commercial source, which is the first-slice case.

**F2. Who may change the flag.**

| Option | Meaning |
|---|---|
| A. Write-once | Set when the new contract is created. A later change is a new executed amendment. |
| B. Product or admin flip | A privileged actor updates the column and writes an audit event. |
| C. Sales flip | The seller changes precedence without a new amendment. |

**Recommendation:** A. Precedence is a commercial fact. A silent flip changes which contract pricing and entitlement read.

**Default:** write-once at creation of the new contract. A change goes through a new amendment document that reaches `EXECUTED`. The S2 chain records that document. No actor flips the column in place. Who may draft that later amendment stays with the parked approval category [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591).

**F3. How overlapping active contracts resolve.**

| Option | Meaning |
|---|---|
| A. One rule for pricing, entitlement, and visibility | The contract with `precedence_flag` true is the commercial source. Both contracts stay visible. |
| B. Flag wins for pricing and entitlement; visibility is a separate policy | Visibility stays on [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595). |
| C. Reader-specific rules | Pricing, entitlement, and visibility each pick a contract by their own rule. |

**Recommendation:** A for pricing and entitlement, with both contracts visible as reference. Two true flags on one `prior_contract_id` chain is a failed read, not a silent pick.

**Default:** pricing and entitlement read the precedence-true contract. The original stays readable as reference. Zero true flags and one active contract: that contract is the source. Two true flags: the read fails closed. DRG-595 still owns who may see a contract. This note does not set that visibility policy.

**F4. Original contract at its own end date.**

| Option | Meaning |
|---|---|
| A. Follows its own end date | The amendment does not close it. `status` becomes `EXPIRED` when that end date passes. The row remains a reference. |
| B. Stays `ACTIVE` indefinitely | The original never expires. |
| C. Terminated at new-contract creation | The amendment closes the original. |

**Recommendation:** A. The call record already says the original is not closed by the amendment. The S1 check constraint already has `EXPIRED`. Option C contradicts the call record.

**Default:** new-contract creation leaves the original `ACTIVE` and open as a reference. Its own end date moves it to `EXPIRED` through the existing status set. No new status value. No amendment-driven `TERMINATED`.

**F5. Where the mode is stored.**

| Option | Meaning |
|---|---|
| A. Column on the amendment document | `amendment_mode` is `NEW_CONTRACT` or `AMEND_IN_PLACE`. |
| B. Inferred | Mode is inferred from whether a new `contracts` row was inserted. |
| C. LifecycleChange only | Quote-service stores the mode and CAM does not. |

**Recommendation:** A. ADR-035 places the document in CAM. The mode is a fact of that document. Inference breaks the moment a backfill or a merge also inserts a contract.

**Default:** the column arrives with the DRG-592 increment, not with S1. Until that increment, every document is amend-in-place. The first-slice API, which S3 has not started, does not gain this field from this note.

**F6. Which contract owns the amendment document.**

| Option | Meaning |
|---|---|
| A. The new contract owns the document | `amendment_documents.contract_id` is the new contract. `prior_contract_id` points at the original. |
| B. The original owns the document | The new contract is only a result row. |
| C. Both own a copy | Two documents, one per contract. |

**Recommendation:** A. Numbering is per contract (`{contract_number}-A{nnn}` at `EXECUTED`). One document has one `contract_id` in V1_7.

**Default:** one document on the new contract, `prior_contract_id` set, no second document on the original.

### Owner decision needed

Confirm or replace defaults F1–F6. OQ26 and OQ27 stay as already resolved on `main`. The decision that is still open is the flag shape, the writer, overlap resolution, the original's end-date behavior, and the document link.

### Dependencies

| Dependency | Why it is named |
|---|---|
| S1 / DRG-579, V1_7 | Header and `prior_contract_id` exist. The flag column does not. |
| S2 / DRG-580 | Audit chain exists. A precedence event type does not. |
| ADR-035 | Placement and the thin-header list stay. This increment adds a column beside that list. |
| DRG-581 (S3) | Mode on the document API waits until S3 is separately authorized. This note does not authorize it. |
| DRG-554 | Regrouping of OQ7 headers is a merge follow-up, not a second new-contract design. See section 2. |
| DRG-591 | Who may draft a later precedence-changing amendment. Parked. |

### What it unblocks

An owner confirmation of F1–F6 unblocks a later schema increment for `precedence_flag` and `amendment_mode`, and the V-rules for new-contract mode that the mid-term spec says are undefined. It does not unblock S3, S6, or a change to the S1 header. DRG-607 (per-cluster specs) can cite this frame. It cannot treat the frame as accepted.

---

## 2. DRG-554 — Co-termination, agreement merge, and the OQ7 regrouping follow-up

**Issue:** [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) (RA-20), Medium, Backlog. Relations read: DRG-591, DRG-461, DRG-607, DRG-585, DRG-547.

OQ7's thin default stays: one contract per existing subscription. This section is the regrouping follow-up. It does not rewrite the backfill.

### Problem

There is no accepted co-term policy and no accepted way to merge agreements. Multi-line and multi-agreement changes need a date rule and, separately, a way to fold more than one agreement into one survivor. S7 already created the one-contract-per-subscription shape. Subscriptions that commercially belong together stay on separate headers until a later merge. [DRG-461](https://linear.app/drgamtd-workspace/issue/DRG-461) (BC-07-04) is the existing amendments-and-co-termination item. Its acceptance criteria are unchecked and its delivery text has not been reverified. This frame extends that item. It does not open a second co-term implementation.

### Settled on main

- **Single-agreement mid-term end date.** Mid-term V5: amended lines co-term to the existing end date unless a co-term policy says otherwise. [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md). First-slice FR-11 repeats the existing-end rule. [`amendment-first-slice-requirements-and-acceptance.md`](../amendment-first-slice-requirements-and-acceptance.md).
- **A date column exists. A policy does not.** V1_7 `amendment_documents.co_term_date` is a date. There is no `CoTermPolicy` table and no `merged_from_ids` column.
- **OQ7 / S7 default is in force.** [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md) states the adopted wording: one thin contract per existing subscription, no synthetic history, no retroactive events, and a later merge tracked on DRG-554 is the place to regroup. The backfill functions do not regroup. `prior_contract_id` and `governing_agreement_ref` are null on those rows.
- **The co-term spec is a proposal.** [`specs/co-termination.md`](specs/co-termination.md) status is proposal only. It names modes `LatestEnd`, `EarliestEnd`, `None`, `AlignToAgreement`, and a proposed `AlignToDate`. Its open questions 1–9 are unanswered. Open question 6 leaves merge versus co-term unset. Open question 8 points the day-count at the amendment proration rule and forbids a third formula.
- **Renewal stub records a separate customer-level coterm note.** [`renewal-policy-stub.md`](../renewal-policy-stub.md) clarification: blended or rates-carry-forward, carry-forward as the named default, shared end date as the anchor, all-or-nothing per customer, optional renewal group. Approver deferred to DRG-591. That note is not an accepted policy.
- **Proration is the Accepted day-stub.** Co-term chooses a date. The charge for a partial period stays [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) D-01. This section does not add a formula.
- **DRG-461's `ContractAmended` event is not specified here.** S6 is not authorized. ADR-024 through ADR-027 stay Proposed. No event contract is added.

### Forks

**F1. Mode set.**

| Option | Meaning |
|---|---|
| A. DRG-554's four | `LatestEnd`, `EarliestEnd`, `None`, `AlignToAgreement`. |
| B. Four plus `AlignToDate` | An explicit anchor date. The co-term proposal adds this because a named anniversary is neither the latest nor the earliest existing end. |
| C. Customer-level renewal coterm as a second engine | The renewal-stub note (carry-forward, shared anchor, renewal group) is a separate policy with its own dates. |

**Recommendation:** B for the agreement date rule. The renewal-stub coterm supplies an anchor into this same rule. It is not a second engine.

**Default:** `AlignToDate` is in the proposed set. It is not accepted. A single-agreement mid-term amendment with no policy row still uses V5 (the existing end) and does not require a `CoTermPolicy`.

**F2. Missing policy.**

| Option | Meaning |
|---|---|
| A. Means `None` | Each line keeps its end. |
| B. Means `AlignToAgreement` | Every line takes the agreement end. |
| C. Split | Single-agreement amendment uses V5. A multi-agreement change with no mode is refused. |

**Recommendation:** C.

**Default:** C. V5 covers the first slice. Multi-agreement co-term waits for an explicit mode.

**F3. Which lines move.**

| Option | Meaning |
|---|---|
| A. Every line on the target agreements | Untouched lines move too. |
| B. Only lines in the change | A line the seller did not include keeps its end. |

**Recommendation:** B.

**Default:** B.

**F4. Shortening a term.**

| Option | Meaning |
|---|---|
| A. Allowed, and a credit is emitted here | Co-term calculates the credit. |
| B. Allowed as a date only | Credit, if any, is DRG-550. |
| C. Forbidden | `EarliestEnd` and an earlier anchor are rejected. |

**Recommendation:** B. The Accepted proration ADR prices the requested quantity forward and does not emit a credit. Section 5 owns credit.

**Default:** B. Shortening is allowed only when the seller chose `EarliestEnd` or an earlier anchor. The co-term policy writes the date and writes no credit.

**F5. Lengthening past the governing contract end.**

| Option | Meaning |
|---|---|
| A. Allowed as a mid-term amendment | The end date moves out. |
| B. Refused on a mid-term amendment | Lengthening is a Renew, or new-contract mode under DRG-592. |
| C. Allowed only as `AlignToDate` | An explicit anchor may pass the current end. |

**Recommendation:** B. The mid-term policy forbids term extension. V5 caps the amended line at the existing end unless a co-term policy says otherwise. This frame's proposed policy does not say otherwise for a date past the governing end.

**Default:** B. A mid-term co-term refuses an end after the governing contract end.

**F6. Merge versus co-term, including OQ7 regrouping.**

| Option | Meaning |
|---|---|
| A. Co-term only | Dates align. Agreements and S7 headers stay separate. No regrouping. |
| B. Later DRG-554 merge retargets subscriptions | After an executed merge, `subscriptions.contract_id` moves to the survivor. Source headers stay as references. `mergedFromIds` is written on the survivor. The S7 backfill is not rerun. |
| C. Regroup by DRG-592 new-contract mode | A new contract is the survivor. Backfilled headers stay put and are linked with `prior_contract_id`. |

**Recommendation:** B for the OQ7 follow-up, because [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md) already names DRG-554 as the regrouping place. Co-term (F1) still aligns dates across agreements that remain separate. Merge is a different intent. Option C stays available if the owner refuses to retarget `contract_id`. Using C would put regrouping on DRG-592, which the S7 text does not say.

**Default:** S7 rows stay one contract per subscription. The backfill is not regrouped. A merge intent is Proposed: it writes survivor identity, does not delete source rows, and does not run until the owner accepts a later change. The `contract_id` move itself is not specified by V1_8 and is not authorized here. [`oq7-header-backfill-owner-decision.md`](oq7-header-backfill-owner-decision.md) already records that no opened text specifies that move.

**F7. Source agreement after merge.**

| Option | Meaning |
|---|---|
| A. Stays active as a reference until a later executed close | Matches the DRG-592 original-stays-open record. |
| B. Cancelled when `mergedFromIds` is written | Commercial close is a side effect of the pointer. |
| C. Deleted | Source row is removed. |

**Recommendation:** A.

**Default:** A. No delete. No automatic `TERMINATED`.

**F8. Home for the work relative to DRG-461.**

| Option | Meaning |
|---|---|
| A. DRG-461 remains the capability item | This frame is the policy. Implementation links to DRG-461. |
| B. DRG-554 replaces DRG-461 | BC-07-04 is closed as duplicate. |

**Recommendation:** A. The DRG-554 description says extend or link rather than duplicate.

**Default:** A. DRG-461's `ContractAmended` event is not adopted. S6 remains unauthorized.

### Owner decision needed

Confirm or replace F1–F8. The OQ7 one-contract-per-subscription default is not one of the open choices. F6's default leaves that default in place and keeps regrouping as a later, unaccepted merge.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-461 | Existing BC-07-04 home. Link, do not fork a second model. |
| DRG-585 / S7 | Headers exist as one per subscription. Regrouping waits on this frame's F6. |
| [`specs/co-termination.md`](specs/co-termination.md) | Proposal this frame does not accept. `AlignToDate` and the merge questions come from its open list. |
| [`specs/catalog-model.md`](specs/catalog-model.md) | Proposed `coTermable` flag. The catalog model is not accepted. A missing flag is not a refusal until that model is accepted. |
| DRG-576 | Product relationships and eligibility. Backlog. |
| DRG-591 | Approver for customer-level coterm. Parked. |
| Accepted proration ADR | Day-count when the anchor falls mid-period. Not reopened. |
| DRG-550 | Credit if a shortened term should pay one. Section 5. Default there is no credit. |

### What it unblocks

An owner confirmation unblocks a later co-term policy and a later merge design for OQ7 headers. It does not unblock a rewrite of V1_8, an S3 change, an S6 event, or DRG-461's unchecked acceptance criteria. Renewal RN1 in [`renewal-policy-spec.md`](renewal-policy-spec.md) already defers a co-term anchor to DRG-554. That deferral stays.

---

## 3. DRG-594 — Legal-review-free amendments

**Issue:** [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (RA-60), High, Backlog. Relations read: DRG-560, DRG-552, DRG-591, DRG-581, DRG-607, DRG-601.

### Problem

The call record says an amendment skips legal review only when it changes pricing, term, or seats, and nothing else. Clauses, products, special terms, and the governing agreement need legal review before execution. The first slice has no legal-review gate. Add and Remove of a product line are outside the free set unless product treats them as seats. General approvals (who approves, thresholds) stay parked on DRG-591 and [DRG-601](https://linear.app/drgamtd-workspace/issue/DRG-601).

### Settled on main

- **The free set is recorded and is not an ADR.** [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) clarification 2026-10-16: only pricing, term, or seats are legal-review-free. Add or Remove of a product line is outside that set unless product treats it as seats. That sentence is the open product question.
- **First-slice signature cut has no legal gate.** Signature policies in slice: `NOT_REQUIRED`, `NOTICE_ONLY`, `CLICK_THROUGH`, and those auto-execute after `APPROVED` (FR-08). `WET` and `E_SIGN` exist on the V1_7 check constraint and are rejected for the slice (`SIGNATURE_NOT_SUPPORTED_IN_SLICE` in the S3 criteria). S3 has not started, so that rejection is not a running gate.
- **Line vocabulary the classifier can read is already stored.** V1_7 `chk_amendment_line_action` allows `ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE`. [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) is a proposal and is not the authority for this classifier.
- **State machine has no legal-review state.** Spec section 3: `DRAFT` to `IN_REVIEW` when priced and V1–V12 pass. No legal-review transition.
- **OQ6 voids approvals on a pre-execution revision.** [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md). A classification bound to a digest does not survive onto the next revision. OQ18 stays Proposed. S3 stays unauthorized.
- **Partial apply is a different question.** OQ5 option A is adopted for the first slice in [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md). That adoption does not accept [`specs/partial-apply.md`](specs/partial-apply.md) and does not authorize S4 or S6. A legal-review hold is not a `BLOCKED` unit.

### Forks

**F1. Seats versus Add and Remove.**

| Option | Meaning |
|---|---|
| A. Seats are a same-SKU quantity Update | `UPDATE` of quantity, price, or term on the existing SKU is free. `ADD` of a SKU and `REMOVE` of a product line require review. |
| B. Same-SKU Add and Remove count as seats | Adding or removing seats of the SKU already on the contract is free. A different SKU requires review. |
| C. Any quantity change is free | Includes a new SKU. |

**Recommendation:** A. The call record says adding or removing a product line is outside the free set unless product treats it as seats. Treating a product add as seats blurs that sentence. A same-SKU quantity change is the seat change.

**Default:** A. First-slice Add and Remove of SaaS lines require legal review under this proposed default. The first-slice text is not edited by this note, and S3 does not gain the gate.

**F2. Where the gate sits.**

| Option | Meaning |
|---|---|
| A. Guard on `DRAFT` to `IN_REVIEW` | Legal-review-required cannot leave `DRAFT` until a legal-review record exists. No new state. |
| B. New `LEGAL_REVIEW` state | Inserted before `APPROVED`. |
| C. Allowed into `IN_REVIEW`, blocked before `APPROVED` | Review is an approval task, specified by DRG-591. |

**Recommendation:** A. The ticket asks for a gate on `DRAFT` to `IN_REVIEW` and a V-rule. A new state is a state-machine change this note does not accept. Option C pulls parked approval design forward.

**Default:** A. The guard is Proposed. It is not added to S3.

**F3. Override.**

| Option | Meaning |
|---|---|
| A. No override | A required document stays in `DRAFT` until the classification changes. |
| B. A named legal role may override | The S2 event records the classification and the actor. Sales cannot override. |
| C. Sales may override | The seller attests and continues. |

**Recommendation:** B, with the role left unnamed until DRG-591 is unparked.

**Default:** no override path exists until that role is named. Until then the guard refuses. Sales cannot override.

**F4. Mixed documents and header edits.**

| Option | Meaning |
|---|---|
| A. Whole document | One line or header edit outside the free set makes the document require review. |
| B. Per line | Free lines skip review and other lines wait. |
| C. Header term and price are free; governing agreement, clauses, and special terms require review | Combined with A for lines. |

**Recommendation:** A, using C's field split. Free header fields are price and term. `governing_agreement_ref`, clauses, and special terms require review. Product identity on a line requires review.

**Default:** A. The document is legal-review-required if any edit is outside the free set. Lines are not split across legal outcomes. That is distinct from OQ5 partial apply.

**F5. First-slice timing.**

| Option | Meaning |
|---|---|
| A. Gate enters before S3 starts | The first-slice auto-execute cut changes. |
| B. Gate is the next increment | S1–S8 keep the current signature cut. The gate lands with DRG-594 after the owner accepts it. |

**Recommendation:** B. Same sequencing as new-contract mode. This note does not rewrite FR-08.

**Default:** B.

### Owner decision needed

Confirm or replace F1–F5. F1 is the product question the ticket leaves open: whether SaaS Add and Remove count as seats. The proposed default is that they do not.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-552 | Action-code vocabulary. The V1_7 check constraint already stores a set. [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) is not accepted. |
| DRG-581 (S3) | The guard would live on the state machine S3 has not started. |
| DRG-580 (S2) | Audit of classification and override uses the existing chain. No new event type is registered here. |
| DRG-591, DRG-601, DRG-560 | Approval design stays parked. This section defines the bypass set only. |
| OQ6 record | A revision returns the document to `DRAFT` and voids the prior classification. |

### What it unblocks

An owner confirmation unblocks a later V-rule and a later guard on the S3 machine. It does not start S3, does not unpark DRG-591, and does not change first-slice auto-execute. DRG-537's UI spec already leaves this gate off the draft create body. That exclusion stays.

---

## 4. DRG-599 — Amendment SKU mapping table

**Issue:** [DRG-599](https://linear.app/drgamtd-workspace/issue/DRG-599) (RA-65), High, Backlog. Relations read: DRG-565, DRG-576, DRG-591, DRG-590, DRG-607.

### Problem

Upgrade, swap, and replacement need a mapping from a SKU on the original contract to the SKU or SKUs on the amendment. The call record gives that table to the product team. Sales may dispute a mapping. A contested mapping holds the amendment: execution and apply wait until product resolves the dispute. First-slice Add, Update, and Remove on the same SKU do not use a mapping. Conditional rules on top of a mapping stay in Drools and stay parked on [DRG-590](https://linear.app/drgamtd-workspace/issue/DRG-590).

### Settled on main

- **No product-to-product relationship is landed.** [`specs/catalog-model.md`](specs/catalog-model.md) records the product-service shape: unique `sku`, `ProductType`, status `DRAFT` / `ACTIVE` / `DEPRECATED` / `RETIRED`, and no relationship row. That file is a proposal. It does not accept a model.
- **DRG-576 is the relationship story.** It asks for effective-dated `ProductRelationship` types `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, `ADD_ON_OF`. The catalog proposal says the dispute-and-hold flow is DRG-599 and is out of that slice. Its open question 8 leaves publish rights as DRG-599's subject and assumes product-service writes and quote-service reads.
- **First slice excludes swaps.** Requirements file: Swap, Upgrade, and Downgrade wait for DRG-576. V1_7 still allows those action codes in the check constraint. Allowing the token is not a mapping table.
- **Hold is not partial apply.** [`specs/partial-apply.md`](specs/partial-apply.md) lists SKU-mapping holds as out of that slice. OQ5 option A adopts unit status `BLOCKED` for a failed apply unit. A contested mapping holds the document before execution. It is not a partial-apply outcome.
- **Executed documents stay immutable.** OQ6. A dispute does not rewrite an executed document.
- **DRG-609 is not the validator.** The pricing-service design is not accepted. A mapping read is not routed through that design.

### Forks

**F1. Where the mapping lives.**

| Option | Meaning |
|---|---|
| A. Relationship types on the DRG-576 model | One effective-dated row. Dispute status points at that version. No second table. |
| B. A separate amendment SKU mapping table | Source SKU, target SKUs, effective dates, version, owned by product, beside the catalog relationship. |
| C. Both | Catalog relationship authorizes the transition. The mapping table is a commercial overlay sales can dispute. |

**Recommendation:** A. Two tables that both say "A upgrades to B" will diverge. The ticket allows either home. The catalog proposal already reserved the hold for DRG-599 without accepting the model.

**Default:** A, still Proposed. This note does not accept [`specs/catalog-model.md`](specs/catalog-model.md). If the owner wants sales to dispute a commercial overlay without unpublishing the catalog row, C is the alternative, and the overlay is the row the hold reads.

**F2. Dispute status and who resolves it.**

| Option | Meaning |
|---|---|
| A. `Active`, `Contested`, `Resolved`. Product resolves. Sales may open a dispute and may not clear it. | The ticket's set. |
| B. Add `Rejected` | Product can refuse the mapping. The amendment stays held. |
| C. Sales resolves | The disputing seller clears the hold. |

**Recommendation:** A. Option B can be added later without changing the hold. Option C contradicts the call record.

**Default:** A. No SLA and no notification sender are chosen. The hold does not wait on a notification. DRG-591 stays parked for the wider approval design.

**F3. Where the hold bites.**

| Option | Meaning |
|---|---|
| A. Before `IN_REVIEW` and again before apply | A mapping contested after approval cannot execute or apply. The whole document waits. |
| B. Apply only | The document can execute while contested. |
| C. Per line, partial apply | Contested lines become `BLOCKED` units and other lines apply. |

**Recommendation:** A. The ticket says the amendment cannot proceed to execution or apply while any line uses a contested mapping. Option C collapses this into DRG-565.

**Default:** A. Document-level hold. No unit commits. Distinct from OQ5. S3 and S4 are not authorized to implement it.

**F4. Which lines require a mapping.**

| Option | Meaning |
|---|---|
| A. Only when the source SKU and the target SKU differ | Same-SKU Add, Update, and Remove need no row. |
| B. Every line | Including same-SKU quantity edits. |

**Recommendation:** A. The ticket says first-slice same-SKU lines do not use mappings.

**Default:** A.

**F5. Pin versus live row.**

| Option | Meaning |
|---|---|
| A. Pin the relationship version on the line at pricing | A later contest holds documents that pinned that version and are not yet executed. |
| B. Read the live row at apply | A mapping edit after pricing changes the executed meaning. |

**Recommendation:** A. It matches the catalog proposal's pin (its AC-4) without accepting that proposal. OQ6 keeps an executed document immutable, so a contest does not reopen it.

**Default:** A.

### Owner decision needed

Confirm or replace F1–F5. F1 is the table-versus-relationship choice the ticket names. The proposed default is one catalog relationship, product-owned, with dispute status on that version.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-576 and [`specs/catalog-model.md`](specs/catalog-model.md) | Proposed relationship types. Not accepted. |
| DRG-421 | Catalog versions, named by DRG-576 as the pin. Not landed as that pin. |
| DRG-552 | Action codes a relationship type must match. |
| DRG-565 / OQ5 | Partial apply stays a different hold. |
| DRG-590 | Conditional rules on top of a mapping. Parked. |
| DRG-591 | Who resolves, beyond "product team". Parked for the general design. Product resolution is the proposed default here. |
| DRG-609 | Not accepted. Mapping validation does not wait on that design and does not start it. |

### What it unblocks

An owner confirmation unblocks a later relationship-and-dispute design inside DRG-576's proposed model. It does not unblock catalog implementation, S3, S4, S6, or Drools rule work. Same-SKU first-slice lines stay mapping-free.

---

## 5. DRG-550 — Price policy, credit, and termination fee

**Issue:** [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) (RA-16), Medium, Backlog. Relations read: DRG-583, DRG-589, DRG-542, DRG-534, DRG-603, DRG-572, DRG-564, DRG-547.

The issue title and description still name proration. The formula is already Accepted. This section does not reopen it. The forks are the price mode, the credit policy, and the termination fee.

### Problem

Continuation today keeps the contracted unit rate and prorates the stub. There is no accepted choice among retain, list, and override. There is no accepted credit policy and no accepted termination fee. Those are needed for downgrade and cancel-style amendment lines. [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542) and [DRG-603](https://linear.app/drgamtd-workspace/issue/DRG-603) are Done. They closed the formula conflict. They did not close this policy set.

### Settled on main

- **Formula.** [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) is Accepted. D-01 is the day-stub in `QuoteContinuationPolicy.plan`. D-02 keeps that method as the only implementation. D-04 says DRG-550 may add an amendment price policy (`RetainContracted`, `List`, `Override`), a credit policy, and a termination fee. The current continuation already retains the contracted unit rate. A credit for unused days of the old quantity, and a termination fee, are this story. The formula prices the requested quantity forward and does not emit that credit. A credit is not implemented by turning `rule-007` back on. `None` and `NextCycle`, if added, are switches around the accepted method. They are not a second formula. `MonthDay` in the mid-term spec's mode list is not reopened as a formula.
- **Money columns exist. Policies do not.** V1_7 stores `credit_total` and `tcv_delta` on the document. It does not store a price mode, a credit policy, or a fee policy.
- **DRG-609 is not accepted.** Moving evaluation into pricing-service, if a later story does that, moves this method. It does not reimplement it. The DRG-609 note does not authorize that move. S5 stays blocked.
- **Audit of the decision is a different story.** [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) and [`story-drg-564-policy-decision-audit-trail.md`](story-drg-564-policy-decision-audit-trail.md). This note does not accept that trail.
- **Customer cancellation refunds stay parked.** [DRG-589](https://linear.app/drgamtd-workspace/issue/DRG-589). This section does not unpark them.
- **Ramp amendments stay on DRG-534.** Elapsed periods, future periods, and re-governance are that story. This section does not decide them.
- **Mid-term OQ10 stays open.** Whether a downsell is permitted before a minimum commitment ends, and which credit or fee applies, is still an open question on the mid-term spec. The defaults below do not close OQ10.

### Forks

**F1. Price mode.**

| Option | Meaning |
|---|---|
| A. `RetainContracted` only | Today's continuation behavior. List and override are refused. |
| B. `RetainContracted`, `List`, `Override` | The set D-04 names. Missing mode means `RetainContracted`. |
| C. Add flat-adjustment as a fourth mode | A price delta that is not retain, list, or override. |

**Recommendation:** B. Flat adjustment is an `Override` or a delta recorded on top of `RetainContracted`. It is not a fourth formula. "Prorated delta" in the mid-term clarification means the Accepted stub applied to the requested quantity.

**Default:** B. The line defaults to `RetainContracted`. `List` and `Override` are explicit. `Override` still has to satisfy mid-term V14 (actor, reason, value) when that rule is implemented. This note does not implement V14.

**F2. Credit policy.**

| Option | Meaning |
|---|---|
| A. `NoCredit` | No credit line. Matches current continuation. |
| B. `ProratedUnused` | A separate credit amount for the removed quantity, computed by calling the Accepted day-stub method. Not a new formula. |
| C. `FullRemaining` | Credit the unused term in full, without the stub fraction. |
| D. Seller picks per line, no tenant default | Every downgrade states a policy or is refused. |

**Recommendation:** the set is A, B, and C. The default value is A. B calls the Accepted method and does not restore the full-term ratio. C is a commercial choice, not a proration formula. D forces a choice the first slice does not have a field for.

**Default:** `NoCredit`. A shortened co-term (section 2, F4) also emits no credit until the owner selects B or C. Mid-term OQ10 stays open.

**F3. Termination fee.**

| Option | Meaning |
|---|---|
| A. `NoFee` | No fee. |
| B. Flat amount | A configured amount, snapshotted on the decision. |
| C. Percent of remaining contract value | A configured percent. The remaining value uses the Accepted forward charge, not a new day-count. |
| D. Cut-off window | A fee applies only before a named date. |

**Recommendation:** A as the default, with B and C as configured values the owner can turn on later. D is a further policy and is not in the proposed default set.

**Default:** `NoFee`. A fee is not folded into the proration method. DRG-589 stays parked. This fee, when later configured, is the amendment downgrade or early-end fee only.

**F4. Where the three policies are stored.**

| Option | Meaning |
|---|---|
| A. Tenant default, per-line override, snapshotted when priced | The snapshot is what apply reads. |
| B. Per document only | Every line on the document shares one mode, one credit policy, and one fee. |
| C. Pricing-service owns the policy store | Requires the unaccepted DRG-609 design. |

**Recommendation:** A. Apply re-reads the snapshot. It does not re-resolve the tenant default. C waits on a design that is not accepted.

**Default:** A. The snapshot names the price mode, the credit policy, and the fee policy. No pricing-service contract is added. DRG-564 remains the audit-trail story.

**F5. Switches around the stub.**

| Option | Meaning |
|---|---|
| A. No extra switches | Every partial period uses the Accepted stub. |
| B. `None` and `NextCycle` as switches | `None` skips the stub. `NextCycle` starts at the next billing boundary. The method underneath stays D-01. |

**Recommendation:** B, because D-04 already says those two, if added, are switches around the accepted method.

**Default:** the switch is unset, so the Accepted stub runs. Adding the switch is a later change. It is not a new formula and it is not a reason to edit the proration ADR.

### Owner decision needed

Confirm or replace F1–F5. The proration formula is not on that list.

### Dependencies

| Dependency | Why it is named |
|---|---|
| Accepted proration ADR, D-04 | Bounds this section. Credit and fee sit around the method. |
| DRG-542, DRG-603 | Formula conflict is Done. Not reopened. |
| DRG-609, DRG-583 (S5) | Pricing-service line pricing and PolicyDecision identity. Design not accepted. S5 blocked. |
| DRG-564 | Audit trail for the snapshot. Separate note. Not accepted by this frame. |
| DRG-534 | Ramp lock and re-governance. Out of this section. |
| DRG-572 | One-time and retroactive charge lines. Out of this section. |
| DRG-589 | Parked customer-cancellation refunds. Not unparked. |
| DRG-554 | Date choice when a co-term shortens a line. Credit stays `NoCredit` under F2's default. |
| Mid-term OQ10 | Minimum-commitment downsell. Still open. |

### What it unblocks

An owner confirmation unblocks a later policy model for price mode, credit, and termination fee around the Accepted stub. It does not unblock a formula change, a Drools edit, a pricing-service migration, S5, or S6. DRG-609 stays a proposal.

---

## What an owner confirmation would still not do

Confirmation of any default in this note:

- accepts no ADR and edits no ADR;
- leaves ADR-017 and ADR-024 through ADR-027 Proposed;
- leaves the proration ADR Accepted and unchanged;
- does not start S3 / DRG-581;
- does not authorize S6 / DRG-584;
- does not accept the DRG-609 design;
- does not change the OQ7 one-contract-per-subscription default;
- authorizes no code, migration, flag, or deploy.
