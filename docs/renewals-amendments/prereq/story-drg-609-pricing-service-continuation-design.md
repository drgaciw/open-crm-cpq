# DRG-609 — pricing-service continuation design

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Date: 2026-10-16.*

**Story:** [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609/story-pricing-service-support-for-addupdateremove-lines-with). Design note for how `pricing-service` could price Add, Update, and Remove continuation lines and return a PolicyDecision id, a policy version, and a trace id.

**Status of this note:** a design proposal. It is not an ADR. No code starts until the owner accepts this design. Until that acceptance, this note does not start a Flyway migration, a Drools edit, a flag, a route, or a merge. [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583/s5-quote-service-lifecyclechange-amend-with-pricing-service-decisions) (S5) stays blocked on the capability this note describes.

**ADR status used below.** [adr-amendment-proration-formula.md](https://github.com/drgaciw/open-crm-cpq/blob/b5eef8ebba8c87fdf76ee2f6a01b2c2c6fed2242/docs/renewals-amendments/prereq/adr-amendment-proration-formula.md) opens with **Status: Proposed. Not accepted.** [adr-renewal-uplift-basis-and-precedence.md](https://github.com/drgaciw/open-crm-cpq/blob/b5eef8ebba8c87fdf76ee2f6a01b2c2c6fed2242/docs/renewals-amendments/prereq/adr-renewal-uplift-basis-and-precedence.md) opens with **Status: Proposed on 2026-10-16. Not accepted.** Proration and uplift are **Proposed until [#623](https://github.com/drgaciw/open-crm-cpq/pull/623) merges, then Accepted**. #623 is the ADR status-change pull request for [#605](https://github.com/drgaciw/open-crm-cpq/pull/605), [#609](https://github.com/drgaciw/open-crm-cpq/pull/609), and [#606](https://github.com/drgaciw/open-crm-cpq/pull/606). It was read in this pass: open, draft, base `main`, not merged. An ADR counts as accepted only after that status-change pull request merges. The checklist this pull request is scored against is [CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338), approved by the owner on 2026-10-16. `verified by reading the issue` for that page, fetched in this pass. `verified by reading the pull request` for #623.

**Integration update (#622).** The evidence below is pinned to `dd0f1a8`. #622 adds a dedicated classpath renewal-default evaluation at salience 10, once per quote, using authoritative customer churn and sanitized result metadata. It records a fraction without pricing a continuation line or returning PolicyDecision identity. The older statement that the renewal classpath rule never executes is baseline evidence, not current behavior after #622. ADR links use the accepted locations introduced by #623; their previous filenames had a DRAFT prefix. This design remains a proposal and does not authorize implementation.

## Verification

**Drools was NOT re-executed in this pass.** Nothing was run. This pass did not start Maven, a Drools session, `fireAllRules`, Testcontainers, or an HTTP call to pricing-service.

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Checked by reading source, SQL, or docs in the tree at `origin/main` (`dd0f1a8`). Not executed. |
| `verified by reading the issue` | Read from Linear in this pass. Not checked against a pull request diff. |
| `unverified` | Not established by a file or issue read in this pass. |

**Claims marked as executed: none.**

## Question

How can `pricing-service` price a continuation line whose action is Add, Update, or Remove, and return a PolicyDecision id, a policy version, and a trace id, given that today's execute path cannot?

The gap is recorded in [oq9-pricing-continuation-capability.md](oq9-pricing-continuation-capability.md). This note designs the capability. It does not close the gap.

## Current state

`verified by reading code` unless a row says otherwise.

### Execute prices a list line and returns no decision identity

`PricingExecutionController` maps `POST /api/v1/pricing/execute`, `POST /api/v1/pricing/simulate`, `POST /api/v1/pricing/simulate/bulk`, and `GET /api/v1/pricing/audit/{quoteExternalId}`. Execute and simulate both return `ApiResponse.success(...)`. File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionController.java`.

`ApiResponse.success(T data)` and `ApiResponse.success(T data, String message)` set `success`, `data`, and optionally `message`. They do not set `traceId`. The factory that sets `traceId` is `ApiResponse.error(String message, String errorCode, String traceId)`. File: `backend/libs/common/src/main/java/com/opencrm/common/dto/ApiResponse.java`. Where the error factory's trace value is minted was not opened (`GlobalExceptionHandler` was not read). `unverified` for the MDC key.

`PricingContext` fields read from the entity: `customerExternalId`, `quoteExternalId`, `lineItems`, `region`, `currency`, `durationInMonths`, `subscriptionType`, `metadata`. The field comment on `subscriptionType` says `NEW`, `AMENDMENT`, `RENEWAL`. File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingContext.java`.

`PricingLineRequest` fields read from the entity: `productExternalId`, `lineItemType`, `quantity`, `listPriceOverride`, `metadata`, `schedule`, `billingFrequency`. No action, no Delta versus Absolute, no source accepted-line id, no effective date, no contracted rate, no proration method. File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingLineRequest.java`.

`PricingResult` fields read from the entity: `success`, `lineResults`, `waterfall`, `financialMetrics`, `explanationCodes`, `approvalState`, `approvalTier`, `executionTimeMs`, `errorMessage`. File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingResult.java`.

`PricingLineResult` fields read from the entity: `productExternalId`, `lineItemType`, `quantity`, `listPrice`, `unitPrice`, `discountRate`, `discountAmount`, `surchargeAmount`, `finalPrice`, `explanationCodes`, `metadata`, `pricedPeriods`. File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingLineResult.java`.

No Java type named `PolicyDecision` is declared under `backend/` on this commit (`git grep` for `class PolicyDecision` and `record PolicyDecision` returned no matches). `verified by reading code`

### What `executeInternal` does with a line

File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionService.java`.

`execute` calls `executeInternal(context, true)`. `simulate` calls `executeInternal(context, false)`.

Active rule-set selection is `findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(ACTIVE, LocalDate.now())`, then `activeRuleSets.get(0)` when the list is not empty. The date is the server's today. The call as written has no order argument. Whether any database holds more than one ACTIVE set is `unverified`.

Each line starts as list price times quantity. List price is `listPriceOverride` when set, otherwise the price-book batch entry, otherwise `PricingProduct.listPrice`, otherwise zero. A ramp schedule, when present and when ramp pricing is enabled, replaces the line unit and final price from the first priced period.

When active rules exist, the method builds a `RuleExecutionContext` with `ruleSetId` set to `String.valueOf(ruleSet.getId())` and `engineType` DROOLS. It does not set a rule-set version on that builder. Facts added: the pricing `Customer`, the `PricingContext`, and the `PricingLineResult` list. `RuleExecutionResult.getRulesFired()` is logged. The count is not copied onto `PricingResult`.

`Customer`, `PricingContext`, and `PricingLineResult` are not `QuoteFact`. Declarations read in this pass: `Customer extends BaseEntity`, `public class PricingContext`, `public class PricingLineResult`, `public class QuoteFact`. Files: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/customer/Customer.java`, the waterfall types above, and `backend/libs/rules-engine/src/main/java/com/opencrm/rules/model/QuoteFact.java`.

The success `PricingResult` is built with `success` true, the line list, an empty `explanationCodes` list, and `approvalState` `"APPROVED"`. `executionTimeMs` is a duration. It is not an id.

### The two classpath rules do not price a change line

`backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl`, rule `Amendment Proration`, salience 540. The `when` clause matches `QuoteFact` whose `dealType` string equals `AMENDMENT`. The consequence reads `remainingTermDays`, `fullTermDays`, and `amendmentDeltaAmount`, divides `delta * remainingDays / fullTermDays` at scale 4, `HALF_UP`, and writes `prorationAdjustment` and `coTermDate` on that quote fact. The rule text has no Add, Update, or Remove condition, no quantity, and no write to a line unit price or final price. A `fullTermDays` of 0 is a divisor with no `> 0` check in this file. The throw was not observed.

`backend/services/pricing-service/src/main/resources/rules/renewal-uplift.drl`, rule `Renewal Uplift and Retention Offer`, salience 530. The `when` clause matches `QuoteFact` with a customer, and `dealType` equal to `RENEWAL`. The consequence sets `renewalUpliftPct` to `0.08`, or to `0.03` when `churnScore` is greater than 70, and in that churn branch sets `retentionOffer` to `ENABLE_12M_PRICE_PROTECTION`. It sets `explanationCode` to `RENEWAL_UPLIFT_POLICY`. It does not read an action, a quantity, or a contracted rate, and it does not write a line price.

Execute loads rule text from `rule.getContent()` on the active rows of the selected rule set. It does not load these classpath files on that path. `verified by reading code`

`V8__guard_managed_seed_optional_inputs.sql` rewrites the `when` clause of system-owned `rule.uuid = 'rule-007'` on `rule_set.uuid = 'rs-default-001'`, and only when the stored content matches a fixed md5. The replacement adds non-null checks for `remainingTermDays`, `fullTermDays`, and `amendmentDeltaAmount`, and `fullTermDays > 0`. The classpath file read above does not contain those guards. File: `backend/services/pricing-service/src/main/resources/db/migration/V8__guard_managed_seed_optional_inputs.sql`. `V2__seed_default_rules.sql` was not opened in this pass. What V2 inserted, beyond the string V8 searches for, is `unverified` here. OQ9 records that V2 inserts the classpath body as `rule-007`. That statement is from the OQ9 note, which this pass did read.

Because the inserted facts are not `QuoteFact`, these two rules' `when` clauses do not match the execute fact list. That match result was not observed in a session. `verified by reading code`

### Audit rows that exist

`PricingAuditLog` columns read from the entity: surrogate `id`, `uuid`, `quoteExternalId`, `customerExternalId`, `ruleSetId`, `inputSnapshot`, `outputSnapshot`, `waterfallTrace`, `rulesFired`, `executionTimeMs`, `executedAt`, `executedBy`. `@PrePersist` assigns `UUID.randomUUID()` to `uuid` when null, and `Instant.now()` to `executedAt` when null. File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingAuditLog.java`.

`persistAuditLog` runs only when `persistAudit` is true (execute, not simulate). The builder sets quote id, customer id, `ruleSetId` from `ruleSet.getId()` when a set was selected, JSON snapshots of the context and the result, and `executionTimeMs`. It does not set `waterfallTrace`, `rulesFired`, or `executedBy`. `save` is not read back. The new `uuid` is not copied onto `PricingResult`. `JsonProcessingException` is logged as `AUDIT_INTEGRITY_FAILURE` and the method returns. Any other exception is logged the same way and then thrown as `BusinessException` with code `AUDIT_PERSISTENCE_FAILED`. Neither branch puts an id on the result. Those branches were not executed. `verified by reading code`

`RuleSet.ruleSetVersion` exists, integer, default 1. File: `backend/services/pricing-service/src/main/java/com/opencrm/pricing/ruleset/RuleSet.java`. `executeInternal` does not copy `ruleSetVersion` onto `PricingResult`. The Java field that holds `rule_set.uuid` was not opened. V8 predicates on `rule_set.uuid`. `unverified` which Java property maps to that column.

quote-service on this commit has `commercial_decision_audit`. The migration comment says the table records only the decision to open a renewal draft, and that DRG-564 owns the general continuation pricing-decision trail. File: `backend/services/quote-service/src/main/resources/db/migration/V1_11__processed_events_inbox.sql`. The insert in `QuoteContinuationService.recordDecision` writes `decision_kind` `SYSTEM_RENEWAL_DRAFT` and `policy_id` `QuoteContinuationPolicy`. It does not write a pricing-service decision id, a policy version, or a trace id. File: `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationService.java`. `verified by reading code`

### What S5 already requires of a priced line

From docs read in this pass. `verified by reading code`

| Need | Where it was read |
|---|---|
| Action codes in the first slice are Add, Update (quantity, Delta or Absolute), and Remove. Swap, Upgrade, and Downgrade are out of slice. | `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md`, slice definition |
| Every priced line carries a PolicyDecision id, policy id and version, and the pricing-service trace id. Pricing does not run inside quote-service or CAM. | Same file, FR-14. `docs/renewals-amendments/mid-term-amendment-policy-spec.md`, V8 |
| AC-S5-2: a ChangeLine with AmendmentPolicy RetainContracted and proration Daily, when priced, has those ids plus pinned product, pricebook, and configuration data (V8, V9). | First-slice file, S5 acceptance |
| Document line columns `policy_id`, `policy_version`, `policy_decision_id`, `pricing_trace_id` are text, not null when the line is priced. | `docs/renewals-amendments/amendment-entity-field-schema.md` |
| A5: each line records which policy fired, why, an inputs snapshot digest, rules fired, outputs, and who decided. | Mid-term spec, A5. Storage of that trail is the gap DRG-612 names. |

`QuoteContinuationRequest` on this commit has `Operation` values `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`, and `@Positive` on `quantity`. File: `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java`. A remove-to-zero quantity cannot satisfy `@Positive`. `QuoteContinuationPolicy` and `QuoteService` were not opened in this pass. OQ9's description of the quote-service rate calculation is a claim in that note, not a re-read of those classes here. `unverified` against those two classes in this pass.

## Options

Each option is a proposal. None is implemented.

### Option A — Insert `QuoteFact` on execute and reuse the two DRL files

Add, Update, and Remove would be folded into `dealType`, `amendmentDeltaAmount`, and the renewal percent fields, and `executeInternal` would insert a `QuoteFact` so `amendment-proration.drl` and `renewal-uplift.drl` can match.

Trade-offs:

- The Drools port and the seeded rule rows stay the execution path.
- The amendment rule prices no line. It writes one quote-level decimal. Its `when` clause does not mention action, so Add, Update, and Remove would share one consequence.
- The uplift rule writes a fraction (`0.08` or `0.03`) and an optional retention string. It does not write a unit price.
- Inserting `QuoteFact` changes the fact set of every execute that has active rules, including quotes that are not continuations.
- Execute runs database `rule.content`, not the classpath file. The classpath amendment rule and the V8 rewrite of `rule-007` already differ. Driving line prices from "the DRL" would still have to name which body.
- The full-term ratio in the classpath rule and the continuation day-stub described in the proration draft are different procedures. That draft is Proposed until #623 merges, then Accepted. Using the classpath ratio as the line price before that merge would treat the draft as accepted.
- Decision identity is still missing after the rule fires. `rulesFired` is logged and dropped, and `PricingResult` has no policy fields.

### Option B — A separate continuation evaluation that returns decision identity

Add a new pricing operation whose request is continuation lines and whose success body carries `policyDecisionId`, `policyId`, `policyVersion`, and `traceId`. Leave `POST /api/v1/pricing/execute` and `POST /api/v1/pricing/simulate` on their current fact list and current success body. Leave both DRL files unchanged.

Trade-offs:

- Current execute clients keep the contract that was read.
- The line model can name Add, Update, and Remove without overloading `QuoteFact`.
- The numeric procedure for Daily, and the renewal uplift merge, stay unbound while proration and uplift are Proposed until #623 merges, then Accepted. The operation can reject an unbound method instead of inventing a formula.
- A second entry point has to be kept in step with execute if a later accepted ADR moves both onto one route.
- Returning ids does not by itself create the commercial-decision audit. That store is DRG-612.

### Option C — Keep the money calculation where continuation lives today, and only stamp ids

pricing-service would mint a PolicyDecision id, a policy version, and a trace id for a rate some other service already chose.

Trade-offs:

- The smallest change inside pricing-service.
- V8 and FR-14, as read, say the line is not priced inside quote-service or CAM. A stamp on a rate quote-service already computed leaves that split in place.
- OQ9 already records today's execute path as unable to satisfy AC-S5-2. Stamping ids on an unchanged waterfall does not add Add, Update, or Remove semantics to `PricingLineRequest`.

### Option D — Optional continuation fields on the existing execute body

Add the line fields and the four ids to `PricingContext`, `PricingLineRequest`, and `PricingResult`, and branch inside `executeInternal` when an action is present. Do not insert `QuoteFact`.

Trade-offs:

- One route for the quote client to call.
- Every current success body grows new properties. `ApiResponse` uses `NON_NULL`, so absent new fields would stay absent. Whether quote-service's reader rejects unknown properties was not read. `unverified`
- Simulate and execute share `executeInternal`. A continuation branch that persists a decision would persist on execute only, matching today's `persistAudit` split, unless the branch is special-cased.
- The waterfall's `finalPrice = listPrice * quantity` would have to be skipped or overwritten for a continuation line. A missed branch would price a Remove as a positive list extension.
- The same ADR block as option B applies to the arithmetic.

## Recommended option

**Option B.**

The continuation evaluation is a new operation on pricing-service. It owns the Add, Update, and Remove line model and the decision identity on the success body. `executeInternal` keeps inserting `Customer`, `PricingContext`, and `PricingLineResult` only. `amendment-proration.drl` and `renewal-uplift.drl` stay as they are until an accepted ADR says to change them.

The amount for RetainContracted plus Daily is part of AC-S5-2, and the procedure that computes it is the proration ADR once that ADR is accepted. This note does not copy either formula into a requirement ahead of that acceptance. Proration and uplift are Proposed until #623 merges, then Accepted. Until that merge, the operation returns no amount for a proration method and reports the method unbound. The same hold applies to renewal uplift. The classpath uplift rule is not a line pricer. No code starts until the owner accepts this design, including after #623 merges.

Option A is rejected because the rules that were read do not price a change line. Option C is rejected because it leaves pricing outside pricing-service, which V8 and FR-14 forbid. Option D can be revisited after the contract has shipped once and an accepted ADR collapses the two routes. It is not the first cut, because a continuation line must not share the list-times-quantity path by accident.

## API and contract sketch

This sketch is not an OpenAPI file and not a controller. No route with this shape exists on `PricingExecutionController`. The path below is a name for the sketch.

```text
POST /api/v1/pricing/continuation/evaluate

request
  customerExternalId
  quoteExternalId
  currency
  region
  policyAsOf                 date; the pin for rule-set selection
  policyId                   optional; when present, select that rule set
  policyVersion              optional; required when policyId is present
  lines[]
    lineRef                  caller key, echoed on the response
    action                   Add | Update | Remove
    quantitySemantics        Delta | Absolute
    startQuantity            required for Update and Remove
    deltaQuantity            required when quantitySemantics is Delta
    endQuantity              required; >= 0; 0 is allowed for Remove
    productExternalId
    sourceAcceptedLineId     required for Update and Remove; omitted for Add
    contractedUnitRate       optional summary; never the sole source of Daily pricing
    billingAnchorDate        required for every Daily line, including Add
    subscriptionTermStart, subscriptionTermEnd  required inclusive boundaries for every Daily line
    acceptedRatePeriods[]    complete priced source schedule, ordered and non-overlapping
      acceptedLineId, sequence, startDate, endDate, billingAnchorDate
      quantity, netUnitRate, billingFrequency, provenance, sourceRevision
    selectedOptionCodes[]    required configuration option set; [] for an unconfigured product
    configurationEvidenceRef versioned configuration result or authenticated source snapshot
    pricebookVersion         caller pin to validate, or resolve at policyAsOf and pin on response
    effectiveDate
    amendMode                RetainContracted for the AC-S5-2 case
    prorationMethod          Daily for the AC-S5-2 case

success data
  traceId                    one id for this evaluation
  policyId                   public id of the rule set used
  policyVersion              RuleSet.ruleSetVersion of that set
  lines[]
    lineRef
    policyDecisionId         new id, one per line, not the audit-row uuid
    policyId
    policyVersion
    traceId                  the evaluation trace, copied onto the line
    productExternalId
    pricebookVersion         pin for V9; absent when the line is unbound
    configurationHash        interim pin described by FR-15; absent when unbound
    endQuantity
    unitRate                 absent when procedureStatus is UNBOUND
    amount                   absent when procedureStatus is UNBOUND
    procedureStatus          PRICED | UNBOUND
    inputsDigest             digest of the line inputs that were priced or refused
    explanationCodes
```

The success envelope sets `ApiResponse.traceId` to the same `traceId`. Today's `success(...)` factories do not, so the continuation response cannot use those factories unchanged. That is a design constraint, not an edit in this note.

Checks the sketch includes, taken from rules that were read:

- Action outside Add, Update, and Remove fails. AC-S5-3 names `ACTION_NOT_SUPPORTED_IN_SLICE` for Swap, Upgrade, and Downgrade on submit. The pricing operation uses that code when it is given those actions, so the quote layer does not have to price them. The acceptance criterion itself is on the LifecycleChange submit path in the first-slice doc, which is quote-service work under DRG-583.
- Update and Remove require `sourceAcceptedLineId`. V4, as read, requires an explicit source accepted line for Update and Remove. Add does not.
- `endQuantity >= 0`. V7, as read, requires `start_qty + delta_qty = end_qty` and `end_qty >= 0`. When semantics are Delta, the operation checks `startQuantity + deltaQuantity = endQuantity` and does not invent a side of the equation. Absolute uses `endQuantity` as the priced quantity and does not apply a delta.
- Remove may send `endQuantity` 0. `QuoteContinuationRequest.quantity` is `@Positive` and cannot express that. The new operation does not reuse that constraint.
- RetainContracted uses `contractedUnitRate` from the request. pricing-service does not read quote-service tables for that rate. For Daily it requires the complete accepted source schedule and term/anchor inputs above, authenticated by the trusted service boundary and pinned to sourceRevision; a lone caller rate is insufficient. It validates coverage through the inclusive term end, non-overlap, matching source line/revision, accepted provenance and currency. Missing or inconsistent evidence returns no price. The stub denominator uses the source billing-period boundaries and later full months use their own accepted rates, including steps; no flat-rate extrapolation is allowed. The uplift draft says pricing-service stays stateless and cites ADR-008. ADR-008 was not opened in this pass. `unverified` against ADR-008.
- `prorationMethod` other than a method whose procedure is an accepted ADR returns `procedureStatus` `UNBOUND`, no `unitRate`, and no `amount`, and still returns `policyDecisionId`, `policyId`, `policyVersion`, and `traceId` for the refusal. Daily stays `UNBOUND` while proration is Proposed until #623 merges, then Accepted. MonthDay, None, and NextCycle are names in the technical-approach doc. AC-S5-2 does not name them. They stay `UNBOUND` in this sketch.
- Policy selection on this operation: when `policyId` and `policyVersion` are present, that pair is the pin copied onto every line. When they are absent, selection may use the same repository method execute uses, with `policyAsOf` in place of `LocalDate.now()`, but must additionally require `expirationDate IS NULL OR expirationDate >= policyAsOf` before counting candidates. Effective date is also <= policyAsOf and the row is ACTIVE and not deleted. An explicitly pinned id/version must exist and satisfy its applicable validity rules; invalid or expired pins are rejected, never merely echoed. If the eligible set contains more than one row, the operation fails with `POLICY_SELECTION_AMBIGUOUS` and prices nothing. It does not call `get(0)`. Execute's own `get(0)` behavior stays on execute. Whether more than one ACTIVE set exists anywhere is `unverified`.
- `policyId` on the wire is text, matching the document-line columns. V8 shows rule sets have a `uuid` (`rs-default-001` on the seed set). The Java property for that column was not opened. The implementation reads the public uuid once that property is confirmed. The Long surrogate stored in `pricing_audit_log.rule_set_id` is not the document column. `unverified` until the entity field is read.
- `policyVersion` is `RuleSet.ruleSetVersion` of the selected set.
- `policyDecisionId` is a new UUID string per line. It is not `PricingAuditLog.uuid`. That uuid is one row per execute, and simulate does not insert a row. It is also not the `commercial_decision_audit.id` written for `SYSTEM_RENEWAL_DRAFT`, which is one row per renewal draft and carries no pricing trace.
- A new Add line supplies the target subscription term and billing anchor even when no source line exists. Its rate must come from an authoritative agreement/product policy or pricebook resolution. RetainContracted with no eligible agreement rate is UNBOUND/UNRESOLVED_BASIS, never a caller-selected rate or silent list fallback.
- `selectedOptionCodes` and configuration evidence are explicit inputs even for Add, which has no source line. Resolve/validate the pricebook version and compute the FR-15 SHA-256 pin over product id, that version and sorted option codes. Validate the configuration evidence; never invent a hash from product id alone. The empty set is explicit, not missing input.
- `inputsDigest` is computed over the canonical line inputs (action, semantics, quantities, product, source line, contracted rate, effective date, complete accepted rate schedule and source revision, billing anchors and term boundaries, sorted option codes and configuration evidence, pricebook version, amend mode, proration method, policy id, policy version). The digest is returned so a later audit row can store the same value. Writing that row is DRG-612.

`amendMode` values List and Override are outside the AC-S5-2 path this sketch prices. AC-S5-4, which stores an override and opens a `CONTRACT_MODIFICATION` approval, is quote-service work under DRG-583.

Renewal uplift inputs (basis, cap, floor, price-as-of beyond `policyAsOf`, churn score) are not on this request. The classpath uplift rule does not price a line. The uplift ADR is Proposed until #623 merges, then Accepted.

## Audit implications

On `origin/main` at `dd0f1a8`, quote-service has an append-only `commercial_decision_audit` table. `V1_11__processed_events_inbox.sql` says DRG-564 owns the general continuation pricing-decision trail, and that this table records only the decision to open a renewal draft. `QuoteContinuationService.recordDecision` inserts one row for `SYSTEM_RENEWAL_DRAFT` with `policy_id` set to the literal `QuoteContinuationPolicy`, `operation` `RENEWAL`, and `mode` `RENEWAL_EXIT_RATE`. The columns that were read do not include a pricing-service PolicyDecision id, a policy version, or a trace id. `verified by reading code`

That table is not the pricing decision trail this note specifies. [DRG-612](https://linear.app/drgamtd-workspace/issue/DRG-612/story-commercial-decision-audit-mechanism-for-pricing-rule-outcomes) is the story that reconciles it with PolicyDecision and with `PricingAuditLog`. `verified by reading the issue`. This note does not add a table and does not write a row.

What exists is `PricingAuditLog`, an execution log:

- Execute writes input and output snapshots and the rule-set surrogate id.
- `rules_fired` and `waterfall_trace` are columns on the entity and are not set by `persistAuditLog`.
- `executed_by` is not set by that method.
- The row uuid is not returned on `PricingResult`.
- Simulate does not write a row.
- A serialization failure returns after a log line. The caller can receive a price with no audit row on that branch. A later persistence failure throws `AUDIT_PERSISTENCE_FAILED` after the price was calculated. Neither outcome was executed.

DRG-612, as read, says PolicyDecision is a design note, that those two audit columns are unused, and that the story must reconcile the execution log with one commercial-decision mechanism so two stores are not built. This note follows that. The continuation operation returns the ids and the inputs digest. It does not add a table, and it does not treat `PricingAuditLog` as the PolicyDecision record.

A5 (policy, reason, inputs digest, rules fired, outputs, who decided) and A1 (PolicyDecisions are immutable once executed) are requirements on the trail DRG-612 stores. Returning ids on an HTTP body does not meet "audit events visible" in the first-slice definition of done. That visibility stays on DRG-612.

`rulesFired` from Drools is not a decision trail for option B, because the recommended operation does not fire `amendment-proration.drl` or `renewal-uplift.drl`. When an accepted ADR later routes a procedure through the rules port, the fired-rule list belongs in the DRG-612 record, not only in a log line.

## Migration

No migration ships with this note.

When a sequenced implementation follows option B:

- `POST /api/v1/pricing/execute` and `POST /api/v1/pricing/simulate` keep their current request fields, fact list, and success body. No caller of those routes changes in the pricing story.
- No edit to `amendment-proration.drl`, `renewal-uplift.drl`, or the `rule-007` seed. The classpath-versus-V8 difference stays a known gap until a story that is allowed to change rules is sequenced. This story is not that story.
- No new pricing table for commercial decisions. If a later change persists the returned ids, that change is DRG-612, expand-only, and reviewed as such. Widening `pricing_audit_log` into that store is not the recommendation here.
- quote-service keeps calling the current execute path until DRG-583. `QuoteService.java` was not opened in this pass. The first-slice definition of done says the legacy path is unchanged when its flag is off. The flag's property name was not read. `unverified` for the property key. The continuation route can exist without a quote-service caller.
- `QuoteContinuationRequest` stays `@Positive` on quantity until the quote-service story switches Amend onto the new operation. Remove-to-zero is representable only on the new contract.
- Policy selection for execute stays `get(0)` of the unordered active list. The ambiguous-set failure applies only to the new operation. Changing execute's selection would change which rules run for existing quotes. That change is out of this design.

## Test plan

This pass ran no tests. The cases below are the plan for the implementation story. They are not results.

Unit tests, no Drools session and no Testcontainer:

- A mid-period stepped schedule supplies the stub denominator and each later month's accepted rate; missing anchors, period gaps, overlaps, stale revisions and unauthenticated evidence produce no price.
- Two configured Add lines with different option sets produce different pins; missing option/evidence inputs are rejected, while explicit [] is valid for an unconfigured product.
- An expired ACTIVE set is excluded before ambiguity checks; expired caller pins are rejected.

- Add, Update with Delta, Update with Absolute, and Remove each receive a distinct `policyDecisionId`, the same `traceId`, and a `policyId` plus `policyVersion` copied from the selected rule set.
- Remove with `endQuantity` 0 is accepted by the new contract.
- Update or Remove without `sourceAcceptedLineId` is rejected.
- Delta that fails `startQuantity + deltaQuantity = endQuantity` is rejected.
- Swap, Upgrade, and Downgrade are rejected with `ACTION_NOT_SUPPORTED_IN_SLICE`.
- Daily, and any other proration method, returns `procedureStatus` `UNBOUND` with the four ids set and with `amount` absent, while proration is Proposed until #623 merges, then Accepted.
- A fixture that expects `300.0000`, or a day-stub amount taken from the proration draft, is not part of this plan before that merge. Those figures would treat the draft as accepted.
- Two ACTIVE rule sets and no caller pin yield `POLICY_SELECTION_AMBIGUOUS` and no priced line.
- A caller pin (`policyId`, `policyVersion`) is echoed even when other active sets exist.
- The success envelope's `traceId` equals the body `traceId`.

Regression on the current path, still without claiming a Drools run in this note:

- `executeInternal` with `persistAudit` false writes no `PricingAuditLog`.
- `executeInternal` with `persistAudit` true still builds the audit row without `waterfallTrace`, `rulesFired`, or `executedBy`, and still does not copy the audit uuid onto `PricingResult`.
- The fact list on execute remains the pricing `Customer`, the `PricingContext`, and the line results. `QuoteFact` is not inserted.
- A flat line is still list (or list override) times quantity. That assertion belongs in the existing pricing execution tests. `PricingExecutionServiceTest` was not opened in this pass. OQ9 says that class has no amendment case. `unverified` against the test source here.

Integration: classes named `*IntegrationTest` need Docker. A run without Docker does not execute them. This note does not ask for a new Testcontainer test until the implementation story exists. The V8 guard and `ManagedSeedOptionalInputsIntegrationTest` were not run. OQ9 records the seeded amendment expectation `1200 * 90 / 360 = 300` at scale 4. This pass did not re-open that test class. `unverified` against the test source.

Contract characterization, when the route exists: a golden success document for one Add line and one Remove line contains the four ids and `procedureStatus`. It does not contain an amount while proration is Proposed until #623 merges, then Accepted.

## Scope against DoD v0.2

[CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338) is the approved rule. It replaces v0.1. Done means every item passes. A story is Done only when the owner merges it to `main` and every item passes. Items 2 and 7 are owner gates. The pull request for this note scores each item pass or N/A. This section is the scope of the design, not that score.

The first-slice paragraph in `amendment-first-slice-requirements-and-acceptance.md` section 3 still reads: acceptance criteria automated, migration reviewed for expand-contract, docs updated, no change to legacy path behaviour when the flag is off, audit events visible. That paragraph is the slice story's written done line. It is not a second checklist.

| Topic | Where it sits |
|---|---|
| Line model Add, Update, Remove, Delta and Absolute, and the four ids on the success body | Specified in this note. DRG-609's outcome is the note. AC-S5-2, FR-14, and V8 are the later pricing bar. |
| RetainContracted as the AC-S5-2 mode name | Specified here. |
| Daily amount | The slot is specified here. The arithmetic stays unbound while proration is Proposed until #623 merges, then Accepted. Until that merge `procedureStatus` is `UNBOUND`. |
| Product, pricebook version, and the interim configuration hash | Specified on a `PRICED` line for AC-S5-2 and V9. Replacing the interim hash is DRG-428. |
| Execute and simulate left unchanged | Specified here, so the flag-off path in the first-slice paragraph stays intact. |
| MonthDay, None, NextCycle | Not named by AC-S5-2. Left `UNBOUND`. Not part of this note's outcome. |
| List and Override, and `CONTRACT_MODIFICATION` approval | AC-S5-4 is DRG-583. Not part of this note's outcome. |
| Renewal uplift basis, cap, floor, and the classpath fraction | Uplift is Proposed until #623 merges, then Accepted. DRG-541 is a different story. Not part of this request. |
| Swap, Upgrade, Downgrade priced | Out of the first slice. The sketch rejects them. |
| Pricing PolicyDecision row, `rules_fired`, `waterfall_trace`, actor on `PricingAuditLog` | DRG-612. V1_11 records only the renewal-draft-open decision. |
| LifecycleChange, the continuation facade, AC-S5-1, AC-S5-5, AC-S5-6 | DRG-583. |
| Inserting `QuoteFact` so the two classpath rules fire | Option A. Not the recommendation. |
| Trace id on simulate of today's waterfall | OQ9's draft proposal mentions it. AC-S5-2 does not. Not part of this request. |
| Termination fees and credits | The proration draft says it informs DRG-550. Not specified here. |
| Backdating reason and approval (V6) | The spec places that on the document transition. Not specified here. |

## Evidence index

Read in this pass. Not executed.

| Item | Path or source |
|---|---|
| OQ9 finding | `docs/renewals-amendments/prereq/oq9-pricing-continuation-capability.md` |
| Proration ADR draft, Proposed until #623 merges, then Accepted | `docs/renewals-amendments/prereq/adr-amendment-proration-formula.md` (header and the two formula sections). Status-change pull request [#623](https://github.com/drgaciw/open-crm-cpq/pull/623) for #605. |
| Uplift ADR draft, Proposed until #623 merges, then Accepted | `docs/renewals-amendments/prereq/adr-renewal-uplift-basis-and-precedence.md` (header and context through D-02). Status-change pull request #623 for #609. #623 also covers #606. |
| Execute orchestration and audit insert | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionService.java` |
| Audit entity | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingAuditLog.java` |
| Controller routes | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionController.java` |
| Request and result fields | `PricingContext.java`, `PricingLineRequest.java`, `PricingResult.java`, `PricingLineResult.java` under `.../pricing/waterfall/` |
| Rule set version | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/ruleset/RuleSet.java` |
| Classpath rules | `backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl`, `renewal-uplift.drl` |
| Seed guard for `rule-007` | `backend/services/pricing-service/src/main/resources/db/migration/V8__guard_managed_seed_optional_inputs.sql` |
| Fact types | `Customer.java`, `QuoteFact.java` |
| Success versus error trace field | `backend/libs/common/src/main/java/com/opencrm/common/dto/ApiResponse.java` |
| Continuation request quantity | `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java` |
| V8, FR-14, AC-S5-2, definition of done | `docs/renewals-amendments/mid-term-amendment-policy-spec.md`, `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` |
| Decision columns | `docs/renewals-amendments/amendment-entity-field-schema.md` |
| Renewal-draft audit table and insert | `backend/services/quote-service/src/main/resources/db/migration/V1_11__processed_events_inbox.sql`; `QuoteContinuationService.recordDecision` |
| Approved checklist | [CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338) |
| Stories | DRG-609, DRG-612 (Linear, this pass) |

## Unverified in this pass

- Any runtime result: rule firing, divide-by-zero, HTTP JSON, audit insert, test success.
- `GlobalExceptionHandler` and the source of the error-path trace id.
- `V2__seed_default_rules.sql` body, beyond the substring V8 replaces.
- `QuoteContinuationPolicy.java` and `QuoteService.java`. OQ9 describes them. This pass did not.
- `PricingExecutionServiceTest` and `ManagedSeedOptionalInputsIntegrationTest` source.
- ADR-008 text. The uplift draft cites it for a stateless pricing-service.
- The Java field behind `rule_set.uuid`.
- How many ACTIVE rule sets a given database contains.
- Whether quote-service ignores unknown JSON properties on `PricingResult`.
- The feature-flag property key for the legacy continuation path.
