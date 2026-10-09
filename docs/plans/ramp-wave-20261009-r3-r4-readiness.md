# Ramp wave: R3 and R4 readiness, 2026-10-09

This is a docs-only handoff assessment, not implementation or merge authorization. R3 (DRG-339, 13 points) and R4 (DRG-341, 8 points) may run concurrently **after R2-GOV merges**, as specified in [the sprint plan](../forge/41-price-ramp-sprint.md). This wave prepares and verifies R2; it does not bypass that dependency.

## Evidence and current baseline

Source inspected in the frontend worktree at clean product baseline `e2773ac` plus the frontend-only R2 patch. Backend baseline context/impact used local GitNexus alias `ramp-wave-20261009`, indexed at `e2773ac`, through `C:/Users/dgorn/open-crm-cpq/.gitnexus/run.cjs`. GitNexus MCP and port 4200 belong to another checkout and were not used. Concept queries returned no results with the FTS-disabled warning, so named-symbol context and source confirmation supplied the evidence. These are source findings, not runtime acceptance results.

| Area | Actual state at e2773ac | Remaining R3/R4 work |
| --- | --- | --- |
| Accepted event | `backend/libs/common/.../event/dto/QuoteAcceptedEvent.java` already has `LineItemSnapshot` and a `lineItems` list. Its pricing snapshot has ARR/TCV/ACV, but no `exitArr`; its line snapshot has commercial scalar fields, but no typed `ramp` array. | Add optional `ramp` on the **existing** line snapshot and optional pricing `exitArr`. Stay v1; do not create a second line list or QuoteAccepted v2. |
| Event factory | `QuoteAcceptedKafkaEventFactory.from` already maps term months/start/end and sorts accepted lines by sortOrder. `lineItemSnapshot` maps scalar line fields. `QuoteService.accept` calls the factory and publishes through the outbox. | Extend the existing mapping with the persisted priced periods after R2; reject missing required ramp pricing rather than reconstructing prices from authored rates. |
| Compatibility tests | `QuoteAcceptedEventToleranceTest` already feeds an unknown `ramp` property into a line to prove tolerance; that property is ignored by the current DTO. It also round-trips scalar line items. | Prove typed ramp values survive a round-trip, legacy payloads still parse, and real factory mapping emits the AC-10 figures. The existing unknown-field test is not evidence that rate periods are implemented. |
| Subscription | One subscription per quote (`SUB-<quoteId>`), no line/rate child collection. Entity has start/end/renewal dates, MRR and ARR. | Subscription-owned dated rate periods, active-rate lookup and exit-rate semantics, with a non-destructive legacy backfill. |
| Consumer | `QuoteAcceptedEventConsumer` uses KafkaInbox plus subscription-number deduplication; ERP work is queued. It still sets Standard Plan, `LocalDate.now()`, one-year end, and ARR = totalAmount. | Read actual term/dates and pricing ARR; persist accepted line rates atomically with subscription creation, preserving inbox and ERP separation. |
| REST | `SubscriptionController` already returns `SubscriptionDto` under `/api/v1/subscriptions`; list/get/customer/status/lifecycle/renew endpoints exist. | Add GetRampSchedule as a DTO endpoint on this controller, with no JPA entity escape. |
| Renewal | `SubscriptionService.renew` only changes the end date; `RenewalScheduler` emits eligibility events. | Quote renewal uses the exit unit rate with uplift; extension holds the exit rate; amendment inherits remaining periods. Existing eligibility scheduling does not perform these calculations. |
| Persistence/events | Contract-asset migrations end at `V1_5`. Source search found no RampScheduler, RampApplied, GetRampSchedule or rate-period table. | Reserve the next migration after checking the new base; add scheduler, apply marker, outbox contract and lag observability. |
| Hybrid | Pricing `ProductType.HYBRID_MIGRATION_RAMP` already exists. No migration-window property, book-value/credit handling or dual-entitlement evaluation was found in inspected source. | Pricing-owned migration policy under `ramp/migration/`, integrated through PricingExecutionService. Read the enum; do not edit it. |

## Graph evidence and next edit gates

Read-only impact results on the baseline:

