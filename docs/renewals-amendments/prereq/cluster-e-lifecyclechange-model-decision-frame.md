# Cluster E — LifecycleChange model decision frame

**Status:** Proposed. This frame is a decision record for the owner. It is not an accepted ADR, and accepting it authorizes no code, migration, or deploy.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Base:** `main` at `0d71251d` (includes merged [#642](https://github.com/drgaciw/open-crm-cpq/pull/642), merge `3ee3ca0f`).

**Stories:** [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548) (RA-14, unified intent), [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) (RA-18, action codes), [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) (RA-19, action date versus trigger dates). Linear comments on all three were empty when read. Relations are listed under each story.

**Cluster:** E, lifecycle-change model. One note. One section per ticket.

## How to read this frame

Every fork below has options, a recommendation, and a default. The default is the choice this Proposed record would use if the owner later adopts the frame. Adoption is a later act. This file does not perform it.

Boundaries that stay as they are:

- [ADR-035](../adr-035-amendment-document-placement.md) is **Accepted** for placement and for the first-slice schema and audit stories only (S1, S2; DRG-579, DRG-580). Its acceptance paragraph says later stories still need their own gates.
- [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) and [ADR-024](../../adr/ADR-024-order-orchestration-context.md) through [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) are **Proposed**. This frame does not treat them as accepted and does not choose a transport, an order saga, a command topic, or a compensation path.
- The amendment proration formula in [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) is **Accepted** (2026-10-16). This frame names which stored date fills that formula's existing effective-date input. It does not reopen the formula, the scale, the billing-period stub, or the Drools rule disposition.
- S6 is not authorized. This frame defines no event type, topic, registry entry, or outbox contract beyond the S2 writer that already exists.
- S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) was gated on [#642](https://github.com/drgaciw/open-crm-cpq/pull/642). That pull request merged to `main` on 2026-10-17. OQ6 is adopted only as recorded there and in [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) and the OQ6 row of [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md): the revision rule, and no code or story. The register says S3 still needs separate owner authorization. This frame does not start S3.
- The [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) design in [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) is a proposal. It is not accepted. OQ9 in the register still blocks S5 until pricing-service capability is verified.
- [specs/lifecycle-intents.md](specs/lifecycle-intents.md) is an earlier proposal. It predates the landed CAM vocabulary and disagrees with it in the places listed under each ticket. This frame does not edit that file and does not accept it. Where it disagrees with S1/S2 schema on `main`, the schema is what is stored.

CPQ Decision classified these three from titles. The descriptions were read. None is skipped. Each description also contains an implementation-shaped next step (extend an enum, define a contract, add date columns). Those steps are answered below as already stored, or withheld. This file performs none of them.

## Shared reconciliation: what S1/S2 already stored

S1 is the contract-documents schema in `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`. S2 is the hash-chained append in `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java` (class comment: DRG-580). The Java enums live in `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/`.

There is no `LifecycleChange` Java type and no `lifecycle-changes` route in quote-service. A workspace search of `*.java` finds the name only inside comments and the CAM column `lifecycle_change_id`. ADR-035 D-03 places the aggregate in quote-service. The column is a required UUID with `uq_amendment_document_lifecycle` and no foreign key (`V1_7__contract_documents.sql` lines 49–50 and 95).

### Already settled on main

| Fact | Where |
|---|---|
| Document intent is a closed set of nine tokens: `RENEW`, `AMEND`, `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT`. | `AmendmentIntent.java`; `chk_amendment_document_intent` in `V1_7__contract_documents.sql` lines 100–102. Same nine names in ADR-035 D-03 and D-05. |
| One document per lifecycle change id. All nine intents share that spine. | `uq_amendment_document_lifecycle`; ADR-035 D-05. |
| Document status is one column: `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID`. | `AmendmentDocumentStatus.java`; `chk_amendment_document_status` lines 110–113. `AmendmentDocument` javadoc: state transitions are DRG-581; the mapping stores the row. |
| Signature policy is `WET`, `E_SIGN`, `CLICK_THROUGH`, `NOTICE_ONLY`, `NOT_REQUIRED`. Slice policies confirmed with ADR-035 are `NOT_REQUIRED`, `NOTICE_ONLY`, `CLICK_THROUGH` (OQ10). E-sign execution is out of slice (DRG-457). | `SignaturePolicy.java`; `chk_amendment_document_signature` lines 114–116; ADR-035 acceptance, OQ10. |
| Append-only document events, with status from/to constrained to the same status set, actor type `USER` / `SYSTEM` / `SERVICE`, and a hash chain. UPDATE, DELETE, and TRUNCATE raise `42501`. | `amendment_document_events` lines 305–346; `reject_amendment_document_event_mutation` and triggers at lines 370–388; `AmendmentDocumentEvent.java`; `AmendmentEventWriter.java`. `event_type` is `varchar`, not a closed check. |
| Line action codes, quantity semantics, business categories, and `price_as_of` are closed checks. | Lines 261–275 of `V1_7__contract_documents.sql`. Enums: `ActionCode.java`, `QuantitySemantics.java`, `BusinessCategory.java`, `PriceAsOf.java`. |
| Quantity identity `start_qty + delta_qty = end_qty` and `end_qty >= 0` applies to every semantics value, including `REPLACE`. | `chk_amendment_line_qty` line 276. |
| Header dates: `action_date` and `contract_effective_date` are not null. `service_activation_date` and `customer_acceptance_date` are nullable. Backdating of the contract effective date requires `backdating_reason_code`. | `AmendmentDocument.java` lines 90–103; check lines 129–131. |
| Line service period: `effective_from` not null, `effective_to` null or on/after `effective_from`. | `AmendmentDocumentLine.java` lines 85–88; `chk_amendment_line_dates` line 277. |
| Rate periods already have `start_date`, `end_date`, `effective_end_date`, `billing_period_start`, and `billing_period_end`. V1_7 adds `amendment_document_line_id` and `revision_no`. | `V1_6__subscription_rate_schedule.sql` lines 21–39; `V1_7__contract_documents.sql` lines 357–359. |
| Quote continuation operations are `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`, with one `effectiveDate` and one positive quantity. `EXTENSION` and `RENEWAL` both store `DealType.RENEWAL`. `continuation_operation` keeps them distinguishable. [DRG-546](https://linear.app/drgamtd-workspace/issue/DRG-546) is Done. | `QuoteContinuationRequest.java` lines 10–18; `Quote.java` lines 90–96; `QuoteContinuationService.java` lines 328–330; `DealType.java`. |
| First-slice executable action codes are Add, Update, and Remove. Swap, Upgrade, and Downgrade wait on catalog relationships. | ADR-035 acceptance, OQ12 **CONFIRMED 2026-10-16**. `ActionCode.java` javadoc: first-slice behavior is Add, Update, and Remove; other tokens are stored only. Requirements slice definition in `amendment-first-slice-requirements-and-acceptance.md`. |
| OQ6 revision rule, as recorded in #642 only. | Register OQ6 row; `oq6-revision-model-owner-decision.md`. |
| Proration arithmetic for a continuation effective date. | Accepted `adr-amendment-proration-formula.md`. `QuoteContinuationPolicy.plan` is the path that runs. |

`DocumentKind` (`RENEWAL` is a stored token; the S1 comment says this story has no renewal behavior), `PolicySlot` (renewal and price-lock slots are stored tokens only in S1), and `InitiationTrigger` are the same kind of storage: the check exists, the behavior does not. Files: `DocumentKind.java`, `PolicySlot.java`, `InitiationTrigger.java`, and the matching checks in `V1_7__contract_documents.sql` lines 103–109 and 273–275.

### Still open

- The quote-service `LifecycleChange` aggregate, its API, and the continuation facade. Story: DRG-583 (S5), status Todo when read. ADR-035 does not authorize S5.
- Which stored tokens are executable, versus stored only. OQ12 settles Add, Update, and Remove. It does not turn the other intent, action, category, or policy-slot tokens into behavior.
- Default policy per intent. `policy_slot` is a line column. There is no default-policy table and no code that selects a slot from `intent`.
- Which of `price_as_of`'s three values the Accepted proration formula reads. The column is not null and has no SQL default. The field schema marks EffectiveDate as an inference (`amendment-entity-field-schema.md` §2.5, marker `[I]`).
- Approval of a backdated effective date. The storage check exists. Mid-term V6 and AC-S3-8 are S3 behavior. S3 is not started here.
- Per-line service periods that differ from the header effective date. [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) is still Backlog. `charge_type` is already a line column (`RECURRING`, `ONE_TIME`, `USAGE`).
- ARR decomposition of one category. [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) is still Backlog.
- Legal-review classes. [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) is still Backlog.
- Quote continuation remains one source line and one quantity (`QuoteContinuationRequest`). Multi-line quote behavior is [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536), still Backlog, even though `amendment_document_lines` can store many lines.
- [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) remains Backlog in Linear. The document table it asked for is the S1 schema above. This frame does not change that ticket.

---

## DRG-548 — Unified LifecycleChange intent model

**Linear:** [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548/unified-lifecyclechange-intent-model-cancel-restructure-suspend-resume). Status when read: Backlog. Priority: High. Labels: `renewals-amendments`, `Open-CRM-CPQ`.

**Relations read:** [DRG-472](https://linear.app/drgamtd-workspace/issue/DRG-472) (Backlog, renewal with uplift and cancellation), [DRG-546](https://linear.app/drgamtd-workspace/issue/DRG-546) (Done), [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) (Backlog), [DRG-461](https://linear.app/drgamtd-workspace/issue/DRG-461) (Backlog, amendments and co-termination), [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) (In Review, per-cluster specs umbrella), [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) (Backlog, two work queues on one model), [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) (Backlog, conversion pricing policy), [DRG-561](https://linear.app/drgamtd-workspace/issue/DRG-561) (Done, ADR-035), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) (Backlog, research intake).

### Problem

The research gap says quote continuation has four operations and no cancel, restructure, suspend, resume, transfer, true-up, or convert intent. The suggested next step is to extend that enum into an intent model with a default policy per intent, and to write an ADR.

The document spine on `main` already stores the nine intents ADR-035 names. The quote aggregate that would author them does not exist. Extending `QuoteContinuationRequest.Operation` and accepting an ADR are outside this frame.

### Already settled on main

- The closed document intent set and the one-document-per-lifecycle-change rule, in the shared table above.
- Placement: quote-service authors LifecycleChange; CAM stores the copy on `amendment_documents.intent` (ADR-035 D-03, D-05). The copy column exists. The authoring type does not.
- `QuoteContinuationRequest.Operation` stays `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`. DRG-546 is Done: `quotes.continuation_operation` distinguishes `EXTENSION` from `RENEWAL` while `DealType` for both remains `RENEWAL` (`Quote.java` lines 90–96; `QuoteContinuationService.dealType` lines 328–330).
- `EXPAND` and `EXTEND` are absent from `AmendmentIntent` and from `chk_amendment_document_intent`. `specs/lifecycle-intents.md` lists both. That proposal is not what the database accepts.
- `DocumentKind` and `PolicySlot` store renewal, termination, restructure, true-up, and conversion tokens. Their S1 comments say the non-amendment slots are stored only.
- OQ15 (price-lock as its own intent versus Amend) is not blocking and is left with RA-34. The field schema's price-lock row uses `intent = Amend` and `action_code = Update` as an inference. This frame does not decide OQ15.
- DRG-597 records a call decision: two work queues over one shared model, filtered by intent. That decision is on the ticket. It is outside the first slice. This frame does not design the queues.

### Options

**Fork E1 — Document intent vocabulary.**

| Option | Meaning |
|---|---|
| A | Keep the nine tokens in `AmendmentIntent` and `chk_amendment_document_intent`. |
| B | Add `EXTEND` and `EXPAND` to that check. Requires a migration. |
| C | Replace the nine with the four quote continuation operations and drop Cancel, Restructure, Suspend, Resume, Transfer, TrueUp, and Convert. |

Recommendation: **A**. Default: **A**.

**Fork E2 — Who authors the intent.**

| Option | Meaning |
|---|---|
| A | Quote-service LifecycleChange authors the intent. CAM copies it onto `amendment_documents.intent`. This is ADR-035 D-03. |
| B | CAM authors the intent and quote-service mirrors it. |
| C | `QuoteContinuationRequest.Operation` is the only intent. The document column is derived and not stored. |

Recommendation: **A**. The placement is already Accepted. The missing piece is the quote-service type, which S5 has not been authorized to build. Default: **A**. This frame adds no second writer.

**Fork E3 — Default policy per intent.**

| Option | Meaning |
|---|---|
| A | No default-policy table. The only intent a first-slice change may submit is `AMEND`, and the only slot it may pin is `AMENDMENT`. Other tokens stay storable. |
| B | Publish a static intent-to-slot map as documentation. Do not enforce it in schema or in the first slice. |
| C | The seller picks `policy_slot` with no default from intent. |

Recommendation: **A** for anything that would execute, with the map below recorded as **B** and not enforced. Default: **A**.

Proposed map, documentation only, not a check and not a behavior:

| Intent | Slot the map would name | First-slice submit |
|---|---|---|
| `AMEND` | `AMENDMENT` | Allowed when S5 is separately authorized |
| `RENEW` | `RENEWAL` | Stored only |
| `TRUE_UP` | `TRUE_UP` | Stored only |
| `CONVERT` | `CONVERSION` | Stored only |
| `RESTRUCTURE` | none until DRG-573 | Stored only |
| `CANCEL`, `SUSPEND`, `RESUME`, `TRANSFER` | none | Stored only |

**Fork E4 — Restructure versus convert.**

DRG-573 says Restructure is agreement-level and `CONVERT_METRIC` only changes the metric. The conversion policy is a separate story.

| Option | Meaning |
|---|---|
| A | Keep both intents. Neither selects a policy until DRG-573. `CONVERT_METRIC` stays a line action. |
| B | `RESTRUCTURE` selects `policy_slot = CONVERSION`. |
| C | Collapse `CONVERT` into `RESTRUCTURE` and migrate the check. |

Recommendation: **A**. Default: **A**. This frame does not define ConversionPolicy.

**Fork E5 — Extension versus renew on the document.**

| Option | Meaning |
|---|---|
| A | A future document for a quote whose `continuation_operation` is `EXTENSION` stores intent `RENEW`. The quote column remains the distinction. No new token and no backfill. |
| B | Add document intent `EXTEND`. |
| C | Backfill historical quotes that collapsed the two. |

Recommendation: **A**. Default: **A**. DRG-546 is already Done at the quote column. Historical rows stay as they are.

**Fork E6 — Expansion.**

| Option | Meaning |
|---|---|
| A | Quote `EXPANSION` stays `DealType.EXPANSION` and `continuation_operation = EXPANSION`. A future document copies intent `AMEND`. |
| B | Add document intent `EXPAND`. |
| C | Store expansion as intent `RENEW`. |

Recommendation: **A**. Default: **A**. Seat expansion is an amendment intent plus a line category (fork L4), not a tenth intent.

**Fork E7 — Which intents may be submitted.**

| Option | Meaning |
|---|---|
| A | Storage allows nine. A future first-slice submit allows `AMEND` only. |
| B | Enable `RENEW` behavior in this decision. |
| C | Enable all nine. |

Recommendation: **A**. Default: **A**. Renewal, cancel, suspend, resume, transfer, true-up, restructure, and convert behavior stay on their own stories (DRG-472, DRG-573, and the renewal policy spec). This frame does not authorize them.

### Recommendation and default

Adopt E1-A, E2-A, E3-A, E4-A, E5-A, E6-A, and E7-A. The nine stored intents are the vocabulary. Quote-service remains the author when S5 exists. The first slice, when authorized, submits `AMEND` only. `EXTENSION` and `EXPANSION` stay quote continuation operations. They do not become new document intents.

### Owner decision needed

Confirm E1 through E7, or replace a default. The decision that changes the database is any choice other than A on E1, E4, E5, or E6. Those other choices are migrations, and this frame does not authorize one.

### Dependencies

ADR-035 (Accepted, S1/S2 only). OQ12 (confirmed action subset). DRG-546 (Done). DRG-573 for any conversion or restructure policy. DRG-576 / OQ12 for product-relationship actions. DRG-583 for the quote-service aggregate. OQ9 and the unaccepted DRG-609 design before S5 can price. OQ6 only as #642 recorded it; S3 still needs its own authorization.

### What it unblocks

An owner can point a later S5 story at a closed intent set and at the rule that the first slice submits `AMEND` only. DRG-597 can filter queues on the nine stored tokens without a second table. It does not unblock S3, S5, S6, a renewal job, a conversion policy, or a change to `QuoteContinuationRequest.Operation`.

---

## DRG-552 — ChangeLine action codes, quantity semantics, and business categories

**Linear:** [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552/changeline-action-codes-quantity-semantics-and-business-categories). Status when read: Backlog. Priority: Medium.

**Relations read:** [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) (Backlog), [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) (Backlog), [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) (In Review), [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) (Backlog), [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) (Todo; S5 lists DRG-552 as a dependency), [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) (Backlog), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) (Backlog).

### Problem

The research gap says continuation has no per-line action, no delta/absolute/replace choice, and no business category. The suggested next step is to define a ChangeLine contract: `actionCode`, `quantitySemantics`, `startQty`, `deltaQty`, `endQty`, `businessCategory`.

That contract is already the row in `amendment_document_lines`, with a UUID `change_line_ref` back to a lifecycle line that quote-service does not yet store. The open part is which values may be submitted, how quantity identity treats `REPLACE`, who sets the category, and which actions require a source line.

### Already settled on main

Action codes in `ActionCode.java` and `chk_amendment_line_action` (lines 261–264):

`ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE`.

Quantity semantics in `QuantitySemantics.java` and line 270: `DELTA`, `ABSOLUTE`, `REPLACE`.

Business categories in `BusinessCategory.java` and lines 265–268:

`INITIAL_SALE`, `RENEWAL`, `UPSELL`, `DOWNSELL`, `CROSS_SELL`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CANCELLATION`, `TERMS_AND_CONDITIONS`, `TRANSFER`, `TRUE_UP`.

Also settled:

- `start_qty`, `delta_qty`, and `end_qty` are `numeric(19,4)` not null, and `chk_amendment_line_qty` requires `start_qty + delta_qty = end_qty` and `end_qty >= 0` for every semantics value (line 276).
- `UPDATE`, `REMOVE`, `SWAP`, `UPGRADE`, and `DOWNGRADE` require `source_accepted_line_uuid` (`chk_amendment_line_source_required`, lines 281–284). `ADD` does not.
- OQ12, confirmed by the owner with ADR-035: first-slice behavior is Add, Update, and Remove. The requirements slice text also limits quantity semantics in slice to Delta or Absolute. AC-S5-3 rejects Swap, Upgrade, and Downgrade on submit with `ACTION_NOT_SUPPORTED_IN_SLICE`. That criterion is a future S5 test. It is not implemented, because there is no LifecycleChange submit path.
- `specs/lifecycle-intents.md` uses `Delete`, `Cancel`, `Surrender`, and `Convert`, and a category `Conversion` plus `Other(code)`. Those tokens are not in the checks. A row using them fails the check. The proposal's rule that Replace does not carry a delta disagrees with `chk_amendment_line_qty`.
- One category column per line. There is no second category and no ARR-bridge column. That gap is DRG-575.
- `charge_type` is already on the line. DRG-572's statement that ChangeLine has no charge type is true of `QuoteContinuationRequest` and false of `amendment_document_lines`.

### Options

**Fork L1 — Closed action-code set.**

| Option | Meaning |
|---|---|
| A | Keep the twelve tokens. Research names map onto them: Delete → `REMOVE`, line-level cancel → `TERMINATE`, metric change → `CONVERT_METRIC`. Agreement conversion stays intent `CONVERT` (fork E4). |
| B | Migrate in `SURRENDER`, `CANCEL`, `CONVERT`, and `DELETE` from `specs/lifecycle-intents.md`. |
| C | Shrink the check to `ADD`, `UPDATE`, and `REMOVE` so the other tokens cannot be stored. |

Recommendation: **A**. Default: **A**. OQ12 already limits what the first slice executes. Shrinking the check would reject tokens S1 was built to store. Growing it reopens a landed check.

**Fork L2 — What a future first-slice submit accepts.**

| Option | Meaning |
|---|---|
| A | Accept `ADD`, `UPDATE`, and `REMOVE` only, with `DELTA` or `ABSOLUTE`. Reject `SWAP`, `UPGRADE`, and `DOWNGRADE` as AC-S5-3 already says. Reject the other stored action tokens the same way until their own stories exist. `REPLACE` stays in the enum and is not a first-slice semantics. |
| B | Accept every stored action and every semantics value. |
| C | Also accept `NO_CHANGE` and `RENEW` in the first slice. |

Recommendation: **A**. Default: **A**. This restates OQ12 and the requirements slice. It does not edit the requirements file. It does not implement the rejection.

**Fork L3 — Quantity identity, including `REPLACE`.**

| Option | Meaning |
|---|---|
| A | Keep `chk_amendment_line_qty`. `ABSOLUTE` and `REPLACE` still store `delta_qty = end_qty - start_qty`. Callers send all three numbers. |
| B | Relax the check so `REPLACE` may omit delta. |
| C | Remove `REPLACE` from the enum. |

Recommendation: **A**. Default: **A**. The identity is landed. Relaxing it is a migration this frame does not authorize. A swap remains an action (`SWAP`) plus semantics, and `SWAP` is outside the first slice (OQ12, DRG-576).

**Fork L4 — Who sets `business_category`.**

| Option | Meaning |
|---|---|
| A | Exactly one value from the stored set. A future first-slice writer sets it from the action and the sign of `delta_qty`, and does not accept a second category. |
| B | The seller types the category. The system does not derive it. |
| C | Add compound categories, `CONVERSION`, or `OTHER`. |

Recommendation: **A**. Default: **A**, using this derivation for a future first-slice writer only:

| Line | Category |
|---|---|
| `ADD` | `UPSELL` |
| `UPDATE` and `delta_qty > 0` | `UPSELL` |
| `UPDATE` and `delta_qty < 0` | `DOWNSELL` |
| `UPDATE` and `delta_qty = 0` | `TERMS_AND_CONDITIONS` |
| `REMOVE` | `CANCELLATION` |

`CROSS_SELL`, `SWAP`, `UPGRADE`, `DOWNGRADE`, `TRANSFER`, `TRUE_UP`, `RENEWAL`, and `INITIAL_SALE` stay stored and are not assigned by that writer. A product add that is cross-sell rather than upsell is a later pricing classification (DRG-575), not a second category on the line. The owner replaces this table by choosing B or by editing the table. Until then the default is the table.

**Fork L5 — Per-intent allow-list.**

| Option | Meaning |
|---|---|
| A | No new database constraint. Combined with E7-A and L2-A, the only submittable pair is intent `AMEND` with `ADD`, `UPDATE`, or `REMOVE`. |
| B | Record a matrix for later quote-service enforcement. Do not enforce it now. |
| C | Add a SQL trigger from intent to action. |

Recommendation: **A** as the rule that has force, and record the matrix below as **B** so a later story has a starting list. Default: **A**. There is no submit path to enforce, and a trigger would be a migration.

Matrix, not enforced:

| Intent | Actions the vocabulary can carry | First-slice submit |
|---|---|---|
| `AMEND` | `ADD`, `UPDATE`, `REMOVE`, plus stored-only `NO_CHANGE`, `UPGRADE`, `DOWNGRADE`, `SWAP` | `ADD`, `UPDATE`, `REMOVE` |
| `RENEW` | `RENEW`, `ADD`, `REMOVE`, `NO_CHANGE` | none |
| `CANCEL` | `TERMINATE`, `REMOVE` | none |
| `SUSPEND` | `SUSPEND` | none |
| `RESUME` | `RESUME` | none |
| `TRUE_UP` | `UPDATE`, `ADD` | none |
| `CONVERT` | `CONVERT_METRIC` | none |
| `RESTRUCTURE` | `CONVERT_METRIC`, `ADD`, `REMOVE`, `TERMINATE` | none |
| `TRANSFER` | no action token today | none |

**Fork L6 — Source line required.**

The landed check requires a source line for `UPDATE`, `REMOVE`, `SWAP`, `UPGRADE`, and `DOWNGRADE`. It does not require one for `TERMINATE`, `SUSPEND`, `RESUME`, `RENEW`, or `CONVERT_METRIC`.

| Option | Meaning |
|---|---|
| A | Leave the check. The first slice only needs the source rule it already has (`UPDATE` and `REMOVE` require a source; `ADD` does not). |
| B | Extend the check to `TERMINATE`, `SUSPEND`, `RESUME`, `RENEW`, and `CONVERT_METRIC` now. |
| C | Drop the check. |

Recommendation: **A**. Default: **A**. A later story that enables `TERMINATE` brings its own source-line rule. This frame does not migrate the check.

**Fork L7 — Category for a conversion line.**

`policy_slot` has `CONVERSION`. `business_category` has no `CONVERSION` token.

| Option | Meaning |
|---|---|
| A | Write no conversion line in the first slice. DRG-573 chooses the category later. No new token now. |
| B | Store conversion lines as category `SWAP` until DRG-573. |
| C | Add category `CONVERSION`. |

Recommendation: **A**. Default: **A**.

### Recommendation and default

Adopt L1-A, L2-A, L3-A, L4-A, L5-A, L6-A, and L7-A. The stored enums and the quantity check are the contract. The first slice, when a later story builds submit, uses Add, Update, and Remove with Delta or Absolute, one derived category, and the source-line check that already exists.

### Owner decision needed

Confirm L1 through L7. L4's derivation table is the commercial choice. Any option other than A on L1, L3, L5, L6, or L7 is a migration.

### Dependencies

OQ12 and ADR-035 acceptance (the executable subset). DRG-576 before Swap, Upgrade, or Downgrade can mean a catalog relationship. DRG-536 before the quote API carries more than one line. DRG-575 before one category is decomposed into ARR effects. DRG-594 before action codes gate legal review. DRG-583 before a submit path exists. S3 and S6 are not dependencies this frame starts.

### What it unblocks

S5's AC-S5-1 and AC-S5-3 have a stored vocabulary and a confirmed executable subset to aim at, once S5 is separately authorized and OQ9 is verified. DRG-575 can assume one category per line. DRG-594 can classify those lines later. It does not unblock a multi-line continuation API, a catalog relationship model, an ARR bridge, or a legal-review gate.

---

## DRG-553 — Separate effective date from action date

**Linear:** [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553/separate-effective-date-from-action-date-trigger-dates). Status when read: Backlog. Priority: Medium.

**Relations read:** [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) (In Review), [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) (Todo; S5 lists DRG-553 as a dependency), [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572) (Backlog), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) (Backlog).

### Problem

The research gap says the product has effective-dated rate periods and no distinct action date versus effective dates. The suggested next step is to add `actionDate`, `contractEffective`, `serviceActivation`, and `customerAcceptance` to the change record and to rate periods, because proration, backdating, and ARR need them.

The document row already has those four dates. The line already has its own service period. Rate periods already have their own bounds and a trace to the line. The quote continuation request still has a single `effectiveDate` and no action date. The Accepted proration formula already consumes an effective date. This frame chooses which stored date that input is, and it leaves the formula alone.

Two different "OQ9" names exist. The register's OQ9 is whether pricing-service supports amendment policy modes, and it blocks S5. The mid-term spec's OQ9 (`mid-term-amendment-policy-spec.md`) is backdating limits and approval thresholds for V6. This section uses the mid-term meaning only when it says V6.

### Already settled on main

- `amendment_documents.action_date` and `contract_effective_date` are not null. `service_activation_date` and `customer_acceptance_date` are nullable (`AmendmentDocument.java` lines 90–103; `V1_7__contract_documents.sql` lines 64–67).
- `chk_amendment_document_backdating` (lines 129–131): `contract_effective_date >= action_date` OR `backdating_reason_code` is not null. The other two dates are outside that check.
- ADR-035 D-06: the document signed or executed time is not the change effective date. `executed_at` is a separate timestamp on the document.
- Line `effective_from` / `effective_to` and `price_as_of` in `ACTION_DATE`, `EFFECTIVE_DATE`, `LINE_START` (`PriceAsOf.java`; check line 272). `PriceAsOf`'s javadoc says the field-schema default is the effective date. The column has no SQL default. The field schema marks that default as an inference.
- Rate-period bounds and the line trace, in the shared table. Rate periods do not have `action_date`, `contract_effective_date`, `service_activation_date`, or `customer_acceptance_date` columns.
- `QuoteContinuationRequest.effectiveDate` is the date `QuoteContinuationPolicy.plan` prorates from. The Accepted proration ADR describes that path. The request has no action date and no service-activation or customer-acceptance field.
- `amendment_document_events` stores `occurred_at`, `recorded_at`, and an optional `reason_code`. It does not store the four commercial dates. The dates live on the document. S2's `AmendmentEventWriter` appends the hash chain and an audit outbox row. This frame adds no event type.
- FR-17 and AC-S3-8 (backdating without a reason fails on submit) are requirements text for S3 and S5. The storage check is the part that exists. The submit behavior does not.

### Options

**Fork D1 — Which date fills the Accepted proration formula.**

The formula stays the Accepted day-stub in `QuoteContinuationPolicy`. This fork only names the input.

| Option | Meaning |
|---|---|
| A | `price_as_of = EFFECTIVE_DATE` means the formula's effective date is `contract_effective_date`. `ACTION_DATE` means `action_date`. `LINE_START` means the line's `effective_from`. |
| B | Always price from `action_date`. |
| C | Always price from `customer_acceptance_date`. |
| D | Change the proration formula. |

Recommendation: **A**. Default: **A**. A future first-slice writer sets `price_as_of = EFFECTIVE_DATE` on every line it prices. Option D is rejected. The formula is Accepted and stays closed.

**Fork D2 — Service activation and customer acceptance.**

| Option | Meaning |
|---|---|
| A | Both columns stay nullable. They are not proration inputs. A first-slice submit does not require them. |
| B | Both are required on every document. |
| C | Required only when `signature_policy` is `WET` or `E_SIGN`. Those policies are out of slice (OQ10, DRG-457). |

Recommendation: **A**. Default: **A**.

**Fork D3 — Backdating.**

| Option | Meaning |
|---|---|
| A | Keep `chk_amendment_document_backdating` as the storage rule. Do not add approval thresholds. V6 approval and AC-S3-8 stay on S3. |
| B | Extend the check so `service_activation_date` and `customer_acceptance_date` also require a reason when they precede `action_date`. |
| C | Forbid backdating and drop the reason-code escape. |

Recommendation: **A**. Default: **A**. A stored row may carry a contract effective date before the action date only with a non-null `backdating_reason_code`. This frame authorizes no approval workflow and no change to the check. OQ23 (cancellation proration and refunds) stays parked.

**Fork D4 — Header dates versus the line service period.**

| Option | Meaning |
|---|---|
| A | The header keeps the four dates. The line keeps `effective_from` and `effective_to`. In the first slice, `effective_from` equals `contract_effective_date`. A line whose service period starts earlier is DRG-572. |
| B | Copy all four trigger dates onto every line. |
| C | Drop the header dates and keep only line dates. |

Recommendation: **A**. Default: **A**. The columns already exist in both places. The first slice does not invent a second set. `effective_to` follows the existing line check (null, or on/after `effective_from`).

**Fork D5 — Quote continuation request.**

| Option | Meaning |
|---|---|
| A | Leave `QuoteContinuationRequest` as it is until a separately authorized S5 change. One `effectiveDate`. No `actionDate` on that request. |
| B | Add the four dates to `QuoteContinuationRequest` now. |

Recommendation: **A**. Default: **A**. When S5 is authorized, the facade maps the request's `effectiveDate` to `contract_effective_date` and sets `action_date` from the submit date, with `price_as_of = EFFECTIVE_DATE`. That mapping is the default for that future change. It is not implemented here.

**Fork D6 — Dates on `subscription_rate_periods`.**

| Option | Meaning |
|---|---|
| A | Add no date columns to the rate period. The period keeps `start_date`, `end_date`, `effective_end_date`, and the billing-period bounds. Trace to the line via `amendment_document_line_id` and `revision_no` (ADR-035 D-06). The document holds the action and trigger dates. |
| B | Add `action_date` and the three trigger dates onto each rate period. |
| C | Add only a copy of the pricing effective date onto the period. |

Recommendation: **A**. Default: **A**. The ticket's request to add the four dates onto rate periods is answered by the trace that V1_7 already added. A second copy of the dates would be a migration.

### Recommendation and default

Adopt D1-A, D2-A, D3-A, D4-A, D5-A, and D6-A. Action date and contract effective date are already required and distinct. The Accepted proration formula reads `contract_effective_date` when `price_as_of` is `EFFECTIVE_DATE`, which is the first-slice value. Service activation and customer acceptance stay optional and unused by pricing. Rate periods keep their own bounds and the line trace.

### Owner decision needed

Confirm D1 through D6. D1 is the commercial choice (which date prices). D3's approval thresholds stay on S3 and are not decided here. Any option other than A on D3, D4, or D6 is a migration.

### Dependencies

Accepted proration ADR (input only). ADR-035 D-06 (trace, already in V1_7). V6 and AC-S3-8 for approval behavior, which wait on S3's own authorization after #642. DRG-572 for a line period that is not the header effective date. DRG-583 for the quote-service fields. Register OQ9 (pricing capability) before S5 prices anything. The unaccepted DRG-609 design is not a dependency this frame treats as settled.

### What it unblocks

A later S5 or S3 story can read the date columns that already exist and can name `contract_effective_date` as the proration input without reopening the formula. It does not unblock a quote-request change, a rate-period migration, a backdating approval flow, retroactive one-time periods, or ARR reporting.

---

## What this frame does not do

- It does not accept an ADR, edit an Accepted ADR, or assign a number.
- It does not start S3 / DRG-581, S5 / DRG-583, S6, or S4 / DRG-582.
- It does not treat ADR-017 or ADR-024 through ADR-027 as accepted.
- It does not change the Accepted proration formula.
- It does not accept the DRG-609 design.
- It does not enable a flag, add a route, add a topic, or deploy.
- It does not edit `specs/lifecycle-intents.md`, the requirements file, the field schema, or the register.
