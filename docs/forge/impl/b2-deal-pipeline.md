---
status: done
id: b2-deal-pipeline
---

# b2-deal-pipeline

## Summary

PR-FE-3: Deal pipeline UX hardening. Replaced native `prompt()` lose flow with a Material reason dialog (parity with leads). Kanban cards now expose Advance / Won / Lost stage actions wired to `DealService.advanceStage` / `markWon` / `markLost`. Kanban header shows open-pipeline totals from `loadPipelineSummary`. List/detail/kanban use `actionLoading` disable patterns instead of empty `error: () => {}` handlers. Advance is gated off for `NEGOTIATION` (API requires win/lose). Vitest covers service POST URLs and list/kanban action wiring.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/deals/src/lib/lose-reason-dialog/lose-reason-dialog.component.ts` | New Material lose-reason dialog for deals |
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.ts` | Dialog lose; `actionLoading`; `canAdvance`/`canClose` gates |
| `frontend/libs/crm/deals/src/lib/deal-detail/deal-detail.component.ts` | Same dialog + loading/disable patterns |
| `frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.ts` | Card stage menu; pipeline header totals; refresh board+summary |
| `frontend/libs/crm/deals/src/lib/deal.service.ts` | `loadPipelineSummary` no longer toggles shared `loading` (safe with `list`) |
| `frontend/libs/crm/deals/src/lib/deal.service.spec.ts` | advance/win/lose/pipeline HTTP contracts |
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.spec.ts` | List action wiring + dialog lose |
| `frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.spec.ts` | Kanban summary + stage action wiring |

## Tests

```bash
cd frontend && npx nx test crm-deals
```

Result: **21 passed** (service 5, list 8, kanban 7, scaffold 1).

Covers:

- `POST /api/v1/deals/{id}/advance|win|lose` (+ lose `{ reason }` body)
- `GET /api/v1/deals/pipeline` without flipping list `loading`
- List: advance/win/lose dialog wiring, NEGOTIATION advance gate, error clears `actionLoading`
- Kanban: `loadPipelineSummary` on init, open-stage totals, advance/win/lose refresh

## Blast radius (static; GitNexus MCP offline)

| Symbol | Callers | Risk |
|--------|---------|------|
| `DealService.loadPipelineSummary` | Was unused by UI; now kanban only. Dashboard still uses raw HTTP. | **LOW** |
| `DealService.advanceStage` / `markWon` / `markLost` | list, detail, kanban (same lib) | **LOW–MEDIUM** |
| List/detail/kanban components | `deals.routes.ts` only | **LOW** |

No HIGH/CRITICAL callers outside `crm-deals`.

## Notes

- Single-step advance only (no drag multi-hop); matches backend `STAGE_TRANSITIONS`.
- Win/delete still use native `confirm` (same as lead convert); lose uses Material dialog per acceptance.
- Browser E2E not run in this worktree (no browser MCP); verification via Vitest unit wiring.
- No push (forge rule).
