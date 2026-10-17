# Cluster E — LifecycleChange model decision frame

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Base:** `main` at `0d71251d` (includes merged [#642](https://github.com/drgaciw/open-crm-cpq/pull/642), merge `3ee3ca0f`).

**Stories:** [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548) (RA-14, unified intent), [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) (RA-18, action codes), [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) (RA-19, action date versus trigger dates). Linear comments on all three were empty when read on 2026-10-17. Relations are listed under each story.

**Cluster:** E, lifecycle-change model. One note. One section per ticket.

Accepting this note authorizes no code, migration, or deploy. No flag is enabled. Nothing is deployed.

## How to read this frame

Every fork has options, a recommendation, and a default. Each default is **Proposed, unaccepted, owner decision required.** The alternatives sit beside that default. Adoption is a later act by the owner. This file does not perform it.

**Read** means the cited file was opened and the cited lines were read against `main` at `0d71251d`. **Search** means a text search, not a running process. **Unverified** means the claim was not opened, not executed, or the search can miss it.

Boundaries that stay as they are:

- [ADR-035](../adr-035-amendment-document-placement.md) is **Accepted** for placement and for the first-slice schema and audit stories only (S1, S2; DRG-579, DRG-580). Read: status line and the acceptance paragraph. Later stories still need their own gates.
- [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) and [ADR-024](../../adr/ADR-024-order-orchestration-context.md) through [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) are **Proposed**. Read: `docs/adr/README.md` catalog rows. This frame does not treat them as accepted and does not choose a transport, an order saga, a command topic, or a compensation path.
- The amendment proration formula in [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) is **Accepted** (2026-10-16). Read: status line and Decision D-01 (the day-stub). This frame names which stored date fills that formula's existing effective-date input. It does not reopen the formula, the scale, the billing-period stub, or the Drools rule disposition.
- S6 is not authorized. This frame defines no event type, topic, registry entry, or outbox contract.
- S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) was gated on [#642](https://github.com/drgaciw/open-crm-cpq/pull/642). That pull request merged to `main` on 2026-10-17. OQ6 is adopted only as recorded there and in [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) and the OQ6 row of [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md): the revision rule, and no code or story. The register says S3 still needs separate owner authorization. This frame does not start S3.
- The [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) design in [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) is a proposal. Read: its status paragraph. It is not accepted. OQ9 in the register still blocks S5 until pricing-service capability is verified. Read: register row OQ9.
- [specs/lifecycle-intents.md](specs/lifecycle-intents.md) is an earlier proposal. Read: its status line and proposed contract. This frame does not edit that file and does not accept it. Where it disagrees with the S1 migration on `main`, the migration is what is stored.

CPQ Decision classified these three from titles. The descriptions were read. None is skipped. Each description also contains an implementation-shaped next step (extend an enum, define a contract, add date columns). Those steps are answered below as already stored, or withheld. This file performs none of them.

## GAP — quote-service has no LifecycleChange type

This is a gap. It is not a fork and not a decision.

Search of `backend/services/quote-service` for `LifecycleChange` and `lifecycle-changes` in `*.java`, `*.yml`, and `*.yaml` returned no matches (2026-10-17). Search of the repo for `class LifecycleChange` and `record LifecycleChange` returned no Java type. Quote-service controllers that were opened map `/api/v1/quotes` and `/api/v1/quotes/continuations` (`backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteController.java`, `QuoteContinuationController.java`). None map `lifecycle-changes`.

CAM stores the id, not the type. Migration `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql` lines 49–50 declare `lifecycle_change_id uuid NOT NULL` and `lifecycle_change_revision integer NOT NULL`. Line 95 is `uq_amendment_document_lifecycle UNIQUE (lifecycle_change_id)`. That column has no `REFERENCES` clause in the statements read (lines 45–136). The Java mapping is `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/AmendmentDocument.java` lines 40–44. `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/repository/documents/AmendmentDocumentRepository.java` declares `findByLifecycleChangeId`. That method looks up the column. It does not define a LifecycleChange type.

`docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` names `/lifecycle-changes`. Read: `x-opencrm-status: proposal` at line 66. That file is a proposal.

A running process, a gateway route dump, and a database catalog were not queried. **Unverified by execution.**

ADR-035 D-03 (read) places LifecycleChange in quote-service. The type's absence does not change that placement and does not decide who authors intent. Fork E2 is the authoring choice for a type that is not in the tree.

## Owner-decision table

Every row is **Proposed, unaccepted, owner decision required.** The default is the choice this note would use if the owner later adopts it. Alternatives are the other options in that fork. None of them is accepted by this file.

| Fork | Question | Default | Alternatives |
|---|---|---|---|
| E1 | Document intent vocabulary | A. Keep the nine tokens in `AmendmentIntent` and `chk_amendment_document_intent` | B. Add `EXTEND` and `EXPAND` (migration). C. Replace the nine with `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION` and drop Cancel, Restructure, Suspend, Resume, Transfer, TrueUp, and Convert |
| E2 | Who authors intent once a LifecycleChange type exists | A. Quote-service authors it. CAM copies it onto `amendment_documents.intent` (ADR-035 D-03) | B. CAM authors it and quote-service mirrors it. C. `QuoteContinuationRequest.Operation` is the only intent and the document column is derived |
| E3 | Default policy per intent | A. No default-policy table. A future first-slice submit uses intent `AMEND` and slot `AMENDMENT` only | B. Publish a static intent-to-slot map and do not enforce it. C. The seller picks `policy_slot` with no default from intent |
| E4 | Restructure versus convert | A. Keep both intents. Neither selects a policy until DRG-573. `CONVERT_METRIC` stays a line action | B. `RESTRUCTURE` selects `policy_slot = CONVERSION`. C. Collapse `CONVERT` into `RESTRUCTURE` (migration) |
| E5 | Extension versus renew on the document | A. A future document for quote `continuation_operation = EXTENSION` stores intent `RENEW`. No new token. No backfill | B. Add document intent `EXTEND` (migration). C. Backfill historical quotes |
| E6 | Expansion | A. Quote `EXPANSION` stays `DealType.EXPANSION`. A future document copies intent `AMEND` | B. Add document intent `EXPAND` (migration). C. Store expansion as intent `RENEW` |
| E7 | Which intents a future submit may send | A. Storage allows nine. A future first-slice submit allows `AMEND` only | B. Enable `RENEW` behavior in this decision. C. Enable all nine |
| L1 | Closed action-code set | A. Keep the twelve tokens in `chk_amendment_line_action` | B. Migrate in `SURRENDER`, `CANCEL`, `CONVERT`, and `DELETE`. C. Shrink the check to `ADD`, `UPDATE`, `REMOVE` |
| L2 | Future first-slice submit set | A. `ADD`, `UPDATE`, `REMOVE` with `DELTA` or `ABSOLUTE` only | B. Accept every stored action and every semantics value. C. Also accept `NO_CHANGE` and `RENEW` |
| L3 | Quantity identity, including `REPLACE` | A. Keep `chk_amendment_line_qty` (`start + delta = end`, `end >= 0`) for every semantics value | B. Relax the check so `REPLACE` may omit delta (migration). C. Remove `REPLACE` from the enum (migration) |
| L4 | Who sets `business_category` | A. One stored value, derived for a future first-slice writer from action and the sign of `delta_qty` (table under L4) | B. The seller types the category. C. Add compound categories, `CONVERSION`, or `OTHER` (migration) |
| L5 | Per-intent action allow-list | A. No new database constraint. The only pair a future first-slice submit allows is `AMEND` with `ADD`, `UPDATE`, or `REMOVE` | B. Record a matrix for later enforcement and do not enforce it now. C. Add a SQL trigger from intent to action (migration) |
| L6 | Source line required | A. Leave `chk_amendment_line_source_required` as written | B. Extend it to `TERMINATE`, `SUSPEND`, `RESUME`, `RENEW`, and `CONVERT_METRIC` (migration). C. Drop the check (migration) |
| L7 | Category for a conversion line | A. Write no conversion line in the first slice. No new token. DRG-573 chooses later | B. Store conversion lines as category `SWAP` until DRG-573. C. Add category `CONVERSION` (migration) |
| D1 | Which date fills the Accepted proration formula | A. `price_as_of` selects the input: `EFFECTIVE_DATE` → `contract_effective_date`, `ACTION_DATE` → `action_date`, `LINE_START` → line `effective_from`. Future first-slice writer sets `EFFECTIVE_DATE` | B. Always price from `action_date`. C. Always price from `customer_acceptance_date`. D. Change the proration formula |
| D2 | Service activation and customer acceptance | A. Both columns stay nullable and are not proration inputs | B. Require both on every document (migration). C. Require both only when `signature_policy` is `WET` or `E_SIGN` |
| D3 | Backdating | A. Keep `chk_amendment_document_backdating`. Approval thresholds stay on S3 | B. Extend the check to `service_activation_date` and `customer_acceptance_date` (migration). C. Forbid backdating and drop the reason-code escape (migration) |
| D4 | Header dates versus the line service period | A. Header keeps the four dates. Line keeps `effective_from` / `effective_to`. Future first-slice lines set `effective_from` equal to `contract_effective_date` | B. Copy all four trigger dates onto every line (migration). C. Drop the header dates and keep only line dates (migration) |
| D5 | Quote continuation request dates | A. Leave `QuoteContinuationRequest` unchanged until a separately authorized S5 change | B. Add the four dates to `QuoteContinuationRequest` now |
| D6 | Dates on `subscription_rate_periods` | A. Add no date columns. Keep the V1_6 bounds and the V1_7 line trace | B. Add `action_date` and the three trigger dates on each rate period (migration). C. Add only a copy of the pricing effective date (migration) |

## Shared reconciliation: what S1/S2 already stored

S1 schema file, read: `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`.

S2 writer, read (class comment and the start of `append`, lines 1–40): `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java`. The comment says the class appends one hash-chained event and its audit outbox row (DRG-580). The rest of `append` was not re-read this pass. **Unverified** beyond those lines.

Java enums, read: `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/`.

### Already on main

| Fact | File path | Migration |
|---|---|---|
| Document intent is the closed set `RENEW`, `AMEND`, `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT` | `.../domain/documents/AmendmentIntent.java`; check `chk_amendment_document_intent` at lines 100–102 of the S1 migration. ADR-035 D-03 and D-05 name the same nine (read) | `V1_7__contract_documents.sql` |
| One document per `lifecycle_change_id` | Unique constraint `uq_amendment_document_lifecycle`, S1 migration line 95; `AmendmentDocument.java` lines 23–25 | `V1_7__contract_documents.sql` |
| Document status is one column: `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID` | `AmendmentDocumentStatus.java`; `chk_amendment_document_status` lines 110–113. `AmendmentDocument.java` javadoc, read: state transitions are DRG-581; this mapping stores the row | `V1_7__contract_documents.sql` |
| Signature policy is `WET`, `E_SIGN`, `CLICK_THROUGH`, `NOTICE_ONLY`, `NOT_REQUIRED` | `SignaturePolicy.java` (javadoc, read: e-sign execution is out of slice, DRG-457); `chk_amendment_document_signature` lines 114–116. ADR-035 acceptance confirms slice policies `NOT_REQUIRED`, `NOTICE_ONLY`, `CLICK_THROUGH` (OQ10), read | `V1_7__contract_documents.sql` |
| `amendment_document_events` is append-only. Status from/to use the document status set. Actor type is `USER`, `SYSTEM`, or `SERVICE`. Hashes are 64 lowercase hex. `event_type` is `varchar(80)` with no closed check | Table lines 305–346; function `reject_amendment_document_event_mutation` and triggers `trg_amendment_document_events_append_only` and `trg_amendment_document_events_no_truncate` lines 370–388 (UPDATE, DELETE, and TRUNCATE raise `42501`). `AmendmentDocumentEvent.java` | `V1_7__contract_documents.sql` |
| Line action, category, quantity semantics, charge type, `price_as_of`, and policy slot are closed checks | Lines 261–275. Enums: `ActionCode.java`, `BusinessCategory.java`, `QuantitySemantics.java`, `ChargeType.java`, `PriceAsOf.java`, `PolicySlot.java` | `V1_7__contract_documents.sql` |
| Quantity identity `start_qty + delta_qty = end_qty` and `end_qty >= 0` applies to every semantics value, including `REPLACE` | `chk_amendment_line_qty` line 276. Columns lines 228–230, `numeric(19,4) NOT NULL` | `V1_7__contract_documents.sql` |
| Header dates: `action_date` and `contract_effective_date` are `date NOT NULL`. `service_activation_date` and `customer_acceptance_date` are nullable. Backdating of the contract effective date requires `backdating_reason_code` | Columns lines 64–68; `chk_amendment_document_backdating` lines 129–131; `AmendmentDocument.java` lines 90–103 | `V1_7__contract_documents.sql` |
| Line service period: `effective_from date NOT NULL`; `effective_to` null or on/after `effective_from` | `AmendmentDocumentLine.java` lines 85–88; `chk_amendment_line_dates` line 277 | `V1_7__contract_documents.sql` |
| `UPDATE`, `REMOVE`, `SWAP`, `UPGRADE`, and `DOWNGRADE` require `source_accepted_line_uuid`. The check does not name `ADD`, `TERMINATE`, `SUSPEND`, `RESUME`, `RENEW`, or `CONVERT_METRIC` | `chk_amendment_line_source_required` lines 281–284 | `V1_7__contract_documents.sql` |
| Rate periods have `start_date`, `end_date`, `effective_end_date`, `billing_period_start`, and `billing_period_end` | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_6__subscription_rate_schedule.sql` lines 21–39 | `V1_6__subscription_rate_schedule.sql` |
| Rate periods gain `amendment_document_line_id` and `revision_no` | S1 migration lines 357–359. A search of `backend/**/*.sql` for `subscription_rate_periods` hit only `V1_6` and `V1_7`. No `action_date` column on that table in those two files | `V1_7__contract_documents.sql` (alter); columns created in `V1_6__subscription_rate_schedule.sql` |
| Quote continuation operations stored on the quote are `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION` | `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java` lines 10–18; `Quote.java` lines 90–96; check `chk_quotes_continuation_operation` | `backend/services/quote-service/src/main/resources/db/migration/V1_12__continuation_operation.sql` lines 23–28 |
| `EXTENSION` and `RENEWAL` both use `deal_type` `RENEWAL`. `deal_type` itself is `NEW`, `AMENDMENT`, `RENEWAL`, `EXPANSION` | `QuoteContinuationService.java` lines 328–330 (read); `DealType.java` (read); `chk_quotes_deal_type` | `V1__create_quote_schema.sql` lines 56–58; `V1_12__continuation_operation.sql` lines 1–3 and 30–31 (comment and column comment) |
| The continuation request has one `effectiveDate` and one `@Positive` quantity. Authoring copies that date onto `quotes.subscription_effective_date` when present, else the plan start. The request JSON is `quotes.continuation_request_json` | `QuoteContinuationRequest.java` lines 17–18; `QuoteContinuationService.java` lines 80–87; `Quote.java` lines 83–88 | `V1_10__quote_subscription_continuation.sql` lines 4–5. No `action_date` column in the quote migrations that were searched (`V1__create_quote_schema.sql`, `V1_10`, `V1_12`) |
| `DocumentKind` includes `RENEWAL`. The Java comment says that token is stored only and this story has no renewal behavior | `DocumentKind.java`; `chk_amendment_document_kind` lines 106–109 | `V1_7__contract_documents.sql` |
| `PolicySlot` is `AMENDMENT`, `RENEWAL`, `PRICE_LOCK`, `TRUE_UP`, `MAINTENANCE`, `CONVERSION`. The Java comment says renewal and price-lock slots are stored tokens only in S1 | `PolicySlot.java`; `chk_amendment_line_policy_slot` lines 273–275 | `V1_7__contract_documents.sql` |
| `InitiationTrigger` is `MANUAL`, `LEAD_TIME`, `TERM_END_AUTO`, `THRESHOLD`, `TRUE_UP_CYCLE`, `END_OF_LIFE`, `API` | `InitiationTrigger.java`; `chk_amendment_document_trigger` lines 103–105 | `V1_7__contract_documents.sql` |
| `charge_type` is `RECURRING`, `ONE_TIME`, `USAGE` | `ChargeType.java`; column line 234; `chk_amendment_line_charge` line 271 | `V1_7__contract_documents.sql` |
| `price_as_of` is `ACTION_DATE`, `EFFECTIVE_DATE`, or `LINE_START`, `varchar(40) NOT NULL`, with no SQL `DEFAULT` on that column | Column line 235; `chk_amendment_line_price_as_of` line 272; `PriceAsOf.java` javadoc says the field-schema default is the effective date. Field schema §2.5 marks that default `[I]` (`docs/renewals-amendments/amendment-entity-field-schema.md`, read) | `V1_7__contract_documents.sql` |
| OQ12: first-slice action codes Add, Update, and Remove. Swap, Upgrade, and Downgrade wait on RA-42 / DRG-576 | ADR-035 acceptance paragraph (read); register row OQ12 (read). `ActionCode.java` javadoc (read) says first-slice behavior is Add, Update, and Remove and other tokens are stored only. Requirements slice sentence (read): `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` | No new migration. The tokens themselves are the `V1_7__contract_documents.sql` action check |
| OQ6 revision rule, as recorded in #642 only | Register OQ6 row (read); `docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md` (read) | No migration in that record |
| Proration formula for an amendment or expansion is the day-stub. The input read in source is `request.getEffectiveDate()` | Accepted ADR Decision D-01 (read). `QuoteContinuationPolicy.java` line 78 (read). Tests were not executed. **Unverified by execution** | No migration. The ADR says acceptance does not itself change code |

`.../domain/documents/` above is `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/`.

A search of `backend/**/*.sql` for a `CREATE TABLE` line containing `policy` or `intent` returned no matches. `V1_7__contract_documents.sql` was read and contains no default-policy table. A `CREATE TABLE` whose name sits on the next line would miss that search. **Unverified** for that shape.

### Still open

These are open questions. They are not decisions of this note.

- Default policy per intent (fork E3). `policy_slot` is a line column in `V1_7__contract_documents.sql`. No code that selects a slot from `intent` was found in the files read. A whole-repo proof was not run. **Unverified** outside the files named above.
- Which `price_as_of` value a future writer sends (fork D1). The column is required and has no SQL default (citation in the table).
- Approval of a backdated effective date. The storage check is `chk_amendment_document_backdating` in `V1_7__contract_documents.sql`. Mid-term V6 and AC-S3-8 are requirements text (`mid-term-amendment-policy-spec.md` V6, read; `amendment-first-slice-requirements-and-acceptance.md` AC-S3-8, read). S3 is not started here.
- A line service period that differs from the header effective date. [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) was Backlog when read on 2026-10-17.
- ARR decomposition of one category. [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) was Backlog when read on 2026-10-17.
- Legal-review classes. [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) was Backlog when read on 2026-10-17.
- Quote continuation authoring writes one line from one source (`QuoteContinuationService.java` lines 90–94, read). Multi-line quote behavior is [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536), Backlog when read. `amendment_document_lines` can store many lines (`uq_amendment_line_triple` on `document_id, revision_no, line_no`, S1 migration line 256).
- [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) was Backlog in Linear when read on 2026-10-17. The document table is the S1 migration above. This frame does not change that ticket.

The missing quote-service type is the GAP section above, not an item in this list of decisions.

---

## DRG-548 — Unified LifecycleChange intent model

**Linear:** [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548/unified-lifecyclechange-intent-model-cancel-restructure-suspend-resume). Status when read on 2026-10-17: Backlog. Priority: High. Labels: `renewals-amendments`, `Open-CRM-CPQ`.

**Relations read the same day:** [DRG-472](https://linear.app/drgamtd-workspace/issue/DRG-472) (Backlog), [DRG-546](https://linear.app/drgamtd-workspace/issue/DRG-546) (Done), [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) (Backlog), [DRG-461](https://linear.app/drgamtd-workspace/issue/DRG-461) (Backlog), [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) (In Review), [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) (Backlog), [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) (Backlog), [DRG-561](https://linear.app/drgamtd-workspace/issue/DRG-561) (Done), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) (Backlog). Not re-fetched after that read.

### Problem

The research gap says quote continuation has four operations and no cancel, restructure, suspend, resume, transfer, true-up, or convert intent. The suggested next step is to extend that enum into an intent model with a default policy per intent, and to write an ADR.

The document spine on `main` already stores the nine intents. Citations are in the shared table (`AmendmentIntent.java`, `chk_amendment_document_intent` in `V1_7__contract_documents.sql`). The quote-service type that would author them is the GAP above. Extending `QuoteContinuationRequest.Operation` and accepting an ADR are outside this frame.

### Already on main

- The nine document intents, the unique lifecycle id, and ADR-035 D-03 / D-05 placement text. Files and migrations: shared table.
- `QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION` (`QuoteContinuationRequest.java` line 10; `V1_12__continuation_operation.sql` lines 25–28).
- DRG-546 is Done in Linear (read 2026-10-17). The distinguishing column is `quotes.continuation_operation` (`V1_12__continuation_operation.sql`; `Quote.java` lines 90–96). `deal_type` for both `EXTENSION` and `RENEWAL` remains `RENEWAL` (`QuoteContinuationService.java` lines 328–330; `V1__create_quote_schema.sql` lines 56–58).
- `EXTEND` and `EXPAND` are absent from `AmendmentIntent.java` and from `chk_amendment_document_intent` (read). `specs/lifecycle-intents.md` lists both (read). That proposal is not what `V1_7__contract_documents.sql` accepts.
- OQ15 (price-lock as its own intent versus Amend) is the register row, status NOT BLOCKING (read). The field schema's price-lock row uses `intent = Amend` and `action_code = Update` and marks that cell `[I]` (`docs/renewals-amendments/amendment-entity-field-schema.md` section 5, read in the prior pass). This frame does not decide OQ15.
- DRG-597's description, read 2026-10-17, records a call decision of two work queues over one shared model. That text is the Linear issue. No repo file was opened to confirm a queue implementation. **Unverified** as code. This frame does not design the queues.

### Options

**Fork E1 — Document intent vocabulary.**

| Option | Meaning |
|---|---|
| A | Keep the nine tokens in `AmendmentIntent` and `chk_amendment_document_intent`. |
| B | Add `EXTEND` and `EXPAND` to that check. Requires a migration. |
| C | Replace the nine with the four quote continuation operations and drop Cancel, Restructure, Suspend, Resume, Transfer, TrueUp, and Convert. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, add `EXTEND` and `EXPAND` to `chk_amendment_document_intent` by a migration; C, replace the nine tokens with `AMENDMENT`, `EXPANSION`, `RENEWAL`, and `EXTENSION`.

**Fork E2 — Who authors the intent.**

The missing type is the GAP. This fork does not decide that the type is absent. It chooses the author for when a type exists.

| Option | Meaning |
|---|---|
| A | Quote-service LifecycleChange authors the intent. CAM copies it onto `amendment_documents.intent`. This is ADR-035 D-03, already Accepted as placement. |
| B | CAM authors the intent and quote-service mirrors it. |
| C | `QuoteContinuationRequest.Operation` is the only intent. The document column is derived and not stored. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, CAM authors the intent and quote-service mirrors it; C, the continuation operation is the only intent and `amendment_documents.intent` is derived. This frame adds no second writer and does not build the type.

**Fork E3 — Default policy per intent.**

| Option | Meaning |
|---|---|
| A | No default-policy table. The only intent a future first-slice submit may send is `AMEND`, and the only slot it may pin is `AMENDMENT`. Other tokens stay storable. |
| B | Publish a static intent-to-slot map as documentation. Do not enforce it in schema or in the first slice. |
| C | The seller picks `policy_slot` with no default from intent. |

Recommendation: **A** for anything that would execute. The map below is option B written out so the owner can see it. It is not the default and it is not enforced.

| Intent | Slot the map would name | Future first-slice submit |
|---|---|---|
| `AMEND` | `AMENDMENT` | Only if S5 is separately authorized |
| `RENEW` | `RENEWAL` | Stored only |
| `TRUE_UP` | `TRUE_UP` | Stored only |
| `CONVERT` | `CONVERSION` | Stored only |
| `RESTRUCTURE` | none until DRG-573 | Stored only |
| `CANCEL`, `SUSPEND`, `RESUME`, `TRANSFER` | none | Stored only |

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, publish the map above and do not enforce it; C, the seller picks `policy_slot` with no default from intent.

**Fork E4 — Restructure versus convert.**

DRG-573's description, read 2026-10-17, says Restructure is agreement-level and `CONVERT_METRIC` only changes the metric. The conversion policy is that story. No ConversionPolicy type was opened. **Unverified** as code.

| Option | Meaning |
|---|---|
| A | Keep both intents. Neither selects a policy until DRG-573. `CONVERT_METRIC` stays a line action in `V1_7__contract_documents.sql`. |
| B | `RESTRUCTURE` selects `policy_slot = CONVERSION`. |
| C | Collapse `CONVERT` into `RESTRUCTURE` and migrate the check. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, `RESTRUCTURE` selects slot `CONVERSION`; C, drop intent `CONVERT` and migrate rows to `RESTRUCTURE`. This frame does not define ConversionPolicy.

**Fork E5 — Extension versus renew on the document.**

| Option | Meaning |
|---|---|
| A | A future document for a quote whose `continuation_operation` is `EXTENSION` stores intent `RENEW`. The quote column remains the distinction (`V1_12__continuation_operation.sql`). No new token and no backfill. |
| B | Add document intent `EXTEND`. |
| C | Backfill historical quotes that collapsed the two. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, add `EXTEND` to `chk_amendment_document_intent`; C, backfill historical quotes. DRG-546 is Done at the quote column. Historical rows stay as `V1_12` left them.

**Fork E6 — Expansion.**

| Option | Meaning |
|---|---|
| A | Quote `EXPANSION` stays `DealType.EXPANSION` and `continuation_operation = EXPANSION` (`V1__create_quote_schema.sql`, `V1_12__continuation_operation.sql`). A future document copies intent `AMEND`. |
| B | Add document intent `EXPAND`. |
| C | Store expansion as intent `RENEW`. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, add document intent `EXPAND`; C, store expansion as intent `RENEW`. Seat expansion, if a later story writes a document, is intent `AMEND` plus a line category (fork L4).

**Fork E7 — Which intents may be submitted.**

| Option | Meaning |
|---|---|
| A | Storage allows nine (`V1_7__contract_documents.sql`). A future first-slice submit allows `AMEND` only. |
| B | Enable `RENEW` behavior in this decision. |
| C | Enable all nine. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, enable `RENEW` behavior here; C, enable all nine intents here. Renewal, cancel, suspend, resume, transfer, true-up, restructure, and convert behavior stay on their own stories (DRG-472, DRG-573, and `docs/renewals-amendments/prereq/renewal-policy-spec.md`). This frame does not authorize them. There is no submit path to turn on. That absence is the GAP.

### Recommendation and default

**Default for DRG-548:** E1-A, E2-A, E3-A, E4-A, E5-A, E6-A, and E7-A. Proposed, unaccepted, owner decision required. Alternatives are the B and C rows in the owner-decision table.

### Owner decision needed

Confirm E1 through E7, or replace a default. Any choice other than A on E1, E4, E5, or E6 changes `chk_amendment_document_intent` in a new migration. This frame does not authorize that migration.

### Dependencies

ADR-035 (Accepted, S1/S2 only). OQ12 (confirmed action subset, register and ADR-035 acceptance, read). DRG-546 (Done, Linear 2026-10-17) and `V1_12__continuation_operation.sql`. DRG-573 for any conversion or restructure policy. DRG-576 / OQ12 for product-relationship actions. DRG-583 for a future quote-service aggregate. The GAP is not that dependency's authorization. OQ9 and the unaccepted DRG-609 design before S5 can price. OQ6 only as #642 recorded it. S3 still needs its own authorization.

### What it unblocks

An owner can point a later S5 story at a closed intent set and at the proposed rule that a first slice submits `AMEND` only. DRG-597 can filter queues on the nine stored tokens without a second table. It does not unblock S3, S5, S6, a renewal job, a conversion policy, or a change to `QuoteContinuationRequest.Operation`. It does not fill the GAP.

---

## DRG-552 — ChangeLine action codes, quantity semantics, and business categories

**Linear:** [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552/changeline-action-codes-quantity-semantics-and-business-categories). Status when read on 2026-10-17: Backlog. Priority: Medium.

**Relations read the same day:** [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) (Backlog), [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) (Backlog), [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) (In Review), [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (Backlog), [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) (Todo; the issue text lists DRG-552 as a dependency), [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) (Backlog), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) (Backlog). Not re-fetched after that read.

### Problem

The research gap says continuation has no per-line action, no delta/absolute/replace choice, and no business category. The suggested next step is to define a ChangeLine contract: `actionCode`, `quantitySemantics`, `startQty`, `deltaQty`, `endQty`, `businessCategory`.

That storage contract is the row in `amendment_document_lines`. Migration: `V1_7__contract_documents.sql` lines 214–298. Java: `AmendmentDocumentLine.java`. `change_line_ref uuid NOT NULL` is line 220 of the migration and lines 45–46 of the entity. The quote-service type those refs would point at is the GAP. The open part is which values a future submit may send, how quantity identity treats `REPLACE`, who sets the category, and which actions require a source line.

### Already on main

Action codes in `ActionCode.java` and `chk_amendment_line_action` (`V1_7__contract_documents.sql` lines 261–264):

`ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE`.

Quantity semantics in `QuantitySemantics.java` and line 270: `DELTA`, `ABSOLUTE`, `REPLACE`.

Business categories in `BusinessCategory.java` and lines 265–268:

`INITIAL_SALE`, `RENEWAL`, `UPSELL`, `DOWNSELL`, `CROSS_SELL`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CANCELLATION`, `TERMS_AND_CONDITIONS`, `TRANSFER`, `TRUE_UP`.

Also stored, with the same migration:

- `start_qty`, `delta_qty`, and `end_qty` are `numeric(19,4) NOT NULL` (lines 228–230). `chk_amendment_line_qty` (line 276) requires `start_qty + delta_qty = end_qty` and `end_qty >= 0` for every semantics value.
- Source-line check, lines 281–284, as in the shared table.
- OQ12 and the ADR-035 acceptance paragraph (read): first-slice behavior is Add, Update, and Remove. The requirements slice sentence (read) limits in-slice quantity semantics to Delta or Absolute. AC-S5-3 (read) says a Swap, Upgrade, or Downgrade submit is rejected with `ACTION_NOT_SUPPORTED_IN_SLICE`. That sentence is requirements text. No submit method was found. The missing method is the GAP, not a decision that the criterion is implemented.
- `specs/lifecycle-intents.md` (read) uses `Delete`, `Cancel`, `Surrender`, and `Convert`, and a category `Conversion` plus `Other(code)`. Those tokens are not in the `V1_7` checks (read). The proposal's rule that Replace does not carry a delta disagrees with `chk_amendment_line_qty`.
- One `business_category` column (line 222). No second category column and no ARR-bridge column in the `amendment_document_lines` statements that were read (lines 214–298). A later migration adding those columns was not found by the `subscription_rate_periods` / documents search. A dedicated search for `arr_bridge` was not run. **Unverified** for a column of that name outside this table definition.
- `charge_type` check line 271: `RECURRING`, `ONE_TIME`, `USAGE`. DRG-572's description says ChangeLine has no charge type. That is true of `QuoteContinuationRequest.java` (read: no `chargeType` field) and false of `amendment_document_lines`.

### Options

**Fork L1 — Closed action-code set.**

| Option | Meaning |
|---|---|
| A | Keep the twelve tokens. Research names map onto them: Delete → `REMOVE`, line-level cancel → `TERMINATE`, metric change → `CONVERT_METRIC`. Agreement conversion stays intent `CONVERT` (fork E4). |
| B | Migrate in `SURRENDER`, `CANCEL`, `CONVERT`, and `DELETE` from `specs/lifecycle-intents.md`. |
| C | Shrink the check to `ADD`, `UPDATE`, and `REMOVE` so the other tokens cannot be stored. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, add `SURRENDER`, `CANCEL`, `CONVERT`, and `DELETE` by a migration; C, shrink `chk_amendment_line_action` to `ADD`, `UPDATE`, and `REMOVE`.

**Fork L2 — What a future first-slice submit accepts.**

| Option | Meaning |
|---|---|
| A | Accept `ADD`, `UPDATE`, and `REMOVE` only, with `DELTA` or `ABSOLUTE`. Reject `SWAP`, `UPGRADE`, and `DOWNGRADE` as AC-S5-3 already says in the requirements file. Reject the other stored action tokens the same way until their own stories exist. `REPLACE` stays in the enum and is not a first-slice semantics. |
| B | Accept every stored action and every semantics value. |
| C | Also accept `NO_CHANGE` and `RENEW` in the first slice. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, accept every stored action and every semantics value; C, also accept `NO_CHANGE` and `RENEW` in the first slice. This restates OQ12 and the requirements slice. It does not edit the requirements file. It does not implement a rejection. There is no submit path. That is the GAP.

**Fork L3 — Quantity identity, including `REPLACE`.**

| Option | Meaning |
|---|---|
| A | Keep `chk_amendment_line_qty` in `V1_7__contract_documents.sql`. `ABSOLUTE` and `REPLACE` still store `delta_qty = end_qty - start_qty`. Callers send all three numbers. |
| B | Relax the check so `REPLACE` may omit delta. |
| C | Remove `REPLACE` from the enum. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, allow `REPLACE` without a delta (migration); C, remove `REPLACE` from `chk_amendment_line_qty_semantics` (migration). A swap remains action `SWAP`, which OQ12 leaves to DRG-576.

**Fork L4 — Who sets `business_category`.**

| Option | Meaning |
|---|---|
| A | Exactly one value from the stored set. A future first-slice writer sets it from the action and the sign of `delta_qty`. |
| B | The seller types the category. The system does not derive it. |
| C | Add compound categories, `CONVERSION`, or `OTHER`. |

Derivation used only if the owner adopts A:

| Line | Category |
|---|---|
| `ADD` | `UPSELL` |
| `UPDATE` and `delta_qty > 0` | `UPSELL` |
| `UPDATE` and `delta_qty < 0` | `DOWNSELL` |
| `UPDATE` and `delta_qty = 0` | `TERMS_AND_CONDITIONS` |
| `REMOVE` | `CANCELLATION` |

`CROSS_SELL`, `SWAP`, `UPGRADE`, `DOWNGRADE`, `TRANSFER`, `TRUE_UP`, `RENEWAL`, and `INITIAL_SALE` stay in `chk_amendment_line_category` and are not assigned by that writer. A product add that is cross-sell rather than upsell is DRG-575, not a second category on the line.

Recommendation: **A**.

**Default:** A, including the derivation table. Proposed, unaccepted, owner decision required. Alternatives: B, the seller types one category from the stored set and the writer does not derive it; C, add compound categories, `CONVERSION`, or `OTHER` by a migration.

**Fork L5 — Per-intent allow-list.**

| Option | Meaning |
|---|---|
| A | No new database constraint. Combined with E7-A and L2-A, the only pair a future first-slice submit allows is intent `AMEND` with `ADD`, `UPDATE`, or `REMOVE`. |
| B | Record a matrix for later quote-service enforcement. Do not enforce it now. |
| C | Add a SQL trigger from intent to action. |

The matrix under option B, not enforced and not the default:

| Intent | Actions the vocabulary can carry | Future first-slice submit |
|---|---|---|
| `AMEND` | `ADD`, `UPDATE`, `REMOVE`, plus stored-only `NO_CHANGE`, `UPGRADE`, `DOWNGRADE`, `SWAP` | `ADD`, `UPDATE`, `REMOVE` |
| `RENEW` | `RENEW`, `ADD`, `REMOVE`, `NO_CHANGE` | none |
| `CANCEL` | `TERMINATE`, `REMOVE` | none |
| `SUSPEND` | `SUSPEND` | none |
| `RESUME` | `RESUME` | none |
| `TRUE_UP` | `UPDATE`, `ADD` | none |
| `CONVERT` | `CONVERT_METRIC` | none |
| `RESTRUCTURE` | `CONVERT_METRIC`, `ADD`, `REMOVE`, `TERMINATE` | none |
| `TRANSFER` | no action token in `chk_amendment_line_action` | none |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, keep the matrix above as documentation for a later story and do not enforce it; C, add a SQL trigger from intent to action. There is no submit path to enforce. That is the GAP. A trigger would be a migration.

**Fork L6 — Source line required.**

The landed check is `chk_amendment_line_source_required` in `V1_7__contract_documents.sql` lines 281–284.

| Option | Meaning |
|---|---|
| A | Leave the check. A future first slice uses the source rule it already has (`UPDATE` and `REMOVE` require a source; `ADD` does not). |
| B | Extend the check to `TERMINATE`, `SUSPEND`, `RESUME`, `RENEW`, and `CONVERT_METRIC` now. |
| C | Drop the check. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, require a source line for `TERMINATE`, `SUSPEND`, `RESUME`, `RENEW`, and `CONVERT_METRIC` by a migration; C, drop `chk_amendment_line_source_required`.

**Fork L7 — Category for a conversion line.**

`policy_slot` includes `CONVERSION` (`chk_amendment_line_policy_slot`, `V1_7__contract_documents.sql` lines 273–275). `chk_amendment_line_category` has no `CONVERSION` token (lines 265–268).

| Option | Meaning |
|---|---|
| A | Write no conversion line in the first slice. DRG-573 chooses the category later. No new token now. |
| B | Store conversion lines as category `SWAP` until DRG-573. |
| C | Add category `CONVERSION`. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, use category `SWAP` for a conversion line until DRG-573; C, add `CONVERSION` to `chk_amendment_line_category` by a migration.

### Recommendation and default

**Default for DRG-552:** L1-A, L2-A, L3-A, L4-A, L5-A, L6-A, and L7-A. Proposed, unaccepted, owner decision required. Alternatives are the B and C rows in the owner-decision table.

### Owner decision needed

Confirm L1 through L7. L4's derivation table is the commercial choice inside default A. Any option other than A on L1, L3, L5, L6, or L7 is a migration.

### Dependencies

OQ12 and the ADR-035 acceptance paragraph (the executable subset, read). DRG-576 before Swap, Upgrade, or Downgrade can mean a catalog relationship. DRG-536 before the quote API carries more than one line. DRG-575 before one category is decomposed into ARR effects. DRG-594 before action codes gate legal review. DRG-583 before a submit path exists. The GAP stays a gap until that story is separately authorized. S3 and S6 are not dependencies this frame starts.

### What it unblocks

S5's AC-S5-1 and AC-S5-3 have a stored vocabulary and a confirmed executable subset to aim at, once S5 is separately authorized and OQ9 is verified. DRG-575 can assume one category column per line (`V1_7__contract_documents.sql` line 222). DRG-594 can classify those lines later. It does not unblock a multi-line continuation API, a catalog relationship model, an ARR bridge, or a legal-review gate.

---

## DRG-553 — Separate effective date from action date

**Linear:** [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553/separate-effective-date-from-action-date-trigger-dates). Status when read on 2026-10-17: Backlog. Priority: Medium.

**Relations read the same day:** [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) (In Review), [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) (Todo; the issue text lists DRG-553 as a dependency), [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) (Backlog), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) (Backlog). Not re-fetched after that read.

### Problem

The research gap says the product has effective-dated rate periods and no distinct action date versus effective dates. The suggested next step is to add `actionDate`, `contractEffective`, `serviceActivation`, and `customerAcceptance` to the change record and to rate periods, because proration, backdating, and ARR need them.

The document row already has those four dates (`V1_7__contract_documents.sql` lines 64–67). The line already has its own service period (lines 232–233 and 277). Rate periods already have their own bounds (`V1_6__subscription_rate_schedule.sql` lines 21–39) and a trace to the line (`V1_7__contract_documents.sql` lines 357–359). The quote continuation request still has a single `effectiveDate` (`QuoteContinuationRequest.java` line 18) and no action-date field (file read). The Accepted proration formula already consumes an effective date (`QuoteContinuationPolicy.java` line 78; ADR D-01). This frame chooses which stored date that input is. It leaves the formula alone.

Two different "OQ9" names exist. The register's OQ9 (read) is whether pricing-service supports amendment policy modes, and it blocks S5. The mid-term spec's OQ9 (`docs/renewals-amendments/mid-term-amendment-policy-spec.md`, read) is backdating limits and approval thresholds for V6. This section uses the mid-term meaning only when it says V6.

### Already on main

- `amendment_documents.action_date` and `contract_effective_date` are `date NOT NULL`. `service_activation_date` and `customer_acceptance_date` are nullable. File: `AmendmentDocument.java` lines 90–103. Migration: `V1_7__contract_documents.sql` lines 64–67.
- `chk_amendment_document_backdating` (same migration, lines 129–131): `contract_effective_date >= action_date` OR `backdating_reason_code IS NOT NULL`. The other two dates are outside that check (the check text was read).
- ADR-035 D-06 (read): the document signed or executed time is not the change effective date. `executed_at timestamptz` is a separate column, `V1_7__contract_documents.sql` line 87, and `AmendmentDocument.java` lines 160–161.
- Line `effective_from` / `effective_to` and `price_as_of` in `ACTION_DATE`, `EFFECTIVE_DATE`, `LINE_START`. `PriceAsOf.java`; check line 272; column line 235 has no `DEFAULT`. Field schema §2.5 marks EffectiveDate `[I]` (read).
- Rate-period bounds and the line trace, shared table. Those two migrations do not add `action_date`, `contract_effective_date`, `service_activation_date`, or `customer_acceptance_date` to `subscription_rate_periods`.
- `QuoteContinuationRequest.effectiveDate` is the date `QuoteContinuationPolicy.plan` assigns to `effective` for an amendment or expansion (line 78, read). The Accepted ADR describes that path (D-01, D-02, read). The request type has no action-date field and no service-activation or customer-acceptance field (file read). `quotes.subscription_effective_date` is the persisted copy (`V1_10__quote_subscription_continuation.sql` line 4; `QuoteContinuationService.java` lines 80–85). Tests of `plan` were not executed. **Unverified by execution.**
- `amendment_document_events` stores `occurred_at`, `recorded_at`, and optional `reason_code` (`V1_7__contract_documents.sql` lines 321–327; `AmendmentDocumentEvent.java`). The four commercial dates are on `amendment_documents`, not on the event row (table body read, lines 305–346). `AmendmentEventWriter.java` lines 18–22 (read) say it appends the hash chain and an audit outbox row. This frame adds no event type. The rest of `append` was not re-read. **Unverified** beyond those lines.
- FR-17 and AC-S3-8 are requirements text (`amendment-first-slice-requirements-and-acceptance.md`, read). The storage check is the part in `V1_7__contract_documents.sql`. No submit implementation of AC-S3-8 was opened. **Unverified** as running behavior. The missing submit path is the GAP.

### Options

**Fork D1 — Which date fills the Accepted proration formula.**

The formula stays the Accepted day-stub in `QuoteContinuationPolicy` (ADR D-01, read). This fork only names the input.

| Option | Meaning |
|---|---|
| A | `price_as_of = EFFECTIVE_DATE` means the formula's effective date is `contract_effective_date`. `ACTION_DATE` means `action_date`. `LINE_START` means the line's `effective_from`. |
| B | Always price from `action_date`. |
| C | Always price from `customer_acceptance_date`. |
| D | Change the proration formula. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, always price from `action_date`; C, always price from `customer_acceptance_date`; D, change the proration formula. A future first-slice writer, if S5 is separately authorized, sets `price_as_of = EFFECTIVE_DATE` on every line it prices. Option D reopens an Accepted ADR. This frame does not adopt D.

**Fork D2 — Service activation and customer acceptance.**

| Option | Meaning |
|---|---|
| A | Both columns stay nullable (`V1_7__contract_documents.sql` lines 66–67). They are not proration inputs. A future first-slice submit does not require them. |
| B | Both are required on every document. |
| C | Required only when `signature_policy` is `WET` or `E_SIGN`. Those policies are out of the confirmed slice (ADR-035 acceptance, OQ10, read; `SignaturePolicy.java` javadoc, DRG-457). |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, make `service_activation_date` and `customer_acceptance_date` `NOT NULL` by a migration; C, require them only for `WET` or `E_SIGN`.

**Fork D3 — Backdating.**

| Option | Meaning |
|---|---|
| A | Keep `chk_amendment_document_backdating`. Do not add approval thresholds. V6 approval and AC-S3-8 stay on S3. |
| B | Extend the check so `service_activation_date` and `customer_acceptance_date` also require a reason when they precede `action_date`. |
| C | Forbid backdating and drop the reason-code escape. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, extend the check to the other two dates; C, reject every `contract_effective_date` before `action_date` and drop `backdating_reason_code` as an escape. A stored row may carry a contract effective date before the action date only with a non-null `backdating_reason_code`, which is what the existing check already requires. This frame authorizes no approval workflow and no change to the check. OQ23 (cancellation proration and refunds) stays parked (register row, read).

**Fork D4 — Header dates versus the line service period.**

| Option | Meaning |
|---|---|
| A | The header keeps the four dates. The line keeps `effective_from` and `effective_to`. In a future first slice, `effective_from` equals `contract_effective_date`. A line whose service period starts earlier is DRG-572. |
| B | Copy all four trigger dates onto every line. |
| C | Drop the header dates and keep only line dates. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, add the four dates onto `amendment_document_lines` by a migration; C, drop `action_date` and the three trigger dates from `amendment_documents`. The columns already exist in `V1_7__contract_documents.sql`. `effective_to` follows `chk_amendment_line_dates` (null, or on/after `effective_from`).

**Fork D5 — Quote continuation request.**

| Option | Meaning |
|---|---|
| A | Leave `QuoteContinuationRequest` as it is until a separately authorized S5 change. One `effectiveDate`. No action-date field on that request. |
| B | Add the four dates to `QuoteContinuationRequest` now. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, add `actionDate`, `contractEffective`, `serviceActivation`, and `customerAcceptance` to `QuoteContinuationRequest` and to a quote migration now. If S5 is later authorized, the proposed mapping is: request `effectiveDate` → `contract_effective_date`, submit date → `action_date`, `price_as_of = EFFECTIVE_DATE`. That mapping is part of default A for a future change. It is not implemented here. The missing type that would carry it is the GAP.

**Fork D6 — Dates on `subscription_rate_periods`.**

| Option | Meaning |
|---|---|
| A | Add no date columns to the rate period. The period keeps `start_date`, `end_date`, `effective_end_date`, and the billing-period bounds from `V1_6__subscription_rate_schedule.sql`. Trace to the line via `amendment_document_line_id` and `revision_no` (`V1_7__contract_documents.sql` lines 357–359; ADR-035 D-06, read). The document holds the action and trigger dates. |
| B | Add `action_date` and the three trigger dates onto each rate period. |
| C | Add only a copy of the pricing effective date onto the period. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, add the four commercial dates onto `subscription_rate_periods` by a migration; C, add one pricing-effective-date column on that table. The ticket's request to put the four dates on rate periods is answered, as a proposal, by the line trace `V1_7` already added.

### Recommendation and default

**Default for DRG-553:** D1-A, D2-A, D3-A, D4-A, D5-A, and D6-A. Proposed, unaccepted, owner decision required. Alternatives are the other columns in the owner-decision table.

### Owner decision needed

Confirm D1 through D6. D1 is the commercial choice (which date prices). D3's approval thresholds stay on S3 and are not decided here. Any option other than A on D3, D4, or D6 is a migration. Option D on D1 reopens the Accepted proration ADR.

### Dependencies

Accepted proration ADR (input only; `adr-amendment-proration-formula.md` D-01). ADR-035 D-06 (trace, already in `V1_7__contract_documents.sql`). V6 and AC-S3-8 for approval behavior, which wait on S3's own authorization after #642. DRG-572 for a line period that is not the header effective date. DRG-583 for quote-service fields. The absence of those fields today is the GAP, not a decision to leave the request unchanged forever. Register OQ9 (pricing capability) before S5 prices anything. The unaccepted DRG-609 design is not a dependency this frame treats as settled.

### What it unblocks

A later S5 or S3 story can read the date columns in `V1_7__contract_documents.sql` and can name `contract_effective_date` as the proration input when `price_as_of` is `EFFECTIVE_DATE`, without reopening the formula. It does not unblock a quote-request change, a rate-period migration, a backdating approval flow, retroactive one-time periods, or ARR reporting.

---

## What this frame does not do

- It does not accept an ADR, edit an Accepted ADR, or assign a number. Its status is Proposed, unaccepted, owner decision required.
- It does not turn the quote-service GAP into a decision.
- It does not start S3 / DRG-581, S5 / DRG-583, S6, or S4 / DRG-582.
- It does not treat ADR-017 or ADR-024 through ADR-027 as accepted.
- It does not change the Accepted proration formula.
- It does not accept the DRG-609 design.
- It does not enable a flag, add a route, add a topic, or deploy.
- It does not edit `specs/lifecycle-intents.md`, the requirements file, the field schema, or the register.
