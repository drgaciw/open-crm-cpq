# Requirements: Price ramping (multi-period pricing within a term)

**ID:** REQ-PRC-001 (provisional — only REQ-AI-014 exists, so no numbering scheme is documented yet)
**Status:** Accepted for implementation on 2026-10-03, by [ADR-023](../adr/ADR-023-price-ramp-schedule-model.md). Nothing in this document is implemented in code yet. D-01, D-02, D-04, D-05 and D-08 stay as drafted. D-09 (split the two stories) was instructed by the owner on 2026-09-29 and stays applied. D-03, D-06 and D-07 were taken on 2026-10-03 in the same review, on the owner's instruction to process this sprint. There was no separate finance meeting and no separate sales-ops meeting. The sprint plan is [docs/forge/41-price-ramp-sprint.md](../forge/41-price-ramp-sprint.md).
**Created:** 2026-09-29. Decisions closed 2026-10-03.
**Architecture:** [ADR-023 Price ramp schedule model](../adr/ADR-023-price-ramp-schedule-model.md) (**Accepted** 2026-10-03) records D-01 through D-08. R1 starts only after that Acceptance is on `main`. The ramp flag stays default off.
**Backlog:** Stories allocated on 2026-09-29; each Notion page links its Linear issue. R1: [BC-03-09](https://app.notion.com/p/3e3f7cb4e4df815dbf58ceab3530934f), [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336/bc-03-09-ramp-pricing-per-term-period). R2: [BC-04-07](https://app.notion.com/p/3eaf7cb4e4df810bb8b3f86c8469da16), [DRG-338](https://linear.app/drgamtd-workspace/issue/DRG-338/bc-04-07-ramp-governance-per-period-policy-and-approval-binding). R3: [BC-09-04](https://app.notion.com/p/3e3f7cb4e4df81e0a557f20a78b6543b), [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339/bc-09-04-ramp-schedule-and-step-application). R4: [BC-09-07](https://app.notion.com/p/3eaf7cb4e4df8130a393dbe5406ee4db), [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341/bc-09-07-hybrid-migration-ramp-with-dual-entitlement-window). Split out under D-09: [BC-03-12](https://app.notion.com/p/3eaf7cb4e4df8160b30dc5c1554995d9), [DRG-337](https://linear.app/drgamtd-workspace/issue/DRG-337/bc-03-12-usage-tier-rating-for-quoting) (usage tiers) and [BC-09-06](https://app.notion.com/p/3eaf7cb4e4df818c86bcd714082d710e), [DRG-340](https://linear.app/drgamtd-workspace/issue/DRG-340/bc-09-06-credit-draw-down-for-prepaid-balances) (credit draw-down). Epics DRG-283 (BC-03), DRG-286 (BC-04) and DRG-291 (BC-09).

## Outcome

A quote for a recurring offer can carry a contractually committed schedule of prices and/or quantities across the term (for example Year 1 at $40, Year 2 at $45, Year 3 at $50 per seat per month). The platform prices each period through the normal waterfall, governs the deal on its worst period, states TCV, ACV and ARR truthfully for a ramp, freezes the schedule at acceptance, and carries it into the subscription so the active rate changes on the step date as sold.

Today none of this exists: a quote has one price and one term, and a ramp can only be approximated by mispricing the whole term at a single rate. See [Current state](#current-state-evidence).

## Terms

| Term | Meaning in this document |
|---|---|
| Ramp | A committed, ordered schedule of per-period prices and/or quantities inside one term. |
| Period (`RampStep`) | A contiguous block of whole months with one quantity and one price basis. Uses the Capability Map name for the value object. |
| Escalator | A generator (start price plus a percentage or amount per anniversary) that expands into explicit periods. It is an authoring aid, not a second stored model. |
| Not a ramp | Renewal uplift (across terms), amendment proration (mid-term delta), usage tiers (consumption-based rates), prepaid credit draw-down, one-off migration credit. These are related and interact with a ramp, but are separate. |

## Current state (evidence)

Evidence from `origin/main` at `3b7c85c`, checked on 2026-09-29. Re-checked at `03bb6a67` on 2026-10-03: the line numbers in this table still match. Two facts that the original pass understated are recorded under [Re-check](#re-check-2026-10-03) and override the sentence they correct.

| Area | Finding | Evidence |
|---|---|---|
| Ramp logic | None. The only trace is the `HYBRID_MIGRATION_RAMP` product-type label; nothing branches on it. | `backend/services/pricing-service/.../product/ProductType.java:31`, `frontend/libs/shared/models/src/lib/pricing.model.ts:31,56` |
| Term and price model | One `durationInMonths` and one price per line. The quote-to-pricing request sends only product, line type and quantity per line, so no per-line data can reach pricing today. | `PricingContext`, `PricingLineRequest`, `QuoteService.java:611-631` |
| Metrics basis | ARR, TCV, ACV and CLV are derived from one flat `monthlyPrice` (sum of line final prices). | `MetricsCalculator.java:74-102`, PRD §4.2 |
| ACV definition conflict | The formula is `(monthlyPrice × months) / 12` (PRD line 108, `MetricsCalculator.java:94-96`), but the PRD glossary (line 925) defines ACV as TCV divided by contract years. A flat 12-month contract at $4,000 per month yields ACV 4,000 under the code, and 48,000 under the glossary. | as cited |
| Margin | `estimatedMarginPct` is a constant 40.0 (comment: cost data, matrix M5 deferred) while the margin rule compares against 0.35, a fraction. Margin governance is not driven by real cost today. | `MetricsCalculator.java:120-121`, `approval-margin-exception.drl:10`, PRD §7.5 |
| Waterfall trace | `buildWaterfall` returns an empty `steps` list, and `WaterfallStepType` has no period or ramp step. | `PricingExecutionService.java:291-309`, `WaterfallStepType.java` |
| Rule ordering | Rules run before metrics are computed. The TCV-tier and ACV-floor rules read `tcv` and `acv` from fact metadata; I found no Java that sets those keys, so these two rules appear dormant on the `execute` path. How the rule corpus's `QuoteFact` model is bridged from the facts inserted at `PricingExecutionService.java:218-220` was not traced. | `PricingExecutionService.java:198-241`, `tcv-pricing-tiers.drl:13-14`, `acv-minimum-commitment.drl:13-14` |
| Approvals | The approval request carries `requestedDiscount`. Supersession on changed discount figures shipped in #559. Nothing represents per-period figures. | approval-routing `ApprovalService.java:69,194` |
| Accepted-quote event and contract | `QuoteAccepted` carries `termMonths`, start/end and ARR/TCV/ACV totals. The contract consumer builds a Subscription with a hard-coded one-year term, plan "Standard Plan" and `arr = totalAmount`, ignoring the term. | `QuoteAcceptedEvent.java:124-162`, contract-asset-management `QuoteAcceptedEventConsumer.java:65-77` |
| Adjacent rules | Renewal uplift (8% or 3%), amendment proration (days), migration credit (30% of book value). None is a ramp. | `renewal-uplift.drl`, `amendment-proration.drl`, `migration-credit.drl` |
| Agent spec | `agents/term-coterming.md` and `agents/cpq-agent-020.md` describe a ramp preview and mid-ramp proration; the agent code has no ramp logic. | `TermCotermingAgent.java` |

## Re-check 2026-10-03

At `03bb6a67` the table above still holds, with these corrections. The sprint DAG uses the corrections.

- **Supersession (#559) compares amounts, not a fingerprint.** `ApprovalService.java:69` still copies `requestedDiscount` on `submit`, and lines 190–195 still throw 409 `APPROVAL_SUPERSEDED`. The comparison lives in `DiscountApprovalEventConsumer`: a PENDING discount row for the same quote line is identical when `requestedDiscount`, `marginDelta` and `currencyCode` match. No fingerprint field exists.
- **The quote pricing client fails on unknown response fields.** `PricingServiceClient.java:40` builds `new ObjectMapper()`. An extra property on the pricing response breaks every calculate call. The request path is the opposite: pricing-service uses Spring MVC, which ignores unknown JSON, so a schedule on the request is dropped and the line is priced flat. FR-09 is written for that pair of facts.
- **Fixtures `execute/01` to `04` lock the whole metrics object, including ACV, and lock `steps` to `[]`.** `05` is an error fixture. P-1 may change `steps` only. The ACV version may change ACV only, and only in a fixture that names ADR-023. Prices stay.
- **Execute does not insert `QuoteFact` and does not set `tcv` or `acv`.** Facts at `PricingExecutionService.java:218-220` are `Customer`, `PricingContext` and `PricingLineResult`. The 50k/100k rule is tested by inserting `acv` on a `QuoteFact` (`PricingDrulesGoldenFixtureTest`). Wiring that fact into execute is R2, not the ACV PR.
- **`QuoteAccepted` stays on event version 1.** ADR-018 and `EventContractRegistry.java:29-30` treat added fields as non-breaking, and the registry keeps one contract per topic. FR-20 does not bump the version.
- **The PDF a user can generate is quote-service's template.** document-generation's consumer creates a job and does not load the quote. FR-19 for this sprint is the quote-service template, in R2. The stub job stays out.

## Functional requirements

### Authoring and validation

| ID | Requirement |
|---|---|
| FR-01 | A recurring (subscription-type) quote line may carry an ordered ramp schedule of periods. Each period has a sequence, a start month (1-based), a length in whole months, a quantity, and one price basis: an absolute unit price, or a discount fraction off list. A line without a schedule is a single implicit period covering the term and behaves exactly as today. One-time, perpetual, fee and tax lines reject a schedule. |
| FR-02 | Validate every schedule at write time and again before pricing: the first period starts at month 1, periods are contiguous with no gaps or overlaps, the sum of period months equals the quote `durationInMonths`, each period has at least 1 month, prices and quantities are not negative, at least one period is billable, and the period count does not exceed a configured maximum. Violations return 422 `RAMP_SCHEDULE_INVALID` with machine-readable reasons (`RAMP_GAP`, `RAMP_OVERLAP`, `RAMP_TERM_MISMATCH`, `RAMP_PERIOD_EMPTY`, `RAMP_NEGATIVE_VALUE`, `RAMP_NOT_ALLOWED_FOR_LINE_TYPE`). |
| FR-03 | An escalator (start price plus a percentage or amount per anniversary or per configured period length) expands server-side into explicit periods. Only explicit periods are stored and priced; the generator inputs are kept for display only. Expansion is deterministic and follows FR-08 rounding. |
| FR-04 | Changing the term, currency or region marks the schedule stale. A quote with a stale schedule cannot be priced-as-current, submitted for approval or accepted until it is re-validated and re-priced. |

### Pricing

| ID | Requirement |
|---|---|
| FR-05 | Pricing-service prices each period independently through the waterfall. List price comes from the price book once per product per request (not once per period). Absolute-price periods use the negotiated price as the base, subject to floors and approval. Discount-basis periods apply the fraction to list. Volume tiers, surcharges, partner adjustments, FX and tax evaluate with that period's quantity and the term context. The output for each period is a `PriceElement`: net unit price, net monthly amount, period amount, discount rate against list, explanation codes and waterfall steps. |
| FR-06 | The waterfall returned by `execute` contains ordered steps for every period (today the list is empty), including a new period-selection step type. `ExplainWaterfall` for a quote line explains each period. |
| FR-07 | Pricing stays deterministic: identical inputs (schedule, price book version, rule-set, context) replay to identical per-period results. The pricing audit log records the schedule and the per-period output, and the response records the price book version and rule-set used. |
| FR-08 | All arithmetic is `BigDecimal`. Unit prices carry 4 decimal places and amounts 2, rounded half-up (the current behaviour in `MetricsCalculator` and `QuoteService`). Period amount = round(net monthly × months). Line total = sum of period amounts. Quote total = sum of line totals. Totals are never rounded separately. |
| FR-09 | Never price a ramp silently as flat. If a request carries a schedule that pricing-service cannot honour (flag off, older version, unsupported line type), it returns 422 `RAMP_NOT_SUPPORTED`. Quote-service treats a response with no per-period results for a line it sent a schedule for as a failed calculation and persists no totals. The quote client parses the response with `new ObjectMapper()` (`PricingServiceClient.java:40`), so an unknown response field fails the call for every quote, flat ones included. The view DTOs therefore learn the optional period fields before pricing emits them. The request is the silent path: Spring MVC ignores an unknown schedule and would price the term at one rate. That is the failure this rule forbids. |

### Metrics

| ID | Requirement |
|---|---|
| FR-10 | Metrics for a ramp quote: TCV = sum of period amounts across lines; ACV = TCV divided by (term months / 12); `arr` = run rate at term start × 12; new `exitArr` = final-period run rate × 12; plus a per-period summary. CLV must not be derived from a single flat monthly price for a ramp quote. Definitions follow decisions D-02 and D-03. |
| FR-11 | Flat parity: a quote with no ramp produces numerically identical prices and metrics to today while `acv-version` is `legacy`, and fixtures `execute/01` to `05` keep those numbers. P-1 may fill `steps` (they are `[]` today) and must not change prices or ACV. When `acv-version` is `term-years`, the only permitted numeric difference is ACV, committed with an ADR-023 reference. |

### Governance

| ID | Requirement |
|---|---|
| FR-12 | Policy that consumes TCV or ACV (TCV tiers, ACV floors, approval-tier triggers) uses the ramp-aware metrics from FR-10. Discount thresholds and margin floors are evaluated per period, and the worst period (highest discount, lowest margin) governs the outcome. The routing reason names the offending period. |
| FR-13 | Ramp is not generally available until margin per period is real (prerequisite P-2), or until the interim guardrail in D-06 is in force. The current constant margin cannot govern a ramp. |
| FR-14 | A schedule whose unit price or quantity decreases between adjacent periods is flagged and routes for approval, unless the product or rule marks step-downs as allowed (for example a migration ramp). |
| FR-15 | Approval binds to the exact schedule. The approval request carries a pricing fingerprint (hash of the normalised schedule, term and totals) and the governing period's discount. Any change that alters the fingerprint supersedes an open request and returns `APPROVAL_SUPERSEDED` for late decisions on the old one, extending #559, which compares discount figures only. Approval-routing stays the single canonical writer (ADR-013). A quote cannot be accepted while a request for its current version is open or rejected. |
| FR-16 | An accepted quote's schedule is immutable. A change creates a new quote version. Published price books remain immutable (`PRICE_BOOK_PUBLISHED_IMMUTABLE`), so replays stay valid. |

### Quote experience

| ID | Requirement |
|---|---|
| FR-17 | Quote line create, update and read accept and return the schedule as DTOs only (ADR-006). Quote-service sends the schedule per line in the pricing request and stores the per-period results. |
| FR-18 | The CPQ UI lets the user add, edit and remove periods, generate them from an escalator, see inline validation that mirrors FR-02, and preview period, months, quantity, net unit price, monthly amount and period total with TCV, ACV and exit ARR. An approval banner cites the governing period. The subscriptions UI stays held. |
| FR-19 | The quote PDF job (created by document-generation from `QuoteAccepted`) renders the schedule table for lines that carry one. The generator must obtain the schedule from the event payload or the quote-service API, never from another service's database. How the generator gathers quote data today was not traced for this document. |

### Events and contract (BC-09)

| ID | Requirement |
|---|---|
| FR-20 | `QuoteAccepted` gains an additive per-line ramp array (sequence, start month, months, quantity, net unit price, net monthly, period amount) and `exitArr`, on event version 1. ADR-018 and `EventContractRegistry.java:29-30` say added fields are not a new version, and a second contract on `cpq.quote.status.accepted` fails the one-topic rule. `QuoteAcceptedEventToleranceTest` is extended with the new fields (the D8 precedent) to prove a consumer built before the change still reads the payload, and the registry fitness check passes. The three consumers (contract-asset-management, crm-service, document-generation) keep working unchanged, and the crm-service message (`QuoteAcceptedCrmMessage`) carries `tcv`, `acv` and `arr` with the FR-10 meaning. CRM does not gain Deal columns for those three figures. **2026-10-04:** a per-line snapshot, `lineItems`, landed on v1 ahead of R3, with the same field names as `QuoteAcceptedCrmMessage.LineItemSnapshot`. The ramp array is a `ramp` list on each `lineItems` entry, not a second per-line list. |
| FR-21 | Contract-asset-management creates the subscription with the real term and dates from the event (today hard-coded to one year), and persists one rate period per `RampStep` with dates derived from the term start by calendar-date arithmetic. Processing stays idempotent (ADR-018 D6). |
| FR-22 | `GetRampSchedule` returns the periods for a subscription. The active rate on a date is the period containing that date, and the boundary date belongs to the new period. Dates are calendar dates, not instants. |
| FR-23 | A `RampScheduler` applies each step on its effective date. `ApplyRamp` is idempotent per subscription and sequence and emits `RampApplied` through the outbox carrying the effective date, not the run time. After downtime it catches up missed steps in order. Scheduler lag is a metric with an alert, following the ADR-018 D10 pattern. Duplicate or late runs never produce a second effect. |

### Lifecycle

| ID | Requirement |
|---|---|
| FR-24 | An amendment that adds quantity or products mid-ramp inherits the remaining periods of the parent schedule and is co-termed to the master end date, by default. Proration works inside the current period at that period's rate and follows D-04. A different schedule for the added quantity is allowed and goes through FR-15. |
| FR-25 | Renewal uplift is applied to the exit-period rate (D-05). A renewal quote starts as a single flat period unless a new ramp is authored. Price-protection offers are unaffected. Extension beyond the schedule end holds the final period's rate. |
| FR-26 | Hybrid migration ramp (PRD §5.3, "Cloud Migration Ramp + Dual Entitlement Window"): coordinated recurring lines, one declining (on-prem or maintenance) and one rising (SaaS), each with its own ramp, inside a dual-entitlement window with a maximum length and a margin floor. How migration credit (30% of book value) is applied across periods is decided at slice R4 design. The `HYBRID_MIGRATION_RAMP` type becomes its marker. |

## Calculation contract

For a line with periods `i = 1..n`, net monthly price `m_i` (after the waterfall for that period's quantity) and months `d_i`, with `sum(d_i) = term months`:

- `periodAmount_i = round2(m_i × d_i)`; `lineTotal = sum(periodAmount_i)`; `TCV = sum(lineTotal)`.
- `ACV = TCV / (term months / 12)`; `arr = sum(m_1) × 12`; `exitArr = sum(m_n) × 12` across lines.
- A single-period schedule reduces to today's flat behaviour for TCV and ARR. Its ACV equals the ACV of the same quote without a schedule under whichever function D-03 selects: flat and ramp quotes never use different ACV functions (ADR-023 D-02).

## Acceptance scenarios

ACV figures in AC-01 and AC-02 assume the D-03 recommended formula (TCV divided by term-years). Under today's formula (TCV / 12), AC-01 would show ACV 6,750 and AC-02 would show 11,300.

| ID | Given / expected result |
|---|---|
| AC-01 | 50 named seats, list $50 per seat per month, 36-month term, periods 1–12 at $40, 13–24 at $45, 25–36 at $50, no other rules firing. Monthly 2,000 / 2,250 / 2,500; period totals 24,000 / 27,000 / 30,000; TCV 81,000; ACV 27,000; `arr` 24,000; `exitArr` 30,000. Pricing the whole term at the Year-1 rate would give TCV 72,000, understating by 9,000; this is the failure the feature prevents. |
| AC-02 | Quantity ramp, list $40, quantities 50 / 100 / 150 for 12 months each, named-seat tier at 100 or more seats giving 7% (`volume-discount-named-seat.drl`). Per-period unit 40.00 / 37.20 / 37.20; monthly 2,000 / 3,720 / 5,580; period totals 24,000 / 44,640 / 66,960; TCV 135,600; ACV 45,200; `exitArr` 66,960. A tier taken from the first period only would give 144,000; a tier taken from peak quantity for all periods would give 133,920 (decision D-07). |
| AC-03 | A schedule with a gap (1–12 and 14–36), an overlap, periods summing to 30 of 36 months, a zero-month period, a negative price, or attached to a non-recurring line is rejected with 422 `RAMP_SCHEDULE_INVALID` and the matching reason code. Nothing is persisted. |
| AC-04 | An escalator starting at $40 with 5% per anniversary over 36 months expands to 40.0000 / 42.0000 / 44.1000, stored as three explicit periods. An escalator of +$5 per anniversary expands to 40 / 45 / 50. |
| AC-05 | List $50, periods at $34 / $45 / $50 (discounts 32% / 10% / 0%, blended 14%), discount threshold 25% (PRD §7.5). The quote routes to SALES_VP citing period 1. A blended-only evaluation would not route it. |
| AC-06 | With an open PENDING approval for the AC-05 schedule, the rep changes period 2 from $45 to $40. The maximum discount is unchanged at 32% but the fingerprint changes, so the open request becomes SUPERSEDED, a new request is created, and a late decision on the old one returns 409 `APPROVAL_SUPERSEDED`. Re-saving an identical schedule supersedes nothing. |
| AC-07 | Quotes without a ramp: fixtures `execute/01` to `05` pass with unchanged prices, waterfall totals and metrics. If D-03 is approved, only ACV differs and the fixture diff is committed with the ADR reference. |
| AC-08 | With the ramp flag off, or against a pricing-service without ramp support, a request carrying a schedule returns 422 `RAMP_NOT_SUPPORTED`. If a response lacks per-period results for a line that carried a schedule, quote-service fails the calculation and stores no totals. |
| AC-09 | Recorded schedule, price book version, rule-set and context replay to identical per-period results and an identical fingerprint. |
| AC-10 | Accepting the AC-01 quote emits `QuoteAccepted` with three ramp periods, `termMonths` 36, TCV 81,000, ACV 27,000 and `exitArr` 30,000. A captured pre-change payload still parses in every consumer, and the registry fitness check passes. |
| AC-11 | Term start 2027-01-01. The consumer creates a subscription ending after 36 months (not one year) with three rate periods: 2027-01-01 to 2027-12-31, 2028-01-01 to 2028-12-31, 2029-01-01 to 2029-12-31. Redelivery creates no duplicate periods. The active rate on 2027-12-31 is period 1 and on 2028-01-01 is period 2. |
| AC-12 | The scheduler is down across 2028-01-01 for two days. After restart exactly one `RampApplied` is emitted with effective date 2028-01-01. A second run or a duplicate delivery has no further effect. The lag metric rises and then clears. |
| AC-13 | 20 seats are added at the start of month 19 of the AC-01 schedule (6 months of period 2 at $45, then 12 months of period 3 at $50). Added TCV is 20 × (6 × 45 + 12 × 50) = 17,400, co-termed at month 36, with no day-based proration layered on top at a whole-month boundary. |
| AC-14 | Renewal of the AC-01 subscription: uplift 8% on the exit rate $50 gives $54.00. Applying it to the three-year average of $43 would give $46.44, below the price the customer already pays, which is why the exit rate is the base (D-05). |
| AC-15 | Quantity steps from 150 to 100 between periods: the schedule is flagged as a step-down and routes for approval unless the product allows it. |
| AC-16 | With real cost data per period (prerequisite P-2), a period whose margin falls below the 35% floor routes to DEAL_DESK even when the blended margin clears it. |
| AC-17 | The term of a quote holding a 36-month schedule is changed to 24 months. The schedule is marked stale (FR-04), submit-for-approval and accept are refused, and re-validation reports `RAMP_TERM_MISMATCH` until the periods are corrected and the quote is re-priced. |

## Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-01 | Performance targets in PRD §11.1 (p95 pricing under 200 ms) hold for ramp quotes up to the configured maximum periods and lines. Period pricing must not multiply price-book or database lookups per period. The performance test records the delta against a flat quote. |
| NFR-02 | All API and event changes are additive. A feature flag (proposed `opencrm.pricing.ramp.enabled`, default off) exists on pricing-service and quote-service, and flag-off behaviour is the typed rejection in FR-09, never silent flat pricing. |
| NFR-03 | Conform to existing decisions: DTO-only API boundary (ADR-006), schema per service and no cross-service database reads (ADR-008), gateway stays edge-only (ADR-009), transactional outbox and registry-as-contract events (ADR-010, ADR-018), no business logic in shared libraries (ADR-011; the shared ramp DTO is a published-language contract only), rules behind the port (ADR-012), single-writer approvals (ADR-013). |
| NFR-04 | Security follows ADR-016: approver identity from the token, four-eyes unchanged. Authoring escalator templates and overriding governance need explicit roles. The audit trail records the actor and schedule differences. |
| NFR-05 | Tests: golden fixtures in the existing `execute/` and `drl/` style; property tests (period sums, contiguity, determinism); event contract tests; scheduler tests with a fake clock covering downtime catch-up and duplicates; `backend-unit.sh` stays green; Docker integration tests for the outbox-to-consumer path are tagged `integration`. `scripts/ci/frontend.sh` tests only an allowlist of `crm-*` and shared projects (line 19), so `cpq-*` specs are skipped; the CPQ UI specs need explicit gate wiring or their results do not count. |
| NFR-06 | Observability: counters for ramp quotes priced and validation failures by reason, scheduler lag, `RampApplied` count, dead-letter volume. Default logs exclude customer payloads. |
| NFR-07 | Existing subscriptions are backfilled with one implicit rate period through a non-destructive Flyway migration, so `GetRampSchedule` works for every subscription. |

## Decisions required

Status on 2026-10-03: D-01 through D-08 are recorded in [ADR-023](../adr/ADR-023-price-ramp-schedule-model.md) (**Accepted**). D-09 stays applied. D-02's ACV clause and D-03 were decided together. Provenance is the owner's 2026-10-03 instruction to process this sprint, not a separate finance or sales-ops meeting.

| ID | Decision | Options | Recommendation and why |
|---|---|---|---|
| D-01 | Where the schedule lives | Per-line schedule on the quote line; one quote line per period; rule-driven only | Per-line schedule. One commercial object gives one approval, one contract and correct TCV and amendment semantics. Per-period lines break those; rule-only cannot carry negotiated prices. |
| D-02 | Metric definitions for a ramp | ARR at start or at exit; ACV as TCV over term-years or first-year value | TCV as the sum; ACV = TCV / term-years; `arr` at start, plus `exitArr`. Needs finance sign-off, because approval tiers and floors consume these figures. |
| D-03 | Fix the ACV formula | Keep `(monthly × months) / 12`; change to TCV / term-years | **Taken.** ACV = TCV / term-years, property `opencrm.pricing.metrics.acv-version`, default `legacy`. Not the ramp flag. See ADR-023 D-03. Flat quotes keep today's numbers until `term-years` is set. The 50k and 100k floors are tested both ways. |
| D-04 | Period granularity | Whole months on anniversary dates; day-level | Whole months. Day-level proration remains inside a period only. Keeps schedules readable and boundary dates unambiguous. |
| D-05 | Renewal and extension base | Exit-period rate; average rate; Year-1 rate | Exit rate (see AC-14). |
| D-06 | Governance before real margin exists | Interim guardrail (approval on high period discount or step-down; treat the 40.0 placeholder as non-authoritative); or block ramp until cost data lands | **Taken.** Interim guardrail. Margin `40.0` is non-authoritative. Ramp is not generally available. Flag default off. |
| D-07 | Volume tiers on quantity ramps | Per-period quantity; peak quantity; committed total | **Taken.** The period's own quantity (AC-02). Sales-ops confirmation is owner-delegated with the 2026-10-03 instruction, not independently confirmed. |
| D-08 | Home for the rate schedule (BC-09) | contract-asset-management; a new subscription service | contract-asset-management, which already owns the `Subscription`. Subscriptions UI stays held. |
| D-09 | Scope of the two stories | Keep usage tiers and credit draw-down inside BC-03-09 and BC-09-04; split them out | Split. Ramp is the shared core; usage tiers and credit draw-down are separate requirements with their own acceptance criteria. |

## Delivery slices and story mapping

Prerequisites before R1: P-1 populate waterfall steps (FR-06); P-2 real cost-based margin per period, or D-06 in force; P-3 extend the quote-to-pricing request to carry per-line data (FR-17); P-4 decide D-03; P-5 wire CPQ frontend tests into the CI gate.

| Slice | Content | Requirements | Backlog mapping |
|---|---|---|---|
| R1 | Pricing core and quote authoring, behind the flag | FR-01 to FR-11, FR-17, FR-18 | BC-03-09 / DRG-336, "Ramp pricing per term period" (Phase 1) |
| R2 | Governance and approvals | FR-12 to FR-16 (FR-19 rides on BC-06-03) | BC-04-07 / DRG-338, "Ramp governance: per-period policy and approval binding" (Phase 2) |
| R3 | Events, subscription rate schedule, scheduler, amendment and renewal | FR-20 to FR-25 | BC-09-04 / DRG-339, "Ramp schedule and step application" (Phase 3). Credit draw-down moved to BC-09-06 (D-09) |
| R4 | Hybrid migration ramp | FR-26 | BC-09-07 / DRG-341, "Hybrid migration ramp with dual-entitlement window" (Phase 3) |
| Split-out | Usage-tier rating; credit draw-down | not covered here | BC-03-12 / DRG-337 and BC-09-06 / DRG-340, each needing its own REQ |

Re-estimated on 2026-10-03. The old 8 (BC-03-09) and 5 (BC-09-04) counted stories with three acceptance criteria and were not revalidated. They are withdrawn.

| Story | Was | Now | Why |
| --- | --- | --- | --- |
| DRG-336 / BC-03-09 (R1) | 8 | **21** | Waterfall, versioned ACV, quote persistence, pricing engine, wire, and the quote UI |
| DRG-338 / BC-04-07 (R2) | none | **8** | Per-period discount, step-down, fingerprint supersession, quote banner, quote-service PDF table |
| DRG-339 / BC-09-04 (R3) | 5 | **13** | Additive `QuoteAccepted`, real term on the subscription, rate periods, scheduler, amendment, renewal |
| DRG-341 / BC-09-07 (R4) | none | **8** | Paired ramps, one migration credit, dual-entitlement window, approval |

DRG-337 and DRG-340 are unchanged and stay out of this sprint. FR-19's user-visible table is in R2, on the quote-service template. BC-06-03 (document-generation's stub job) is not this sprint. The PR-level split and the points that are not the Linear rollup are in the sprint doc.

## Rollout and completion

- Ship each slice behind the flag, default off. Enable in a non-production environment first.
- Reconcile TCV, ACV and exit ARR for a sample of real or synthetic ramp deals against an independent spreadsheet before any production enablement. The owner sets the sample size.
- Close this item only with AC-01 to AC-17 passing (AC-16 only once P-2 exists), the event contract check green, the event operations runbook updated for `RampApplied` and scheduler lag, PRD §4.2, §5.3 and the glossary aligned with D-02 and D-03, the ADR accepted, and the Notion and Linear statuses updated. Mark anything not exercised, such as live ERP propagation or Docker-dependent tests, as outstanding rather than passed.

## Traceability

Source rows quote the acceptance criteria as they stood before the 2026-09-29 split.

| Source | Covered by |
|---|---|
| Notion BC-03-09 "RampStep pricing produces per-period PriceElements" | FR-01, FR-05, FR-06, AC-01, AC-02 |
| Notion BC-03-09 "Output feeds BC-09" | FR-20, FR-21, AC-10, AC-11 |
| Notion BC-09-04 "RampApplied changes the active rate at the step date" | FR-22, FR-23, AC-11, AC-12 |
| Notion BC-03-09 "Usage tiers rate estimated volumes for quoting" (moved to BC-03-12) | Out of scope here (D-09) |
| Notion BC-09-04 "CreditBalance never goes negative without an exception" and "CreditDrawnDown event is emitted" (moved to BC-09-06) | Out of scope here (D-09) |
| Capability Map: `RampStep`, `RampScheduler`, `ApplyRamp`, `GetRampSchedule`, `RampApplied` | Naming in FR-01, FR-22, FR-23 |
| PRD §5.3 Cloud Migration Ramp + Dual Entitlement Window | FR-26 (slice R4) |
| PRD §4.2 metric formulas | FR-10, D-02, D-03 |
| Agent spec `agents/term-coterming.md`, `agents/cpq-agent-020.md` | FR-18 (preview), FR-24 |

## Out of scope

Usage metering and rating, credit draw-down and prepaid balances (D-09; BC-03-12 / DRG-337 and BC-09-06 / DRG-340), billing schedules and invoicing (BC-10), revenue recognition (BC-11), FX rate management beyond the quote currency, cross-quote price protection, any subscriptions or contract-asset UI (held), agent behaviour that consumes the schedule, and enabling any Jev flag. This requirements file is not itself a schema or code change. The authorized implementation is the PR DAG in the sprint doc, after ADR-023 is on `main`, with both switches defaulting off.

## References

- [PRD](../../cpq-pricing-prd.md) §4.2, §5.3, §7.4, §7.5, §11.1 and the glossary.
- [ADR index](../adr/README.md); [ADR-023](../adr/ADR-023-price-ramp-schedule-model.md); ADR-006, ADR-008, ADR-009, ADR-010, ADR-011, ADR-012, ADR-013, ADR-016, ADR-018.
- [Event operations runbook](../runbooks/event-operations.md).
- Code paths cited in the Current state table.
- Format reference: [REQ-jev-openrouter-model-selection.md](./REQ-jev-openrouter-model-selection.md).
