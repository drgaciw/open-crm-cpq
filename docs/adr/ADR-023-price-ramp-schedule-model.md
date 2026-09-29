# ADR-023: Price ramp schedule model

**Status:** Proposed on 2026-09-29. Documentation only: nothing here is implemented, and no code, schema or flag change follows from this file until the ADR is Accepted. D-02 also needs finance sign-off, and it must be accepted in the same review as D-03 (see [D-02](#d-02--metric-definitions-for-a-ramp)).
**Date:** 2026-09-29
**Deciders:** Project owner (decision pending). Finance sign-off is required for D-02. Drafted from [REQ-PRC-001](../backlog/REQ-price-ramping.md), decisions D-01, D-02, D-04, D-05 and D-08.
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

- any code change, schema change, Flyway migration or data backfill
- enabling any ramp flag (`opencrm.pricing.ramp.enabled` is proposed by the REQ and stays off)
- the ACV formula correction (D-03), which is a separate decision that this ADR depends on, not one it takes
- D-06 (governance before real margin exists), D-07 (volume tiers on quantity ramps) and D-09 (story split). D-09 is already applied to the backlog, and D-06 and D-07 belong to the owner and sales operations
- usage-tier rating (BC-03-12) and credit draw-down (BC-09-06), which are separate requirements
- any subscriptions or contract-asset user interface (held, see [ARCHITECTURE §17](../ARCHITECTURE.md))
- enabling any Jev flag

**Why the number is 023:** ADR-017 to ADR-022 are reserved for other topics (synchronous calls, fitness functions and ADR lifecycle, observability, AI topology, runtime topology; see the [review](../forge/39-adr-gitnexus-review-2026-09-22.md)). This is the next free number, and it does not take one of the reserved ones.

---

## Context

A recurring offer sold as "Year 1 at $40, Year 2 at $45, Year 3 at $50 per seat per month" has no place to live in the platform today. The REQ records the gap in full; this section keeps only the evidence that drives the five decisions below (checked on `main` at `a979892`, 2026-09-29).

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

**Dependency on D-03 (read this before deciding).** D-02 fixes what TCV, `arr` and `exitArr` mean for a ramp. Its ACV clause is not separable from D-03. Under the recommended D-03 formula (ACV = TCV / term-years), AC-01 gives ACV 27,000. Under today's formula (TCV / 12) it would give 6,750, which is meaningless for a 36-month deal. A one-period schedule must report the same ACV as the same quote without a schedule, so a ramp cannot adopt one ACV formula while flat quotes keep another. This ADR therefore recommends accepting D-02 and D-03 together. If D-03 is rejected, the ramp figures that policy consumes (`tcv`, `arr`, `exitArr`) proceed, and ACV stays on the legacy function for flat and ramp alike until a definition is agreed.

Finance sign-off is required because approval tiers and floors consume these figures. Correcting ACV changes existing values and the behaviour of the 50k and 100k ACV floor rule, so it is announced and versioned, and any fixture change is committed with a reference to this ADR.

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
- The rate periods arrive on `QuoteAccepted` as an additive per-line ramp array (sequence, start month, months, quantity, net unit price, net monthly, period amount) plus `exitArr`. This is an `eventVersion` bump through the ADR-018 D5 registry, and `QuoteAcceptedEventToleranceTest` is extended to prove that a consumer built before the change still reads the payload. The consumer also starts using the event's real term and dates, replacing the hard-coded year.
- `GetRampSchedule` returns the periods for a subscription. The active rate on a date is the period containing it.
- A `RampScheduler` in the same service applies each step on its effective date. `ApplyRamp` is idempotent per subscription and sequence (ADR-018 D6), emits `RampApplied` through the outbox carrying the effective date rather than the run time, and catches up missed steps in order after downtime. Scheduler lag is a metric with an alert (ADR-018 D10 pattern).
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
- **Not decided here:** the ACV formula (D-03), governance before real margin exists (D-06), volume tiers on quantity ramps (D-07), the persistence schema, and how the quote PDF obtains the schedule (FR-19, carried by BC-06-03).
- **Identity follows ADR-016.** Approver identity comes from the token, and four-eyes is unchanged.

## Rollout (after Acceptance and a wave plan)

1. Decide D-03 in the same review as D-02, and record finance sign-off.
2. Clear the prerequisites: P-1 (populate waterfall steps), P-3 (carry per-line data in the quote-to-pricing request), P-4 (the D-03 decision) and P-5 (wire the CPQ frontend tests into the CI gate; `scripts/ci/frontend.sh` line 19 skips `cpq-*` today).
3. R1, BC-03-09: pricing core and quote authoring behind a default-off flag. Flag-off behaviour is a typed `422 RAMP_NOT_SUPPORTED`, never silent flat pricing.
4. R2, BC-04-07: per-period governance and approval binding. Ramp is not generally available until this ships and margin per period is real (P-2), or the D-06 guardrail is in force.
5. R3, BC-09-04: event, consumer, rate schedule, scheduler, amendment and renewal.
6. R4, BC-09-07: hybrid migration ramp. Its design is deferred.
7. Enable in a non-production environment first, and reconcile TCV, ACV and exit ARR against an independent spreadsheet before any production enablement.

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
  - The ACV correction changes existing values and must be announced.
- **Residual:**
  - Margin per period cannot be real until P-2 (cost data) exists. Until then the D-06 guardrail governs, and the 40.0 placeholder must not be treated as authoritative.
  - The carried estimates (8 and 5 points) were sized on smaller stories and need re-estimating after Acceptance.
  - Sales operations must still confirm D-07 (per-period quantity for volume tiers) before AC-02 is final.
  - Whether the document-generation service obtains the schedule from the event or from the quote API was not traced for this ADR.

## Acceptance and fitness functions

- **Golden fixtures** in the existing `fixtures/pricing/execute/` and `drl/` style: AC-01 (TCV 81,000), AC-02 (TCV 135,600, once D-07 is confirmed), AC-04 (escalator expansion), plus a flat-parity run of fixtures `execute/01` to `05` that passes unchanged. If D-03 is approved, the only permitted fixture difference is ACV, committed with a reference to this ADR.
- **Property tests:** periods are contiguous, `sum(months)` equals the term, no period is empty, and identical inputs replay to identical per-period results and fingerprint.
- **Boundary tests (D-04):** term start 2027-01-01 gives periods ending 2027-12-31, 2028-12-31 and 2029-12-31, with the active rate on 2027-12-31 in period 1 and on 2028-01-01 in period 2. A term starting 2027-01-31 gives boundaries 2027-02-28 and 2027-03-31.
- **Event contract:** `QuoteAcceptedEventToleranceTest` is extended with the ramp array and `exitArr`, a captured pre-change payload still parses in every consumer, and `EventContractFitnessTest` stays green.
- **Consumer and scheduler tests:** redelivery creates no duplicate rate periods, and a scheduler run with a fake clock across two days of downtime emits exactly one `RampApplied` for the step date (AC-11 and AC-12).
- **Boundary fitness (source scan in the unit gate, in the style of `EventContractFitnessTest`):** the `RampStep` DTO in `libs/common` holds no logic (ADR-011), and no service other than contract-asset-management reads the rate-period tables (ADR-008).
- **Renewal:** AC-14 (54.00, not 46.44) is asserted against the exit rate.

## Evidence (`main` at `a979892`)

- `backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItem.java:27-77`: single price fields and one waterfall per line.
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java:77-78` (default term 12 months) and `:611-631` (`buildPricingContext`).
- `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/MetricsCalculator.java:74-102` (flat metrics), `:94-96` (ACV) and `:120-121` (constant margin).
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
