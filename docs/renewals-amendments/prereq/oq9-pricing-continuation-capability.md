# OQ9 — pricing-service continuation capability

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Date: 2026-10-16. Review pass: 2026-10-16.*

## Verification

**Drools was NOT re-executed in this pass.**

Nothing was run. This pass did not start Maven, a Drools session, `fireAllRules`, Testcontainers, or an HTTP call to pricing-service. The JDK on the reader was not used to execute pricing code.

Every claim in this file is marked with one of these two phrases:

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Checked by reading source, SQL, or docs in the tree. Not executed. |
| `verified by running` | Checked by executing code or a test in this pass. |

**Claims marked `verified by running`: none.**

Runtime behavior is unverified. That includes whether a rule fires, whether a divide throws, whether `ManagedSeedOptionalInputsIntegrationTest` passes, and what a live execute response contains. Those statements below are conclusions from the text that was read. They are not engine results.

Hand evaluation of the proration formula (`1200 * 90 / 360`) is arithmetic on the source text. It is not a Drools run. It is marked `verified by reading code`.

## Question and verdict

**Question.** Can `pricing-service` price Add, Update, and Remove lines and return a PolicyDecision id, a policy version, and a trace id? `verified by reading code`

**Verdict.** It cannot, on the evidence of the source. `POST /api/v1/pricing/execute` and `POST /api/v1/pricing/simulate` price a flat or ramp line from list (or a caller list-price override) through the current waterfall. They have no ChangeLine action, no quantity semantics, and no decision identity on the success body. Seeded rule `rule-007` / classpath `amendment-proration.drl` prorates one quote-level delta when a `QuoteFact` is inserted. `PricingExecutionService` never inserts that fact, so the rule does not run on execute or simulate. The last sentence is a reading of the insert list and the rule's `when` clause. It was not observed in a session. `verified by reading code`

**Effect on S5.** [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583/s5-quote-service-lifecyclechange-amend-with-pricing-service-decisions) lists this check as a blocker. The check is recorded here. The capability is absent in the source, so AC-S5-2 and validation rule V8 stay unmet by the current API. This file does not implement pricing, accept an ADR, flip a flag, or merge anything. `verified by reading code`

**Numbering.** DRG-583 calls this check OQ9 (pricing-service capability). `mid-term-amendment-policy-spec.md` §8 uses OQ9 for a different question: backdating limits and approval thresholds (V6). This file is the pricing-capability check. It does not answer the backdating question. `verified by reading code`

## What S5 requires

From DRG-583 and the design set already in this folder. Each row was read from those docs. `verified by reading code`

| Need | Source | Mark |
|---|---|---|
| ChangeLines with action Add, Update, or Remove, and quantity semantics Delta or Absolute | DRG-583 scope; schema `action_code`, `quantity_semantics` | `verified by reading code` |
| RetainContracted plus Daily proration on a priced line | DRG-583 AC-S5-2; spec §1 mode list | `verified by reading code` |
| Each priced line carries a PolicyDecision id, policy id, policy version, and pricing-service trace id | Spec V8; schema columns `policy_id`, `policy_version`, `policy_decision_id`, `pricing_trace_id`; spike §5 | `verified by reading code` |
| Pricing stays in pricing-service. quote-service and CAM do not price the line | Spec V8 | `verified by reading code` |

There is no `PolicyDecision` type anywhere under `backend/`. The name exists only in `docs/renewals-amendments/`. `verified by reading code`

## 1. Execute request and response

`PricingExecutionController` exposes execute, simulate, bulk simulate, and audit fetch. Execute returns `ApiResponse<PricingResult>` via `ApiResponse.success(result, message)`. `verified by reading code`

`PricingContext` fields: `customerExternalId`, `quoteExternalId`, `lineItems`, `region`, `currency`, `durationInMonths`, `subscriptionType`, `metadata`. The comment on `subscriptionType` says `NEW`, `AMENDMENT`, `RENEWAL`. No pricing-service Java reads `getSubscriptionType()`. Quote calculate copies the quote's own `subscriptionType` (tests use values such as `ANNUAL`), which is a billing label, not a deal type. `verified by reading code`

`PricingLineRequest` fields: `productExternalId`, `lineItemType`, `quantity`, `listPriceOverride`, `metadata`, optional `schedule`, optional `billingFrequency`. There is no action code, no Delta versus Absolute, no source accepted-line id, no effective date, no contracted rate, and no proration method. `verified by reading code`

`PricingResult` fields: `success`, `lineResults`, `waterfall`, `financialMetrics`, `explanationCodes`, `approvalState`, `approvalTier`, `executionTimeMs`, `errorMessage`. `verified by reading code`