| Symbol | Result | Interpretation |
| --- | --- | --- |
| Subscription | MEDIUM; 8 direct, 9 total, lower-bound because BaseEntity/dynamic binding is not completely traced | Consumer, service, mapper, repository, ERP adapter/job and RenewalScheduler are visible dependencies; reread relevant source and renew impact on the post-R2 base. |
| QuoteAcceptedEvent | MEDIUM; 7 direct, 19 total; accept flow affected, Quote/Event modules | Existing consumers include contract-asset, CRM and document-generation; compatibility and acceptance tests span services. |
| QuoteAcceptedKafkaEventFactory | Graph reports LOW/0 | Source confirms QuoteService.accept calls `from`; zero does not establish that it is unused. |
| SubscriptionController | Graph reports LOW/0 | Spring annotations expose live REST endpoints; zero does not remove API contract obligations. |
| PricingExecutionService, backend disambiguated | Graph reports LOW/0 | Source confirms PricingExecutionController calls execute/simulate/simulateBulk/getAuditTrail; do not treat zero as unused. |

No product symbols were edited in this assessment. Implementers must rerun file-specific impact on their new base before editing, warn on HIGH/CRITICAL, resolve UNKNOWN with source confirmation, and run complete worktree-specific graph change checks before committing. This document does not certify runtime behavior or replace those gates.

## R3 acceptance and bounded implementation sequence

1. **Additive accepted contract and factory (AC-10).** Add `ramp` to LineItemSnapshot with sequence, startMonth, months, quantity, netUnitPrice, netMonthly and periodAmount; add pricing exitArr. Map R2 persisted priced-period output. Assert three periods, termMonths 36, TCV 81,000, ACV 27,000, exitArr 30,000; retain pre-change captured payloads and EventContractFitnessTest. Common DTOs hold data only. QuoteAccepted remains version 1 under ADR-018 D5.
2. **Rate schema and consumer (AC-11).** Add contract-asset-owned rate rows and DTO/repository/service, then extend the existing consumer. Persist subscription and periods in the inbox transaction; duplicate event IDs and payloads without eventId create no duplicate rows. Preserve ERP sync queuing. Backfill one implicit period for each existing subscription without deleting or rewriting subscriptions. Existing migrations stop at V1_5, so V1_6 is currently the next slot, to be reconfirmed on the implementation base.
3. **Calendar boundaries and GetRampSchedule.** Anchor every month boundary to original term start (`termStart.plusMonths(startMonth - 1)`), not the previous period boundary. For 2027-01-01/36 months, ends are 2027-12-31, 2028-12-31, 2029-12-31; 2028-01-01 belongs to period 2. Test January 31 anchors (February 28, March 31), leap years, inclusive final end and invalid term/date combinations. Preserve established service behavior for captured legacy events with absent optional dates using an explicit compatibility fallback.
4. **ApplyRamp and scheduler (AC-12).** Use an injected clock and a transactional apply service. Each due rate applies once; outbox and apply marker commit atomically. Catch up missed periods in effective-date order. Emit RampApplied with the contracted effective date, not execution time. A scheduler spanning two days after 2028-01-01 emits exactly once; second execution/redelivery does nothing. Verify lag metric rises and clears and add the existing ADR-018 D10 alert pattern. Define inactive/suspended/cancelled subscription behavior before writing scheduler logic.
5. **Amendment and renewal (AC-13/14).** Rate-schedule service supplies remaining periods for added quantity; month 19 adds 20 × (6 × 45 + 12 × 50) = 17,400 through month 36, with no day-based proration on a whole-month boundary. Inside-period proration uses that period's rate. Renewal uplift is 8% on exit $50 = $54.00, not average-based $46.44; a fresh renewal is flat unless authored as a ramp. Extension holds the final rate. Keep price protection unchanged.

Resolve these source-driven design details in the R3 handoff before implementation:

- **Multiple ramp lines:** the current consumer creates one subscription per quote. A unique `(subscription, sequence)` key collides when two accepted lines both have period 1. Recommended rate-row identity is `(subscription_id, accepted_line_uuid, sequence)` with ApplyRamp idempotence at the same line scope. Record this clarification against ADR-023 D8/ADR-018 D6 rather than quietly pretending one quote has one ramp line.
- **Legacy backfill data:** historical subscriptions only carry aggregate MRR/ARR; they do not contain product quantities or unit prices. Preserve an explicit implicit aggregate period with a legacy identity and known aggregate amounts; never invent seat quantity or unit rate. Define exit-rate renewal behavior for such rows separately from new line-level schedules.
- **Amendment/renewal integration boundary:** the sprint file allowlist keeps R3 out of pricing-service. R3 should read GetRampSchedule and author quote inputs in its allocated quote-service path, not modify the R4 pricing lane or silently reinterpret existing renewal DRL. If an additional client/request field is required, record its narrow extension to the sprint allowlist before editing.

