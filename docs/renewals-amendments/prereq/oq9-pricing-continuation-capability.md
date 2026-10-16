# OQ9 — pricing-service continuation capability

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Date: 2026-10-16.*

**Question.** Can `pricing-service` price Add, Update, and Remove lines and return a PolicyDecision id, a policy version, and a trace id?

**Verdict.** It cannot. `POST /api/v1/pricing/execute` and `POST /api/v1/pricing/simulate` price a flat or ramp line from list (or a caller list-price override) through the current waterfall. They have no ChangeLine action, no quantity semantics, and no decision identity on the success body. Seeded rule `rule-007` / classpath `amendment-proration.drl` prorates one quote-level delta when a `QuoteFact` is inserted by a test. `PricingExecutionService` never inserts that fact, so the rule does not run on execute or simulate.

**Effect on S5.** [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583/s5-quote-service-lifecyclechange-amend-with-pricing-service-decisions) lists this check as a blocker. The check is done. The capability is absent, so AC-S5-2 and validation rule V8 stay unmet. This file does not implement pricing, accept an ADR, flip a flag, or merge anything.

**Numbering.** DRG-583 calls this check OQ9 (pricing-service capability). `mid-term-amendment-policy-spec.md` §8 uses OQ9 for a different question: backdating limits and approval thresholds (V6). This file is the pricing-capability check. It does not answer the backdating question.

## Method

Static read of the execute path, the amendment rule (classpath and Flyway seed), the audit and response types, and the quote-service continuation caller. Line numbers are from `main` at the time of this note.

This pass did not re-execute Drools. The workspace has no local Drools or rules-engine build artifacts, Docker is unavailable (so `*IntegrationTest` / Testcontainers did not run), and the JDK here is 21 while the backend target is Java 25. The arithmetic below is the rule body evaluated by hand. It matches the assertion already present in `ManagedSeedOptionalInputsIntegrationTest` (1200 × 90 / 360 = 300). That test was not re-run here.

## What S5 requires

From DRG-583 and the design set already in this folder:

| Need | Source |
|---|---|
| ChangeLines with action Add, Update, or Remove, and quantity semantics Delta or Absolute | DRG-583 scope; schema `action_code`, `quantity_semantics` |
| RetainContracted plus Daily proration on a priced line | DRG-583 AC-S5-2; spec §1 mode list |
| Each priced line carries a PolicyDecision id, policy id, policy version, and pricing-service trace id | Spec V8; schema columns `policy_id`, `policy_version`, `policy_decision_id`, `pricing_trace_id`; spike §5 |
| Pricing stays in pricing-service. quote-service and CAM do not price the line | Spec V8 |

There is no `PolicyDecision` type anywhere under `backend/`. The name exists only in `docs/renewals-amendments/`.

## 1. Execute request and response

`PricingExecutionController` exposes execute, simulate, bulk simulate, and audit fetch. Execute returns `ApiResponse<PricingResult>` via `ApiResponse.success(result, message)`.

`PricingContext` fields: `customerExternalId`, `quoteExternalId`, `lineItems`, `region`, `currency`, `durationInMonths`, `subscriptionType`, `metadata`. The comment on `subscriptionType` says `NEW`, `AMENDMENT`, `RENEWAL`. No pricing-service Java reads `getSubscriptionType()`. Quote calculate copies the quote's own `subscriptionType` (tests use values such as `ANNUAL`), which is a billing label, not a deal type.

`PricingLineRequest` fields: `productExternalId`, `lineItemType`, `quantity`, `listPriceOverride`, `metadata`, optional `schedule`, optional `billingFrequency`. There is no action code, no Delta versus Absolute, no source accepted-line id, no effective date, no contracted rate, and no proration method.

`PricingResult` fields: `success`, `lineResults`, `waterfall`, `financialMetrics`, `explanationCodes`, `approvalState`, `approvalTier`, `executionTimeMs`, `errorMessage`.

`PricingLineResult` fields: product, type, quantity, list, unit, discount, surcharge, `finalPrice`, `explanationCodes`, `metadata`, optional `pricedPeriods`.

`PricingExecuteView` in quote-service mirrors that payload and adds nothing for a decision id, policy id, policy version, or trace id.

| S5 field | On execute success body |
|---|---|
| PolicyDecision id | Absent. No type, no column on the result, no id returned from the audit insert |
| Policy id | Absent. The active `RuleSet` primary key is stored on the audit row only, and only for execute |
| Policy version | Absent. `RuleSet.ruleSetVersion` exists (seed `rs-default-001` is version 1) and `RuleExecutionContext.ruleSetVersion` exists. `executeInternal` does not set either onto the result |
| Trace id | Absent on success. See §4 |

`executeInternal` builds each line as list price × quantity, then inserts three fact kinds into Drools: the pricing `Customer`, the `PricingContext`, and the `PricingLineResult` list. It does not insert `QuoteFact` or `QuoteLineItemFact`. The classpath amendment rule matches only `QuoteFact`, so it cannot fire on this path. `RuleExecutionResult.getRulesFired()` is logged and then dropped. `PricingResult.explanationCodes` is created empty and never filled from rules. The result is created with `approvalState` `"APPROVED"`. `PeriodGovernance` can later set `"PENDING"` when a ramp schedule is present. A flat line keeps `"APPROVED"`.

