# cpq-quotes

Quotes feature library (`@frontend/cpq-quotes`).

## Pattern

HTTP services use the **DealService signal-state pattern** (`HttpClient` + signals), not `BaseCrudService`.

## HTTP — QuoteService

| Method | Path |
|--------|------|
| list / CRUD | `/api/v1/quotes` |
| lifecycle | `/api/v1/quotes/:id/{submit\|approve\|reject\|send\|accept\|expire\|cancel\|clone\|calculate}` |
| line items | `/api/v1/quotes/:quoteId/line-items` |

```ts
import { QuoteService } from '@frontend/cpq-quotes';
```

UI (list/detail/builder) lands in PR-FE-CPQ-06.

## Running unit tests

```bash
npx nx test cpq-quotes
```
