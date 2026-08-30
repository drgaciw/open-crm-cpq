---
status: done
id: b2-cpq-models
---

# b2-cpq-models

## Summary

PR-FE-CPQ-02: Add product, quote, and pricing TypeScript models under `@frontend/shared-models`, with **separate** catalog vs pricing `ProductType` enums to avoid DTO drift.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/shared/models/src/lib/product.model.ts` | Catalog enums/DTOs (`CatalogProductType`, `ProductDto`, create/update) |
| `frontend/libs/shared/models/src/lib/quote.model.ts` | Quote enums/DTOs (`QuoteDto`, line items, `QuoteDealType` ≠ CRM `DealType`) |
| `frontend/libs/shared/models/src/lib/pricing.model.ts` | Pricing/price-book/rules/execution models (`PricingProductType`, …) |
| `frontend/libs/shared/models/src/lib/cpq-models.spec.ts` | Type-guard + label-map tests; asserts catalog≠pricing ProductType sets |
| `frontend/libs/shared/models/src/index.ts` | Export product/quote/pricing models |
| `docs/forge/impl/b2-cpq-models.md` | This status note |

## Acceptance

- [x] Enums/DTOs JSDoc-mirrored to Java (`ProductDto`, `QuoteDto`, pricing/pricebook types)
- [x] Separate ProductType enums: `CatalogProductType` vs `PricingProductType` (documented drift)
- [x] Unit tests for type guards / label maps
- [x] `shared-models` tests pass (`nx test shared-models` — 7/7)
- [x] `tsc -p libs/shared/models/tsconfig.lib.json --noEmit` clean

## Blast radius

- Additive exports only on `@frontend/shared-models` — existing CRM consumers unchanged (grep callers; GitNexus MCP unavailable in this worktree).
- Risk: **LOW**

## Notes

- Catalog `ProductType` includes `HARDWARE`, `SAAS_FLAT_RATE`, `CUSTOM`, …; pricing includes `SAAS_MAU`, `HYBRID_MIGRATION_RAMP`, … — do not merge.
- `QuoteDealType` / `QuoteApprovalState` are quote-service enums, not CRM `DealType` / pricing approval strings.
- `PricingResult.approvalState` remains `string` on the wire; `PricingApprovalState` is for UI badge vocabulary from the pricing backlog.
