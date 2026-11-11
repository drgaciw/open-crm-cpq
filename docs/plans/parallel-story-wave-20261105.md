# Parallel story wave — 2026-11-05

The owner requested delegation of the next Linear/Notion stories with maximum parallelism. This run uses four concurrent slots: root integrates; three leads own separate deliverables. Leads may fan out independent reviewers when a slot becomes available. Parallelism is bounded by dependencies and file ownership, not by the number of backlog rows.

## Source and collision checks

Live [Linear project](https://linear.app/drgamtd-workspace/project/open-crm-cpq-5a50ace0d150) is the delivery-status authority. The [Notion staffing board](https://app.notion.com/p/410f7cb4e4df812bafabe9ac08a9e738) and [delegation log](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82) supply staffing and decision context. Read on November 5, approximately 19:00–19:25 America/Chicago. There is no current DRG cycle and no Todo/unstarted project queue; Backlog alone does not establish readiness.

Existing work must not be duplicated:

| Work | Observed ownership/evidence |
| --- | --- |
| DRG-511, DRG-517, DRG-514a | Notion reports existing Cursor workers; native worker health was not independently checked. |
| DRG-699 | In Progress, PR #802. |
| DRG-623 | In Review, PR #827. |
| DRG-644 / DRG-675 / DRG-568 | In Progress, PRs #800 / #804 / #807. |
| DRG-534 | In Progress, existing decision brief PR #803. Scout/reconciliation only here. |
| DRG-460 / DRG-469 | Existing activation brief PR #809 covers both. |
| DRG-714 / DRG-715 | In Progress, PRs #814 / #815. |
| DRG-622 | Existing repository evidence identifies PostgreSQL concurrency coverage landed in #722; no duplicate fix. |
| DRG-512 | Notion explicitly says planning-only, not cleared for implementation. |

## Delegated lanes

| Agent | Story and concrete deliverable | Exclusive ownership | Release condition |
| --- | --- | --- | --- |
| `repo_readiness` | [DRG-531](https://linear.app/drgamtd-workspace/issue/DRG-531): verify period-cost foundation; implement additive term-start transport/date selection only if the actual cost lookup AC can be met. Otherwise produce a precise prerequisite brief. | `drg-531-term-start-readiness-20261105.md`; proposed quote/pricing transport and date classes/tests only after graph impact. | Actual term start dates period 2 at 2028-01-01 for a 2027-01-01 start; absence preserves fallback; existing consumers remain compatible. |
| `notion_queue` | [DRG-520](https://linear.app/drgamtd-workspace/issue/DRG-520): verify accepted ADR-030 and landed DRG-510, then establish complete timeline API/privacy contract and implement only if supported. | `drg-520-activity-timeline-20261105.md`; new activity package/client/tests; parity doc. | Note/task/email/meeting timeline, valid subject access, author integrity and visibility; controller/schema/gateway/client delivered together. |
| `linear_queue` | [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534): reconcile the current code and proposed amendment requirements with existing PR #803. | `drg-534-ramp-amendments-readiness-20261105.md` only. | Record missing behavior, unresolved forks and next executable work without duplicating the current owner. |
| Root | Integration, dependency DAG, collision review and verification scheduling. | This plan; any required shared gateway/config contracts and migration allocation. | Review all deliverables and report implemented, ready, blocked and already-owned work separately. |

All filenames above are under `docs/plans/`. No worker may change another lane's files without coordination. The activity lead may fan out a reviewer when a slot frees; review must challenge unsupported blockers as well as unsafe implementation assumptions.

## Execution rules

- Work in `C:/Users/dgorn/open-crm-cpq`, branch `feat/parallel-story-wave-20261105`, base `7492fd09`. Reuse the current checkout; the existing ten-worktree cap is already reached. Preserve the owner's untracked `.angular/` and nested `jev-worker/`.
- Bind graph work to this checkout through the local CLI. GitNexus MCP and port 4200 point at the other checkout. No code edit before upstream impact; UNKNOWN requires confirmation; HIGH/CRITICAL requires a pre-edit warning.
- Root serializes index repair/refresh and heavyweight builds. Approximately 3–5 GiB RAM was free. No services, browsers or containers are started/restarted/killed by this delegation.
- Shared migration versions are allocated centrally after checking current main and the externally staffed CRM migration lanes. Do not reserve a version solely from this older checkout's maximum.
- Keep the S4 asset lifecycle/write holds, ADR-005, NgRx, runtime Jev flags, subscription FE, CI and other repository holds. No merge, deploy, flag activation, real-data action or external notification is authorized by this plan.
- Scope-blocked stories produce actionable prerequisite contracts; partially wired code does not count as satisfying an unavailable end-to-end acceptance criterion.

## Verification record

Initial discovery completed across all three agents. GitNexus incremental refresh failed on inconsistent `file_fts`; `analyze --repair-fts --index-only` succeeded. The next refresh detected the interrupted write and completed a full rebuild in 60 seconds: index/source commit `7492fd09`, 30,307 nodes and 63,422 edges. No application symbols were edited during repair. Final graph status and lane outcomes are recorded below after verification.

The activity lead fanned out two subagents: `activity_review` owns the frontend client and interceptor tests; `activity_integration` owns PostgreSQL/concurrency tests and reviews the backend boundaries. Four slots remain the maximum at any instant. Root added the new client to the CRM test script and path aliases and owns the gateway integration.

DRG-531's graph/source inspection confirmed the actual cost foundation is absent, so that lane delivered a prerequisite brief without unused transport code. DRG-534's reconciliation delivered the ownership/fork brief while preserving PR #803. DRG-520 proceeds with all four manually recorded activity types, `source=MANUAL`, verified workforce identity and the existing coarse CRM access policy. Email/meeting references are opaque metadata; no provider calls, bodies or verified-sync evidence are created. DRG-521 owns provider ingestion. ADR-036 commercial account tenancy and ADR-034 scoped agents are not implemented by this wave.

Locally allocate `V16__activity_timeline.sql`: fetched `origin/main` still ends at V15, DRG-517 has no migration, and DRG-514a explicitly has no preassigned migration and remains unpushed behind its CRM migration gate. This is a local allocation, not a reservation in another worker. Reconcile versions at the existing serialized push gate before any future publication.

AutoJev tools were not exposed; no advisory verdict is claimed. Linear's advertised `get_agent_context` returned tool-not-found. Local results cannot replace exact-head CI or CPQ Decision scoring; no story is marked Done here. Docker integration tests, browser UAT and chaos are separate checks, and browser/chaos scripts are not wired into CI. Frontend CI covers all Nx projects but has no typecheck target; the PR-check workflow has no frontend stage (DRG-527). A down CPQ service or empty fallback is not acceptance evidence.

## Review and executed checks

The activity lead reused a freed slot for `activity_final_review`, an independent reviewer who authored none of the patch. It found no actionable defect in the bounded API/client contract. The frontend author also reviewed the backend independently. Review corrected DTO null handling, deleted-subject filtering, authenticated JWT test fixtures and PostgreSQL completion-time precision before final verification.

| Check | Outcome |
| --- | --- |
| Focused backend activity service/HTTP tests | 18 passed. Real binding/service/mapper/advice with mocked persistence; not a full DB/security-chain test. |
| Angular client tests | 10 passed with the real response interceptor. |
| New client lint | Passed. Existing Nx executor deprecation notices remain. |
| Client library and test TypeScript checks | Both passed. Initial unsupported matcher generic arguments were corrected and tests rerun. |
| Affected backend reactor | `mvnw.cmd -B -Punit-tests -pl services/crm-service,services/gateway-service -am test` passed: **991 tests**, zero failures/errors/skips (common263, security137, Jev decision66, CRM411, gateway114). Includes both new gateway predicate cases. |
| PostgreSQL integration | Five new integration cases compiled but were not executed. Docker's Linux-engine pipe is absent; Docker was not started. |
| Browser/UAT | Not run; this wave adds an API/client, with no timeline screen. |
| Whitespace | `git diff --check` passed. |

Evidence logs are local ignored files under `.gitnexus/`: `activity-backend-unit.log`, `activity-affected-modules-unit.log`, `activity-gateway-red.log`, and `drg-520-frontend-{test,lint,tsc-lib,tsc-spec}.log`. Gateway failing-first evidence is two actual missing-route failures in default/dev configurations. The backend's initial unauthenticated JWT-fixture failure is not claimed as failing-first feature evidence.

DRG-520 is local implementation for review, with human authors only and no provider sync or timeline screen. Accounts merged/soft-deleted hide their source-linked activities; no automatic activity transfer is implemented. Its database concurrency/migration guarantees remain pending the five Docker tests. DRG-531 is blocked on the missing landed cost foundation. DRG-534 remains with PR #803. All remote statuses and existing worker ownership remain unchanged.

## Final graph review

The final forced local rebuild completed in 58 seconds: 30,516 nodes, 63,863 edges, 899 clusters, source commit `7492fd09`. Scoped intent-to-add entries exposed this wave's new files to `git diff`; the owner's `.angular/` and `jev-worker/` were excluded. No content was staged for commit.

`detect-changes --scope all --repo .` reports **36 changed files, 174 symbols, 17 affected processes, CRITICAL risk**. The critical classification was reported before handoff and is retained; test success does not waive it. The affected flows are activity creation/completion through identity/context/authority checks, subject lookup and business exceptions. Existing shared authentication implementations were not edited. New-symbol pre-edit UNKNOWN results were confirmed against source before creation. YAML/JSON configuration targets were not graph-resolvable; exact configuration readers and passing default/dev predicate tests provide bounded confirmation, not a graph all-clear for configuration dependencies.

The normal CLI formatter prints at most 15 symbols and 10 flows even with a larger limit. A second local call through the same CLI `LocalBackend` implementation retained the complete structured result at `.gitnexus/parallel-wave-graph-full.json`: all **174/174** symbols and **17/17** processes, with no `partial` or `truncated` flag. This call binds both repository and worktree to `C:/Users/dgorn/open-crm-cpq`; it does not contact the GitNexus MCP or another checkout. The initial text output was not used as a completeness check.

Delivery remains local on `feat/parallel-story-wave-20261105`, without commit/push or tracker-Done claims. Full-file patch/hash evidence is retained under the ignored `.gitnexus/` directory. The ten-worktree count is unchanged.
