# CPQ ramp followup delivery wave

The owner delegated selection and parallel execution of the next CPQ stories on 2026-11-01. This wave delivers DRG-633, DRG-634 and DRG-635 together because they extend the same landed ramp-authoring capability. Work uses `feat/cpq-parallel-followups-20261101`, isolated from the owner's checkout. The branch was synchronized from initial base `4615b35c` to `origin/main` commit `5ccfe79c` for closeout.

## Sources and selection

Linear is the delivery-status authority; the [Notion project hub](https://app.notion.com/p/3e3f7cb4e4df8197abf7d804245c5df4) describes Notion statuses as a lagging mirror. Existing local architecture summaries claiming no backlog are historical.

| Story | Acceptance | Owner and scope |
| --- | --- | --- |
| [DRG-633](https://linear.app/drgamtd-workspace/issue/DRG-633) | Offer percent increases against the base price, with existing compounding as the default. | Ramp lead `linear_backlog`: escalator DTO, expansion and extension, ramp editor, focused backend and frontend tests. |
| [DRG-634](https://linear.app/drgamtd-workspace/issue/DRG-634) | Generate a shorter final period when term length does not divide by interval; cover the entire term without overlap or gaps. | Same ramp lead, avoiding concurrent writers in the generator. |
| [DRG-635](https://linear.app/drgamtd-workspace/issue/DRG-635) | Extending a ramp saves the extended quote term and survives reload. | Persistence lead `notion_requirements`: query/controller/service persistence and optimistic locking, parent quote integration and tests; coordinate editor changes with ramp lead. |

DRG-532 authoring and DRG-632 quote tenant prerequisites are landed on the selected base. The original owner checkout at `55ed45cf` lacks them and remains unchanged.

The readiness agent verified [DRG-622](https://linear.app/drgamtd-workspace/issue/DRG-622) is already covered by `DocumentApplyIntegrationTest` real PostgreSQL lock-race and HTTP-race tests, landed in PR #722. Leave that false positive untouched. Docker is unavailable, so those tests have not been rerun.

[DRG-530](https://linear.app/drgamtd-workspace/issue/DRG-530) stays queued: its cost-import and pricing-catalog replication foundation is absent even on the selected base. [DRG-685](https://linear.app/drgamtd-workspace/issue/DRG-685) is a parked wizard epic with draft criteria and no children; it needs decomposition before delivery. Neither condition authorizes lifting project holds.

## Execution and ownership

Four concurrent agents are available including the root. Discovery initially filled all four slots. After readiness completed, the ramp lead spawned `ramp_boundary_review` for independent edge-case review. The root owns integration, this plan, shared shell/config decisions and final verification. Leads own disjoint files; changes to shared interfaces require explicit coordination. Heavy builds run one at a time because free RAM was approximately 6.4 GiB before indexing. No shared application shell changes are planned.

The worktree uses its own GitNexus index. Every symbol edit follows local CLI graph analysis and an upstream impact report; UNKNOWN needs confirming text evidence, and HIGH/CRITICAL is reported before editing. The GitNexus MCP and port 4200 refer to another checkout and are excluded from this wave. Final graph change analysis runs against the edited worktree.

The owner subsequently authorized the recommended closeout: clean dependencies under CI-pinned Node, real Docker/PostgreSQL verification, isolated browser UAT, next-wave readiness and one draft PR. Docker Desktop and this wave's bounded test services were started for those checks; unrelated containers started by Docker's own policy were not stopped. No production deploy, real-data apply, default flag change or merge is included. The conventional-commit skill is used for packaging; this repository's Jenkins/CI contract takes precedence over that skill's generic Buildkite examples. This feature branch is not a Graphite-managed stack branch.

## Product decisions

An omitted compounding option preserves current behavior. With base 100 and 10 percent escalation, compound prices are 100, 110, 121; noncompounding prices are 100, 110, 120. Amount-based escalation is unchanged. Extension preserves authored periods and uses the selected arithmetic for new periods. Period-count limits include the shorter final period.

The quote term and its persisted schedule must remain consistent. DRG-635 must not save an extended term while leaving the old persisted ramp as the only schedule. Reuse existing quote term storage; no migration is needed solely to add an already-present duration field. Use optimistic concurrency and existing approval/pricing invalidation rules.

Noncompounding generation calculates each output directly from the base plus the period index times the unrounded increment, then rounds to four decimal places. Extension anchors on the actual final authored price and adds multiples of the unrounded base increment. Existing short final periods remain unchanged during extension; new periods start after the covered month and the new final period may also be short.

The UI uses paired line-item identity and expected quote version to request atomic extension persistence. Legacy requests without either retain preview behavior; supplying only one is invalid. The atomic path persists the new term and target schedule together and returns the flushed quote version. A changed quote term invalidates pricing and approval, including other lines whose schedules need adjustment.

Notion mirrors: [633](https://app.notion.com/p/408f7cb4e4df810dbb54c0711d6c12c6), [634](https://app.notion.com/p/408f7cb4e4df81fab9adc52ab4d533ec), [635](https://app.notion.com/p/408f7cb4e4df818fb489e9ca5cdcbbb1). Their blank bodies supply traceability; Linear supplies the implementation requirements.

## Verification and delivery status

The three stories are implemented locally. Independent review covered arithmetic, metadata round trips, atomicity, optimistic conflicts and delayed responses. It caught and resolved misplaced form controls, cancellable mutation responses, older responses overwriting newer state, and conflicts raised during an automatic flush. The quote term and schedule save also permits another line to catch up to the quote's already extended term.

| Check | Observed result |
| --- | --- |
| `backend/mvnw.cmd -B -Punit-tests -pl services/quote-service -am test` | PASS: 905 tests, zero failures/errors/skips: quote-service 449, common 253, security 137, Jev decision 66. |
| `nx test cpq-quotes --skip-nx-cache`, `NX_DAEMON=false` | PASS under clean Node 22.22.3: 151 tests across 9 files, including two browser-discovered DOM binding regressions. |
| `nx run-many -t lint --projects=cpq-quotes,shared-models --parallel=1 --skip-nx-cache` | PASS. Existing unused-variable and non-null-assertion warnings remain outside this change. |
| `nx build crm --skip-nx-cache` | PASS: production build, initial bundle 694.36 kB. |
| `git diff --check` | PASS. |
| PostgreSQL closeout | PASS: 8 real integration tests, zero failures/errors/skips, on PostgreSQL 16 with actual Flyway migrations and Hibernate validation. Upgrade, reload, after-flush transaction rollback and simultaneous-version conflict are covered. The updated migration test also passed separately after executing the actual header rollback SQL and proving the original quote, line, escalator inputs and explicit ramp step survived. |
| Clean dependency install | PASS: `npm ci` under checksum-verified official Node 22.22.3/npm 10.9.8; installed Vite 8.3.5 matches the lockfile. The shared junction was removed without deleting the original dependency tree. |
| Browser UAT | PASS: six journeys against the actual quote service and private PostgreSQL with synthetic data; zero page errors. Default and noncompounding arithmetic, 26-month short tail, metadata hard reload, delayed committed-response locking, 38-month extension hard reload, and stale-version 409/no mutation passed. |
| GitNexus working-tree audit | Final closeout audit after a forced refresh: 32 files, 167 symbols, 5 affected flows, MEDIUM risk; full result contains all 167 changed symbols and returns no partial/truncated flags. |

Pre-edit upstream analysis of `RampEscalatorExpander.expand` reported HIGH risk: 13 impacted symbols, including its overload, schedule attachment and query expansion; `nextPrice` also reported HIGH risk across two processes. This warning was reported before edits and drove the broader quote regression run, including clone, continuation and renewal coverage. The final change audit's different MEDIUM classification does not waive that initial edit risk. The four final affected flows are expansion to business exceptions, list to escalator reconstruction, expansion to the noncompounding selector, and expansion to the base increment.

The index and audit are bound to `C:/Users/dgorn/cpq-wt/cpq-parallel-followups-20261101` and its edited working tree. The first audit used base `4615b35c`; the pre-commit closeout audit refreshes against base `5ccfe79c`. Full structured evidence is retained locally as `.gitnexus/wave-graph-audit.json`; the normal CLI formatter shows only 15 changed symbols, so its abbreviated output alone is not used as a completeness check.

Full-suite failures first exposed an obsolete remainder-rejection expectation and a migration inventory missing V1_16. The revised tests check persisted periods cover 14 months as 12 plus 2 and include the new migration. The final frontend lint also caught a loose null comparison in new template text; strict equality now passes.

Real browser UAT then found retained DOM controls: the noncompounding request and server response were correct (third unit 120), but the input still displayed 121 from the preceding compound generation. Two DOM regressions captured the failure for same-length generation and a refreshed line input, including edits propagating into the current controls. Replacing the form array through the root form's `setControl` in `replaceSteps` and `applyLine` fixes the bindings while preserving index tracking and avoiding new Angular collection-recreation warnings. The failing browser report, screenshots, failing-first logs and test diffs are retained in `.gitnexus/browser-artifacts/initial-ui-failure.json`, `browser-dom-red.log`, `browser-dom-red-test.patch` and `browser-dom-refreshed-red.log`. Their source reference is `5ccfe79c` plus the captured dirty working-tree/test diff, not an invented failing-first commit.

## Acceptance and test mapping

| Requirement | Test evidence |
| --- | --- |
| DRG-633 base-price percentage option; omitted value compounds; correct rounding | `RampEscalatorExpanderTest` arithmetic/rounding/normalization cases; `RampEscalatorMetadataTest` legacy NULL and false read/attach cases; editor option/request/DOM tests; real browser 100/110/121 versus 100/110/120. |
| DRG-633 authoring choice survives save/read/clone | `QuoteServiceRampTest` clone assertion; metadata round-trip tests; real `RampSchedulePersistenceIntegrationTest` reload and extension; `RampCompoundingMigrationIntegrationTest` legacy upgrade; browser hard reload. |
| DRG-634 short final periods without truncation, overlap, gaps or excess allocation | `RampEscalatorExpanderTest` generation/extension/overflow/ceiling/short-tail cases; `QuoteLineItemServiceRampTest` persisted 12+2 months and DTO; `RampScheduleQueryServiceTest` preview coverage; editor short-period tests; browser 26 = 12+12+2 and extension to 38. |
| DRG-635 durable term and target schedule save with old preview compatibility | `RampExtensionSaveTest`, query/controller tests, quote client tests, paired editor request tests, actual PostgreSQL reload and browser hard reload. Other line data is preserved. |
| DRG-635 stale version, concurrent race and rollback | Unit tests cover stale input, half-pair, foreign line, forbidden quote state, automatic/explicit flush conflicts and same-term second-line catch-up; real PostgreSQL tests cover simultaneous same-version transactions and rollback after flushed writes. Browser verifies actual 409 leaves the stored schedule unchanged. |
| DRG-635 pricing/approval invalidation and committed response reconciliation | Unit state/invalidation/status tests, delayed response and older-version client regressions; real browser holds a committed extension response, checks locked controls, then releases it and reloads. Live approval/outbox delivery remains outside browser coverage. |
| Schema and compatibility inventory | `LifecycleChangeMigrationGuardTest` includes V1_16; real Flyway upgrade and Hibernate validation use committed migrations. The new nullable column needs no backfill. |

Original DRG-635 RED compilation evidence is `.gitnexus/drg635-red.log` and its new regression test diff. DRG-633/634 have regression and browser evidence; no separate original preimplementation failing-first execution was captured for their initial arithmetic changes. This process evidence gap is disclosed for the designated scorer rather than retroactively described as TDD.

## Closeout and review gates

The [reusable browser harness](../../scripts/verification/ramp-followups-browser-acceptance.mjs) verifies six journeys and records HTTP outcomes, page errors, bundle hash, source head and dirty-tree status. Its successful promoted run used the real private quote service and PostgreSQL; a final script-only timeout/failure guard was syntax-checked and independently reviewed afterward. This distinction is retained. Screenshots and raw reports remain in ignored `.gitnexus/browser-artifacts/`. The wave-owned JVM, browser, static server and PostgreSQL container were stopped and their ports released. Docker Desktop and unrelated containers remain untouched after engine startup.

Independent review found no remaining blocking product issue. The designated CPQ Decision review remains pending: the connector exposes no invocation operation, and the available computer-use inventory reports local browser access disabled in Work settings. No approval, score or PASS is inferred from either limitation. No merge or Done transition is performed by this closeout.

The draft PR must publish its exact head/base/CI-run tuple and link the actual checks. The full changed-file set below is the allowlist; proxy configuration, YAML/runtime flags, workflows, security source, event registry, asset/apply production code, ADRs and unrelated user files are forbidden changes. Applicable security/tenant and live approval/outbox/pricing coverage gaps remain visible rather than marked N/A. New event types, registry entries and deployments are N/A because none are introduced or executed.

The [next-wave readiness plan](cpq-next-wave-readiness-20261101.md) preserves the latest asset holds, identifies the narrow DRG-623 residual, gates retry/event work on accepted contracts, and proposes seven wizard children. It records the newer owner queue DRG-651 → DRG-635 → DRG-699; no further implementation is launched by this closeout. Current ramp review should finish before the next coding wave is selected against that queue.

Local verification logs are in the worktree's ignored `.gitnexus/` directory. Initial logs are `quote-unit-regression.log`, `quotes-vitest.log`, `wave-frontend-lint.log` and `wave-crm-build.log`; closeout logs include `closeout-npm-ci.log`, `closeout-quotes-vitest.log`, `closeout-frontend-lint.log`, `closeout-crm-build.log` and `db-closeout-integration.log`. The owner's original checkout remains at `55ed45cf` with its original untracked directories untouched. Packaging targets one draft PR with an explicit changed-file allowlist; local credentials, generated reports, screenshots and runtime artifacts remain ignored.

Known verification boundaries: the Docker-backed quote ramp tests are separate from the Docker-free unit profile; this local run does not execute every backend module's integration tests. Browser UAT/chaos is outside CI. The isolated browser path uses the actual quote service and PostgreSQL, with synthetic data and loopback-only ports; gateway/CSRF enforcement, production JWT and tenant roles, pricing, live approval routing, outbox delivery and downstream systems are outside that path. Unavailable CPQ lists or 503 fallbacks would not establish feature correctness. No frontend typecheck target exists; the PR workflow has no frontend stage (DRG-527), while full CI and primary Jenkins include frontend. The production build compiles Angular templates and application types. Clean Node/dependency reproducibility is now verified rather than inherited from the earlier shared junction. AutoJev tools are absent and Linear's advertised `get_agent_context` returned tool-not-found; no advisory verdict or shared-note result is claimed.

The latest governing source is [DoD v0.4](https://app.notion.com/p/408f7cb4e4df81b18b7adfee3d5b8e82), whose body records owner approval despite its Draft title. It requires the designated CPQ Decision scorer and exact head/base/CI-run tuple. No callable scorer was exposed; local independent review supports, but cannot replace, that gate. Main commit `5ccfe79c` has a successful Backend Unit (main) check in [run 41341790990](https://github.com/drgaciw/open-crm-cpq/actions/runs/41341790990); that is not a full-matrix or feature-head CI result. Exact feature-head CI, designated scoring and owner merge remain separate gates. Trackers must not say Done before those gates are satisfied.

## Exact changed file allowlist

```text
backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItem.java
backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItemMapper.java
backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItemService.java
backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampEscalatorDto.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampEscalatorExpander.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampExtendRequest.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleAttacher.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleController.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleDto.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleQueryService.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleViews.java
backend/services/quote-service/src/main/resources/db/migration/V1_16__ramp_escalator_compounding.sql
backend/services/quote-service/src/test/java/com/opencrm/quote/lifecycle/LifecycleChangeMigrationGuardTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/lineitem/QuoteLineItemServiceRampTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteServiceRampTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampCompoundingMigrationIntegrationTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampEscalatorExpanderTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampEscalatorMetadataTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampExtensionSaveTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampSchedulePersistenceIntegrationTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampScheduleQueryServiceTest.java
docs/plans/cpq-next-wave-readiness-20261101.md
docs/plans/cpq-parallel-followups-20261101.md
frontend/libs/cpq/quotes/src/lib/quote-ramp-editor/quote-ramp-editor.component.html
frontend/libs/cpq/quotes/src/lib/quote-ramp-editor/quote-ramp-editor.component.spec.ts
frontend/libs/cpq/quotes/src/lib/quote-ramp-editor/quote-ramp-editor.component.ts
frontend/libs/cpq/quotes/src/lib/quote.service.spec.ts
frontend/libs/cpq/quotes/src/lib/quote.service.ts
frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts
frontend/libs/shared/models/src/lib/quote.model.ts
scripts/verification/ramp-followups-browser-acceptance.mjs
```
