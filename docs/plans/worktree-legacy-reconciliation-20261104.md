# Legacy worktree reconciliation, 2026-11-04

The owner requested publication and integration of every registered worktree
branch, followed by reduction to ten worktrees. This review covers the fourteen
ADR, October ramp, CSRF, and S3E worktree branches against `origin/main` at
`0a3f0f8e`. Existing feature flags and architecture holds remain unchanged.

The aggregate records the original branch tips as merge parents while retaining
the newer upstream implementation. It restores one genuinely missing file:
`AiServiceAggregateOwnershipBoundaryTest.java`, copied unchanged from S3E-4.
Its five tests verify the audit schema, the AI service's entity boundary, and
the absence of commercial aggregate repositories. No production code is changed.

## Branch decisions

| Branch | Evidence and resolution |
| --- | --- |
| `codex/ramp-wave-20261009-integration` | Already an ancestor of upstream; no source delta. |
| `codex/ramp-wave-20261009-r1-verification` | Preservation snapshot: 27 of its 48 changed files exactly match upstream. The other 21 have later ramp, event-contract, mapper, test, documentation, or browser-fixture improvements; retain upstream. |
| `codex/ramp-wave-20261009-r2-backend` | Preservation snapshot: 24 of 45 changed files exactly match upstream. Its migrations, approval binding, repository fixes, and persistence coverage are present; remaining files have subsequent improvements. |
| `codex/ramp-wave-20261009-r2-frontend` | Two of five changed files exactly match upstream, including the component test and Kafka integration test. Main's component adds explicit null checks and quote-version binding; its model adds later fields and its readiness document has later closeout evidence. |
| `codex/uat-frontend-csrf-20261010` | A normal merge preview produces exactly the upstream tree; integrated change already present. |
| `codex/uat-gateway-csrf-20261010` | A normal merge preview produces exactly the upstream tree; integrated change already present. |
| `codex/uat-harness-csrf-20261010` | A normal merge preview produces exactly the upstream tree; integrated change already present. |
| `codex/uat-integration-20261010` | Already an ancestor of upstream. |
| `codex/adr014-evaluation` | All twelve branch-only commits have exact patch equivalents upstream. |
| `codex/adr014-refresh` | All nine branch-only commits have exact patch equivalents upstream. |
| `codex/adr-gap-integration` | Already an ancestor of upstream. |
| `codex/adr-audit-privacy` | The payload-free routine logging line is present. Its privacy test is present with the newer `CurrentActor` constructor; retain the current test. |
| `codex/adr014-execution` | Eleven older commits have patch equivalents. The final preservation commit's `AiAuditLog` blob exactly matches upstream, and its Azure compatibility configuration is also present. Retain later security/runtime configuration. |
| `impl/s3e-4-pricing-batch-no-aggregate-ownership` | Pricing implementations and model-backed tests are present; newer audit actors, JSON mappings, model routing, migrations, and domain tests supersede the old versions. Restore the missing ownership-boundary test; retain upstream everywhere else. This is a Graphite-managed branch whose remote tip already equals its local tip. |

## Snapshot differences reviewed

The October 17 snapshots were expressly preservation commits, as recorded in
the local `sync-parity-20261017.json`, `sync-worktree-review-20261017.json`, and
`sync-parity-result-20261017.json` evidence. Their commit dates do not mean their
source is newer than the October 9-10 implementations already integrated upstream.

The nonidentical backend files retain the following upstream improvements:

- Event registry/topic additions for subsequent accepted event contracts.
- Pricing governance for hybrid migrations, authoritative rates, and period caps.
- Quote mappings and hash inputs for continuation operations, tenant identity,
  compounding, hybrid migration, proration, priced periods, and pricing values.
- Updated quote calculation, line-item, document, governance, and persistence tests.
- Repository integration-test isolation (`@Configuration`, not a second
  `@SpringBootConfiguration`) and the schema initialization script.
- Managed-seed integration coverage for the DRG-542 V9 rule-007 policy, retaining
  later policy expectations instead of restoring superseded amendment assertions.
- The independent-review graph closeout, browser font-asset allowance, and frontend
  readiness closeout.

The old AI audit migration is not restored over the newer schema history. Current
pricing-agent tests are not deleted to recreate the old S3E test layout. No Jev,
dual-read, Activities, or other held feature is enabled by this reconciliation.

Full file-by-file blob identities, latest upstream modifying commits, snapshot
diffs, and normal merge previews were retained in the local housekeeping archive
under `C:/Users/<username>/worktree-housekeeping-20261104/`.

## Validation and limits

The targeted Maven reactor build compiled the current AI service and its test
sources. `AiServiceAggregateOwnershipBoundaryTest` passed all five tests using
the normal forked Surefire JVM. An earlier diagnostic run with `forkCount=0`
failed because its reactor working directory changed the test's relative source
path; rerunning with normal Surefire forking passed without source changes.

Production `AiAuditLog` impact is CRITICAL (51 affected nodes, five direct
dependents, five processes in the older integration index). That class remains
unchanged. The newly restored test was initially absent from the graph; text
confirmation found only its original test definition and JUnit reflective usage.
The exact-worktree index was refreshed at `0a3f0f8e` before the final gate. Raw
all-scope analysis identified two files and six restored test symbols with
CRITICAL risk. Repeated complete runs reported 22 or 44 affected processes;
class impact reported 213-513 nodes, 88-313 direct dependents, and 25-54
processes. This observed graph count variability remains a verification limit;
the CRITICAL verdict was consistent. The raw reports have neither partial
nor truncated results. The broad graph reach includes reflective class lookup
and generic assertions; the actual source delta remains a test and this report.
The CRITICAL verdict is disclosed, not relabeled as low. An earlier stale-index
zero was not treated as clean evidence.

This targeted check does not execute Docker integration tests, browser UAT,
chaos testing, or full CI. The original snapshot branches and their remote refs
remain available. GitHub requires a pull request and linear main history, so the
eventual main integration must use the allowed PR squash workflow; the aggregate
branch preserves the detailed ancestry separately.
