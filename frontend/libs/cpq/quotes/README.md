# cpq-quotes

Quotes feature library (`@frontend/cpq-quotes`).

PR-FE-CPQ-06 **lite**: presentational list/detail using `@frontend/shared-ui` primitives and mock data, plus `QuoteService` HTTP client for gateway `/api/v1/quotes`.

## Pattern

HTTP services use the **DealService signal-state pattern** (`HttpClient` + signals), not `BaseCrudService`.

## HTTP — QuoteService

| Method | Path |
|--------|------|
| list / CRUD | `/api/v1/quotes` |
| lifecycle | `/api/v1/quotes/:id/{submit\|approve\|reject\|send\|accept\|expire\|cancel\|clone\|calculate}` |
| line items | `/api/v1/quotes/:quoteId/line-items` |

```ts
import { QuoteService, QUOTES_ROUTES, QuotesListComponent } from '@frontend/cpq-quotes';
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
