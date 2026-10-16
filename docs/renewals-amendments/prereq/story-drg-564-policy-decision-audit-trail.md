# DRG-564 — PolicyDecision and price-snapshot audit trail

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Date: 2026-10-16.*

**Story:** [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564/continuation-pricing-decisions-are-not-audited-no-policy-trail-or) (RA-30). Design note for the per-line PolicyDecision record and the frozen price inputs that [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy) and [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549/renewal-pricing-policy-hierarchy-same-list-uplift-percent-uplift) record into.

**Status:** Proposed. Not accepted. This file is not an ADR, takes no number, and is not copied into `docs/adr/`. Acceptance is the owner's act. **Accepting this note authorizes no code and no migration.** It authorizes no Flyway file, no Drools edit, no flag, no route, and no merge. The audit fields [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy) and [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549/renewal-pricing-policy-hierarchy-same-list-uplift-percent-uplift) point at this note only after that acceptance. Until then those stories do not implement against this file.

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
| `commercial_decision_audit` | The migration comment calls the table append-only and says it records only the decision to open a renewal draft, and that DRG-564 owns the general continuation pricing-decision trail. The SQL creates no UPDATE/DELETE trigger. Columns are `id`, `quote_id`, `quote_number`, `decision_kind`, `decided_by`, `policy_id`, `operation`, `mode`, `subscription_id`, `event_id`, `inputs_json`, `outputs_json`, `created_at`. `QuoteContinuationService.recordDecision` is the only insert. It writes `decision_kind` `SYSTEM_RENEWAL_DRAFT`, `policy_id` `QuoteContinuationPolicy`, `operation` `RENEWAL`, `mode` `RENEWAL_EXIT_RATE`. The JSON includes subscription id, observed revision, exit quantity, exit unit price, and `upliftPercent` `"0"`. The column list has no policy version, no pricing-service trace id, and no per-line decision id. `author` does not call `recordDecision`. File: `V1_11__processed_events_inbox.sql`; `QuoteContinuationService.java`. | One row per system-created renewal draft. A per-line price snapshot is a different grain. Option B is the choice to widen this table into that grain. |
| `processed_events` | ADR-018 inbox in the same migration: primary key `(consumer_group, event_id)`. `SubscriptionRenewalEligibleEventConsumer.onRenewalEligible` claims the inbox, then calls `draftRenewal`, in one transaction. A failed claim returns before a second draft. File: `SubscriptionRenewalEligibleEventConsumer.java`. | The inbox row is the idempotency claim for that consumer. It is not a pricing decision. Under option A, a later price-snapshot insert for that draft would sit in the same transaction, after the claim. |

`verified by reading code`

### `pricing_audit_log.rules_fired` and `waterfall_trace` (DRG-612 claim)

