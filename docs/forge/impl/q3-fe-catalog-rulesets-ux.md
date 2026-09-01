---
status: done
id: q3-fe-catalog-rulesets-ux
wave: Q
pr: Q3
---

# q3-fe-catalog-rulesets-ux

## Summary

Wave Q3: copy H1-shaped local `httpErrorMessage` (`errorCode` / `error.errorCode` → `message [CODE]`) into catalog + rule-sets message-only helpers. Add 768px density wrap for `header-actions` and nested catalog/rule-set sections. Keep P3 `config-rules-panel` extracted. No shell; no Java; catalog HTTP stays `/api/v1/configuration/rules` (never pricing `/api/v1/rules`; no validate UI).

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `httpErrorMessage` (`catalog-detail.component.ts`) | **MEDIUM** (7 same-file callers) | Additive display only — append `[code]` when present |
| FE `httpErrorMessage` (option-create / option-group-create) | **LOW** | 1 same-file caller each |
| FE `httpErrorMessage` (config-rules-panel / config-rule-create / rule-impact) | **UNKNOWN** | Index miss; `rg` confirms same-file callers (2 / 1 / 1) |
| FE `httpErrorMessage` (rule-set-detail / rule-dialog) | **LOW** | 3 / 4 same-file callers |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-q3` (`fix/forge-q3-fe-catalog-rulesets-ux`)  
CLI: `node .gitnexus/run.cjs impact httpErrorMessage --file <path> -d upstream -r /home/username01/Projects/open-crm-cpq`  
Index stale (~15 commits behind). **UNKNOWN ≠ unused** — confirmed with `rg`.  
**WARN:** catalog-detail MEDIUM — additive string formatting only (H1 pattern).

## Files

| Path | Change |
|------|--------|
| `catalog-detail.component.ts` (+spec) | H1 helper; 768px wrap header-actions / nested sections; specs mock `{ message, errorCode }` |
| `option-create-dialog.component.ts` | H1 helper |
| `option-group-create-dialog.component.ts` | H1 helper |
| `config-rules-panel.component.ts` (+spec) | H1 helper; 768px section-header wrap; list-error spec |
| `config-rule-create-dialog.component.ts` (+spec) | H1 helper; create-error spec |
| `rule-set-detail.component.ts` (+spec) | H1 helper; 768px header-actions / rules-header wrap; activate-error spec |
| `rule-dialog.component.ts` (+spec) | H1 helper; validate-error spec |
| `rule-impact-dialog.component.ts` (+spec) | H1 helper; analyzeImpact-error spec |
| `docs/forge/impl/q3-fe-catalog-rulesets-ux.md` | This note |

## Acceptance

- [x] Helpers match H1: `errorCode` / `error.errorCode` → `` `${message} [${code}]` ``
- [x] Specs mock `{ message, errorCode }` and assert `message [CODE]`
- [x] 768px wrap for header-actions + nested catalog / rule-set sections
- [x] `config-rules-panel` remains extracted (not dumped into catalog-detail)
- [x] Catalog HTTP `/api/v1/configuration/rules` only; never pricing `/api/v1/rules`; no validate UI
- [x] No shell (`app.routes` / sidebar / tsconfig / `test:cpq`); no quotes-detail / shared-ui / product-bundles
- [x] No Java, NgRx, dual-read flip, `00-orchestrator-status.md`
- [x] Vitest `cpq-catalog` + `cpq-rule-sets` green

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
NX_DAEMON=false npx nx test cpq-rule-sets --skip-nx-cache
```

Result (2026-08-31): `cpq-catalog` **10 files / 69 tests passed**; `cpq-rule-sets` **7 files / 32 tests passed**.  
Browser E2E not available in this agent session — density wrap + errorCode banners verified via Vitest mocks/`errorCode` assertions.

## detect-changes (pre-commit)

From worktree cwd: `detect-changes --scope all` → **14 files / 35 symbols / 0 processes / risk LOW**.  
`--scope compare --base-ref main` same. Diff paths: catalog + rule-sets helpers/specs + this note only (no shell / routes / Java / `00-orchestrator-status`).

## Out of scope

- Shell / routes / sidebar / tsconfig / `test:cpq`
- quotes-detail / shared-ui / product-bundles
- Product `POST /configuration/validate` UI
- Pricing `/api/v1/rules` from catalog
- Java / NgRx / dual-read / Activities / #238 / #241 / WhatsApp #430 / subscriptions FE / ADR-006 / N′′ / R2
- `docs/forge/00-orchestrator-status.md`
