# Open CRM CPQ: backend gate remediation plan

**Prepared:** 2026-11-08 · **Revised/validated:** 2026-11-08 (against `origin/main` `9fce6dce`)
**Scope:** Failures in `PR check` (`pr-check.yml`) and `main-verify.yml`. These are recommendations; no fixes have been made yet.

## Executive assessment

**`main` itself is red; this is not a PR-only problem.** The last green `main-verify` was [run 42388639316](https://github.com/drgaciw/open-crm-cpq/actions/runs/42388639316) on `0fb75c15`, 2026-11-07 15:54Z. That commit used Boot 4.0.9 and springdoc 2.3.0. Every non-cancelled `main-verify` since then has failed: 21 failed, 84 cancelled, 0 green. Every PR therefore inherits a broken base, including documentation and dependency PRs.

Three compounding causes:

1. **Nothing enforces the gate.** Ruleset `15678208` ("Protect main branch") requires a pull request, linear history, and blocks deletion and force-push. It has **no `required_status_checks` rule**, so `PR check` is advisory. All six PRs cited in the first draft merged with red checks: #934, #932, #832, #925, #837 and #824. So did the two dependency bumps that caused P0/P1: #907 (Boot 4.1.2) and #922 (springdoc 2.10.0).
2. **Incompatible dependencies were merged.** #907 moved the parent to **Spring Boot 4.1.2**, despite the parent POM comment `HOLD: keep Spring Boot 4.0.x (no 4.1 until the Azure line is resolved)`. #922 moved **springdoc 2.3.0 → 2.10.0**. springdoc's [compatibility matrix](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) maps Boot 4.0.x to springdoc **3.0.x** and Boot 4.1.x to springdoc **3.1.x–3.2.x**; the 2.x line targets Boot 3. 2.3.0 happened to work on Boot 4.0.9. 2.10.0 loads the Boot 3 class `org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties` and fails.
3. **The fail-fast reactor hides failures in later modules.** `run-core.sh backend-unit` stops at the first failing module, so each fix reveals the next failure. On current `main`, the Gateway fails and the nine modules after it are `SKIPPED`: Pricing, Price Book, Product, Quote, AI, Configuration Engine, Approval Routing, Document Generation and Contract/Asset Management. Their status on the current base is **unknown**.

## Validated evidence

| Pri | Finding | Evidence | Status on `main` `9fce6dce` |
|---|---|---|---|
| **P0** | Gateway context fails: springdoc 2.10.0 references the missing Boot 3 `WebFluxProperties`. This fails `GatewayApplicationStartupTest` (4 failures) and `GatewayManagementPortTest` (4 errors). | [main-verify 42498751130](https://github.com/drgaciw/open-crm-cpq/actions/runs/42498751130). The same signature appears on PR runs [#934](https://github.com/drgaciw/open-crm-cpq/actions/runs/42498537715), [#832 (docs)](https://github.com/drgaciw/open-crm-cpq/actions/runs/42492593062) and on #922's own run [42432842465](https://github.com/drgaciw/open-crm-cpq/actions/runs/42432842465), where it was **already red before merge**. | **Reproduces on main.** Root cause confirmed against springdoc's matrix. |
| **P0** | PR check is not a required status check, so red PRs merge. | `gh api repos/drgaciw/open-crm-cpq/rulesets/15678208`: no `required_status_checks` rule, no bypass actors. Classic branch protection is not configured (HTTP 404). | Confirmed. This is the root cause of the cascade. |
| **P1** | Contract/Asset Management (CAM) context fails: the Boot 3-only verifier in `resilience4j-spring-boot3` 2.4.0 rejects the runtime. The verifier itself says *"Update your project to use `io.github.resilience4j:resilience4j-spring-boot4`"*. | [PR #837 run 42422285214](https://github.com/drgaciw/open-crm-cpq/actions/runs/42422285214), after #907. The direct dependency is declared at `backend/services/contract-asset-management/pom.xml:74-77`. `AmendmentPricingHttpHoldTest` **passed** on the last green main, which used Boot 4.0.9. | **Masked on main** because CAM is `SKIPPED`. Expect it to reappear once P0 is fixed. |
| **P2** | `main` turned red at 17:56Z on 2026-11-07, **before** #907 merged at 18:22Z. The first failure was the architecture fitness test `SecurityBaselineFitnessTest.noIdentityInRequestBodies`. Later, `AiServiceAggregateOwnershipBoundaryTest.mainSourcesDeclareOnlyAiAuditLogEntity` failed (expected 1, got 2). | main-verify [42398796792](https://github.com/drgaciw/open-crm-cpq/actions/runs/42398796792) (Security Library) and [42422804018](https://github.com/drgaciw/open-crm-cpq/actions/runs/42422804018) (AI Service). | Security Library now **passes**. AI Service is **unknown** because it is `SKIPPED` behind the Gateway. |
| **P3** | `GatewayCsrfBootstrapTest` got 403 where the tests expected 200 and 401. | [PR #824 run 42140717485](https://github.com/drgaciw/open-crm-cpq/actions/runs/42140717485), dated 2026-11-06 on an older base. | **Likely stale.** On the last green main the CSRF bootstrap suite passed 6/6 and `CsrfProtectionFilterTest` passed 13/13. Only recheck after P0 is fixed. |
| Doc | The documented baseline has drifted. | `CLAUDE.md` and `AGENT.md:12` say Boot **4.0.6**, the Flyway comment in `backend/pom.xml:49` says **4.0.9**, and the parent is **4.1.2**. | The documentation does not match the POM. |

## 0. Stop the bleeding (do first)

1. **Freeze merges to `main`** apart from the shared-fix PR(s) below until `main-verify` is green. Announce the freeze; queued Renovate automerges keep landing otherwise.
2. **Make `Backend Unit (PR gate)` a required status check** in ruleset `15678208`, with "require branches up to date" if the merge volume allows it. Do this **after** main is green, otherwise every PR is blocked. Also check whether Renovate's `automerge` settings honour required checks.
3. Pause Renovate PRs for `spring-boot-starter-parent`, `springdoc.version` and `resilience4j` until the baseline decision in step 1.1 below is recorded. Use `packageRules` with `enabled: false` or `dependencyDashboardApproval: true`.

## 1. Restore a compatible baseline (one shared-fix PR)

1. **Decide the Boot line; the owner must choose.**
   - **Option A: honour the HOLD.** Revert the parent to **4.0.9** (the last green version) and set springdoc to **3.0.x**. This is lowest risk and consistent with the POM comment. Spring Cloud `2025.1.4` must still be checked against 4.0.x.
   - **Option B: lift the HOLD.** Stay on **4.1.2** with springdoc **3.1.x/3.2.x**. Remove or rewrite the HOLD comment, confirm the Azure line (`spring-cloud-azure` 5.8, verifier currently disabled) still works on 4.1, and confirm Spring Cloud `2025.1.4` supports Boot 4.1.
   - Record the decision in an ADR addendum or in the parent POM comment. Fix the version drift in `CLAUDE.md`, `AGENT.md` and the Flyway comment in the same PR.
2. **springdoc:** set `springdoc.version` to the 3.x line that matches the chosen Boot version. The parent `dependencyManagement` pins both `springdoc-openapi-starter-webmvc-ui` and `-webflux-ui`, so all services move together. Run startup and `/v3/api-docs` checks for the gateway **and** every WebMVC service, not only the gateway.
3. **Resilience4j (CAM):** if Boot 4.1 is kept, replace `resilience4j-spring-boot3` with the Boot 4 module named by the verifier, `resilience4j-spring-boot4`. Confirm it is published at a version that matches `resilience4j-ratelimiter`. Keep the `erpSync` rate limiter configured. Do not disable the verifier. If Option A is chosen, still re-run `AmendmentPricingHttpHoldTest` to confirm it passes on 4.0.x as it did before.
4. **Diagnose with fail-at-end** so one run shows every broken module, not only the first:

   ```bash
   cd backend
   ./mvnw -B -fae -pl services/gateway-service -am dependency:tree \
     -Dincludes='org.springframework.boot:*,org.springdoc:*,org.springframework.cloud:*,io.github.resilience4j:*'
   ./mvnw -B -fae verify -DskipITs     # or the backend-unit profile used by run-core.sh; check the exact flags there
   ```

5. Triage everything the `-fae` run reveals, especially **AI Service** (P2 fitness test) and the other eight skipped modules. Fix them in the same PR if they block the gate, otherwise in separate PRs. All of them must be green before main is declared fixed.
6. Run the CI stages exactly as CI does, from the repo root:

   ```bash
   python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py   # validated: passes on 2026-11-08
   bash scripts/ci/run-core.sh backend-unit
   ```

## 2. CSRF (recheck only)

After main is green, confirm that `GatewayCsrfBootstrapTest` and `CsrfProtectionFilterTest` pass. Open an investigation only if they fail on the corrected base. If one is needed: identify the filter returning 403, compare it against the ADR-016 bootstrap and origin contract, and keep every security assertion enabled.

## 3. Land and propagate

1. Merge the shared-fix PR only with a **green** PR check on its exact head, then confirm `main-verify` is green on the resulting merge commit.
2. Enable the required check (step 0.2), then lift the merge freeze.
3. The PRs cited in the first draft are **already merged**, so there is nothing to rebase for them. Update the **open** PRs from the fixed base according to the stack/merge policy (Graphite stacks: restack from the bottom). Re-run PR check on representative docs, frontend-only and backend PRs, then on the rest. Record head SHA, base SHA and run link for each.
4. Treat any new failure as independent until proven otherwise, and record test name, root cause, commit and run link.

## Acceptance criteria

- The Boot line decision (A or B) is recorded. The parent POM comment, `CLAUDE.md` and `AGENT.md` agree with the effective POM.
- springdoc is on the 3.x line that matches Boot. No `*-spring-boot3` integration artifacts remain in the dependency tree.
- `run-core.sh backend-unit` passes with **all 16 reactor modules `SUCCESS`** (none `SKIPPED`) on the fix PR and on the resulting `main` commit's `main-verify`.
- The gateway starts, the management port behaves as tested, and `/v3/api-docs` responds on the gateway and on at least one WebMVC service.
- `AmendmentPricingHttpHoldTest`, the CSRF suites, `SecurityBaselineFitnessTest` and `AiServiceAggregateOwnershipBoundaryTest` pass, or each remaining defect has a tracked issue.
- `Backend Unit (PR gate)` is a required status check in ruleset `15678208`.
- No test, security gate or compatibility verifier was disabled to get green. The existing `spring.cloud.azure.compatibility-verifier.enabled=false` is pre-existing and must be revisited under Option B.

## Validation log (2026-11-08)

| Claim in first draft | Result |
|---|---|
| Boot 4.1.2, springdoc 2.10.0 on main | ✅ Confirmed (`backend/pom.xml:10`, `:55`). |
| Boot 4.0.x HOLD comment exists | ✅ Confirmed (`backend/pom.xml:198-200`), but #907 violated it. |
| Run links ↔ PR numbers | ✅ All six map correctly. ⚠️ All six PRs are **merged**, not pending. |
| "Leading hypothesis" springdoc/Boot mismatch | ✅ Upgraded to **confirmed** using springdoc's matrix and the timeline: 2.3.0 green → 2.10.0 red. |
| Failure is PR-scoped | ❌ `main-verify` has been red since 2026-11-07 17:56Z. |
| Resilience4j "confirm still reproducible" | ⚠️ Cannot be reproduced on main yet because CAM is skipped. The dependency is unchanged, so expect it to reproduce. |
| CSRF P1 | ⬇️ Downgraded: suites passed on the last green main. |
| 672 PR-check runs | ✅ `total_count = 672`. |
| `./mvnw`, module paths, scripts, test classes exist | ✅ All present. The drift check passes. |

**Not done:** no fix applied, `backend-unit` not run locally, no `-fae` run, and no review of all 672 runs. The Resilience4j Boot 4 artifact version and Spring Cloud 2025.1.x support for Boot 4.0 and 4.1 still need checking against upstream docs before choosing versions.
