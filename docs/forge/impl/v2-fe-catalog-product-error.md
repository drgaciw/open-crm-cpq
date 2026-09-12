---
status: done
id: v2-fe-catalog-product-error
wave: V
pr: V2
---

# v2-fe-catalog-product-error

## Summary

Wave V2: copy H1 `httpErrorMessage` into `product.service.ts` and wire `list` / `getById` / `search` `error.set` so catalog list/detail banners show `message [CODE]` when `errorCode` is present. List/detail already bind `service.error()` — structure unchanged. Did not reopen catalog-detail NG8011 (#482). No shared helper extract; no `app.routes` / shell; no crm styles/project.json (V1); no deal-kanban (V3); no Java / NgRx / dual-read YAML; did not edit `docs/forge/00-orchestrator-status.md`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-v2` (`fix/forge-v2-fe-catalog-product-error`)  
Index: **stale** (~47 commits; analyze skipped per wave plan). Disambiguated FE vs BE with `--file frontend/libs/cpq/catalog/src/lib/product.service.ts`.

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `ProductService` | **UNKNOWN** (`partial: true`) | `rg`: catalog-list (`list`/`search` + `error()`), catalog-detail (`getById` + `error()`), create-dialog (`create` Observable only) |
| FE `list` / `getById` / `search` | **UNKNOWN** | Same FE callers; list had 2 dropped receiver-typing sites at index time |
| `httpErrorMessage` (new local) | n/a | Copied H1; not shared extract |

**WARN:** UNKNOWN ≠ unused. Confirmed with `rg`. Plan risk **LOW**. Not HIGH/CRITICAL.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/catalog/src/lib/product.service.ts` | Local H1 helper; list / getById / search `error.set(httpErrorMessage(err))` |
| `frontend/libs/cpq/catalog/src/lib/product.service.spec.ts` | list/getById/search assert `message [CODE]` |
| `docs/forge/impl/v2-fe-catalog-product-error.md` | This note |

## Acceptance

- [x] list / getById / search `error.set` is `message [CODE]` when `errorCode` present
- [x] List/detail banners still bind `service.error()` (structure unchanged)
- [x] Did not edit catalog-detail NG8011 (#482)
- [x] No shared `httpErrorMessage` extract; no `app.routes` / shell
- [x] Did not edit crm styles/project.json (V1) or deal-kanban (V3)
- [x] No Java / NgRx / dual-read YAML / Wave T / R2 / N/S/U reopen
- [x] Did not edit `docs/forge/00-orchestrator-status.md`
- [x] Vitest `cpq-catalog` green

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
```

Result: **10 files / 73 tests passed** (including 3 new `product.service` errorCode specs).

## Out of scope

- Shared `httpErrorMessage` extract
- `app.routes` / sidebar / shell
- V1 crm Sass/budget; V3 deal-kanban
- catalog-detail NG8011 reopen (#482)
- Java / NgRx / dual-read YAML flip
- Wave T / `listLineItems`; R2; N/S/U reopen
- Activities; #238; #241; WhatsApp #430; subscriptions FE; Accept ADR-006
- `docs/forge/00-orchestrator-status.md`
