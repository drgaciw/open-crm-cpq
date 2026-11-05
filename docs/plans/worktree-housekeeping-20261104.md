# Worktree housekeeping — 2026-11-04

User scope: review every registered worktree, publish its branch, reconcile pending work through protected main, and retain exactly ten worktrees. Three agents reviewed recent CPQ, legacy ADR/ramp/CSRF, and OMS branches in parallel; cleanup ran after source-read clearance.

## Recovery and retention

The initial inventory contained 49 worktrees and 47 named branch checkouts (including main). All had clean tracked files. Local-only files are preserved outside Git in `C:/Users/dgorn/worktree-housekeeping-20261104/`: a complete `branches-before.bundle`, original inventory/ref manifests, individual ZIP archives, SHA-256 checksums, and per-file archive metadata. No historical branch is deleted. The nested `jev-worker/` is an independent clean repository and remains untouched.

Archives preserve non-generated untracked files, ignored local configuration, logs, verification evidence, and Surefire/Failsafe reports. Rebuildable GitNexus indexes, dependencies, Angular/Nx caches, dist, and other target output are excluded and listed in each archive manifest. Local credentials are never committed or published.

Retain main, two recent CPQ worktrees, three ramp verification snapshots, ADR integration/runtime, and OMS integration/advisory. These ten cover current work and unresolved integration evidence without retaining every component checkout.

## Merge policy and review

GitHub main rules require a pull request, one independent approving review, and linear history. Auto-merge is disabled. Normal merge-commit pushes are rejected. Historical ancestry is preserved on pushed housekeeping branches; source changes must enter main by squash/rebase through a reviewed PR. No rule bypass or force push is authorized or used.

Recent branches were already squash-landed: flexible ramps in #750 and dated-term correction in #772. The dated branch combined patch matches #772; the flexible-ramp branch matches all 37 changed files at its squash. `housekeeping/recent-20261104` records their ancestry while preserving the exact main tree. Four DRG spec branches are already ancestors of main.

Legacy review preserves evolved upstream implementations rather than replacing them with snapshots. The unique AI ownership-boundary test is recovered; the legacy reconciliation report records per-branch evidence. OMS is a substantive pilot integration and is reviewed separately against current quote acceptance, Kafka inbox/outbox delivery, migrations, and operational flags.

## Initial worktree inventory

| Initial branch or detached tip | Initial path | Retention |
|---|---|---|
| main | C:/Users/dgorn/open-crm-cpq | Retain |
| codex/ramp-wave-20261009-integration | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-wave-20261009/integration | Archive and remove |
| codex/ramp-wave-20261009-r1-verification | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-wave-20261009/r1-verification | Retain |
| codex/ramp-wave-20261009-r2-backend | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-wave-20261009/r2-backend | Retain |
| codex/ramp-wave-20261009-r2-frontend | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-wave-20261009/r2-frontend | Retain |
| codex/uat-frontend-csrf-20261010 | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/uat-fixes-20261010/frontend-csrf | Archive and remove |
| codex/uat-gateway-csrf-20261010 | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/uat-fixes-20261010/gateway-csrf | Archive and remove |
| codex/uat-harness-csrf-20261010 | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/uat-fixes-20261010/harness-csrf | Archive and remove |
| codex/uat-integration-20261010 | C:/Users/dgorn/.codex-worktrees/open-crm-cpq/uat-fixes-20261010/integration | Archive and remove |
| feat/oms-acceptance | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-acceptance | Archive and remove |
| feat/oms-broker | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-broker | Archive and remove |
| feat/oms-ci-browser | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-ci-browser | Archive and remove |
| feat/oms-compat | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-compat | Archive and remove |
| feat/oms-contract | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-contract | Archive and remove |
| feat/oms-contract-final | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-contract-final | Archive and remove |
| feat/oms-db-role | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-db-role | Archive and remove |
| feat/oms-domain | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-domain | Archive and remove |
| feat/oms-drills | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-drills | Archive and remove |
| feat/oms-evidence-docs | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-evidence-docs | Archive and remove |
| feat/oms-execution | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-execution | Archive and remove |
| feat/oms-finance | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-finance | Archive and remove |
| feat/oms-fulfillment | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-fulfillment | Archive and remove |
| feat/oms-gateway-recovery | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-gateway-recovery | Archive and remove |
| feat/oms-integration | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-integration | Retain |
| feat/oms-operations | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-operations | Archive and remove |
| feat/oms-quote | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-quote | Archive and remove |
| feat/oms-quote-correction | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-quote-correction | Archive and remove |
| feat/oms-recovery | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-recovery | Archive and remove |
| feat/oms-reliability | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-reliability | Archive and remove |
| feat/oms-replacement | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-replacement | Archive and remove |
| feat/oms-source-retention | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-source-retention | Archive and remove |
| feat/oms-ui | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-ui | Archive and remove |
| feat/oms-ui-adjudication | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-ui-adjudication | Archive and remove |
| feat/oms-ui-final | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/oms-ui-final | Archive and remove |
| impl/s3e-4-pricing-batch-no-aggregate-ownership | C:/Users/dgorn/.config/superpowers/worktrees/open-crm-cpq/stack-3e | Archive and remove |
| grok/drg-645-spec | C:/Users/dgorn/cpq-wt/cpq-drg-645 | Archive and remove |
| grok/drg-646-spec | C:/Users/dgorn/cpq-wt/cpq-drg-646 | Archive and remove |
| grok/drg-647-spec | C:/Users/dgorn/cpq-wt/cpq-drg-647 | Archive and remove |
| grok/drg-649-spec | C:/Users/dgorn/cpq-wt/cpq-drg-649 | Archive and remove |
| feat/cpq-parallel-followups-20261101 | C:/Users/dgorn/cpq-wt/cpq-parallel-followups-20261101 | Retain |
| fix/ramp-extension-dated-term | C:/Users/dgorn/cpq-wt/pr-review-dated-ramp-20261101 | Retain |
| detached d99bc1c0 | C:/Users/dgorn/open-crm-cpq/.kilo/worktrees/shining-scissor | Archive and remove |
| codex/adr014-evaluation | C:/Users/dgorn/open-crm-cpq-adr-approvals | Archive and remove |
| codex/adr-audit-privacy | C:/Users/dgorn/open-crm-cpq-adr-audit-review | Archive and remove |
| codex/adr014-refresh | C:/Users/dgorn/open-crm-cpq-adr-database | Archive and remove |
| codex/adr-gap-integration | C:/Users/dgorn/open-crm-cpq-adr-integration | Retain |
| codex/adr014-execution | C:/Users/dgorn/open-crm-cpq-adr-runtime | Retain |
| detached 03bb6a67 | C:/Users/dgorn/open-crm-cpq-integrate | Archive and remove |
| feat/oms-jev-advisory | C:/Users/dgorn/open-crm-cpq-oms-jev | Retain |

