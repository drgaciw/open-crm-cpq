---
status: done
id: o2-fe-catalog-options-attrs
wave: O
pr: O2
---

# o2-fe-catalog-options-attrs

## Summary

Nest product **option groups** and **attribute values** on CPQ `catalog-detail` (same-file, L1-style). Add shared-models DTOs and catalog-lib HTTP helpers for `/api/v1/product-options/**` and `/api/v1/product-attributes/**`. No shell routes/sidebar; no CRM customers API; no quotes/rule-sets/bundles/configuration validate.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| BE `ProductOptionController` | **UNKNOWN** (0 callers; HTTP) | Gateway `product-options/**` on product-service; FE empty before O2 |
| BE `ProductAttributeController` | **UNKNOWN** (0 callers; HTTP) | Gateway `product-attributes/**`; FE empty before O2 |
| FE `CatalogDetailComponent` | **LOW** (4 impacted) | `index.ts`, `catalog.routes.ts` → CRM `app.routes` / `app.config` (not edited) |
| FE `ProductService` | **LOW** — **not edited** | New sibling services only |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-o2` (`feat/forge-o2-fe-catalog-options-attrs`)  
Impact CLI: `node .gitnexus/run.cjs impact … -r /home/username01/Projects/open-crm-cpq --file <java/ts path>`  
**UNKNOWN ≠ unused** — confirmed with `rg`: gateway routes present; FE clients were empty; agents/docs also call these HTTP paths.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/shared/models/src/lib/product-option.model.ts` | `ProductOptionGroupDto` / `ProductOptionDto` / create requests + selection/adjustment unions |
| `frontend/libs/shared/models/src/lib/product-attribute.model.ts` | `ProductAttributeDto` / `AttributeValueDto` / `AttributeValueSetRequest` + data-type union |
| `frontend/libs/shared/models/src/index.ts` | Re-export option/attribute models |
| `frontend/libs/shared/models/src/lib/cpq-models.spec.ts` | Guards/labels for new unions |
| `frontend/libs/cpq/catalog/src/lib/product-option.service.ts` (+spec) | list/create group, add option, delete group |
| `frontend/libs/cpq/catalog/src/lib/product-attribute.service.ts` (+spec) | list definitions (paged), list/set values |
| `frontend/libs/cpq/catalog/src/lib/option-group-create-dialog/*` | Create group dialog (`OptionGroupCreateRequest`) |
| `frontend/libs/cpq/catalog/src/lib/option-create-dialog/*` | Add option dialog |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.ts` | Nested options + attribute values panels |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.spec.ts` | Vitest coverage for load/create/add/delete/set |
| `frontend/libs/cpq/catalog/src/index.ts` | Export new services |
| `docs/forge/impl/o2-fe-catalog-options-attrs.md` | This note |

## Acceptance

- [x] Groups load via `GET /api/v1/product-options/groups/product/{id}`
- [x] Create group dialog → `POST /api/v1/product-options/groups`
- [x] Add option → `POST /api/v1/product-options/groups/{groupId}/options`
- [x] Delete group ConfirmDialog → `DELETE /api/v1/product-options/groups/{id}`
- [x] Attribute values load via `GET /api/v1/product-attributes/products/{id}/values`
- [x] Set value → `POST …/values` with definition picker `GET /api/v1/product-attributes`
- [x] Shared-models DTOs match BE
- [x] No shell (`app.routes` / sidebar / `tsconfig.base` / `test:cpq`); no customers API
- [x] Vitest `cpq-catalog` + `shared-models` green

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test shared-models --skip-nx-cache
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
```

Result (2026-08-31): `shared-models` 2 files / 14 tests passed; `cpq-catalog` 7 files / 53 tests passed.
