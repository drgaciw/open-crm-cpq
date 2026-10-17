# DRG-560 + DRG-594 — Amendment governance routing (decision frame)

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-10-17
**Base:** `main` at `63e6ec8d` (merge of [#663](https://github.com/drgaciw/open-crm-cpq/pull/663)).
**Project:** OPEN-CRM-CPQ, Linear team DRG, milestone Renewals & Amendments.
**Stories:** [DRG-560](https://linear.app/drgamtd-workspace/issue/DRG-560) (RA-26), [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (RA-60).

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No flag is enabled. Nothing is deployed. Accepting this authorizes no code, migration, or deploy. DRG-560 and DRG-594 stay In Review until the owner merges. No backfill runs. No apply runs.

DRG-560 and DRG-594 stay In Review. Linear text read 2026-10-17: DRG-560 says continuation changes have no approval step or revision handling, names QuoteWerks and Chargebee as research, and suggests routing lifecycle changes through the existing deal-governance engine, related to [DRG-443](https://linear.app/drgamtd-workspace/issue/DRG-443). Relations read: DRG-535, DRG-443, DRG-594, DRG-562, DRG-547. DRG-594 limits legal-review-free changes to pricing, term, or seats, and asks for a `DRAFT` to `IN_REVIEW` gate, a V-rule, and an audit of the classification and any override. Relations read: DRG-560, DRG-552, DRG-591, DRG-581, DRG-607, DRG-601. DRG-443 was Urgent, Backlog on that read. DRG-591 was Backlog and parked. Those other statuses are the Linear read. They are not git facts.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or migration named in the same sentence was opened on this `main`. |
| `unverified` | Not opened, not executed, or not queried. A search that found no row is stated as a search, not as a live database result. |

No database was queried. Whether Flyway has applied any migration in an environment is `unverified`. No service was started. No request was sent.

## OQ6 text, quoted

Source file on this `main`: [`docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) (#642, with the #663 header now on that file). The paragraphs below are copied from that file. They are not paraphrased.

Current status and current decision:

> **Status:** OQ6 remains adopted under delegation; its boundary contracts and OQ18 are decided under the owner's renewed delegation on 2026-10-17. See [the delegated record](delegated-renewals-amendments-decisions-2026-10-17.md). No implementation story is authorized here.

> The pre-execution revision rule, stable provisional number, terminal refusal, executed immutability and at-most-one executed revision remain as recorded below. The [CAM contracts](delegated-renewals-amendments-decisions-2026-10-17.md#cam-transactions-and-delivery) now select the CAM API/version/idempotency behavior, audit types, local concurrency criterion and durable S5 delivery. The [storage/digest contract](delegated-renewals-amendments-decisions-2026-10-17.md#storage-and-digests) now selects nullable unpriced values, a CAM-owned immutable envelope store and genuine close-time hashing. AC-S3-9..11, AC-S5-7 and AC-S8-6 are adopted criteria, with execution evidence still required.

> OQ18 is confirmed under delegation: one shared utility in `backend/libs/common`, delivered with S3, reused without forks by S5/S6, using the versioned commercial envelope and canonicalization contract in the linked record. The old owner-confirmation gate is satisfied by this decision. S3's separate implementation-authorization gate remains.

The same file says the older material is historical:

> The material below preserves the original reading, delegation evidence and rationale. References to unconfirmed OQ18 or open boundary contracts describe that earlier state and are superseded by the current decision above.

Historical rule, still the revision rule the current decision keeps:

> 1. **Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`).** A new revision of the LifecycleChange opens a new document revision on the same document, with the same `provisionalNumber` (OQ4). The previous revision closes with `status_at_close = SUPERSEDED` and keeps its content digest. The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again. The persisted representation of an unpriced revision is not decided here.

> 2. **Executed, in any apply state (`EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`).** The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED` and an audit event, which is AC-S3-5 applied to revisions. "Executed but not yet applied" gets no special path. The remedy is a new LifecycleChange that produces a new, correcting amendment document, the same remedy OQ5 option A adopted for a blocked unit.

Historical sentences in that same section, superseded where the current decision and [#663](https://github.com/drgaciw/open-crm-cpq/pull/663) select a contract:

> The delegated ruling does not choose a supersession event type, payload/schema, topic, or outbox contract. Event requirements in draft acceptance criteria remain proposals and do not authorize an event contract or S6 work.

> - It does not authorize S3, S4, S5, S6, S7 or S8. S3 / DRG-581 needs separate owner authorization.
> - It does not change OQ5 (adopted for the first slice), OQ7 (still `DEFAULT PROPOSED` in the register) or any ADR status.

OQ5 option A stays the first-slice partial-apply rule (`read` register OQ5). The thin contract stays one thin contract per existing subscription (`read` register OQ7 on this `main`: **ADOPTED, header-only first slice**). This note does not regroup. S3 / DRG-581 stays unlaunched. S6 is not authorized.

## Adopted constraints this note does not reopen

[#663](https://github.com/drgaciw/open-crm-cpq/pull/663) [`delegated-renewals-amendments-decisions-2026-10-17.md`](delegated-renewals-amendments-decisions-2026-10-17.md) adopts the baseline defaults of [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) [`cluster-a-amendment-modes-and-policy-decision-frame.md`](cluster-a-amendment-modes-and-policy-decision-frame.md) for 594-F1 through 594-F5 (`read` fork index). The pre-decision words "not adopted" and "owner option" in that frame are historical. The substantive choices in force are:

| Fork | Substantive choice this note uses |
|---|---|
| 594-F1 | A same-SKU quantity `UPDATE` is seats. `ADD` of a SKU and `REMOVE` of a product line require review. |
| 594-F2 | A guard on `DRAFT` to `IN_REVIEW`. No new state. |
| 594-F3 | A named legal role may override. Until that role is named the guard refuses. Sales cannot override. |
| 594-F4 | One edit outside the free set makes the whole document require review. Free header fields are price and term. |
| 594-F5 | The gate is a later increment. S1–S8 keep the current signature cut. |

`read` the #663 catalog paragraph: "Legal-review classification/gates are a later increment; until its named legal permission is delivered, the override path refuses. Sales cannot override."

`read` #663 D-STORAGE: "Clear old policy-decision identities, trace and approval/signature references on the new revision as required by OQ6." A future additive CAM migration uses the next free `V1_10` or later. `V1_9` stays reserved for S4. Existing migrations are immutable.

`read` #663 D-SNAPSHOT: approvals and signatures are excluded from the hashed commercial projection. "Approval/signature evidence binds to the resulting digest separately."

`read` #663 D-RACE: revision, submit, approval mutation, priced-result installation, and execute serialize on the CAM document row. Execution verifies the digest and the approval binding under that lock. Success `AMENDMENT_SUPERSEDED` and refusal `AMENDMENT_REVISION_REJECTED` are local audit types. "No new S6 domain-event topic/schema is adopted here."

`read` #663 D-PRICED-WRITE: installing a priced result "invalidates any prior approval binding" and does not execute. Repricing an `IN_REVIEW` or `APPROVED` document first opens a new revision under OQ6.

`read` #663 migration-and-access paragraph: "No new Entra role or approval/execute permission is silently granted."

`read` #663 pricing paragraph: before commercial use, pricing-service confirms one immutable versioned commercial-decision snapshot per priced continuation line in a pricing-owned append-only store. That paragraph "replaces the overlapping ownership/handoff question without accepting all DRG-564/612 details." [#643](https://github.com/drgaciw/open-crm-cpq/pull/643) [`story-drg-564-policy-decision-audit-trail.md`](story-drg-564-policy-decision-audit-trail.md) stays a proposal. This note does not accept it and does not put approval columns on its sketch.

Entitlements and conversion price stay pending DRG-609 record merge. `read` [`story-drg-609-pricing-service-continuation-design.md`](story-drg-609-pricing-service-continuation-design.md): the note is a design proposal and is not an ADR. #663 does not take that older proposal wholesale.

Uplift precedence stays the Accepted record [`adr-renewal-uplift-basis-and-precedence.md`](adr-renewal-uplift-basis-and-precedence.md) D-04 (`read`): contract clause, then line, then agreement, then account, then the rules default. Tokens: CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES. This note does not reopen that order.

The proration record [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) stays Accepted (`read` its status line). This note does not restate the formula and does not reopen it.

ADR-017 and ADR-024 through ADR-027 stay Proposed (`read` [`docs/adr/README.md`](../../adr/README.md) status column for those four numbers and ADR-017). They are not prerequisites. #663 does not accept an ADR-026 command topic.

OQ17 stays **DEFAULT PROPOSED** (`read` register): approval only for overrides, backdating, or total ARR delta above a configurable threshold. The ARR delta threshold is a Finance value that stays owner-set. This note invents no number. OQ25 stays parked on DRG-591 (`read` register). DRG-601 [`approval-requirements-skeleton.md`](../approval-requirements-skeleton.md) stays a skeleton (`read` its header: "SKELETON. NOT DECIDED."). This note does not unpark either story.

[#648](https://github.com/drgaciw/open-crm-cpq/pull/648) through [#660](https://github.com/drgaciw/open-crm-cpq/pull/660) are on this `main`. Where #663 adopts one of their defaults, this note cites that adoption. It does not replace it.

## Owner-decision table

Every row is Proposed, unaccepted, owner decision required. The default is the recommendation. The alternatives are the other options in that fork.

| Fork | Question | Default | Alternatives |
|---|---|---|---|
| G1 Route home | Where an amendment approval is created | One `CONTRACT_MODIFICATION` row in the existing approval-routing service. No second engine, no `InApproval` state, no `supersedesChangeId` | `QUOTE` / `GOVERNANCE` as `QuoteApprovalTierMapper` maps them; the DRG-601 chain tables; a new LifecycleChange state and `supersedesChangeId` |
| G2 When it fires | Which edits open that row | Proposed, unaccepted, owner decision required. OQ17, unchanged: override, backdating, or total ARR delta above the threshold. The ARR delta threshold is a Finance value that stays owner-set. This note invents no number. No row otherwise | Every submit opens a row; a `SYSTEM` approved row below the threshold |
| G3 Who decides | Which person may approve, and who may not | A person who is not the requester and whose existing role satisfies the tier. Override uses `SALES_VP`. Backdating or ARR-delta-only uses `DEAL_DESK`. No new Entra role. Legal stays the adopted refuse-until-named path | `CFO` on every amendment; the seller decides their own request; this note names and grants a legal role |
| G4 Legal beside commercial | How the adopted DRG-594 guard meets the commercial row | Legal stays the adopted `DRAFT` to `IN_REVIEW` guard and is not an approval row. The commercial row, when G2 fires, blocks `IN_REVIEW` to `APPROVED`. Both are later than S1–S8 | A second `TERMS` row that stops before `APPROVED`; one combined row for legal and commercial |
| G5 Fields | Which stored fields the route uses | Existing columns only. `approval_ref` holds the request id when a row exists and is null when G2 does not fire. `line_id` is null | A new document-id column before any route; leave `approval_ref` empty and keep the id only on the quote |
| G6 Void on revision | How a voided approval is recorded | Proposed, unaccepted, owner decision required. Clear `approval_ref` on a pre-execution revision. `read` [`docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md`](delegated-renewals-amendments-decisions-2026-10-17.md) D-STORAGE, and `read` [`backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalStatus.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalStatus.java). Map that pending row to `SUPERSEDED`. `read` the same #663 file and `read` the same `ApprovalStatus` enum. An `APPROVED` row is not updated | `cancel()` the pending row; add a `VOIDED` status |
| G7 Audit entries | Which records hold the commercial decision and the price snapshot | Proposed, unaccepted, owner decision required. The approval row is the commercial decision. The price snapshot stays the #663 pricing-owned store. The classification event is not decided in this row | Classification only in `reason`; approval columns on the DRG-564 sketch; a new CAM classification table |
| G9 Classification event | Later local classification event whose type is not chosen | OPEN FORK. Proposed, unaccepted, owner decision required. No event name. No schema | A later decision may select a type and a payload. This row names neither and writes no schema. An S6 topic is not a choice here |
| G8 Uplift and discount | What is not an approval route | Precedence CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES is not a route. A resolved uplift is not a trigger. A manual override is the OQ17 override. The discount Kafka path stays `DISCOUNT` | Every non-zero uplift opens `GOVERNANCE`; route overrides as `DISCOUNT`; return no price until an approval row exists |

## Already on main

- **Document approval pin.** `read` [`V1_7__contract_documents.sql`](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql) `CREATE TABLE amendment_documents`: `approval_ref varchar(255)`, `content_digest varchar(64) NOT NULL`, `status varchar(40) NOT NULL`, `current_revision_no integer NOT NULL`, `lifecycle_change_id uuid NOT NULL`. `read` [`AmendmentDocument.java`](../../../backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/AmendmentDocument.java) column `approval_ref`. No Java writer of that column was opened (`unverified` as a call path).
- **No legal-review status.** `read` `chk_amendment_document_status` in the same migration, as listed by [`cluster-e-lifecyclechange-model-decision-frame.md`](cluster-e-lifecyclechange-model-decision-frame.md): `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID`. `LEGAL_REVIEW` is not in that list.
- **Line policy pins, not an approval.** `read` the same migration, `amendment_document_lines`: `policy_id`, `policy_version`, `policy_decision_id`, `pricing_trace_id`. The check requires all four together or all four null.
- **Action tokens.** `read` `chk_amendment_line_action` in the same migration: `ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE`. `read` register OQ12: the slice actions are Add, Update, Remove.
- **Signature cut.** `read` register OQ10: `NOT_REQUIRED`, `NOTICE_ONLY`, `CLICK_THROUGH`. `read` `chk_amendment_document_signature` in `V1_7__contract_documents.sql` also allows `WET` and `E_SIGN`. 594-F5 keeps that cut.
- **CAM migrations present.** `read` the CAM migration directory: `V1_7__contract_documents.sql`, `V1_7_1__durable_renewal_eligibility_publications.sql`, `V1_7_2__freeze_amendment_line_membership.sql`, `V1_8__contract_header_backfill.sql`. No `V1_9` file is in that directory. No `V1_7_3` file is in that directory. `V1_9` stays reserved for S4 `document_apply_ledger` (`read` [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md) S4 row). Any later CAM migration is `V1_10` or higher, next free at build time, with a rollback statement. Never `V1_7_3`. This note writes none.
- **Approval types.** `read` [`ApprovalType.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalType.java): `DISCOUNT`, `MARGIN`, `TERMS`, `PRICING_EXCEPTION`, `CONTRACT_MODIFICATION`, `QUOTE`, `GOVERNANCE`. `read` [`V1_1__create_approval_tables.sql`](../../../backend/services/approval-routing/src/main/resources/db/migration/V1_1__create_approval_tables.sql) and [`V1_2__add_quote_governance_approval_types.sql`](../../../backend/services/approval-routing/src/main/resources/db/migration/V1_2__add_quote_governance_approval_types.sql) for the check that allows those tokens. A search of `backend/**/*.java` for `CONTRACT_MODIFICATION` found only `ApprovalType.java`. No production writer assigns it. Quote-service call sites were not opened beyond that search (`unverified` as a second pass).
- **Who may decide today.** `read` [`ApprovalAuthority.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/service/ApprovalAuthority.java): `DEAL_DESK` by DealDesk, SalesVP, or CFO; `SALES_VP` by SalesVP or CFO; `CFO` by CFO; an unknown tier by no one. `Admin` and `CPQ.User` decide nothing. `read` [`ApprovalService.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/service/ApprovalService.java) `authorizeDecision`: a service token is refused (`APPROVER_MUST_BE_A_USER`); a role that misses the tier is refused (`APPROVER_ROLE_REQUIRED`); the requester is refused (`SELF_APPROVAL`). The method was read and not executed (`unverified` as runtime).
- **BFF mapping is not `CONTRACT_MODIFICATION`.** `read` [`QuoteApprovalTierMapper.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/service/QuoteApprovalTierMapper.java): `DEAL_DESK` and `SALES_VP` map to type `QUOTE`; `CFO` maps to type `GOVERNANCE`. `read` `ApprovalService.submit`: `quoteId` is required.
- **Discount path.** `read` [`DiscountApprovalEventConsumer.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/event/DiscountApprovalEventConsumer.java): it inserts `ApprovalType.DISCOUNT` and calls `supersede` on an older pending discount for the same line. That consumer was not executed (`unverified` as runtime). It is not an amendment writer.
- **Supersede status.** `read` [`ApprovalStatus.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalStatus.java): the enum value is `SUPERSEDED`. `read` [`V1_8__approval_superseded_status.sql`](../../../backend/services/approval-routing/src/main/resources/db/migration/V1_8__approval_superseded_status.sql): `SUPERSEDED` requires `superseded_by` and `superseded_at`. `read` `ApprovalRequest.supersede`: only a `PENDING` row can be superseded. `approve` and `reject` also require `PENDING`. This `V1_8` is the approval-routing migration. It is not CAM `V1_8__contract_header_backfill.sql`. `read` [`docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md`](delegated-renewals-amendments-decisions-2026-10-17.md) D-STORAGE: "Clear old policy-decision identities, trace and approval/signature references on the new revision as required by OQ6."
- **Approval-routing versions.** `read` that service's migration directory through `V1_9__ramp_approval_binding.sql` (`pricing_fingerprint`, `governing_period` on the entity; `read` [`ApprovalRequest.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalRequest.java)). That `V1_9` is not CAM `V1_9`. A later approval-routing column, if a story adds one, is the next free version in that service at build time, with a rollback statement. This note writes none.
- **Reject outbox.** `read` `ApprovalService.reject`: it publishes `EventContractRegistry.QUOTE_APPROVAL_REJECTED_V1` with aggregate type `ApprovalRequest`. `approve` publishes no event in the method that was read.
- **OQ17 and V14 are text.** `read` register OQ17. `read` [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) V14: a manual price override records actor, reason code, and the overridden value, and routes to approval. The same spec's `IN_REVIEW` to `APPROVED` row names `CONTRACT_MODIFICATION` or auto-approval under the threshold. No auto-approve method was found in `ApprovalService` (the class was read). Whether another class inserts a system approval is `unverified` outside that class and the discount consumer.
- **DRG-549 override.** #663 adopts the DRG-549 baseline (`read` fork index): `OVERRIDE` is the explicit unit price on the line, with actor and reason, and it is out of the first slice. "No price until a separate approval record exists" is an alternative in that frame, not the adopted choice.

## Forks

### G1. Route home

| Option | Meaning |
|---|---|
| A. Existing approval-routing, type `CONTRACT_MODIFICATION` | One row per document revision that G2 selects. `quote_id` is the continuation quote. |
| B. The BFF mapper | `QUOTE` for Deal Desk and Sales VP, `GOVERNANCE` for CFO. |
| C. The DRG-601 skeleton | Chain templates, thresholds, and delegation tables. |
| D. The DRG-560 research sketch | An `InApproval` state and `supersedesChangeId` on the LifecycleChange. |

**Default:** Proposed, unaccepted, owner decision required. Option A. The row is created in the existing approval-routing service with `approval_type` `CONTRACT_MODIFICATION`. There is no second engine. Document states stay the `V1_7__contract_documents.sql` list. OQ6 rule 1, quoted above, is the revision. No `supersedesChangeId` is added. Alternatives written out: map the amendment through `QUOTE` and `GOVERNANCE` as `QuoteApprovalTierMapper` does for quote BFF submit (option B); host the DRG-601 skeleton tables (option C); add `InApproval` and `supersedesChangeId` (option D). DRG-443 stays Backlog and is not a prerequisite. DRG-591 and DRG-601 stay parked.

### G2. When the commercial row fires

| Option | Meaning |
|---|---|
| A. OQ17 as written | Override, backdating, or total ARR delta above the threshold. The ARR delta threshold is a Finance value that stays owner-set. |
| B. Every submit | A row exists for every amendment. |
| C. System row below the threshold | An approved row with no person, so `approval_ref` is never null. |

**Default:** Proposed, unaccepted, owner decision required. Option A. OQ17 stays **DEFAULT PROPOSED** and this note does not change its sentence: approval only for overrides, backdating, or total ARR delta above a configurable threshold. The ARR delta threshold is a Finance value that stays owner-set. This note invents no number. When none of those three is true, no `approval_requests` row is created and `approval_ref` stays null. That null, paired with the content digest, is the approval binding execution checks. A null ref is valid only when this classification said no row was required. A required row that is missing or not `APPROVED` fails the binding. No `SYSTEM` approved row is inserted, because no such writer was read. Alternatives written out: every submit opens a row (option B); insert a system-approved row below the threshold (option C). Entitlements and conversion price are not inputs. They stay pending DRG-609 record merge.

### G3. Who decides

| Option | Meaning |
|---|---|
| A. Existing roles, split by trigger | Override: tier `SALES_VP`. Backdating or ARR-delta-only: tier `DEAL_DESK`. |
| B. CFO for every amendment | Tier `CFO` on every G2 row. |
| C. Seller self-approval | The requester may decide. |
| D. Name a legal role here | This note grants an Entra role. |

**Default:** Proposed, unaccepted, owner decision required. Option A. The decider is a person, not a service, whose existing role satisfies the tier, and who is not the requester. That is `ApprovalService.authorizeDecision` and `ApprovalAuthority` as read. A higher role may decide a lower tier, which that class already allows. An unknown tier fails closed. The ARR delta threshold is a Finance value that stays owner-set. This note invents no number and names no person. The legal decider is not this tier. Adopted 594-F3 and the #663 catalog sentence stand: until the named legal permission is delivered, the override path refuses, and sales cannot override. This note does not name that permission and does not grant an Entra role. #663 says no new Entra role or approval permission is silently granted. Coterm approvers and the partial-apply remedy approver stay parked on DRG-591 (`read` OQ25; `read` [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) remedy-approver row). Alternatives written out: tier `CFO` on every amendment (option B); the seller decides their own request (option C); this note names and grants a legal role (option D).

### G4. Legal guard beside the commercial row

| Option | Meaning |
|---|---|
| A. Two checks, adopted legal gate unchanged | Legal blocks `DRAFT` to `IN_REVIEW`. Commercial, when G2 fires, blocks `IN_REVIEW` to `APPROVED`. |
| B. Legal as a `TERMS` row | A second request stops the document before `APPROVED`. |
| C. One combined row | Legal and commercial share one `CONTRACT_MODIFICATION`. |

**Default:** Proposed, unaccepted, owner decision required. Option A. The adopted 594-F1 and 594-F4 class rules classify the document. The adopted 594-F2 guard runs on `DRAFT` to `IN_REVIEW` and adds no state. The adopted 594-F5 timing stands: S1–S8 keep the OQ10 signature cut, and this guard is a later increment. It is not added to S3. S3 / DRG-581 stays unlaunched. Quoted OQ6 text: "S3 / DRG-581 needs separate owner authorization." When G2 fires, the `CONTRACT_MODIFICATION` row is the guard on `IN_REVIEW` to `APPROVED`, which is the mid-term spec row that was read. The adopted 599-F3 hold stays a different gate: the whole document waits before `IN_REVIEW`, immediately before `EXECUTED`, and again before apply. This note does not shrink that hold. A pre-execution revision follows OQ6 rule 1, quoted above, including "Approvals and signatures are void". Rule 2, quoted above, refuses a revision after `EXECUTED` with 409 `IMMUTABLE_EXECUTED`. Alternatives written out: a `TERMS` request that stops the document before `APPROVED` (option B, which is the 594-F2 alternative this adoption did not select); one combined row (option C).

### G5. Fields

| Option | Meaning |
|---|---|
| A. Existing columns | No new approval column and no new CAM column. |
| B. Document id on the request first | No route until a new column exists. |
| C. Quote only | `approval_ref` stays empty. |

**Default:** Proposed, unaccepted, owner decision required. Option A. The commercial row uses columns that already exist on `approval_requests`: `quote_id`, `line_id`, `approval_type`, `status`, `requester_id`, `approver_id`, `approval_tier`, `reason`, `rejection_reason`, `submitted_at`, `approved_at`, `rejected_at`, `superseded_by`, `superseded_at`. `quote_id` is the continuation quote, because `ApprovalService.submit` requires it. `line_id` is null because adopted 594-F4 is the whole document. `pricing_fingerprint` and `governing_period` stay the ramp binding from approval-routing `V1_9__ramp_approval_binding.sql`. This note does not require them for a flat amendment. `amendment_documents.approval_ref` stores the request id as text when a row exists. It is null when G2 does not fire. The binding D-RACE verifies is that ref together with `content_digest`. D-SNAPSHOT excludes the approval from the hashed projection and binds the evidence to the digest separately. Header fields the class rule reads, already stored, are price and term (`tcv_delta`, `arr_delta`, and the date columns in `V1_7__contract_documents.sql`) against action `ADD`, `UPDATE`, and `REMOVE`. No `supersedesChangeId` is added. No threshold table is added. No chain table is added. A later column, on either service, is the next free version at build time, with a rollback statement. CAM `V1_9` stays reserved for S4 `document_apply_ledger`. Never `V1_7_3`. This note writes no migration. Alternatives written out: block the route until `approval_requests` has a document id (option B); leave `approval_ref` empty and keep the id only on the quote (option C).

### G6. Void on a new revision

| Option | Meaning |
|---|---|
| A. Clear the pin; supersede only `PENDING` | The new `DRAFT` does not cite the old request. History stays. |
| B. Cancel | `ApprovalRequest.cancel` on the pending row. |
| C. New `VOIDED` status | A later approval-routing migration. |

**Default:** Proposed, unaccepted, owner decision required. Option A. Clear `approval_ref` on a pre-execution revision. That clear cites [`docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md`](delegated-renewals-amendments-decisions-2026-10-17.md) D-STORAGE (`read`: "Clear old policy-decision identities, trace and approval/signature references on the new revision as required by OQ6.") and cites [`backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalStatus.java`](../../../backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalStatus.java) (`read`: enum value `SUPERSEDED`). Map the pending approval row to `SUPERSEDED`. That mapping cites the same `ApprovalStatus` enum and the same #663 file. `ApprovalRequest.supersede` allows the move only from `PENDING` (`read` that method). [`V1_8__approval_superseded_status.sql`](../../../backend/services/approval-routing/src/main/resources/db/migration/V1_8__approval_superseded_status.sql) already stores `superseded_by` and `superseded_at` (`read`). An `APPROVED` row is not updated, because `supersede` refuses it. It remains history. The new revision does not cite it. D-PRICED-WRITE invalidates the prior approval binding when a price is installed (`read` the same #663 file). OQ6 rule 1, quoted above: "The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again." Rule 2, quoted above, gives an executed document no new approval route. The remedy is a new LifecycleChange. Revision audit types in that #663 file stay `AMENDMENT_SUPERSEDED` and `AMENDMENT_REVISION_REJECTED`. Those names are the revision audits. They are not a classification-event name. This note does not add an S6 topic. The historical OQ6 sentence that the ruling does not choose a supersession event type is quoted above and is superseded by D-RACE for those two local revision types. Alternatives written out: cancel the pending row (option B); add `VOIDED` in a later approval-routing migration, next free at build time, with a rollback statement, not written here (option C).

### G7. Audit entries

| Option | Meaning |
|---|---|
| A. Approval row and price snapshot | The commercial decision stays the approval row. The price snapshot stays the #663 pricing-owned store. The classification event is G9. |
| B. `reason` only | The classification is text on the approval row. |
| C. DRG-564 sketch | Approval columns on `quote.policy_decisions`. |
| D. New CAM table | A classification table in a later CAM migration. |

**Default:** Proposed, unaccepted, owner decision required. Option A.

1. **Commercial decision.** The `approval_requests` row is the decision. Fields recorded there are the columns in G5, including who requested, who decided, the tier, the reason, and the timestamps. `ApprovalService.reject` already publishes `QUOTE_APPROVAL_REJECTED_V1`. This note adds no approved-event contract.
2. **Legal classification event.** OPEN FORK. Proposed, unaccepted, owner decision required. The later local classification event whose type is not chosen stays open in G9. This note invents no event name and no schema. It does not add an S6 topic. Revision audit types named in [`docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md`](delegated-renewals-amendments-decisions-2026-10-17.md) are not reused as that name.
3. **Price snapshot.** The #663 pricing paragraph is the price record: a pricing-owned append-only snapshot per priced continuation line. This note does not take the rest of [#643](https://github.com/drgaciw/open-crm-cpq/pull/643). It does not insert `quote.policy_decisions`. It does not assign `pricing_audit_log.rules_fired` or `waterfall_trace`. The line pins `policy_id`, `policy_version`, `policy_decision_id`, and `pricing_trace_id` stay the columns in `V1_7__contract_documents.sql`. They are not approval fields.

Entitlements and any conversion price inside a snapshot stay pending DRG-609 record merge. Alternatives written out: store the classification only in `approval_requests.reason` (option B); add approval columns to the DRG-564 sketch (option C); add a CAM classification table at `V1_10` or higher, next free at build time, with a rollback statement, not written here (option D).

### G9. Later local classification event whose type is not chosen

| Option | Meaning |
|---|---|
| A. OPEN FORK | No event name. No schema. |
| B. A later named type | An owner decision after this note selects the type and the payload. |
| C. An S6 topic | A domain-event contract. |

**Default:** OPEN FORK. Proposed, unaccepted, owner decision required. The later local classification event whose type is not chosen stays open. This note invents no event name and no schema. Alternatives written out: a later decision may select a type and a payload (option B), and this row does not write that name or that schema; an S6 topic (option C), which this note does not choose. #663 D-RACE already says no new S6 domain-event topic or schema is adopted (`read` [`docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md`](delegated-renewals-amendments-decisions-2026-10-17.md)).

### G8. Uplift and discount waivers

| Option | Meaning |
|---|---|
| A. Precedence is not a route | A resolved uplift does not open an approval. A manual override does, as OQ17. |
| B. Every uplift | A non-zero percent opens `GOVERNANCE`. |
| C. Discount type | Overrides use `DISCOUNT` and the Kafka consumer. |
| D. Hold the price | No price until an approval row exists. |

**Default:** Proposed, unaccepted, owner decision required. Option A. Precedence stays CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES. A percent resolved on that order is not an approval trigger. A manual override is the OQ17 override and uses G1 and G2. V14 still names actor, reason code, overridden value, and approval. This note does not implement V14. The adopted DRG-549 `OVERRIDE` meaning stays the explicit unit price, with actor and reason, out of the first slice. The discount consumer stays `DISCOUNT` for quote discount events and is not retargeted. Credit and termination fee stay the adopted 550-F2 `NoCredit` and 550-F3 `NoFee`. The proration record stays Accepted and is not restated. Entitlements and conversion price stay pending DRG-609 record merge, and they are not routing inputs. Alternatives written out: every non-zero uplift opens `GOVERNANCE` (option B); route overrides as `DISCOUNT` (option C); return no price until an approval row exists (option D, the DRG-549 alternative this adoption did not select).

## What an owner decision on this note still does not do

A decision on any row above:

- accepts no ADR and edits no ADR;
- leaves ADR-017 and ADR-024 through ADR-027 Proposed;
- leaves the proration record Accepted and unchanged;
- leaves uplift precedence CLAUSE, LINE, AGREEMENT, ACCOUNT, RULES;
- does not reopen 594-F1 through 594-F5, 599-F3, D-STORAGE, D-SNAPSHOT, D-RACE, or D-PRICED-WRITE;
- does not change OQ5 option A or the OQ7 thin contract;
- does not change OQ17's sentence; the ARR delta threshold is a Finance value that stays owner-set, and this note invents no number;
- does not unpark OQ25, DRG-591, or DRG-601;
- does not start S3 / DRG-581 and does not authorize S6;
- leaves entitlements and conversion price pending DRG-609 record merge;
- does not take the DRG-564 sketch;
- leaves the later local classification event whose type is not chosen as OPEN FORK G9, with no event name and no schema;
- writes no migration, including no CAM `V1_9` and no `V1_7_3`;
- runs no backfill and no apply;
- authorizes no code, flag, or deploy.

DRG-560 and DRG-594 stay In Review until the owner merges.
