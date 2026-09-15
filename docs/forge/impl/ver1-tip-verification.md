---
status: green
id: ver1-tip-verification
wave: VER-1
pr: VER-1
---

# ver1-tip-verification

## Summary

VER-1 (post-merge confidence, non-gating): full tip re-run on `origin/main` @ `e5d60bbb` (VAL-1 `#523`) after the JDK 25 + Angular 22 upgrade. Architect next-steps `/tmp/jdk-ng-next-steps.md` verdict **UPGRADE COMPLETE**. This PR is **docs-only** evidence. No Java, no FE source, no CI, no dual-read flip. Spring Boot parent stays **4.0.6**. JaCoCo line **0.80** not lowered. JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27). HYG-1 already merged as `#524` (`247afb4d`) — not in this PR.

Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** start HYG-1 or other waves.

## GitNexus

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/verify-tip` (`docs/forge-ver1-tip-verification`)  
Docs-only impl note — no indexed code symbols. UNKNOWN ≠ unused; `rg` confirm before commit.

```bash
rg -n 'APPROVALS_LEGACY_LOCAL_READ' backend/services/quote-service/src/main/resources/application.yml
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

Pre-commit (this worktree, file staged): **Diff touched 1 file(s) but no indexed symbols overlap those hunks — not a clean tree.** No `partial`/`truncated`. Checkout confirmed: worktree `docs/forge/impl/ver1-tip-verification.md` only (primary `main` working tree was not the diff). Docs-only hunk after `rg` confirm.

## Toolchain

