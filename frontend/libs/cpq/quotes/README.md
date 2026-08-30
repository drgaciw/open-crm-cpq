# cpq-quotes

Quotes feature library (`@frontend/cpq-quotes`).

<<<<<<< HEAD
## Pattern
=======
PR-FE-CPQ-06 **lite**: presentational list/detail using `@frontend/shared-ui` primitives and mock data. No HTTP service, draft create, or quote builder.
>>>>>>> feat/forge-c1-quotes-stub

HTTP services use the **DealService signal-state pattern** (`HttpClient` + signals), not `BaseCrudService`.

## HTTP — QuoteService

| Method | Path |
|--------|------|
| list / CRUD | `/api/v1/quotes` |
| lifecycle | `/api/v1/quotes/:id/{submit\|approve\|reject\|send\|accept\|expire\|cancel\|clone\|calculate}` |
| line items | `/api/v1/quotes/:quoteId/line-items` |

```ts
<<<<<<< HEAD
import { QuoteService } from '@frontend/cpq-quotes';
```

UI (list/detail/builder) lands in PR-FE-CPQ-06.
=======
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
>>>>>>> feat/forge-c1-quotes-stub

## Running unit tests

```bash
npx nx test cpq-quotes
```
