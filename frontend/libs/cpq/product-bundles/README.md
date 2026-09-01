# cpq-product-bundles

Product bundle admin portal (`@frontend/cpq-product-bundles`).

## Pattern

Colocated DTOs + `ProductBundleService` (signal-state, HttpClient). Paginated list, detail with items table, create dialog, add/remove items, delete bundle ConfirmDialog. Surfaces `BUNDLE_MAX_ITEMS_EXCEEDED` / `BUNDLE_MIN_ITEMS_REQUIRED` in banners. Standalone + OnPush; no NgRx.

## HTTP

| Surface | Path |
|---------|------|
| List / create / get / delete | `/api/v1/product-bundles` |
| Items add / remove | `POST /{id}/items`, `DELETE /{bundleId}/items/{itemId}` |
| Optional product filter | `GET /product/{productId}` |

**Never** call `/api/v1/configuration/**`, `/api/v1/subscriptions`, or `/api/v1/cpq/product-bundles` from the browser.

```ts
import { PRODUCT_BUNDLES_ROUTES } from '@frontend/cpq-product-bundles';
```

## Running unit tests

```bash
NX_DAEMON=false npx nx test cpq-product-bundles --skip-nx-cache
```
