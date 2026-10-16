# Approval requirements for renewals and amendments: configuration-driven skeleton

*open-crm-cpq, design only. No code changes. Date: 16 Oct 2026.*

> **SKELETON. NOT DECIDED.** This is a generic starting point for the separate approvals conversation. It proposes a shape and a vocabulary, not answers. Every default, name and value below is a placeholder for product, finance, legal and sales ops to confirm. It is tracked as the parked item PL-9 (`renewals-amendments-parking-lot.md`), RA-57 / DRG-591, and as its own parked item RA-67 / DRG-601. **Not an active development story. No estimate, no code.** The first slice defaults (OQ10, OQ17) stand until this is unparked.

**Conventions**
- Statements marked **[INFERENCE]** are design proposals.
- State names (DRAFT, IN_REVIEW, APPROVED, OUT_FOR_SIGNATURE, EXECUTED, APPLIED and so on) and `approval_ref` come from `amendment-document-design-spike.md`.
- Conditional logic belongs in Drools (PL-8). Composition data stays plain. This doc keeps approval behavior as plain data and delegates any non-trivial condition to a named Drools rule.

## 1. Goals and the principle
- Approval behavior (who, when, how many, how long, what it blocks) is **data**, edited by an admin, versioned, effective-dated and audited. No approval rule is compiled into a service.
- One generic engine answers: given a subject (amendment document, renewal quote, coterm event) and its context, which approval requirements apply, and what is the resulting chain.
- Per tenant and per contract type overrides layer on a global default.
- The engine reuses the existing approval-routing service for execution where it can (section 9), instead of adding a second approvals system.

## 2. Approver model
An approver reference is a typed value, resolved at routing time.

| Kind | Example | Notes |
|---|---|---|
| ROLE | `SALES_OPS`, `FINANCE_APPROVER`, `LEGAL_REVIEWER` | Any member of the role may act. Roles are tenant scoped. |
| USER | a named user id | For fixed exceptions. Avoid for steady state, since people leave. |
| DYNAMIC | `contract_owner`, `account_owner`, `manager_of(contract_owner)`, `manager_of(manager_of(...))`, `sales_ops(region)`, `finance(region)` | Resolved from CRM and org data when the chain is built. Needs a resolver registry. |
| GROUP | a saved list of users or roles | Convenience for parallel steps. |

- **Resolution:** a resolver turns a DYNAMIC reference into users at build time and records the result on the step (who it resolved to and from which data version), so a later org change does not silently change a live chain.
- **Fallback:** each DYNAMIC reference declares a fallback (for example `manager_of` falls back to the role `SALES_OPS`) for when resolution returns nothing.
- **Self-approval:** a policy flag `allow_self_approval` (default false). The requester never approves their own change.
- **Delegation and out-of-office:** a user can set a delegate with a date range. The engine routes to the delegate while the range is active, and records both identities. Delegation cannot skip the self-approval rule and cannot exceed the delegator's approval limit.
- **Authority limit:** an optional per-role or per-user monetary or delta limit. An approver below the needed limit forwards to the next level.

## 3. Triggers
A trigger says when an approval requirement exists. It is a condition over an **event** and the **context** (a read-only snapshot of the subject).

**Events (first set):** `AMENDMENT_SUBMITTED`, `AMENDMENT_FIELD_CHANGED`, `COTERM_PROPOSED`, `RENEWAL_QUOTE_SUBMITTED`, `PRICE_OVERRIDE`, `PRICE_LOCK_OVERRIDE`, `BACKDATING`, `CANCELLATION_REQUESTED`, `PARTIAL_APPLICATION_REMEDY`, `VOID_REQUESTED`.

**Condition forms** (data, evaluated by a small generic operator set):
- Field predicates: `eq, ne, gt, gte, lt, lte, in, not_in, between, changed, is_null`.
- Boolean: `all`, `any`, `not`.
- Named rule: `{"drools_rule": "ApprovalNeedsLegalReview"}` for anything the operators cannot express, kept in Drools (PL-8).

