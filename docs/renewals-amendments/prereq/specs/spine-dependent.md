# Spine-dependent cluster spec (proposal)

**Status:** Proposed. No business choice in this file is marked adopted.
**Linear:** DRG-648
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `spine-dependent`.
**Read against main:** `bf41c230f6309964a25ef5c2c12e84c41b0c37de`.
**Date:** 2026-10-29.

This file is the dev-ready spec for the spine-dependent line on [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607). It has one section for each issue: DRG-592, DRG-594, DRG-595, DRG-597, DRG-598, DRG-599. It does not edit Linear. Once this file is on `main`, the checklist line `spine-dependent` can be ticked. The reply to the Hygiene comment of 2026-10-16 is drafted in the pull request body.

**Fixtures.** Eighteen JSON files under `fixtures/spine-dependent/`. Each acceptance criterion maps to one file. Each file has a single `"status": "Proposed"` and `"owner_decision": "___ (drg, time)"`. Expected values follow the proposed default named in `follows_proposed_option`. A blank owner slot means that default is still open.

**Conventions.** **[S]** is issue text or a file on main `bf41c230`. **[I]** is a proposal. Every default is labelled **Status: Proposed** and leaves **Owner decision: ___ (drg, time)** blank. Prior frames `../cluster-a-amendment-modes-and-policy-decision-frame.md` and `../cluster-g-visibility-queues-migration-decision-frame.md` are sources for the option lists. This file does not accept those frames.

**DRG-649.** The deeper SKU-mapping spec is a later story. The DRG-599 section below is the same depth as the other sections.

Each section lists the acceptance-criterion table first, then inputs, outputs, storage fields, error codes, the as-is record, and the proposed choices.

---

## DRG-592 — Amendment modes

**Issue:** [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592) (RA-58).

**Issue text [S].** An amendment has two modes. New contract is the preferred mode. Amend in place is the other mode. New-contract mode creates a new contract. The original and the new contract both stay `ACTIVE`. A stored precedence flag on the new contract says it takes precedence. The flag is persisted on the contract record. The original stays open as a reference. The first slice stays amend-in-place. New-contract mode is the next increment.

### Acceptance criteria and fixtures

| AC | Fixture | Input | Expected |
|---|---|---|---|
| AC-592-1 | `fixtures/spine-dependent/drg-592-new-contract-stores-precedence.json` | `amendment_mode` `NEW_CONTRACT`. Document state `EXECUTED`. Original contract `c-orig`, contract state `ACTIVE`, `precedence_flag` false, `end_date` `2027-10-29`. | `error_code` null. A new contract is created, contract state `ACTIVE`, `precedence_flag` true, `prior_contract_id` `c-orig`. Original stays `ACTIVE` with `precedence_flag` false. The document's `contract_id` is the new contract. The original has no second document. |
| AC-592-2 | `fixtures/spine-dependent/drg-592-amend-in-place-leaves-flag-false.json` | `amendment_mode` `AMEND_IN_PLACE`. Document state `EXECUTED`. Contract `c-orig`, contract state `ACTIVE`, `precedence_flag` false. | `error_code` null. `contracts_inserted` 0. `precedence_flag` stays false. `document.contract_id` stays `c-orig`. |
| AC-592-3 | `fixtures/spine-dependent/drg-592-two-true-flags-fail-closed.json` | Pricing read. Two `ACTIVE` contracts, `c-1` and `c-2`, both `precedence_flag` true. | HTTP 409. `error_code` `PRECEDENCE_CONFLICT`. `price` null. Both contracts stay visible. |

### Inputs

- Tenant id, amendment document id, and `amendment_mode` (`NEW_CONTRACT` or `AMEND_IN_PLACE`).
- The source contract id, its contract state, its `end_date`, and its stored `precedence_flag`.
- For a pricing read, the set of `ACTIVE` contracts for the customer in the tenant.

### Outputs

- The contract rows after the proposed apply: id, contract state, `precedence_flag`, `prior_contract_id`.
- The document's `contract_id`.
- On AC-592-3, a refusal and no price.

### State and storage fields

| Field | Home | On main `bf41c230` |
|---|---|---|
| `contracts.prior_contract_id` | `V1_7__contract_documents.sql` line 28 | Present. Foreign key to `contracts.id` (line 35). |
| `contracts.status` | same file, check at line 37 | `ACTIVE`, `EXPIRED`, `TERMINATED`. |
| `contracts.precedence_flag` | Proposed boolean, not null, default false **[I]** | Absent. The migration header at lines 2–3 says this migration adds no `precedence_flag`. |
| `amendment_documents.amendment_mode` | Proposed column on the amendment document **[I]** | Absent. `document_kind` (lines 107–108) is a different token set. |
| `amendment_documents.contract_id` | `V1_7__contract_documents.sql` line 48 | Present. Required. |

