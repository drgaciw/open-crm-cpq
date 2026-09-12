# Wave V architecture — FE polish + non-failing clean-build leftovers

**Status:** READY FOR FORGE — orchestrator locked: Waves **A–U COMPLETE** on `origin/main` @ `4dc72c1`; **N/S/U COMPLETE**; **Wave T skipped**  
**Architect:** `opencrm-architect` (Herdr `w8`) — **plan only; do not spawn implementers**  
**Evidence date:** 2026-09-12  
**Baseline:** `origin/main` @ `4dc72c1` (U closeout `33c9694`; orchestrator `f816101`; TDD NG8011+CE dual-run #478–#482)  
**Survey tip:** `origin/main` @ `4dc72c1`  
**Sources:** `rg` + **read files**. GitNexus **skip analyze**. UNKNOWN ≠ unused; `-r /home/username01/Projects/open-crm-cpq --file`. Linear/Notion Open CRM: **none**.  
**Do not** edit `docs/forge/00-orchestrator-status.md`.

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Reopen N / S / U / N′′** | **REJECT** | Locked complete |
| **Named Wave T / T1 T2** | **REJECT** | Orchestrator skipped T |
| **Former T1** `listLineItems` | **SKIP** | `rg`: **no UI caller** (service L177 + spec L276 only) |
| **R2** list-row delete | **HOLD** | Do not un-hold |
| **New portal / extra `app.routes` lazy** | **REJECT** | All feature routes already `loadChildren`; extra split would fight P1/Q4 |
| **CE Java / dual-run** | **REJECT** | #480 landed; do not reopen |
| **Extract shared `httpErrorMessage`** | **REJECT** | Copy H1 locally |
| **Activities / #238 / #241 / dual-read flip / validate delete / gateway domain / SpEL / Kafka S2B-9 / WhatsApp / NgRx / subscriptions FE / Accept ADR-006** | **HOLD** | Standing |
| **✅ V1 Sass + initial budget (crm app)** | **IN** | Dart Sass `@import` deprecation + 500kb warning vs 630.44 kB; **same** `project.json` |
| **✅ V2 catalog `ProductService` errorCode** | **IN** | list/detail bind `service.error()`; still message-only |
| **✅ V3 deal-kanban empty/error/loading** | **IN** | No error banner; loading no `role="status"`; failed load looks like empty columns |
| **✅ V4 docs closeout** | **IN** | Dual-read J–V; R2 held; T skipped |

**Chosen theme:** FE polish + **non-failing** clean-build. **Zero new portal. Zero chrome owner** (V1 edits `styles` + budgets only — **not** `app.routes` / sidebar / shell).

---

## 1. Problem / evidence (read files, not seeds)

### Git / GitHub / dual-read

```text
HEAD: 4dc72c1
GitNexus: skip analyze
```

| Item | State |
|------|--------|
| Open issues | **#238**, **#241** — **HOLD** |
| Open PRs | **none** |
| Dual-read | `legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |

### V-T1 — Sass `@import` + Tailwind v4 (proved)

`frontend/apps/crm/src/styles.scss` L16–17:

```scss
// Tailwind - using @import as Tailwind requires it
@import 'tailwindcss';
```

L1 already uses Sass `@use '@angular/material' as mat` — mixed module systems.

**Installed:** `tailwindcss@4.1.18` (`frontend/package.json` `^4.1.18`; primary `node_modules`). Package `exports["."].style` = `./index.css` (plain CSS `@layer theme, base, …`). **Not** a Sass module.

| Claim | Verdict |
|-------|---------|
| “Tailwind requires Sass `@import`” | **False.** Tailwind v4 CSS-first requires **CSS** `@import "tailwindcss"` (style export). Sass `@import` is a **Dart Sass deprecation** (`import` rule, Sass 1.80+) |
| `@use 'tailwindcss'` | **Do not.** `@use` loads a **Sass** module with a namespace. Tailwind’s export is CSS; collaborators document `@use` as the wrong tool and Sass+Tailwind as unsupported |
| Keep Sass `@import 'tailwindcss'` | Deprecation remains; Sass may preprocess Tailwind CSS and strip metadata |

**Decision:** stop feeding Tailwind through Sass. New `apps/crm/src/tailwind.css` with CSS `@import "tailwindcss";`. `styles.scss` **drops** the Tailwind `@import` (keep Material `@use` + resets). `project.json` `options.styles`: `styles.scss` **and** `tailwind.css`. Prove with `nx build crm --configuration=production` — **no** Sass `import` deprecation for Tailwind; utilities still apply.

Do **not** silence `--silence-deprecation=import` as the fix.

### V-T2 — initial budget warning (not a fail)

`frontend/apps/crm/project.json` production budgets:

```json
{ "type": "initial", "maximumWarning": "500kb", "maximumError": "1mb" }
```

Measured seed: **630.44 kB** initial → **warning**, under **1mb error**. CI still green.

`app.routes.ts`: **every** feature is already `loadChildren` (dashboard … simulator). Shell + Material + Tailwind + Angular **must** stay in initial. Extra lazy split of Material/shell = chrome fight / HIGH.

**Decision:** raise `maximumWarning` to **`750kb`** (headroom over 630.44 kB; still << 1mb error). Comment in `project.json` citing measured size. **Do not** add/remove routes.

V1 and V2 seeds share `project.json` → **one track (V1)**.

### V-T3 — `product.service.ts` (confirmed)

`product.service.ts` L80, L94, L138: `error.set(err?.error?.message \|\| err?.message \|\| 'An error occurred')`.  
`catalog-list.component.ts` L103–106 / L209: banner binds `this.service.error()`. Catalog-detail also sets `service.error`. Dialogs already have **local** H1 helpers (Q3).

### V-T4 — `listLineItems` (skip)

`quote.service.ts` L177–188 still message-only. `rg listLineItems`: **definition + spec only**. **Skip.**

### V-T5 — deal-kanban vs list (confirmed)

`deal-kanban.component.ts`:

- L49–50: loading spinner **no** `role="status"`
- **No** `service.error()` banner (`rg` zero in file)
- L110–112: per-column `empty-column` “No deals” — OK for a column
- Failed `list()` → empty columns, **not** U3 list’s `role="alert"` + Retry
- Win/lose still `confirm()` / silent `error:` — **out** (not empty/error of load)

Deal-list (U3) already: error banner, loading live region, empty-state only when `!error()`.

---

## 2. Decisions + rejected

### Track 1 — crm app Sass + budget

| Topic | Decision | Rejected |
|-------|----------|----------|
| Tailwind | Plain CSS entry `tailwind.css` + `project.json` `styles[]`; remove Sass `@import 'tailwindcss'` | `@use 'tailwindcss'`; keep deprecated `@import`; `--silence-deprecation=import` |
| Budget | `maximumWarning`: **750kb**; keep `maximumError` **1mb**; comment with 630.44 kB | Extra `loadChildren`; shrinking Material; error-threshold raise |
| Chrome | **No** `app.routes` / sidebar / shell edits | New portal |

### Track 2 — catalog ProductService

| Topic | Decision | Rejected |
|-------|----------|----------|
| Helper | Copy H1 into `product.service.ts` list / getById / search `error.set` | Shared extract; editing catalog-detail NG8011 (#482) |
| UI | List/detail banners unchanged structurally (they already bind `error()`) | New catalog portal |

### Track 3 — deal-kanban

| Topic | Decision | Rejected |
|-------|----------|----------|
| Load error | `role="alert"` + Retry like deal-list; **do not** paint empty board as success | Full `app-empty-state` covering the board |
| Loading | `role="status" aria-live="polite"` | `LoadingService` / shell |
| Column empty | Keep “No deals” per column when load **succeeded** | R2 / list-row delete |

### Track 4 — docs

Dual-read hold **J–V**. R2 held. T skipped. No `00-orchestrator-status.md`.

---

## 3. PR DAG

Disjoint: **crm app styles/budgets** ∥ **catalog ProductService** ∥ **deal-kanban**.

```text
V1 crm Sass + budget              V2 catalog ProductService           V3 deal-kanban
────────────────────              ─────────────────────────           ──────────────
tailwind.css + styles.scss         H1 error.set list/get/search        load error banner
project.json styles + 750kb warn   catalog list/detail banners         loading live region
        │  no app.routes                   │  no routes                        │  no shell
        └──────────────────────────────────┴───────────────────────────────────┘
                                              ▼
                                 V4 docs closeout (no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **V1** | `fix(crm): tailwind CSS entry and initial budget warning` | `styles.scss`, new `tailwind.css`, `apps/crm/project.json`; `docs/forge/impl/v1-fe-crm-sass-budget.md` | **MED** (global CSS) | `nx build crm --configuration=production`: no Sass `import` deprecation from Tailwind; initial size still **< 1mb**; warning threshold 750kb; **no** route changes; smoke: CRM shell still has utility classes if used |
| **V2** | `fix(fe-cpq): product.service errorCode` | `product.service.ts` + spec; `docs/forge/impl/v2-fe-catalog-product-error.md` | **LOW** | list/get/search `message [CODE]`; `nx test cpq-catalog`; no `app.routes` |
| **V3** | `fix(crm): deal kanban load error and loading a11y` | `deal-kanban.component.ts` + spec; `docs/forge/impl/v3-fe-deal-kanban-ux.md` | **LOW** | error banner + Retry; loading `role="status"`; empty columns only when no error; `nx test crm-deals` |
| **V4** | `docs(cpq): Wave V forge closeout` | `docs/forge/impl/v4-wave-v-docs.md` | **LOW** | Dual-read J–V; R2 held; T skipped; **no** `00-orchestrator-status` |

**Day-1 max parallel:** **V1 ∥ V2 ∥ V3** → **V4**.

| Pair | Rule |
|------|------|
| V1 vs V2/V3 | Parallel (app vs libs) |
| V2 vs V3 | Parallel (catalog vs deals) |
| V1 vs chrome | **No** `app.routes` / sidebar / shell |
| T1 / R2 / CE Java | **Forbidden** |

---

## 4. Blast radius

GitNexus **skip analyze**. **`rg` SSOT.**

| Change | `rg` | Risk |
|--------|------|------|
| V1 global styles + budgets | All CRM pages | **MED** — CSS order Material vs Tailwind |
| V2 `ProductService` | catalog-list/detail banners | **LOW** |
| V3 kanban | `DealKanbanComponent` only | **LOW** |
| Dual-read YAML | Flip = **HIGH** — out | — |

**WARN:** Do not flip dual-read. Do not un-hold R2. Do not name T1/T2. Do not `@use` Tailwind. Do not reopen CE (#480). Disambiguate FE vs BE `ProductService` with `--file`.

---

## 5. Herdr layout (orchestrator spawns — architect does not)

| Pane | Agent id | Branch | PR |
|------|----------|--------|-----|
| 1 | `wv-fe-crm-sass-budget` | `fix/forge-v1-fe-crm-sass-budget` | V1 |
| 2 | `wv-fe-catalog-error` | `fix/forge-v2-fe-catalog-product-error` | V2 |
| 3 | `wv-fe-deal-kanban` | `fix/forge-v3-fe-deal-kanban-ux` | V3 |
| 4 | `wv-docs` | `docs/forge-v4-wave-v-closeout` | V4 |

**Chrome owner:** **none.**  
**Model:** Grok 4.5, worktree-isolated. Overlay ≠ merge SSOT.  
**Vitest:** `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`  
**V1 also:** `NX_DAEMON=false npx nx build crm --configuration=production --skip-nx-cache`  
**Java:** none. Do not reopen CE.

---

## 6. Out of scope

- Activities / `/api/v1/activities/**`  
- CI #238; Kafka #241  
- Dual-read YAML flip; product validate delete; gateway domain validation  
- Full SpEL; Kafka S2B-9; WhatsApp #430 undo  
- NgRx; subscriptions FE; Accept ADR-006  
- N′′; **R2**; **named Wave T** / T1 / T2 / `listLineItems`  
- New portal; extra lazy routes; `app.routes` / sidebar / shell  
- CE Java / dual-run reopen (#480)  
- Shared `httpErrorMessage` extract; `LoadingService` global spinner  
- Linear/Notion cmano-clone tickets  

---

## 7. Goal text

```text
Implement Open CRM/CPQ Wave V per docs/forge/34-wave-v-architecture.md in three parallel FE tracks: (1) split Tailwind v4 into apps/crm/src/tailwind.css with CSS @import "tailwindcss"; remove Sass @import from styles.scss; add file to project.json styles; raise initial maximumWarning to 750kb keep error 1mb; do not @use tailwindcss; do not change app.routes; nx build crm --configuration=production; (2) product.service list/getById/search error.set H1 message [CODE]; nx test cpq-catalog; (3) deal-kanban load error banner + Retry like deal-list; loading role=status; do not treat failed load as empty columns; nx test crm-deals. Day-1 V1∥V2∥V3; V4 docs after. No Java. Do not flip dual-read, un-hold R2, name T1/T2, reopen N/S/U, invent Activities, start #238/#241, NgRx, subscriptions FE, or edit 00-orchestrator-status.md. Evidence: docs/forge/impl/v*.md done, vitest/build green.
```

---

## 8. Checklist

- [x] Even with `origin/main` @ `4dc72c1`  
- [x] Tailwind **proved** CSS style export 4.1.18; `@use` rejected; Sass `@import` is deprecation  
- [x] Budget: raise warning, not extra routes  
- [x] `listLineItems` skipped (no UI caller)  
- [x] Dual-read still true; R2 held; T skipped  
- [x] Zero new portal; **no** chrome owner  
- [x] GitNexus analyze skipped; `rg` used  
- [x] Architect **does not spawn** implementers  
- [ ] Orchestrator → Wave V FORGING (`00-*`)  
- [ ] V1–V4 merged  

**PM WARNING:** Do **not** flip dual-read (**HIGH**). Do **not** `@use` Tailwind. Do **not** un-hold R2. Do **not** reopen CE.

**WAVE V — APPROVED FOR FORGE.**