Required verification: unit fixtures for all numbers/boundaries, consumer duplicate/transaction rollback tests, scheduler fake-clock/concurrency tests, DTO/API tests, migration tests against both empty and populated databases, outbox event contract/tolerance tests and cross-schema/entity-boundary fitness checks. Docker-backed `*IntegrationTest` classes must run with Docker and be tagged `integration`; a Docker-less unit run is not their acceptance evidence.

## R4 acceptance and bounded implementation sequence

R4 owns pricing-service migration policy only. It runs concurrently with R3 after R2 lands and never edits quote-service, contract-asset or common DTOs.

- Pair one declining on-premises/maintenance recurring line with one rising SaaS recurring line. Each retains its own ramp; rising line uses existing HYBRID_MIGRATION_RAMP marker.
- Credit is 30% of book value. Book value 10,000 yields 3,000 applied once to the declining line's first period. No subsequent period or rising SaaS line receives it; exit SaaS rate is unchanged.
- Enforce `opencrm.pricing.ramp.migration-window-months` with default 12: overlap 12 accepted, 13 rejected as RAMP_SCHEDULE_INVALID. Test inclusive anniversary boundaries and the first/last overlapping month.
- Hybrid always routes for approval with a reason citing the hybrid window; permitted declining steps within the migration window do not create an ordinary unapproved step-down reason. Extend the post-R2 policy hook rather than bypassing the rules port. Margin placeholder 40.0 is never a pass/fail criterion.
- Reconcile waterfall steps, period totals, TCV, ACV, start ARR and exit ARR after the one-time credit. Test mixed flat/ramp quotes, repeat execution, two candidate pairs, missing partner, mismatched terms/currency and malformed metadata. Performance testing must prove period count does not multiply database/product lookups.

Two design points require a recorded decision before R4 implementation:

1. **First-period credit versus whole-line cap.** The sprint says cap the credit at declining-line TCV, apply it only in the first period and never make the period negative. If first-period amount is 1,000 and line TCV is 3,000, a requested 1,800 credit cannot satisfy all three literally. Recommended resolution is effective credit `min(30% bookValue, lineTCV, firstPeriodAmount)`, with unused credit not transferred to later periods. Record this explicitly in the sprint/ADR decision before coding and add the edge fixture; do not silently spread credit or allow a negative first period.
2. **Quote-to-pricing metadata.** The baseline quote toPricingLine forwards schedule/billingFrequency but not general migration metadata or productType; raw pricing requests already have metadata. R4's pricing-only file ownership needs a documented input source for pair identity, book value and window. Prefer catalog/pricing-owned metadata or already supported request metadata for the slice. If rep-authored quote persistence is required, it is a scope extension and must be allocated deliberately rather than having R4 touch R3's quote files.

## Concurrent ownership and launch gates

| Lane | Exclusive ownership once allowed | Handoff contract |
| --- | --- | --- |
| R3-RATE | Common QuoteAccepted additive fields/tolerance tests; new RampApplied registry/DTO; quote accepted factory and accept mapping; contract-asset domain/schema/consumer/rate DTO/controller/service/scheduler/tests; allocated quote amendment/renewal orchestration | Reads persisted R2 periods and current approval binding; produces GetRampSchedule DTO and RampApplied. No pricing-service edits. |
| R4-HYBRID | New `pricing-service/.../ramp/migration/` classes, PricingExecutionService integration and pricing-owned fixtures/tests/property | Reads R2 policy hook and existing ProductType. Does not modify quote-service/common/contract-asset. |
| Orchestrator | Integration tree, resource slots, contract reconciliation, tracker evidence and PR review | Compares shared acceptance fixtures, schedules one heavy build/index at a time on current low-memory host and protects root changes. |

Before launch: R2 PR is actually merged with authorization; R1/R2 backend, cpq-quotes and full workflow gates have evidence; fingerprint supersession/canonical approval and persisted periods survive reload; feature flags remain off; fresh base/worktrees and graph indexes are bound to each lane; the design clarifications above are recorded. Create isolated R3 and R4 worktrees from that merged base, then execute cheap inspection/tests planning concurrently. On this host, memory-bound Maven/Nx/index/browser runs need an explicit single-slot handoff; additional agents do not justify simultaneous heavy builds.

Keep subscriptions UI, Jev enablement, Activities, NgRx, dual-read flips, usage tiers (DRG-337), credit draw-down (DRG-340), document-generation stub work, order orchestration and production deployment outside these lanes. Do not enable ramp or ACV-version flags as part of readiness work. Order proposals do not override the accepted ramp sprint sequence.

This assessment ran no builds, migrations, runtime servers or integration tests and made no tracker/remote changes. It prepares the next authorized launch; it does not mark DRG-339 or DRG-341 complete.
