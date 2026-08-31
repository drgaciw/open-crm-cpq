---
status: done
id: o1-fe-rule-sets
wave: O
pr: O1
---

# o1-fe-rule-sets

## Summary

Wave O1: new Nx feature lib `frontend/libs/cpq/rule-sets` (`@frontend/cpq-rule-sets`, project `cpq-rule-sets`) — list (optional status filter), detail, create rule set, Activate/Deprecate ConfirmDialog, nested rules via `RuleService.listByRuleSet`, create/edit rule with DRL textarea + `validateSyntax` before (edit soft) / after save. Uses existing `RuleSetService` / `RuleService` from `@frontend/cpq-pricing-data-access`. API prefixes **`/api/v1/rule-sets/**`** and **`/api/v1/rules/**` only** — never `/api/v1/configuration/rules` or `/api/v1/pricing/rule-sets`. Owns shell: `app.routes.ts`, `sidebar.component.ts` (label **Rule sets**, path `rule-sets`), `tsconfig.base.json` path alias, `frontend/package.json` `test:cpq`. Standalone + OnPush + signals; no NgRx; `analyzeImpact` left as stretch non-blocker; did not edit `docs/forge/00-orchestrator-status.md`; dual-read YAML untouched.

## Acceptance

| Criterion | Result |
|-----------|--------|
| New lib `@frontend/cpq-rule-sets` mirroring pricing-customers | Yes |
| List + optional status filter | Yes — `list` / `listByStatus` |
| Detail + create dialog | Yes |
| Activate / Deprecate ConfirmDialog | Yes — DRAFT→activate, ACTIVE→deprecate |
| Nested rules via `RuleService.listByRuleSet` (no bare list) | Yes |
| Create/edit rule DRL textarea + `validateSyntax` | Yes — after save (hard); before save on edit (soft) |
| API `/api/v1/rule-sets` + `/api/v1/rules` only | Yes — service + contract Vitest |
| Never `/api/v1/configuration/rules` or `/api/v1/pricing/rule-sets` | Guarded in api-contract spec |
| Sidebar **Rule sets**; route `rule-sets` | Yes |
| `tsconfig.base.json` path + `test:cpq` project | Yes |
| No NgRx / no Java / no subscriptions FE / no config-rules | Confirmed |
| Vitest green | `nx test cpq-rule-sets --skip-nx-cache` — **28 passed** (6 files) |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/rule-sets/**` | New lib: list/detail/create-dialog/rule-dialog + Vitest + API contract |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy route `rule-sets` |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Nav item **Rule sets** |
| `frontend/tsconfig.base.json` | `@frontend/cpq-rule-sets` alias |
| `frontend/package.json` | Append `cpq-rule-sets` to `test:cpq` |
| `docs/forge/impl/o1-fe-rule-sets.md` | This note |

## Behavior notes

- **List:** Page header + status `mat-select` filter; `DataTable` rows; create dialog → navigate to detail.
- **Create set:** Required `name`; optional description/engineType/effectiveDate/expirationDate.
- **Detail:** Activate (DRAFT) / Deprecate (ACTIVE) via ConfirmDialog; nested rules table from `listByRuleSet`; add/edit/delete rules.
- **Rule dialog:** DRL `content` textarea; create → `validateSyntax` after; edit → soft `validateSyntax` before update + hard after; post-save validate failure keeps dialog open.
- **Shell ownership:** O1 exclusive for routes/sidebar/tsconfig path/`test:cpq` this wave.
- **Stretch skipped:** `analyzeImpact` panel (non-blocker).

## GitNexus (pre-edit)

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-o1` (`feat/forge-o1-fe-rule-sets`)

| Symbol | Risk | Notes |
|--------|------|-------|
| FE `RuleSetService` | **LOW** exact (barrel + transitive data-access importers) | Safe; new UI is additive |
| FE `RuleService` | **LOW** exact (same) | Safe; new UI is additive |
| Upstream `rg` | Service + spec + barrel + README/backlog only | **No UI callers** before O1 — confirmed |

**WARN:** UNKNOWN ≠ unused — these symbols were LOW/exact; still confirmed with `rg` that there were zero component callers before this portal. No HIGH/CRITICAL in-scope. Did **not** flip dual-read default. Did **not** call configuration/rules or pricing/rule-sets. Did **not** edit `00-orchestrator-status.md`.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-rule-sets --skip-nx-cache

rg "basePath = '/api/v1/rule-sets'" frontend/libs/cpq/pricing-data-access/src/lib/rule-set.service.ts
rg "basePath = '/api/v1/rules'" frontend/libs/cpq/pricing-data-access/src/lib/rule.service.ts
rg "listByRuleSet|validateSyntax" frontend/libs/cpq/rule-sets

# Forbidden prefixes must stay absent from feature sources
rg "/api/v1/configuration/rules|/api/v1/pricing/rule-sets" frontend/libs/cpq/rule-sets/src --glob '!**/*.spec.ts' || true
```

## Out of scope

- `analyzeImpact` UI (stretch non-blocker)
- `/api/v1/configuration/rules` / product validate
- `/api/v1/pricing/rule-sets`
- NgRx CPQ store / Java / subscriptions FE / bundles portal
- Dual-read default flip / Activities / WhatsApp #430
- `docs/forge/00-orchestrator-status.md`