Active rule-set selection is `findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(ACTIVE, LocalDate.now())` with no order, then `get(0)`. The date is the server's today, not a line effective date or price-as-of date. With more than one active set, the chosen policy is not pinned.

## 2. Amendment proration rule

Classpath file `backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl` (the execute path does not load this file; execute loads `rules.content` from the database):

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

The rule reads no line. It has no Add, Update, or Remove condition. It does not write `unitPrice` or `finalPrice`. It writes two metadata keys on the quote fact: `prorationAdjustment` and `coTermDate`.

Flyway `V2__seed_default_rules.sql` inserts the same body as `rule-007` on rule set `rs-default-001` (`rule_set_version` 1, status ACTIVE, explanation code `AMENDMENT_PRORATION`, priority 540). `V8__guard_managed_seed_optional_inputs.sql` rewrites that when-clause, for system-owned `rule-007` only, so the rule also requires non-null `remainingTermDays`, `fullTermDays`, and `amendmentDeltaAmount`, and `fullTermDays > 0`. The classpath file does not have those guards. A zero `fullTermDays` on the classpath body divides by zero. After V8, the database body does not fire, and `prorationAdjustment` stays unset.

`PricingDrlCorpusCompileTest` lists `amendment-proration.drl` for compilation. There is no golden fixture under `src/test/resources/fixtures/pricing/drl/` for this rule. `PricingExecutionServiceTest` has no amendment case.

### Worked values (rule body, not a Drools session)

Inputs used by `ManagedSeedOptionalInputsIntegrationTest.retainsValidPolicyOutputsAndDoesNotHideMalformedNumericInput`: `dealType=AMENDMENT`, `remainingTermDays=90`, `fullTermDays=360`, `amendmentDeltaAmount=1200`.

`1200 * 90 / 360 = 300` at scale 4, half up: `300.0000`. The test asserts `prorationAdjustment` equal to `300`. The same inputs with an Add line, an Update line, or a Remove line produce the same number, because the rule never reads an action. Missing optional inputs, or `fullTermDays=0` after V8, leave `prorationAdjustment` null (same test class, `allElevenRulesExecuteMinimalNonUsageRampFactsAfterVerifiedRedAtV7`).

That output is a quote-level decimal on a `QuoteFact`. It is not a per-line price, not a Daily proration of a retained contracted rate, and not a PolicyDecision.

## 3. Where Add / Update / Remove are priced today

`QuoteContinuationPolicy.plan` in quote-service computes continuation rates before pricing-service is called.

`QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, or `EXTENSION`. Quantity is `@Positive` (at least 1). The policy requires exactly one accepted source line with `ACCEPTED_PRICED` provenance. Amendments and expansions keep the target term and the accepted unit rates. A caller-supplied uplift on those operations is rejected. The stub amount is a day-count fraction of one billing period:

`monthly * days(effective, periodEnd) / days(billingStart, periodEnd)` at scale 2, half up.

`QuoteService.calculate` then calls pricing-service to re-price that already chosen unit rate:

- A partial first interval is sent as a one-month ramp step with `priceBasis=ABSOLUTE` and `unitPrice` equal to the contracted net. The call fails closed unless pricing returns that same net unit price, the same net monthly, and no migration credit.
- Later intervals are checked the same way in `verifyContinuationPeriods`.

So pricing-service is a preservation check of a rate quote-service already chose. A new product Add (no source accepted line), a Remove (quantity 0 or a termination), and an Update that must reprice from list or an override are outside this method. Remove-to-zero cannot pass `@Positive` quantity. Swap, Upgrade, and Downgrade are not operations here (DRG-583 AC-S5-3 rejects those on the future LifecycleChange API; they are also absent from pricing).

This matches the risk note on DRG-583: pricing modes are missing, and the fallback is RetainContracted with an explicit override. Today's code already retains the contracted rate inside quote-service. It still does not return a PolicyDecision id, policy version, or trace id, so the fallback does not satisfy V8 or AC-S5-2 by itself.

## 4. Identifiers that exist and are not returned

| Candidate | Where it lives | Returned on execute success? |
|---|---|---|
| `RuleSet.ruleSetVersion` | Column `rule_set_version`, seed value 1 for `rs-default-001` | No. Not copied onto `RuleExecutionContext` or `PricingResult` |
| `RuleSet.id` | Surrogate key | Written to `pricing_audit_log.rule_set_id` on execute only. Not on `PricingResult`. Simulate skips the insert |
| Rule uuid `rule-007` | `rules.uuid` | Not selected into the response. Execute passes `String.valueOf(rule.getId())` as the Drools rule definition id and does not echo it |
| `PricingAuditLog.uuid` | Random UUID assigned in `@PrePersist` | Not copied onto `PricingResult`. The insert is not read back |
| `waterfall_trace`, `rules_fired` | Columns on `pricing_audit_log` (`V1__create_pricing_schema.sql`) | `persistAuditLog` never sets them. They stay null |
| `ApiResponse.traceId` | Set by `ApiResponse.error(message, errorCode, traceId)` from MDC key `traceId` in `GlobalExceptionHandler` | Success factories `success(data)` and `success(data, message)` leave `traceId` null. A priced call has no trace id |
| `executionTimeMs` | `PricingResult` | A duration, not an id |

Audit failure on execute throws `AUDIT_PERSISTENCE_FAILED` after the price exists in memory, except for JSON serialization failure, which is logged as `AUDIT_INTEGRITY_FAILURE` and still returns the result. Either way the caller does not receive an audit id.

## 5. Gap list for DRG-583

1. **No Add / Update / Remove pricing.** The execute line has no action code and no quantity semantics. The amendment rule ignores line action and does not run on execute.
2. **No RetainContracted evaluation inside pricing-service.** Contracted-rate retention and the day-count stub are computed in `QuoteContinuationPolicy`. Pricing is asked to echo an absolute ramp price.
3. **No Daily / MonthDay / None / NextCycle choice on the pricing request.** The Drools consequence is one ratio, `delta * remainingDays / fullTermDays`, scale 4. Quote-service stub proration is scale 2 over one billing period. Those are different formulas (already called out as RA-08 in the sanity check). Neither formula is selected by a policy version on the response.
4. **No PolicyDecision id, policy id, or policy version on the success body.**
5. **No trace id on the success body.** The audit uuid and the error-path MDC trace id are the nearest existing identifiers, and neither is returned from a successful execute.
6. **Classpath rule and database rule differ.** V8 guards exist only on the seeded row. Execute uses the database body. A test that compiles the classpath file is not testing the body execute would run.
7. **Policy selection is unpinned.** First active rule set as of `LocalDate.now()`, unordered. That cannot satisfy "policy version pinned at pricing" (spec V9's companion on the decision, V8).

## 6. Draft proposal (not accepted)

Owner acceptance is required before any ADR is marked Accepted and before any code change. This section is a draft for that decision. It is not an instruction to implement.

S5 cannot meet AC-S5-2 by calling today's execute API. A later pricing change would need an additive contract, for example a continuation evaluation that:

- accepts, per line, action `Add` | `Update` | `Remove`, quantity semantics `Delta` | `Absolute`, the source accepted rate when the action has one, the effective date, amend mode (`RetainContracted` | `List` | `Override`), and proration method (`Daily` | `MonthDay` | `None` | `NextCycle`);
- returns, per line, `policyDecisionId`, `policyId`, `policyVersion`, and `traceId`, plus the priced amount and the inputs digest;
- persists that decision on the audit row and returns the same ids on the success envelope, including simulate when the caller must show a trace before save.

Until that contract exists, quote-service LifecycleChange work can store a continuation only by keeping the current RetainContracted calculation local, and those lines will fail V8. That is the gap DRG-583 named. Closing it is a separate implementation story, not this note.

## Evidence index

| Item | Path |
|---|---|
| Execute / simulate | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionController.java` |
| Orchestration, facts, audit insert | `.../execution/PricingExecutionService.java` (`executeInternal`, `persistAuditLog`) |
| Request / result types | `.../waterfall/PricingContext.java`, `PricingLineRequest.java`, `PricingResult.java`, `PricingLineResult.java` |
| Audit entity | `.../execution/PricingAuditLog.java`; table in `.../db/migration/V1__create_pricing_schema.sql` |
| Rule set version | `.../ruleset/RuleSet.java`; seed `.../db/migration/V2__seed_default_rules.sql` (`rs-default-001`, `rule-007`) |
| V8 when-clause guard | `.../db/migration/V8__guard_managed_seed_optional_inputs.sql` (`rule-007`) |
| Classpath rule | `.../resources/rules/amendment-proration.drl` |
| Drools insert and discarded fire count | `backend/libs/rules-engine/.../drools/DroolsRulesEngine.java`; `RuleExecutionContext.ruleSetVersion` |
| Success vs error trace id | `backend/libs/common/.../dto/ApiResponse.java`; `.../pricing/config/GlobalExceptionHandler.java` |
| Prior Drools assertion (not re-run) | `.../pricing/rule/ManagedSeedOptionalInputsIntegrationTest.java` |
| Continuation rates in quote-service | `.../quote/QuoteContinuationPolicy.java`, `QuoteContinuationRequest.java` |
| Pricing used as a rate echo | `.../quote/QuoteService.java` (`calculate`, `priceInitialInterval`, `verifyContinuationPeriods`, `buildPricingContext`); `.../client/PricingExecuteView.java`; `.../client/PricingServiceClient.java` |
| Required decision fields | `docs/renewals-amendments/mid-term-amendment-policy-spec.md` (V8); `amendment-entity-field-schema.md`; `amendment-document-design-spike.md` §5 |
| S5 story | DRG-583 |
