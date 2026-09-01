---
status: done
id: p2-fe-rule-impact
wave: P
pr: P2
---

# p2-fe-rule-impact

## Summary

Wave P2: extract a read-only **rule impact** dialog from the 649-line rule-set detail surface. Nested rules table gains an **Impact** button per row → `RuleImpactDialogComponent` → existing `RuleService.analyzeImpact(id)` (`GET /api/v1/rules/{id}/impact`) showing all `RuleImpactDto` fields. Scope is `frontend/libs/cpq/rule-sets` only — no shell (`app.routes` / sidebar / tsconfig / `test:cpq`), no `pricing-data-access` edits, no `/api/v1/configuration/rules`, no NgRx, no Java, no `00-orchestrator-status`, dual-read untouched.

## Acceptance

| Criterion | Result |
|-----------|--------|
| Extracted dialog/panel (not 100+ lines dumped into detail template) | Yes — `rule-impact-dialog` |
| Per nested rule **Impact** button | Yes — `openRuleImpact` on detail |
| Calls `RuleService.analyzeImpact(id)` | Yes |
| Shows `RuleImpactDto` fields | Yes — id/name/type/status/priority, rule-set id/status/active, totals, executions all-time + 30d |
| No shell / no `pricing-data-access` edit | Confirmed |
| Never `/api/v1/configuration/rules` | Guarded in api-contract + sources |
| Vitest green | `nx test cpq-rule-sets --skip-nx-cache` |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/rule-sets/src/lib/rule-impact-dialog/*` | New dialog + Vitest |
| `frontend/libs/cpq/rule-sets/src/lib/rule-set-detail/rule-set-detail.component.ts` | Impact button + open dialog |
| `frontend/libs/cpq/rule-sets/src/lib/rule-set-detail/rule-set-detail.component.spec.ts` | Opens impact dialog |
| `frontend/libs/cpq/rule-sets/src/lib/rule-sets.api-contract.spec.ts` | Cover impact dialog + analyzeImpact wire |
| `frontend/libs/cpq/rule-sets/src/index.ts` | Export dialog + data type |
| `frontend/libs/cpq/rule-sets/README.md` | Document impact HTTP |
| `docs/forge/impl/p2-fe-rule-impact.md` | This note |

## Behavior notes

- Dialog loads on open; error state offers Retry; Close dismisses.
- Title uses optional `ruleName` from the row, then prefers `RuleImpactDto.ruleName` once loaded.
- Detail host change is a single **Impact** button + thin `openRuleImpact` — impact UI lives in the extracted component.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`) — Worktree: `.worktrees/forge-p2` (`feat/forge-p2-fe-rule-impact`)  
Index: primary HEAD (CLI may report 2 commits behind — wave doc notes `.worktrees/` false-stale).

| Symbol | Risk | Notes |
|--------|------|-------|
| FE `analyzeImpact` (`--file` rule.service.ts) | **UNKNOWN** (0 callers) | `rg` confirms only service + service spec + backlog — **no UI callers** before P2. Safe to *call*; did **not** edit data-access. |
| FE `RuleService` | **LOW** exact (barrel + CPQ importers) | Do not edit; UI additive only |
| `RuleSetDetailComponent` | **LOW** (barrel + routes) | Host button only |

**WARN:** UNKNOWN ≠ unused — confirmed with `rg analyzeImpact` / `rg RuleImpactDto` that UI was unused before this PR. No HIGH/CRITICAL in-scope. Did **not** flip dual-read. Did **not** edit shell or `pricing-data-access`. Did **not** touch `00-orchestrator-status.md`.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-rule-sets --skip-nx-cache

rg "analyzeImpact" frontend/libs/cpq/rule-sets
rg "openRuleImpact|RuleImpactDialogComponent" frontend/libs/cpq/rule-sets

# Forbidden prefixes must stay absent from feature sources
rg "/api/v1/configuration/rules|/api/v1/pricing/rule-sets" frontend/libs/cpq/rule-sets/src --glob '!**/*.spec.ts' || true
```

## Out of scope

- Shell / `pricing-data-access` / configuration-rules / NgRx / Java / dual-read flip / orchestrator status
