# cpq-pricing-customers

Pricing customers portal feature library (`@frontend/cpq-pricing-customers`).

## Pattern

Uses existing `PricingCustomerService` from `@frontend/cpq-pricing-data-access` (signal-state). List (optional segment filter), detail, create dialog, inline update, and ConfirmDialog soft-delete. Standalone + OnPush; no NgRx.

## HTTP

| Surface | Path |
|---------|------|
| List / get / create / update / delete | `/api/v1/pricing-customers` |

**Never** call `/api/v1/customers` or CRM contact/organization APIs. These are pricing-service customers, not CRM contacts.

```ts
import { PRICING_CUSTOMERS_ROUTES } from '@frontend/cpq-pricing-customers';
import { PricingCustomerService } from '@frontend/cpq-pricing-data-access';
```

## Running unit tests

```bash
NX_DAEMON=false npx nx test cpq-pricing-customers --skip-nx-cache
```