### Error codes

| Code | HTTP | When | Fixture |
|---|---|---|---|
| `PRECEDENCE_CONFLICT` | 409 | A pricing read finds two `ACTIVE` contracts with `precedence_flag` true | AC-592-3 |

AC-592-1 and AC-592-2 expect `error_code` null.

### As-is on main `bf41c230`

`contracts` stores `prior_contract_id` and has no precedence column **[S]**. Contract state is `ACTIVE`, `EXPIRED`, or `TERMINATED` **[S]**. The first-slice document still hangs on one `contract_id` **[S]**.

### Proposed choices

#### 592-F1 Flag shape

| Option | Meaning |
|---|---|
| A | Boolean `precedence_flag`, not null, default false. Amend-in-place leaves it false. New-contract mode sets true on the new contract only at executed apply. |
| B | Integer rank across overlapping contracts. |
| C | A customer-level `prevailing_contract_id` and no boolean column. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 592-F2 Who may change the flag

| Option | Meaning |
|---|---|
| A | The initial winner is set at executed apply. A later change is a correcting document. Apply locks the chain, clears the current winner, sets the replacement, and appends an audit row. Two true flags reject the transfer. |
| B | A product or admin in-place flip with an audit event. |
| C | A sales flip with no new amendment. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 592-F3 Overlap for pricing and visibility

| Option | Meaning |
|---|---|
| A | Pricing reads the precedence-true contract. Both contracts stay visible. Two true flags fail closed with `PRECEDENCE_CONFLICT`. Zero true flags and one active contract: that contract is the pricing source. |
| B | The flag wins pricing, and DRG-595 owns visibility. |
| C | Pricing, entitlement, and visibility each use their own rule. |

**Default:** Option A. Entitlement resolution stays outside this default.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 592-F4 Original contract at its own end date

| Option | Meaning |
|---|---|
| A | Stays `ACTIVE` as a reference until its own `end_date`, then `EXPIRED`. |
| B | Stays `ACTIVE` with no expiry. |
| C | Set to `TERMINATED` when the new contract is created. |

**Default:** Option A. This default adds no status value. The check at `V1_7__contract_documents.sql` line 37 already allows `EXPIRED`.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 592-F5 Where the mode is stored

| Option | Meaning |
|---|---|
| A | A column on the amendment document, in a later increment. S1 does not add it. |
| B | Infer the mode from whether a new `contracts` row was inserted. |
| C | Store the mode only on the LifecycleChange. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 592-F6 Which contract owns the document

| Option | Meaning |
|---|---|
| A | The new contract owns the one document. `prior_contract_id` points at the original. |
| B | The original owns the document. |
| C | Each contract gets a copy. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

---

## DRG-594 — Legal-review-free amendments

