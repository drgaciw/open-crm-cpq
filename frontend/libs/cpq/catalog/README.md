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

Presentational list/detail (PR-FE-CPQ-05 lite) still use mock data until UI wires `ProductService`.

## Running unit tests

```bash
npx nx test cpq-catalog
```
