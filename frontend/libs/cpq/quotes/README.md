# cpq-quotes

Quotes feature library (`@frontend/cpq-quotes`).

List/detail browse quotes via `QuoteService` HttpClient against gateway `/api/v1/quotes`. `MOCK_QUOTES` remain as Vitest fixtures only (Wave G7).

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

## UI wiring (G7)

- **List** — `QuotesListComponent` calls `QuoteService.list` on init / filter / page change. Status and deal type are server filters; text search is client-side on the current page.
- **Detail** — `QuotesDetailComponent` calls `QuoteService.getById` and renders `selectedQuote` (loading / error / not-found states).

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
