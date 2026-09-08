---
status: done
id: s3-fe-bundles-errorcode
wave: S
pr: S3
---

# s3-fe-bundles-errorcode

## Summary

Wave S3: widen `bundleHttpErrorMessage` to append **any** `errorCode` as H1 shape `message [CODE]` (previously only `BUNDLE_MAX_ITEMS_EXCEEDED` / `BUNDLE_MIN_ITEMS_REQUIRED`). Wire `ProductBundleService` `list` / `listByProductId` / `getById` `error.set` through the helper so list/detail banners surface codes. No routes/shell; no shared-utils extract; no Java; dual-read untouched; did not edit `docs/forge/00-orchestrator-status.md`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-s3` (`fix/forge-s3-fe-bundles-errorcode`)  
Index: **stale** (analyze skipped per wave plan).

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `bundleHttpErrorMessage` | **UNKNOWN** (not in index) | `rg`: create/item dialogs + detail only; widening is additive |
| FE `ProductBundleService` list/get | **UNKNOWN** (index hits Java BE class) | `rg`: list + detail components in same lib; no Java edits |
| BE `ProductBundleService` | **UNKNOWN** 0 callers | Not edited — HTTP FE only |

**WARN:** UNKNOWN ≠ unused. Confirmed with `rg` that FE callers stay inside `frontend/libs/cpq/product-bundles`. Plan risk **LOW**.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/product-bundles/src/lib/bundle-http-error.ts` | Append any `errorCode` as `message [CODE]` |
| `frontend/libs/cpq/product-bundles/src/lib/bundle-http-error.spec.ts` | Keep MAX/MIN; assert any code (e.g. `RESOURCE_NOT_FOUND`) |
| `frontend/libs/cpq/product-bundles/src/lib/product-bundle.service.ts` | list / listByProductId / getById use `bundleHttpErrorMessage` |
| `frontend/libs/cpq/product-bundles/src/lib/product-bundle.service.spec.ts` | Banner asserts `message [CODE]` for list/listByProductId/getById |
| `frontend/libs/cpq/product-bundles/src/lib/product-bundle-create-dialog/...spec.ts` | Expect `RESOURCE_NOT_FOUND` appended after helper widen |
| `docs/forge/impl/s3-fe-bundles-errorcode.md` | This note |

## Acceptance

- [x] Any `errorCode` appended as `message [CODE]`
- [x] Existing MAX/MIN helper tests retained
- [x] Service list / listByProductId / getById banners use helper
- [x] No `app.routes` / sidebar / shell / tsconfig / `test:cpq`
- [x] No shared-utils extract; no Java; dual-read YAML untouched
- [x] Did not edit `docs/forge/00-orchestrator-status.md`
- [x] Vitest `cpq-product-bundles` green

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-product-bundles --skip-nx-cache
```

Result: **8 files / 36 tests passed**.

## Out of scope

- P1 routes / shell / Bundles chrome
- Shared `httpErrorMessage` extract
- Quotes / CRM / price-books / simulator (other S tracks)
- Dual-read flip; Activities; #238; #241; WhatsApp #430; R2 list-row delete
- NgRx; subscriptions FE; Accept ADR-006
