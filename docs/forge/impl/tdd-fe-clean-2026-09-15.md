---
status: green
---
# tdd-fe-clean-2026-09-15

## Scope note

**FE Testcontainers / Playwright e2e are absent in-repo.** This round is the full Nx **Vitest + build** gate only — not Docker integration tests and not browser e2e.

Prior green closeouts: [tdd-fe-it-green.md](./tdd-fe-it-green.md), [tdd-fe-it-rerun.md](./tdd-fe-it-rerun.md), [tdd-fe-it-2026-09-13.md](./tdd-fe-it-2026-09-13.md) (20 projects / 94 files / 611 tests). This file records the **2026-09-15** clean rerun on branch `test/tdd-fe-clean-2026-09-15` tipped at `origin/main` `6312a3c`.

## Commands

```bash
cd frontend
# node_modules is a symlink — do not npm ci
export NX_DAEMON=false
npx nx reset
npx nx run-many --target=build --all --parallel=3 --skip-nx-cache
npx nx run-many --target=test --all --parallel=3 --skip-nx-cache
```

## Result

### Build

| Target | Projects | Status |
|--------|----------|--------|
| `build` | **1** (`crm`) | SUCCESS |

- Production bundle OK (generate **31.271s**); initial total **630.43 kB** (within 750 kb warning / 1 mb error budget; **no budget FAIL**)
- No NG8011 compiler diagnostics
- No Sass `@import` deprecation (Tailwind remains CSS `@import` in `tailwind.css`; `styles.scss` uses Material `@use` only)
- Browserslist `caniuse-lite` advisory present; not a compiler/budget failure — left untouched (no `npm ci` / no dep bump)

### Test (Vitest via Nx)

| Project | Test files passed | Tests passed |
|---------|-------------------|--------------|
| shared-models | 2 | 14 |
| crm-organizations | 4 | 14 |
| shared-ui | 7 | 72 |
| cpq-pricing-data-access | 6 | 25 |
| crm-contacts | 3 | 13 |
| crm-leads | 3 | 22 |
| cpq-product-bundles | 8 | 36 |
| cpq-rule-sets | 7 | 32 |
| cpq-quotes | 7 | 79 |
| cpq-pricing-customers | 5 | 29 |
| cpq-pricing-simulator | 3 | 11 |
| shared-data-access | 5 | 34 |
| crm-dashboard | 2 | 6 |
| cpq-approvals | 3 | 15 |
| cpq-price-books | 7 | 53 |
| crm-deals | 5 | 39 |
| cpq-catalog | 10 | 73 |
| crm | 4 | 10 |
| shared-utils | 4 | 36 |
| cpq-pricing-ui | 1 | 1 |
| **Total** | **96** | **614** |

- `NX   Successfully ran target test for 20 projects`
- Failures / errors: **0**
- Wall time (reset + build + test): **418.18s** (~7m)
- Date: 2026-09-15
- HEAD at run: `6312a3c` (`origin/main`)
- Node: v26.7.0; `node_modules` → `/home/username01/Projects/open-crm-cpq/frontend/node_modules` (symlink)
- Code diffs: none (docs-only closeout; gate already green)
- Count delta vs 2026-09-13 closeout: **+2** files / **+3** tests (`crm-organizations` 3→4 / 13→14; `crm-deals` 4→5 / 37→39)

## Holds respected

- Dual-read YAML / `APPROVALS_LEGACY_LOCAL_READ` default **true** — not flipped
- `docs/forge/00-orchestrator-status.md` not edited
- **R2** price-book list-row delete not implemented
- No NgRx; no Activities API invention
- No error-budget inflation
