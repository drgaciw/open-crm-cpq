# cpq-quotes

Quotes feature library (`@frontend/cpq-quotes`).

List/detail/create quotes via `QuoteService` HttpClient against gateway `/api/v1/quotes`. `MOCK_QUOTES` remain as Vitest fixtures only (Wave G7/J3/K2/L1/M1).

## Pattern

HTTP services use the **DealService signal-state pattern** (`HttpClient` + signals), not `BaseCrudService`.

## HTTP — QuoteService

| Method | Path |
|--------|------|
| list / CRUD | `/api/v1/quotes` |
| lifecycle | `/api/v1/quotes/:id/{submit\|approve\|reject\|send\|accept\|expire\|cancel\|clone\|calculate}` |
| line items | `/api/v1/quotes/:quoteId/line-items` |
| documents | `/api/v1/quotes/:id/documents` (+ `/generate`, `/:docId/download` as **blob**) |

```ts
import { QuoteService, QUOTES_ROUTES, QuotesListComponent } from '@frontend/cpq-quotes';
```

## UI wiring (G7 + J3 + K2 + L1 + M1)

- **List** — `QuotesListComponent` calls `QuoteService.list` on init / filter / page change. Status and deal type are server filters; text search is client-side on the current page. **New Quote** navigates to `/quotes/new`.
- **Create** — `QuotesCreateComponent` reactive form matching `QuoteCreateRequest` (required `name`, `customerExternalId`; optional currency, duration, deal, region). Success navigates to detail.
- **Detail** — `QuotesDetailComponent` calls `QuoteService.getById`; status-gated **Calculate** / **Submit** / **Approve** / **Reject** / **Send** / **Accept** / **Expire** (SENT) / **Cancel quote** (not ACCEPTED|CANCELLED); **Clone** navigates to new DRAFT (ConfirmDialog + note: header **and** line items are copied; Calculate still required for waterfall/ARR). Line CRUD when DRAFT|REJECTED. Embedded `QuoteHeaderEditComponent` (PUT `QuoteUpdateRequest` via `QuoteService.update`; Edit+Save when DRAFT|REJECTED; banner includes `QUOTE_NOT_EDITABLE`). Embedded `QuoteDocumentsPanelComponent`: list / generate (default PROPOSAL) / download (blob). Inline error banners. No NgRx; no shell/route edits in M1.

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
