---
status: done
id: c4-pricing-sim
---

# c4-pricing-sim

## Summary

Wave C4 **lite**: pricing simulator stub UI in `libs/cpq/pricing-simulator` — minimal form + result panel with mock execute OK (`PricingResult.success === true`). Lazy CRM route `/pricing/simulator` + sidebar link. No HTTP pricing client; full PR-FE-CPQ-07 deferred.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/pricing-simulator/src/lib/pricing-simulator.mock.ts` | Mock `PricingContext` / execute OK + `mockExecuteOk()` |
| `frontend/libs/cpq/pricing-simulator/src/lib/pricing-simulator.mock.spec.ts` | Vitest for mock fixture |
| `frontend/libs/cpq/pricing-simulator/src/lib/simulator-page/simulator-page.component.ts` | Form + result panel (PageHeader, EmptyState, CurrencyFormatPipe) |
| `frontend/libs/cpq/pricing-simulator/src/lib/simulator-page/simulator-page.component.spec.ts` | Vitest smoke for UI |
| `frontend/libs/cpq/pricing-simulator/src/lib/pricing-simulator.routes.ts` | `SIMULATOR_ROUTES` |
| `frontend/libs/cpq/pricing-simulator/src/index.ts` | Export routes, page, mocks |
| `frontend/libs/cpq/pricing-simulator/README.md` | Stub docs |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy `pricing/simulator` |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Pricing Simulator nav |
| `docs/forge/impl/c4-pricing-sim.md` | This status note |

## Acceptance (lite)

- [x] Minimal form builds a `PricingContext` (customer, product, qty, region, currency, duration, subscription type)
- [x] Result panel shows mock execute OK (success banner, metrics, line table)
- [x] `mockExecuteOk` / `MOCK_PRICING_EXECUTE_OK` with `success: true`
- [x] Optional CRM route `/pricing/simulator` + sidebar
- [x] Vitest smoke (page + mock)
- [x] No activities API / S2B-11 / S2C-12 / CORS wildcard changes

## Verify

```bash
cd frontend
npx vitest run --config libs/cpq/pricing-simulator/vite.config.mts
# or: nx test cpq-pricing-simulator
NX_DAEMON=false npx nx build crm --skip-nx-cache
```

## Notes

- GitNexus MCP offline; callers checked via grep. Additive route/nav only (**LOW** risk).
- Real `POST /api/v1/pricing/simulate` + pricing-ui waterfall widgets remain PR-FE-CPQ-07.
- Browser E2E against `nx serve crm` may be skipped if serve is unavailable; covered by Vitest + build when green.
