# cpq-price-books

Price books portal feature library (`@frontend/cpq-price-books`).

## Pattern

Uses existing `PriceBookService` from `@frontend/cpq-pricing-data-access` (signal-state). List/detail/create only for Day-1. Entry **read** on detail; entry write CRUD is out of Day-1 scope.

## HTTP

| Surface | Path |
|---------|------|
| List / get / create | `/api/v1/price-books` |
| Entries (read) | `/api/v1/price-books/:id/entries` |

**Never** call `/api/v1/pricing/price-books` (wrong gateway prefix).

```ts
import { PRICE_BOOKS_ROUTES } from '@frontend/cpq-price-books';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
```

## Running unit tests

```bash
npx nx test cpq-price-books
```