**Candidate gates** (from PL-9, with source):
1. Coterm events: approval required, approver undecided (coterm research, RA-20 / DRG-554).
2. Amendment submit with total ARR delta above a threshold, any override, or backdating: the OQ17 default.
3. Discount or uplift overrides (V14, FR-18, S5).
4. Price-lock overrides (price-lock stub, OQ15).
5. Cancellations (PL-7).
6. Partial application remedy (OQ5).
7. Backdating (V6, FR-17) and VOID of an executed document (OQ13).
8. **Legal-review-gated fields:** per RA-60 / DRG-594, an amendment may skip legal review only if it changes pricing, term or seats and nothing else. Expressed as data: trigger `LEGAL_REVIEW` when `changed_fields` contains anything outside `{pricing, term, seats}`. Open: add and remove lines are outside that set (conflict C5), so whether they trigger legal is undecided.
9. Discount depth: for example `discount_pct > X` routes to a higher level.

A requirement carries a `reason_code` shown to the approver and stored on the decision.

## 4. Chains
A **chain template** is an ordered list of **steps**. Each step has an approver set and a completion rule.

| Pattern | Definition |
|---|---|
| Single | one step, one approver or role, `any` |
| Sequential | steps run in order. A rejection at any step ends the chain (or returns to the requester, by policy). |
| Parallel | one step with several approvers. Completion rule `all`, `any`, or `quorum(n)`. |
| Mixed | sequential steps where each step may itself be parallel, for example step 1 manager `any`, step 2 finance and legal `all`. |

- **Multiple requirements:** when several triggers fire, their chains are merged by policy `merge_mode`: `UNION` (all steps, deduplicated by approver), `HIGHEST` (only the strictest chain), or `SEQUENCE_BY_PRIORITY`. Default is a placeholder: UNION.
- **Decisions:** APPROVE, REJECT, RETURN_FOR_CHANGES, ABSTAIN (quorum only). A comment is required on REJECT and RETURN.
- **Revision rule:** a new document revision (revision_no change, content_digest change) invalidates open approvals and restarts the chain, matching the spike rule that a transition is accepted only with the matching `revision_no` and `content_digest`.

## 5. Thresholds and routing criteria
Routing selects the chain template for a fired requirement. It is a **decision table** (first match wins by priority) over context attributes.

| Dimension | Examples |
|---|---|
| Value | total ARR delta, TCV, line delta, discount percent, uplift percent |
| Customer tier | strategic, enterprise, mid-market |
| Contract type | SaaS, on-prem, hybrid (first slice is SaaS) |
| Intent | Add, Update, Remove, Renew, Coterm, Cancel |
| Tenant | the owning tenant, with an override scope |
| Region | for example finance and sales ops by region |
| Delta size | absolute and relative change versus the contract |
| Currency | thresholds in a base currency, converted per ADR-032 `base_amount` |
| Flags | override present, backdated, legal-gated field changed |

- Thresholds are **named values** (for example `ARR_DELTA_L1`) stored in a threshold table with tenant and contract-type scope and effective dates, so Finance changes a number without editing a rule. OQ17 says Finance sets the initial value.
- Bands are half-open (`[min, max)`) to avoid overlap and gaps. A validation check flags a gap or overlap at publish time.

## 6. Escalation and timeout
Per step, with defaults inherited from the chain template, then the rule set:
- `sla_hours` and `business_calendar` (tenant calendar, holidays).
- `reminders`: offsets before the SLA (for example 24h, 4h) and a repeat interval, sent to the approver and optionally a delegate.
- `on_timeout`: one of `ESCALATE`, `AUTO_APPROVE`, `AUTO_REJECT`, `EXPIRE`, `NOTIFY_ONLY`.
  - `ESCALATE` goes to a named `escalate_to` reference (for example `manager_of(approver)`), with a limit on escalation depth.
  - `AUTO_APPROVE` is allowed only where the rule set explicitly permits it, never for override, backdating, VOID or legal-gated requirements. **[INFERENCE]** Default should be no auto-approve.
  - `AUTO_REJECT` returns the document to DRAFT with a reason.
