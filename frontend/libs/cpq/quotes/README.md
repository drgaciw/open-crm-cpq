# cpq-quotes

Quotes feature library (`@frontend/cpq-quotes`).

PR-FE-CPQ-06 **lite**: presentational list/detail using `@frontend/shared-ui` primitives and mock data. No HTTP service, draft create, or quote builder.

## Import

```ts
import { QUOTES_ROUTES, QuotesListComponent } from '@frontend/cpq-quotes';
```

## Routes

Lazy-load from the CRM shell:

```ts
{
  path: 'quotes',
  loadChildren: () =>
    import('@frontend/cpq-quotes').then((m) => m.QUOTES_ROUTES),
}
```

- `/quotes` — list (`PageHeader`, `SearchInput`, `DataTable`, `EmptyState`)
- `/quotes/:id` — detail (`StatusBadge` quote type, `CurrencyFormatPipe`)

## Running unit tests

```bash
npx nx test cpq-quotes
```