`PricingLineResult` fields: product, type, quantity, list, unit, discount, surcharge, `finalPrice`, `explanationCodes`, `metadata`, optional `pricedPeriods`. `verified by reading code`

`PricingExecuteView` in quote-service mirrors that payload and adds nothing for a decision id, policy id, policy version, or trace id. `verified by reading code`

| S5 field | On execute success body | Mark |
|---|---|---|
| PolicyDecision id | Absent. No type, no column on the result, no id returned from the audit insert | `verified by reading code` |
| Policy id | Absent. The active `RuleSet` primary key is stored on the audit row only, and only for execute | `verified by reading code` |
| Policy version | Absent. `RuleSet.ruleSetVersion` exists (seed `rs-default-001` is version 1) and `RuleExecutionContext.ruleSetVersion` exists. `executeInternal` does not set either onto the result | `verified by reading code` |
| Trace id | Absent on success. See §4 | `verified by reading code` |

`executeInternal` builds each line as list price × quantity, then inserts three fact kinds into Drools: the pricing `Customer`, the `PricingContext`, and the `PricingLineResult` list. It does not insert `QuoteFact` or `QuoteLineItemFact`. The classpath amendment rule matches only `QuoteFact`. From that text, the rule cannot fire on this path. That fire outcome was not run. `RuleExecutionResult.getRulesFired()` is logged and then dropped. `PricingResult.explanationCodes` is created empty and never filled from rules. The result is created with `approvalState` `"APPROVED"`. `PeriodGovernance` can later set `"PENDING"` when a ramp schedule is present (`PeriodGovernance.java` assigns `"PENDING"`). A flat line has no schedule, so that assignment is not on the flat path. `verified by reading code`

Active rule-set selection is `findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(ACTIVE, LocalDate.now())` with no order, then `get(0)`. The date is the server's today, not a line effective date or price-as-of date. With more than one active set, the chosen policy is not pinned. The repository method declares no `OrderBy`. `verified by reading code`

## 2. Amendment proration rule

Classpath file `backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl`. The execute path does not load this file. Execute loads `rules.content` from the database via `Rule.getContent()`. `verified by reading code`

The classpath rule, paraphrased from the file:

```
rule "Amendment Proration"
when
    QuoteFact
    eval dealType == "AMENDMENT"
then
    prorationAdjustment = amendmentDeltaAmount * remainingTermDays / fullTermDays
        (scale 4, HALF_UP)
    coTermDate = masterContractCoTermDate
```

`verified by reading code`

The rule reads no line. It has no Add, Update, or Remove condition. It does not write `unitPrice` or `finalPrice`. It writes two metadata keys on the quote fact: `prorationAdjustment` and `coTermDate`. `verified by reading code`

Flyway `V2__seed_default_rules.sql` inserts that body as `rule-007` on rule set `rs-default-001` (`rule_set_version` 1, status ACTIVE, explanation code `AMENDMENT_PRORATION`, priority 540). `V8__guard_managed_seed_optional_inputs.sql` rewrites that when-clause, for system-owned `rule-007` only, so the rule also requires non-null `remainingTermDays`, `fullTermDays`, and `amendmentDeltaAmount`, and `fullTermDays > 0`. The classpath file does not have those guards. `verified by reading code`

A zero `fullTermDays` on the classpath body is a divide by `BigDecimal.valueOf(fullTermDays)` with no preceding `> 0` check. The throw was not observed. `verified by reading code`

After V8, the database `when` clause excludes `fullTermDays` of 0, so that body does not match and `prorationAdjustment` stays unset. That non-fire was not observed. The integration test source asserts null in that case. The test was not run. `verified by reading code`

`PricingDrlCorpusCompileTest` lists `amendment-proration.drl` for compilation. There is no golden fixture under `src/test/resources/fixtures/pricing/drl/` for this rule. `PricingExecutionServiceTest` has no amendment case. `verified by reading code`

### Worked values (source text only; Drools was NOT re-executed)

Inputs named in `ManagedSeedOptionalInputsIntegrationTest.retainsValidPolicyOutputsAndDoesNotHideMalformedNumericInput`: `dealType=AMENDMENT`, `remainingTermDays=90`, `fullTermDays=360`, `amendmentDeltaAmount=1200`. `verified by reading code`

Hand evaluation of the rule text: `1200 * 90 / 360 = 300` at scale 4, half up, is `300.0000`. The test source asserts `prorationAdjustment` equal to `300`. The test was not run, and Drools was NOT re-executed. `verified by reading code`

The same inputs plus an Add line, an Update line, or a Remove line are not distinguished by the `when` clause, which never reads an action. A difference in output was not measured. `verified by reading code`

