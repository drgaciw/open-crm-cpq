# cpq-catalog

Product catalog feature library (`@frontend/cpq-catalog`).

## Pattern

HTTP services use the **DealService signal-state pattern** (`HttpClient` + `signal()` / `computed()`), not `BaseCrudService`. Mutations return `Observable`; list/getById update signals via `subscribe`.

## HTTP — ProductService

| Method | Path |
|--------|------|
| `list` / `search` / CRUD | `/api/v1/products` |
| `getBySku` | `/api/v1/products/sku/:sku` |
| `activate` / `deprecate` / `retire` | `/api/v1/products/:id/{activate\|deprecate\|retire}` |

```ts
import { ProductService, CATALOG_ROUTES } from '@frontend/cpq-catalog';
```

List/detail happy path uses `ProductService` (`GET /api/v1/products`, `GET /api/v1/products/:id`). `MOCK_CATALOG_PRODUCTS` remains for Vitest fixtures only.

## Running unit tests

```bash
npx nx test cpq-catalog
```
