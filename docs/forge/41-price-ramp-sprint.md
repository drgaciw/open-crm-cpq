# Price ramp sprint — ADR-023 implementation plan

**Status:** Plan only. ADR-023 is **Accepted** in this same change. No product code is in this PR. Linear issues DRG-336, DRG-338, DRG-339, and DRG-341, and their Notion pages, stay **Backlog** until this docs pull request is on `main`.  
**Architect:** opencrm-architect, worktree `/home/username01/Projects/open-crm-cpq/.worktrees/architect`, branch `docs/forge-ramp-sprint` at base `03bb6a67` (Merge PR #562).  
**Date:** 2026-10-03  
**Do not** edit `docs/forge/00-orchestrator-status.md`. **Do not** touch branch `docs/forge-architect` (`ed427578`). **Do not** merge. **Do not** push to `main`.

The owner's instruction on 2026-10-03, to pull the next sprints from the backlog and process them, is the authorization. JDK 25 / Angular 22 remediation PRs #532–#537 are merged. GitHub had no open PRs when this plan was written. Issues #238 and #241 stay holds.

**Provenance.** Fetched 2026-10-03 from the managed Grok connectors. Linear and Notion were authenticated. Team Drgamtd-workspace, project OPEN-CRM-CPQ. DRG-336, DRG-338, DRG-339, DRG-341, DRG-337, and DRG-340 are Backlog. The only other open title match was epic DRG-291. Notion page bodies match the Linear descriptions. The Notion notes on DRG-336 and DRG-339 still say ADR-023 is Proposed.

| Issue | Notion page id |
| --- | --- |
| DRG-336 | `3e3f7cb4e4df815dbf58ceab3530934f` |
| DRG-338 | `3eaf7cb4e4df810bb8b3f86c8469da16` |
| DRG-339 | `3e3f7cb4e4df81e0a557f20a78b6543b` |
| DRG-341 | `3eaf7cb4e4df8130a393dbe5406ee4db` |
| DRG-337 | `3eaf7cb4e4df8160b30dc5c1554995d9` |
| DRG-340 | `3eaf7cb4e4df818c86bcd714082d710e` |

The improved acceptance criteria in section 3 stay. Section 8 is the story-update record. This pull request does not edit Linear or Notion.

---

## 1. Problem / evidence

A recurring quote cannot carry Year 1 at $40, Year 2 at $45, Year 3 at $50. One line has one price and one term. Pricing the whole term at the Year-1 rate understates AC-01 by 9,000 (TCV 72,000 instead of 81,000).

Re-checked at `03bb6a67` on 2026-10-03. The REQ's line numbers from `3b7c85c` still match. The corrections below are what the DAG uses.

| Claim | Where it still is | What an implementer must not miss |
| --- | --- | --- |
| No ramp flow | GitNexus query `price ramp RampStep` on index `04909db` (indexed 2026-09-13, 145 commits behind that checkout). No ramp process. The only ramp definition was `HYBRID_MIGRATION_RAMP` at `frontend/libs/shared/models/src/lib/pricing.model.ts:56` (literal also at line 31). Java enum `ProductType.java:31`. Nothing branches on it | Do not treat a zero from this index as "unused" |
| One price per line | `QuoteLineItem.java:39-77` (`quantity`, prices, one `pricing_waterfall_json`) | Child rows, not more columns on the line for each year |
| Pricing request is three fields | `QuoteService.java:611-631` sends `productExternalId`, `lineItemType`, `quantity`. Default term 12 is lines 77–78 | `PricingLineRequest.java:20-24` already allows `listPriceOverride` and `metadata`. Quote never sets them |
| Flat metrics | `MetricsCalculator.java:76-96`. ACV is `(monthly × months) / 12` at lines 93–96. Margin constant `40.0` at lines 120–121 | PRD §4.2 line 108 matches the code. Glossary line 925 says TCV / contract years. D-03 versions that gap |
| Empty waterfall | `PricingExecutionService.buildWaterfall` lines 291–309 builds `steps` and never fills it. `WaterfallStepType.java:6-16` has no period value | Fixtures `execute/01`–`04` lock `steps: []` and the whole metrics object, including ACV. `05` is an error fixture |
| Rules do not see execute facts | Facts inserted at `PricingExecutionService.java:218-220`: `Customer`, `PricingContext`, `PricingLineResult`. No `setDecimal("acv")` or `setDecimal("tcv")` under `pricing-service/src/main` | `acv-minimum-commitment.drl:19-22` and `tcv-pricing-tiers.drl` are dormant on execute. Floor tests insert a `QuoteFact` |
| Named-seat 7% | `volume-discount-named-seat.drl:14-19`. Discount is applied to the **unit** list price. The rule matches `QuoteLineItemFact`, which execute does not insert | AC-02's 7% lives in the new period pricer. Do not "fix" execute by inserting `QuoteLineItemFact` (flat fixtures would move) |
| #559 supersession | `ApprovalService.java:69` copies `requestedDiscount`. Lines 190–195 throw 409 `APPROVAL_SUPERSEDED`. The comparison is `DiscountApprovalEventConsumer`: discount, margin delta, currency. No fingerprint | R2 extends that consumer. It does not reinvent `submit()` |
| Accepted event | `QuoteAcceptedEvent` pricing snapshot and `TermSnapshot.termMonths` around lines 124–162. Registry: added fields are not a new version (`EventContractRegistry.java:29-30`; ADR-018) | Stay on v1. A second contract on the same topic fails the fitness test |
| Contract consumer | `QuoteAcceptedEventConsumer.java:65-77`: plan `"Standard Plan"`, `LocalDate.now().plusYears(1)`, `arr = totalAmount`. Does not read `termMonths` | R3. Subscriptions UI stays held. `SubscriptionDto` already exists; do not return the entity |
| Quote client Jackson | `PricingServiceClient.java:40` is `new ObjectMapper()`, which fails on unknown properties | Pricing must not emit a new response field until the quote view has that field. The request path ignores unknown JSON, so a schedule sent too early is priced flat |
| CPQ tests skipped in CI | `scripts/ci/frontend.sh:19`. `docs/ci-contract.yaml` points at the script and does not repeat the list | Add `cpq-quotes` only. Do not add every `cpq-*` project |
| Quote UI | `quotes-detail.component.ts` edits a line as product, quantity, list price. Signals, reactive forms, OnPush. No NgRx. No schedule editor. TCV and ACV are on `QuoteDto` and are not rendered. No approval banner | R1 UI is the editor. The governing-period banner is R2 |
| PDF | Quote page calls quote-service `POST .../documents/generate`. Template `quote-template.html` has no schedule. document-generation's job does not load the quote | R2 edits the quote-service template. The stub job is out |

GitNexus identity for every impact below:

- Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)
- Index commit: `04909db`, indexed 2026-09-13, reported 145 commits behind that checkout's HEAD
- Design worktree: this directory at `03bb6a67`
- Direction: upstream

