# R4 hybrid migration implementation plan, 2026-10-10

DRG-341 / R4-HYBRID implements paired recurring migration ramps after R2 governance. This draft records the lane decisions and tests; it does not claim implementation or acceptance. The orchestrator has authorized next-wave preparation, with product edits waiting for the quote lifecycle gateway/auth acceptance gate and an exact-worktree graph index.

## Baseline and graph evidence

Implementation worktree: `C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-next-20261010/r4`, HEAD `d99bc1c086c900c2d9da9e60587d3712cd47be44`. Read [AGENTS](../../AGENTS.md), [project steering](../../AGENT.md), [the sprint](../forge/41-price-ramp-sprint.md), [readiness handoff](ramp-wave-20261009-r3-r4-readiness.md) and [ADR-023](../adr/ADR-023-price-ramp-schedule-model.md).

Read-only GitNexus CLI calls explicitly used the audit checkout `C:/Users/dgorn/.codex-worktrees/open-crm-cpq/quality-audit-20261009`, its metadata-selected Node 24.15/GitNexus 1.6.12 runner, and `--repo .`. Its index is at `97c63a11e745c2606148dfcaedcf3a7a4f42bd8e`, indexed `2026-10-10T02:17:47.294Z`, one commit behind the audit branch HEAD. The R4 checkout is newer; `git diff 97c63a1 HEAD -- backend/services/pricing-service backend/services/quote-service` is empty. This confirms unchanged relevant source, but the audit index is preparation evidence, not the exact-tree pre-edit gate. No GitNexus MCP or server on port 4200 was used.

The hybrid concept query returned empty with FTS unavailable. Named symbol context and source inspection supplied evidence. Backend names were file-disambiguated where frontend names collide.

| Target | Upstream impact | Relevant dependencies |
| --- | --- | --- |
| `PricingExecutionService` class | UNKNOWN, no resolved callers | Source confirms `PricingExecutionController` calls execute, simulate, simulateBulk and getAuditTrail. Class zero is not an all-clear. |
| `PricingExecutionService.executeInternal` | HIGH; 2 direct, 6 total symbols; 3 affected entrypoints | execute/simulate/bulk through Execution and Ramp. |
| `PeriodGovernance.evaluate` / class | HIGH; 1 direct, 6 total symbols; 3 affected entrypoints | Existing period policy and execute/simulate/bulk. |
| `MetricsCalculator.rampMetrics` | HIGH; 1 direct, 4 total symbols; 3 affected entrypoints | Aggregate metrics used by execute/simulate/bulk. |

HIGH risk is explicitly recorded. Fresh exact-tree method/file impact and regression coverage are required before modifying these targets; lower shared-axis ratings do not waive the warning. Limited context output was used for discovery only, not exhaustive caller certification. No product changes have been made.

## Decisions for this lane

### R4-D1: first-period credit cap

Requested credit is `round2(bookValue * 0.30)`, using decimal HALF_UP arithmetic. Effective credit is `min(requestedCredit, grossDecliningLineTCV, firstGrossPeriodAmount)`, using priced amounts before credit. Apply it once to the declining line's first priced period. Unused credit is recorded and never carried to later periods or the rising line. Thus book value 6,000, line TCV 3,000 and first-period amount 1,000 yields requested 1,800, effective 1,000 and unused 800, with a zero first period.

This resolves the sprint's simultaneous requirements of a whole-line cap, first-period-only application and nonnegative period amount. The orchestrator must record this clarification in the sprint/ADR before implementation; this plan does not silently reinterpret the existing acceptance text.

### R4-D2: credit amount versus recurring rate

The migration credit is a one-time commercial adjustment. Preserve both lines' recurring `netUnitPrice` and `netMonthly`, and all SaaS exit rates. Subtract credit from first-period `periodAmount`; TCV and term-years ACV decrease, while start ARR and exit ARR retain their recurring run-rate meaning. Record gross period amount, requested/effective/unused credit and adjusted amount in pricing-owned explanation metadata/waterfall. Do not manufacture a lower renewal basis by distributing credit over the recurring unit rate.

