# cpq-approvals

Approval inbox feature library (`@frontend/cpq-approvals`).

Day-1: list pending approvals and Approve/Reject against the **quote-service BFF**. No NgRx. No history/SLA UI.

## Pattern

HTTP services use the **DealService / QuoteService signal-state pattern** (`HttpClient` + signals). Do **not** call approval-routing `/api/v1/approvals/**` from the portal.

## HTTP — ApprovalService

| Method | Path |
|--------|------|
| `listPending` | `GET /api/v1/quotes/approvals/pending` |
| `approve` | `POST /api/v1/quotes/approvals/:id/approve` |
| `reject` | `POST /api/v1/quotes/approvals/:id/reject` |

```ts
import { ApprovalService, APPROVALS_ROUTES } from '@frontend/cpq-approvals';
```

Dual-read (`approvals.legacy-local-read-enabled`) stays on the BFF — FE does not flip it.

## Routes

Lazy-load from the CRM shell:

```ts
{
  path: 'approvals',
  loadChildren: () =>
    import('@frontend/cpq-approvals').then((m) => m.APPROVALS_ROUTES),
}
```

- `/approvals` — pending inbox with Approve/Reject + link to `/quotes/:id`

## Running unit tests

```bash
npx nx test cpq-approvals
```
