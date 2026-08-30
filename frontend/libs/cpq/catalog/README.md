# cpq-catalog

Product catalog feature library (`@frontend/cpq-catalog`).

PR-FE-CPQ-05 **lite**: presentational list/detail using `@frontend/shared-ui` primitives and mock data. No HTTP service or quote builder.

## Import

```ts
import { CATALOG_ROUTES, CatalogListComponent } from '@frontend/cpq-catalog';
```

## Routes

Lazy-load from the CRM shell:

```ts
{
  path: 'catalog',
  loadChildren: () =>
    import('@frontend/cpq-catalog').then((m) => m.CATALOG_ROUTES),
}
```

- `/catalog` — list (`PageHeader`, `SearchInput`, `DataTable`, `EmptyState`)
- `/catalog/:id` — detail (`StatusBadge` product type, `CurrencyFormatPipe`)

## Running unit tests

```bash
npx nx test cpq-catalog
```