| Symbol | Risk | What the text search shows |
| --- | --- | --- |
| `MetricsCalculator` | **UNKNOWN** (0 callers) | `PricingExecutionService` calls `calculate` |
| `PricingExecutionService` | **UNKNOWN** | `PricingExecutionController.execute` |
| `WaterfallStepType` (Java enum) | **UNKNOWN** | Field on `PricingWaterfallStep`. The TypeScript alias of the same name is LOW (1 direct) and is a different symbol |
| `ProductType` (pricing) | **UNKNOWN** | Stored on `PricingProduct`. Frontend label only. R4 reads it and does not edit the enum |
| `QuoteService` | **UNKNOWN** | `QuoteController` |
| `QuoteLineItemService` | **UNKNOWN** | Line controller |
| `QuoteAcceptedEventConsumer` (contract) | **UNKNOWN** | Kafka listener. Index cannot see the listener annotation as a caller |
| `DiscountApprovalEventConsumer` | **UNKNOWN** | Same listener shape. This is the #559 writer |
| `QuoteLineItem` | **MEDIUM**, lower-bound (BaseEntity, 29 interface implementations, 6 direct) | `Quote`, `QuoteService`, `QuoteLineItemService`, `QuoteMapper`, `QuoteAcceptedKafkaEventFactory` |
| `QuoteAcceptedEvent` | **MEDIUM** (6 direct, processes `accept` and `acceptQuote`) | Both consumers, the Kafka factory, `QuoteService` |
| `Subscription` | **MEDIUM**, lower-bound | Contract-asset only. R3. No subscriptions UI |
| `ApprovalService` (routing) | **LOW** (1 direct) | Stale relative to #559. The consumer is the supersession writer. Disambiguate from `com.opencrm.quote.approval.ApprovalService` |
| `EventContractRegistry` | **LOW** (3 direct) | R3 adds `RampApplied` only |
| `PricingLineRequest` | **LOW** (1 direct) | Execute path |

No result was HIGH or CRITICAL. **UNKNOWN is unresolved.** An empty caller list is not permission to delete or to skip tests. A docs-only `detect-changes` that reports no indexed symbols is not a clean graph check. The PR body quotes the command output.

---

## 2. Decisions

Taken in ADR-023. This section is the implementation shape.

| ID | Decision | Rejected |
| --- | --- | --- |
| D-01, D-02, D-04, D-05, D-08 | Unchanged from the drafted ADR | Per-period quote lines; rule-only prices; day-level periods; average or Year-1 renewal base; a new subscription service |
| D-03 | `opencrm.pricing.metrics.acv-version`: `legacy` (default) or `term-years`. Same function for flat and ramp. Pricing-service **refuses to start** if the ramp flag is true and the version is not `term-years` | Overloading `opencrm.pricing.ramp.enabled` to also change ACV. That would change the 50k/100k floor in order to turn ramp on, or would hold the correction until ramp is on |
| D-06 | Placeholder `40.0` is not a margin. Governance is per-period discount and step-down. Flag default `false`. 422 `RAMP_NOT_SUPPORTED`, never a silent flat price | Blocking the code until cost data exists. AC-16 stays out until P-2 |
| D-07 | Tier uses the period's quantity. AC-02 is final (TCV 135,600). Not 144,000 and not 133,920 | Peak; first period. Sales-ops did not confirm this independently; the owner delegated it |
| D-09 | Unchanged. DRG-337 and DRG-340 stay out | Pulling usage tiers or credits into R1 |
| Event version | Additive fields on `QuoteAccepted` v1 | A v2 on the same topic. The registry forbids it |
| Where the 7% lives | New period pricer, ramp lines only, thresholds copied from the DRL (100 → 0.07, 500 → 0.12, 1000 → 0.18) | Inserting `QuoteLineItemFact` on the flat execute path. ADR-011 forbids moving that logic into `libs/common` |
| Shared DTO | `RampStep` in `libs/common` is fields only. Each service validates for itself, against the same fixture numbers | A shared validator |
| Quote PDF | R2 adds the table to quote-service's template | Teaching the document-generation stub to call another database |
| Migration credit (R4) | One credit, 30% of book value, applied to the declining line's first period, capped at that line's TCV. Never applied to the rising SaaS line (that would cut the exit rate D-05 renews from). A hybrid ramp always routes for approval. Window length property `opencrm.pricing.ramp.migration-window-months`, default 12. Overlap longer than that is `RAMP_SCHEDULE_INVALID` | Applying 30% in every period (it would triple). Using margin `40.0` as the floor |

**Provenance.** 2026-10-03, project owner to the orchestrator: process this sprint. No finance meeting and no sales-ops meeting were held. Do not invent either in a commit message or a story.

**Flag-off flat quotes** keep today's ACV, TCV, ARR and prices. The only permitted fixture difference when `acv-version=term-years` is ACV, and the file names ADR-023.

---

## 3. Stories

The points below are the proposed story sizes in section 8. Live Linear, fetched 2026-10-03, is still DRG-336 at 8, DRG-338 with no estimate, DRG-339 at 5, and DRG-341 with no estimate. Story points are not the sum of the PR points in the DAG. The four issues stay Backlog until this docs pull request is on `main`.