- Every timeout action is recorded as a decision by a system actor, with the rule and version that caused it.
- Pause rules: SLA clock pauses while a step is in RETURN_FOR_CHANGES.

## 7. Interaction with the amendment lifecycle
Each requirement has an `enforcement` mode saying what it does to document state.

| Mode | Meaning |
|---|---|
| BLOCK | the document cannot leave the named state until the requirement is satisfied |
| NOTIFY | the chain runs and informs approvers, nothing is held |
| ALLOW_WITH_FLAG | processing continues, the document and audit log carry a flag `approval_pending`, and a later rejection raises a remediation task |

Mapping to the spike's states (**[INFERENCE]**, all placeholders):

| Document state or transition | Typical gate | Default mode (proposed) |
|---|---|---|
| DRAFT to IN_REVIEW (submit) | trigger evaluation and chain build | none, evaluation only |
| IN_REVIEW to APPROVED | chain completion. `approval_ref` stores the approval-routing request id. A re-delivered approval for the same revision is a no-op. | BLOCK |
| APPROVED to OUT_FOR_SIGNATURE or EXECUTED | legal review requirement (RA-60) must be satisfied | BLOCK |
| EXECUTED to APPLIED | normally none, the apply gate is EXECUTED | n/a |
| PARTIALLY_APPLIED, APPLY_BLOCKED | remedy approval (OQ5) | BLOCK on the correcting amendment |
| any state to VOID or WITHDRAWN | VOID after EXECUTED needs approval (OQ13) | BLOCK |
| Coterm group | coterm approval on the group | BLOCK for member documents, or ALLOW_WITH_FLAG, undecided |

- `approval_ref` identifies the active chain instance. With multiple requirements it may become a list or a parent id **[INFERENCE]**. This is an open question (section 10).
- Auto-approve below threshold (the OQ17 default) is a rule outcome, not a bypass: it produces a decision record with `decided_by = SYSTEM` and the matching rule version.
- CAM alone writes applied state (ADR-035). The approval engine decides only whether a transition is permitted.

## 8. Configuration data model
All of this is data. Placement (approval-routing, CAM or a new config service) is open.

### 8.1 Tables (logical)
| Table | Purpose and key columns |
|---|---|
| `approval_rule_set` | `id`, `name`, `scope_type` {GLOBAL, TENANT, CONTRACT_TYPE}, `scope_id`, `version`, `status` {DRAFT, PUBLISHED, RETIRED}, `effective_from`, `effective_to`, `parent_rule_set_id` (override base), `created_by`, `published_by` |
| `approval_rule` | `id`, `rule_set_id`, `priority`, `event_type`, `condition_json`, `requirement_code`, `reason_code`, `enforcement`, `blocks_state`, `chain_template_ref`, `routing_table_ref`, `enabled` |
| `approval_threshold` | `id`, `name`, `scope`, `value`, `unit`/`currency`, `effective_from`, `effective_to`, `version` |
| `approver_mapping` | `id`, `scope`, `role_code`, `resolver_kind`, `resolver_expr`, `region`, `fallback_ref`, `authority_limit`, `effective_from`, `effective_to` |
| `approval_chain_template` | `id`, `name`, `version`, `merge_mode`, `steps_json` (ordered steps with approver refs, completion rule, SLA, reminders, `on_timeout`) |
| `approval_routing_row` | decision-table rows: `table_id`, `priority`, `match_json`, `chain_template_ref` |
| `approval_delegation` | `delegator`, `delegate`, `from`, `to`, `limit`, `reason` |
| `approval_instance` | one per live chain: `id`, `subject_type`, `subject_id`, `revision_no`, `content_digest`, `rule_set_version`, `status`, `approval_ref` link |
| `approval_decision` | append-only: `instance_id`, `step`, `approver`, `acting_for`, `decision`, `comment`, `decided_at`, `rule_version`, `resolved_from` |