```text
JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

`newgrp docker` wrapped every `./mvnw` (primary gid docker). Did **not** chmod `docker.sock`. Docker engine **29.7.2**. Node host **v26.7.0**; `npm ci --legacy-peer-deps` in this worktree (real `frontend/node_modules`, not a symlink to primary). Angular **22.1.6**, Nx **23.2.1**, TypeScript **6.0.3**, Analog **2.7.2**, Vite **8.3.0**.

## Files

| Path | Change |
|------|--------|
| `docs/forge/impl/ver1-tip-verification.md` | This note |

## Maven slices (Temurin 25, `newgrp docker`)

Wall: 2026-09-15T07:48:56 → 07:57:40-05:00 (~8m 44s). Tip at run: `e5d60bbb`. ITs not untagged; slices that include ITs were not skipped. `crm+gateway` used the specified `verify -Punit-tests` (units only; failsafe empty; `jacoco.skip` set by that profile).

| Slice | Tests run | Failures | Errors | Skipped | Status |
|-------|----------:|---------:|-------:|--------:|--------|
| libs/common | 86 | 0 | 0 | 0 | SUCCESS |
| libs/security | 13 | 0 | 0 | 0 | SUCCESS |
| libs/rules-engine | 56 | 0 | 0 | 0 | SUCCESS |
| crm-service (`verify -Punit-tests`) | 139 | 0 | 0 | 0 | SUCCESS |
| gateway-service | 54 | 0 | 0 | 0 | SUCCESS |
| pricing-service | 101 | 0 | 0 | 0 | SUCCESS |
| product-service | 28 | 0 | 0 | 0 | SUCCESS |
| quote-service | 31 | 0 | 0 | 0 | SUCCESS |
| pricebook-service | 7 | 0 | 0 | 0 | SUCCESS |
| configuration-engine | 76 | 0 | 0 | 0 | SUCCESS |
| approval-routing | 20 | 0 | 0 | 0 | SUCCESS |
| document-generation | 13 | 0 | 0 | 0 | SUCCESS |
| contract-asset-management | 18 | 0 | 0 | 0 | SUCCESS |
| ai-service | 191 | 0 | 0 | 0 | SUCCESS |
| **Target-module total** | **833** | **0** | **0** | **0** | **BUILD SUCCESS** |

CRM surefire (9 classes, units): ContactCreateRequestJackson 2, ContactService 27, DealService 40, LeadService 37, OrganizationService 20, QuoteAccepted* 11, BaseIntegrationTestUrl 2. Failsafe reports: **0** (profile `unit-tests`). JDK1 recorded 143 for the same command on a pre-merge worktree; this tip’s XML sum is **139**.

### JaCoCo

- Floor **0.80** unchanged in `crm-service/pom.xml` and `pricing-service/pom.xml` (`<minimum>0.80</minimum>`). Plugin **0.8.14**.
- pricing `test`: `jacoco:0.8.14:report` analyzed **31** classes. Line coverage **639/757 ≈ 0.844** (csv 641/759). `jacoco:check` is bound to `verify`, not `test` — not run on this slice; floor not lowered.
- crm `verify -Punit-tests`: `Skipping JaCoCo execution because property jacoco.skip is set` (profile). Floor not lowered.

## Frontend

```bash
cd frontend
npm ci --legacy-peer-deps          # EXIT 0; real node_modules
export NX_DAEMON=false
npx nx build crm --configuration=production --skip-nx-cache
npx nx run-many -t test --all --parallel=3 --skip-nx-cache
```

### Build

| Target | Projects | Status |
|--------|----------|--------|
| `build` (`--configuration=production`) | **1** (`crm`) | SUCCESS |

- Initial total **670.95 kB** (within 750 kb warning / 1 mb error budget; **no budget FAIL**)
- Generate **19.013s**; Nx run **23.3s**; `--skip-nx-cache`
- Browserslist `caniuse-lite` advisory only — not a compiler/budget failure

### Test (Vitest via Nx)

`NX   Successfully ran target test for 20 projects`. Failures **0**. Wall **4m 23s**. `--skip-nx-cache`.

Spec files from Vitest `results.json` (`failed: false` on every file). `crm` app: 4 `*.spec.ts` (nx `crm:test` SUCCESS; not in the libs vite cache). `it()`/`test()` counts are source cases in those executed files.

| Project | Spec files | Spec cases |
|---------|-----------:|-----------:|
| shared-models | 2 | 14 |
| crm-organizations | 4 | 14 |
| shared-ui | 7 | 73 |
| cpq-pricing-data-access | 6 | 25 |
| crm-contacts | 3 | 13 |
| crm-leads | 3 | 22 |
| cpq-product-bundles | 8 | 36 |
| cpq-rule-sets | 7 | 32 |
| cpq-quotes | 8 | 85 |
| cpq-pricing-customers | 5 | 29 |
| cpq-pricing-simulator | 3 | 11 |
| shared-data-access | 5 | 34 |
| crm-dashboard | 2 | 6 |
| cpq-approvals | 3 | 15 |
| cpq-price-books | 7 | 53 |
| crm-deals | 5 | 39 |
| cpq-catalog | 10 | 73 |
| crm | 4 | 12 |
| shared-utils | 4 | 36 |
| cpq-pricing-ui | 1 | 1 |
| **Total** | **97** | **623** |

20/20 projects. Quotes file delta vs 2026-09-15 FE closeout includes NG4 `quotes-list-webmcp.spec.ts`.

## Drift check

```bash
python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py
```

```text
CI contract drift check OK
  contract: docs/ci-contract.yaml
  backend-integration needs: ['backend-unit', 'shared-libraries']
```

EXIT **0**.

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Default still **true**.

- NgRx **held**; Activities API **not invented**; CI `#238` / Kafka `#241` **not started**
- **R2** price-book list-row delete **held**; subscriptions FE **held**; GLM-5 **not runtime**
- Boot **4.0.6**; Drools **10.2.0**; MapStruct **1.5.5.Final**; Spring AI **0.8.1**
- JAVA_HOME **25.0.4.1 LTS** — never 27; JaCoCo **0.80** not lowered
- Did **not** edit `docs/forge/00-orchestrator-status.md`
- Did **not** start HYG-1 (already `#524` on `main`)

## Acceptance

- [x] `"$JAVA_HOME/bin/java" -version` → **25.0.4.1 LTS** (not 27)
- [x] `newgrp docker` + libs `-am test` GREEN
- [x] crm+gateway `verify -Punit-tests` GREEN
- [x] Per-module `-am test` for pricing, product, quote, pricebook, configuration-engine, approval-routing, document-generation, contract-asset-management, ai-service GREEN
- [x] `npm ci --legacy-peer-deps` in this worktree (not a primary `node_modules` symlink) EXIT 0
- [x] `nx build crm --configuration=production --skip-nx-cache` SUCCESS
- [x] `nx run-many -t test --all --parallel=3 --skip-nx-cache` **20/20**
- [x] `verify-drift.py` OK
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] JaCoCo **0.80** not lowered
- [x] Exclusive file: this impl note only
