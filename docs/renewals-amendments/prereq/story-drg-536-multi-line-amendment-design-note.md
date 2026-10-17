# DRG-536 — One amendment, several lines and modes

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Reading base:** `main` at `285ffef8` (merge of [#662](https://github.com/drgaciw/open-crm-cpq/pull/662)).

**Story:** [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) (RA-02). Title when read on Linear on 2026-10-17: "No multi-line amendment support". Priority: High. Status when read: Backlog. Labels: `renewals-amendments`, `Open-CRM-CPQ`. Comments were not read. **Unverified.**

**Relations read the same day:** [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572), [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565), [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563), [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552). Not re-fetched after that read.

This file is a design note. It is not part of the 2026-10-17 documentation-acceptance batch. That batch accepted [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) and [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) as documentation and left each fork's Proposed label as a decision requirement ([documentation-acceptance-2026-10-17.md](documentation-acceptance-2026-10-17.md), read). This note follows those Proposed defaults. It does not replace them.

**Accepting this note authorizes no code, schema, migration, flag, topic, gateway route, deploy, or story.** No flag is enabled. Nothing is deployed. No backfill is run and no apply is run. The ticket stays In Review until the owner merges. S3 and S6 code stay unlaunched. S6 is not authorized.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or migration named in the same sentence was opened on this `main`. |
| `unverified` | Not opened, not executed, or not queried. A search that found no row is a search, not a live database result. |

No database was queried. Whether Flyway has applied any migration in an environment is `unverified`. GitNexus `list_repos` returned no indexed repository. Impact is **UNKNOWN**. An empty index is not evidence that a symbol is unused.

## Standing constraints

| Constraint | Record on this `main` |
|---|---|
| Cluster A modes and policy | [#650](https://github.com/drgaciw/open-crm-cpq/pull/650), [cluster-a-amendment-modes-and-policy-decision-frame.md](cluster-a-amendment-modes-and-policy-decision-frame.md). Accepted as documentation. Fork defaults below stay Proposed, unaccepted, owner decision required. This note does not retitle them. |
| Cluster E LifecycleChange | [#651](https://github.com/drgaciw/open-crm-cpq/pull/651), [cluster-e-lifecyclechange-model-decision-frame.md](cluster-e-lifecyclechange-model-decision-frame.md). Accepted as documentation. The quote-service type is still the GAP recorded there. This note does not fill that GAP. |
| ADR-035 | Accepted for placement and for S1 and S2 only. `read` status line of [adr-035-amendment-document-placement.md](../adr-035-amendment-document-placement.md). Later stories keep their own gates. |
| ADR-017 and ADR-024 through ADR-027 | **Proposed.** `read` catalog rows in [docs/adr/README.md](../../adr/README.md). They are not prerequisites. This note chooses no transport, saga, command topic, or compensation path. |
| Proration | **Accepted.** `read` [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) status line "Accepted 2026-10-16" and number line "none". The formula is not restated and is not reopened. |
| OQ5 option A | Unchanged. `read` the OQ5 row of [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) and [partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md). `specs/partial-apply.md` stays not accepted. |
| OQ7 thin contract | Unchanged. `read` [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) and the header comment of [V1_8__contract_header_backfill.sql](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql). The register cell for OQ7 still says `DEFAULT PROPOSED for slice` (`read`). This note does not edit that cell and does not regroup. |
| OQ6 | Quoted below from [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md). Not paraphrased. |
| DRG-609 | [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) is a design proposal (`read` its status role in Cluster A and Cluster E). It is not accepted. Entitlements and conversion price in this note are pending that design. |
| Uplift precedence | Accepted [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) D-04 (`read`): contract clause, then line, then agreement, then account, then the rules default. This note does not reopen D-04. |
| Migration numbers | CAM files present (`read` directory): `V1_7__contract_documents.sql`, `V1_7_1__durable_renewal_eligibility_publications.sql`, `V1_7_2__freeze_amendment_line_membership.sql`, `V1_8__contract_header_backfill.sql`. `V1_9` is reserved for S4 `document_apply_ledger` as the gate doc designs it (`read` [story-s3-s4-authorization-gate.md](story-s3-s4-authorization-gate.md) S4 row and the V1_8 header comment). No `V1_9` file and no `V1_10` file are in that directory. Any future migration is `V1_10` or higher, the next free version at build time, with a rollback statement. Never `V1_7_3`. This note writes no migration. |
| S3 and S6 | S3 / DRG-581 is not started. S6 is not authorized (`read` OQ5 row). This note defines no event type, topic, registry entry, or outbox contract. |

[#648](https://github.com/drgaciw/open-crm-cpq/pull/648) through [#660](https://github.com/drgaciw/open-crm-cpq/pull/660) are the frames named in the acceptance record. Where this note touches true-up, renewal automation, on-prem conversion, fulfilment, visibility, S3 reconciliation, renewal pricing, product eligibility, one-time charges, OQ6 gaps, or the ARR bridge, it cites that frame and leaves its Proposed default in place.

## OQ6, quoted exactly

Source file on `main`: [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) (#642). The paragraphs below are copied from that file.

> **Status:** OQ6 was decided under delegation by CPQ Decision and relayed by Projects Manager; it is not the owner's personal choice. OQ18 remains **Proposed until the owner confirms it**. OQ6 is recorded for the first slice, but this record authorizes no code, schema, migration, flag, topic, gateway route, deploy, or story. S3 / DRG-581 requires separate owner authorization. OQ18 confirmation is required before building the proposed digest library, not authorization to start S3.

> 1. **Before execution (`DRAFT`, `IN_REVIEW`, `APPROVED`).** A new revision of the LifecycleChange opens a new document revision on the same document, with the same `provisionalNumber` (OQ4). The previous revision closes with `status_at_close = SUPERSEDED` and keeps its content digest. The document returns to `DRAFT`. Approvals and signatures are void; pricing pins from the previous revision do not carry over, and the new revision must be priced and submitted again. The persisted representation of an unpriced revision is not decided here.

> 2. **Executed, in any apply state (`EXECUTED`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`).** The document is immutable. A new revision is refused with 409 `IMMUTABLE_EXECUTED` and an audit event, which is AC-S3-5 applied to revisions. "Executed but not yet applied" gets no special path. The remedy is a new LifecycleChange that produces a new, correcting amendment document, the same remedy OQ5 option A adopted for a blocked unit.

> 3. **Command and integration boundary.** CAM must own the revision and execution state transitions. The exact CAM command/API, including route and request/response contract, is not decided here; the OpenAPI draft's LifecycleChange revision operation is tagged S5 and is not an accepted CAM API. The S3 CAM-local test boundary versus the S5 LifecycleChange trigger, delivery, acknowledgement and reconciliation boundary requires a separate accepted contract. No cross-service race or delivery protocol is adopted here.

> The delegated ruling does not choose a supersession event type, payload/schema, topic, or outbox contract. Event requirements in draft acceptance criteria remain proposals and do not authorize an event contract or S6 work.

> - It does not authorize S3, S4, S5, S6, S7 or S8. S3 / DRG-581 needs separate owner authorization.
> - It does not change OQ5 (adopted for the first slice), OQ7 (still `DEFAULT PROPOSED` in the register) or any ADR status.

## Problem

Linear DRG-536, read 2026-10-17, says a continuation supports one recurring source line, and a real amendment changes several lines at once (add, remove, change quantity). The suggested next step on the issue is a multi-line continuation request and per-line source mapping. That step is answered here as a design. The Java type is not changed.

Cluster E already records the split (`read`): quote continuation authoring writes one line, and `amendment_document_lines` can store many lines. Cluster A already records that a document has one amendment mode, that lines may carry their own price mode, and that a mixed edit is judged on the whole document. This note says how those two frames sit on one amendment: several lines, one document mode, per-line action and price modes, a line order that is not the apply order, a failure that is either the whole price or an OQ5 unit, and totals and audit that cover every line of the revision.

## Already on main

| Fact | File path | Migration |
|---|---|---|
| One continuation request: one `operation`, one `targetSubscriptionNumber`, one `sourceSubscriptionLineUuid`, one `quantity`, one `effectiveDate`. No line list | `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java` lines 10–18 (`read`) | No CAM migration. Quote column `continuation_request_json` is `V1_10__quote_subscription_continuation.sql` as Cluster E cites it. That quote migration was not re-opened this pass. **Unverified** beyond Cluster E's citation and the Java file read here |
| Authoring saves one `QuoteLineItem` from that one source | `QuoteContinuationService.java` lines 90–94 (`read`) | Same quote history. Not re-opened |
| A continuation that has any line besides that one recurring source is refused: "additional lines require a separate quote" | `QuoteContinuationService.java` lines 286–288 (`read`) | Same |
| Source selection requires exactly one accepted line match | `QuoteContinuationPolicy.java` lines 119–123 (`read`). Tests of `plan` were not executed. **Unverified by execution** | No migration |
| One document per `lifecycle_change_id` | `uq_amendment_document_lifecycle` in `V1_7__contract_documents.sql` line 95 (`read`) | `V1_7__contract_documents.sql` |
| Many lines per revision. Unique `(document_id, revision_no, line_no)`. `line_no >= 1`. No SQL check that the numbers are gapless | Same file, lines 214–256 and `chk_amendment_line_no` line 298 (`read`) | `V1_7__contract_documents.sql` |
| One non-null source line per revision | Partial unique index `uq_amendment_line_source` lines 301–303 (`read`) | `V1_7__contract_documents.sql` |
| `UPDATE` and `REMOVE` require `source_accepted_line_uuid`. `ADD` does not | `chk_amendment_line_source_required` lines 281–284 (`read`) | `V1_7__contract_documents.sql` |
| Quantity identity `start_qty + delta_qty = end_qty` and `end_qty >= 0` for every semantics value | `chk_amendment_line_qty` line 276 (`read`) | `V1_7__contract_documents.sql` |
| Header totals `tcv_delta`, `arr_delta`, `one_time_total`, `credit_total` are `numeric(19, 4) NOT NULL`. The line table has `unit_price` and `net_amount`, and no ARR-bridge columns | Header lines 71–74; line columns 239–240; line body 214–298 (`read`) | `V1_7__contract_documents.sql` |
| Application units: `dependency_order integer NOT NULL`, status `PENDING`, `APPLIED`, `BLOCKED`, target type `SUBSCRIPTION`, `ENTITLEMENT`, `ONE_TIME`, `NONE`. No `depends_on` array. The `CREATE TABLE` statement has no `UNIQUE` on `dependency_order` | Lines 189–212 (`read`) | `V1_7__contract_documents.sql` |
| Each line belongs to one unit | `application_unit_no` line 250 and `fk_amendment_line_unit` lines 259–260 (`read`) | `V1_7__contract_documents.sql` |
| Frozen revision: INSERT, UPDATE, and DELETE of its lines are rejected | `V1_7_2__freeze_amendment_line_membership.sql` function `reject_frozen_amendment_line_mutation` (`read`) | `V1_7_2__freeze_amendment_line_membership.sql` |
| Append-only hash-chained events. `event_type varchar(80)` has no value list. `unit_no` is optional on the event | `amendment_document_events` lines 305–346 (`read`). Writer class comment: `AmendmentEventWriter.java` lines 18–22 (`read`). The rest of `append` was not re-read. **Unverified** beyond those lines | `V1_7__contract_documents.sql` |
| `applied_quote_acceptances.accepted_quote_id` is `UNIQUE`. `subscription_id` is `NOT NULL`. One row names one subscription | `V1_6__subscription_rate_schedule.sql` lines 52–56 (`read`) | `V1_6__subscription_rate_schedule.sql` |
| One thin contract per existing subscription. The backfill does not regroup. Flyway installs the functions and does not execute them | `V1_8__contract_header_backfill.sql` header comment (`read`); [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) (`read`) | `V1_8__contract_header_backfill.sql` |
| Document `contract_id` is `NOT NULL`. `subscriptions.contract_id` is nullable | `V1_7__contract_documents.sql` line 48 and the `ALTER TABLE subscriptions` at lines 352–353 (`read`) | `V1_7__contract_documents.sql` |

Quote-service still has no `LifecycleChange` type. That absence is Cluster E's GAP (`read` that section). This note does not search the tree again and does not build the type.

## Owner-decision table

Every row is Proposed, unaccepted, owner decision required. The default is the recommendation. The alternatives are the other options in that fork.

| Fork | Question | Default | Alternatives |
|---|---|---|---|
| 536-F1 | What carries the lines | One `lifecycle_change_id`, one amendment document, many lines on the current revision | One document per line; one quote per line and no shared document |
| 536-F2 | What happens to `QuoteContinuationRequest` | Leave the one-line request as stored until a separately authorized S5 change. The later shape, not applied here, is one request with a line list | Edit `QuoteContinuationRequest` in this decision; never add a line list |
| 536-F3 | How modes sit on that one amendment | One document amendment mode. Lines may mix first-slice actions and per-line price modes. Co-term is one document mode. Follow Cluster A and Cluster E; do not reopen them | One action code for the whole document; one price mode for the whole document; store the amendment mode only on the LifecycleChange |
| 536-F4 | Source and repeated products | Keep `uq_amendment_line_source`. Two `ADD` lines may name the same product | Refuse two `ADD` lines with the same product; allow two lines to name the same source |
| 536-F5 | What `line_no` orders | Seller order inside the revision, for display and for the digest. Not the apply order | `line_no` is the apply order; catalog order, gaps allowed |
| 536-F6 | How lines become units, and who orders units | OQ5 option A unchanged: one unit per target subscription. Who writes `dependency_order` stays the unmet DRG-565 item | One unit per line; the whole document is one unit even across subscriptions; this note adopts create-before-terminate |
| 536-F7 | A line fails while pricing or validating | The whole revision stays unpriced. No partial price and no dropped line | Price the lines that succeeded and omit the rest; copy the previous revision's totals onto the new revision |
| 536-F8 | A unit fails at apply | OQ5 option A unchanged. Inside a unit, all lines commit or the unit writes nothing. The remedy is a new correcting document | Roll back units that already committed; mark one line `BLOCKED` inside a committed unit; rebase the same document |
| 536-F9 | Header totals | One snapshot of the whole current revision, written when pricing succeeds, not recomputed at apply. No line-level ARR bridge | Recompute the four totals at apply from committed units only; add per-line bridge columns in this note |
| 536-F10 | Audit grain | One hash-chained document event per transition. The digest covers the revision. No new event contract | One event per line; a new multi-line event type |
| 536-F11 | Which subscriptions a line may name | Only a subscription whose `contract_id` is the document's `contract_id`. OQ7 is not regrouped | Lines may name subscriptions on other contracts; one continuation per subscription even when they share a contract |
| 536-F12 | Uplift when a later line uses it | Each such line follows Accepted D-04 on its own: clause, then line, then agreement, then account, then the rules default. A first-slice submit still does not send `RENEW` | One uplift percent for every line on the document; a different precedence stack |

---

## 536-F1 — One document carries the lines

| Option | Meaning |
|---|---|
| A | One LifecycleChange id, one `amendment_documents` row, many `amendment_document_lines` on the open revision. |
| B | Each line is its own document and its own number. |
| C | Each line is its own quote. Nothing groups them. |

**Recommendation:** A. `read` `uq_amendment_document_lifecycle` and `uq_amendment_line_triple` in `V1_7__contract_documents.sql`.

**Default:** Proposed, unaccepted, owner decision required. One amendment is one document and one LifecycleChange id, and that revision holds every line (option A). Alternatives written out: one document per line (option B); one quote per line with no shared document (option C). Option C is what `QuoteContinuationService.java` line 288 does today. This default does not turn that refusal off.

## 536-F2 — The continuation request stays one line until S5 is authorized

Cluster E fork D5 default is to leave `QuoteContinuationRequest` unchanged until a separately authorized S5 change (`read` Cluster E). This fork does not replace D5.

| Option | Meaning |
|---|---|
| A | The Java request stays one source line, one quantity, and one `effectiveDate`. A later authorized change may carry a line list. This note does not apply that list. |
| B | Add the line list to `QuoteContinuationRequest` in this decision. |
| C | The product never grows a line list. |

The list option A would use later, and does not add now, is one request whose lines each carry `action_code`, `quantity_semantics`, `start_qty`, `delta_qty`, `end_qty`, `product_external_id`, and `source_accepted_line_uuid` when the action requires a source. Header dates stay the four document dates. Cluster E D4 and [DRG-572](story-drg-572-one-time-retroactive-charge-lines-decision-frame.md) fork 4 keep a per-line effective date off the line. Cluster E D1 keeps proration on `contract_effective_date` and `price_as_of` on `EFFECTIVE_DATE` for a future first-slice writer. Those defaults are unchanged.

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Leave `QuoteContinuationRequest` as stored until a separately authorized S5 change (option A). The line list above is the shape that later change would use. It is not applied in this note, and it does not fill Cluster E's GAP. Alternatives written out: edit `QuoteContinuationRequest` in this decision (option B); never add a line list (option C).

## 536-F3 — Several modes on that one amendment

Modes are not one field. Cluster A and Cluster E already split them. This fork states the multi-line consequence and does not reopen the source forks.

| Layer | Proposed default this note follows | Source fork |
|---|---|---|
| Document amendment mode | One mode per document, a later column, not S1. Until that column exists, documents are amend-in-place because OQ26 says so | Cluster A 592-F5. Alternatives there: infer the mode from a new `contracts` row; store the mode only on the LifecycleChange |
| Document intent and slot | A future first-slice submit sends intent `AMEND` and pins slot `AMENDMENT` | Cluster E E7-A and E3-A |
| Line action and quantity | A revision may mix `ADD`, `UPDATE`, and `REMOVE`, each `DELTA` or `ABSOLUTE`. Other stored tokens stay stored and are not a first-slice submit | Cluster E L2-A, L1-A, L3-A, L5-A |
| Line category | One `business_category`, derived as Cluster E L4 states. No compound category | Cluster E L4-A. ARR split stays [DRG-575](story-drg-575-arr-bridge-per-change-line-decision-frame.md) (#660) |
| Line price mode | `RetainContracted`, `List`, or `Override`. Omitted means `RetainContracted`. Tenant default plus a per-line override, snapshotted when priced | Cluster A 550-F1 and 550-F4 |
| Credit and fee | `NoCredit` and `NoFee` | Cluster A 550-F2 and 550-F3 |
| Co-term | One document mode from the four DRG-554 modes plus `AlignToDate`. Only lines in the change move. A single-agreement amendment with no policy keeps V5. A multi-agreement change with no mode is refused | Cluster A 554-F1, 554-F3, 554-F2 |
| Charge type | Store `RECURRING`. A one-time line is not allowed yet | [DRG-572](story-drg-572-one-time-retroactive-charge-lines-decision-frame.md) (#654) forks 1 and 5 |
| Legal review and SKU hold | One line outside the free set, or one contested mapping, holds the whole document. The class list and the mapping table stay owner options and are not adopted | Cluster A 594-F4, 594-F1, 599-F3, 599-F1 |

| Option | Meaning |
|---|---|
| A | The table above. Mixed first-slice actions and mixed per-line price modes are allowed on one revision. The document still has one amendment mode, one intent, and one co-term mode. |
| B | Every line on the document must share one action code. |
| C | Every line shares one price mode. This is Cluster A 550-F4 option B. |
| D | The amendment mode lives only on the LifecycleChange. This is Cluster A 592-F5 option C. |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. One document mode, mixed first-slice line actions, and per-line price modes (option A), following the source forks named in the table. Alternatives written out: one action code for the whole document (option B); one price mode for the whole document (option C); the amendment mode stored only on the LifecycleChange (option D). Entitlement resolution for a line, and any conversion price, are pending the DRG-609 design (not accepted). This note does not specify them. The proration formula is not restated.

## 536-F4 — Source identity

| Option | Meaning |
|---|---|
| A | The partial unique index stays the rule. `UPDATE` and `REMOVE` name one source. `ADD` has a null source. Two `ADD` lines may repeat `product_external_id`. |
| B | Two `ADD` lines with the same product are refused. |
| C | Two lines in one revision may name the same source. That needs a migration to drop `uq_amendment_line_source`. |

**Recommendation:** A. `read` the index and `chk_amendment_line_source_required`. Cluster E L6-A leaves that check as written.

**Default:** Proposed, unaccepted, owner decision required. One non-null source per revision, and repeated `ADD` products are allowed (option A). Alternatives written out: refuse two `ADD` lines with the same product (option B); allow two lines to name the same source (option C). Option C is a later migration at `V1_10` or higher, next free at build time, with a rollback statement. It is not written here. Never `V1_7_3`. `V1_9` stays reserved for the S4 `document_apply_ledger`.

## 536-F5 — Line order

| Option | Meaning |
|---|---|
| A | `line_no` is the seller's order, starting at 1, unique per revision. The digest lists lines in that order. Apply does not read `line_no` as its sequence. |
| B | `line_no` is the apply order. |
| C | Lines are ordered by catalog id. Gaps are allowed. |

The landed check is `line_no >= 1` plus the unique triple (`read`). It does not require a gapless sequence. Gapless numbering is this proposal, not a constraint in `V1_7__contract_documents.sql`.

A frozen revision cannot gain, lose, or rewrite a line (`read` `V1_7_2__freeze_amendment_line_membership.sql`). A change of membership before execution follows quoted OQ6 rule 1, and a change after execution follows quoted OQ6 rule 2. This note does not choose the CAM command. Quoted OQ6 rule 3 leaves that command open.

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. `line_no` orders the revision for display and for the digest, and it is not the apply order (option A). Alternatives written out: `line_no` is the apply order (option B); catalog order with gaps allowed (option C).

## 536-F6 — Units and apply order

OQ5 option A is unchanged. The adopted points, as the register row and [partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md) record them (`read`): one unit per target subscription; a unit is atomic; no automatic rollback; `PARTIALLY_APPLIED` when a later unit fails after at least one unit has committed; `APPLY_BLOCKED` when no unit has committed; the remedy is a correcting amendment; signature stays all-or-nothing.

[partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md) also records (`read`) that the written create-before-terminate rule, including who writes `dependsOnUnitNos`, is **UNMET**. The landed column is `dependency_order`, not an array named `dependsOnUnitNos` (`read` `V1_7__contract_documents.sql`). The field-schema cell says `dependency_order` "creates before terminates" (`read` [amendment-entity-field-schema.md](../amendment-entity-field-schema.md) §2.6). That cell does not close the unmet rule. This note does not close it either.

Cluster B [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) DRG-559's proposed default remains one Restructure change whose apply shape is three named units, create before terminate, not built (`read` that frame's owner-decision row). This note does not build that shape and does not move it into the first slice. Cluster F [#655](https://github.com/drgaciw/open-crm-cpq/pull/655) leaves revoke submission timing open (`read` fork F9). This note does not choose it. Cluster C [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) leaves true-up-plus-arrears apply deferred (`read` its DRG-565 dependency row). This note does not specify a true-up unit.

| Option | Meaning |
|---|---|
| A | Lines that share one target subscription are one unit. Units apply in stored `dependency_order`. The writer of that integer stays open. |
| B | Each line is its own unit. |
| C | Every line on the document is one unit, including lines that name different subscriptions. |
| D | This note adopts create-before-terminate and names the writer. |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. One unit per target subscription, atomic, in stored `dependency_order`, writer still unmet (option A). Alternatives written out: one unit per line (option B); one unit for the whole document across subscriptions (option C); this note adopts create-before-terminate (option D). First-slice lines use `apply_target_type = SUBSCRIPTION`. `ENTITLEMENT` and `ONE_TIME` stay storable and are not a first-slice submit. Entitlement resolution is pending the DRG-609 design (not accepted). A one-time line stays not allowed yet under DRG-572 fork 5. S4 is not authorized to enforce the order. `V1_9` is not written.

## 536-F7 — Partial failure before execution

| Option | Meaning |
|---|---|
| A | One line that fails validation or pricing fails the revision. No line of that revision is stored as priced. Totals are not a sum of the lines that succeeded. |
| B | Successful lines are priced and the failed line is omitted. |
| C | The new revision copies the previous revision's four totals. |

Option A matches Cluster A 594-F4 and 599-F3: the document waits as a whole. It is distinct from OQ5, which is an apply outcome. Quoted OQ6 rule 1 is the revision rule. Its last sentence leaves the persisted representation of an unpriced revision undecided.

[#657](https://github.com/drgaciw/open-crm-cpq/pull/657) option (a) is the proposed representation: the four totals, the digests, and the pricing pins are null together, in a later migration at `V1_10` or higher (`read` [s3-reconciliation-and-owner-decision-frame.md](s3-reconciliation-and-owner-decision-frame.md)). Recommending that option there is not a migration approval. This note does not approve one either. Placeholder zeroes are not an unpriced representation (`read` the same section and the field-schema unpriced-totals paragraph). Option (c) in that frame, priced revisions only, conflicts with OQ6 and is not this default.

**Recommendation:** A, with storage of the unpriced revision left on #657 option (a).

**Default:** Proposed, unaccepted, owner decision required. A pricing or validation failure leaves the whole revision unpriced (option A). Alternatives written out: keep the lines that priced and drop the rest (option B); copy the previous totals forward (option C). No migration is written. Any later migration that stores the nulls is `V1_10` or higher, next free at build time, and its rollback stops while any affected row remains null and does not invent amounts. `V1_9` stays reserved. Never `V1_7_3`.

## 536-F8 — Partial failure at apply

| Option | Meaning |
|---|---|
| A | OQ5 option A, unchanged. Lines in one unit commit together or the unit writes nothing. Committed units stay committed. The fix is a new LifecycleChange and a new document. |
| B | A failed later unit rolls back units that already committed. |
| C | One line inside a committed unit is `BLOCKED` and the other lines of that unit stay applied. |
| D | Rebase: a new revision of the same executed document. |

Quoted OQ6 rule 2 is the post-execution rule. Option A uses that quoted remedy. Option D would revise a document that quoted rule 2 keeps immutable.

Under 536-F11 and the adopted OQ7 rule, a first-slice document names one contract and, after that thin-contract shape, one subscription. One subscription is one unit. That unit either commits (`APPLIED`) or writes nothing (`APPLY_BLOCKED`). `PARTIALLY_APPLIED` stays the OQ5 outcome for a document that later has two target subscriptions. This note does not create that second subscription.

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Apply failure follows OQ5 option A, and a unit does not commit a subset of its lines (option A). Alternatives written out: roll back committed units (option B); block one line inside a committed unit (option C); rebase the executed document (option D). S4 and S6 stay unauthorized. No apply is run.

## 536-F9 — Totals

| Option | Meaning |
|---|---|
| A | When pricing succeeds, the four header amounts are one snapshot of every line in the revision. Apply reads them. It does not recompute them. |
| B | Apply recomputes the four amounts from the units that committed. |
| C | This note adds per-line ARR-bridge columns. |

There is no line column for `tcv_delta`, `arr_delta`, `one_time_total`, or `credit_total` (`read` `V1_7__contract_documents.sql`). This note does not define the arithmetic that fills the four headers. The proration ADR stays Accepted and is not restated. [DRG-575](story-drg-575-arr-bridge-per-change-line-decision-frame.md) (#660) keeps the bridge off the line. Under Cluster A 550-F2 the credit policy in force is `NoCredit`, so this policy writes no credit into `credit_total`. Under DRG-572 fork 5 a one-time line is not allowed yet, so this note does not place a one-time amount in `one_time_total`. A conversion amount is pending the DRG-609 design (not accepted). Currency is the document `currency_code`. A line in another currency is refused. Illustrative figures from the sanity check are not recomputed here.

Quoted OQ6 rule 2 says the document is immutable in every apply state, including `PARTIALLY_APPLIED`. A blocked unit does not gain a second set of totals.

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. The four header totals are one digest-bound snapshot of the priced revision, not recomputed at apply (option A). Alternatives written out: recompute them at apply from committed units (option B); add per-line bridge columns in this note (option C). Option C would be a migration at `V1_10` or higher and is not written. DRG-575 remains the bridge frame.

## 536-F10 — Audit

| Option | Meaning |
|---|---|
| A | One append-only document event per transition, written by the existing `AmendmentEventWriter`. `payload_digest` covers the revision: header, every line, and the four totals. `unit_no` may name a unit on an apply outcome because the column exists. No new `event_type` list. |
| B | One event per line. |
| C | A new event type for multi-line apply, with a topic and a registry entry. |

[DRG-564](story-drg-564-policy-decision-audit-trail.md) remains a proposed per-line PolicyDecision note (`read` its status: Proposed, not accepted). This note does not accept it. The line columns `policy_id`, `policy_version`, `policy_decision_id`, and `pricing_trace_id` stay as `V1_7__contract_documents.sql` defined them (`read`). Quoted OQ6 text: the delegated ruling does not choose a supersession event type, payload, schema, topic, or outbox contract. S6 is not authorized.

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. One document event per transition, digest over the whole revision, no new event contract (option A). Alternatives written out: one event per line (option B); a new multi-line event type with a topic and a registry entry (option C). Option C is S6 work and is not authorized.

## 536-F11 — One contract

| Option | Meaning |
|---|---|
| A | Every line's target subscription has `subscriptions.contract_id` equal to `amendment_documents.contract_id`. A mismatch is refused. Subscriptions are not regrouped. |
| B | One document's lines may name subscriptions on other contracts. |
| C | Subscriptions that share a contract still get one continuation each. |

OQ7 stays the adopted thin contract: one header per existing subscription, and the backfill does not regroup (`read` `V1_8__contract_header_backfill.sql`). Cluster A 554-F6 does not select a regrouping. This note does not run `contract_header_backfill_count`, `contract_header_backfill_apply`, or `contract_header_backfill_compensate`.

`applied_quote_acceptances` cannot store one quote against two subscriptions (`read` `V1_6__subscription_rate_schedule.sql`: `accepted_quote_id` unique, one `subscription_id`). That gap is DRG-563. The S4 ledger that would record units is `V1_9`, reserved, and not written. Cluster B cites the same ledger limit (`read`). This note does not start DRG-563.

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Lines stay on the document's contract, and OQ7 is not regrouped (option A). Alternatives written out: lines across contracts (option B); one continuation per subscription even when they share a contract (option C). Several lines on that one subscription are the first-slice multi-line case. Several subscriptions on one document wait on a later owner choice this note does not make.

## 536-F12 — Uplift precedence, per line

The Accepted uplift record D-04 (`read`) takes each field from the first rung that sets it: contract clause, then line, then agreement, then account, then the rules default. Cluster E E7-A and L2-A keep `RENEW` off a future first-slice submit. [DRG-549](drg-549-renewal-pricing-resolution-design-frame.md) (#658) remains that frame. This note does not restate its pricing walk and does not enable a renewal submit.

| Option | Meaning |
|---|---|
| A | If a later story prices uplift on a line, that line uses D-04 on its own. A first-slice amendment submit still does not send `RENEW`. |
| B | One uplift percent on the document applies to every line. |
| C | A different precedence stack for amendment lines. |

**Recommendation:** A.

**Default:** Proposed, unaccepted, owner decision required. Per-line D-04 precedence, clause then line then agreement then account then rules, and no first-slice `RENEW` submit (option A). Alternatives written out: one document-level uplift percent (option B); a different precedence stack (option C). Option C would reopen an Accepted record. This note does not adopt it. No price is calculated.

---

## What an owner decision on this note still does not do

A decision on any row:

- authorizes no code, migration, flag, topic, gateway route, or deploy;
- does not edit `QuoteContinuationRequest`, `QuoteContinuationService`, or `QuoteContinuationPolicy`;
- does not fill the quote-service LifecycleChange GAP;
- does not start S3 / DRG-581, S4 / DRG-582, S5 / DRG-583, or S6;
- does not authorize S6;
- leaves ADR-017 and ADR-024 through ADR-027 Proposed, and does not use them as prerequisites;
- leaves the proration record Accepted and does not restate it;
- leaves OQ5 option A and the OQ7 thin contract unchanged;
- does not run a backfill and does not run apply;
- does not accept the DRG-609 design and does not specify entitlements or conversion price;
- does not accept DRG-564, `specs/partial-apply.md`, or `specs/lifecycle-intents.md`;
- does not close DRG-565's unmet ordering rule;
- writes no migration. `V1_9` stays reserved for `document_apply_ledger`. Any future migration is `V1_10` or higher, next free at build time, with a rollback statement that stops while any affected row remains null and never fabricates values. Never `V1_7_3`.

The ticket stays In Review until the owner merges.

## Unverified

- Linear comments on DRG-536, and any status change after the 2026-10-17 read.
- The full body of DRG-563. The ledger columns cited above were read. The issue body was not.
- `QuoteContinuationPolicy` tests. The source method was read. It was not executed.
- The remainder of `AmendmentEventWriter.append` after the class comment.
- A live database catalog, and whether any environment has applied the CAM migrations.
- Quote migration `V1_10__quote_subscription_continuation.sql`, beyond Cluster E's citation and the Java field read here.
- GitNexus. `list_repos` returned no repository. Impact: **UNKNOWN**.
