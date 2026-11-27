# Residual AM04: Named tests and CI-matched pass/fail/skip counts

**Linear:** DRG-930 (residual DoD AM04, FU-13 under DRG-652 W0-3)
**Rubric:** DoD v0.4 — AM04 (`Named tests with pass counts`, w5, MUST) and C05 (`Existing suite intact`, w5)
**Decision Rulings Applied:** R1 (frontend counts), R2 (integration evidence), R3 (PR `#719` post-merge verification) — follow-up corrective draft to merged PR `#1096` (`a877f90a`)
**Scope:** Residual evidence only. No code, schema, migration, flag, hold, or Linear issue-status changes (`DRG-656` stays `In Review`; `DRG-657` and `DRG-660` stay S7 NO-GO). Draft PR only; `drg` personally merges by PR number.

## Decision rulings (R1–R3) & CI provenance

- **R1 (Frontend counts — `#1003` / `DRG-761`) — ACCEPTED with annotation:** *Frontend spec counts are source-counted, not CI-reported; pr-check uploads backend Surefire XML only.* `.github/workflows/pr-check.yml:44-51` is the backend `pr-check-unit-test-reports` upload (`backend/**/target/surefire-reports/*.xml`); the frontend job at `.github/workflows/pr-check.yml:53-67` uploads nothing.
- **R2 (Integration evidence — `@Tag("integration")`):**
  - **PR `#713` (`DRG-654`) — REJECTED / UNRESOLVED:** `AssetActivationConsumerIntegrationTest` (`AssetActivationConsumerIntegrationTest.java:44`) and `AssetCreatedReplayIntegrationTest` (`AssetCreatedReplayIntegrationTest.java:39`) on current `main` now force `"opencrm.assets.legacy-acceptance-write-enabled=true"` (added after `#713` by `#735`), so they are **not** the tests merged at `aeb1993d`. Nightly run `45928423550` at `602c8526` is **excluded** as original `#713` integration proof. Resolution requires either **(a)** a rerun at merge commit `aeb1993d` in a throwaway worktree with Testcontainers, or **(b)** a recorded gap that `drg` explicitly accepts. Until one exists, `#713` integration evidence remains **UNRESOLVED**.
  - **All other PRs with integration tests (`#719` / `DRG-656`, `#741` / `DRG-655`) — ACCEPTED with provenance note:** *counts from the nightly at `602c8526`, not PR-time code* (nightly `ci.yml` run `45928423550`, job `140277160475`, SHA `602c8526`, `2026-11-27`).
- **R3 (PR `#719` / `DRG-656`) — ACCEPTED with gap note:** *No PR-gate run exists (feature-branch target; `pr-check` only fires for PRs into `main`, `.github/workflows/pr-check.yml:13-14`); `Main verify` run `41320727271` is post-merge evidence only.* Run-history verification (`gh run list --branch cursor/drg-656-asset-history-5ccc` → `[]`): **no failing run in run history**.
- **Backend unit CI logs:** `.github/workflows/pr-check.yml:44-51` retains backend `pr-check-unit-test-reports` Surefire XML for 7 days; for the Nov 1 / Nov 15 PR runs, per-class Maven Surefire `Tests run / Failures / Errors / Skipped` counts were extracted directly from the live GitHub Actions job logs for the cited run IDs. Current `main` backend unit counts are cross-checked against `main-verify.yml` run `46014458682` (job `140557911414`, SHA `a73ff773`, `2026-11-27`).

Notation: `pass/fail/skip` where `fail = Failures + Errors` (every CI-reported backend row below has `Failures = 0`, `Errors = 0`, `Skipped = 0`).

---

## Summary matrix

