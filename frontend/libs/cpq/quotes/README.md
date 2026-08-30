# cpq-quotes

Quotes feature library (`@frontend/cpq-quotes`).

List/detail/create quotes via `QuoteService` HttpClient against gateway `/api/v1/quotes`. `MOCK_QUOTES` remain as Vitest fixtures only (Wave G7/J3).

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

## UI wiring (G7 + J3)

- **List** — `QuotesListComponent` calls `QuoteService.list` on init / filter / page change. Status and deal type are server filters; text search is client-side on the current page. **New Quote** navigates to `/quotes/new`.
- **Create** — `QuotesCreateComponent` reactive form matching `QuoteCreateRequest` (required `name`, `customerExternalId`; optional currency, duration, deal, region). Success navigates to detail.
- **Detail** — `QuotesDetailComponent` calls `QuoteService.getById`; **Calculate** / **Submit** / optional **Add line item** use existing HTTP methods. Actions disabled when status is not editable (Submit = DRAFT only; Calculate/Add = DRAFT|REJECTED). Inline error banners from HTTP failures.

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
- `/quotes/new` — create form
- `/quotes/:id` — detail (`StatusBadge` quote type, `CurrencyFormatPipe`, lifecycle actions)

## Running unit tests

```bash
npx nx test cpq-quotes
```
