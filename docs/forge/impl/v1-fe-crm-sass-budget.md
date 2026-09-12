---
status: done
id: v1-fe-crm-sass-budget
---

# v1-fe-crm-sass-budget

## Summary

Wave V1 CRM Sass + initial budget: move Tailwind v4 into a plain CSS entry (`tailwind.css` with CSS `@import "tailwindcss"`), remove Sass `@import 'tailwindcss'` from `styles.scss` (and the false “Tailwind requires it” comment), register both style files in `project.json`, and raise production initial `maximumWarning` to **750kb** (keep `maximumError` **1mb**) citing measured **630.44 kB**. No `app.routes` / sidebar / shell / lazy splits; no V2/V3; no Java / NgRx / dual-read / `00-orchestrator-status`.

## Files

| Path | Change |
|------|--------|
| `frontend/apps/crm/src/tailwind.css` | **New** — CSS `@import "tailwindcss";` (Tailwind 4.1.18 style export) |
| `frontend/apps/crm/src/styles.scss` | Removed Tailwind Sass `@import` + false comment; keep Material `@use` + resets |
| `frontend/apps/crm/project.json` | `styles`: `styles.scss` + `tailwind.css`; initial `maximumWarning` **750kb**; comment cites 630.44 kB |
| `docs/forge/impl/v1-fe-crm-sass-budget.md` | this status note |

## Acceptance

- [x] No Sass `@import 'tailwindcss'` / no `@use 'tailwindcss'` / no `--silence-deprecation=import`
- [x] `project.json` styles include both `styles.scss` and `tailwind.css`
- [x] Initial budget: `maximumWarning` **750kb**, `maximumError` **1mb**
- [x] `nx build crm --configuration=production` — no Sass `import` deprecation from Tailwind; initial **630.43 kB** still **< 1mb** (under 750kb warning; no budget warning)
- [x] No `app.routes` / sidebar / shell / extra lazy splits
- [x] No catalog `ProductService` (V2), deal-kanban (V3), Java, NgRx, dual-read YAML, `00-orchestrator-status.md`
- [x] No Wave T / T1 / T2, R2 list-row delete, N/S/U reopen

## Out of scope (this wave)

- V2 catalog ProductService errorCode
- V3 deal-kanban empty/error/loading a11y
- V4 docs closeout
- Extra `loadChildren` / Material shrink / raising error budget
- Activities / #238 / #241 / dual-read flip / NgRx / R2 / named Wave T

## Notes

- GitNexus: impact on CSS/`project.json` targets **UNKNOWN** (not indexed symbols). `rg` SSOT: `styles.scss` referenced only from `apps/crm/project.json` `options.styles`. Plan risk **MED** (global CSS order Material vs Tailwind) — styles array order: `styles.scss` then `tailwind.css`.
- Tailwind `exports["."].style` = `./index.css` (plain CSS `@layer`), not a Sass module.
