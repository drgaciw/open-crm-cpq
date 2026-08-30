---
status: done
id: b1-crm-fe-lifecycle
---

# b1-crm-fe-lifecycle

## Summary

PR-FE-2: Expose lead lifecycle actions on the lead **list** UI (parity with detail), wire existing `LeadService` methods, and add Unqualified + priority filters. Priority control on create/edit form. Vitest covers list action wiring (qualify/convert) and service query params.

Search response shape left unchanged (Batch 0). PR-FE-3 (deal pipeline UX) not in this batch.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/leads/src/lib/lead-list/lead-list.component.ts` | Status-gated row menu: Contacted / Qualify / Convert / Lose; Unqualified tab; priority filter + column; filtered empty state |
| `frontend/libs/crm/leads/src/lib/lead-list/lead-list.component.spec.ts` | Vitest: qualify/convert wiring, navigate via `convertedDealId`, tabs/priority filters, status gates |
| `frontend/libs/crm/leads/src/lib/lead.service.ts` | Optional `priority` on `list()` |
| `frontend/libs/crm/leads/src/lib/lead.service.spec.ts` | List status+priority params; qualify POST |
| `frontend/libs/crm/leads/src/lib/lead-form/lead-form.component.ts` | Priority select on create/edit |

## Tests

```bash
cd frontend && npx nx test crm-leads
```

Covers:

- `onQualify` → `LeadService.qualify` + list refresh
- `onConvert` → `convertToDeal` → `/deals/{convertedDealId}` (confirm cancel + missing deal id paths)
- Unqualified tab → `status=UNQUALIFIED`
- Priority filter → `priority` query param
- Service `list`/`qualify` HTTP contracts

## Notes

- GitNexus MCP tools were unavailable in this worktree; blast radius assessed statically (list/form route-scoped; `list()` additive optional param — **LOW**).
- Convert navigation uses `convertedDealId` (Batch 0 contract), not lead id.
- Lose reuses `LoseReasonDialogComponent` from detail.
- FE-3 (deal lose dialog / kanban stage actions) deferred.
