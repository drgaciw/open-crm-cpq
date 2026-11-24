# DRG-514 write-route role table (C16)

Decision 7:28 PM CT: quote-line bulk-remove reuses the existing commercial quote-write roles.
Catalog PUTs, including the price-book PUT, are Admin-only. A missing or unknown role is not in the
allow set, so the matcher returns 403. No token returns 401 from the shared jwt chain.

| Route | Allowed roles | Refused roles |
| --- | --- | --- |
| `POST /api/v1/quotes/{quoteId}/line-items/bulk-remove` | `CPQ.User`, `DealDesk`, `SalesVP`, `CFO` (`LifecycleAuthority.ALLOWED`) | `Admin`, `InstalledBase.Read`, `Internal.Invoke`, no app role, unknown role |
| `PUT /api/v1/product-bundles/{id}` | `Admin` | `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `InstalledBase.Read`, `Internal.Invoke`, no app role, unknown role |
| `PUT /api/v1/product-bundles/{bundleId}/items/{itemId}` | `Admin` | same refused set |
| `PUT /api/v1/configuration/rules/{id}` | `Admin` | same refused set |
| `PUT /api/v1/price-books/{id}` | `Admin` | same refused set |

`PUT /api/v1/price-books/{id}` is the whole update. A rename that sets no clear flag is Admin-only.
The matcher is `PUT /api/v1/price-books/*`, one path segment, so price-book entry updates stay on the
baseline authenticated rule.

The gates are `OpenCrmSecurityCustomizer` beans:

- `backend/services/quote-service/src/main/java/com/opencrm/quote/security/QuoteLineBulkRemoveAuthorization.java`
- `backend/services/product-service/src/main/java/com/opencrm/product/security/CatalogPutAuthorization.java`
- `backend/services/pricebook-service/src/main/java/com/opencrm/pricebook/security/PriceBookPutAuthorization.java`

Catalog tables still have no `tenant_id`. That isolation waits on drg and is not part of this table.