This requires an explicit credit exception to ADR-023 D-02's `periodAmount = monthly * months` identity. Existing MetricsCalculator already sums period amounts for TCV and reads netMonthly for ARR/exitArr; use that behavior without introducing a second ACV function. The broader CLV/discount metrics and waterfall presentation must reconcile with the adjustment and be tested. If contract/event billing semantics require a separate credit field, that is an orchestrator-owned additive contract decision, not an unallocated R4 edit in common DTOs.

### R4-D3: metadata input and trust boundary

Use the already-supported pricing request metadata for customer-specific pair identity, book value and overlap. Do not put customer book value in shared catalog metadata. The minimal contract is a nested `hybridMigration` object on each of the two line requests containing a stable `pairId`, a role (`DECLINING` or `RISING`) and the same whole-month window (`startMonth`, `months`); book value is provided on the declining member. Line identity must be unambiguous within the pricing request; reject duplicate product identities within one pair until an allocated stable line ID is available.

Parse metadata once into a typed pricing-owned value object. Reject invalid decimal/string coercions, negative book value, missing partner, more than two members, inconsistent windows, mismatched currency, invalid role or missing explicit recurring schedules with `RAMP_SCHEDULE_INVALID`. Role declarations are not authority: verify rising product type is the existing `HYBRID_MIGRATION_RAMP`, and verify declining product is an allowed `ONPREM_*`/maintenance recurring offer from the batch-fetched `PricingProduct`. Caller metadata may not override product type or `rampStepDownAllowed`.

The overlap window must fit both lines' priced monthly intervals and be no longer than `opencrm.pricing.ramp.migration-window-months` (default 12). End month is exclusive (`startMonth + months`), matching anniversary arithmetic; months 1 through 12 is 12 months, not 13. Reject a 13-month overlap. The declining member's step-down exception applies only within this validated pair/window; ordinary declines outside it still require the normal policy reason.

The current `QuoteService.toPricingLine` forwards schedules/billing frequency but no request metadata. Therefore this pricing-only input source supports execution/simulation, not yet the sprint's stored-quote acceptance. The orchestrator must allocate a narrow quote authoring/persistence/forwarding handoff and prove metadata survives reload and participates in approval fingerprinting. R4 must not edit R3-owned quote files concurrently or claim DRG-341 complete without that handoff.

## Implementation slices once gates open

1. **Policy parser and pair validation:** red tests for the metadata contract, authoritative product checks and 12/13-month boundaries. Add new pricing-only classes under `ramp/migration/`, using the existing product batch result; no additional per-period product/database lookups.
2. **Credit adjustment:** red tests for 10,000 -> 3,000, capped first period, decimal rounding, unchanged later periods and SaaS exit. Price each ramp through the existing waterfall, then adjust a fresh result once before aggregate metric calculation. Repeat execute/simulate starts from authored input; never subtract again from previously adjusted mutable result.
3. **Governance hook:** extend the existing rules-port policy to set PENDING approval at least DEAL_DESK with `RAMP_HYBRID_WINDOW` and window/pair evidence, even with zero credit and no discount/decline. Retain the highest applicable canonical tier. Authorize only validated in-window declining steps; never globally set a product's allowance to true. Preserve configured discount/commitment policy and exclude placeholder margin.
4. **Metric/waterfall audit:** existing metric calculation consumes adjusted amount and unchanged recurring rates. Ensure all output and persisted audit agree on gross amount, credit, adjusted period/line TCV and aggregate TCV/ACV/ARR/exitArr. Do not mutate authored schedules or active flag defaults.
5. **Quote integration handoff:** orchestrator/R3 coordination supplies persisted migration inputs, forwards validated pricing metadata, stores adjustment/governance evidence, invalidates stale approval on any relevant input change, and proves reload plus submit/send/accept. This is an explicit additional ownership decision, not a hidden pricing-only completion claim.

