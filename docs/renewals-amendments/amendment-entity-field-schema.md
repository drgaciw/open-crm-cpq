# Amendment entity: field schema

*open-crm-cpq (drgaciw/open-crm-cpq), universal renewals-and-amendments project. Documentation only; no code changes. Date: 2026-10-16.*

**Binding target:** `mid-term-amendment-policy-spec.md` (V1 to V14, A1 to A8, six extension points, lifecycle states). **Conventions:** **[S]** = field or rule named in a project file (*spec*, *spike*, *rca*, *sanity*, *research*); **[I]** = inference or proposal made here. "OQn" = the spec's open questions; OQ1 to OQ8 are the eight undecided spike decisions. Where a field depends on one, the row says **dep OQn** and gives the default, which follows the spike recommendation unless noted. Types are logical (PostgreSQL-style), not a migration.

## 1. Placement and spine

- **quote-service** owns LifecycleChange (intent, ChangeLines, pricing calls, document render requests) [S rca 4; spike 6.1].
- **contract-asset-management (CAM)**, as a separate contract-documents module with its own schema and `/contracts/{id}/amendments` API, owns ContractHeader, AmendmentDocument, its revisions, lines, application units and the append-only event log [S rca 4]. The module never writes rate periods; the CAM ledger is the single writer of applied state [S rca 3]. (Placement is OQ1; this schema assumes the stated default.)
- **Spine plus policies.** One AmendmentDocument per LifecycleChange, whatever the intent (Renew, Amend, Cancel, Restructure, Transfer, Suspend, Resume, TrueUp, Convert) [S research 7.2]. Mid-term amendment, renewal and price-lock extension add no tables; they read, write and constrain spine fields through extension points (section 5) [S spec 7].
- **Salesforce analogue [S rca 1, 3]:** LifecycleChange is QuoteAction (intent); ContractHeader is the Contract record plus AssetContractRelationship; AmendmentDocument is a ContractDocumentVersion in the Contracts pillar; the applied rate periods are AssetStatePeriod; the line-to-period link is AssetActionSource. Action date stays separate from effective date, as on AssetAction.

Cross-service links are by value (uuid plus digest), never foreign keys across databases [S spike 2].

## 2. Entities and fields

Every CAM table also carries `tenant_id uuid not null`, `created_at timestamptz not null`, `created_by text not null` [S spike 1]. Mutable tables add `version int not null` (optimistic lock) and `updated_at/by`. Rows reaching a frozen state become immutable (A1).

### 2.1 LifecycleChange link (quote-service, referenced by value)

CAM stores no LifecycleChange table, only these copied keys on AmendmentDocument and its revisions.

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `lifecycle_change_id` | uuid | not null | unique on `amendment_documents` | spike 2 [S] |
| `lifecycle_change_revision` | int | not null | equals revision being snapshotted | spike 2 [S] |
| `intent` | enum (9 intents) | not null | V1: Amend for mid-term policy; registered via intent hook | research 7.2, V1 [S] |
| `initiation_trigger` | enum {Manual, LeadTime, TermEndAuto, Threshold, TrueUpCycle, EndOfLife, API} | not null | none | research 7.2 [S] |
| `request_identifier` | text | null | unique per tenant when present (headless idempotency) | rca 3, T2 [S]; uniqueness [I] |

### 2.2 ContractHeader (`contracts`)

Thin header, keyed so DRG-458 can enrich it. **dep OQ3**, default: create now.

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `id` | uuid | not null | PK | spike 6.3 [S] |
| `contract_number` | text | not null | unique (`tenant_id`, `contract_number`) | spike decision 4 [S] |
| `customer_account_id` | uuid | not null | today `subscriptions.customer_id` | spike 1 [S] |
| `term_model` | enum {Termed, Evergreen, Perpetual} | not null | none | research 7.2 [S] |
| `start_date`, `end_date` | date | not null; `end_date` null if Evergreen | `end_date >= start_date`; bounds V5 | V5 [S] |
| `governing_agreement_ref` | text | null | MSA or EULA version pin | spike 1 [S] |
| `status` | enum {ACTIVE, EXPIRED, TERMINATED} | not null | written only by CAM ledger on apply | [I] |
| `last_executed_seq` | int | not null | default 0; incremented in the EXECUTED transaction | A3 [I mechanism] |
| `prior_contract_id` | uuid | null | renewal chain | research 7.2 [S] |

