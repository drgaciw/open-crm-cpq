# cpq-pricing-simulator

Nx library for `@frontend/cpq-pricing-simulator`.

Presentational pricing simulator stub (Wave C4): minimal form + result panel with
mock `PricingResult` success (no HTTP). Full UX (waterfall components, real
`POST /api/v1/pricing/simulate`) lands in PR-FE-CPQ-07.

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