[DRG-612](https://linear.app/drgamtd-workspace/issue/DRG-612/story-commercial-decision-audit-mechanism-for-pricing-rule-outcomes) says those columns are unused. `verified by reading the issue`. Checked against the tree at `e3ee211b` (these files are unchanged on this branch). `verified by reading code`

| Fact | File |
|---|---|
| Both columns exist, nullable `TEXT`. No later pricing migration alters the table or writes either column. The only SQL mention is the create. | `backend/services/pricing-service/src/main/resources/db/migration/V1__create_pricing_schema.sql` (`waterfall_trace`, `rules_fired`) |
| The entity maps `waterfallTrace` and `rulesFired`. `@PrePersist` sets `uuid` and `executedAt` only. | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingAuditLog.java` |
| The only production `PricingAuditLog.builder()` is `persistAuditLog`. It sets `quoteExternalId`, `customerExternalId`, `ruleSetId`, `inputSnapshot`, `outputSnapshot`, and `executionTimeMs`. It does not set `rulesFired`, `waterfallTrace`, or `executedBy`. `save` follows. `execute` calls `executeInternal(context, true)`, so that insert runs on execute. `simulate` calls `executeInternal(context, false)`, so simulate does not insert. | `PricingExecutionService.java` (`execute`, `simulate`, `executeInternal`, `persistAuditLog`) |
| `ruleResult.getRulesFired()` is an `int` on `RuleExecutionResult`. Execute logs that count and does not copy it onto the audit entity. `DroolsRulesEngine` sets that int from `fireAllRules`. | `PricingExecutionService.java` (the `log.info` after `droolsRulesEngine.execute`); `backend/libs/rules-engine/src/main/java/com/opencrm/rules/drools/DroolsRulesEngine.java`; `backend/libs/rules-engine/src/main/java/com/opencrm/rules/engine/RuleExecutionResult.java` |
| `RuleService.getImpact` counts audit rows by `rule_set_id`. It does not read `rules_fired` or `waterfall_trace`. | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/rule/RuleService.java`; `PricingAuditLogRepository.java` |
| A unit test of execute asserts the saved entity's `getRulesFired()` and `getWaterfallTrace()` are null. That test was read, not run. | `backend/services/pricing-service/src/test/java/com/opencrm/pricing/execution/PricingDrulesGoldenFixtureTest.java` |
| The shared pricing model declares optional `waterfallTrace` and `rulesFired`. No other frontend file reads them. That type does not write the table. | `frontend/libs/shared/models/src/lib/pricing.model.ts` |

A search of `*.java` and `*.sql` for `setRulesFired`, `setWaterfallTrace`, `.rulesFired(`, and `.waterfallTrace(` found no assignment of the audit-log fields. The `.rulesFired(` hits are `RuleExecutionResult` in the rules engine and its test. `verified by reading code`

So the DRG-612 claim holds for writers: the columns exist, and no production code assigns them. `input_snapshot` and `output_snapshot` are assigned, by the builder above.

What that does not mean: a continuation quote that is calculated can still get a `pricing_audit_log` row. `QuoteContinuationPolicy.plan` does not call pricing-service. `QuoteContinuationService.preview`, `author`, and `draftRenewal` do not call `PricingServiceClient`. `QuoteService.calculate` does. After `validateContinuation`, `priceInitialInterval` posts `/api/v1/pricing/execute` when the plan has an initial proration, and the main path posts it again through `fetchPricingData` unless the plan has no periods and returns early. `PricingServiceClient.executePricing` is that POST. Execute persists the audit row described above. `verifyContinuationPeriods` then checks the priced net unit against the plan and throws `SUBSCRIPTION_CONTINUATION_RATE_CHANGED` when it differs. The row's `input_snapshot` is the `PricingContext` sent on that POST. The continuation policy id, policy version, mode rung, and the schedule view at `observedRevision` are not columns on `pricing_audit_log`. Files: `QuoteContinuationPolicy.java`, `QuoteContinuationService.java`, `QuoteService.java` (`calculate`, `priceInitialInterval`, `fetchPricingData`, `verifyContinuationPeriods`), `PricingServiceClient.java`. `verified by reading code`

No Java type named `PolicyDecision` exists under `backend/` on this commit. `verified by reading code`

### Distinct gap

The missing record is one immutable snapshot per priced continuation line:

- policy id and version, intent, and mode
- the inputs that produced the price, including the schedule view at the observed revision
- outputs, explanation codes, rules fired, who decided
- a digest of that body

That body is not an `amendment_document_events` row, not a `processed_events` claim, not the `SYSTEM_RENEWAL_DRAFT` row, and not `pricing_audit_log.uuid`. [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609/story-pricing-service-support-for-addupdateremove-lines-with)'s design note on `main` already returns a new id on a sketched HTTP body and assigns the insert to [DRG-612](https://linear.app/drgamtd-workspace/issue/DRG-612/story-commercial-decision-audit-mechanism-for-pricing-rule-outcomes). `verified by reading code` for that note. This note defines the row those ids would point at. It does not accept the DRG-609 route sketch.

DRG-612, as read, says the story must reconcile `commercial_decision_audit`, this PolicyDecision record, and the `PricingAuditLog` columns so there is one audit mechanism. `verified by reading the issue`. The column check above is the code result that claim depends on. The recommendation below is one target table. The owner chooses the home. This note does not design a second writer or a second chain.

## Where the record would live

This section is **option A, a recommendation**. The alternatives are in Options. The owner chooses. Nothing in this section is a decision.

**Module, if the owner picks A:** quote-service, PostgreSQL schema `quote`. ADR-008 (Accepted) gives each service its own schema and forbids cross-schema joins. pricing-service cannot insert into `quote`. CAM cannot insert into `quote`. Under option A, quote-service persists the row.

**Who computes the numbers** is separate from who inserts the row:

- Renewal unit price, once [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy) implements the Accepted uplift record: pricing-service merges the rungs and returns the body. quote-service stores that body and does not recompute it. The uplift record says pricing-service stays stateless and does not read another service's database.
- Amendment day-stub, under the Accepted proration record D-02: the running method remains `QuoteContinuationPolicy.plan` in quote-service. The same table can hold that row. See Owner decision 2 for the trace id.

**V1_13 is a would-be number.** It is the next integer after `V1_12__continuation_operation.sql` on this commit. This note does not reserve it and does not create a file. [DRG-611](https://linear.app/drgamtd-workspace/issue/DRG-611/story-durable-dedupe-marker-for-the-renewal-eligible-event) (durable dedupe marker for the renewal-eligible event) is Backlog and has not been started. Its text asks for a migration and does not name a Flyway version. That backstop may also want the next quote-service number. **Numbering is settled when the migration PR exists**, whichever story opens it. `verified by reading the issue` for DRG-611. `verified by reading code` for `V1_12` being the latest quote-service migration on this commit.

Versions read on `e3ee211b`:

| Schema | Latest file on this commit | Next integer | Who might use it |
|---|---|---|---|
| `quote` | `V1_12__continuation_operation.sql` | V1_13, would-be only | Option A or B, if a later migration PR chooses that number. DRG-611 may want the same integer for its marker. Neither story has a migration PR. |
| `pricing` | `V8__guard_managed_seed_optional_inputs.sql` | V9 is the next integer | [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542/amendment-proration-rule-conflicts-with-continuation-formula), open PR [#638](https://github.com/drgaciw/open-crm-cpq/pull/638), file `V9__disable_unedited_system_amendment_proration.sql`. `verified by reading the pull request` (file list only; the SQL text was not opened). #638 is open and not merged. Option D would be a later pricing version, settled by the migration PR that creates it. This note creates neither V9 nor V10. |
| `contract` | `V1_8__contract_header_backfill.sql` on this commit; the event table is `V1_7__contract_documents.sql` | none in this recommendation | S2 chain and the header backfill stay untouched under every option except E, which would still be a later CAM migration PR if the owner picks it |

### Table sketch

Sketch only. Not a Flyway migration. Not applied.

```text
-- SKETCH for option A only. Not a migration. Would-be name, not a reserved version:
-- backend/services/quote-service/src/main/resources/db/migration/V1_13__policy_decisions.sql
-- The migration PR that exists first settles whether this file is V1_13.
-- Schema: quote. Append-only. One row per priced continuation line per evaluation.
-- A reprice inserts a new row. No UPDATE, DELETE, or TRUNCATE of an existing row.
-- This sketch does not alter commercial_decision_audit or processed_events.
-- Option B would alter commercial_decision_audit instead of creating this table.

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

Append-only enforcement option A would add, in that same would-be migration: a quote-schema trigger that rejects UPDATE, DELETE, and TRUNCATE, modeled on `reject_amendment_document_event_mutation` in CAM `V1_7`. A new function in the quote schema. Not a call into the CAM function. `commercial_decision_audit`'s migration comment says append-only, and that file creates no such trigger. `verified by reading code` for both migrations. Mid-term A1 is why the recommendation wants the trigger on a PolicyDecision row. A1 is a spec requirement, not a constraint on `commercial_decision_audit` today.

`decision_digest` canonical version 1 covers: `canonical_version`, `policy_id`, `policy_version`, `mode`, `mode_rung`, `decision_kind`, `observed_revision`, `inputs_digest`, and the canonical `outputs_json`. It excludes `id`, `created_at`, and `pricing_trace_id` so a trace assigned after the body is hashed does not rewrite the digest. That exclusion is a proposal in this note. `AmendmentEventHasher` excludes `recorded_at` for a different reason (the database overwrites it). The two canonical forms stay different. This sketch does not call `AmendmentEventHasher`. A shared helper in `backend/libs/common` would be new code and is out of this note.

`inputs_json` for every row includes the `SubscriptionScheduleView` facts `plan` actually read: subscription number, customer id, currency, status, start, end, commercial revision, and the selected line's accepted-line uuid, product external id, provenance, and each non-retired rate period's start, end, quantity, net unit price, and net monthly. Spike §5 also names an evidence store addressed by digest. This sketch stores the JSON in the row and the digest beside it. A separate evidence store is [DRG-311](https://linear.app/drgamtd-workspace/issue/DRG-311) / [DRG-312](https://linear.app/drgamtd-workspace/issue/DRG-312) and is not designed here.

Keys the JSON gains when the producer is the Accepted uplift merge (DRG-567). Names follow that record's D-07:

`basis`, `basisAmount`, `percentPoints` or `upliftAmount`, `exitNet`, `priorList`, `listUnit`, `priceAsOf` (`ActionDate`, `EffectiveDate`, or `LineStart`), `priceAsOfDate`, `pricebookVersion`, each cap and floor field with the rung that set it, `capApplied`, `floorApplied`, `unitPrice` at scale 4, `extendedAmount` at scale 2, `clauseId`, `rulesRetentionMarker`, `rulesRungSuppliedPercent`, explanation code `RENEWAL_UPLIFT_POLICY` when that rung supplied the percent, and on failure one of `UNRESOLVED_BASIS`, `UNRESOLVED_UPLIFT`, `UNRESOLVED_RENEWAL_MODE`, `PRICE_PROTECTION_CONFLICT`. Churn score may sit in `inputs_json` when the rules rung ran. The uplift record already says the customer-facing document does not gain a churn field. Rounding mode for the single unit is half-up at scale 4 (D-05). Per-step escalator rounding stays DRG-533.

Keys the JSON gains for the amendment stub (Accepted proration D-02): `mode` `RetainContracted`, the partial-period `ProrationSnapshot` fields `plan` already builds (start, end, billing-period bounds, quantity, net unit price, net monthly, period amount), and the following full-month periods. The recommendation stores `rules_fired` as `[]` on this path, because `plan` does not fire a rule. The classpath `amendment-proration.drl` rule is not this snapshot.

Preview (`QuoteContinuationService.preview`) does not insert. That matches `simulate`, which does not insert a `PricingAuditLog`. `verified by reading code` for both methods' lack of an insert.

## Options

The owner chooses one home. **Option A is the recommendation, not a decision.** The reasons under the other options are why the recommendation sets them aside. They are here so the owner can pick one of them anyway.

| Option | What the owner would be choosing | Why the recommendation sets it aside |
|---|---|---|
| **A. New `quote.policy_decisions` (recommended)** | One new append-only row per priced line, in the quote schema. `commercial_decision_audit` stays the draft-open row the V1_11 comment describes. The S2 chain stays the document lifecycle. `pricing_audit_log` stays the execute log, with `rules_fired` and `waterfall_trace` still unassigned. DRG-612, once sequenced, would create this one table and would not also create a second price-snapshot table. The Flyway number is would-be and is settled when that migration PR exists. | — |
| **B. Extend `commercial_decision_audit`** | Add the per-line columns (policy version, trace id, digests, line id, the D-07 JSON) to the table that already has `inputs_json`, `outputs_json`, `policy_id`, and `mode`. One quote-service audit table, which is a direct reading of DRG-612's "one mechanism." | The only insert is one `SYSTEM_RENEWAL_DRAFT` row per draft that `draftRenewal` opens. `author` does not insert. An amendment, an extension, and a seller-opened renewal have no row. The V1_11 comment limits this table to opening the draft and assigns the general trail to DRG-564. Widening it mixes a quote-level draft-open fact with a per-line price snapshot. A later migration can still add a nullable link from the draft row to a line decision if the owner picks A. |
| **C. Populate `pricing_audit_log.rules_fired` and `waterfall_trace`** | Keep the existing execute row as the decision. `persistAuditLog` would start assigning those two columns, and `input_snapshot` / `output_snapshot` (already assigned) would be treated as the price body. No new table. | The row grain is one execute, not one continuation line. Simulate does not insert. `plan`, `preview`, `author`, and `draftRenewal` do not call execute, so those paths still write nothing here. `QuoteService.calculate` does call execute, and that row's snapshot is the `PricingContext` of lines that already carry continuation prices. `verifyContinuationPeriods` only checks the net unit. The table has no policy id, policy version, mode, rung, or observed revision. The rules-engine `rulesFired` value is an int that execute logs and drops. The audit column is `TEXT`. `persistAuditLog` does not assign it, so the row that method inserts leaves it null. Filling it audits the waterfall execute. It does not, by itself, record the continuation decision DRG-564 names. ADR-008 means quote-service cannot update this table. The writer stays pricing-service. |
| **D. New table in the `pricing` schema** | pricing-service owns the canonical row and returns its id. Matches "pricing-service produces the decision" in the renewal policy proposal (that file is still a proposal). | V9 is the DRG-542 file on open PR #638. A new pricing table's version is whatever the migration PR gets, not a number this note assigns. `plan` does not call pricing-service, so the table stays empty for preview, author, and draft renewal until those paths move. ADR-008 blocks quote-service from inserting into `pricing`. A second store beside `commercial_decision_audit` is the split DRG-612 says to avoid, unless the owner chooses this table as the single mechanism and leaves the draft-open row as a quote-level fact only. |
| **E. Store the price body in `amendment_document_events`** | One chain for lifecycle and price. `payload_digest` is already 64 lowercase hex. | The hasher's canonical form is the event row. The writer requires an `AmendmentDocument` and throws when the id is unknown. `draftRenewal` creates a quote and no document, so a renewal draft has nowhere to append. Putting the schedule view in the event row gives the chain a second job. The line columns `policy_decision_id` and `pricing_trace_id` are already the pin. The event can cite a decision id. That citation works under A, B, C, or D. |

## Recommendation

Option A. quote-service would insert `policy_decisions`. The computing component returns the body. quote-service would not recompute a body that pricing-service already returned.

That recommendation is not the owner's choice. Picking A, B, C, D, or E still **authorizes no code and no migration**. DRG-612 remains the story that would create the migration, and only after a CPQ Lead sequence. If the owner picks A, the recommendation is that DRG-612 does not also add a `pricing` policy table, does not start assigning `rules_fired` and `waterfall_trace` as the continuation snapshot, and does not change `AmendmentEventHasher`. A nullable link from `commercial_decision_audit` to the line decision can be a later column. It is not in the sketch.

If the owner picks C, assigning `rules_fired` and `waterfall_trace` is the continuation snapshot, and the reasons in the table above are what that choice accepts.

The current renewal branch substitutes a null `upliftPercent` with zero and rounds the unit with `money` at scale 2 (`QuoteContinuationPolicy`, `movePointLeft(2)`, `setScale(2, HALF_UP)`). The Accepted uplift record rejects that silent zero (`UNRESOLVED_UPLIFT`) and stores the renewal unit at scale 4. The recommendation for a row produced by today's branch is `policy_id` `QuoteContinuationPolicy`, `policy_version` `continuation-policy-scale2`, `mode` `CALLER_UPLIFT`, and `mode_rung` `caller`, recording the percent actually applied, including zero. That row would not use mode `UpliftPct` and would not name an account rung. The token is how a reader would tell a pre-policy draft from an Accepted-uplift decision. Owner decision 3 is that choice.

## What DRG-567 needs

DRG-567 implements basis, cap, floor, price-as-of, and the five-rung merge from the Accepted uplift record. That record's consequence says DRG-564's per-line snapshot gains the D-07 fields, and it leaves the audit table to DRG-564. It names the story. It does not name this file.

**DRG-567's audit fields point at this note only after the owner accepts it.** Until acceptance, DRG-567 does not implement against this file. Accepting this note authorizes no code and no migration, including no DRG-567 change.

After acceptance, the fields a DRG-567 implementation has to be able to store are the D-07 keys listed above, plus `mode_rung` for the rung that set the mode (D-04: contract clause, then line, then agreement, then account, then the rules default). Which table holds them is the owner's home choice. A price returned with nowhere to store those fields still fails the snapshot the uplift record names. This note does not implement the merge and does not change `QuoteContinuationPolicy`. The uplift record's own "does not authorize code" line still stands.

## What DRG-549 needs

DRG-549 owns the mode set `Same`, `List`, `UpliftPct`, `UpliftAmt`, `Override`, `Renegotiate`. The Accepted uplift record D-04 says DRG-549's resolver uses that record's order, and that a missing mode is `UNRESOLVED_RENEWAL_MODE` and a missing percent is `UNRESOLVED_UPLIFT`.

**DRG-549's audit fields point at this note only after the owner accepts it.** Until acceptance, DRG-549 does not implement against this file. Accepting this note authorizes no code and no migration.

After acceptance, the snapshot stores the resolved mode, `mode_rung`, and those codes when the merge fails. It does not choose the catalog. DRG-549's issue text still says the walk is account, then agreement, then line. `verified by reading the issue`. The Accepted uplift record D-04 says clause, then line, then agreement, then account, then rules. The recommended snapshot stores D-04. This note does not reopen precedence and it does not edit the issue.

The renewal policy proposal (`docs/renewals-amendments/prereq/renewal-policy-spec.md`) is a proposal, not an accepted ADR. Its RN3 and AC-RN-05 ask each renewed line to carry policy id, policy version, trace id, basis, cap applied, price-as-of, rounding, and precedence. AC-RN-25 says the pricing input snapshot omits prior-term usage unless the resolved policy declares a usage input. This row is the snapshot those lines name. Accepting this note does not accept that proposal.

## Owner decision needed

1. **Home.** Choose A, B, C, D, or E. Option A is the recommendation, not a decision this note has made. Choosing a home, and accepting this note, authorizes no code and no migration.
2. **Trace id while quote-service still computes.** The Accepted proration record keeps the amendment stub in `QuoteContinuationPolicy`. Mid-term V8 says every priced line carries a pricing-service trace id and that no line is priced inside quote-service. Those two statements disagree for the stub. Recommendation: the row may be inserted with `pricing_trace_id` null and `policy_id` `QuoteContinuationPolicy` until a pricing-service evaluation returns a trace. The amendment document line columns stay empty until that trace exists, so V8's "not null when priced" on the document line is unchanged. The alternative is to insert no amendment row until that move, which leaves the path DRG-564 describes without a snapshot.
3. **Current renewal zero.** The recommendation uses `CALLER_UPLIFT` / `continuation-policy-scale2` for a row produced by today's scale-2 branch, so that row is distinct from an Accepted-uplift decision. The alternative is to insert no renewal row until DRG-567's merge is live.
4. **Inline JSON.** The recommendation stores the schedule view in `inputs_json` with `inputs_digest` beside it. The alternative is digest-only plus an evidence store. That store is not designed here. This pass did not measure the byte size of a `SubscriptionScheduleView`.
5. **Numbering.** V1_13 is a would-be number for a quote-service migration under option A or B. DRG-611's backstop is not launched (Backlog, not started) and may also want that next quote-service version. Its issue does not name V1_13. The number is settled when a migration PR exists. This note does not open that PR. `verified by reading the issue` for DRG-611.
6. **Sequencing.** Accepting this note names a recommended shape. It does not sequence DRG-612, DRG-567, or DRG-549. DRG-567 and DRG-549 point their audit fields here only after acceptance. DRG-612's issue says not to start without a CPQ Lead sequence. `verified by reading the issue`

## Out of scope

- Creating any migration. V1_13 is a would-be number. DRG-611 may need the next quote-service version. The migration PR settles the number.
- Editing `AmendmentEventWriter`, `AmendmentEventHasher`, `AmendmentChainVerifier`, or `V1_7`.
- Editing `commercial_decision_audit`, `processed_events`, or `PricingAuditLog`.
- Editing the Accepted uplift record, the Accepted proration record, ADR-023, or `docs/adr/README.md`.
- Treating ADR-017 or ADR-024 through ADR-027 as accepted.
- Accepting the renewal policy proposal, the DRG-609 route sketch, or the OpenAPI draft.
- Price-book retrieval (DRG-539), escalator compounding (DRG-533), and the execute-path waterfall (DRG-311 for pricing-service only, as DRG-564's issue states).

## Scope against DoD v0.2

The pull request scores [CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338). This section is the scope of the design. The score is on the pull request.

This note defines the record and a recommended table. Accepting it authorizes no code and no migration. It writes no row, publishes no event, and changes no rule. DRG-567 and DRG-549 point their audit fields here only after acceptance. Commercial-decision persistence, once sequenced, is DRG-612 against the home the owner chooses.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-564, DRG-567, DRG-549, DRG-612, DRG-542, DRG-611 | Linear, this pass |
| Uplift record, Accepted, D-04, D-05, D-07 | `docs/renewals-amendments/prereq/adr-renewal-uplift-basis-and-precedence.md` |
| Proration record, Accepted, D-02 | `docs/renewals-amendments/prereq/adr-amendment-proration-formula.md` |
| ADR-008, ADR-017, ADR-024..027 catalog | `docs/adr/ADR-008-postgres-schema-per-service.md`, `docs/adr/README.md` |
| S2 writer, hasher, verifier, outbox | CAM classes named above; migration `V1_7__contract_documents.sql` |
| Draft-open audit and inbox | `V1_11__processed_events_inbox.sql`, `QuoteContinuationService.recordDecision`, `SubscriptionRenewalEligibleEventConsumer` |
| Continuation formula | `QuoteContinuationPolicy.plan` |
| `rules_fired` / `waterfall_trace` writers | `V1__create_pricing_schema.sql`; `PricingAuditLog.java`; `PricingExecutionService.persistAuditLog`; `RuleExecutionResult.java`; `DroolsRulesEngine.java`; `RuleService.getImpact`; `PricingDrulesGoldenFixtureTest`; `frontend/libs/shared/models/src/lib/pricing.model.ts` |
| Calculate calls execute | `QuoteService.calculate`, `priceInitialInterval`, `fetchPricingData`, `verifyContinuationPeriods`; `PricingServiceClient.executePricing` |
| Line pin columns | `docs/renewals-amendments/amendment-entity-field-schema.md` |
| Spike §5 | `docs/renewals-amendments/amendment-document-design-spike.md` |
| V8, A1, A5 | `docs/renewals-amendments/mid-term-amendment-policy-spec.md` |
| DRG-609 hand-off of the insert | `docs/renewals-amendments/prereq/story-drg-609-pricing-service-continuation-design.md` |
| Renewal proposal RN3, AC-RN-25 | `docs/renewals-amendments/prereq/renewal-policy-spec.md` (proposal) |
| Pricing V9 reservation | GitHub PR #638 file list |
| DoD v0.2 | Notion page fetched this pass. `verification.state` was `unverified`. Title and body both say approved. |

## Unverified in this pass

- Any runtime result: inserts, hash values, trigger behaviour, Kafka redelivery. The golden-fixture assertions were read and not run.
- The SQL body of `V9__disable_unedited_system_amendment_proration.sql` on PR #638. Only the path was read. This note does not describe that file's statements.
- Byte size of a frozen `SubscriptionScheduleView` in `inputs_json`.
- GitNexus blast radius. The index was absent. Impact is UNKNOWN.
- Notion `verification.state` for the DoD page.
- Whether a GitHub pull request for DRG-611 exists outside the Linear attachment list read in this pass. The issue itself is Backlog, not started, and names no Flyway version.
