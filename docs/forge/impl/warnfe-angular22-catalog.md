---
status: green
id: warnfe-angular22-catalog
wave: WARN-FE
pr: WARN-FE
---

# warnfe-angular22-catalog

## Summary

WARN-FE only: browserslist `caniuse-lite` DB refresh + npm 12 install-scripts allow-list for esbuild/nx (and the four other packages blocked on the same `npm ci`). Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. No `frontend/package.json` version bumps. `npm ci --legacy-peer-deps` stays. No NgRx. No UAT-1. No BE. Did **not** edit `docs/forge/00-orchestrator-status.md`.

Tip: `origin/main` @ `10093e56`. Branch `fix/forge-warnfe-angular22`. Worktree `/home/username01/Projects/open-crm-cpq/.worktrees/fe-warn`. Real `frontend/node_modules` (not a symlink to primary).

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fe-warn`  
Index: `04909db` (2026-09-13), behind HEAD `10093e56`. Plan 38: **skip analyze** (stale vs tip). UNKNOWN ≠ unused.

| Target | Risk | Confirmation |
|--------|------|----------------|
| `allowScripts` | **UNKNOWN** (not a symbol) | `impact` error: target not found. `rg` **zero** `allowScripts`/`allow-scripts` in tree before this PR |
| `nx` | **UNKNOWN** (not a symbol) | same |
| `caniuse-lite` | **UNKNOWN** (not a symbol) | same; lockfile-only DB refresh |

No functions/classes/methods edited. Toolchain files only. Blast radius is all 20 FE projects' install/build toolchain — plan **MED**, not application HIGH/CRITICAL. Did not treat empty callers as unused.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "allowScripts" --direction upstream --repo /home/username01/Projects/open-crm-cpq
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "nx" --direction upstream --repo /home/username01/Projects/open-crm-cpq
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "caniuse-lite" --direction upstream --repo /home/username01/Projects/open-crm-cpq
```

## GitNexus (pre-commit)

From this worktree, files staged, `detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq`:

```text
Diff touched 3 file(s) but no indexed symbols overlap those hunks — not a clean tree.
```

No `partial` / `truncated`. Checkout confirmed: this worktree's three exclusive files (`frontend/package-lock.json`, `frontend/.npmrc`, `docs/forge/impl/warnfe-angular22-catalog.md`). Zero overlapping indexed symbols is expected (lockfile / npmrc / docs). `rg` dual-read YAML untouched.

## Toolchain

```text
node v26.7.0
npm 12.0.2
NX_DAEMON=false
npm ci --legacy-peer-deps
```

Host `ignore-scripts=false`. npm 12 default: dependency install scripts opt-in via `allowScripts` / `.npmrc` `allow-scripts`. Canonical `package.json#allowScripts` is **out of exclusive files** this PR; project `.npmrc` is the authorized path (npm docs: team policy in `package.json` **or** `.npmrc`; CLI `--allow-scripts` on project-scoped `npm ci` is `EALLOWSCRIPTS`).

## RED (tip lockfile, no `.npmrc`)

### `npm ci --legacy-peer-deps` EXIT 0

Quoted stderr:

```text
npm warn install-scripts 6 packages had install scripts blocked because they are not covered by allowScripts:
npm warn install-scripts   @parcel/watcher@2.6.0 (install: node scripts/build-from-source.js)
npm warn install-scripts   @swc/core@1.16.2 (postinstall: node postinstall.js)
npm warn install-scripts   esbuild@0.28.2 (postinstall: node install.js)
npm warn install-scripts   lmdb@3.5.6 (install: node-gyp-build-optional-packages)
npm warn install-scripts   msgpackr-extract@3.0.4 (install: node-gyp-build-optional-packages)
npm warn install-scripts   nx@23.2.1 (postinstall: node -e "try{require('./dist/bin/post-install')}catch(e){}")
npm warn install-scripts
npm warn install-scripts Run `npm install-scripts ls` to review, or `npm install-scripts approve <pkg>` to allow.
```

`file node_modules/esbuild/bin/esbuild` → **Node.js script executable, ASCII text** (postinstall did not replace the JS stub with the native binary).

Also (not WARN-FE-fixable without `package.json` dep bumps):

```text
npm warn deprecated whatwg-encoding@2.0.0: Use @exodus/bytes instead for a more spec-conformant and faster implementation
npm warn deprecated @angular/animations@22.1.6: @angular/animations is deprecated. Use `animate.enter` and `animate.leave` instead. For more information see: https://v22.angular.dev/guide/animations.
```

### Production build advisory

`NX_DAEMON=false npx nx build crm --configuration=production --skip-nx-cache` **SUCCESS** (initial **670.95 kB** ≤ 750 kb warn). Quoted stderr:

