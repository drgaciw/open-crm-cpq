# Phase 0 Rule Catalog Reconciliation

## Scope

This document captures Phase 0 reconciliation evidence for the pricing rule catalog spike in this worktree. It inventories the target DRL resource files and seeded rules, then maps legacy rules from the source `PricingRules.drl` into `overlap`, `additive`, and `defer` buckets.

## Evidence Sources

- Target DRL resource catalog: `backend/services/pricing-service/src/main/resources/rules/*.drl`
- Seeded rules migration: `backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql`
- Legacy source: external repo (pricing-ai-rules-service, removed from worktree)

## Findings Summary

- Phase 0 target catalog contains **8 DRL resource files**.
- Seed migration `V2__seed_default_rules.sql` seeds **8 active rules** in one default rule set (`Core Pricing Rules`).
- The DRL resource file names/rule names align **1:1** with the seeded rule names.
- Legacy `PricingRules.drl` contains **9 rules**; none are direct name matches, but two are **partial conceptual overlaps** with Phase 0 seeded behaviors.

## Target DRL Resource Inventory (Phase 0)

| DRL file | Rule name | Primary scope | Salience |
|---|---|---|---:|
| `backend/services/pricing-service/src/main/resources/rules/volume-discount-named-seat.drl` | `Named Seat Volume Discount` | SaaS named-seat volume discount by quantity + term gate | 620 |
| `backend/services/pricing-service/src/main/resources/rules/usage-overage-charge.drl` | `Usage Overage Charge` | Usage overage computation | 610 |
| `backend/services/pricing-service/src/main/resources/rules/perpetual-maintenance.drl` | `Perpetual Maintenance Price` | Perpetual maintenance fee calculation | 580 |
| `backend/services/pricing-service/src/main/resources/rules/core-license-pack.drl` | `Core License Pack Floor` | On-prem core pack rounding floor | 590 |
| `backend/services/pricing-service/src/main/resources/rules/migration-credit.drl` | `Perpetual to SaaS Migration Credit` | Migration credit | 560 |
| `backend/services/pricing-service/src/main/resources/rules/approval-margin-exception.drl` | `Route Approval for Margin Exception` | Approval routing for low margin | 930 |
| `backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl` | `Amendment Proration` | Amendment proration / co-term alignment | 540 |
| `backend/services/pricing-service/src/main/resources/rules/renewal-uplift.drl` | `Renewal Uplift and Retention Offer` | Renewal uplift and churn-based retention offer | 530 |

## Seeded Rule Inventory (Phase 0)

Seed source: `backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql`

### Rule Set

| UUID | Name | Status | Version | Effective date | Engine |
|---|---|---|---:|---|---|
| `rs-default-001` | `Core Pricing Rules` | `ACTIVE` | 1 | `2026-01-01` | `DROOLS` |

### Seeded Rules

| Seed UUID | Rule name | Type | Priority | Explanation code |
|---|---|---|---:|---|
| `rule-001` | `Named Seat Volume Discount` | `PRICING` | 620 | `DISC_VOLUME_NAMED_SEAT` |
| `rule-002` | `Usage Overage Charge` | `PRICING` | 610 | `USAGE_OVERAGE_APPLIED` |
| `rule-003` | `Perpetual Maintenance Price` | `PRICING` | 580 | `MAINTENANCE_STANDARD_22PCT` |
| `rule-004` | `Core License Pack Floor` | `PRICING` | 590 | `CORE_PACK_ROUND_UP` |
| `rule-005` | `Perpetual to SaaS Migration Credit` | `PRICING` | 560 | `MIGRATION_CREDIT_30` |
| `rule-006` | `Route Approval for Margin Exception` | `APPROVAL` | 930 | `MARGIN_BELOW_FLOOR` |
| `rule-007` | `Amendment Proration` | `PRICING` | 540 | `AMENDMENT_PRORATION` |
| `rule-008` | `Renewal Uplift and Retention Offer` | `RENEWAL` | 530 | `RENEWAL_UPLIFT_POLICY` |

## DRL Resource <-> Seed Parity Notes