### R1 — DRG-336 / BC-03-09 — 21 points (was 8)

Ramp pricing per term period. Covers FR-01 to FR-11, FR-17 and FR-18, except the governing-period banner (that sentence of FR-18 is R2).

Acceptance:

- AC-01 prices to monthly 2,000 / 2,250 / 2,500, period totals 24,000 / 27,000 / 30,000, TCV 81,000, ACV 27,000, `arr` 24,000, `exitArr` 30,000. Year-1-for-the-whole-term (TCV 72,000) fails the test.
- AC-02 uses each period's quantity. TCV 135,600, ACV 45,200, `exitArr` 66,960. The test also asserts the two rejected totals are not produced.
- AC-03 returns 422 `RAMP_SCHEDULE_INVALID` and the matching reason, and persists nothing.
- AC-04 expands 5% to unit prices 40.0000 / 42.0000 / 44.1000 and +$5 to 40 / 45 / 50. Only the steps are stored.
- AC-07: with the version at `legacy`, fixtures `execute/01`–`05` keep today's prices and ACV. `steps` may become a non-empty list (P-1) and that is the only other change. With `term-years`, a committed fixture differs in ACV only and cites ADR-023.
- AC-08: flag off, or a schedule the server will not price, is 422 `RAMP_NOT_SUPPORTED`. A response with no period results for a line that had a schedule persists no totals.
- AC-09: same schedule, price book version, rule set and context replay to the same period results.
- D-03 floors, both ways, as numbers in ADR-023. The test does not depend on the execute path inserting `QuoteFact`.
- A line with no schedule prices as today.
- UI: add, edit, remove periods; generate from an escalator; inline checks that match AC-03; preview period, months, quantity, net unit, monthly, period total, TCV, ACV, exit ARR. Signals and reactive forms. No NgRx.
- `npx nx test cpq-quotes` is run in the UI PR. It does not count in CI until the allowlist PR.

### R2 — DRG-338 / BC-04-07 — 8 points (was unset)

Ramp governance. Depends on R1-WIRE. FR-12 to FR-16, the banner sentence of FR-18, and the quote-service PDF table (FR-19). Not BC-06-03.

Acceptance:

- AC-05: discounts 32% / 10% / 0% route to `SALES_VP` citing period 1. A blended 14% does not route.
- AC-06: changing period 2 from $45 to $40, with the max discount still 32%, supersedes the open PENDING request because the fingerprint changed. A late decision returns 409 `APPROVAL_SUPERSEDED`. An identical save supersedes nothing.
- AC-15: quantity 150 then 100 is a step-down and routes, unless the product allows step-down.
- AC-17: term change to 24 months marks the schedule stale. Submit and accept are refused until the periods match and the quote is re-priced.
- An accepted quote's schedule is immutable. A change is a new quote version. `calculate` on an accepted quote does not rewrite the schedule.
- The quote-service PDF template renders the schedule table for lines that have one. document-generation is not modified.
- No assertion uses `estimatedMarginPct` of 40.0 as a pass or fail. AC-16 is not claimed.

### R3 — DRG-339 / BC-09-04 — 13 points (was 5)

Ramp schedule and step application. Depends on R2 (both edit `QuoteService`). FR-20 to FR-25.

Acceptance:

- AC-10: `QuoteAccepted` v1 carries three periods, `termMonths` 36, TCV 81,000, ACV 27,000, `exitArr` 30,000. A captured pre-change payload still parses. `EventContractFitnessTest` stays green. No v2 registration.
- AC-11: term start 2027-01-01 creates a subscription that ends after 36 months, with periods 2027-01-01–2027-12-31, 2028-01-01–2028-12-31, 2029-01-01–2029-12-31. Redelivery does not duplicate. The active rate on 2027-12-31 is period 1 and on 2028-01-01 is period 2.
- AC-12: downtime across 2028-01-01 emits exactly one `RampApplied` with effective date 2028-01-01. A second run does nothing.
- AC-13: 20 seats added at the start of month 19 add TCV 17,400, co-termed to month 36, with no day proration on that boundary.
- AC-14: 8% on the exit rate $50 is $54.00, not $46.44.
- `GetRampSchedule` returns a DTO. The subscriptions UI is not added.
- Existing subscriptions gain one implicit rate period by a non-destructive migration.
- CRM keeps copying `tcv`, `acv` and `arr` and does not gain Deal columns.

### R4 — DRG-341 / BC-09-07 — 8 points (was unset)

Hybrid migration ramp. Depends on R2. File-disjoint from R3, so it may run in parallel with R3 after R2 merges. FR-26, with the credit rule in section 2.

Acceptance:

- Two lines, one `ONPREM_*` or maintenance declining, one SaaS rising, each with its own schedule, product type `HYBRID_MIGRATION_RAMP` on the rising line.
- Book value 10,000 produces a credit of 3,000 on the declining line's first period only. A second period does not receive another 3,000. The SaaS exit unit price is unchanged by the credit.
- A credit larger than the declining line's TCV is capped. The period amount does not go negative.
- A dual-entitlement overlap of 13 months is rejected when the window max is 12. An overlap of 12 months is stored and the quote routes for approval.
- The approval reason cites the hybrid window, not margin 40.0.

---

## 4. PR DAG

Day-1 PRs share no files. Later PRs wait on the `Depends on` column. Points in this table are staffing for the PR. The Linear points are in section 3.

