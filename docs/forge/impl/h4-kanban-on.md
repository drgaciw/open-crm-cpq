---
status: done
id: h4-kanban-on
wave: H
pr: H4
salvage: 436
---

# h4-kanban-on

## Summary

Wave **H4**: replace per-stage `filter` + `reduce` in
`DealKanbanComponent.columns` with a single-pass `Map` bucket distribution
(`O(stages + deals)`). Salvages the algorithm from open Bolt PR **#436** without
its formatting-only churn or outdated template baseline (pipeline totals / card
menus from B2 stay).

## GitNexus (pre-edit)

| Symbol | Upstream | Risk | Notes |
|--------|----------|------|-------|
| `DealKanbanComponent` | `deals.routes.ts` → `index.ts` → `app.routes.ts` | **LOW** | Pure FE recompute; 0 processes |
| `columns` computed | template `@for` only | **LOW** | Behavior-preserving totals |

**Repository:** open-crm-cpq · **Worktree:** `feat/forge-h4-kanban-on` · Index ~28 commits behind HEAD at edit (FE graph still accurate via text + impact).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.ts` | Single-pass stage Map bucketing + column totals |
| `frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.spec.ts` | Multi-deal / empty-stage / null-amount totals |
| `docs/forge/impl/h4-kanban-on.md` | This status note |

## Acceptance

- [x] `columns` is single-pass O(N) (no `filter` per stage)
- [x] Vitest covers column totals (sum, empty stage, missing amount → 0)
- [x] No API / service / route changes
- [x] Salvage #436 algorithm only (keep current B2 UX)

## Verify

```bash
cd frontend
npx vitest run --config libs/crm/deals/vite.config.mts
# or: NX_DAEMON=false npx nx test crm-deals --skip-nx-cache
```

## Notes

- Unknown / unexpected stages are skipped (bucket absent) — same as filter miss.
- Pipeline header totals still come from `loadPipelineSummary` (unchanged).
- No push (forge rule).
