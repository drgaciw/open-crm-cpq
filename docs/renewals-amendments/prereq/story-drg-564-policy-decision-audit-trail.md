# DRG-564 — PolicyDecision and price-snapshot audit trail

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Date: 2026-10-16.*

**Story:** [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564/continuation-pricing-decisions-are-not-audited-no-policy-trail-or) (RA-30). Design note for the per-line PolicyDecision record and the frozen price inputs that [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy) and [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549/renewal-pricing-policy-hierarchy-same-list-uplift-percent-uplift) record into.

**Status:** Proposed. Not accepted. This file is not an ADR, takes no number, and is not copied into `docs/adr/`. Acceptance is the owner's act. Until then this note authorizes no code, no Flyway file, no Drools edit, no flag, no route, and no merge.

**Does not change:** [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) and [ADR-024](../../adr/ADR-024-order-orchestration-context.md) through [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) stay **Proposed** in `docs/adr/README.md` on this commit. This note does not treat them as accepted. It does not edit the Accepted uplift record or the Accepted proration record.

**Commit read:** `e3ee211b` (`origin/main` at the time of writing). [#630](https://github.com/drgaciw/open-crm-cpq/pull/630) is the merge commit `fd6421e4` on that history (S2 hash-chained amendment event writer).

## Verification

Nothing in this pass was executed. No Maven run, no Drools session, no Testcontainer, no HTTP call.

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at `e3ee211b`. Not executed. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading the pull request` | Read from GitHub metadata in this pass. The diff body was not opened. |
| `unverified` | Not established by a file, issue, or pull-request read in this pass. |

GitNexus `query` for this concept returned `No indexed repositories`. Blast radius is **UNKNOWN**. The file reads below are the evidence. An empty graph is not evidence that a symbol is unused.

## Question

Where does a continuation line's PolicyDecision live, what does the row contain, and which existing audit mechanisms does that row reuse?

The gap DRG-564 names: continuation rates are computed in quote-service by `QuoteContinuationPolicy`, that path writes no `PricingAuditLog` row, and the `SubscriptionScheduleView` read at `observedRevision` is not frozen. `verified by reading the issue`

## Reused, and the distinct gap

### Reused from the S2 hash chain (#630)

These pieces stay as they are. This note does not extend them.

| Piece | What it already does | How a PolicyDecision uses it |
|---|---|---|
| `amendment_document_events` | Append-only document lifecycle row in the `contract` schema. Columns include `prev_hash`, `content_hash`, `payload_digest`, `payload_ref`, per-document `seq_no`. UPDATE, DELETE, and TRUNCATE are rejected. `cam_app` has SELECT and INSERT. File: `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`. | The document line already has text columns `policy_id`, `policy_version`, `policy_decision_id`, `pricing_trace_id` (`docs/renewals-amendments/amendment-entity-field-schema.md`). A later event may set `payload_ref` to the decision id and `payload_digest` to `decision_digest`. The event row stays a lifecycle record. |
| `AmendmentEventWriter` | Appends one event and an outbox row in the caller's transaction. Requires an existing `AmendmentDocument`. Locks that row. Gapless `seq_no`. Duplicate `source_event_id` returns the original row. `payloadDigest` must be 64 lowercase hex characters. File: `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java`. | A priced document can cite a decision that already exists. The writer is not the component that stores rate periods, uplift rungs, or a schedule view. |
| `AmendmentEventHasher` | SHA-256 over canonical version 1 of the event row. Excludes `content_hash` and `recorded_at`. Genesis `prev_hash` is 64 zeros. File: `.../events/AmendmentEventHasher.java`. | The digest algorithm family (SHA-256, 64 lowercase hex) is the same shape `payload_digest` already requires. The canonical form is the event row. A price snapshot uses a different canonical form. The hasher class stays in the CAM module. |
| `AmendmentChainVerifier` | Recomputes `prev_hash` and `content_hash` per document and reports a gap or a broken link. File: `.../audit/AmendmentChainVerifier.java`. | It verifies the document chain. It does not verify price arithmetic. |
| `AmendmentAuditOutbox` | Stages `AMENDMENT_AUDIT_RECORDED_V1` in the same transaction. Payload carries document id, amendment number, `contentHash`, correlation id, event id. File: `.../audit/AmendmentAuditOutbox.java`. | Publication of the document event stays on this outbox. The price body is not added to that payload. |

`draftRenewal` opens a quote before any amendment document exists. `AmendmentEventWriter.append` throws when the document id is unknown. A renewal draft therefore has no chain to append to. `verified by reading code`

### Reused from quote-service audit that already shipped

| Piece | What it already does | Boundary |
|---|---|---|
| `commercial_decision_audit` | One append-only row per system-created renewal draft. `V1_11__processed_events_inbox.sql` says DRG-564 owns the general continuation pricing-decision trail, and that this table records only the decision to open the draft. `QuoteContinuationService.recordDecision` inserts `decision_kind` `SYSTEM_RENEWAL_DRAFT`, `policy_id` `QuoteContinuationPolicy`, `operation` `RENEWAL`, `mode` `RENEWAL_EXIT_RATE`. Inputs include subscription id, observed revision, exit quantity, exit unit price, and `upliftPercent` `"0"`. There is no policy version, no pricing-service trace id, and no per-line decision id. `author` does not insert this row. | Stays the draft-open decision. It is not the per-line price snapshot. |
| `processed_events` | ADR-018 inbox in the same migration: primary key `(consumer_group, event_id)`. `SubscriptionRenewalEligibleEventConsumer.onRenewalEligible` claims the inbox, then calls `draftRenewal`, in one transaction. A failed claim returns before a second draft. | Stays the idempotency claim for that consumer. A later price-snapshot insert for that draft belongs in the same transaction, after the claim. The inbox row is not a pricing decision. |

`verified by reading code`

### Reused from pricing-service, as the execution log only

`PricingAuditLog` (`pricing_audit_log`) is one row per `execute` when `persistAudit` is true. `persistAuditLog` sets quote id, customer id, `ruleSetId`, JSON of the context and the result, and `executionTimeMs`. It does not set `waterfallTrace`, `rulesFired`, or `executedBy`. The generated `uuid` is not copied onto `PricingResult`. A `JsonProcessingException` is logged and the method returns. File: `PricingAuditLog.java`, `PricingExecutionService.persistAuditLog`. `verified by reading code`

`QuoteContinuationPolicy.plan` does not call pricing-service. A renewal or amendment priced on that path has no `pricing_audit_log` row. `verified by reading code` for the policy class; the absence of a pricing client call inside that class was read directly. Whether some other quote-service method writes a pricing audit row for the same plan was not searched beyond `QuoteContinuationService` and this policy. `unverified` for every other caller.

No Java type named `PolicyDecision` exists under `backend/` on this commit. `verified by reading code`

### Distinct gap

The missing record is one immutable snapshot per priced continuation line:

- policy id and version, intent, and mode
- the inputs that produced the price, including the schedule view at the observed revision
- outputs, explanation codes, rules fired, who decided
- a digest of that body

That body is not an `amendment_document_events` row, not a `processed_events` claim, not the `SYSTEM_RENEWAL_DRAFT` row, and not `pricing_audit_log.uuid`. [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609/story-pricing-service-support-for-addupdateremove-lines-with)'s design note on `main` already returns a new id on a sketched HTTP body and assigns the insert to [DRG-612](https://linear.app/drgamtd-workspace/issue/DRG-612/story-commercial-decision-audit-mechanism-for-pricing-rule-outcomes). `verified by reading code` for that note. This note defines the row those ids would point at. It does not accept the DRG-609 route sketch.

DRG-612, as read, says the repo must end with one commercial-decision mechanism, reconciling this record, `commercial_decision_audit`, and the unused `PricingAuditLog` columns. `verified by reading the issue`. This note gives that story one target table. It does not design a second writer, a second chain, or a pricing-schema copy.

## Where the record would live

**Module:** quote-service, PostgreSQL schema `quote`. ADR-008 (Accepted) gives each service its own schema and forbids cross-schema joins. pricing-service cannot insert into `quote`. CAM cannot insert into `quote`. quote-service persists the row.

**Who computes the numbers** is separate from who inserts the row:

- Renewal unit price, once [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy) implements the Accepted uplift record: pricing-service merges the rungs and returns the body. quote-service stores that body and does not recompute it. The uplift record says pricing-service stays stateless and does not read another service's database.
- Amendment day-stub, under the Accepted proration record D-02: the running method remains `QuoteContinuationPolicy.plan` in quote-service. The same table can hold that row. See Owner decision 2 for the trace id.

**Would-be migration, not created by this note:** `backend/services/quote-service/src/main/resources/db/migration/V1_13__policy_decisions.sql`.

Versions read on `e3ee211b`:

| Schema | Latest file on this commit | Next number | Reserved by |
|---|---|---|---|
| `quote` | `V1_12__continuation_operation.sql` | **V1_13** for this table | this sketch only; no file is added |
| `pricing` | `V8__guard_managed_seed_optional_inputs.sql` | V9 is the next integer | [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542/amendment-proration-rule-conflicts-with-continuation-formula), open PR [#638](https://github.com/drgaciw/open-crm-cpq/pull/638), file `V9__disable_unedited_system_amendment_proration.sql`. `verified by reading the pull request` (file list only; the SQL text was not opened). #638 is open and not merged. This design does not use V9 or V10. |
| `contract` | `V1_8__contract_header_backfill.sql` on this commit; the event table is `V1_7__contract_documents.sql` | none for this design | S2 chain and the header backfill stay untouched |

### Table sketch

Sketch only. Not a Flyway migration. Not applied.

```text
-- SKETCH. Not a migration. Would-be file name:
-- backend/services/quote-service/src/main/resources/db/migration/V1_13__policy_decisions.sql
-- Schema: quote. Append-only. One row per priced continuation line per evaluation.
-- A reprice inserts a new row. No UPDATE, DELETE, or TRUNCATE of an existing row.
-- Do not alter commercial_decision_audit or processed_events in this version.

policy_decisions (
    id                         UUID PRIMARY KEY,          -- the policy_decision_id other rows store as text
    quote_id                   BIGINT NOT NULL REFERENCES quotes (id),
    quote_line_id              BIGINT NULL REFERENCES quote_line_items (id),
    amendment_document_line_id VARCHAR(36) NULL,          -- text pin only; no cross-schema foreign key
    subscription_id            VARCHAR(200) NOT NULL,
    observed_revision          BIGINT NOT NULL,
    decision_kind              VARCHAR(64) NOT NULL,      -- RENEWAL, EXTENSION, AMENDMENT, EXPANSION
    policy_id                  VARCHAR(100) NOT NULL,
    policy_version             VARCHAR(64) NOT NULL,
    mode                       VARCHAR(64) NOT NULL,
    mode_rung                  VARCHAR(32) NULL,          -- clause, line, agreement, account, rules, caller
    pricing_trace_id           VARCHAR(64) NULL,          -- see Owner decision 2
    decided_by                 VARCHAR(100) NOT NULL,
    inputs_json                TEXT NOT NULL,             -- canonical snapshot, including the schedule view that was read
    outputs_json               TEXT NOT NULL,
    rules_fired                TEXT NOT NULL,             -- JSON array; empty array when no rule fired
    explanation_codes          TEXT NOT NULL,             -- JSON array
    reason_text                TEXT NULL,
    inputs_digest              CHAR(64) NOT NULL,         -- lowercase hex SHA-256 of inputs_json
    decision_digest            CHAR(64) NOT NULL,         -- lowercase hex SHA-256 of the canonical decision body
    canonical_version          INTEGER NOT NULL,          -- starts at 1; this form is not AmendmentEventHasher
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
)
```

Indexes the implementation story would add: `(quote_id)`, `(subscription_id, decision_kind)`. No unique constraint on `(quote_id, quote_line_id)`. A correction is a new row (mid-term A1).

Append-only enforcement the implementation story would add, in this same would-be version: a quote-schema trigger that rejects UPDATE, DELETE, and TRUNCATE, modeled on `reject_amendment_document_event_mutation` in CAM `V1_7`. A new function in the quote schema. Not a call into the CAM function. `commercial_decision_audit` has a comment that says append-only and has no such trigger. `verified by reading code`. The price snapshot uses the trigger, because A1 forbids updating a PolicyDecision.

`decision_digest` canonical version 1 covers: `canonical_version`, `policy_id`, `policy_version`, `mode`, `mode_rung`, `decision_kind`, `observed_revision`, `inputs_digest`, and the canonical `outputs_json`. It excludes `id`, `created_at`, and `pricing_trace_id` so a trace assigned after the body is hashed does not rewrite the digest. That exclusion is a proposal in this note. `AmendmentEventHasher` excludes `recorded_at` for a different reason (the database overwrites it). The two canonical forms stay different. quote-service does not call the CAM hasher (ADR-008). A shared helper in `backend/libs/common` is new code and is out of this note.

`inputs_json` for every row includes the `SubscriptionScheduleView` facts `plan` actually read: subscription number, customer id, currency, status, start, end, commercial revision, and the selected line's accepted-line uuid, product external id, provenance, and each non-retired rate period's start, end, quantity, net unit price, and net monthly. Spike §5 also names an evidence store addressed by digest. This sketch stores the JSON in the row and the digest beside it. A separate evidence store is [DRG-311](https://linear.app/drgamtd-workspace/issue/DRG-311) / [DRG-312](https://linear.app/drgamtd-workspace/issue/DRG-312) and is not designed here.

Keys the JSON gains when the producer is the Accepted uplift merge (DRG-567). Names follow that record's D-07:

`basis`, `basisAmount`, `percentPoints` or `upliftAmount`, `exitNet`, `priorList`, `listUnit`, `priceAsOf` (`ActionDate`, `EffectiveDate`, or `LineStart`), `priceAsOfDate`, `pricebookVersion`, each cap and floor field with the rung that set it, `capApplied`, `floorApplied`, `unitPrice` at scale 4, `extendedAmount` at scale 2, `clauseId`, `rulesRetentionMarker`, `rulesRungSuppliedPercent`, explanation code `RENEWAL_UPLIFT_POLICY` when that rung supplied the percent, and on failure one of `UNRESOLVED_BASIS`, `UNRESOLVED_UPLIFT`, `UNRESOLVED_RENEWAL_MODE`, `PRICE_PROTECTION_CONFLICT`. Churn score may sit in `inputs_json` when the rules rung ran. The uplift record already says the customer-facing document does not gain a churn field. Rounding mode for the single unit is half-up at scale 4 (D-05). Per-step escalator rounding stays DRG-533.

Keys the JSON gains for the amendment stub (Accepted proration D-02): `mode` `RetainContracted`, the partial-period `ProrationSnapshot` fields `plan` already builds (start, end, billing-period bounds, quantity, net unit price, net monthly, period amount), and the following full-month periods. `rules_fired` is `[]` on this path. The classpath `amendment-proration.drl` rule is not this snapshot.

Preview (`QuoteContinuationService.preview`) does not insert. That matches `simulate`, which does not insert a `PricingAuditLog`. `verified by reading code` for both methods' lack of an insert.

## Options

| Option | Verdict |
|---|---|
| **A. New `quote.policy_decisions`, would-be V1_13 (recommended)** | One row per priced line. Leaves `commercial_decision_audit` as the draft-open row, which is what V1_11 already says. Leaves the S2 chain as the document lifecycle. Leaves `pricing_audit_log` as the execute-path log. DRG-612 implements this one table and does not add another. |
| B. Widen `commercial_decision_audit` in V1_13 | The table's comment and its insert are one row per renewal draft, with mode `RENEWAL_EXIT_RATE`. A per-line price snapshot is a different cardinality. `author` does not write the table today. Widening it makes the draft-open row and the price snapshot the same mechanism by force, which is the reconciliation DRG-612 owns, done inside the wrong grain. |
| C. New table in `pricing`, version V10 | V9 is the DRG-542 file on open PR #638. A V10 policy table would be a second store beside the quote-service trail V1_11 already assigned to DRG-564. Today's `QuoteContinuationPolicy` path never calls pricing-service, so the table would be empty for the path DRG-564 describes. ADR-008 blocks quote-service from inserting into `pricing`. |
| D. Store the price body in `amendment_document_events` | The chain hashes a lifecycle row and points at a payload with `payload_ref`. Putting the schedule view in the event row duplicates the chain's job and still misses every renewal draft that has no document. `AmendmentEventWriter` cannot append without a document. |

## Recommendation

Option A. quote-service inserts `policy_decisions`. The computing component returns the body. quote-service does not recompute a body that pricing-service already returned.

DRG-612 is the story that creates the would-be V1_13 file, the trigger, and the insert, after a CPQ Lead sequence. DRG-612 does not also add a `pricing` policy table, does not widen `pricing_audit_log` into this record, and does not change `AmendmentEventHasher`. It may later add a nullable link from `commercial_decision_audit` to `policy_decisions.id`. That link is not in this sketch.

`PricingAuditLog.rules_fired` and `waterfall_trace` stay unused by this design. Filling them on the execute path is a DRG-612 choice about the waterfall log. Those columns are not the continuation snapshot.

The current renewal branch substitutes a null `upliftPercent` with zero and rounds the unit with `money` at scale 2 (`QuoteContinuationPolicy`, `movePointLeft(2)`, `setScale(2, HALF_UP)`). The Accepted uplift record rejects that silent zero (`UNRESOLVED_UPLIFT`) and stores the renewal unit at scale 4. A row written from today's branch uses `policy_id` `QuoteContinuationPolicy`, `policy_version` `continuation-policy-scale2`, `mode` `CALLER_UPLIFT`, and `mode_rung` `caller`. It records the percent actually applied, including zero. It does not use mode `UpliftPct` and it does not name an account rung. That token is how a reader tells a pre-policy draft from an Accepted-uplift decision.

## What DRG-567 needs

DRG-567 implements basis, cap, floor, price-as-of, and the five-rung merge from the Accepted uplift record. That record's consequence says DRG-564's per-line snapshot gains the D-07 fields, and it leaves the audit table to DRG-564.

This note is that table and those fields. A DRG-567 implementation that returns a price without writing this row still fails the snapshot the uplift record names. The fields it must be able to store are the D-07 keys listed above, plus `mode_rung` for the rung that set the mode (D-04: contract clause, then line, then agreement, then account, then the rules default).

This note does not implement the merge, does not change `QuoteContinuationPolicy`, and does not authorize DRG-567 to start. The uplift record's own "does not authorize code" line still stands.

## What DRG-549 needs

DRG-549 owns the mode set `Same`, `List`, `UpliftPct`, `UpliftAmt`, `Override`, `Renegotiate`. The Accepted uplift record D-04 says DRG-549's resolver uses that record's order, and that a missing mode is `UNRESOLVED_RENEWAL_MODE` and a missing percent is `UNRESOLVED_UPLIFT`. The snapshot stores the resolved mode, `mode_rung`, and those codes when the merge fails. It does not choose the catalog.

DRG-549's issue text still says the walk is account, then agreement, then line. `verified by reading the issue`. The Accepted uplift record D-04 says clause, then line, then agreement, then account, then rules. This note stores D-04. It does not reopen precedence and it does not edit the issue.

The renewal policy proposal (`docs/renewals-amendments/prereq/renewal-policy-spec.md`) is a proposal, not an accepted ADR. Its RN3 and AC-RN-05 ask each renewed line to carry policy id, policy version, trace id, basis, cap applied, price-as-of, rounding, and precedence. AC-RN-25 says the pricing input snapshot omits prior-term usage unless the resolved policy declares a usage input. This row is the snapshot those lines name. Accepting this note does not accept that proposal.

## Owner decision needed

1. **Home.** Accept option A (`quote.policy_decisions`, would-be V1_13), or pick B, C, or D. The recommendation is A. Accepting A means DRG-612 must not add a second price-snapshot table in `pricing` or in `amendment_document_events`.
2. **Trace id while quote-service still computes.** The Accepted proration record keeps the amendment stub in `QuoteContinuationPolicy`. Mid-term V8 says every priced line carries a pricing-service trace id and that no line is priced inside quote-service. Those two statements disagree for the stub. Recommendation: the row may be inserted with `pricing_trace_id` null and `policy_id` `QuoteContinuationPolicy` until a pricing-service evaluation returns a trace. The amendment document line columns stay empty until that trace exists, so V8's "not null when priced" on the document line is unchanged. The alternative is to insert no amendment row until that move, which leaves the path DRG-564 describes without a snapshot.
3. **Current renewal zero.** Accept the `CALLER_UPLIFT` / `continuation-policy-scale2` token for rows produced by today's scale-2 branch, so they are not read as Accepted-uplift decisions. The alternative is to insert no renewal row until DRG-567's merge is live.
4. **Inline JSON.** Accept storing the schedule view in `inputs_json` with `inputs_digest` beside it. The alternative is digest-only plus an evidence store. That store is not designed here. Row size of a full schedule view was not measured. `unverified`
5. **Sequencing.** Accepting this note names the shape. It does not sequence DRG-612, DRG-567, or DRG-549, and it does not authorize a migration. DRG-612's issue says not to start without a CPQ Lead sequence. `verified by reading the issue`

## Out of scope

- Creating `V1_13`, any pricing migration, or any CAM migration.
- Editing `AmendmentEventWriter`, `AmendmentEventHasher`, `AmendmentChainVerifier`, or `V1_7`.
- Editing `commercial_decision_audit`, `processed_events`, or `PricingAuditLog`.
- Editing the Accepted uplift record, the Accepted proration record, ADR-023, or `docs/adr/README.md`.
- Treating ADR-017 or ADR-024 through ADR-027 as accepted.
- Accepting the renewal policy proposal, the DRG-609 route sketch, or the OpenAPI draft.
- Price-book retrieval (DRG-539), escalator compounding (DRG-533), and the execute-path waterfall (DRG-311 for pricing-service only, as DRG-564's issue states).

## Scope against DoD v0.2

The pull request scores [CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338). This section is the scope of the design. The score is on the pull request.

This note defines the record and the would-be table. It writes no row, publishes no event, and changes no rule. Commercial-decision persistence, once sequenced, is DRG-612 against option A if the owner accepts it.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-564, DRG-567, DRG-549, DRG-612, DRG-542 | Linear, this pass |
| Uplift record, Accepted, D-04, D-05, D-07 | `docs/renewals-amendments/prereq/adr-renewal-uplift-basis-and-precedence.md` |
| Proration record, Accepted, D-02 | `docs/renewals-amendments/prereq/adr-amendment-proration-formula.md` |
| ADR-008, ADR-017, ADR-024..027 catalog | `docs/adr/ADR-008-postgres-schema-per-service.md`, `docs/adr/README.md` |
| S2 writer, hasher, verifier, outbox | CAM classes named above; migration `V1_7__contract_documents.sql` |
| Draft-open audit and inbox | `V1_11__processed_events_inbox.sql`, `QuoteContinuationService.recordDecision`, `SubscriptionRenewalEligibleEventConsumer` |
| Continuation formula | `QuoteContinuationPolicy.plan` |
| Execute audit log | `PricingAuditLog`, `PricingExecutionService.persistAuditLog` |
| Line pin columns | `docs/renewals-amendments/amendment-entity-field-schema.md` |
| Spike §5 | `docs/renewals-amendments/amendment-document-design-spike.md` |
| V8, A1, A5 | `docs/renewals-amendments/mid-term-amendment-policy-spec.md` |
| DRG-609 hand-off of the insert | `docs/renewals-amendments/prereq/story-drg-609-pricing-service-continuation-design.md` |
| Renewal proposal RN3, AC-RN-25 | `docs/renewals-amendments/prereq/renewal-policy-spec.md` (proposal) |
| Pricing V9 reservation | GitHub PR #638 file list |
| DoD v0.2 | Notion page fetched this pass. `verification.state` was `unverified`. Title and body both say approved. |

## Unverified in this pass

- Any runtime result: inserts, hash values, trigger behaviour, Kafka redelivery.
- The SQL body of `V9__disable_unedited_system_amendment_proration.sql` on PR #638. Only the path was read.
- Whether any class other than `QuoteContinuationService` persists a pricing decision for a continuation plan.
- Byte size of a frozen `SubscriptionScheduleView` in `inputs_json`.
- GitNexus blast radius. The index was absent. Impact is UNKNOWN.
- Notion `verification.state` for the DoD page.