| PR | Owner | Effort | Risk | Depends on | Tests |
| --- | --- | --- | --- | --- | --- |
| **R1-DTO** | `libs/common` published language | 1 | LOW | — | Source scan: no arithmetic, no `validate`, no `throw` |
| **R1-WF** | pricing-service waterfall | 3 | UNKNOWN on `PricingExecutionService` | — | `execute/01`–`04` prices and ACV unchanged; `steps` filled; `05` unchanged |
| **R1-ACV** | pricing-service metrics | 5 | UNKNOWN on `MetricsCalculator` | — | Both floor cases; `legacy` default; term-years fixture differs in ACV only |
| **R1-QUOTE** | quote-service authoring | 8 | UNKNOWN on `QuoteService`; MEDIUM on `QuoteLineItem` | — | AC-03, AC-04, flag-off 422, stale refuses submit/accept, clone copies steps. View fields added, schedule not sent |
| **R1-UI** | `cpq-quotes` | 5 | LOW (new component; detail file is the embed) | — | `npx nx test cpq-quotes`. CI still skips it |
| **R1-CI** | `scripts/ci/frontend.sh` only | 2 | LOW | — | Allowlist contains `cpq-quotes`. If the existing suite is red, stop |
| **R1-ENGINE** | pricing-service period pricing | 8 | UNKNOWN on the execute path | R1-WF, R1-ACV, R1-DTO, R1-QUOTE | AC-01, AC-02, AC-08, AC-09. Startup fails if ramp is on and version is `legacy`. Flat `01`–`05` prices unchanged |
| **R1-WIRE** | quote-service calculate | 5 | UNKNOWN on `QuoteService` | R1-ENGINE, R1-QUOTE | Sends the schedule. Missing period results persist nothing. AC-01 through the quote API |
| **R2-GOV** | approval-routing + pricing policy + quote guards + UI banner + quote PDF | 8 | UNKNOWN on the discount consumer; MEDIUM on `QuoteAccepted` is not this PR | R1-WIRE, R1-UI | AC-05, AC-06, AC-15, AC-17 |
| **R3-RATE** | contract-asset + `QuoteAccepted` v1 + scheduler | 13 | MEDIUM on `QuoteAcceptedEvent` and `Subscription` | R2-GOV | AC-10, AC-11, AC-12, AC-13, AC-14. Docker tests tagged `integration` |
| **R4-HYBRID** | pricing-service migration policy | 8 | UNKNOWN on `ProductType` (read, do not edit) | R2-GOV | Credit once, cap, 12-month window, approval. Parallel with R3-RATE |

R1-ENGINE depends on R1-QUOTE even though the Java files do not overlap. `PricingServiceClient` fails if pricing emits `exitArr` or period fields before the quote view declares them. R1-QUOTE adds those optional fields and does not read them yet.

### Exclusive files

**R1-DTO** — new files only:

- `backend/libs/common/src/main/java/com/opencrm/common/pricing/RampStep.java`
- `backend/libs/common/src/main/java/com/opencrm/common/pricing/RampPriceBasis.java`
- `backend/libs/common/src/main/java/com/opencrm/common/pricing/RampEscalator.java`
- `backend/libs/common/src/test/java/com/opencrm/common/pricing/RampStepContractTest.java`

Fields on `RampStep`: `sequence`, `startMonth`, `months`, `quantity` (`Integer`), `priceBasis` (`ABSOLUTE` or `DISCOUNT_FRACTION`), `unitPrice`, `discountFraction`. Exactly one price field is set; the class does not check that. `RampEscalator`: `startPrice`, `mode` (`PERCENT` or `AMOUNT`), `amount`, `everyMonths`. No expand method. Do not edit `EventContractRegistry`, `QuoteAcceptedEvent`, or any service.

**R1-WF:**

- `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionService.java` (`buildWaterfall` only)
- `backend/services/pricing-service/src/test/resources/fixtures/pricing/execute/01-list-price-baseline.json` through `04-evaluate-mapped-happy-path.json` (`steps` only)
- a new test `.../execution/PricingWaterfallStepsTest.java`

Do not add `PERIOD_SELECTION`. Use `LIST_PRICE`, `BASE_PRICE`, `NET_PRICE`. Do not edit `MetricsCalculator`, `application.yml`, `WaterfallStepType`, or any metric number in the fixtures.

**R1-ACV:**

- `MetricsCalculator.java`
- new `AcvVersion.java` and `AcvVersionProperties.java` under `com.opencrm.pricing.execution`
- `src/main/resources/application.yml` — add only `opencrm.pricing.metrics.acv-version: legacy`
- `MetricsCalculatorTest.java`
- new `AcvVersionFloorTest.java`
- new fixtures under `src/test/resources/fixtures/pricing/acv-term-years/` that differ from `execute/01` in the `acv` field only and contain the text `ADR-023`

Do not edit `PricingExecutionService` or the `execute/01`–`05` files. Do not set the default to `term-years`. Do not add the ramp flag (R1-ENGINE owns that line).

Floor cases to assert, by inserting the ACV onto a `QuoteFact` and firing `acv-minimum-commitment.drl`:

- Monthly 5,000, 12 months. Legacy ACV 5,000 → floor 50,000. Term-years ACV 60,000 → floor 100,000.
- Monthly 10,000, 12 months. Legacy ACV 10,000 → floor 50,000. Term-years ACV 120,000 → no floor.

**R1-QUOTE** — quote-service only. Next migration is `V1_9` (latest is `V1_8`).

- New `quote_ramp_steps` table and `QuoteRampStep` entity, repository, and a quote-service DTO with the same field names as `RampStep`. Do not import `libs/common` and do not edit it. This PR is parallel with R1-DTO. R3 maps the quote rows onto the common type. The field names are the contract.
- `QuoteLineItem.java`, `QuoteLineItemService.java`, `LineItemCreateRequest.java`, `QuoteLineItemDto.java`
- `Quote.java`, `QuoteDto.java`, `QuoteService.java`, `QuoteMapper.java`
- `PricingExecuteView.java`, `PricingLineResultView.java`, `PricingFinancialMetricsView.java` — add optional `pricedPeriods` and `exitArr`. Do not require them.
- `application.yml` — `opencrm.pricing.ramp.enabled: false` and `opencrm.pricing.ramp.max-periods: 36`
- New `RampScheduleValidator` and `RampEscalatorExpander` in quote-service
- Tests next to the existing quote tests