### 2.2a Precedence flag (separate column, not part of the thin header)
Owner resolution 2026-10-16 (audit conflict C2, OQ27): the stored precedence flag on the new contract is a **separate, indexable column** on the contract record. It is **not** part of the thin header field set approved in ADR-035 (id, number, tenant, customer reference, start, end, status, last_executed_seq, prior_contract_id), and that list is unchanged.

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `precedence_flag` | boolean (shape provisional; boolean vs rank is open in DRG-592) | not null, default false | **indexed** (index on `tenant_id`, `precedence_flag` or a partial index on true rows; exact index is a DRG-592 design detail) | owner C2 resolution [S call]; shape [I] |

- Reason for a separate indexable column: it is checked on every read across ordering and fulfillment, so it must be cheap to filter and must not be derived from amendment history at read time.
- Not in S1 (DRG-579, amend-in-place first slice). It arrives with new-contract-preferred mode (RA-58 / DRG-592), the next increment.

`subscriptions.contract_id uuid null` is added (nullable during backfill, **dep OQ7**) [S spike 2].

### 2.3 AmendmentDocument (`amendment_documents`)

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `id` | uuid | not null | PK | spike 1 [S] |
| `contract_id` | uuid | not null | FK `contracts`; one contract per document | V1 [S] |
| `provisional_number` | text | not null | reserved at DRAFT, stable across revisions | spec 3 [S]; split from final number [I]; **RESOLVED OQ4** |
| `amendment_seq` | int | null until EXECUTED | unique (`contract_id`, `amendment_seq`); = `last_executed_seq + 1`; ORIGINAL is 0 | A3, spike 2 [S]; **RESOLVED OQ4** |
| `amendment_number` | text | null until EXECUTED | `{contract_number}-A{seq:03}` | spike decision 4 [S]; **RESOLVED OQ4** (default: no reset per renewal term) |
| `document_kind` | enum {ORIGINAL, AMENDMENT, RENEWAL, AMENDED_AND_RESTATED, TRUE_UP_NOTICE, TERMINATION, RESTRUCTURE, BACKFILLED} | not null | derived from intent, overridable | spike 1, 6.3 [S] |
| `status` | enum (spec 3 states) | not null | transitions only per spec 3 table | spec 3 [S] |
| `apply_status` | enum {NOT_STARTED, PARTIALLY_APPLIED, APPLIED, APPLY_BLOCKED} | null | used only if apply runs before EXECUTED | **RESOLVED OQ2**: column removed from v1; apply gated on EXECUTED, single `status` [decision 2026-10-16] |
| `current_revision_no` | int | not null | FK to revisions | spike 1 [S] |
| `executed_revision_no` | int | null | set once; never changed | V11 [S] |
| `signature_policy` | enum {WET, E_SIGN, CLICK_THROUGH, NOTICE_ONLY, NOT_REQUIRED} | not null | per intent default | spike 1 [S] |
| `approval_ref` | text | null | required to leave IN_REVIEW unless auto-approved | spec 3 [S] |
| `signature_envelope_ref` | text | null | unique when present | spike 3 [S] |
| `action_date` | date | not null | separate from effective date | V6, rca 3 [S] |
| `contract_effective_date` | date | not null | V5 bounds; before `action_date` needs reason | V5, V6 [S] |
| `service_activation_date`, `customer_acceptance_date` | date | null | none | spike 1 [S] |
| `backdating_reason_code` | text | null | not null when effective < action date | V6 [S rule; I field] |
| `co_term_date` | date | null | defaults to contract `end_date` | V5 [S] |
| `currency_code` | char(3) | not null | none | spike 1 [S] |
| `tcv_delta`, `arr_delta`, `one_time_total`, `credit_total` | numeric(19,4) | not null | digest-bound copy; never recomputed | spike 1 [S] |
| `content_digest` | char(64) | not null | SHA-256 canonical JSON of current revision | V10 [S] |
| `rendition_digest`, `signed_copy_sha256` | char(64) | null | signed hash required for EXECUTED via envelope | spec 3, spike 5 [S] |
| `template_id`, `template_version` | text | null | pinned at render | spike 1 [S] |
| `supersedes_document_id`, `superseded_by_document_id`, `voids_document_id` | uuid | null | self-FK; links added, facts never rewritten | spike 2 [S] |
| `evidence_class` | enum {STANDARD, LEGACY} | not null | LEGACY only for backfill | **dep OQ7**, default synthesize as LEGACY [S spike] |
| `retention_until` | date | null | set at EXECUTED or terminal state | R1, **dep OQ8** (default contract end + N, N unset) |
| `legal_hold` | boolean | not null | default false; blocks disposal | R2 [S] |
| `expires_at`, `executed_at`, `applied_at` | timestamptz | null | `executed_at` set once | spike 1 [S] |
| `is_deleted` | boolean | not null | must be false once EXECUTED | A1 [S] |

