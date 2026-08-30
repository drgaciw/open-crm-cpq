# cpq-pricing-data-access

Shared pricing HTTP services (`@frontend/cpq-pricing-data-access`).

## Pattern

All services follow the **DealService signal-state pattern** (`HttpClient` + `signal()`), **not** `BaseCrudService`. Documented choice for PR-FE-CPQ-03 (matches CRM libs; `RuleService` has no bare list endpoint so BaseCrud would not fit cleanly).

## Services & base paths

| Service | Base path |
|---------|-----------|
| `PricingCustomerService` | `/api/v1/pricing-customers` |
| `PriceBookService` | `/api/v1/price-books` |
| `RuleSetService` | `/api/v1/rule-sets` |
| `RuleService` | `/api/v1/rules` (list via `/by-rule-set/:id`) |
| `PricingExecutionService` | `/api/v1/pricing` (`execute`, `simulate`, `simulate/bulk`, `audit/:quoteExternalId`) |

```ts
import {
  PricingCustomerService,
  PriceBookService,
  RuleSetService,
  RuleService,
  PricingExecutionService,
} from '@frontend/cpq-pricing-data-access';
```

## Running unit tests

```bash
npx nx test cpq-pricing-data-access
```