Behaviour: flag off → 422 `RAMP_NOT_SUPPORTED`, nothing persisted. Flag on (tests only) → FR-02, escalator expansion, stale on term/currency/region change, submit and accept refused while stale, clone copies steps. `calculate` with a schedule returns 422 `RAMP_NOT_SUPPORTED` and does not call pricing. Do not put the schedule in `buildPricingContext` (R1-WIRE). Allowed lines: `billingFrequency` in `MONTHLY`, `QUARTERLY`, `SEMI_ANNUAL`, `ANNUAL`, `BIENNIAL`, and `lineItemType` not in `DISCOUNT`, `FEE`, `TAX`. Anything else is `RAMP_NOT_ALLOWED_FOR_LINE_TYPE`. Cap 36 periods.

Do not edit `approval/**`, `QuoteAcceptedKafkaEventFactory`, `document/**`, `libs/common`, pricing-service, or `frontend/`.

**R1-UI:**

- New `frontend/libs/cpq/quotes/src/lib/quote-ramp-editor/` (component, template, spec)
- `quotes-detail.component.ts` and `quotes-detail.component.spec.ts` (embed only)
- `quote.service.ts` and `quote.service.spec.ts`
- `frontend/libs/shared/models/src/lib/quote.model.ts`

Contract the mocks use:

- Line write accepts `schedule.steps[]` with the `RampStep` fields, and optional `schedule.escalator`.
- `POST /api/v1/quotes/{quoteId}/ramp-schedule/expand` with `{ durationInMonths, escalator }` returns steps and persists nothing.
- Preview reads `pricedPeriods` and quote `tcv`, `acv`, `exitArr` when present.

Signals, reactive forms, OnPush. No NgRx. No governing-period banner. Do not edit `pricing.model.ts`, catalog, price-books, approvals, or `scripts/ci/frontend.sh`.

**R1-CI:**

- `scripts/ci/frontend.sh` line 19: append `cpq-quotes` to `TARGET_PROJECTS`. Nothing else.

Run `npx nx test cpq-quotes`. If it is red before your change, stop and report. Do not delete tests. Follow `.cursor/skills/ci-contract-drift-check/SKILL.md`. The allowlist is not duplicated in Jenkins or the GitHub workflow.

**R1-ENGINE** (after the four dependencies):

- `PricingLineRequest.java` optional `schedule`
- `PricingExecutionService.java` period loop, reusing the existing price-book batch (one lookup per product per request, not per period)
- `WaterfallStepType.java` add `PERIOD_SELECTION`
- `MetricsCalculator.java` ramp branch: TCV is the sum of period amounts, ACV uses the active version, `arr` is the first period's monthly × 12, `exitArr` is the last, CLV is not the flat monthly proxy
- `FinancialMetrics.java` add `exitArr`
- `application.yml` add `opencrm.pricing.ramp.enabled: false` and `max-periods: 36`
- Startup failure if the flag is true and `acv-version` is not `term-years`
- New period pricer. Named-seat thresholds as in section 2, ramp lines only
- New fixtures for AC-01, AC-02, AC-08, AC-09

Flag off with a schedule → 422 `RAMP_NOT_SUPPORTED`. Do not change `execute/01`–`05` prices. Do not edit quote-service.

**R1-WIRE:**

- `QuoteService.buildPricingContext` and the apply-pricing method
- Mapping of `pricedPeriods` and `exitArr`
- Replace the calculate stub: call pricing, and if any sent schedule has no period results, persist no totals and return 422 `RAMP_NOT_SUPPORTED`

**R2-GOV** files, and no others: `DiscountApprovalEventConsumer`, `ApprovalRequest`, approval-routing `V1_9`, routing DTOs and mapper, `DiscountApprovalRequiredEvent` (additive fingerprint and governing period), quote-service `approval/**` and the submit/accept guards on `QuoteService`, `quotes-detail` banner, `quote-template.html`, `QuoteDocumentService`, and a new pricing class `PeriodGovernance` called from execute. Fingerprint is a hash of the normalised schedule, term and totals. Identical hash → #559 duplicate behaviour. Different hash → supersede. Do not route on margin `40.0`. Do not edit contract-asset or `QuoteAcceptedEvent`.

**R3-RATE** files, and no others: `QuoteAcceptedEvent` (additive), `QuoteAcceptedEventToleranceTest`, `EventContractRegistry` (new `RampApplied` contract only), `QuoteAcceptedKafkaEventFactory`, `QuoteService.accept`, contract-asset consumer, new rate-period entity, `V1_6`, `RampScheduler`, `GetRampSchedule` on the existing controller returning `Subscription` rate-period DTOs. Idempotent per subscription and sequence. Effective date on the event, not the run time. Do not edit pricing-service.

**R4-HYBRID** files, and no others: new classes under `pricing-service/.../ramp/migration/` and the call from `PricingExecutionService`. Read `ProductType`; do not edit the enum. Do not edit `QuoteService`, contract-asset, or `libs/common`.

### Wire numbers the tests lock

AC-01 and AC-02 are in the REQ. Escalator rounding is half-up, unit scale 4, money scale 2. Period amount = round2(net monthly × months). Line total = sum of period amounts. Quote total = sum of line totals. Totals are not rounded again.

`arr` = sum of first-period net monthly across lines × 12. `exitArr` = sum of last-period net monthly × 12. ACV uses the active version. Ramp pricing requires `term-years`, so AC-01's ACV is 27,000 and AC-02's is 45,200.

---

## 5. Blast radius

| Surface | What moves | What must stay |
| --- | --- | --- |
| Pricing execute | Waterfall steps, then per-period results, then `exitArr` | Flat prices in `execute/01`–`05` under `legacy`. Rules still do not see `QuoteLineItemFact` |
| `MetricsCalculator` | Versioned ACV; ramp branch in R1-ENGINE | Default output identical to lines 87–96 today |
| Quote calculate | Fail-closed until WIRE, then the schedule is sent | `approvals.legacy-local-read-enabled` stays `${APPROVALS_LEGACY_LOCAL_READ:true}` |
| Quote line API | Optional `schedule`. Absent means today's line | Entities stay off the wire (ADR-006) |
| Approvals | R2 fingerprint on the #559 path | Routing remains the only writer (ADR-013). Four-eyes unchanged (ADR-016) |
| `QuoteAccepted` | R3 adds fields on v1 | Three consumers keep parsing old payloads. CRM Deal columns unchanged |
| Subscription | R3 rate periods and real term | No frontend. DTO only |
| CPQ quote page | Editor, then banner | Catalog, price books, approvals inbox, NgRx |
| CI | `cpq-quotes` tests start running | Other `cpq-*` projects stay off the allowlist |
| Gateway | Nothing | No ramp logic (ADR-009) |