### 2.4 AmendmentRevision (`amendment_document_revisions`)

**dep OQ6**, default: new LifecycleChange revision creates a new document revision under the same number; at most one EXECUTED.

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `document_id`, `revision_no` | uuid, int | not null | PK (`document_id`, `revision_no`) | spike 2 [S] |
| `lifecycle_change_revision` | int | not null | unique (`document_id`, `lifecycle_change_revision`) | spike 2 [S] |
| `target_quote_id` | uuid | not null | becomes `accepted_quote_id` on apply | spike 2 [S] |
| `content_digest` | char(64) | not null | immutable | V10 [S] |
| `inputs_snapshot_ref` | text | not null | frozen schedule view and pins (A8) | spike 5 [S] |
| `status_at_close` | enum {OPEN, SUPERSEDED, EXECUTED, REJECTED, WITHDRAWN, EXPIRED} | not null | partial unique: one EXECUTED per document | V11 [S] |
| `frozen_at` | timestamptz | null | row immutable once set | A1 [I] |

### 2.5 AmendmentLine / ChangeLine (`amendment_document_lines`)

One row per ChangeLine per revision.

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `id` | uuid | not null | PK; referenced by rate periods (A4) | spike 5 [S] |
| `document_id`, `revision_no`, `line_no` | uuid, int, int | not null | unique triple | spike 1 [S] |
| `change_line_ref` | uuid | not null | LifecycleChange line by value | A4 [S] |
| `action_code` | enum {Add, Update, Remove, NoChange, Renew, Upgrade, Downgrade, Swap, ConvertMetric, Suspend, Resume, Terminate} | not null | allowed set per intent hook | research 7.2 [S] |
| `business_category` | enum (research 7.2) | not null | none | research 7.2 [S] |
| `apply_target_type` | enum {SUBSCRIPTION, ENTITLEMENT, ONE_TIME, NONE} | not null | none | spike 1 [S] |
| `target_subscription_id` | uuid | null | not null when SUBSCRIPTION; ACTIVE at action date | V1, V2 [S] |
| `source_accepted_line_uuid` | text | null | not null for Update, Remove, Swap, Upgrade, Downgrade; unique per revision; ACCEPTED_PRICED | V4 [S] |
| `result_accepted_line_uuid` | text | null | set on apply | spike 1 [S] |
| `quantity_semantics` | enum {Delta, Absolute, Replace} | not null | none | V7 [S] |
| `start_qty`, `delta_qty`, `end_qty` | numeric(19,4) | not null | check `start + delta = end`, `end >= 0` | V7 [S] |
| `metric` | text | null | none | spike 1 [S] |
| `effective_from`, `effective_to` | date | not null; `effective_to` null for perpetual | V5; `effective_to` changed only via term hook | V5, spec 7 [S] |
| `charge_type` | enum {Recurring, OneTime, Usage} | not null | default Recurring | sanity G6 [S] |
| `price_as_of` | enum {ActionDate, EffectiveDate, LineStart} | not null | default EffectiveDate [I] | sanity G1 [S] |
| `product_external_id`, `pricebook_version`, `configuration_hash` | text | not null | pinned at pricing; equal at apply | V9 [S] |
| `unit_price`, `net_amount` | numeric(19,4) | null for NONE lines | from pricing service only | V8 [S] |
| `policy_slot` | enum {Amendment, Renewal, PriceLock, TrueUp, Maintenance, Conversion} | not null | pricing hook | spec 7 [S] |
| `policy_id`, `policy_version`, `policy_decision_id`, `pricing_trace_id` | text | not null when priced | V8 | V8, A5 [S] |
| `segment_schedule` | jsonb | null | ordered, contiguous `{from, to, mode, value}` within line term | spec 7 hook 4 [S]; checks [I] |
| `override_value`, `override_reason_code`, `override_actor` | numeric, text, text | null | all or none; forces approval | V14 [I] |
| `application_unit_no` | int | not null | FK to unit | spike 1 [S] |