**Issue:** [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (RA-60).

**Issue text [S].** An amendment skips legal review only when it changes pricing, term, or seats, and nothing else. Any other change requires legal review before execution. General approvals stay parked on DRG-591. This section records the as-is approval type and does not design a writer.

### Acceptance criteria and fixtures

| AC | Fixture | Input | Expected |
|---|---|---|---|
| AC-594-1 | `fixtures/spine-dependent/drg-594-same-sku-update-skips-legal-review.json` | From `DRAFT`. One line `UPDATE` of `SKU-A` to `SKU-A`, quantity 10 to 12. Header edits `price` and `term`. | `legal_review_required` false. Transition to `IN_REVIEW` allowed. Document state `IN_REVIEW`. `approval_row_written` false. `approval_type_written` null. `error_code` null. |
| AC-594-2 | `fixtures/spine-dependent/drg-594-add-sku-requires-legal-review.json` | From `DRAFT`. One line `ADD` of `SKU-B`. No header edit. | `legal_review_required` true. Transition refused. Document state stays `DRAFT`. HTTP 409. `error_code` `LEGAL_REVIEW_REQUIRED`. `approval_row_written` false. `approval_type_written` null. |
| AC-594-3 | `fixtures/spine-dependent/drg-594-mixed-document-requires-review.json` | From `DRAFT`. One same-SKU `UPDATE` line, plus header edit `governing_agreement_ref`. | Whole document `legal_review_required` true. `per_line_skip` false. Document state stays `DRAFT`. HTTP 409. `error_code` `LEGAL_REVIEW_REQUIRED`. `approval_row_written` false. `approval_type_written` null. |

### Inputs

- Document state, the line list (`action_code`, source SKU, target SKU, quantity), and the set of edited header fields.
- Actor id, used only when a later owner decision names an override role. These three fixtures send no override.

### Outputs

- `legal_review_required` for the whole document.
- The document state after the `DRAFT` to `IN_REVIEW` attempt.
- `approval_row_written` and `approval_type_written`. Under the proposed default 594-F6 option A, both stay empty on every fixture in this section.

### State and storage fields

| Field | Home | On main `bf41c230` |
|---|---|---|
| `amendment_documents.status` | `V1_7__contract_documents.sql` lines 110–112 | `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID`. No legal-review state. |
| `amendment_lines.action_code` | same file, lines 261–263 | Includes `ADD`, `UPDATE`, `REMOVE`, and the other listed tokens. |
| `approval_requests.approval_type` | `V1_1__create_approval_tables.sql` check, lines 36–38, replaced by `V1_2__add_quote_governance_approval_types.sql` lines 10–18 | The check allows `CONTRACT_MODIFICATION`. The check does not insert a row. |
| `ApprovalType.CONTRACT_MODIFICATION` | `ApprovalType.java` line 8 | Enum constant only. |
| Legal-review classification | Proposed derived value on the `DRAFT` to `IN_REVIEW` guard **[I]** | No column. |

### Error codes

| Code | HTTP | When | Fixture |
|---|---|---|---|
| `LEGAL_REVIEW_REQUIRED` | 409 | The proposed guard refuses `DRAFT` to `IN_REVIEW` because the document is outside the free set | AC-594-2, AC-594-3 |

AC-594-1 expects `error_code` null.

### As-is on main `bf41c230`: `CONTRACT_MODIFICATION` has no writer

The token is declared and allowed. Nothing on main writes it.

- `backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalType.java` line 8 is `CONTRACT_MODIFICATION,`. That line declares the enum constant. It does not build an `ApprovalRequest`.
- `backend/services/approval-routing/src/main/resources/db/migration/V1_1__create_approval_tables.sql` line 37 lists `'CONTRACT_MODIFICATION'` inside `chk_approval_type`. That constraint filters values. It does not insert a row.
- `backend/services/approval-routing/src/main/resources/db/migration/V1_2__add_quote_governance_approval_types.sql` line 15 lists `'CONTRACT_MODIFICATION',` in the replacement check. It does not insert a row.
- The production assignments of `ApprovalType` on this tree are `backend/services/approval-routing/src/main/java/com/opencrm/approval/event/DiscountApprovalEventConsumer.java` line 96 (`.approvalType(ApprovalType.DISCOUNT)`) and `backend/services/approval-routing/src/main/java/com/opencrm/approval/service/QuoteApprovalTierMapper.java` lines 24–26 (`ApprovalType.QUOTE` for `DEAL_DESK` and `SALES_VP`, `ApprovalType.GOVERNANCE` for `CFO`).
- A search of `*.java`, `*.sql`, `*.yml`, `*.yaml`, `*.json`, `*.xml`, `*.ts`, `*.tsx`, `*.js`, and `*.mjs` on this tree finds `CONTRACT_MODIFICATION` in Java only at `ApprovalType.java` line 8. No `approvalType(ApprovalType.CONTRACT_MODIFICATION)` call exists.

### Proposed choices

#### 594-F1 Seats versus Add and Remove

| Option | Meaning |
|---|---|
| A | A same-SKU quantity `UPDATE` is seats and is free, together with price and term. `ADD` of a SKU and `REMOVE` of a product line require review. |
| B | Same-SKU Add and Remove count as seats. A different SKU requires review. |
| C | Any quantity change is free, including a new SKU. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 594-F2 Where the gate sits

| Option | Meaning |
|---|---|
| A | A guard on `DRAFT` to `IN_REVIEW`. No new state. |
| B | A new `LEGAL_REVIEW` state before `APPROVED`. |
| C | The document may enter `IN_REVIEW` and stops before `APPROVED`. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 594-F3 Override

| Option | Meaning |
|---|---|
| A | No override. The document stays in `DRAFT` until the classification changes. |
| B | A named legal role may override, and the actor is recorded. Until that role is named, the guard refuses. Sales cannot override. |
| C | The seller may override. |

**Default:** Option B, with the refusal in force until the role is named.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 594-F4 Mixed edits

| Option | Meaning |
|---|---|
| A | One line or header edit outside the free set makes the whole document require review. Free header fields are price and term. |
| B | Free lines skip review while other lines wait. |
| C | Governing agreement and clauses are free as well. |

**Default:** Option A, using price and term as the free header fields.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 594-F5 First-slice timing

| Option | Meaning |
|---|---|
| A | The gate is required before S3 starts. |
| B | The gate is a later increment. S1–S8 keep the current signature cut. |

**Default:** Option B.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 594-F6 Approval-type writer

This choice is the open question on the as-is record above. Option B is listed. It is not designed.

| Option | Meaning |
|---|---|
| A | Leave `CONTRACT_MODIFICATION` with no writer. The classification guard does not insert `approval_requests`. |
| B | Add a writer that inserts `approval_type` `CONTRACT_MODIFICATION`. |
| C | Keep using `QUOTE` and `GOVERNANCE` from `QuoteApprovalTierMapper` lines 24–26. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

---

## DRG-595 — Visibility

**Issue:** [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595) (RA-61).

**Issue text [S].** Contracts, renewals, and amendments are visible to the contract owner, who can act, and to read-only sales ops and finance. Read-only means no edit, submit, approve, or execute. Read-only access also sees audit history. Approver roles stay parked on DRG-591.

### Acceptance criteria and fixtures

| AC | Fixture | Input | Expected |
|---|---|---|---|
| AC-595-1 | `fixtures/spine-dependent/drg-595-owner-may-edit-draft.json` | Actor `user-owner`, stored owner `user-owner`, action `EDIT`, document state `DRAFT`, tenant `t-1`. | `allowed` true. `error_code` null. |
| AC-595-2 | `fixtures/spine-dependent/drg-595-read-only-verbs-refused.json` | Actors `user-ops` (`role_label` `SALES_OPS`) and `user-fin` (`role_label` `FINANCE`). Actions `EDIT`, `SUBMIT`, `APPROVE`, `EXECUTE`. Tenant `t-1`. | Each action `allowed` false. HTTP 403. `error_code` `READ_ONLY_FORBIDDEN`. |
| AC-595-3 | `fixtures/spine-dependent/drg-595-read-only-audit-same-tenant.json` | Actor `user-fin`, `role_label` `FINANCE`, action `READ_AUDIT`. Contract `c-1` in tenant `t-1`. Contract `c-2` in tenant `t-2`. | Same-tenant read `allowed` true, audit events returned, `writes` 0. Other-tenant read `allowed` false. HTTP 403. `error_code` `TENANT_MISMATCH`. |

`role_label` in these fixtures is the ticket's words. It is not an Entra role name on main. See 595-F5.

### Inputs

- Caller user id, tenant id from the caller, action (`EDIT`, `SUBMIT`, `APPROVE`, `EXECUTE`, `READ_AUDIT`), and the contract or document id.
- The stored owner user id for that contract.
- For a read-only caller, the role label `SALES_OPS` or `FINANCE` as a label in this spec.

### Outputs

- `allowed` and, on a refusal, the error code.
- For `READ_AUDIT`, the audit events of that contract in the caller's tenant, and a write count of 0.

### State and storage fields

| Field | Home | On main `bf41c230` |
|---|---|---|
| `contracts` thin header | `V1_7__contract_documents.sql` lines 17–33 | `tenant_id`, `contract_number`, `customer_account_id`, `term_model`, `start_date`, `end_date`, `governing_agreement_ref`, `status`, `last_executed_seq`, `prior_contract_id`, plus audit columns. No owner column. |
| `contracts.created_by` | line 30 | Present. A backfill marker is not a person. |
| `amendment_document_events` | same migration | Present. The read in AC-595-3 uses this table. |
| Contract owner fact | Proposed row outside the thin header: `contract_id`, `owner_user_id`, `effective_from` **[I]** | Absent. |
| App roles | `docs/adr/ADR-016-identity-authentication-service-trust.md` decision 7 | `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin`. No sales-ops role and no finance role. |

### Error codes

| Code | HTTP | When | Fixture |
|---|---|---|---|
| `READ_ONLY_FORBIDDEN` | 403 | A read-only label attempts edit, submit, approve, or execute | AC-595-2 |
| `TENANT_MISMATCH` | 403 | The contract's tenant differs from the caller's tenant | AC-595-3 |

AC-595-1 expects `error_code` null.

### As-is on main `bf41c230`

The thin header has no owner column **[S]**. ADR-016 decision 7 names five app roles and defers record-level authorization **[S]**. The words "sales ops" and "finance" are the issue text. They are not roles in that decision.

### Proposed choices

#### 595-F1 Who the owner is

| Option | Meaning |
|---|---|
| A | A named user id, distinct from `created_by`. |
| B | The CRM account owner of `customer_account_id`, resolved on read. |
| C | `contracts.created_by`. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 595-F2 Where that owner is stored

| Option | Meaning |
|---|---|
| A | A fact outside the ADR-035 thin-header list. |
| B | A new thin-header column. |
| C | Derived on every read, with no stored owner. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 595-F3 How ownership transfers

| Option | Meaning |
|---|---|
| A | An explicit transfer appends an audit row and leaves executed documents unchanged. |
| B | The owner follows the CRM account owner automatically. |
| C | A stored owner plus optional CRM sync. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 595-F4 What the owner may do

| Option | Meaning |
|---|---|
| A | Create, edit, and submit a draft once those APIs exist. Approve stays with DRG-591. Execute stays outside this note. |
| B | Act includes approve and execute. |
| C | Nobody edits until S3 is authorized. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 595-F5 How sales ops and finance are named

| Option | Meaning |
|---|---|
| A | Labels in this note only. The ADR-016 names stay unchanged. |
| B | Map those ticket words onto `DealDesk` and `CFO` inside this file. |
| C | Add Entra roles in this note. |

**Default:** Option A. This file does not accept a role map.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 595-F6 What read-only access sees inside a tenant

| Option | Meaning |
|---|---|
| A | Every contract in the caller's tenant, including that contract's `amendment_document_events`. |
| B | Only assigned contracts. |
| C | Contracts in other tenants. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 595-F7 When the owner check arrives relative to S3

| Option | Meaning |
|---|---|
| A | It is a named prerequisite. This note does not start S3. |
| B | Accepting this note starts S3. |
| C | S3 ships the read-only access first and skips the owner check. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

---

## DRG-597 — Work queues

**Issue:** [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) (RA-63).

**Issue text [S].** Renewals and amendments are two work queues, with separate views, assignment, and SLAs, over one shared data model. The model is the LifecycleChange spine, the amendment document, and the contract records. There is no duplicate table per queue. The queues are outside the first slice.

### Acceptance criteria and fixtures

| AC | Fixture | Input | Expected |
|---|---|---|---|
| AC-597-1 | `fixtures/spine-dependent/drg-597-queues-partition-shared-rows.json` | Shared rows `lc-r` intent `RENEW` and `lc-a` intent `AMEND`. | Renewals queue contains `lc-r` only. Amendments queue contains `lc-a` only. `shared_row_ids` are `lc-r` and `lc-a`. `copied_table` false. `error_code` null. |
| AC-597-2 | `fixtures/spine-dependent/drg-597-other-intent-in-neither-queue.json` | Shared row `lc-s` intent `SUSPEND`. | Renewals queue empty. Amendments queue empty. `queue_membership` null. `error_code` null. |
| AC-597-3 | `fixtures/spine-dependent/drg-597-owner-sets-due-date.json` | Owner of `lc-r` sets `due_on` `2026-12-01`. Sales ops sets `assignee_user_id` `user-ops` on the same row. | Side row keyed by `lc-r` stores `due_on` `2026-12-01`. `copied_table` false. Sales ops write HTTP 403, `error_code` `ASSIGNMENT_FORBIDDEN`, `assignee_user_id` null. |

### Inputs

- The shared lifecycle-change row: id, intent, tenant, contract id.
- Queue filter `renewals` or `amendments`.
- For assignment, the caller (owner or read-only label) and the optional `due_on` and `assignee_user_id`.

### Outputs

- The list of shared row ids in that queue.
- The side-row assignment, when the owner write is allowed.
- A refusal when a read-only label writes assignment.

### State and storage fields

| Field | Home | On main `bf41c230` |
|---|---|---|
| `amendment_documents.intent` | `V1_7__contract_documents.sql` line 51 | Present. |
| `amendment_documents.lifecycle_change_id` | line 49 | Present. |
| Queue membership | Proposed filter on the shared row **[I]** | No queue table. |
| `queue_assignments` | Proposed side row: `lifecycle_change_id`, `assignee_user_id`, `due_on` **[I]** | Absent. It does not copy the document. |

### Error codes

| Code | HTTP | When | Fixture |
|---|---|---|---|
| `ASSIGNMENT_FORBIDDEN` | 403 | A caller who is not the contract owner writes assignee or due date | AC-597-3 |

AC-597-1 and AC-597-2 expect `error_code` null. AC-597-2 records an empty membership, which is a successful read.

### As-is on main `bf41c230`

Intent is a column on the amendment document **[S]**. There is no renewals-queue table and no amendments-queue table **[S]**.

### Proposed choices

#### 597-F1 Where queue membership lives

| Option | Meaning |
|---|---|
| A | A filter on the shared spine. Assignment and due date, when stored, are a side row keyed by `lifecycle_change_id`. |
| B | Two tables that copy the document. |
| C | One undifferentiated list. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 597-F2 Which intents are in which queue

| Option | Meaning |
|---|---|
| A | Renewal queue is `RENEW` only. Amendment queue is `AMEND` only. Other intents wait. |
| B | Renewal queue also includes `EXTENSION`. |
| C | The amendment queue includes every intent other than `RENEW`. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 597-F3 Who may assign and set a due date

| Option | Meaning |
|---|---|
| A | Sales ops may assign under a write permission recorded in a later owner decision. |
| B | Only the owner may assign and set due dates on owned contracts. |
| C | Assignment waits for DRG-591. |

**Default:** Option B.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 597-F4 SLA duration

| Option | Meaning |
|---|---|
| A | An optional due date only. |
| B | Ninety days before end date. |
| C | No due date in this spec. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 597-F5 HTTP surface

| Option | Meaning |
|---|---|
| A | Two filters on `GET /api/v1/lifecycle-changes`. No new route. |
| B | New `/queues/renewals` and `/queues/amendments` resources. |
| C | The quotes list filtered by deal type. |

**Default:** Option A. This spec adds no route.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

---

## DRG-598 — Phased migration of in-flight contracts

**Issue:** [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598) (RA-64).

**Issue text [S].** In-flight contracts migrate in phases ordered by contract end date. The first phase is upcoming renewals, the nearest end dates. Scope expands incrementally from sales and marketing metrics. S7 itself is unchanged and can run per phase. OQ7 stays open. This section specifies the phase manifest. It does not call the backfill.

### Acceptance criteria and fixtures

| AC | Fixture | Input | Expected |
|---|---|---|---|
| AC-598-1 | `fixtures/spine-dependent/drg-598-nearest-end-dates-first.json` | `sub-a` `end_date` `2026-11-01`, `sub-b` `2027-06-01`, `sub-c` `2026-12-15`. Each `contract_id` null. Window `end_date` on or before `2026-12-31`. Order by `subscriptions.end_date` ascending. | Manifest order `sub-a`, `sub-c`. Excluded `sub-b`. `backfill_invoked` false. `header_written` false. `error_code` null. |
| AC-598-2 | `fixtures/spine-dependent/drg-598-unapproved-expansion-does-not-apply.json` | Expansion manifest adds `sub-b` with metric `segment` `enterprise`. `manifest_owner_slot` is `___ (drg, time)`. Action `APPLY`. | HTTP 409. `error_code` `MANIFEST_UNAPPROVED`. `expansion_applied` false. `backfill_invoked` false. `header_written` false. |
| AC-598-3 | `fixtures/spine-dependent/drg-598-unmigrated-stays-on-current-path.json` | `sub-d` `contract_id` null, absent from the manifest. | `contract_id` stays null. `header_written` false. `oq11_flag_changed` false. `acceptance_path` `CURRENT`. `error_code` null. |

### Inputs

- Subscription id, `subscriptions.end_date`, `subscriptions.renewal_date`, and `subscriptions.contract_id`.
- A written manifest: the end-date window, the ordered subscription ids, and any metric note (segment, region, or product line).
- The action `SELECT` or `APPLY`, and the blank or filled owner slot.

### Outputs

- The ordered manifest of subscription ids, and the ids the window excludes.
- On `APPLY` while the owner slot is blank, `MANIFEST_UNAPPROVED` and no write.
- `backfill_invoked` false on every fixture in this section.

### State and storage fields

| Field | Home | On main `bf41c230` |
|---|---|---|
| `subscriptions.end_date` | `V1_1__create_contract_tables.sql` line 17 | Present, not null. |
| `subscriptions.renewal_date` | line 18 | Present, nullable. |
| `subscriptions.contract_id` | `V1_7__contract_documents.sql` line 353 | Present, nullable. Index at line 355. |
| `contract_header_backfill_apply(p_tenant_id uuid)` | `V1_8__contract_header_backfill.sql` line 85 | Present. This spec does not call it. |
| Phase manifest | Proposed document: window, ordered subscription ids, metric notes, owner slot **[I]** | No manifest table. |

### Error codes

| Code | HTTP | When | Fixture |
|---|---|---|---|
| `MANIFEST_UNAPPROVED` | 409 | `APPLY` runs while `manifest_owner_slot` is `___ (drg, time)` | AC-598-2 |

AC-598-1 and AC-598-3 expect `error_code` null. They also expect `backfill_invoked` false.

### As-is on main `bf41c230`

S7 orders nothing by end date. `contract_header_backfill_apply` takes a tenant id **[S]**. Eligible rows in the V1_8 header comment are `contract_id` null. A phase in this spec is a list of ids. It is not a call to that function.

### Proposed choices

#### 598-F1 What a phase is

| Option | Meaning |
|---|---|
| A | A written manifest of subscription ids. It is not a call to `contract_header_backfill_apply`. |
| B | One owner call of that function is the phase. |
| C | This spec edits V1_8 to take an end-date window. |

**Default:** Option A. This default authorizes no backfill.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 598-F2 How wide the first end-date window is

| Option | Meaning |
|---|---|
| A | The owner names the window on the manifest. |
| B | A fixed 90-day window. |
| C | Every future end date in one phase. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 598-F3 Who approves an expansion, and where metrics live

| Option | Meaning |
|---|---|
| A | The project owner approves each manifest. Metric values sit on the manifest. No new column. |
| B | Sales ops, as named on the ticket, approve it. |
| C | Finance, as named on the ticket, approves it. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 598-F4 Whether a phase synthesizes amendment history

| Option | Meaning |
|---|---|
| A | Headers only, and only if a later owner decision authorizes a write. This spec authorizes none. |
| B | The phase also writes `BACKFILLED` documents. |
| C | History is a later single cutover. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 598-F5 How migrated and unmigrated subscriptions coexist

| Option | Meaning |
|---|---|
| A | `contract_id` null stays on the current acceptance path. A header does not switch the row onto a document apply path. |
| B | The OQ11 per-tenant flag turns on. |
| C | A new per-contract flag. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 598-F6 Validation, rollback, and reconciliation

| Option | Meaning |
|---|---|
| A | Count, apply, and compensate stay owner steps in the S7 companion. This spec runs none of them. |
| B | This spec defines new SQL. |
| C | Backup restore replaces the compensate function. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 598-F7 Which date orders the phase

| Option | Meaning |
|---|---|
| A | `subscriptions.end_date`, the date S7 copies to `contracts.end_date`. |
| B | `subscriptions.renewal_date`. |
| C | The earlier of the two. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

---

## DRG-599 — Amendment SKU mapping

**Issue:** [DRG-599](https://linear.app/drgamtd-workspace/issue/DRG-599) (RA-65).

**Issue text [S].** An amendment SKU mapping defines how a SKU on the original contract maps to the SKU or SKUs on an amendment. The product team owns it. Sales can dispute a mapping. A contested mapping holds the amendment: execution and apply wait until product resolves the dispute. Same-SKU add, update, and remove in the first slice do not use a mapping. Conditional rules stay on DRG-590. DRG-649 specifies the dispute flow in more depth later. This section stops at the same depth as the sections above.

### Acceptance criteria and fixtures

| AC | Fixture | Input | Expected |
|---|---|---|---|
| AC-599-1 | `fixtures/spine-dependent/drg-599-same-sku-needs-no-mapping.json` | From `DRAFT`. Line `UPDATE` source `SKU-A` target `SKU-A`. | `mapping_required` false. `dispute_checked` false. Transition to `IN_REVIEW` allowed. Document state `IN_REVIEW`. `error_code` null. |
| AC-599-2 | `fixtures/spine-dependent/drg-599-contested-mapping-holds-document.json` | From `DRAFT`. Line `SWAP` source `SKU-A` target `SKU-B`. Pinned relationship version `v3`. `dispute_state` `CONTESTED`. Sales actor sets `dispute_state` `RESOLVED`. | Transition refused. Document state stays `DRAFT`. `apply_allowed` false. HTTP 409. `error_code` `MAPPING_CONTESTED`. Sales resolve HTTP 403. `error_code` `DISPUTE_RESOLVE_FORBIDDEN`. `dispute_state` stays `CONTESTED`. |
| AC-599-3 | `fixtures/spine-dependent/drg-599-pinned-version-clears-only-when-product-resolves.json` | Same line pinned to `v3`. Product actor sets pinned `v3` to `RESOLVED`. Live row `v4` is `CONTESTED`. | Pinned `v3` `dispute_state` `RESOLVED`. `hold_on_pinned_version` false. Transition to `IN_REVIEW` allowed. The pin stays `v3`. `error_code` null. |

### Inputs

- Line action, source SKU, target SKU, and the relationship version pinned at pricing.
- `dispute_state` of that pinned version (`ACTIVE`, `CONTESTED`, or `RESOLVED` under 599-F2 option A).
- Actor role for a dispute write: `SALES` or `PRODUCT`.
- Document state at the gate (`DRAFT` for these fixtures).

### Outputs

- Whether a mapping is required.
- Whether the document may leave `DRAFT`, and whether apply is allowed.
- The dispute state of the pinned version after the actor's write.

### State and storage fields

| Field | Home | On main `bf41c230` |
|---|---|---|
| Line action tokens | `V1_7__contract_documents.sql` lines 261–263 | Present, including `SWAP`, `UPGRADE`, `DOWNGRADE`. The check is not a mapping table. |
| Product relationship row | Proposed effective-dated catalog relationship, dispute state on that version **[I]** | Absent. `specs/catalog-model.md` is a separate proposal and is not accepted here. |
| Line pin | Proposed `relationship_version` on the change line **[I]** | Absent. |
| Separate mapping table | Option 599-F1 B **[I]** | Absent. Not the default. |

### Error codes

| Code | HTTP | When | Fixture |
|---|---|---|---|
| `MAPPING_CONTESTED` | 409 | Any line uses a pinned mapping whose dispute state is `CONTESTED` | AC-599-2 |
| `DISPUTE_RESOLVE_FORBIDDEN` | 403 | A sales actor sets the dispute state to `RESOLVED` | AC-599-2 |

AC-599-1 and AC-599-3 expect `error_code` null.

### As-is on main `bf41c230`

Product-service migrations on this tree have no `product_relationship` table **[S]**. The action-code check allows `SWAP` **[S]**. A contested mapping is a different hold from partial apply.

### Proposed choices

#### 599-F1 Where the mapping lives

| Option | Meaning |
|---|---|
| A | One effective-dated catalog relationship. Dispute state points at that version. |
| B | A separate amendment SKU mapping table. |
| C | A catalog row plus a commercial overlay. |

**Default:** Option A. This file does not accept `specs/catalog-model.md`.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 599-F2 Dispute status and who resolves it

| Option | Meaning |
|---|---|
| A | `ACTIVE`, `CONTESTED`, `RESOLVED`. Product resolves. Sales may open a dispute and may not clear it. |
| B | Add `REJECTED`. The hold remains. |
| C | Sales clears the hold. |

**Default:** Option A. No SLA and no notification sender are in this default. Those belong to DRG-649.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 599-F3 Where the hold bites

| Option | Meaning |
|---|---|
| A | The whole document waits before `IN_REVIEW`, immediately before `EXECUTED`, and again before apply. Each gate rechecks the pinned version. |
| B | Apply only. |
| C | Contested lines are partial-apply units. |

**Default:** Option A. The fixtures assert the `DRAFT` to `IN_REVIEW` gate. The same code applies at the other two gates under this option.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 599-F4 Which lines need a mapping

| Option | Meaning |
|---|---|
| A | A mapping is required only when the source SKU and the target SKU differ. |
| B | Every line requires a mapping, including same-SKU edits. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

#### 599-F5 Pin versus live row

| Option | Meaning |
|---|---|
| A | The line pins the relationship version at pricing. A later contest holds documents that pinned that version and are not yet executed. |
| B | Apply reads the live row. |

**Default:** Option A.

**Status:** Proposed

**Owner decision:** ___ (drg, time)

---

## What this file does not do

- It does not change code, configuration, or a migration.
- It does not start S3, call `contract_header_backfill_apply`, or enable a flag.
- It does not tick DRG-607 and it does not post the Hygiene reply.
- It does not mark a choice adopted. Every default above is **Status: Proposed** with **Owner decision: ___ (drg, time)** left blank.
- It does not design the `CONTRACT_MODIFICATION` writer. That writer is option B of 594-F6.
- It does not replace DRG-649. Dispute SLA, notifications, and the dispute UI stay in that follow-up.