`QuoteService` is the file R1-QUOTE, R1-WIRE, R2 and R3 all edit, in that order. That is why they are a chain. `PricingExecutionService` is the file R1-WF, R1-ENGINE, R2 and R4 edit, in that order, except R4 may follow R2 in parallel with R3.

---

## 6. Herdr layout

Architect does not spawn implementers. Six day-1 panes, one worktree each, branched from `docs/forge-ramp-sprint`. If that branch is not on `main`, the PR base is `docs/forge-ramp-sprint` so the diff is only the pane's files.

| Pane | PR | After it merges |
| --- | --- | --- |
| 1 | R1-DTO | R1-ENGINE may import the type |
| 2 | R1-WF | R1-ENGINE |
| 3 | R1-ACV | R1-ENGINE |
| 4 | R1-QUOTE | R1-ENGINE, then R1-WIRE |
| 5 | R1-UI | R2 banner |
| 6 | R1-CI | none |

Then one pane for R1-ENGINE, one for R1-WIRE, one for R2-GOV, then two panes R3-RATE ∥ R4-HYBRID.

Java on every backend pane: `JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS`. Testcontainers only inside `newgrp docker`. Do not chmod the socket. Do not start the stack on :4200 or :8080 from these panes. Frontend pane: `npm ci --legacy-peer-deps` in that worktree, `NX_DAEMON=false`.

Worker prompts for the six unblocked PRs are in `/tmp/herdr-ramp/architect-verdict.md`.

---

## 7. Out of scope

- Enabling `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version=term-years` in committed config
- Any `opencrm.jev.*.enabled`, Jev/OpenRouter as runtime, GLM-5, Track 2 Spring AI
- Activities API, CI #238, agent Kafka #241, dual-read flip, NgRx, subscriptions UI, full SpEL, Kafka S2B-9, gateway domain validation, deleting product `POST /validate`, WhatsApp #430, price-book list-row delete
- `SECURITY_OPEN`, Cookie CSRF on domain services
- DRG-337 usage tiers, DRG-340 credit draw-down
- P-2 real cost, AC-16
- document-generation's stub PDF job (BC-06-03)
- PRD §4.2 and glossary edits (do them when the version is enabled, not while the default is `legacy`)
- Billing, revenue recognition, agent consumption of the schedule
- Logic in `libs/common`, cross-schema SQL, entities on `/api/v1/**`

---

## 8. Story updates

Fetched 2026-10-03. The live descriptions below are still the tracker text. Each replacement is the description to write to that Linear issue and to the matching Notion page when this docs pull request merges to `main`. Leave every title as it is. Leave every status **Backlog**. Do not mark the Linear issue or the Notion page Accepted before this pull request is on `main`. ADR-023 in this branch stays Accepted. The decision is recorded in this sprint doc and lands when the docs pull request merges.

DRG-337 and DRG-340 are not edited.

### DRG-336 — live text should change

Live: Backlog, estimate 8. Acceptance criteria are the REQ lines (AC-01, AC-03, AC-08, AC-07, AC-04, AC-17, AC-09, AC-02) with no AC-02 totals. The estimate note says the 8 points were carried over and not revalidated. Notion page `3e3f7cb4e4df815dbf58ceab3530934f` matches that description, and its note says ADR-023 is Proposed.

Change the estimate from 8 to 21. Replace the description with the markdown below. The 21 points replace the carried-over 8.

**Replacement description:**

~~~markdown
**As a** pricing admin, **I want** ramp schedules (per-period price and quantity) validated and priced through the waterfall for each term period, **so that** multi-year subscription offers are quoted at the price the customer will pay in each period.

**Acceptance criteria**

* Given 50 named seats at list $50 per seat per month over 36 months with periods at $40, $45 and $50, when the quote is priced, then each period returns a PriceElement (monthly 2,000 / 2,250 / 2,500; period totals 24,000 / 27,000 / 30,000), TCV is 81,000, ACV 27,000, `arr` 24,000 and `exitArr` 30,000. Pricing the whole term at the Year-1 rate (TCV 72,000) fails (REQ AC-01).
* Given list $40 and quantities 50, 100 and 150 for 12 months each, when tiers use that period's own quantity and the named-seat 7% starts at 100, then unit prices are 40 / 37.20 / 37.20, monthly amounts are 2,000 / 3,720 / 5,580, period totals are 24,000 / 44,640 / 66,960, TCV is 135,600, ACV is 45,200 and `exitArr` is 66,960. First-period quantity for every period (TCV 144,000) and peak quantity for every period (TCV 133,920) both fail (AC-02, decision D-07).
* Given a schedule with a gap, an overlap, periods that do not sum to the term, a zero-month period, a negative price or a non-recurring line, then the request is rejected with 422 RAMP_SCHEDULE_INVALID and a reason code, and nothing is persisted (AC-03).
* Given an escalator of 5% per anniversary from $40, then unit prices expand to 40.0000 / 42.0000 / 44.1000. Given +$5, then they expand to 40 / 45 / 50. Only the explicit steps are stored (AC-04). Given a term change, then the schedule is marked stale until it is re-validated and re-priced (AC-17).
* Given the ramp flag is off, or pricing-service will not price the schedule, when a schedule is sent, then 422 RAMP_NOT_SUPPORTED is returned. A ramp is never priced as flat. A missing per-period result persists no totals (AC-08).
* Given a quote with no ramp, and `opencrm.pricing.metrics.acv-version=legacy`, then prices, waterfall totals and metrics stay numerically identical to today. Waterfall `steps` may become a non-empty list. That is the only other change (AC-07).
* Given recorded inputs, the same price book version, rule set and context, then per-period results and the fingerprint replay identically, and the waterfall lists ordered steps for every period (AC-09).
* Given monthly 5,000 for 12 months, then legacy ACV 5,000 floors to 50,000 and term-years ACV 60,000 floors to 100,000. Given monthly 10,000 for 12 months, then legacy ACV 10,000 floors to 50,000 and term-years ACV 120,000 has no floor (ADR-023 D-03). The floor test inserts the ACV. It does not require the execute path to insert QuoteFact.