| Story | PR | CI Run (PR gate / post-merge) | Job ID | Unit / FE Gate | Integration Tests (`@Tag("integration")`) | Ruling Status | C05 Newly Disabled/Skipped |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **DRG-654** | `#713` (`aeb1993d`) | `41319799611` (`PR check`, `2026-11-01`) | `124887371762` | **SUCCESS** (reactor `2334/0/0`; CAM `271/0/0`; 6 unit classes `14/0/0`) | **UNRESOLVED** — 2 IT classes (`AssetActivationConsumerIntegrationTest`, `AssetCreatedReplayIntegrationTest`); nightly `602c8526` **REJECTED** (R2: forces legacy flag on) | **R2 REJECTED / UNRESOLVED** (IT) | `0` |
| **DRG-656** | `#719` (`abafd089`) | No PR-gate run (feature-branch target; no failing run in run history); post-merge `41320727271` (`Main verify`, `2026-11-01`) | `124890148174` | **SUCCESS** (post-merge only; reactor `2341/0/0`; CAM `276/0/0`; 3 unit classes `5/0/0`) | 1 IT class (`AssetHistoryReplayIntegrationTest` `1/0/0` — **counts from the nightly at `602c8526`, not PR-time code**) | **R2 + R3 ACCEPTED with notes** | `0` |
| **DRG-655** | `#741` (`81ed9752`) | `41338079411` (`PR check`, `2026-11-01`) | `124942062957` | **SUCCESS** (reactor `2468/0/0`; CAM `329/0/0`; 6 unit classes `22/0/0`) | 1 IT class (`AssetHistoryReplayIntegrationTest` `1/0/0` — **counts from the nightly at `602c8526`, not PR-time code**) | **R2 ACCEPTED with note** | `0` |
| **DRG-658** | `#742` (`7088b710`) | `41338081186` (`PR check`, `2026-11-01`) | `124942068824` | **SUCCESS** (reactor `2462/0/0`; quote `429/0/0`; 4 unit classes `12/0/0`) | None in PR diff | **ACCEPTED** | `0` |
| **DRG-659** | `#740` (`e53f90c1`) | `41338076686` (`PR check`, `2026-11-01`) | `124942054538` | **SUCCESS** (reactor `2469/0/0`; CAM `336/0/0`; 7 unit classes `27/0/0`) | None in PR diff | **ACCEPTED** | `0` |
| **DRG-761** | `#1003` (`94001683`) | `43721274542` (`PR check`, `2026-11-15`) | `132833579960` (FE), `132833579900` (BE) | **SUCCESS** (CI-reported target pass `cpq-contracts:test` + `crm:test`; per-file FE specs **source-counted, not CI-reported**; BE reactor `3511/0/0`) | None in PR diff (3 Node e2e-lib files outside CI: `29/0/0` local) | **R1 ACCEPTED with annotation** | `0` |
| **DRG-653** | `#958` (`ae74f83d`) | Docs-only ADR (0 test files changed) | — | N/A (AM04 code-PR trigger does not apply) | None | N/A | `0` |

---

## Per-story evidence

### 1. DRG-654 — PR #713 (`aeb1993d`): Create asset when an order activates

