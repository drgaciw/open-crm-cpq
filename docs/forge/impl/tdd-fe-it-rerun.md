---
status: green
---
# tdd-fe-it-rerun

## Scope note

**FE Testcontainers / Playwright e2e are absent in-repo.** This round is the full Nx **Vitest + build** gate only — not Docker integration tests and not browser e2e.

Prior green closeout: [tdd-fe-it-green.md](./tdd-fe-it-green.md) (20 projects / 611 tests). This file records a **rerun** on branch `test/tdd-fe-it-2`.

## Commands

```bash
cd frontend
# node_modules is a symlink — do not npm ci
NX_DAEMON=false npx nx run-many --target=build --all --parallel=3 --skip-nx-cache
NX_DAEMON=false npx nx run-many --target=test --all --parallel=3 --skip-nx-cache
```

## Result

### Build

| Target | Projects | Status |
|--------|----------|--------|
| `build` | **1** (`crm`) | SUCCESS |

- Production bundle OK (~24s); initial total **630.43 kB** (within 750 kb warning budget)

### Test (Vitest via Nx)

| Project | Test files passed | Tests passed |
|---------|-------------------|--------------|
| shared-models | 2 | 14 |
| shared-ui | 7 | 72 |
| crm-organizations | 3 | 13 |
| cpq-pricing-data-access | 6 | 25 |
| crm-contacts | 3 | 13 |
| crm-leads | 3 | 22 |
| cpq-rule-sets | 7 | 32 |
| cpq-quotes | 7 | 79 |
| cpq-product-bundles | 8 | 36 |
| shared-data-access | 5 | 34 |
| cpq-pricing-customers | 5 | 29 |
| cpq-pricing-simulator | 3 | 11 |
| cpq-approvals | 3 | 15 |
| cpq-price-books | 7 | 53 |
| crm-dashboard | 2 | 6 |
| crm-deals | 4 | 37 |
| cpq-catalog | 10 | 73 |
| crm | 4 | 10 |
| shared-utils | 4 | 36 |
| cpq-pricing-ui | 1 | 1 |
| **Total** | **94** | **611** |

- `NX   Successfully ran target test for 20 projects`
- Failures / errors: **0**
- Wall time (test run-many): ~4m 32s (272s)
- Date: 2026-09-12
- HEAD at run: `bd303d3`
- Node: v26.7.0; `node_modules` → `/home/username01/Projects/open-crm-cpq/frontend/node_modules` (symlink)
- Code diffs: none (docs-only closeout; gate already green)

## Holds respected

- Dual-read YAML / `APPROVALS_LEGACY_LOCAL_READ` untouched
- `docs/forge/00-orchestrator-status.md` not edited
- **R2** price-book list-row delete not implemented
- No H1 helper rewrite
- No NgRx; no Activities API invention