## Ownership reconciliation

The sprint currently allows R4 only new `ramp/migration/` classes and the call from PricingExecutionService. Before product edits, record a narrow extension for existing `PeriodGovernance.evaluate` and its tests: it currently reads step-down allowance exclusively from product metadata and resets caller-supplied governance. An execution-only postprocessor cannot safely preserve that protection while suppressing just the migration-window step-down reason. Prefer no MetricsCalculator change because its amount/run-rate split already supports the chosen credit semantics. Any remaining discount/CLV reconciliation requiring edits must be allocated first and receive fresh impact.

R4 owns pricing-service only. Do not edit ProductType, quote-service, contract-asset or libs/common without a separate orchestrator allocation. Tests/new fixtures and a migration-window properties class belong to this lane. New property defaults may live in the pricing-owned class without changing shared ramp/ACV defaults. The orchestrator owns ADR/sprint decision updates and cross-lane contract reconciliation.

## TDD and acceptance matrix

| Test group | Required behavior |
| --- | --- |
| Golden paired ramp | Distinct declining and rising recurring schedules; requested 3,000 credit applied once; SaaS rate unchanged; first/later period totals, TCV, ACV and both run rates exact. |
| Cap/rounding | First-period cap smaller than whole-line cap; requested cap larger than TCV; zero book value; decimal book value; nonnegative amounts; unused credit explicitly reported. |
| Window | 12 accepted, 13 rejected; first/last month inclusive with exclusive end; late start out of term; quantity/unit declines inside accepted window and outside ordinary policy. |
| Malformed pair | Missing/extra/duplicate partner, inconsistent pair/window/currency, invalid product type, nonrecurring lines, absent ramps, unsafe metadata overriding policy; fail closed. |
| Governance | Always approval with hybrid-window reason; normal discount/CFO policy wins if stronger; no ordinary step-down reason inside exception; ordinary reason outside; no margin 40.0 decision. |
| Repeat/mixed requests | Execute twice and simulate twice identical; multiple valid distinct pairs receive independent credits; mixed flat/ramp outputs preserve existing flat golden prices. |
| Lookup budget | One existing batch pricebook resolution and one existing product batch regardless of number of periods; no product/customer lookup multiplication. |
| Persistence handoff | Reload inputs/output evidence, window/pair fingerprint changes invalidate old approvals, quote acceptance snapshot amounts reflect one credit. Pricing unit tests alone do not satisfy this. |

Run targeted pricing migration/period-governance/engine/waterfall/metrics tests first, then the complete pricing unit suite and relevant architecture/contract checks. Integration tests must be tagged `integration` and run with Docker for database/runtime claims. Resource-intensive Maven, Nx, GitNexus analysis and browser tasks require the orchestrator's single heavy-work slot. No servers or containers started during this assessment.

## Launch and completion gates

- R2 is merged; primary main and isolated lanes are based on merged `d99bc1c` or newer agreed base.
- Root quote lifecycle gateway/auth acceptance gate supplies real evidence; CPQ 503/empty fallbacks do not count.
- Fresh local R4 index, exact pre-edit method/file impact, and acknowledged HIGH risk precede product edits.
- Orchestrator records R4-D1/D2, metadata contract and narrow governance/quote ownership extensions.
- New behavior has failing tests before implementation and passing targeted plus appropriate module checks afterward.
- Complete worktree-specific `detect-changes --scope all` and comparison review precede any commit. UNKNOWN, partial or truncated output is unresolved.
- Flags remain off; ACV default stays legacy. Subscriptions UI, Jev, Activities, NgRx, dual-read flips, usage tiers, credit draw-down and production rollout remain held.

Known gaps at this draft: no R4 implementation, tests, migrations, browser acceptance or tracker updates were performed. Docker integration and browser/chaos are not covered by prior unit results. Frontend CI's allowlist does not cover every CPQ/shared-ui project. The reused audit graph has freshness/FTS limitations. R4 is ready for scoped implementation only after the gates above, not ready for completion.