**Realizes:** RampStep · PriceElement · PriceLines · ExplainWaterfall

**Scope:** REQ slice R1 (FR-01 to FR-11, FR-17, FR-18), except the governing-period banner, which is DRG-338. Feature flag `opencrm.pricing.ramp.enabled` defaults to off on pricing-service and quote-service. Not generally available until DRG-338 ships. ACV uses `opencrm.pricing.metrics.acv-version`: `legacy` (default, today's `(monthly × months) / 12`) or `term-years` (TCV / term-years). Flat quotes keep today's numbers until that version is set. Pricing-service refuses to start if the ramp flag is true and the version is not `term-years`. The two switches stay separate.

**Split:** usage tiers stay on DRG-337. This story does not edit DRG-337.

**Depends on:** ADR-023, recorded Accepted in this branch. The Accepted status lands in the catalog when the docs pull request merges to main. Until that merge, this issue stays Backlog. Do not mark this Linear issue or Notion page `3e3f7cb4e4df815dbf58ceab3530934f` Accepted before that pull request is on main. Prerequisites P-1, P-3, P-4 and P-5 are in the sprint plan.

**Estimate:** 21. The carried-over 8 is withdrawn. It was not revalidated.

**Decision record:** `docs/forge/41-price-ramp-sprint.md` and ADR-023. This text lands when the docs pull request merges.
~~~

### DRG-338 — live text should change

Live: Backlog, no estimate. Acceptance criteria are AC-05, AC-06, AC-15, AC-16 (once P-2) and FR-16. The scope sentence says FR-19 (the schedule table on the quote PDF) is carried by BC-06-03. Notion page `3eaf7cb4e4df810bb8b3f86c8469da16` matches that description.

Change the estimate from none to 8. Replace the description with the markdown below. FR-19 for this sprint is the quote-service template. The document-generation stub (BC-06-03) stays out.

**Replacement description:**

~~~markdown
**As a** deal desk approver, **I want** discount and margin evaluated for every ramp period and the approval bound to the exact schedule, **so that** a back-loaded discount cannot be approved as if it were flat, and a changed schedule cannot ride on an old approval.

**Acceptance criteria**

* Given list $50 with periods at $34, $45 and $50 (discounts 32%, 10%, 0%; blended 14%) and a 25% discount threshold, when the quote is evaluated, then it routes to SALES_VP citing period 1. A blended-only evaluation would not route it (REQ AC-05).
* Given an open PENDING approval for that schedule and a rep changes period 2 from $45 to $40, then the maximum discount is unchanged but the fingerprint changes, the open request becomes SUPERSEDED, a new request is created, and a late decision on the old one returns 409 APPROVAL_SUPERSEDED. Re-saving an identical schedule supersedes nothing (AC-06).
* Given quantity steps from 150 to 100 between periods, then the schedule is flagged as a step-down and routes for approval unless the product allows it (AC-15).
* Given a term change to 24 months, then the schedule is marked stale. Submit and accept are refused until the periods match and the quote is re-priced (AC-17).
* Given an accepted quote, then its schedule is immutable and a change creates a new quote version. `calculate` on an accepted quote does not rewrite the schedule (FR-16).
* The quote page names the governing period. The quote-service PDF template renders the schedule table for lines that have one. document-generation is not modified, and BC-06-03 does not carry this table (FR-19 for this sprint).
* No assertion uses `estimatedMarginPct` of 40.0 as a pass or a fail. AC-16 stays out until real cost data (P-2) exists. The placeholder 40.0 is not a margin (decision D-06).

**Realizes:** EvaluateQuote · ExplainPolicyBreach · SubmitForApproval

**Scope:** REQ slice R2 (FR-12 to FR-16), the banner sentence of FR-18, and the quote-service PDF table (FR-19). Approval-routing stays the single canonical writer (ADR-013). The fingerprint extends the discount comparison added in #559. Ramp is not generally available. The flag stays default off.

**Blocked by:** DRG-336. This issue stays Backlog until the docs pull request merges to main. Do not mark this Linear issue or Notion page `3eaf7cb4e4df810bb8b3f86c8469da16` Accepted before that pull request is on main.

**Estimate:** 8. There was no estimate on the live issue.

**Decision record:** `docs/forge/41-price-ramp-sprint.md`. This text lands when the docs pull request merges.
~~~

### DRG-339 — live text should change

Live: Backlog, estimate 5. Acceptance criteria AC-10 through AC-14 already carry the worked numbers (TCV 81,000, ACV 27,000, exitArr 30,000, the 2027–2029 period dates, one RampApplied, added TCV 17,400, renewal $54.00). The estimate note says the 5 points were carried over and not revalidated. Notion page `3e3f7cb4e4df81e0a557f20a78b6543b` matches that description, and its note says ADR-023 is Proposed. The live blocker is DRG-336 only.

Change the estimate from 5 to 13. Replace the description with the markdown below. The worked AC-10 through AC-14 numbers stay. The dependency becomes DRG-338, because R3 and R2 both edit `QuoteService`. The ADR note changes from Proposed to the decision recorded on this branch.

**Replacement description:**

~~~markdown
**As a** subscription manager, **I want** each ramp period stored on the subscription and applied on its effective date, **so that** the customer is billed the contracted rate for every period without manual intervention.

**Acceptance criteria**

* Given the 36-month ramp quote from DRG-336 is accepted, then QuoteAccepted version 1 carries three ramp periods, termMonths 36, TCV 81,000, ACV 27,000 and exitArr 30,000. A captured pre-change payload still parses in every consumer. The registry fitness check passes. There is no QuoteAccepted v2 (REQ AC-10, ADR-018).
* Given term start 2027-01-01, then the subscription ends after 36 months (not one year) with three rate periods, 2027-01-01 to 2027-12-31, 2028-01-01 to 2028-12-31 and 2029-01-01 to 2029-12-31. Redelivery creates no duplicate periods. The active rate on 2027-12-31 is period 1 and on 2028-01-01 is period 2 (AC-11). The boundary date belongs to the new period (decision D-04).
* Given the scheduler is down across 2028-01-01 for two days, then after restart exactly one RampApplied is emitted with effective date 2028-01-01. A second run or duplicate delivery has no further effect. The lag metric rises then clears (AC-12).
* Given 20 seats added at the start of month 19, then added TCV is 17,400, co-termed at month 36, with no day-based proration at a whole-month boundary (AC-13).
* Given a renewal of that subscription with an 8% uplift, then the renewal price is $54.00, based on the exit-period rate of $50, not $46.44 (AC-14, decision D-05).
* GetRampSchedule returns a DTO. The subscriptions UI stays held. Existing subscriptions gain one implicit rate period by a non-destructive migration. CRM keeps copying tcv, acv and arr and does not gain Deal columns.

**Realizes:** ApplyRamp · RampApplied · GetRampSchedule

**Scope:** REQ slice R3 (FR-20 to FR-25). The rate schedule is hosted in contract-asset-management (ADR-023, decision D-08). Events are additive on QuoteAccepted version 1 through the ADR-018 registry. ApplyRamp is idempotent per subscription and sequence (ADR-018 D6). ADR-023 is Accepted in the docs branch. That catalog status lands when the docs pull request merges to main. Until then this issue stays Backlog, and the Notion note that says Proposed is replaced by this paragraph. Do not mark this Linear issue or Notion page `3e3f7cb4e4df81e0a557f20a78b6543b` Accepted before that pull request is on main.

**Split:** credit draw-down stays on DRG-340. This story does not edit DRG-340.

**Blocked by:** DRG-338 (R3 and R2 both edit QuoteService). DRG-336 is already required by DRG-338. BC-09-05 (renewal) follows decision D-05.

**Estimate:** 13. The carried-over 5 is withdrawn. It was not revalidated. The slice spans the event, the consumer, the scheduler and the migration.

**Decision record:** `docs/forge/41-price-ramp-sprint.md` and ADR-023. This text lands when the docs pull request merges.
~~~

### DRG-341 — live text should change

Live: Backlog, no estimate. The acceptance text says the design is deferred and that how the migration credit is applied across periods is decided at slice design, before the story is estimated. Notion page `3eaf7cb4e4df8130a393dbe5406ee4db` matches that description. The live blockers are DRG-339, DRG-336, and DRG-338.

Change the estimate from none to 8. Replace the description with the markdown below. The migration credit is decided: 30% of book value, once, on the declining line's first period, capped at that line's TCV, never on the rising SaaS line. After DRG-338 merges, this story is file-disjoint from DRG-339 and may run in parallel with it.

**Replacement description:**

~~~markdown
**As a** sales lead selling an on-premises to SaaS migration, **I want** two coordinated recurring lines, one declining and one rising, each with its own ramp inside a dual-entitlement window, **so that** the customer moves platforms without paying twice at full price or losing entitlement mid-migration.

**Acceptance criteria**

* Given one declining on-premises or maintenance line and one rising SaaS line, each with its own ramp, and product type HYBRID_MIGRATION_RAMP on the rising line, then both lines are priced per period through the DRG-336 waterfall.
* Given perpetual book value 10,000, then the migration credit is 3,000 (30% of book value, PRD §5.3), applied once to the declining line's first period. A later period does not receive another 3,000. The SaaS exit unit price is unchanged by the credit, so the exit rate that decision D-05 renews from stays intact.
* Given a credit larger than the declining line's TCV, then the credit is capped at that TCV and the period amount does not go negative.
* Given `opencrm.pricing.ramp.migration-window-months` default 12, then an overlap of 13 months is rejected with RAMP_SCHEDULE_INVALID, and an overlap of 12 months is stored.
* Given a stored hybrid ramp, then the quote routes for approval because it is a hybrid ramp. The approval reason cites the hybrid window. It does not cite margin 40.0 (decision D-06).
* Given a step-down in the declining line inside this window, then it is accepted as a migration ramp and is not flagged as an unapproved step-down (REQ FR-14 exception).

**Realizes:** RampStep · PriceElement (via DRG-336). HYBRID_MIGRATION_RAMP is the marker. The ProductType enum is read and not edited.

**Scope:** REQ slice R4 (FR-26). Source: PRD §5.3, "Cloud Migration Ramp + Dual Entitlement Window". The credit rule above is the slice design. It replaces the live sentence that left the credit undecided.

**Blocked by:** DRG-338. After that story merges, this story is file-disjoint from DRG-339 and may run in parallel with it. This issue stays Backlog until the docs pull request merges to main. Do not mark this Linear issue or Notion page `3eaf7cb4e4df8130a393dbe5406ee4db` Accepted before that pull request is on main.

**Estimate:** 8. The live issue had no estimate because the credit rule was open. The rule is now recorded.

**Decision record:** `docs/forge/41-price-ramp-sprint.md` and ADR-023. This text lands when the docs pull request merges.
~~~

### DRG-337 and DRG-340 — not edited

DRG-337 (BC-03-12, usage tier rating, Notion page `3eaf7cb4e4df8160b30dc5c1554995d9`) and DRG-340 (BC-09-06, credit draw-down, Notion page `3eaf7cb4e4df818c86bcd714082d710e`) stay out of this sprint. Their live descriptions, estimates (none), and Backlog status are left as fetched. D-09 split them out, and that split stands. No replacement description is written for either issue.