### 2.6 ApplicationUnit (`amendment_application_units`)

**dep OQ5**, default: partial application allowed, atomic units.

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `document_id`, `unit_no` | uuid, int | not null | PK | spike 2 [S] |
| `dependency_order` | int | not null | creates before terminates | V12 [S] |
| `target_type`, `target_ref` | enum, text | not null | one subscription or entitlement | spike 4 [S] |
| `observed_commercial_revision` | bigint | not null | equals `commercial_revision` at apply | V3 [S] |
| `applied_commercial_revision` | bigint | null | set on apply | spike 5 [S] |
| `apply_key_digest` | char(64) | null | stored replay value; (`document_id`, `unit_no`) is the unique apply key; different digest rejected | V13 [S] |
| `status` | enum {PENDING, APPLIED, BLOCKED} | not null | none | spike 4 [S]; enum [I] |

`applied_quote_acceptances` gains nullable `amendment_document_id` and `application_unit_no` (RA-29) [S spike 5].

### 2.7 AmendmentEvent (`amendment_document_events`, append-only)

| Field | Type | Null | Constraint | Source |
|---|---|---|---|---|
| `event_id` | uuid | not null | PK | A2 [S] |
| `document_id` | uuid | not null | FK | A2 [S] |
| `seq_no` | bigint | not null | unique (`document_id`, `seq_no`); = previous + 1, starts 1 | A2 [I] |
| `event_type` | text | not null | spike 6.6 names plus `ValidationRejected` | spike 6.6 [S]; extra [I] |
| `from_status`, `to_status` | enum | null | both set for transitions | A6 [S] |
| `revision_no`, `unit_no` | int | null | none | [I] |
| `actor_id`, `actor_type`, `actor_role` | text, enum {USER, SYSTEM, SERVICE}, text | not null | none | A6 [S] |
| `occurred_at`, `recorded_at` | timestamptz (UTC) | not null | `recorded_at` server-set | A6 [S]; `recorded_at` [I] |
| `reason_code` | text | null | required for rejections, backdating, overrides, void | V6, V14 [I] |
| `payload_digest`, `payload_ref` | char(64), text | not null | none | A6 [S] |
| `prev_hash` | char(64) | not null | equals `content_hash` of `seq_no - 1`; genesis constant for 1 | A2 [S] |
| `content_hash` | char(64) | not null | SHA-256 over canonical row minus itself (spec's `event_hash`) | A2 [S] |
| `source_event_id` | uuid | null | unique (inbox dedup) | spike 3 [S] |
| `correlation_id` | text | not null | links outbox export and evidence chain | A7 [S] |

Append-only is enforced by grants (INSERT only) and a trigger rejecting UPDATE and DELETE [I]. A verification job recomputes hashes and checks `seq_no` gaps (A2).

## 3. Relationships

- ContractHeader 1 : 0..* AmendmentDocument; 1 : 1..* subscriptions.
- LifecycleChange 1 : 0..1 AmendmentDocument (by value).
- AmendmentDocument 1 : 1..* AmendmentRevision (at most one EXECUTED); 1 : 1..* AmendmentLine; 1 : 1..* ApplicationUnit; 1 : 1..* AmendmentEvent; 0..1 supersedes, voids.
- AmendmentLine * : 1 ApplicationUnit; * : 0..1 source rate period (`source_accepted_line_uuid`); 1 : 0..1 PolicyDecision (pricing-service, by value).
- Rate period * : 1 AmendmentLine via new `amendment_document_line_id` (A4) [S spike 5].

## 4. Indexes, uniqueness and idempotency

| Purpose | Constraint | Rule |
|---|---|---|
| Draft idempotency | unique `amendment_documents(lifecycle_change_id)`; unique `events(source_event_id)` | spike 3 |
| Headless idempotency | unique (`tenant_id`, `request_identifier`) | T2 [I] |
| One executed revision | partial unique `revisions(document_id) where status_at_close = 'EXECUTED'`; `executed_revision_no` write-once | V11 |
| Gapless numbering | unique (`contract_id`, `amendment_seq`); seq assigned as `last_executed_seq + 1` under contract row lock in the EXECUTED transaction; check `amendment_seq >= 0` | A3 [I mechanism] |
| Apply idempotency | unique (`document_id`, `unit_no`); store `apply_key_digest` as replay data, same digest is a no-op and a different digest is rejected and audited | V13 |
| Envelope dedup | unique `signature_envelope_ref` | spike 3 |
| Source line uniqueness | unique (`document_id`, `revision_no`, `source_accepted_line_uuid`) where not null | V4 |
| Event chain | unique (`document_id`, `seq_no`); unique `content_hash` | A2 |
| Work queues | (`contract_id`, `status`); (`status`, `expires_at`); (`tenant_id`, `legal_hold`, `retention_until`) | [I] |

## 5. Policy binding

| Policy | Reads | Writes | Constrains | Extension points |
|---|---|---|---|---|
| Mid-term amendment (spec, first) | contract `start_date`, `end_date`; subscription status; `commercial_revision`; source rate periods | `intent = Amend`; lines with Add, Update, Remove, Swap, Upgrade, Downgrade; `policy_slot = Amendment`; `backdating_reason_code`; override fields | V1 to V14; `effective_to = co_term_date`; contract `end_date` unchanged; one contract | intent (baseline), pricing, validation, trigger (manual, API, click-through) |
| Renewal (RA-15, RA-33) | contract `end_date`, `term_model`; exit rates; RenewalPolicy | `intent = Renew`; `document_kind = RENEWAL`; `action_code = Renew`; `policy_slot = Renewal`; `price_as_of`; extended `effective_to`; new contract `end_date` and `prior_contract_id` on apply (ledger) | `effective_from = old end + 1` [S transcript 3.1]; numbering reset **RESOLVED OQ4** | intent, term, pricing, trigger (lead-time, term-end), validation |
| Price-lock extension (RA-34) | existing `segment_schedule`; line term; contract clause precedence | `segment_schedule` entries; `policy_slot = PriceLock`; `intent = Amend`, `action_code = Update` [I] | segments contiguous inside line term; may not move contract `end_date` (needs Renew) | pricing, segment, validation |

All three inherit spine rules V3, V8, V10, V12 and V13, which no policy may remove (spec hook 6) [S], and all audit rules A1 to A8.

## 6. Status columns summary

`amendment_documents.status` (spec 3, binding); `apply_status` (only if OQ2 resolves to apply before EXECUTED); `amendment_document_revisions.status_at_close`; `amendment_application_units.status`; `contracts.status`. Every change to any of them writes an AmendmentEvent in the same transaction (A6) [I].

## 7. Open questions

1. **Number timing (OQ4): RESOLVED 2026-10-16.** Provisional number at DRAFT, stable across revisions; final `amendment_seq` = last executed + 1 assigned at EXECUTED in the same transaction; format `{contract_number}-A{seq:03}`; no reset on renewal.
2. **Apply status (OQ2): RESOLVED 2026-10-16.** Single `status` column only; changes applied only after EXECUTED; no parallel apply-status track. Pre-signature application is an edge case, not a modeled lifecycle. The `apply_status` column is dropped from v1.
3. **Hash scope:** chain per document (proposed) or also a per-contract or global anchor for cross-document tamper evidence? [I]
4. **Price-lock intent:** does price-lock extension run under Amend, or register its own intent via the intent hook? [I]
5. **ContractHeader `status`:** owned by this module or derived from DRG-458 later (OQ3)? [I]
6. **Retention fields (OQ8):** N years and disposal method need Legal before `retention_until` is computed.


## Resolved decisions (2026-10-16)

- OQ4 numbering: provisional at draft, final sequence at execution (last executed + 1, same transaction), `{contract_number}-A{nnn}`, no renewal reset. Rationale: avoids gaps with in-flight drafts.
- OQ2 apply status: single status column, apply only after execution. Rationale: avoids dual-state consistency overhead under financial-grade audit; pre-signature apply is an edge case.


## Addendum 2026-10-16 (dev-readiness pass; nothing above changed)
- Migration order and next version: V1_7 expand (this schema), V1_8 contract header backfill, V1_9 `document_apply_ledger` (new, unique on `document_id`, `unit_no`, with `apply_key_digest` stored and checked on replay), legacy `applied_quote_acceptances` untouched. Details in `amendment-technical-approach-and-dependencies.md` section 3.
- Add nullable `amendment_document_line_id` and `revision_no` to `subscription_rate_periods` in V1_7 (A4).
- Open items 3 to 6 of section 7 are tracked as OQ14 (hash scope, default per document), OQ15 (price-lock intent, not blocking), OQ3 (header ownership, default thin header owned by this module), OQ8 (retention, blocks go-live only) in `renewals-amendments-open-questions-register.md`.

## Clarification 2026-10-16 (call decisions: precedence flag and original contract)
Source: transcript addendum "Renewals and amendments decisions audit"; backlog DRG-592 (RA-58).
- **Conflict.** ContractHeader (section 2.2) has `prior_contract_id` and `status` {ACTIVE, EXPIRED, TERMINATED} but no precedence field, and `rca-contract-precedence-research.md` inferred precedence is derived from the amendment chain. The call decided a **stored precedence flag on the new contract**; both contracts stay **ACTIVE** and the original stays **open as a reference** (no SUPERSEDED or closed status for the original).
- **Not changed in v1 schema (S1, DRG-579).** First slice is amend-in-place, so S1 carries no new column. The flag shape (boolean vs rank), the link from the new contract to the original (`prior_contract_id` is the candidate) and who may change it are open and sit in DRG-592.
- **Coterm and settings.** Customer-level coterm grouping and renewal groups (DRG-554) and renewal settings, auto-renew default off and notice period default with override (DRG-596), are not in this schema; `notice_period_days` and `term_end_action` stay clause data, settings live elsewhere.

**C2 resolved 2026-10-16 (owner).** The stored precedence flag lives in its own indexable column (`precedence_flag`, section 2.2a), outside the thin header. The ADR-035 header list is not altered. See OQ27 in `renewals-amendments-open-questions-register.md`.