- Rule names and salience/priority values match 1:1 across the 8 DRL resources and the 8 seeded SQL rule records.
- Phase 0 resource files package/import syntax is normalized compared with SQL `content` strings (expected formatting difference).
- Minor non-functional divergence observed in `usage-overage-charge.drl`: the resource file binds `$meta: metadata` in `QuoteFact(...)`, while the seeded SQL content uses `QuoteFact()` for the same rule. Behavior intent remains the same because `$meta` is not used in the rule body.

## Legacy `PricingRules.drl` Reconciliation Mapping

Legacy source rule file contains 9 rules:

- `Calculate ARR`
- `Calculate TCV`
- `Calculate ACV`
- `Volume Discount Based on ARR`
- `TCV-based Pricing Tiers`
- `ACV-based Minimum Commitment`
- `Calculate CLV`
- `Churn Risk Adjustment`
- `Enterprise Subscription Bundle Pricing`

### Mapping Table (Source -> Phase 0 Disposition)

| Legacy source rule | Disposition | Phase 0 relation / rationale |
|---|---|---|
| `Calculate ARR` | `additive` | Enables later quote-level pricing policies but no Phase 0 seeded rule computes ARR. Better implemented as derived metric pre-processing (or utility rules) once `QuoteFact` field parity is defined. |
| `Calculate TCV` | `additive` | Same pattern as ARR; required dependency for `TCV-based Pricing Tiers`. Not represented in Phase 0 catalog. |
| `Calculate ACV` | `additive` | Same pattern as ARR/TCV; required dependency for `ACV-based Minimum Commitment`. Not represented in Phase 0 catalog. |
| `Volume Discount Based on ARR` | `overlap` (partial) | Conceptually overlaps with Phase 0 `Named Seat Volume Discount` (tiered discounting), but Phase 0 version is line-item quantity/term-driven rather than quote ARR-driven. Do not port verbatim without product policy reconciliation. |
| `TCV-based Pricing Tiers` | `additive` | New quote-level pricing tier behavior not present in Phase 0 seed set. Depends on TCV metric derivation and conflict resolution with other price writers. |
| `ACV-based Minimum Commitment` | `additive` | New minimum-commitment floor behavior not present in Phase 0 seed set. Depends on ACV derivation and explicit precedence/order against discounts/multipliers. |
| `Calculate CLV` | `defer` | CLV is analytics-oriented and not consumed by Phase 0 pricing/approval rules. Low parity value for near-term pricing compatibility. |
| `Churn Risk Adjustment` | `overlap` (partial) | Conceptually overlaps with Phase 0 `Renewal Uplift and Retention Offer` because both use churn risk in renewal economics. Phase 0 encodes uplift/retention-offer logic instead of a direct final-price discount. |
| `Enterprise Subscription Bundle Pricing` | `defer` | High-complexity composite pricing rule (bundle detection, enterprise adjustments, min-price guarantee) with legacy domain dependencies (`Quote`, `QuoteLineItem`, bundle model) not present in current Phase 0 fact model evidence. |

## Phase 2 Recommendation

Prioritize Phase 2 on **additive quote-level pricing policy chain** with explicit ordering, and continue to treat Phase 0 seeded domain rules as the baseline catalog.

Recommended Phase 2 sequence:

1. Introduce a **derived-metrics layer** for `ARR`, `TCV`, and `ACV` (prefer deterministic pre-processing or low-salience utility rules, but avoid legacy logging side effects in the first pass).
2. Port **`TCV-based Pricing Tiers`** and **`ACV-based Minimum Commitment`** with a documented write-order/precedence model for `finalPrice`, `priceMultiplier`, and floors.
3. Reconcile the **volume discount policy** choice (`ARR`-driven legacy vs quantity/term-driven Phase 0 seeded rule) before adding any new discount writer to avoid double-discount behavior.
4. Defer **`Calculate CLV`** and **`Enterprise Subscription Bundle Pricing`** until the target fact model includes analytics/bundle requirements and product policy confirms business need.

## Phase 2 Guardrails

- Keep one source of truth for each price field mutation (`finalPrice`, `discountRate`, `priceMultiplier`) per execution stage.
- Document conflict resolution between quote-level and line-item-level pricing rules before enabling mixed rule sets.
- Preserve seeded Phase 0 rule names/explanation codes for auditability; add new rules as additive catalog entries rather than repurposing existing IDs.
