---
status: done
id: p3-fe-catalog-config-rules
wave: P
pr: P3
---

# p3-fe-catalog-config-rules

## Summary

Nest product **configuration rules** on CPQ `catalog-detail` via an **extracted** `config-rules-panel` (not dumped into the ~1056-line detail file). Add catalog-lib models + `ConfigurationRuleService` for `/api/v1/configuration/rules` (+ `/product/{id}`). Create dialog + ConfirmDialog delete. No shell; no shared-models index edits; no pricing `/api/v1/rules`; no validate UI.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| BE `ConfigurationRuleController` | **UNKNOWN** (0 callers; HTTP) | Gateway `/api/v1/configuration/**` on product-service; FE empty before P3 |
| FE `CatalogDetailComponent` | **LOW** (4 impacted) | `index.ts`, `catalog.routes.ts` → CRM `app.routes` (**not edited**) |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-p3` (`feat/forge-p3-fe-catalog-config-rules`)  
Impact CLI: `node .gitnexus/run.cjs impact … -r /home/username01/Projects/open-crm-cpq --file <java/ts path>`  
**UNKNOWN ≠ unused** — confirmed with `rg`: controller + parity fixture + CE tests; FE clients were empty. Product `POST /validate` **retained** (no FE).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/catalog/src/lib/configuration-rule.model.ts` | `ConfigRuleType` / `ConfigurationRuleDto` / `ConfigRuleCreateRequest` (catalog lib only) |
| `frontend/libs/cpq/catalog/src/lib/configuration-rule.service.ts` (+spec) | listByProduct / create / delete |
| `frontend/libs/cpq/catalog/src/lib/config-rule-create-dialog/*` | Create dialog (`sourceProductId` = current product) |
| `frontend/libs/cpq/catalog/src/lib/config-rules-panel/*` | Extracted panel: list + create + delete ConfirmDialog |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.ts` | Minimal host: `<cpq-config-rules-panel [productId]="product.id" />` |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.spec.ts` | Mock `ConfigurationRuleService`; host assertion |
| `frontend/libs/cpq/catalog/src/index.ts` | Export service + panel + model types |
| `docs/forge/impl/p3-fe-catalog-config-rules.md` | This note |

## Acceptance

- [x] Rules load via `GET /api/v1/configuration/rules/product/{productId}`
- [x] Create dialog → `POST /api/v1/configuration/rules` with `ConfigRuleCreateRequest` (`sourceProductId` = current product; type REQUIRES/EXCLUDES/RECOMMENDS/DEFAULT/VALIDATES; optional `targetProductId` + `conditionExpression` textarea — store only)
- [x] Delete ConfirmDialog → `DELETE /api/v1/configuration/rules/{id}`
- [x] Models in catalog lib — **not** `shared-models/src/index.ts`
- [x] Extracted panel; catalog-detail insert is minimal
- [x] Never `/api/v1/rules` (pricing); never validate UI; product validate retained
- [x] No shell (`app.routes` / sidebar / `tsconfig.base` / `test:cpq`); no product-bundles / subscriptions / NgRx / Java / `00-orchestrator-status` / dual-read flip
- [x] Vitest `cpq-catalog` green

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
```

Result (2026-08-31): `cpq-catalog` 10 files / 69 tests passed.