Missing optional inputs, or `fullTermDays=0` after V8, are asserted null by `allElevenRulesExecuteMinimalNonUsageRampFactsAfterVerifiedRedAtV7` in the same test class. That test was not run. `verified by reading code`

The rule output is a quote-level decimal on a `QuoteFact`. The source does not show a per-line price, a Daily proration of a retained contracted rate, or a PolicyDecision. `verified by reading code`

## 3. Where Add / Update / Remove are priced today

`QuoteContinuationPolicy.plan` in quote-service computes continuation rates before pricing-service is called. `verified by reading code`

`QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, or `EXTENSION`. Quantity is `@Positive` (at least 1). The policy requires exactly one accepted source line with `ACCEPTED_PRICED` provenance. Amendments and expansions keep the target term and the accepted unit rates. A non-zero caller uplift on those operations is rejected (`termMonths` set, or `upliftPercent` with a non-zero sign, throws). The stub amount in source is a day-count fraction of one billing period: `monthly * days(effective, periodEnd) / days(billingStart, periodEnd)` at scale 2, half up. `verified by reading code`

`QuoteService.calculate` then calls pricing-service with that already chosen unit rate. `verified by reading code`

- A partial first interval is sent as a one-month ramp step with `priceBasis=ABSOLUTE` and `unitPrice` equal to the contracted net. The method throws unless the returned net unit price and net monthly match and migration credit is null. That throw was not provoked. `verified by reading code`
- Later intervals are checked the same way in `verifyContinuationPeriods`. `verified by reading code`

From that source, pricing-service is a preservation check of a rate quote-service already chose. A new product Add (no source accepted line), a Remove (quantity 0 or a termination), and an Update that must reprice from list or an override are outside `plan`. Remove-to-zero cannot pass `@Positive` quantity. Swap, Upgrade, and Downgrade are not values of `QuoteContinuationRequest.Operation`. DRG-583 AC-S5-3 says the future LifecycleChange API rejects those three. They are also absent from the pricing request types. `verified by reading code`

DRG-583's risk note says pricing modes are missing and the fallback is RetainContracted with an explicit override. Today's quote-service source retains the contracted rate in `QuoteContinuationPolicy`. That path's response types still have no PolicyDecision id, policy version, or trace id, so the fallback does not satisfy V8 or AC-S5-2 in the types that were read. `verified by reading code`

## 4. Identifiers that exist and are not returned

| Candidate | Where it lives | Returned on execute success? | Mark |
|---|---|---|---|
| `RuleSet.ruleSetVersion` | Column `rule_set_version`, seed value 1 for `rs-default-001` | No. Not copied onto `RuleExecutionContext` or `PricingResult` | `verified by reading code` |
| `RuleSet.id` | Surrogate key | Written to `pricing_audit_log.rule_set_id` on execute only. Not on `PricingResult`. Simulate skips the insert (`persistAudit` false) | `verified by reading code` |
| Rule uuid `rule-007` | `rules.uuid` | Not selected into the response. Execute passes `String.valueOf(rule.getId())` as the Drools rule definition id and does not echo it | `verified by reading code` |
| `PricingAuditLog.uuid` | Random UUID assigned in `@PrePersist` | Not copied onto `PricingResult`. The insert is not read back | `verified by reading code` |
| `waterfall_trace`, `rules_fired` | Columns on `pricing_audit_log` (`V1__create_pricing_schema.sql`) | `persistAuditLog` never sets them | `verified by reading code` |
| `ApiResponse.traceId` | Set by `ApiResponse.error(message, errorCode, traceId)` from MDC key `traceId` in `GlobalExceptionHandler` | Success factories `success(data)` and `success(data, message)` do not set `traceId` | `verified by reading code` |
| `executionTimeMs` | `PricingResult` | Present as a duration field, not an id | `verified by reading code` |

`persistAuditLog` logs `AUDIT_INTEGRITY_FAILURE` and returns after `JsonProcessingException`. Any other exception there throws `BusinessException` with code `AUDIT_PERSISTENCE_FAILED`. Neither branch puts an audit id on `PricingResult`. Those branches were not executed. `verified by reading code`

## 5. Gap list for DRG-583

Each gap is a reading of the sections above. None was confirmed by a run. `verified by reading code`

1. **No Add / Update / Remove pricing.** The execute line type has no action code and no quantity semantics. The amendment rule's `when` clause does not mention line action, and execute does not insert `QuoteFact`. `verified by reading code`
2. **No RetainContracted evaluation inside pricing-service.** Contracted-rate retention and the day-count stub are in `QuoteContinuationPolicy`. Pricing is sent an absolute ramp price to match. `verified by reading code`
3. **No Daily / MonthDay / None / NextCycle choice on the pricing request.** The Drools consequence is one ratio, `delta * remainingDays / fullTermDays`, scale 4. Quote-service stub proration is scale 2 over one billing period. The sanity check already names that formula conflict RA-08. Neither formula is selected by a policy version field on `PricingResult`. `verified by reading code`
4. **No PolicyDecision id, policy id, or policy version on the success body.** `verified by reading code`
5. **No trace id on the success body.** The audit uuid and the error-path MDC trace id are the nearest identifiers in source, and the success factories do not set `traceId`. `verified by reading code`
6. **Classpath rule and database rule differ.** V8 guards exist only on the seeded `rule-007` update. Execute uses `rule.getContent()`. A test that compiles the classpath file compiles a body without the V8 guard. `verified by reading code`
7. **Policy selection is unpinned.** First active rule set as of `LocalDate.now()`, and the repository method has no order. That source does not pin a policy version on the result (spec V8; V9 pins product, pricebook, and configuration hash). `verified by reading code`

## 6. Draft proposal (not accepted)

Owner acceptance is required before any ADR is marked Accepted and before any code change. This section is a draft for that decision. It is not an instruction to implement. It is not a finding about current code, so it is not marked `verified by reading code` or `verified by running`.

S5 cannot meet AC-S5-2 by calling today's execute API, on the reading in §§1–5. A later pricing change would need an additive contract, for example a continuation evaluation that:

- accepts, per line, action `Add` | `Update` | `Remove`, quantity semantics `Delta` | `Absolute`, the source accepted rate when the action has one, the effective date, amend mode (`RetainContracted` | `List` | `Override`), and proration method (`Daily` | `MonthDay` | `None` | `NextCycle`);
- returns, per line, `policyDecisionId`, `policyId`, `policyVersion`, and `traceId`, plus the priced amount and the inputs digest;
- persists that decision on the audit row and returns the same ids on the success envelope, including simulate when the caller must show a trace before save.

Until that contract exists, quote-service LifecycleChange work can store a continuation only by keeping the current RetainContracted calculation local, and those lines fail V8 on the types read above. That is the gap DRG-583 named. Closing it is a separate implementation story, not this note.

## Evidence index

Read in this pass. Not executed. `verified by reading code`

| Item | Path |
|---|---|
| Execute / simulate | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionController.java` |
| Orchestration, facts, audit insert | `.../execution/PricingExecutionService.java` (`executeInternal`, `persistAuditLog`) |
| Ramp approval assignment | `.../ramp/PeriodGovernance.java` |
| Request / result types | `.../waterfall/PricingContext.java`, `PricingLineRequest.java`, `PricingResult.java`, `PricingLineResult.java` |
| Audit entity | `.../execution/PricingAuditLog.java`; table in `.../db/migration/V1__create_pricing_schema.sql` |
| Rule set lookup | `.../ruleset/RuleSetRepository.java`; `.../ruleset/RuleSet.java` |
| Seed | `.../db/migration/V2__seed_default_rules.sql` (`rs-default-001`, `rule-007`) |
| V8 when-clause guard | `.../db/migration/V8__guard_managed_seed_optional_inputs.sql` (`rule-007`) |
| Classpath rule | `.../resources/rules/amendment-proration.drl` |
| Drools insert and discarded fire count | `backend/libs/rules-engine/.../drools/DroolsRulesEngine.java`; `RuleExecutionContext.ruleSetVersion` |
| Corpus compile list | `backend/libs/rules-engine/.../drools/PricingDrlCorpusCompileTest.java` |
| Success vs error trace id | `backend/libs/common/.../dto/ApiResponse.java`; `.../pricing/config/GlobalExceptionHandler.java` |
| Test source, not run | `.../pricing/rule/ManagedSeedOptionalInputsIntegrationTest.java`; `.../pricing/execution/PricingExecutionServiceTest.java`; `src/test/resources/fixtures/pricing/drl/` |
| Continuation rates in quote-service | `.../quote/QuoteContinuationPolicy.java`, `QuoteContinuationRequest.java` |
| Pricing used as a rate echo | `.../quote/QuoteService.java` (`calculate`, `priceInitialInterval`, `verifyContinuationPeriods`, `buildPricingContext`); `.../client/PricingExecuteView.java`; `.../client/PricingServiceClient.java` |
| Required decision fields | `docs/renewals-amendments/mid-term-amendment-policy-spec.md` (V8); `amendment-entity-field-schema.md`; `amendment-document-design-spike.md` §5 |
| S5 story | DRG-583 |
