# cpq-pricing-simulator

Nx library for `@frontend/cpq-pricing-simulator`.

Pricing simulator UI (Wave G8): form builds a `PricingContext` and calls
`PricingExecutionService.execute` → `POST /api/v1/pricing/execute`. HTTP and
`success=false` failures surface via an error empty-state. Mock fixtures
(`mockExecuteOk` / `MOCK_PRICING_*`) remain for Vitest only.

## Route

Lazy-loaded from the CRM shell at `/pricing/simulator` via `SIMULATOR_ROUTES`.

## Import

```ts
import {
  SIMULATOR_ROUTES,
  SimulatorPageComponent,
  MOCK_PRICING_EXECUTE_OK,
  mockExecuteOk,
} from '@frontend/cpq-pricing-simulator';
```

## Running unit tests

```bash
nx test cpq-pricing-simulator
# or:
npx vitest run --config libs/cpq/pricing-simulator/vite.config.mts
```
