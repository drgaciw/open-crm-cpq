# Residual AM04: Named tests and CI-matched pass/fail/skip counts

**Linear:** DRG-930 (residual DoD AM04, FU-13 under DRG-652 W0-3)
**Rubric:** DoD v0.4 — AM04 (`Named tests with pass counts`, w5, MUST) and C05 (`Existing suite intact`, w5)
**Scope:** Residual evidence only. No code, schema, migration, flag, hold, or Linear issue-status changes (`DRG-656` stays `In Review`; `DRG-657` and `DRG-660` stay S7 NO-GO). Draft PR only.

## CI provenance & artifact note

`.github/workflows/pr-check.yml` uploads `pr-check-unit-test-reports` with `retention-days: 7`. Because the five PR-check runs cited in `DRG-930` executed on `2026-11-01` (`#713`, `#741`, `#742`, `#740`) and `2026-11-15` (`#1003`), their XML report artifacts have expired (`artifacts: 0` via GitHub API). Their GitHub Actions **job logs** (90-day retention) remain live and contain the exact Maven Surefire per-class `Tests run / Failures / Errors / Skipped` summaries and Nx target results reported below.

Integration tests (`@Tag("integration")`) are excluded from the Docker-free `Backend Unit (PR gate)` (`-Punit-tests`) by design; they are listed separately with their verified Testcontainers execution counts from the latest nightly `ci.yml` `Backend Integration (Testcontainers)` job on `main` (run `45928423550`, job `140277160475`, SHA `602c8526`, `2026-11-27`). Current `main` unit counts are cross-checked against `main-verify.yml` run `46014458682` (job `140557911414`, SHA `a73ff773`, `2026-11-27`).

Notation: `pass/fail/skip` where `fail = Failures + Errors` (every row below has `Failures = 0`, `Errors = 0`, `Skipped = 0`).

---

## Summary matrix

| Story | PR | CI Run (PR gate / first CI) | Job ID | Unit / FE Gate | Integration Tests (`@Tag("integration")`) | C05 Newly Disabled/Skipped in Diff |
| --- | --- | --- | --- | --- | --- | --- |
| **DRG-654** | `#713` (`aeb1993d`) | `41319799611` (`PR check`, `2026-11-01`) | `124887371762` | **SUCCESS** (reactor `2334/0/0`; CAM `271/0/0`; 6 unit classes `14/0/0`) | 2 classes (`2/0/0` in nightly IT `45928423550`) | `0` |
| **DRG-656** | `#719` (`abafd089`) | No PR check; post-merge `41320727271` (`Main verify`, `2026-11-01`) | `124890148174` | **SUCCESS** (reactor `2341/0/0`; CAM `276/0/0`; 3 unit classes `5/0/0`) | 1 class (`1/0/0` in nightly IT `45928423550`) | `0` |
| **DRG-655** | `#741` (`81ed9752`) | `41338079411` (`PR check`, `2026-11-01`) | `124942062957` | **SUCCESS** (reactor `2468/0/0`; CAM `329/0/0`; 6 unit classes `22/0/0`) | 1 class (`1/0/0` in nightly IT `45928423550`) | `0` |
| **DRG-658** | `#742` (`7088b710`) | `41338081186` (`PR check`, `2026-11-01`) | `124942068824` | **SUCCESS** (reactor `2462/0/0`; quote `429/0/0`; 4 unit classes `12/0/0`) | None in PR diff | `0` |
| **DRG-659** | `#740` (`e53f90c1`) | `41338076686` (`PR check`, `2026-11-01`) | `124942054538` | **SUCCESS** (reactor `2469/0/0`; CAM `336/0/0`; 7 unit classes `27/0/0`) | None in PR diff | `0` |
| **DRG-761** | `#1003` (`94001683`) | `43721274542` (`PR check`, `2026-11-15`) | `132833579960` (FE), `132833579900` (BE) | **SUCCESS** (Vitest: `cpq-contracts:test` + `crm:test` green; BE reactor `3511/0/0`) | None in PR diff (3 Node e2e-lib files outside CI: `29/0/0` local) | `0` |
| **DRG-653** | `#958` (`ae74f83d`) | Docs-only ADR (0 test files changed) | — | N/A (AM04 code-PR trigger does not apply) | None | `0` |

---

## Per-story evidence

### 1. DRG-654 — PR #713 (`aeb1993d`): Create asset when an order activates