```text
Browserslist: browsers data (caniuse-lite) is 8 months old. Please run:
  npx update-browserslist-db@latest
  Why you should do it regularly: https://github.com/browserslist/update-db#readme
```

Lockfile `caniuse-lite` **1.0.30001769**. No NG8xxx.

## GREEN

### (a) browserslist DB refreshed — advisory gone

`npx update-browserslist-db@latest` (`.npmrc` moved aside for the nested `npm install caniuse-lite` child, which treats inherited `--allow-scripts` as CLI → `EALLOWSCRIPTS`; then restored). `package.json` **unchanged**.

| Package | From | To |
|---------|------|----|
| `caniuse-lite` | 1.0.30001769 | **1.0.30001810** |
| `baseline-browser-mapping` | 2.9.19 | **2.11.23** |

Did **not** add `frontend/.browserslistrc` — DB refresh was sufficient. Re-run production build stderr has **no** `Browserslist:` / `caniuse-lite` line. SUCCESS, initial **670.95 kB**.

### (b) install-scripts execute

`frontend/.npmrc`:

```ini
allow-scripts=esbuild,@swc/core,nx,@parcel/watcher,lmdb,msgpackr-extract
```

All six RED-blocked packages (plan named esbuild/nx; the other four were on the same `npm ci` line). Not `dangerously-allow-all-scripts`. Not `package.json#allowScripts` (exclusive-file).

Fresh `rm -rf node_modules && npm ci --legacy-peer-deps` EXIT 0. **No** `install-scripts … blocked` lines. `npm install-scripts ls` → `No packages with unreviewed install scripts.` `file node_modules/esbuild/bin/esbuild` → **ELF 64-bit LSB executable** (postinstall ran). Real `node_modules` at this worktree path (not a symlink).

### (c) build + tests

```bash
export NX_DAEMON=false
npx nx build crm --configuration=production --skip-nx-cache   # SUCCESS
npx nx run-many -t test --all --parallel=3 --skip-nx-cache    # 20/20
```

| Gate | Result |
|------|--------|
| production build | **SUCCESS**, 670.95 kB, no browserslist advisory, no NG8xxx |
| `nx run-many -t test --all --parallel=3 --skip-nx-cache` | **Successfully ran target test for 20 projects**, wall **5m 30s** |

20 projects: cpq-pricing-data-access, cpq-pricing-customers, cpq-pricing-simulator, cpq-product-bundles, shared-data-access, crm-organizations, cpq-price-books, cpq-pricing-ui, cpq-approvals, cpq-rule-sets, crm-dashboard, shared-models, crm-contacts, shared-utils, cpq-catalog, cpq-quotes, crm-deals, crm-leads, shared-ui, crm.

## Third-party documented skips (not suppressed)

| Line | Why skip |
|------|----------|
| `npm warn deprecated @angular/animations@22.1.6` | Angular 22 deprecation; removing the dep is a `package.json` bump (exclusive-file **out**). Not NG8xxx. |
| `npm warn deprecated whatwg-encoding@2.0.0` | Transitive (jsdom). Same: no dep bump this PR. |

Did **not** use `-nowarn` / log-level suppression.

## Files

| Path | Change |
|------|--------|
| `frontend/package-lock.json` | `caniuse-lite` 1.0.30001769 → 1.0.30001810; `baseline-browser-mapping` 2.9.19 → 2.11.23 |
| `frontend/.npmrc` | npm 12 `allow-scripts` for the six RED-blocked packages |
| `docs/forge/impl/warnfe-angular22-catalog.md` | This note |

Did **not** edit `frontend/package.json`. Did **not** add `.browserslistrc`. Did **not** edit `backend/**`, CI, `docs/ARCHITECTURE.md`, `AGENT.md`, or `docs/forge/00-orchestrator-status.md`.

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. NgRx **held**. Activities API **not invented**. Did **not** start UAT-1 or BE (IT-1/IT-2/WARN-BE).

## Acceptance

- [x] RED: `npm ci --legacy-peer-deps` stderr quoted (esbuild/nx + four siblings blocked)
- [x] RED: production-build browserslist `caniuse-lite` advisory quoted
- [x] GREEN (a): DB refreshed; advisory gone on re-build
- [x] GREEN (b): fresh `npm ci --legacy-peer-deps` runs esbuild/nx install-scripts (ELF esbuild; `install-scripts ls` empty)
- [x] GREEN (c): production build SUCCESS **and** tests **20/20**
- [x] No `frontend/package.json` version changes
- [x] `npm ci --legacy-peer-deps` stays
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] No NgRx; no UAT-1; no BE; no `00-orchestrator-status.md`