### 8.2 Overrides, versioning and effective dating
- **Resolution order (most specific wins):** contract-type scoped, then tenant scoped, then global. A scoped rule set may `override` or `disable` a base rule by `requirement_code`, or add new rules. It never edits the base.
- **Versioning:** a published rule set version is immutable. A change creates a new DRAFT version, which is validated, simulated and then published with an `effective_from`.
- **Effective dating:** the version in force at the **evaluation time of the document's submit** is pinned on the `approval_instance` (`rule_set_version`). In-flight chains finish under their pinned version, unless an admin explicitly re-evaluates.
- **Validation at publish:** no threshold band gaps or overlaps, every approver reference resolvable, every chain template referenced exists, no unreachable rules, `AUTO_APPROVE` only where permitted.

### 8.3 Audit
- Decisions and rule set publish events are appended to the hash-chained audit log from S2 (`amendment-technical-approach-and-dependencies.md`). Key management for the chain remains open.
- Each decision stores who, acting for whom, which rule and version fired, and which context values it saw, so a decision can be explained later.

### 8.4 Simulation and dry run
- A **dry-run** endpoint takes a subject snapshot (or a historical document) and a rule set version (published or draft) and returns the fired requirements, the resolved chain, the approvers, and the enforcement per state, with no side effects.
- **Backtest:** replay a set of past amendments against a draft version and diff the outcome versus what actually happened (more, fewer, different approvers). Needed before Finance changes a threshold.

### 8.5 Example rule set (YAML, illustrative only, not a decision)
```yaml
rule_set:
  name: renewals-amendments-default
  scope: { type: GLOBAL }
  version: 3
  effective_from: 2026-11-01
  merge_mode: UNION
  thresholds:
    ARR_DELTA_L1: { value: 25000, currency: USD }
    ARR_DELTA_L2: { value: 100000, currency: USD }
  chain_templates:
    mgr_only:
      steps:
        - { name: manager, approvers: [ "manager_of(contract_owner)" ], complete: any,
            sla_hours: 24, reminders: [ 8, 2 ], on_timeout: ESCALATE, escalate_to: "manager_of(approver)" }
    mgr_then_finance:
      steps:
        - { name: manager, approvers: [ "manager_of(contract_owner)" ], complete: any, sla_hours: 24, on_timeout: ESCALATE }
        - { name: finance, approvers: [ "role:FINANCE_APPROVER@region" ], complete: any, sla_hours: 48, on_timeout: NOTIFY_ONLY }
    legal_parallel:
      steps:
        - { name: review, approvers: [ "role:LEGAL_REVIEWER", "role:SALES_OPS" ], complete: { quorum: 2 }, sla_hours: 72 }
  rules:
    - code: ARR_DELTA_APPROVAL
      event: AMENDMENT_SUBMITTED
      when: { any: [ { field: arr_delta_abs, gte: "$ARR_DELTA_L1" },
                     { field: has_override, eq: true },
                     { field: backdated, eq: true } ] }
      route:
        - { match: { arr_delta_abs: { gte: "$ARR_DELTA_L2" } }, chain: mgr_then_finance }
        - { match: {}, chain: mgr_only }
      enforcement: BLOCK
      blocks_state: IN_REVIEW
    - code: LEGAL_REVIEW_GATE
      event: AMENDMENT_SUBMITTED
      when: { field: changed_fields, not_subset_of: [ pricing, term, seats ] }
      route: [ { match: {}, chain: legal_parallel } ]
      enforcement: BLOCK
      blocks_state: APPROVED
    - code: COTERM_APPROVAL
      event: COTERM_PROPOSED
      when: { field: group_size, gte: 2 }
      route: [ { match: {}, chain: mgr_then_finance } ]   # approver undecided, placeholder
      enforcement: BLOCK
    - code: DISCOUNT_DEPTH
      event: PRICE_OVERRIDE
      when: { drools_rule: DiscountDepthExceeded }
      route: [ { match: { customer_tier: strategic }, chain: mgr_then_finance }, { match: {}, chain: mgr_only } ]
      enforcement: ALLOW_WITH_FLAG
  overrides: []   # tenant and contract-type sets list overrides by rule code, for example { code: COTERM_APPROVAL, disable: true }
```
All values above (amounts, SLAs, roles, enforcement) are placeholders.