## Verification and limits

Frontend focused quote-service unit tests: 19 passed. Recovered AI ownership-boundary tests: 3 passed. Detailed source integration verification and final cleanup outcomes are recorded below when complete.

Docker-dependent integration tests and browser/chaos UAT are separate from unit-test results. Browser/chaos scripts are not wired into CI. Whole-frontend CI builds/lints/tests all Nx projects but has no frontend typecheck target; PR-check runs no frontend stage. The full CI matrix and primary Jenkins pipeline do include frontend. CPQ services were not started or restarted during housekeeping.

## Completed cleanup

Verified final registered worktree set: exactly 10. Removed 39 registrations and preserved 2,176 local files in 39 ZIP archives totaling 13,325,178 compressed bytes. Every ZIP was reopened and checked for expected entries, CRC integrity, and SHA-256. Each removed branch was verified against its same-named remote and complete local Git bundle; detached heads were confirmed in bundled main history.

Windows left empty `C:/Users/dgorn/cpq-wt/cpq-drg-645` and `C:/Users/dgorn/cpq-wt/cpq-drg-649` directories after native Git removed their contents and registrations. Automatic approval review rejected a subsequent empty-directory deletion with `blocked by policy`. No bypass or process termination was attempted.

## Ongoing housekeeping

Keep a ten-worktree cap. Reuse the retained integration worktrees rather than allocating a checkout for every component. After a PR lands, compare source changes against the squash commit before treating branch-only commit counts as outstanding work. Verify the remote ref and archive meaningful local files before removal. Keep backups local until merge review and relevant verification are complete; do not commit caches or replace evolved upstream code with historical snapshots.

## Final source verification

All 47 original named refs, including main, were verified remotely by ancestry against the initial snapshot. The three housekeeping aggregates preserve legacy and squash-landed source history without deleting historical branches.

PR #795 includes the original AI boundary test and a four-line gateway test-fixture repair. Untouched main reproduced two failures in six CSRF bootstrap tests because the lightweight ReactiveWebApplicationContextRunner lacked Boot conversion and read the origin CSV as one list item. Installing ApplicationConversionService in the fixture makes all six pass; production filters and every token/origin/status assertion remain unchanged. The root merge was graph-checked using the fresh linked-worktree index and explicit root worktree binding: no partial or truncated precommit result; risk remains CRITICAL. A broad depth-three test-class impact query hit GitNexus internal chunk limits and remains partial; a bounded depth-one rerun and the modified runner method analysis completed. Do not describe that broad query as clean.

The main checkout dependency directory was restored with npm ci from its committed lockfile. With matching declared versions, focused quote-service tests passed 19 tests and OMS orders passed 28 tests in four files; the standalone OMS evaluator passed 10 Python tests. The restored AI boundary test passed three targeted Maven tests. These focused checks are not whole-frontend CI or browser UAT.

The substantive OMS pilot integration has a separate branch and review. It reconciles existing migration checksums, shared event and security contracts, legacy default ownership, and explicit disabled rollout flags. Its compilation/unit evidence and remaining Docker/runtime checks are tracked with that integration; this housekeeping PR does not claim OMS has merged to main.
