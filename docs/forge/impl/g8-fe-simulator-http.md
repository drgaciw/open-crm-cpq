---
status: done
id: g8-fe-simulator-http
wave: G
pr: G8
---

# g8-fe-simulator-http

## Summary

Wave **G8**: wire `simulator-page` happy path to existing
`PricingExecutionService.execute` (`POST /api/v1/pricing/execute`). Remove
`mockExecuteOk` from the component path; keep mock fixtures for Vitest only.
HTTP failures and `success=false` responses show an **error empty-state**
(`app-empty-state`) with dismiss.

## GitNexus (pre-edit)

| Symbol | Upstream | Risk | Notes |
|--------|----------|------|-------|
| FE `PricingExecutionService` | 1 direct | **LOW** | Class in `pricing-data-access`; ambiguous vs Java service — disambiguated by path |
| `SimulatorPageComponent` | routes + index + CRM lazy route | **LOW** | Additive HTTP wire |
| `mockExecuteOk` | `onSimulate` only | **LOW** | Call site removed from component; retained for specs |

Index was ~9 commits behind HEAD at edit time; FE not in Java graph.
Text `rg` confirmed only simulator-page called `mockExecuteOk` on happy path.

**Repository:** open-crm-cpq · **Worktree:** `feat/forge-g8-fe-simulator-http` · Risk **LOW–MED** (architecture), graph **LOW**.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/pricing-simulator/src/lib/simulator-page/simulator-page.component.ts` | Inject `PricingExecutionService`; `onSimulate` → `execute`; error empty-state; copy no longer claims mock |
| `frontend/libs/cpq/pricing-simulator/src/lib/simulator-page/simulator-page.component.spec.ts` | Mock service; happy / HTTP error / success=false / invalid form |
| `frontend/libs/cpq/pricing-simulator/README.md` | Document HTTP execute + mock-for-tests |
| `docs/forge/impl/g8-fe-simulator-http.md` | This status note |

Unchanged (fixtures only): `pricing-simulator.mock.ts` (+ its spec),
`PricingExecutionService` implementation (Wave C2).

## Acceptance

- [x] Simulate hits `/api/v1/pricing/execute` via `PricingExecutionService.execute`
- [x] `mockExecuteOk` not used on component happy path (tests only)
- [x] Error empty-state for HTTP failure and `success=false`
- [x] Vitest green for `cpq-pricing-simulator`
- [x] No NgRx, no new HTTP stack, no G6/G7/G9 scope

## Verify

```bash
cd frontend
npx vitest run --config libs/cpq/pricing-simulator/vite.config.mts
# or: NX_DAEMON=false npx nx test cpq-pricing-simulator --skip-nx-cache
```

## Notes

- Chose `execute` (not `simulate`) to match Wave G acceptance path
  `/api/v1/pricing/execute`.
- Form still seeds defaults from `MOCK_PRICING_CONTEXT` (seed data only).
- Full waterfall widgets / customer autocomplete remain PR-FE-CPQ-07.