### 8.6 Relation to Drools, approval-routing, OQ17 and OQ25
- **Drools (PL-8):** plain data covers the common operators and routing tables. Anything conditional beyond that is a named Drools rule referenced from `condition_json`. Approver mappings and chain templates stay plain composition data.
- **Existing approval-routing service:** it already exists and `ApprovalType` includes `CONTRACT_MODIFICATION` (see the design spike). Not verified for this skeleton: per-gate approver roles, delegation, parallel steps, escalation, a coterm type, and tenant-level config. The first task of the approvals conversation is a capability check. Options: (a) extend approval-routing to host this model, (b) keep a thin rule layer that builds requests for approval-routing, (c) a new service. **[INFERENCE]** (b) is the lowest risk.
- **OQ17:** the proposed default (approval only for overrides, backdating or ARR delta above a Finance-set threshold) is expressible as the `ARR_DELTA_APPROVAL` rule above. This skeleton does not change it.
- **OQ25:** this skeleton is the starting point for OQ25 (approver for coterm and other gates deferred). It resolves none of it.

## 9. Non-goals
- No decision on who approves any gate. No approver names, roles or thresholds are proposed as final.
- No code, schema migration, API or UI. No estimate.
- Not a replacement for e-signature or signature policy (OQ10, DRG-457).
- Not a general workflow engine for non-renewal, non-amendment approvals (for example discount approvals on new quotes), although the model should not preclude reuse.
- No change to ADR-035 placement or the first slice (S1 to S8).
- No billing, refund or proration logic (PL-7).

## 10. Open questions
1. Who approves each gate: sales ops, contract owner, manager chain, finance or legal by threshold. Coterm first (OQ25).
2. Where does the engine live: approval-routing, CAM or a new service. What does approval-routing support today (roles, delegation, parallel, escalation)?
3. Is `approval_ref` a single id or a list when several requirements fire? Merge mode default?
4. Enforcement per gate: BLOCK, NOTIFY or ALLOW_WITH_FLAG. Is ALLOW_WITH_FLAG acceptable to Finance and Legal at all?
5. Are add and remove lines legal-gated (conflict C5 with RA-60)?
6. Timeout policy: is auto-approve ever allowed? Business calendar and SLA per tenant?
7. Threshold ownership and change control: who edits, who publishes, is four-eyes needed on rule set changes? Initial values (OQ17, Finance).
8. Do coterm approvals apply per group or per member document (conflict C4: one document per contract)?
9. Do in-flight approvals follow the pinned version or re-evaluate when a rule set changes?
10. Delegation limits and audit expectations for out-of-office routing.
11. Does an executed-document VOID (OQ13) need a different or higher chain than pre-execution withdraw?
12. Hash chain scope and key management for decision records (OQ14 and the open key question).
13. Per-tenant versus per-contract-type override precedence when both exist.
14. Pricing-service amendment modes (OQ9) and how a pricing change event reaches the engine.

## 11. Related documents
- `renewals-amendments-parking-lot.md` (PL-9, PL-8, PL-7)
- `amendment-document-design-spike.md` (states, `approval_ref`, approval flow)
- `renewals-amendments-open-questions-register.md` (OQ5, OQ10, OQ13, OQ14, OQ15, OQ17, OQ25)
- `coterm-renewal-quote-research.md`, `mid-term-amendment-policy-spec.md`, `price-lock-policy-stub.md`
- `adr-035-amendment-document-placement.md`
- Tracking: RA-57 / DRG-591 (parked approvals category), RA-67 / DRG-601 (this skeleton), RA-60 / DRG-594 (legal-review gating)