- **PR CI run:** `41319799611` (`PR check` / `Backend Unit (PR gate)`, job `124887371762`, head `41069371`, `2026-11-01T15:09:46Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41319799611
  - Reactor total (15 modules): `2334` run, `0` failures, `0` errors, `0` skipped (`Common`: `245/0/0`; `Gateway`: `110/0/0`; `CAM`: `271/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (CI-reported in run `41319799611`)

| Test class | Surefire `@DisplayName` (if overridden) | PR CI (`41319799611`) | Current `main` (`46014458682`) |
| --- | --- | --- | --- |
| `EventContractRegistryTest` | `ADR-018 D5: EventContractRegistry is complete and consistent` | `8/0/0` | `8/0/0` |
| `AssetAccountIdTest` | — | `1/0/0` | `1/0/0` |
| `AssetAuditOnCreateTest` | — | `1/0/0` | `1/0/0` |
| `AssetCreationEntitlementLinkedTest` | — | `1/0/0` | `1/0/0` |
| `AssetCreationEntitlementNullTest` | — | `1/0/0` | `1/0/0` |
| `GatewayDispatchSmokeTest` | — | `2/0/0` | `2/0/0` |

#### Integration tests (`@Tag("integration")`, excluded from PR unit gate) — **UNRESOLVED (R2 REJECTED)**

> **R2 Ruling (REJECTED as original `#713` proof):** On current `main` (including nightly `45928423550` at `602c8526`), `AssetActivationConsumerIntegrationTest` (`AssetActivationConsumerIntegrationTest.java:44`) and `AssetCreatedReplayIntegrationTest` (`AssetCreatedReplayIntegrationTest.java:39`) force `"opencrm.assets.legacy-acceptance-write-enabled=true"` (added after `#713` in `#735`). Consequently, those nightly runs are **not** the tests merged at `aeb1993d` and are excluded as `#713` integration proof.
>
> **Resolution path (either):**
> 1. Rerun both tests at merge commit `aeb1993d` in a throwaway worktree with Testcontainers, **or**
> 2. Record this gap explicitly for `drg` acceptance.
>
> Until one of those exists, `#713` integration evidence is **UNRESOLVED**.

| Test class | PR unit gate (`41319799611`) | Original `#713` (`aeb1993d`) IT evidence status | Why later nightly (`602c8526`) is excluded |
| --- | --- | --- | --- |
| `AssetActivationConsumerIntegrationTest` | Not run (`-Punit-tests`) | **UNRESOLVED** | Forces `opencrm.assets.legacy-acceptance-write-enabled=true` at line 44 (post-`#735`) |
| `AssetCreatedReplayIntegrationTest` | Not run (`-Punit-tests`) | **UNRESOLVED** | Forces `opencrm.assets.legacy-acceptance-write-enabled=true` at line 39 (post-`#735`) |

- **Non-test support files in diff:** `AssetActivationTestSupport.java` (helper fixture, 0 `@Test` methods).

---

### 2. DRG-656 — PR #719 (`abafd089`): Queryable asset history and supersede chain

- **PR CI run (R3 gap note):** *No PR-gate run exists (feature-branch target `cursor/drg-654-asset-on-activation-a53d` from head `cursor/drg-656-asset-history-5ccc`; `pr-check` only fires for PRs into `main`, `.github/workflows/pr-check.yml:13-14`); `Main verify` run `41320727271` is post-merge evidence only.*
- **Run-history basis:** `gh run list --branch cursor/drg-656-asset-history-5ccc` returned `[]` — **no failing run in run history**.
- **Post-merge CI run on merged commit (`abafd089`):** `41320727271` (`Main verify` / `Backend Unit (main)`, job `124890148174`, `2026-11-01`, **SUCCESS** — post-merge evidence only)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41320727271/job/124890148174
  - Reactor total (15 modules): `2341` run, `0` failures, `0` errors, `0` skipped (`CAM`: `276/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (CI-reported in post-merge `Main verify` run `41320727271` only)

| Test class | Post-merge `Main verify` (`41320727271`) | Current `main` (`46014458682`) |
| --- | --- | --- |
| `AssetHistoryProjectionTest` | `1/0/0` | `1/0/0` |
| `AssetSupersedeChainTest` | `1/0/0` | `1/0/0` |
| `InstalledBaseAsOfTest` | `3/0/0` | `3/0/0` |

#### Integration tests (`@Tag("integration")`, excluded from unit gate) — **R2 ACCEPTED with provenance note**

> **R2 Provenance note:** counts from the nightly at `602c8526`, not PR-time code.

| Test class | Post-merge unit gate (`41320727271`) | Nightly `Backend Integration` (`45928423550`, job `140277160475`, SHA `602c8526`) |
| --- | --- | --- |
| `AssetHistoryReplayIntegrationTest` | Not run (`-Punit-tests`) | `1/0/0` *(counts from the nightly at `602c8526`, not PR-time code)* |

- **Non-test support/fixture files in diff:** `InMemoryAssetHistoryStore.java`, `fixtures/AssetAmendedFixture.java`, `fixtures/AssetSupersededFixture.java`, `fixtures/AssetTerminatedFixture.java` (0 `@Test` methods).

---

### 3. DRG-655 — PR #741 (`81ed9752`): Amend, terminate, and supersede assets inside document apply

- **PR CI run:** `41338079411` (`PR check` / `Backend Unit (PR gate)`, job `124942062957`, head `1abda73f`, `2026-11-01T18:43:38Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41338079411
  - Reactor total (15 modules): `2468` run, `0` failures, `0` errors, `0` skipped (`Common`: `253/0/0`; `CAM`: `329/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (CI-reported in run `41338079411`)

| Test class | Surefire `@DisplayName` (if overridden) | PR CI (`41338079411`) | Current `main` (`46014458682`) |
| --- | --- | --- | --- |
| `EventContractRegistryTest` | `ADR-018 D5: EventContractRegistry is complete and consistent` | `8/0/0` | `8/0/0` |
| `AssetChangeApplyTest` | — | `6/0/0` | `6/0/0` |
| `AssetHistoryProjectionTest` | — | `1/0/0` | `1/0/0` |
| `AssetSupersedeChainTest` | — | `1/0/0` | `1/0/0` |
| `InstalledBaseAsOfTest` | — | `3/0/0` | `3/0/0` |
| `DocumentApplyAssetReplayTest` | — | `3/0/0` | `3/0/0` |

#### Integration tests (`@Tag("integration")`, excluded from PR unit gate) — **R2 ACCEPTED with provenance note**

> **R2 Provenance note:** counts from the nightly at `602c8526`, not PR-time code.

| Test class | PR unit gate (`41338079411`) | Nightly `Backend Integration` (`45928423550`, job `140277160475`, SHA `602c8526`) |
| --- | --- | --- |
| `AssetHistoryReplayIntegrationTest` | Not run (`-Punit-tests`) | `1/0/0` *(counts from the nightly at `602c8526`, not PR-time code)* |

- **Non-test support/fixture files in diff:** `AssetActivationTestSupport.java`, `InMemoryAssetHistoryStore.java`, `fixtures/AssetAmendedFixture.java`, `fixtures/AssetSupersededFixture.java`, `fixtures/AssetTerminatedFixture.java` (0 `@Test` methods).

---

### 4. DRG-658 — PR #742 (`7088b710`): Open renewal quotes from active assets

- **PR CI run:** `41338081186` (`PR check` / `Backend Unit (PR gate)`, job `124942068824`, head `f657b169`, `2026-11-01T18:43:40Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41338081186
  - Reactor total (15 modules): `2462` run, `0` failures, `0` errors, `0` skipped (`Quote Service`: `429/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (CI-reported in run `41338081186`)

| Test class | Surefire `@DisplayName` (if overridden) | PR CI (`41338081186`) | Current `main` (`46014458682`) |
| --- | --- | --- | --- |
| `AssetRenewalDraftsTest` | — | `7/0/0` | `24/0/0` (expanded in later PRs) |
| `HttpAssetReadClientTest` | — | `2/0/0` | `2/0/0` |
| `PricingServiceClientContextTest` | `PricingServiceClient Spring wiring (UAT-1 boot)` | `1/0/0` | `1/0/0` |
| `RestClientConfigAuthTest` | `quote-service outbound calls: user token relayed to /api/v1, service token to CE /internal` | `2/0/0` | `2/0/0` |

#### Integration tests

- None added or changed in PR `#742`.

---

### 5. DRG-659 — PR #740 (`e53f90c1`): Report upcoming renewals and asset churn

- **PR CI run:** `41338076686` (`PR check` / `Backend Unit (PR gate)`, job `124942054538`, head `cda999c6`, `2026-11-01T18:43:36Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41338076686
  - Reactor total (15 modules): `2469` run, `0` failures, `0` errors, `0` skipped (`CAM`: `336/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (CI-reported in run `41338076686`)

| Test class | PR CI (`41338076686`) | Current `main` (`46014458682`) |
| --- | --- | --- |
| `AssetReadAuthorizationTest` | `5/0/0` | `5/0/0` |
| `AssetReadDevModeTest` | `1/0/0` | `1/0/0` |
| `AssetReadHttpAuthorizationTest` | `4/0/0` | `4/0/0` |
| `AssetReadJwtFilterTest` | `1/0/0` | `1/0/0` |
| `AssetReportControllerTest` | `3/0/0` | `3/0/0` |
| `AssetReportReconciliationTest` | `1/0/0` | `1/0/0` |
| `AssetReportServiceTest` | `12/0/0` | `12/0/0` |

#### Integration tests

- None added or changed in PR `#740`.

---

### 6. DRG-761 — PR #1003 (`94001683`): Read-only Contracts and Assets screens

- **PR CI run:** `43721274542` (`PR check`, head `f8daac53`, `2026-11-15T13:52:28Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/43721274542
  - `Frontend (PR gate)` (job `132833579960`): **SUCCESS** — `npx nx run-many --target=test --all` (Vitest via `@nx/vitest:test`, not Karma) passed all 21 projects, including `✔ nx run cpq-contracts:test` and `✔ nx run crm:test`.
  - `Backend Unit (PR gate)` (job `132833579900`): **SUCCESS** — reactor total (17 modules): `3511/0/0`.
  - `Frontend npm audit (PR gate)` (job `132833579749`): **SUCCESS**.
  - C05 diff check: `0` newly disabled or skipped tests (0 `skip`, `todo`, `only`, or `.each` calls across all 6 Vitest spec files).

#### Frontend unit specs (Vitest) — **R1 ACCEPTED with annotation**

> **R1 Annotation:** *Frontend spec counts are source-counted, not CI-reported; pr-check uploads backend Surefire XML only.*
> - `.github/workflows/pr-check.yml:44-51` uploads backend Surefire XML (`pr-check-unit-test-reports`, `backend/**/target/surefire-reports/*.xml`).
> - `.github/workflows/pr-check.yml:53-67` (`frontend` job) uploads nothing; `nx run-many --target=test --all` logs target-level pass status only (`✔ nx run cpq-contracts:test`, `✔ nx run crm:test`).

| Spec file | Nx project | CI-reported result (`43721274542`) | Source-counted cases at `#1003` (`94001683`) *(not CI-reported)* | Source-counted cases on `main` (`a73ff773`) *(not CI-reported)* |
| --- | --- | --- | --- | --- |
| `asset-list.component.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` (target pass only) | `6` (0 skip/todo/each) | `7` (0 skip/todo/each) |
| `contract-detail.component.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` (target pass only) | `10` (0 skip/todo/each) | `11` (0 skip/todo/each) |
| `contract-list.component.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` (target pass only) | `7` (0 skip/todo/each) | `9` (0 skip/todo/each) |
| `contract.service.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` (target pass only) | `5` (0 skip/todo/each) | `5` (0 skip/todo/each) |
| `contracts.routes.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` (target pass only) | `1` (0 skip/todo/each) | `1` (0 skip/todo/each) |
| `sidebar.component.spec.ts` | `crm` | `✔ crm:test` (target pass only) | `3` (0 skip/todo/each) | `4` (0 skip/todo/each) |

#### Node E2E harness unit tests (outside `scripts/ci/frontend.sh`)

Not executed by `PR check` (`Frontend (PR gate)` only runs Nx projects); verified locally via `node --test` on `origin/main` (`a73ff773`): **29 pass / 0 fail / 0 skip**.

| Test file | Source-counted cases at `#1003` (`94001683`) | Local `node --test` on `main` (`a73ff773`) |
| --- | --- | --- |
| `frontend/e2e/lib/mcp-playwright.test.mjs` | `15` | `15/0/0` |
| `frontend/e2e/lib/report.test.mjs` | `10` | `10/0/0` |
| `frontend/e2e/lib/residue.test.mjs` | `3` | `4/0/0` |

#### Integration / Browser UAT

- No backend integration tests in PR `#1003`.
- Browser UAT (`uat-full.mjs`, `uat-jev-mcp.mjs`, asset-lifecycle UAT) is not wired into `pr-check.yml`. Note: nightly `ci.yml` run `45928423550` (`2026-11-27`) failed `Frontend UAT (Playwright)` before running `uat-cdk-ux.mjs` due to `npm ci` lockfile mismatch (`Missing: chokidar@5.0.0 from lock file`).

---

## Audit of Already-Posted Linear Comments & Required Clarifications

`cod-00` already posted the initial six DRG-930 comments from PR `#1096` onto Linear (`DRG-654`, `DRG-655`, `DRG-656`, `DRG-658`, `DRG-659`, `DRG-761`) without changing any issue status. Under **R1–R3**, **4 of those 6 posted comments require clarification addenda** (comments only; **do not** change any issue status; `DRG-656` stays `In Review`):

| Linear Issue | Clarification Needed? | Ruling | Reason |
| --- | --- | --- | --- |
| **DRG-654** (`#713`) | **YES — REQUIRED** | **R2 (REJECTED)** | Prior comment cited nightly `45928423550` (`602c8526`) for the two ITs. Must retract nightly as `#713` proof and mark `#713` integration evidence **UNRESOLVED** because both ITs now force `opencrm.assets.legacy-acceptance-write-enabled=true` (lines 44 and 39). |
| **DRG-656** (`#719`) | **YES — REQUIRED** | **R2 + R3** | Must add R3 gap note (feature-branch target `cursor/drg-654-asset-on-activation-a53d`, `.github/workflows/pr-check.yml:13-14`, `Main verify` `41320727271` is post-merge evidence only, run history shows `no failing run in run history`) and R2 provenance note (`counts from the nightly at 602c8526, not PR-time code`). |
| **DRG-655** (`#741`) | **YES — REQUIRED** | **R2** | Must annotate `AssetHistoryReplayIntegrationTest` (`1/0/0`) with `counts from the nightly at 602c8526, not PR-time code`. |
| **DRG-761** (`#1003`) | **YES — REQUIRED** | **R1** | Must annotate per-file Vitest counts with `Frontend spec counts are source-counted, not CI-reported; pr-check uploads backend Surefire XML only` (`.github/workflows/pr-check.yml:44-51` backend upload vs `:53-67` frontend job uploads nothing). |
| **DRG-658** (`#742`) | **No** | — | Backend unit-only PR; live PR-gate job log counts match verbatim; no ITs in diff. |
| **DRG-659** (`#740`) | **No** | — | Backend unit-only PR; live PR-gate job log counts match verbatim; no ITs in diff. |

### Ready-to-post Linear clarification addenda (for `cod-00` — comments only, no status changes)

#### Clarification for DRG-654 (R2)

```markdown
DRG-930 R2 clarification (PR #713 / `aeb1993d`):
Retracting the prior nightly `45928423550` (`602c8526`) citation for `AssetActivationConsumerIntegrationTest` and `AssetCreatedReplayIntegrationTest`. Per decision ruling R2, both tests on `main` now force `opencrm.assets.legacy-acceptance-write-enabled=true` (`AssetActivationConsumerIntegrationTest.java:44`, `AssetCreatedReplayIntegrationTest.java:39`), so they are not the tests merged at `aeb1993d`.

PR #713 integration evidence is **UNRESOLVED** until either:
1. Both tests are rerun at merge commit `aeb1993d` in a throwaway worktree with Testcontainers, or
2. `drg` explicitly accepts this recorded gap.

Unit-test counts from PR-gate CI run `41319799611` (`124887371762`) remain unchanged.
```

#### Clarification for DRG-656 (R2 + R3)

```markdown
DRG-930 R2/R3 clarification (PR #719 / `abafd089`):
- **R3 (PR-gate gap):** No PR-gate run exists (feature-branch target `cursor/drg-654-asset-on-activation-a53d` from `cursor/drg-656-asset-history-5ccc`; `pr-check` only fires for PRs into `main`, `.github/workflows/pr-check.yml:13-14`); `Main verify` run `41320727271` is post-merge evidence only. Run history (`gh run list --branch cursor/drg-656-asset-history-5ccc`): no failing run in run history.
- **R2 (integration provenance):** `AssetHistoryReplayIntegrationTest` (`1/0/0`, run `45928423550`, job `140277160475`) — counts from the nightly at `602c8526`, not PR-time code.
```

#### Clarification for DRG-655 (R2)

```markdown
DRG-930 R2 clarification (PR #741 / `81ed9752`):
- **R2 (integration provenance):** `AssetHistoryReplayIntegrationTest` (`1/0/0`, run `45928423550`, job `140277160475`) — counts from the nightly at `602c8526`, not PR-time code.
Unit-test counts from PR-gate CI run `41338079411` (`124942062957`) remain unchanged.
```

#### Clarification for DRG-761 (R1)

```markdown
DRG-930 R1 clarification (PR #1003 / `94001683`):
- **R1 (frontend counts):** Frontend spec counts are source-counted, not CI-reported; `pr-check` uploads backend Surefire XML only (`.github/workflows/pr-check.yml:44-51` is the backend upload; the frontend job at `.github/workflows/pr-check.yml:53-67` uploads nothing).
- CI run `43721274542` (`Frontend (PR gate)`, job `132833579960`) reports target-level pass markers only (`✔ cpq-contracts:test`, `✔ crm:test`). Per-file counts (`asset-list`: 6, `contract-detail`: 10, `contract-list`: 7, `contract.service`: 5, `contracts.routes`: 1, `sidebar`: 3 at `94001683`) are source-counted with 0 skip/todo/each markers.
```

---

## Known gaps & explicit limits

- **PR `#713` (`DRG-654`) integration evidence is UNRESOLVED (R2):** Later nightly runs (`602c8526`) force `opencrm.assets.legacy-acceptance-write-enabled=true` and cannot certify `#713` (`aeb1993d`); requires either a throwaway-worktree Testcontainers rerun at `aeb1993d` or explicit `drg` acceptance of the recorded gap.
- **PR `#719` (`DRG-656`) post-merge only (R3):** Feature-branch target prevented `pr-check` from firing (`.github/workflows/pr-check.yml:13-14`); `Main verify` `41320727271` is post-merge evidence only; `gh run list --branch cursor/drg-656-asset-history-5ccc` shows no failing run in run history.
- **PR `#1003` (`DRG-761`) source-counted frontend specs (R1):** Frontend spec counts are source-counted, not CI-reported; `pr-check` uploads backend Surefire XML only (`.github/workflows/pr-check.yml:44-51` vs `:53-67`); `frontend/e2e/lib/*.test.mjs` (`29/0/0` local) is not invoked by `scripts/ci/frontend.sh`.
- **Linear clarification posting:** Posting the four clarification addenda above (`DRG-654`, `DRG-656`, `DRG-655`, `DRG-761`) requires `cod-00`'s authenticated Linear MCP writer (comments only, no status changes).

## References

- Linear `DRG-930`: https://linear.app/drgamtd-workspace/issue/DRG-930/residual-dod-am04-named-tests-with-ci-matched-passfailskip-counts-on
- Prior merged PR `#1096`: https://github.com/drgaciw/open-crm-cpq/pull/1096 (`a877f90a`)
- DoD v0.4 rubric: https://app.notion.com/p/408f7cb4e4df81b18b7adfee3d5b8e82
- `DRG-652` W0-3 evidence: https://app.notion.com/p/422f7cb4e4df81e4b286d7e4cfa5ce0f
- Related residual Draft PRs: `#1093` (`DRG-932`), `#1094` (`DRG-931`), `#1095` (`DRG-924`)
