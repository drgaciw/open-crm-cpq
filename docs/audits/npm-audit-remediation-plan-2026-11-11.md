# Frontend npm audit remediation plan — open-crm-cpq

**Date:** 2026-11-11, 2:02 PM CST (America/Chicago)
**Repo:** [drgaciw/open-crm-cpq](https://github.com/drgaciw/open-crm-cpq)
**Source commit (`origin/main`):** `18e8aacf57e03c3c938b61357afdd8c7990efe85` (DRG-472 slice a, #971)
**Trigger:** “Frontend npm audit (PR gate)” job added by #972 (`fd3fd096`). Failing run [43090421913](https://github.com/drgaciw/open-crm-cpq/actions/runs/43090421913) at 1:47 PM CST. Command, in `frontend/`: `npm ci && npm audit --audit-level=high`.
**Why this is urgent:** every open PR against main now hits this same gate. Fixing the four highs on main unblocks the whole PR queue.

## Summary

| | |
|---|---|
| Job directory | `frontend/` (from `.github/workflows/pr-check.yml`, job `frontend-npm-audit`) |
| Audit Node (the failing job) | `26.13.0` (npm 11.20.0 in this reproduction) |
| Frontend build/test job Node | `24.22.3` (local verification used Sprite Node 24.22.1) |
| On main today | 27 vulnerabilities: **4 high**, 23 moderate, 0 critical |
| Recommended next step | One small **lockfile-only** PR bumping the four high packages. No `overrides`, no `--force`, no nx/eslint/angular bump. |
| Scoring / merge | Score with **CPQ Decision**. **drg** merges before the queue resumes. This plan branch does not open that PR. |

**Top priority:** land a lockfile-only `npm update` of the four high packages on main; that is enough for `npm audit --audit-level=high` to pass and for the queue to move again.

**Local verification (not committed):** after `npm update @babel/plugin-transform-modules-systemjs browserslist fast-uri follow-redirects` in `frontend/` on a scratch tree of `18e8aacf`:

- `npm audit --audit-level=high` exited **0** under Node 26.13.0 / npm 11.20.0 (0 high, 23 moderate remaining).
- `bash scripts/ci/run-core.sh frontend` exited **0** under Node 24.22.1: `crm:build:production` green, lint 20/20, test 20/20.
- `frontend/package.json` was unchanged. Only `frontend/package-lock.json` changed. Those edits were **reverted** and are not on this branch.

A blanket `npm audit fix` also cleared the highs, but it additionally downgraded `@eslint/js` from 10.0.1 to **10.0.0** (npm: “This version should not be used.”). Do **not** ship that side effect. The targeted update does not touch `@eslint/js`.

---

## The four high findings

The failing log also names `minimatch` (via `eslint` / `@eslint/config-array` and `nx` / `@nx/devkit`). That advisory is **moderate**, not high. It is in the later batch below. The four highs, reproduced with `npm audit --json` on this SHA, are:

### 1. `@babel/plugin-transform-modules-systemjs` — do now

| | |
|---|---|
| Advisory | [GHSA-fv7c-fp4j-7gwp](https://github.com/advisories/GHSA-fv7c-fp4j-7gwp) (high). Generates arbitrary code when compiling malicious input. |
| Installed | **7.29.0** |
| Vulnerable range | npm report `7.12.0 - 7.29.0`; advisory `>=7.12.0 <=7.29.3` |
| Patched | **7.29.4+** (latest 7.x: **7.29.8**; 8.1.0 exists but is a major and is not required) |
| Path | `@nx/js@24.0.5` → `@babel/preset-env@7.29.0` → `@babel/plugin-transform-modules-systemjs@7.29.0` |
| How to fix | `npm update @babel/plugin-transform-modules-systemjs` (verified → **7.29.8**, plus `@babel/helper-plugin-utils` 7.28.6 → 7.29.7). No `overrides`. `npm audit fix` without `--force` also works, but see the `@eslint/js` warning above. |
| Effort | small (part of one lockfile PR) |
| Breaking-change risk | **low**. Stays on the 7.29 line. Locally build/lint/test passed. |
| Now or batch | **Now** — include in the single high-fix PR. |

### 2. `browserslist` — do now

| | |
|---|---|
| Advisories | [GHSA-c83g-rgw3-j3cx](https://github.com/advisories/GHSA-c83g-rgw3-j3cx) (high, unbounded memory / OOM) and [GHSA-73wf-gq98-2v4g](https://github.com/advisories/GHSA-73wf-gq98-2v4g) (high, crash / prototype write via untrusted `browserslist-stats.json`). |
| Installed | **4.28.1** |
| Vulnerable range | `<=4.28.6` |
| Patched | **4.28.7+** (latest 4.x: **4.29.5**) |
| Path | Direct from `@angular/build@22.2.6` (and `update-browserslist-db`); also `@angular/compiler-cli@22.2.5` → `@babel/core@8` → `@babel/helper-compilation-targets`, and `@nx/js@24.0.5` → `@babel/core@7` / `@babel/preset-env` / `core-js-compat`. All resolve to one `browserslist@4.28.1`. |
| How to fix | `npm update browserslist` (verified → **4.29.5**, plus the usual `caniuse-lite` / `electron-to-chromium` / `node-releases` / `update-browserslist-db` / `baseline-browser-mapping` dataset bumps). No `overrides`. |
| Effort | small (same lockfile PR) |
| Breaking-change risk | **low**. Minor 4.28 → 4.29. Locally build/lint/test passed. |
| Now or batch | **Now** — include in the single high-fix PR. |

### 3. `fast-uri` — do now

| | |
|---|---|
| Advisories (high) | [GHSA-v2hh-gcrm-f6hx](https://github.com/advisories/GHSA-v2hh-gcrm-f6hx), [GHSA-7p8r-x3mc-p8w7](https://github.com/advisories/GHSA-7p8r-x3mc-p8w7), [GHSA-q3j6-qgpj-74h6](https://github.com/advisories/GHSA-q3j6-qgpj-74h6), [GHSA-v39h-62p7-jpjc](https://github.com/advisories/GHSA-v39h-62p7-jpjc), [GHSA-f65p-4m7j-42xc](https://github.com/advisories/GHSA-f65p-4m7j-42xc), [GHSA-jqff-g426-hqxp](https://github.com/advisories/GHSA-jqff-g426-hqxp), [GHSA-4c8g-83qw-93j6](https://github.com/advisories/GHSA-4c8g-83qw-93j6), [GHSA-qw65-cvwx-89v3](https://github.com/advisories/GHSA-qw65-cvwx-89v3). Two moderate and two low advisories on the same 3.1.x line are cleared by the same bump to 3.1.9. |
| Installed | **3.1.0** |
| Vulnerable range (as reported) | `3.0.0 - 3.1.8` (highs are all fixed by **3.1.7+**; remaining moderate/low need **3.1.9**) |
| Patched | **3.1.9** (latest 3.x). 4.2.3 is a major and is not required. |
| Path | `@angular-devkit/core@22.2.6` → `ajv@8.20.0` → `fast-uri@3.1.0` |
| How to fix | `npm update fast-uri` (verified → **3.1.9**). No `overrides`. |
| Effort | small (same lockfile PR) |
| Breaking-change risk | **low**. Patch on 3.1.x, not the 4.x major. Locally build/lint/test passed. |
| Now or batch | **Now** — include in the single high-fix PR. |

### 4. `follow-redirects` — do now

| | |
|---|---|
| Advisories | [GHSA-q3jh-wrc6-jgq8](https://github.com/advisories/GHSA-q3jh-wrc6-jgq8), [GHSA-8r9p-f939-6h3c](https://github.com/advisories/GHSA-8r9p-f939-6h3c) (both high, `<1.16.1`), and [GHSA-mrwp-f3q8-3g9p](https://github.com/advisories/GHSA-mrwp-f3q8-3g9p) (high, `<=1.16.1`). |
| Installed | **1.16.0** |
| Vulnerable range | `1.16.0 - 1.16.1` |
| Patched | **1.16.2+** (latest: **1.16.3**) |
| Path | `@nx/web@24.0.5` → `http-server@14.1.1` → `http-proxy@1.18.1` → `follow-redirects@1.16.0` |
| How to fix | `npm update follow-redirects` (verified → **1.16.3**). No `overrides`. |
| Effort | small (same lockfile PR) |
| Breaking-change risk | **low**. Patch on 1.16.x. Locally build/lint/test passed. |
| Now or batch | **Now** — include in the single high-fix PR. |

---

## Sequencing

### 1. Single small fix PR on main (do this first)

On a fresh branch off current `origin/main`, in `frontend/`, under Node 26.13.0 if practical (the audit job’s version):

```bash
npm ci
npm update @babel/plugin-transform-modules-systemjs browserslist fast-uri follow-redirects
npm audit --audit-level=high   # must exit 0
```

Commit **only** `frontend/package-lock.json`. Do not change `package.json`. Do not add `overrides`. Do not run `npm audit fix --force`. Do not downgrade `@eslint/js`, `eslint`, or any `nx` / `@nx/*` package.

Suggested title: `chore(frontend): bump four high npm audit transitives`.

Then:

1. Confirm CI: “Frontend npm audit (PR gate)” green, and “Frontend (PR gate)” green (`scripts/ci/run-core.sh frontend`).
2. Score the PR with **CPQ Decision**.
3. **drg** merges it onto main **before the queue resumes**.

This unblocks the whole PR queue: every PR is failing the same `npm audit --audit-level=high` check against main `18e8aacf`.

### 2. Later batch — the 23 moderates

Do **not** hold the high-fix PR for these. `npm audit fix --force` is the wrong tool: npm’s suggested “fix” is a **breaking downgrade** (`@nx/*@18.3.5` from 24.0.5, `eslint@9.39.5` from 10.x, and in one case `typescript-eslint@8.36.0` from 8.72.1).

| Group | Packages / advisories | Suggested later action | Risk |
|---|---|---|---|
| Easy lockfile bumps (`fixAvailable: true`, no `--force`) | `fflate` 0.8.2 → 0.8.3 ([GHSA-px8p-9vwx-vf98](https://github.com/advisories/GHSA-px8p-9vwx-vf98)); `qs` 6.16.0 → 6.17.1 ([GHSA-6pq8-jhm2-2vcw](https://github.com/advisories/GHSA-6pq8-jhm2-2vcw)); `cosmiconfig`’s `yaml` 1.10.2 → 1.10.3 ([GHSA-48c2-rrv3-qjmp](https://github.com/advisories/GHSA-48c2-rrv3-qjmp)) | A later lockfile PR (`npm update fflate qs yaml`, or a careful `npm audit fix` **after** confirming `@eslint/js` stays on 10.0.1). | Low |
| `minimatch` (the named moderate) | [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249), range `7.0.0 - 11.0.0`. Installed `10.2.5` (via `nx@24.0.5` / `@nx/devkit@24.0.5`) and `10.2.6` (via `eslint@10.14.0`). Also pulled by `@eslint/config-array@0.23.5` and `@typescript-eslint/typescript-estree@8.72.1`. Patched: **11.0.1+** (latest **11.0.8**). | Prefer an `overrides` entry `"minimatch": "11.0.8"` (or `>=11.0.1`) plus `npm install`, then re-run lint and tests. Do **not** accept `npm audit fix --force`. A parent bump of nx / eslint / typescript-eslint is only needed if the override breaks those packages. | Medium (override into a new major of `minimatch`); verify lint/test before merge |
| Advisory “parents” of `minimatch` | `@eslint/config-array`, `eslint`, `@eslint/js`, `nx`, `@nx/angular`, `@nx/devkit`, `@nx/eslint`, `@nx/eslint-plugin`, `@nx/js`, `@nx/vite`, `@nx/vitest`, `@nx/web`, `@nx/workspace`, `typescript-eslint` and `@typescript-eslint/*` (6.16.0–8.72.2) | These are moderate only because they depend on vulnerable `minimatch`. They should clear once the override (or a real upstream bump) lands. Do not downgrade eslint 10 → 9 or nx 24 → 18. | n/a if handled via `minimatch` override |

`@eslint/js@>=10.0.1` is listed as moderate only as a parent of `eslint`. A blanket `npm audit fix` “solves” it by installing deprecated `@eslint/js@10.0.0`. Leave it on **10.0.1** until the `minimatch` override or an eslint patch is ready.

### 3. Out of scope / notes

- This plan branch is **docs only**. It does not open the fix PR and does not change the lockfile.
- Do not reuse `docs/jdk25-audit-2026-11-11` (already merged as #973).
- The audit job pins Node **26.13.0** while the frontend build/test job pins **24.22.3**. The highs reproduced on 26.13.0. Harmonizing those Node versions is a separate cleanup, not required to unblock the queue.
- No merges, and no edits to other branches, were made for this plan.

No secrets or tokens are included in this file.