## Bounded red-first task sequence after the core gate

This sequence incorporates root's latest [combined plan](ramp-next-20261010.md), including its reserved cross-lane priced-period fields. Product work has not started. The R4 tree is still at `d99bc1c` and has no lane-local GitNexus index; root supplies a fresh index on the agreed post-gateway base before edits. The prior graph evidence above remains preparation evidence only.

### Cross-lane amount contract

Pricing output reserves additive optional `grossPeriodAmount` and `migrationCredit` on `PricedPeriod`. Root owns matching quote response/persisted views, accepted-event DTOs and producer handoff, serially with R3. The fields are data only in shared contracts.

For the authorized first declining period:

- `grossPeriodAmount = round2(netMonthly * months)`.
- `migrationCredit = effectiveCredit`, with `0 <= effectiveCredit <= grossPeriodAmount`.
- `periodAmount = grossPeriodAmount - migrationCredit`.
- `netUnitPrice`, `netMonthly`, quantity, months and dates are unchanged.

Only that period emits the optional adjustment fields, including zero effective credit if needed to retain explicit migration evidence. Ordinary periods omit both optional fields and retain the established recurring identity. Requested and unused credit remain pricing-owned audit metadata; they are not new common business logic. Root must preserve both optional fields through reload and acceptance. R3 must validate the gross-minus-credit identity rather than reconstructing an adjusted period amount from rates, and must never apply the one-time credit again during a rate transition or renewal.

### R4-D4: actual entitlement overlap

Determine entitlement from **positive scheduled quantity**, including zero-priced/free periods. A price of zero does not remove entitlement. Each period covers the whole-month interval `[startMonth, startMonth + months)`. Intersect the declining and rising members' positive-quantity intervals, merge adjacent intersections, and require exactly one nonempty contiguous overlap. Its start and length must match the two members' declared metadata window; reject a declared window that hides additional overlap or a disconnected second overlap. The actual window, not the caller's label, must satisfy the configured maximum of 12 months.

This is a deterministic check using authored period quantities; it needs no day-based proration, cross-schema reads or subscription access. When metadata cannot match a valid actual overlap, fail with `RAMP_SCHEDULE_INVALID` and a concrete reason. Both line schedules still cover the common quote term under existing R1 validation; the declining member normally has zero quantity after the overlap ends. A unit-price-zero declining period with positive quantity still overlaps.

Zero quantity is already representable in R1: quote `RampScheduleValidator`, pricing `RampPeriodPricer`, the quote `V1_9` quantity check and the frontend schedule validator reject negatives rather than zero. The editor requires a quantity but imposes no positive minimum. Graph contexts and unchanged source across the gateway-only baseline confirm this read-only fact; no global validation relaxation is allocated or needed. Actual paired execution remains a red-first implementation test.

The step-down exception covers transitions whose new period starts inside the validated overlap. The exact exclusive-end boundary also permits a declining member's transition to zero quantity: that closes the 12-month entitlement instead of extending it. A price/quantity decrease elsewhere retains ordinary governance. Do not waive product types, general recurrence requirements or any discount/commitment rules. This boundary clarification is scoped to the window termination, not a global product allowance.

### Work packages and evidence checkpoints

