# DRG-531 — Term-start cost dating readiness

DRG-531 is not independently implementable on the inspected checkout: its effective-dated cost lookup does not exist here. Deliver the DRG-526 cost-basis and DRG-438 margin foundation on the selected base first, then implement the additive context field and date selection together. Adding an unused field would not satisfy the story's cost-dating acceptance criterion.

## Sources and scope

Live Linear was read on 2026-11-05. [DRG-531](https://linear.app/drgamtd-workspace/issue/DRG-531) is Backlog, three points, with no explicit blocking relations. Its requirements are an optional term-start field in the quote-to-pricing context, actual term-start dating when present, unchanged pricing-date fallback when absent, and compatibility with existing request consumers. Its example requires a quote starting 2027-01-01 to select period 2 cost as of 2028-01-01.

[DRG-526](https://linear.app/drgamtd-workspace/issue/DRG-526) and [DRG-438](https://linear.app/drgamtd-workspace/issue/DRG-438) are both In Progress. DRG-526 owns effective-dated ERP-export cost ingestion, period-start lookup, recorded cost evidence and replay. DRG-438's current brief limits its margin wave to the cost-basis slice and leaves guidance bands in backlog. Its referenced `docs/plans/margin-wave-20261015.md` is absent from this tree. Tracker status is not evidence that either implementation has landed.

The prior [next-wave audit](cpq-next-wave-readiness-20261101.md) independently recorded the missing cost-ingestion foundation. This audit confirms the dependency on the current tree rather than copying that conclusion. The [Notion hub](https://app.notion.com/p/3e3f7cb4e4df8197abf7d804245c5df4) is a design/status mirror; live Linear remains the delivery authority under the existing [delivery plan](cpq-parallel-followups-20261101.md).

No production or test code is changed by this readiness lane. No tracker status, default flag, service, real-data operation or delivery gate is changed.

## Checkout and verified implementation

Inspection binds to `C:/Users/dgorn/open-crm-cpq`, source HEAD `7492fd09`, on delivery branch `feat/parallel-story-wave-20261105`. The local GitNexus index was refreshed against that HEAD before exploration. MCP and port 4200 refer to another checkout and were not used.

| Evidence | Observed behavior |
| --- | --- |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/Quote.java` | The existing authoritative quote date is `LocalDate startDate`; no new quote column is required merely to transport it. |
| `QuoteService.buildPricingContext` in the same package | Builds a map containing customer, quote, lines, region, currency, duration, subscription type and optional deal-type metadata; it omits `startDate`. Graph callers are `calculate` and `priceInitialInterval`. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingContext.java` | Request DTO has no term-start date. Its graph references include execution controller, validator and bulk simulation request. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/ramp/RampPeriodPricer.java` | `price(line, listPrice, durationInMonths, maxPeriods)` handles month-indexed prices and quantities. It has no date, cost repository or cost resolver dependency. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/ramp/PricedPeriod.java` | Holds month indexes, priced amounts and waterfall steps; no selected cost or cost-effective date. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/MetricsCalculator.java` | Flat and ramp paths still emit the non-authoritative `40.0` estimated-margin placeholder. |

Graph searches for `CostImport` resolved only the earlier readiness document; the `margin` query found AI-agent prompts rather than a pricing cost service. Exact symbol contexts for `RampPeriodPricer`, `MetricsCalculator`, `PricingContext` and `buildPricingContext` supplied the implementation boundaries. Confirming source searches found no `CostImport`, `CostBasis`, `unitCost`, `costBasis`, `margin.source`, `effectiveFrom`, `pricingDate` or `termStart` in pricing-service source, apart from the separately reported margin placeholder matches. Existing ruleset effective dates and renewal-policy dates are different contracts, not a cost lookup seam.

Bounded upstream impact was run before writing this brief:

| Target and bound | Callers / consumers reported | Risk |
| --- | --- | --- |
| `buildPricingContext`, depth 2 | `calculate`, `priceInitialInterval`, then `QuoteController.calculate`; 3 impacted symbols, no indexed processes | LOW |
| `RampPeriodPricer`, depth 1 | `PricingExecutionService` class and import file; 2 impacted entries, no indexed processes | LOW |
| Pricing-service `PricingContext`, depth 1 | `PricingExecutionController`, `PricingContextValidator`, `BulkPricingSimulationRequest`; 3 import files, no indexed processes | LOW |

These are bounded preparation results, not a complete future code-change certification. The producer uses a map and generated DTO accessors; text and contract tests must cover consumers that graph calls do not resolve. Fresh impact analysis is required on the foundation's landed symbols before implementation. No HIGH/CRITICAL, partial or truncated flags were returned by these bounded impact calls. An initial multiword query failed argument parsing and was replaced with successful single-concept queries; that failed query supplies no evidence.

## Proposed implementation contract after the foundation lands

This is a delivery brief, not a new Finance decision or an authorization to revive old margin branches.

1. Add nullable ISO calendar-date `termStart` to the pricing request context, populated from the existing `Quote.startDate`. Keep the property additive and optional. Do not rename quote persistence, introduce a database migration, move pricing logic into the gateway or put cost selection in a shared library.
2. Resolve the cost-period anchor once per pricing execution: supplied `termStart` wins; absence uses the DRG-526 foundation's established pricing date. Inspect and preserve that fallback's clock/time-zone semantics after it lands. Do not add a second independently computed current date or silently use ruleset/renewal dates.
3. Resolve each period's cost on `anchor.plusMonths(startMonth - 1)`, anchored to the original date each time. Preserve calendar-date semantics and the ADR-023 anniversary rule. Do not derive the next boundary from a previously clamped month.
4. Pass the selected date into the actual DRG-526 cost resolver. Preserve its product, region, currency, effective-version, missing/stale-cost and replay contracts. Record the selected cost/version/date through that foundation's existing trace. Changing the quote term anchor must follow its existing pricing/snapshot invalidation rules.
5. Keep no-date request behavior and arithmetic unchanged. Verify the actual Jackson configuration used at HTTP boundaries and any frontend/direct pricing callers; an additive Java field alone does not prove rolling compatibility. Retain the existing malformed-date rejection behavior.

Ownership should remain with one quote/pricing transport-and-cost-dating lead. Candidate files are `QuoteService`, pricing `PricingContext`, the foundation's cost-selection orchestration, and their focused contract tests. Exact cost classes cannot be named honestly until that foundation is present. Coordinate any pricing TypeScript interface updates with the frontend owner; this story does not require a new UI control.

## Acceptance-to-verification plan

| Check | Testable evidence required before code completion |
| --- | --- |
| Producer forwarding | Capture the actual quote-to-pricing request from both `calculate` and the initial-interval path; a quote with `startDate=2027-01-01` carries `termStart=2027-01-01`. A null date retains the accepted legacy representation. |
| Actual cost-date selection | Fixed pricing date different from term start; synthetic approved cost changes on 2028-01-01; a 24-month quote starting 2027-01-01 selects period 2 cost at 2028-01-01. Assert selected version, margin and trace, not just a helper's computed date. |
| Legacy fallback | The equivalent request omitting `termStart` selects exactly the foundation's pre-change pricing-date costs and preserves the golden numeric result. Cover explicit null according to the HTTP mapper's established contract. |
| Anniversary boundaries | January 31 original anchor produces February 28 and March 31 in a non-leap year; leap-year cases and a short final period use the period's own anchored start. |
| Consumer tolerance | Existing captured requests still deserialize; actual request consumers accept the additive field; malformed date still fails with the established response. Cover direct/bulk simulation and map/JSON consumers that graph imports cannot prove. |
| Cost safety and replay | Missing/stale costs remain fail-closed under the foundation's policy; replay retains original captured cost and dates after a newer cost is ingested; no default flag changes. |

First reproduce the dated-cost failure against the landed foundation, then make the bounded change and run quote/pricing unit and contract coverage. Run the foundation's Docker/PostgreSQL integration tests when persistence/replay is involved. Serialize heavyweight builds with the orchestrator. Finish with a complete local `detect-changes` result on the edited checkout and the existing exact-head CI and designated review gates.

## Dispatch outcome and remaining gates

This lane delivered a verified dependency brief rather than shipping unused transport plumbing. DRG-531 remains unimplemented; unblock it by landing and verifying DRG-526/438 on the selected base, or explicitly rescope the story in Linear as a transport-only prerequisite with different acceptance. Do not mark the current cost-dating story Done for a transport-only result.

No tests, builds, Docker integration or browser UAT ran for this documentation-only lane. Integration tests require Docker; a Docker-less unit run would not execute them. Browser/chaos scripts are outside CI. Whole-frontend CI covers all Nx projects but has no typecheck target, and PR-check has no frontend stage. The existing CPQ Decision/exact-head CI/owner merge gates and all feature/asset holds remain in force. AutoJev tools were unavailable; no advisory verdict is claimed.