- **PR CI run:** `41319799611` (`PR check` / `Backend Unit (PR gate)`, job `124887371762`, head `41069371`, `2026-11-01T15:09:46Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41319799611
  - Reactor total (15 modules): `2334` run, `0` failures, `0` errors, `0` skipped (`Common`: `245/0/0`; `Gateway`: `110/0/0`; `CAM`: `271/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (executed in CI run `41319799611`)

| Test class | Surefire `@DisplayName` (if overridden) | PR CI (`41319799611`) | Current `main` (`46014458682`) |
| --- | --- | --- | --- |
| `EventContractRegistryTest` | `ADR-018 D5: EventContractRegistry is complete and consistent` | `8/0/0` | `8/0/0` |
| `AssetAccountIdTest` | — | `1/0/0` | `1/0/0` |
| `AssetAuditOnCreateTest` | — | `1/0/0` | `1/0/0` |
| `AssetCreationEntitlementLinkedTest` | — | `1/0/0` | `1/0/0` |
| `AssetCreationEntitlementNullTest` | — | `1/0/0` | `1/0/0` |
| `GatewayDispatchSmokeTest` | — | `2/0/0` | `2/0/0` |

#### Integration tests (`@Tag("integration")`, excluded from PR unit gate)

| Test class | PR unit gate (`41319799611`) | Nightly `Backend Integration` (`45928423550`, job `140277160475`) |
| --- | --- | --- |
| `AssetActivationConsumerIntegrationTest` | Not run (`-Punit-tests`) | `1/0/0` |
| `AssetCreatedReplayIntegrationTest` | Not run (`-Punit-tests`) | `1/0/0` |

- **Non-test support files in diff:** `AssetActivationTestSupport.java` (helper fixture, 0 `@Test` methods).

---

### 2. DRG-656 — PR #719 (`abafd089`): Queryable asset history and supersede chain

- **PR CI run:** None (`checks: []` on PR `#719`; tracked separately by the CI-evidence follow-up).
- **First CI run on merged commit (`abafd089`):** `41320727271` (`Main verify` / `Backend Unit (main)`, job `124890148174`, `2026-11-01`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41320727271/job/124890148174
  - Reactor total (15 modules): `2341` run, `0` failures, `0` errors, `0` skipped (`CAM`: `276/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (executed in post-merge `Main verify` run `41320727271`)

| Test class | Post-merge CI (`41320727271`) | Current `main` (`46014458682`) |
| --- | --- | --- |
| `AssetHistoryProjectionTest` | `1/0/0` | `1/0/0` |
| `AssetSupersedeChainTest` | `1/0/0` | `1/0/0` |
| `InstalledBaseAsOfTest` | `3/0/0` | `3/0/0` |

#### Integration tests (`@Tag("integration")`, excluded from unit gate)

| Test class | Post-merge unit gate (`41320727271`) | Nightly `Backend Integration` (`45928423550`, job `140277160475`) |
| --- | --- | --- |
| `AssetHistoryReplayIntegrationTest` | Not run (`-Punit-tests`) | `1/0/0` |

- **Non-test support/fixture files in diff:** `InMemoryAssetHistoryStore.java`, `fixtures/AssetAmendedFixture.java`, `fixtures/AssetSupersededFixture.java`, `fixtures/AssetTerminatedFixture.java` (0 `@Test` methods).

---

### 3. DRG-655 — PR #741 (`81ed9752`): Amend, terminate, and supersede assets inside document apply

- **PR CI run:** `41338079411` (`PR check` / `Backend Unit (PR gate)`, job `124942062957`, head `1abda73f`, `2026-11-01T18:43:38Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41338079411
  - Reactor total (15 modules): `2468` run, `0` failures, `0` errors, `0` skipped (`Common`: `253/0/0`; `CAM`: `329/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (executed in CI run `41338079411`)

| Test class | Surefire `@DisplayName` (if overridden) | PR CI (`41338079411`) | Current `main` (`46014458682`) |
| --- | --- | --- | --- |
| `EventContractRegistryTest` | `ADR-018 D5: EventContractRegistry is complete and consistent` | `8/0/0` | `8/0/0` |
| `AssetChangeApplyTest` | — | `6/0/0` | `6/0/0` |
| `AssetHistoryProjectionTest` | — | `1/0/0` | `1/0/0` |
| `AssetSupersedeChainTest` | — | `1/0/0` | `1/0/0` |
| `InstalledBaseAsOfTest` | — | `3/0/0` | `3/0/0` |
| `DocumentApplyAssetReplayTest` | — | `3/0/0` | `3/0/0` |

#### Integration tests (`@Tag("integration")`, excluded from PR unit gate)

| Test class | PR unit gate (`41338079411`) | Nightly `Backend Integration` (`45928423550`, job `140277160475`) |
| --- | --- | --- |
| `AssetHistoryReplayIntegrationTest` | Not run (`-Punit-tests`) | `1/0/0` |

- **Non-test support/fixture files in diff:** `AssetActivationTestSupport.java`, `InMemoryAssetHistoryStore.java`, `fixtures/AssetAmendedFixture.java`, `fixtures/AssetSupersededFixture.java`, `fixtures/AssetTerminatedFixture.java` (0 `@Test` methods).

---

### 4. DRG-658 — PR #742 (`7088b710`): Open renewal quotes from active assets

- **PR CI run:** `41338081186` (`PR check` / `Backend Unit (PR gate)`, job `124942068824`, head `f657b169`, `2026-11-01T18:43:40Z`, **SUCCESS**)
  - URL: https://github.com/drgaciw/open-crm-cpq/actions/runs/41338081186
  - Reactor total (15 modules): `2462` run, `0` failures, `0` errors, `0` skipped (`Quote Service`: `429/0/0`).
  - C05 diff check: `0` newly disabled or skipped tests.

#### Unit tests (executed in CI run `41338081186`)

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

#### Unit tests (executed in CI run `41338076686`)

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

#### Frontend unit specs (Vitest — executed by `cpq-contracts:test` and `crm:test` in CI run `43721274542`)

> **Log-granularity note:** `scripts/ci/frontend.sh` runs `nx run-many --target=test --all`, which emits target-level pass markers (`✔ nx run cpq-contracts:test`, `✔ nx run crm:test`) and suppresses per-file Vitest stdout on success; no Vitest XML artifact is uploaded. The per-file test counts below are verified directly from each spec file at PR `#1003` merge commit `94001683` (0 `.each`, 0 `.skip`, 0 `.todo`) and on `origin/main` (`a73ff773`).

| Spec file | Nx project | CI run `43721274542` target | Cases at `#1003` (`94001683`) | Cases on `main` (`a73ff773`) |
| --- | --- | --- | --- | --- |
| `asset-list.component.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` | `6/0/0` | `7/0/0` |
| `contract-detail.component.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` | `10/0/0` | `11/0/0` |
| `contract-list.component.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` | `7/0/0` | `9/0/0` |
| `contract.service.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` | `5/0/0` | `5/0/0` |
| `contracts.routes.spec.ts` | `cpq-contracts` | `✔ cpq-contracts:test` | `1/0/0` | `1/0/0` |
| `sidebar.component.spec.ts` | `crm` | `✔ crm:test` | `3/0/0` | `4/0/0` |

#### Node E2E harness unit tests (outside `scripts/ci/frontend.sh`)

Not executed by `PR check` (`Frontend (PR gate)` only runs Nx projects); verified locally in isolated worktree via `node --test` on `origin/main` (`a73ff773`): **29 pass / 0 fail / 0 skip**.

| Test file | Cases at `#1003` (`94001683`) | Verified local `node --test` on `main` (`a73ff773`) |
| --- | --- | --- |
| `frontend/e2e/lib/mcp-playwright.test.mjs` | `15` | `15/0/0` |
| `frontend/e2e/lib/report.test.mjs` | `10` | `10/0/0` |
| `frontend/e2e/lib/residue.test.mjs` | `3` | `4/0/0` |

#### Integration / Browser UAT

- No backend integration tests in PR `#1003`.
- Browser UAT (`uat-full.mjs`, `uat-jev-mcp.mjs`, asset-lifecycle UAT) is not wired into `pr-check.yml`. Note: latest nightly `ci.yml` run `45928423550` (`2026-11-27`) failed `Frontend UAT (Playwright)` before running `uat-cdk-ux.mjs` due to `npm ci` lockfile mismatch (`Missing: chokidar@5.0.0 from lock file`).

---

## Ready-to-post Linear comments (for `cod-00` / authenticated Linear MCP writer)

> **Rule reminder (DRG-930):** Post comments only; **do not** change any issue's status (`DRG-656` stays `In Review`).

### Comment for DRG-654

```markdown
Residual DoD AM04/C05 test evidence (DRG-930) — PR #713 (`aeb1993d`)
CI run: `41319799611` (`PR check` / `Backend Unit (PR gate)`, job `124887371762`, SUCCESS; reactor `2334/0/0`, CAM `271/0/0`; `pr-check-unit-test-reports` 7-day artifact expired, counts from live job log; C05: 0 newly disabled/skipped tests in diff).

Unit tests (`pass/fail/skip`):
- `EventContractRegistryTest` (`ADR-018 D5: EventContractRegistry is complete and consistent`): `8/0/0`
- `AssetAccountIdTest`: `1/0/0`
- `AssetAuditOnCreateTest`: `1/0/0`
- `AssetCreationEntitlementLinkedTest`: `1/0/0`
- `AssetCreationEntitlementNullTest`: `1/0/0`
- `GatewayDispatchSmokeTest`: `2/0/0`

Integration tests (`@Tag("integration")`, excluded from Docker-free PR unit gate; verified in nightly `Backend Integration` run `45928423550`, job `140277160475`, SHA `602c8526`):
- `AssetActivationConsumerIntegrationTest`: `1/0/0`
- `AssetCreatedReplayIntegrationTest`: `1/0/0`
```

### Comment for DRG-656

```markdown
Residual DoD AM04/C05 test evidence (DRG-930) — PR #719 (`abafd089`)
PR CI run: none on PR #719 (`checks: []`; tracked under CI-evidence follow-up).
First post-merge CI run on `abafd089`: `41320727271` (`Main verify` / `Backend Unit (main)`, job `124890148174`, SUCCESS; reactor `2341/0/0`, CAM `276/0/0`; C05: 0 newly disabled/skipped tests in diff).

Unit tests (`pass/fail/skip`, from run `41320727271` and unchanged on `main` `46014458682`):
- `AssetHistoryProjectionTest`: `1/0/0`
- `AssetSupersedeChainTest`: `1/0/0`
- `InstalledBaseAsOfTest`: `3/0/0`

Integration tests (`@Tag("integration")`, excluded from Docker-free unit gate; verified in nightly `Backend Integration` run `45928423550`, job `140277160475`, SHA `602c8526`):
- `AssetHistoryReplayIntegrationTest`: `1/0/0`
```

### Comment for DRG-655

```markdown
Residual DoD AM04/C05 test evidence (DRG-930) — PR #741 (`81ed9752`)
CI run: `41338079411` (`PR check` / `Backend Unit (PR gate)`, job `124942062957`, SUCCESS; reactor `2468/0/0`, CAM `329/0/0`; `pr-check-unit-test-reports` 7-day artifact expired, counts from live job log; C05: 0 newly disabled/skipped tests in diff).

Unit tests (`pass/fail/skip`):
- `EventContractRegistryTest` (`ADR-018 D5: EventContractRegistry is complete and consistent`): `8/0/0`
- `AssetChangeApplyTest`: `6/0/0`
- `AssetHistoryProjectionTest`: `1/0/0`
- `AssetSupersedeChainTest`: `1/0/0`
- `InstalledBaseAsOfTest`: `3/0/0`
- `DocumentApplyAssetReplayTest`: `3/0/0`

Integration tests (`@Tag("integration")`, excluded from Docker-free PR unit gate; verified in nightly `Backend Integration` run `45928423550`, job `140277160475`, SHA `602c8526`):
- `AssetHistoryReplayIntegrationTest`: `1/0/0`
```

### Comment for DRG-658

```markdown
Residual DoD AM04/C05 test evidence (DRG-930) — PR #742 (`7088b710`)
CI run: `41338081186` (`PR check` / `Backend Unit (PR gate)`, job `124942068824`, SUCCESS; reactor `2462/0/0`, Quote Service `429/0/0`; `pr-check-unit-test-reports` 7-day artifact expired, counts from live job log; C05: 0 newly disabled/skipped tests in diff).

Unit tests (`pass/fail/skip`):
- `AssetRenewalDraftsTest`: `7/0/0` (`24/0/0` on current `main` run `46014458682`)
- `HttpAssetReadClientTest`: `2/0/0`
- `PricingServiceClientContextTest` (`PricingServiceClient Spring wiring (UAT-1 boot)`): `1/0/0`
- `RestClientConfigAuthTest` (`quote-service outbound calls: user token relayed to /api/v1, service token to CE /internal`): `2/0/0`

Integration tests: none added or modified in PR #742.
```

### Comment for DRG-659

```markdown
Residual DoD AM04/C05 test evidence (DRG-930) — PR #740 (`e53f90c1`)
CI run: `41338076686` (`PR check` / `Backend Unit (PR gate)`, job `124942054538`, SUCCESS; reactor `2469/0/0`, CAM `336/0/0`; `pr-check-unit-test-reports` 7-day artifact expired, counts from live job log; C05: 0 newly disabled/skipped tests in diff).

Unit tests (`pass/fail/skip`):
- `AssetReadAuthorizationTest`: `5/0/0`
- `AssetReadDevModeTest`: `1/0/0`
- `AssetReadHttpAuthorizationTest`: `4/0/0`
- `AssetReadJwtFilterTest`: `1/0/0`
- `AssetReportControllerTest`: `3/0/0`
- `AssetReportReconciliationTest`: `1/0/0`
- `AssetReportServiceTest`: `12/0/0`

Integration tests: none added or modified in PR #740.
```

### Comment for DRG-761

```markdown
Residual DoD AM04/C05 test evidence (DRG-930) — PR #1003 (`94001683`)
CI run: `43721274542` (`PR check`, SUCCESS — `Frontend (PR gate)` job `132833579960`: all 21 Nx Vitest projects passed including `cpq-contracts:test` and `crm:test`; `Backend Unit (PR gate)` job `132833579900`: `3511/0/0`; `Frontend npm audit`: SUCCESS; C05: 0 newly disabled/skipped tests in diff).

Frontend unit specs (Vitest via `@nx/vitest:test`, not Karma; Nx CI log records target-level pass, per-file counts verified from commit `94001683` with 0 skips/todos):
- `asset-list.component.spec.ts`: `6/0/0` (`7/0/0` on `main`)
- `contract-detail.component.spec.ts`: `10/0/0` (`11/0/0` on `main`)
- `contract-list.component.spec.ts`: `7/0/0` (`9/0/0` on `main`)
- `contract.service.spec.ts`: `5/0/0`
- `contracts.routes.spec.ts`: `1/0/0`
- `sidebar.component.spec.ts`: `3/0/0` (`4/0/0` on `main`)

Node E2E helper tests (outside `scripts/ci/frontend.sh`, not run in `PR check`; verified locally via `node --test` on `main` `a73ff773`: `29/0/0`):
- `frontend/e2e/lib/mcp-playwright.test.mjs`: `15/0/0`
- `frontend/e2e/lib/report.test.mjs`: `10/0/0`
- `frontend/e2e/lib/residue.test.mjs`: `3` at `#1003` (`4/0/0` on `main`)

Integration / Browser UAT: no backend integration tests in PR #1003; Playwright UAT suites (`uat-full.mjs`, `uat-jev-mcp.mjs`, asset-lifecycle UAT) are not wired into `pr-check.yml`.
```

---

## Known gaps & explicit limits

- **Expired XML artifacts:** `pr-check-unit-test-reports` has 7-day retention in `.github/workflows/pr-check.yml`, so XML artifacts for runs `41319799611`, `41338079411`, `41338081186`, `41338076686`, and `43721274542` have expired; all backend counts come from the preserved GitHub Actions job logs for those exact run IDs.
- **`#719` (`DRG-656`) PR-time CI gap:** PR `#719` had no PR-gate check run; evidence comes from its post-merge `Main verify` run `41320727271` (job `124890148174`) on merged commit `abafd089` and nightly `Backend Integration` run `45928423550`.
- **`#1003` (`DRG-761`) Vitest log granularity:** `nx run-many --target=test --all` logs target-level pass markers (`cpq-contracts:test`, `crm:test`) without per-spec stdout; per-file case counts were reconciled statically from commit `94001683` and `origin/main` (0 `.skip`/`.todo`/`.each`), and `frontend/e2e/lib/*.test.mjs` is not invoked by `scripts/ci/frontend.sh`.
- **Linear comment posting:** Posting the six comments above to `DRG-654`, `DRG-655`, `DRG-656`, `DRG-658`, `DRG-659`, and `DRG-761` requires an authenticated Linear writer (`cod-00` Linear MCP) to complete `DRG-930`'s acceptance criterion ("Counts are posted on each issue and match the CI reports") without altering any issue status.

## References

- Linear `DRG-930`: https://linear.app/drgamtd-workspace/issue/DRG-930/residual-dod-am04-named-tests-with-ci-matched-passfailskip-counts-on
- DoD v0.4 rubric: https://app.notion.com/p/408f7cb4e4df81b18b7adfee3d5b8e82
- `DRG-652` W0-3 evidence: https://app.notion.com/p/422f7cb4e4df81e4b286d7e4cfa5ce0f
- Related residual PRs: `#1093` (`DRG-932` write-window), `#1094` (`DRG-931` AM06 review verdicts)