| Order | Red-first draft / production ownership | Green checkpoint |
| --- | --- | --- |
| 1. Inputs and entitlement | New `ramp/migration/HybridMigrationPolicyTest` fixtures for typed metadata, authoritative product types/currencies, actual overlap, quantity-zero versus free periods, 12/13 and disconnected/mismatched windows. New parser/pair/interval classes only. | Targeted tests pass with no source/database lookup inside period loops; ordinary requests without migration metadata remain unchanged. |
| 2. Amount arithmetic | New `MigrationCreditTest` fixtures plus pricing-owned additive `PricedPeriod` fields (narrow output-contract extension). Test requested/effective/unused credit, gross-minus-credit, no spill, preserved rates and repeat execution. | Exact numbers and serialized optional fields pass; unchanged ordinary periods omit adjustment fields. |
| 3. Scoped policy | New migration governance tests, narrowly allocated `PeriodGovernance.evaluate` extension and associated tests. Inject only validated facts into the existing rules-port context. | Hybrid always PENDING at least DEAL_DESK with window evidence; stronger existing tiers win; permitted in-window/exit-boundary decline has no ordinary step-down reason; all other declines/discounts remain governed. |
| 4. Engine and audit | `PricingExecutionService.executeInternal` hook after period pricing and before metrics/governance. Use already batch-fetched authoritative products, then derive metrics from adjusted output. Pricing-owned audit/waterfall fixtures. | Execute/simulate/bulk agree; mixed flat/ramp and repeated requests stable; product batch lookup count does not grow with periods. |
| 5. Contract handoff | Root-owned quote/common changes, scheduled after R3 overlap settles. R4 supplies priced response and metadata fixture; R3/root validate adjusted amounts. | Persist/reload/accept retains gross/credit/net amount and rates; migration input/amount/window changes invalidate prior fingerprint. This is required before DRG-341 completion. |
| 6. Regression/review | Full pricing unit suite, both ACV fixtures, existing flat goldens, architecture/event checks and applicable Docker/runtime acceptance coordinated by root. Exact local complete change analysis and independent review. | Only evidence-backed implementation is committed/PR'd; unresolved graph flags or unexecuted integration/browser checks remain explicit gaps. |

Timeline is dependency-based: packages 1-2 are the first coding pass; packages 3-4 follow their validated contract; root can prepare package 5 concurrently but serializes overlapping quote edits; package 6 closes the lane. Each coding package needs an observed failing test followed by its passing check. Heavy test/index/runtime slots are scheduled by root, so elapsed completion cannot be promised before that queue is known. No package above has begun implementation.

### Concrete shared numerical fixture

Term 36 months, currency USD:

- Declining recurring maintenance: 10 units at 100/month for months 1-12, then zero quantity for months 13-36. Gross first-period amount 12,000 and gross line TCV 12,000.
- Rising product type `HYBRID_MIGRATION_RAMP`: 10 units at 50/month for months 1-12, then 20 units at 60/month for months 13-36. Period amounts 6,000 and 28,800; SaaS gross TCV 34,800.
- Actual overlap is months 1-12. Book value 10,000 gives credit 3,000 on the declining first period: gross 12,000, credit 3,000, adjusted 9,000. No later period receives credit.
- Quote TCV 43,800, term-years ACV 14,600, start ARR 18,000 and exit ARR 14,400. SaaS exit unit price stays 60.00; an eventual 8% uplift reads 64.80, with no migration-credit renewal adjustment.

Also retain the cap fixture (book value 6,000; requested 1,800; first gross 1,000; line gross TCV 3,000; effective 1,000; unused 800) as a pure amount-policy test. A zero-book-value pair still requires hybrid-window approval. Repeat execution from the same authored inputs must yield identical adjusted output, and applying a prepared plan to an already adjusted result must fail closed or be explicitly idempotent rather than subtract twice.

### Pre-edit warning list for the eventual implementation

Renew exact-tree impact for `PricingExecutionService.executeInternal` and `buildWaterfall`, `PeriodGovernance.evaluate`, `PricedPeriod`, its new setters/serialization, and the allocated tests. Prior method impacts for execution/governance are HIGH and affect execute/simulate/bulk entrypoints. `PricedPeriod` is a pricing-to-quote response boundary; Lombok setters/serialization and cross-service deserialization can be UNKNOWN even if class context resolves. Verify quote client/view compatibility before the new fields are emitted; root owns that handoff. Do not edit MetricsCalculator unless a specific uncovered metric requires it; its prior `rampMetrics` impact is HIGH and existing amount/run-rate semantics already compute the chosen figures.
