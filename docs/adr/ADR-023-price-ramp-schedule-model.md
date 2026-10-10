# ADR-023: Price ramp schedule model

**Status:** Accepted on 2026-10-03. The project owner told the orchestrator, on that date, to pull the next sprints from the backlog and process them. That instruction is the authorization for this Acceptance, including D-02's finance sign-off and D-03, D-06 and D-07, taken in the same review. There was no separate finance meeting and no separate sales-ops meeting. The precedent is [the owner-delegated record for ADR-016 and ADR-018](../forge/40-owner-decisions-adr-016-018.md). Nothing in the tree implements a ramp yet. This file authorizes the PR DAG in [the price ramp sprint](../forge/41-price-ramp-sprint.md) and no other code. The ramp flag stays default off. Turning it on is not part of Acceptance.
**Date:** 2026-09-29 (proposed). Accepted 2026-10-03.
**Deciders:** Project owner, by the 2026-10-03 instruction above. D-01, D-02, D-04, D-05 and D-08 stay as drafted on 2026-09-29. D-03, D-06 and D-07 were open and are now taken as specified below. D-09 stays applied (usage tiers and credit draw-down are not in this sprint).
**Amends:** none.
**Relates to:**

- [ADR-006](ADR-006-entities-never-at-api-boundary.md) (DTOs only at the API)
- [ADR-008](ADR-008-postgres-schema-per-service.md) (schema per service, no cross-service database reads)
- [ADR-009](ADR-009-gateway-edge-only.md) (gateway carries no domain logic)
- [ADR-010](ADR-010-kafka-outbox-events.md) and [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (outbox, event registry, idempotent consumers)
- [ADR-011](ADR-011-shared-maven-libs-published-language.md) (no business logic in shared libraries)
- [ADR-012](ADR-012-rules-engine-port-abstraction.md) (rules behind the port)
- [ADR-013](ADR-013-quote-bff-approvals-single-writer.md) (approval-routing is the single writer)
- [ADR-016](ADR-016-identity-authentication-service-trust.md) (approver identity from the token)

**Does not authorize:**

- enabling `opencrm.pricing.ramp.enabled` (default `false` on pricing-service and on quote-service) or setting `opencrm.pricing.metrics.acv-version` to anything but `legacy` in a shared config file
- a second ACV function so that ramp quotes and flat quotes disagree
- treating the placeholder margin `40.0` as a real margin, or routing a ramp on it
- usage-tier rating (BC-03-12 / DRG-337) and credit draw-down (BC-09-06 / DRG-340). D-09 stays applied
- any subscriptions or contract-asset user interface (held, see [ARCHITECTURE §17](../ARCHITECTURE.md))
- enabling any `opencrm.jev.*.enabled` flag, Jev/OpenRouter model selection as runtime, or GLM-5 as runtime
- the standing holds: Activities API (`/api/v1/activities/**`), CI #238, agent Kafka #241, flipping `APPROVALS_LEGACY_LOCAL_READ` off, NgRx, full SpEL, Kafka S2B-9, gateway domain validation, deleting product `POST /validate`, WhatsApp #430 undo, price-book list-row delete (harvest "R2", not ramp R2), Track 2 Spring AI, `SECURITY_OPEN`, Cookie CSRF on domain services (ADR-004: gateway only)
- pricing or validation logic in `libs/common` (the shared ramp DTO is published language only, ADR-011) and any cross-database read (ADR-008)
- a `QuoteAccepted` `eventVersion` bump. Adding fields is non-breaking ([ADR-018](ADR-018-event-delivery-guarantees-idempotency.md); `EventContractRegistry` keeps one contract per topic). The ramp array and `exitArr` are additive on v1

**Why the number is 023:** ADR-017 to ADR-022 are reserved for other topics (synchronous calls, fitness functions and ADR lifecycle, observability, AI topology, runtime topology; see the [review](../forge/39-adr-gitnexus-review-2026-09-22.md)). This is the next free number, and it does not take one of the reserved ones.

---

## Context

A recurring offer sold as "Year 1 at $40, Year 2 at $45, Year 3 at $50 per seat per month" has no place to live in the platform today. The REQ records the gap in full. The five model decisions below were checked on `main` at `a979892` (2026-09-29) and re-checked at `03bb6a67` (2026-10-03). The line numbers still match. D-03, D-06 and D-07 use the same tree.

1. **One price and one term per line.** `quote_line_items` holds a single `quantity`, `list_price`, `unit_price`, `discount_rate`, `net_price`, `extended_amount` and one `pricing_waterfall_json` (`backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItem.java:27-77`). The quote holds one `durationInMonths`, which defaults to 12 (`QuoteService.java:77-78`).
2. **Pricing cannot see per-line data.** The pricing context sends only `productExternalId`, `lineItemType` and `quantity` for each line (`QuoteService.java:611-631`). Nothing about a period could reach pricing-service without widening that request.
3. **Metrics come from one flat monthly price.** `MetricsCalculator.java:74-102` derives ARR, TCV, ACV and CLV from the sum of line prices as a single monthly figure. The ACV expression `(monthly × months) / 12` equals TCV / 12, which contradicts the PRD glossary (ACV is TCV divided by contract years, glossary line 925). That contradiction is decision D-03, not this ADR's.
4. **The contract side has no rate schedule.** `Subscription` carries one `plan_name`, one `start_date` and `end_date`, one `mrr` and one `arr` (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/Subscription.java:25-71`). Migration `V1_1__create_contract_tables.sql` creates the tables; `V1_2` to `V1_5` are outbox and inbox work. `QuoteAcceptedEventConsumer` builds the subscription with a hard-coded one-year term, plan "Standard Plan" and `arr = totalAmount`, ignoring the quote's term (`QuoteAcceptedEventConsumer.java:65-77`).
5. **The accepted-quote event carries totals only.** `QuoteAcceptedEvent` has `termMonths`, dates and ARR, TCV and ACV totals (`libs/common/.../dto/QuoteAcceptedEvent.java:124-162`). It has three consumers (contract-asset-management, crm-service, document-generation). `@JsonIgnoreProperties(ignoreUnknown = true)` and `QuoteAcceptedEventToleranceTest` already make additive change safe (ADR-018 D5 and D8).
6. **The vocabulary is decided.** The Capability Map names `RampStep`, `ApplyRamp`, `GetRampSchedule`, `RampApplied` and `RampScheduler`. The only trace in code is the `HYBRID_MIGRATION_RAMP` product-type label (`ProductType.java:31`), which nothing branches on.
7. **Adjacent rules exist and are not ramps.** Renewal uplift (`renewal-uplift.drl`, 8% or 3%), amendment proration by days (`amendment-proration.drl`) and migration credit. They interact with a ramp and each needs a defined base, which D-04 and D-05 supply.

## Decision

### D-01 — Where the schedule lives

**The schedule is a per-line attribute of the quote line.** A recurring quote line may carry an ordered list of `RampStep` values. A line without a list is one implicit period covering the term and behaves exactly as today.

- A `RampStep` has a sequence, a start month (1-based), a length in whole months, a quantity, and exactly one price basis: an absolute unit price, or a discount fraction off list.
- Only explicit periods are stored and priced. An escalator (start price plus a percentage or amount per anniversary) is an authoring generator: it expands server-side into explicit periods, and its inputs are kept for display only.
- One-time, perpetual, fee and tax lines reject a schedule.
- **Ownership.** quote-service owns authoring, validation at write time and persistence, as child rows of the line in its own schema (ADR-008). pricing-service is stateless for the schedule: it receives the periods in the pricing request, prices each independently through the waterfall, and returns one `PriceElement` per period. approval-routing binds approvals to a *fingerprint* (hash of the normalised schedule, term and totals), not to a copy of the schedule (ADR-013).
- **Published language.** The `RampStep` DTO in `libs/common` is a contract only. Validation and pricing logic stay in the services (ADR-011).
- All API and event changes are additive and DTO-only (ADR-006, ADR-018).

### D-02 — Metric definitions for a ramp

For a line with periods `i = 1..n`, net monthly price `m_i` (after the waterfall for that period's quantity) and months `d_i`, with `sum(d_i)` equal to the term months:

- `periodAmount_i = round2(m_i × d_i)`; `lineTotal = sum(periodAmount_i)`; `TCV = sum(lineTotal)` across lines. Totals are never rounded separately, and all arithmetic is `BigDecimal` with half-up rounding (4 decimal places on unit prices, 2 on amounts), as `MetricsCalculator` and `QuoteService` already do.
- `arr` = the run rate at term start × 12. A new `exitArr` = the final period's run rate × 12.
- `ACV` = the same ACV function used for a flat quote, applied to the ramp's TCV. The function is fixed by D-03.
- CLV must not be derived from a single flat monthly price for a ramp quote.

**Worked figure (AC-01).** 50 seats, 36 months, $40, $45 and $50: monthly 2,000, 2,250 and 2,500; period totals 24,000, 27,000 and 30,000; TCV 81,000; `arr` 24,000; `exitArr` 30,000. Pricing the whole term at the Year-1 rate would give TCV 72,000 and understate the deal by 9,000.

**Taken with D-03.** D-02 fixes what TCV, `arr` and `exitArr` mean for a ramp. Its ACV clause is the function D-03 selects, and both were accepted in this review. Under D-03, AC-01 gives ACV 27,000. Under today's formula (TCV / 12) it would give 6,750. A one-period schedule reports the same ACV as the same quote without a schedule, because both use the active version of the one function.

Finance sign-off is owner-delegated by the 2026-10-03 instruction. It was not given in a separate finance meeting. Approval tiers and floors consume these figures. The correction is versioned (D-03). Any fixture change that exists only because the version is on is ACV, committed with a reference to this ADR.

### D-03 — ACV formula, versioned

**ACV = TCV / term-years, behind a version switch that is not the ramp flag. The default stays today's formula.**

The property is `opencrm.pricing.metrics.acv-version`.

| Value | Formula | Who it applies to |
| --- | --- | --- |
| `legacy` (default) | `(monthlyPrice × durationInMonths) / 12`, which equals TCV / 12 | Every quote while the version stays `legacy`. This is the number `MetricsCalculator.java:93-96` produces today |
| `term-years` | TCV / (durationInMonths / 12) | Every quote, flat and ramp, once this value is explicitly set |

Rejected: overloading `opencrm.pricing.ramp.enabled` so that "ramp on" also changes ACV. The correction changes the 50k and 100k floor for flat quotes that have no schedule. D-06 keeps ramp unavailable and the ramp flag default off. One switch would either hold the correction until ramp is turned on, or turn ramp on in order to correct ACV. The floor tests also need both formulas without pricing a schedule.

Coupling, so the two shapes cannot disagree: pricing-service refuses to start when `opencrm.pricing.ramp.enabled=true` and the version is not `term-years`. With the ramp flag off, the only quotes are flat, and they keep today's numbers until the version is set. There is no supported mode in which a ramp quote and a flat quote use different functions.

**Floor, both ways.** `acv-minimum-commitment.drl:19-22` sets a 50,000 floor when `acv < 50000` and a 100,000 floor when `acv < 100000`, and only when `finalPrice` is below that floor. The execute path still does not put `acv` on a `QuoteFact` (nothing under `pricing-service/src/main` calls `setDecimal("acv")`). The tests insert the ACV each formula produces.

- Monthly price 5,000, term 12, TCV 60,000. Legacy ACV 5,000 → floor 50,000. Term-years ACV 60,000 → floor 100,000.
- Monthly price 10,000, term 12, TCV 120,000. Legacy ACV 10,000 → floor 50,000. Term-years ACV 120,000 → no floor.

Fixtures `execute/01` to `05` keep today's ACV while the version is `legacy`. When a fixture is run with `term-years`, the only numeric difference is ACV, and that file names this ADR.

### D-06 — Governance before real margin exists

**Interim guardrail. The placeholder margin is non-authoritative. Ramp is not generally available. The flag defaults off.**

`MetricsCalculator.java:120-121` sets `estimatedMarginPct` to `40.0` and says cost data is deferred. `approval-margin-exception.drl` compares `estimatedMarginPct` to `0.35`, a fraction. The units do not match, and execute never copies the metric onto the fact. A ramp must not be approved or rejected because that constant is 40.

Until real per-period cost exists (prerequisite P-2, not this sprint), governance is the per-period discount and the step-down rule (REQ FR-12 and FR-14). AC-16 stays untestable and is not a completion claim. `opencrm.pricing.ramp.enabled` defaults to `false` on pricing-service and on quote-service. Flag off returns 422 `RAMP_NOT_SUPPORTED`. It never prices the schedule as flat.

### D-07 — Volume tiers on a quantity ramp

**The tier uses that period's own quantity.** Not the peak quantity, and not the first period's quantity.

Sales-ops confirmation is owner-delegated by the same 2026-10-03 instruction. It was not independently confirmed. AC-02 is final for this sprint: list $40, quantities 50 / 100 / 150, the named-seat 7% tier at 100 seats (`volume-discount-named-seat.drl:14-16`), TCV 135,600, ACV 45,200, `exitArr` 66,960. A tier taken from the first period only would give TCV 144,000. A tier taken from the peak quantity for every period would give TCV 133,920. The period pricer must not produce either.

The classpath rule writes `finalPrice` as a discounted unit price and matches `QuoteLineItemFact`. Execute does not insert that fact (`PricingExecutionService.java:218-220` inserts `Customer`, `PricingContext` and `PricingLineResult`). The flat execute path stays as it is. The 7% tier for AC-02 is applied inside the period pricer, for a ramp line only.

### D-04 — Period granularity

**Periods are whole months on anniversary dates.**

- Each boundary is computed from the term start, never chained from the previous boundary: period `k` starts on `termStart.plusMonths(startMonth - 1)`. A term starting on 2027-01-31 therefore has boundaries 2027-02-28 and 2027-03-31, with no drift. A period ends the day before the next one starts, and the last period ends at the term end.
- The boundary date belongs to the new period. Dates are calendar dates, not instants.
- Day-level proration exists only inside a period, at that period's rate (for example an amendment made part-way through a period). An amendment at a whole-month boundary is not prorated by days (AC-13).
- The term start is a whole calendar date, so a period can be reasoned about without a time zone.

### D-05 — Renewal and extension base

**Renewal uplift and extension use the exit-period rate.**

- Renewal uplift is applied to the final period's rate. A renewal quote starts as a single flat period unless a new ramp is authored.
- Extension beyond the schedule end holds the final period's rate.
- Price-protection offers are unaffected.
- **Worked figure (AC-14).** An 8% uplift on the exit rate $50 gives $54.00. The same uplift on the three-year average of $43 gives $46.44, below the price the customer already pays. The exit rate is the only base that never lowers the price the customer pays today.
- BC-09-05 (renewal) reads the exit rate through `GetRampSchedule` (D-08), so it must follow this decision.

### D-08 — Home for the rate schedule

**contract-asset-management hosts the runtime rate schedule.** It already owns `Subscription`, which its entity documents as the authoritative source for subscription and asset state.

- One rate period is persisted per `RampStep`, as child rows of the subscription in that service's own schema (ADR-008), with dates derived from the term start by the D-04 arithmetic.
- The rate periods arrive on `QuoteAccepted` as an additive optional per-line ramp array (sequence, start month, months, quantity, net unit price, net monthly, period amount) plus optional `pricing.exitArr`. `QuoteAccepted` remains version 1 under ADR-018 D5; no version 2 registration is added. `QuoteAcceptedEventToleranceTest` proves both typed round-trip and compatibility with captured pre-change payloads. The consumer also starts using the event's real term and dates, replacing the hard-coded year.
- `GetRampSchedule` returns the periods for a subscription. The active rate on a date is the period containing it.
- A `RampScheduler` in the same service applies each step on its effective date. `ApplyRamp` is idempotent per subscription, accepted line UUID and sequence (ADR-018 D6), so two accepted lines can each have a period 1. It emits `RampApplied` through the outbox carrying the effective date rather than the run time, and catches up missed steps in order after downtime. Scheduler lag is a metric with an alert (ADR-018 D10 pattern).
- Existing subscriptions get one implicit rate period through a non-destructive migration, so `GetRampSchedule` works for every subscription.
- No other service reads these rows. document-generation obtains any schedule it renders from the event or the quote-service API, never from another service's database (ADR-008). The subscriptions user interface stays held.

## Options considered

| Decision | Option | Verdict |
|----------|--------|---------|
| D-01 | **A. Per-line schedule on the quote line (selected)** | One commercial object gives one approval, one contract and correct TCV and amendment semantics. Con: a child table and a wider pricing request |
| D-01 | B. One quote line per period | **Rejected.** Splits one commercial commitment into N lines, so N approvals, N contract lines and a TCV that is only right by summing. Amendment and co-termination lose the relationship between the periods |
| D-01 | C. Rule-driven only (Drools escalation) | **Rejected.** Rules cannot carry negotiated absolute prices, and the per-period result would not be stored for replay |
| D-02 | **A. TCV as the sum of periods, `arr` at start, plus `exitArr` (selected)** | Each figure has one meaning and the run rates are both visible |
| D-02 | B. `arr` at exit only | **Rejected.** Overstates recurring revenue in year 1 and hides the discount |
| D-02 | C. ACV as first-year value | **Rejected.** Disagrees with the glossary and makes ACV depend on which period is first |
| D-03 | **A. TCV / term-years, property `opencrm.pricing.metrics.acv-version`, default `legacy` (selected)** | Flat quotes keep today's numbers until the version is set. Ramp cannot start on the other function |
| D-03 | B. Same formula change, triggered by `opencrm.pricing.ramp.enabled` | **Rejected.** Couples an announced floor change to turning ramp on, which D-06 forbids by default |
| D-03 | C. Keep `(monthly × months) / 12` forever | **Rejected.** Equals the glossary only at a 144-month term. A 12-month quote reports the monthly price as ACV |
| D-06 | **A. Interim guardrail, flag default off, margin 40.0 ignored (selected)** | Ramp can be built and tested without pretending the constant is cost |
| D-06 | B. Block all ramp code until cost data exists | **Rejected.** The owner instructed this sprint to proceed. The flag keeps it out of general use |
| D-07 | **A. The period's own quantity (selected)** | AC-02. A seat tier follows the seats in that year |
| D-07 | B. Peak quantity for every period | **Rejected.** Over-discounts the early periods (TCV 133,920 instead of 135,600) |
| D-07 | C. First period's quantity for every period | **Rejected.** Under-discounts the later periods (TCV 144,000 instead of 135,600) |
| D-04 | **A. Whole months on anniversary dates (selected)** | Readable schedules and unambiguous boundary dates |
| D-04 | B. Day-level periods | **Rejected.** Every boundary needs day arithmetic, leap years and time zones, with no commercial benefit |
| D-05 | **A. Exit-period rate (selected)** | Never lowers today's price (AC-14) |
| D-05 | B. Average rate | **Rejected.** Lowers the renewal price below what the customer pays (46.44 against 50.00) |
| D-05 | C. Year-1 rate | **Rejected.** Reprices the customer far below the exit rate (43.20 against 50.00 at an 8% uplift) |
| D-08 | **A. contract-asset-management (selected)** | Already owns `Subscription`. No new service, schema or deployment |
| D-08 | B. A new subscription service | **Rejected.** A new service and schema for one child table, and it splits subscription state between two owners |
| D-08 | C. quote-service hosts the runtime schedule | **Rejected.** The quote is a snapshot at acceptance. Making it the runtime authority reverses the ownership the event flow already establishes |

## Scope and boundaries

- **The ADR decides the model, not the sequencing.** Delivery slices R1 to R4, their prerequisites P-1 to P-5 and the Linear and Notion stories are in the REQ. R1 is BC-03-09, R2 is BC-04-07, R3 is BC-09-04 and R4 is BC-09-07.
- **Validation lives twice.** quote-service validates at write time and pricing-service validates again before pricing. Under ADR-011 the checks are not shared through a library, so both are tested against the same golden fixtures.
- **Gateway stays edge-only** (ADR-009). No ramp logic is added there.
- **Rules stay behind the port** (ADR-012). Per-period policy is evaluated by calling the port once per period, and the worst period governs. That is R2, not this ADR.
- **Approval-routing stays the single writer** (ADR-013). The fingerprint extends the discount comparison added in #559. The quote-service BFF only forwards.
- **Decided here as of 2026-10-03:** D-03, D-06 and D-07, in the same review as D-02. The persistence schema and the PR order are in the sprint doc, not repeated as a second design.
- **Quote PDF.** The job document-generation creates from `QuoteAccepted` does not load quote data (`DocumentGenerationService` still stubs the body). The PDF a user can generate today is quote-service's own template. The schedule table is R2, on that template. The document-generation stub stays out of this sprint (BC-06-03).
- **`QuoteAccepted` stays v1.** ADR-018 says adding fields is non-breaking. `EventContractRegistry.java:29-30` says the same, and the registry allows one contract per topic. R3 adds the ramp array and `exitArr` to the v1 payload and extends `QuoteAcceptedEventToleranceTest`. It does not register a v2.
- **Identity follows ADR-016.** Approver identity comes from the token, and four-eyes is unchanged.

## Rollout

D-03, D-06 and D-07 are decided. Implementation is [docs/forge/41-price-ramp-sprint.md](../forge/41-price-ramp-sprint.md). The order that matters:

1. P-1 populates flat waterfall steps without changing prices or ACV. P-4 is this ADR. P-5 adds `cpq-quotes` to the `scripts/ci/frontend.sh` allowlist (line 19). That allowlist is the only copy.
2. R1 (DRG-336, re-estimated 21) behind both default-off flags. Flag-off behaviour is 422 `RAMP_NOT_SUPPORTED`.
3. The quote view learns optional period fields before pricing emits them. Quote's `PricingServiceClient` builds `new ObjectMapper()` (`PricingServiceClient.java:40`), which fails on unknown properties. A new response field shipped first breaks every calculate call, including flat quotes.
4. R2 (DRG-338, 8) is the D-06 guardrail: per-period discount and step-down, fingerprint supersession extending #559. Not the constant margin.
5. R3 (DRG-339, 13) then the rate schedule, scheduler, amendment and renewal. R4 (DRG-341, 8) is the hybrid migration ramp, specified in the sprint doc.
6. Enable in a non-production environment first. Reconcile TCV, ACV and exit ARR against an independent spreadsheet before any production enablement. The owner sets the sample size. This ADR does not enable either switch.

## Consequences

- **Positive:**
  - A ramp is priced as sold, and the failure it prevents is visible in numbers (AC-01: 81,000 against 72,000).
  - One commercial object gives one approval, one contract and one TCV.
  - The runtime rate schedule has a single owner, and `RampApplied` gives downstream services a dated signal.
  - The exit rate gives renewals and extensions a base that never lowers the customer's price.
- **Negative:**
  - A child table in quote-service and another in contract-asset-management, and a wider pricing request.
  - Validation is implemented twice and kept in step by shared fixtures.
  - `QuoteAccepted` grows, and the three consumers must keep tolerating unknown fields.
  - The ACV correction changes existing values once `term-years` is set, and it must be announced. The default does not change them.
- **Residual:**
  - Margin per period is not real until P-2. Until then D-06 governs, and `40.0` is not a margin.
  - Story points were re-estimated on 2026-10-03: DRG-336 is 21 (was 8), DRG-339 is 13 (was 5), DRG-338 is 8, DRG-341 is 8. The old 8 and 5 were not revalidated and are withdrawn.
  - D-07 was not confirmed by sales operations independently. It stands because the owner delegated it. A later correction is a new ADR revision, not a silent change in the pricer.
  - document-generation's PDF job still does not read the quote. R2 renders the schedule from quote-service's own template.

## Acceptance and fitness functions

- **Golden fixtures** in the existing `fixtures/pricing/execute/` and `drl/` style: AC-01 (TCV 81,000, ACV 27,000), AC-02 (TCV 135,600, ACV 45,200), AC-04 (escalator expansion). Fixtures `execute/01` to `05` keep today's prices and today's ACV under `acv-version=legacy`. They currently also lock `steps` to `[]` (`PricingExecuteGoldenFixtureTest`). P-1 may change `steps` and nothing else in those files. When `acv-version=term-years`, the only permitted numeric difference is ACV, in a fixture that names this ADR.
- **Floor, both ways (D-03):** the 5,000 / 12-month case and the 10,000 / 12-month case above, each formula inserted as `QuoteFact.acv`, asserting floor 50,000 vs 100,000 and floor 50,000 vs none. Do not wire `acv` into execute in that test. Wiring the fact is R2, and the waterfall PR owns `PricingExecutionService` until it merges.
- **Property tests:** periods are contiguous, `sum(months)` equals the term, no period is empty, and identical inputs replay to identical per-period results and fingerprint.
- **Boundary tests (D-04):** term start 2027-01-01 gives periods ending 2027-12-31, 2028-12-31 and 2029-12-31, with the active rate on 2027-12-31 in period 1 and on 2028-01-01 in period 2. A term starting 2027-01-31 gives boundaries 2027-02-28 and 2027-03-31.
- **Event contract:** `QuoteAcceptedEventToleranceTest` is extended with the ramp array and `exitArr`, a captured pre-change payload still parses in every consumer, and `EventContractFitnessTest` stays green.
- **Consumer and scheduler tests:** redelivery creates no duplicate rate periods, and a scheduler run with a fake clock across two days of downtime emits exactly one `RampApplied` for the step date (AC-11 and AC-12).
- **Boundary fitness (source scan in the unit gate, in the style of `EventContractFitnessTest`):** the `RampStep` DTO in `libs/common` holds no logic (ADR-011), and no service other than contract-asset-management reads the rate-period tables (ADR-008).
- **Renewal:** AC-14 (54.00, not 46.44) is asserted against the exit rate.

## Evidence (`main` at `03bb6a67`, re-checked 2026-10-03; originally `a979892`)

- `backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItem.java:27-77`: single price fields and one waterfall per line.
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java:77-78` (default term 12 months) and `:611-631` (`buildPricingContext`).
- `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/MetricsCalculator.java:74-102` (flat metrics), `:93-96` (ACV) and `:120-121` (constant margin `40.0`).
- `backend/services/pricing-service/src/main/resources/rules/acv-minimum-commitment.drl:19-22` (50k / 100k) and `volume-discount-named-seat.drl:14-19` (7% at 100 seats, applied to the unit price).
- `backend/services/quote-service/src/main/java/com/opencrm/quote/client/PricingServiceClient.java:40` (`new ObjectMapper()`, fails on unknown response properties).
- `scripts/ci/frontend.sh:19` (test allowlist; `cpq-quotes` is absent). `docs/ci-contract.yaml` points at that script and does not repeat the list.
- GitNexus query `price ramp RampStep` on the index at `04909db` (2026-09-13, 145 commits behind the indexed checkout): no ramp flow. The only ramp symbol returned was `HYBRID_MIGRATION_RAMP` in `frontend/libs/shared/models/src/lib/pricing.model.ts:56`. Impact on the symbols named in the sprint DAG is recorded there. `risk: UNKNOWN` is unresolved. No result was HIGH or CRITICAL.
- `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionService.java:198-241` (rule ordering) and `:291-309` (empty waterfall steps).
- `backend/libs/common/src/main/java/com/opencrm/common/event/dto/QuoteAcceptedEvent.java:124-162`, and `QuoteAcceptedEventToleranceTest.java` and `EventContractFitnessTest.java` under `backend/libs/common/src/test/`.
- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/Subscription.java:25-71`, `.../event/QuoteAcceptedEventConsumer.java:65-77` and `src/main/resources/db/migration/V1_1` to `V1_5`.
- `backend/services/approval-routing/src/main/java/com/opencrm/approval/service/ApprovalService.java:69,194` (`requestedDiscount`, `APPROVAL_SUPERSEDED`, from #559).
- `backend/services/pricing-service/src/main/resources/rules/renewal-uplift.drl` and `amendment-proration.drl`.
- PRD §4.2 (metric formulas) and glossary line 925 (ACV); `docs/ARCHITECTURE.md` §17 (subscriptions front end held).

## Sources

- [REQ-PRC-001 Price ramping requirements](../backlog/REQ-price-ramping.md): decisions D-01 to D-09, functional requirements FR-01 to FR-26 and acceptance scenarios AC-01 to AC-17.
- [ADR index](README.md) and the [GitNexus ADR review](../forge/39-adr-gitnexus-review-2026-09-22.md): reserved numbers ADR-017 to ADR-022.
- Backlog: Notion BC-03-09, BC-03-12, BC-04-07, BC-09-04, BC-09-06 and BC-09-07; Linear DRG-336 to DRG-341 under epics DRG-283, DRG-286 and DRG-291.

## Owner-delegated R3/R4 clarifications, 2026-10-10

The project owner authorized the next recommended steps after PR 580: synchronization, remaining lifecycle acceptance, then R3/R4. The orchestrator records these implementation choices before product edits; there was no separate finance or sales-ops meeting. See [the next-wave plan](../plans/ramp-next-20261010.md) for ownership and evidence gates. R1/R2 code has now landed; statements above describing no implementation are the historical 2026-10-03 baseline, not the current delivery status. Runtime defaults and held rollout remain unchanged.

- **D-02 migration-credit exception:** R4 applies one credit to the declining line's first period. Effective credit is `min(round2(bookValue * 0.30), grossDecliningLineTCV, firstGrossPeriodAmount)`, using priced amounts before credit. No credit spills into later periods or the rising SaaS line. This resolves the first-period-only, whole-line-cap and nonnegative-period requirements together. The one-time adjustment reduces that period's amount, line/quote TCV and term-years ACV; recurring net unit/monthly prices, ARR and exit ARR retain their run-rate meaning. For this adjusted period only, amount equals gross recurring period amount minus effective credit rather than netMonthly times months. Preserve auditable gross/effective/unused credit evidence through persisted and accepted snapshots.
- **D-08 legacy and lifecycle:** historical subscriptions receive an explicitly aggregate implicit period with known dates/MRR/ARR, no invented quantity or unit price, and an already-applied marker. New rate/apply identity includes accepted line UUID. ACTIVE subscriptions apply due periods; PENDING/SUSPENDED catch up when active, while CANCELLED/EXPIRED do not advance. Legacy unit-price continuation fails explicitly unless actual commercial line rates are supplied.
- **R3 mutation identity:** AMENDMENT/EXPANSION/RENEWAL require explicit target subscription identity and line identity where applicable; only a new deal creates a subscription. Use target locking and accepted-quote replay identity in addition to inbox event identity. Read the plan from accepted line facts and ARR from accepted pricing. Cancellation permits PENDING/ACTIVE/SUSPENDED only; its new event contract is a separate follow-up.
- **R4 input ownership:** customer-specific pair/window/book value uses validated pricing request metadata, with product roles/types checked against authoritative pricing product data. Root owns the allocated quote persistence/forwarding/fingerprint handoff, scheduled serially with R3's quote changes; raw request execution alone does not complete stored-quote acceptance. The narrow R4 ownership extension includes PeriodGovernance and its tests. Shared DTOs remain data-only and subscriptions UI remains held.
