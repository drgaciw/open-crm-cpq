# OMS Jev offline integration evaluation

Date: 2026-09-21. Evidence type: synthetic, offline integration contracts. No paid or live Jev calls.

The first use case suggests one investigation step using seven finite choice codes. It cannot produce
an executable retry, waiver, acknowledgment, cancellation or completion command. The input contains
only finite category/severity/age bands and tri-state facts; identifiers and evidence stay in local bindings.

## Reproduction

From `backend`, Java 25 and Docker available:

```powershell
./mvnw.cmd -B -pl services/order-management -am test -Pintegration-tests '-Dtest=OrderAdvisory*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
```

`OrderAdvisoryFeaturesTest` checks allowed types, unknown values, age boundaries, excluded data,
deterministic hashes and bounded payloads. `OrderAdvisoryQuestionsTest` scripts the seven answer codes,
forbidden-action rejection, disabled behavior and confidence abstention through FakeJevTransport.
These are authored replies, not observed model predictions.

`OrderAdvisoryServiceTest` exercises Spring commit/rollback synchronization, no-transaction refusal,
disabled switches, duplicate claims, provider/logging failures and in-flight disable. PostgreSQL tests
exercise real migrations, competing claims, stale-version admission, scoped bounded snapshots,
atomic log/link rollback, runtime-role grants and reconciliation with a throwing optional adapter.

## Verification results (2026-09-21)

- Targeted advisory suite: 31 tests passed, including nine real PostgreSQL integration tests.
- Combined backend regression: 630 tests, zero failures, errors or skips across common, security,
  Jev, gateway, quote, OMS, approval-routing and contract-asset modules. The 31 advisory tests are
  included in this total. Docker-required integration tests actually ran.
- Frontend: `cpq-orders` and `cpq-quotes` Vitest targets passed.
- Separate `OrderLiveBrowserUat`: one test passed against isolated OMS HTTP/JWT/PostgreSQL.
  It exercised 11 commands, lost-response recovery and a keyboard probe, with zero browser errors
  and zero automated axe violations. The first attempt failed because the shared dependency tree
  lacked axe-core; installing the package.json-pinned 4.13.0 into the test's local cache fixed setup.
  No application change was needed. This is not manual screen-reader or deployed gateway evidence.
- Contract drift check and duplicate Flyway-version inventory passed. Local GitNexus reported critical
  aggregate risk across the integrated Jev branch, not just the OMS adapter. Initial final-review
  attempts crashed with native access violation `-1073741819`. A process-local 128 MiB database buffer
  and one query thread allowed the complete review to finish without partial/truncated output; the
  installed tool files and query logic were unchanged. The initial index lacked FTS, so no search-index
  completeness is claimed. Full graph review is required again after any subsequent code changes.
- Offline corpus scorer: ten unit tests passed, covering abstention/error denominators, confidence
  validation, forbidden codes, lineage leakage, development/test separation, uncertainty and synthetic
  evidence boundaries. The two-case example also ran through the CLI. These are scoring-tool checks,
  not additional measured model predictions or part of the 630 backend-test total.

Regression command from `backend` (Java heap capped at 256 MiB on this host):

```powershell
./mvnw.cmd -B -pl services/order-management,services/quote-service,services/contract-asset-management,services/gateway-service,services/approval-routing -am verify -Pintegration-tests '-Dspring.test.context.cache.maxSize=2' --no-transfer-progress
./mvnw.cmd -B -pl services/order-management -am test -Pintegration-tests '-Dtest=OrderLiveBrowserUat' '-Dsurefire.failIfNoSpecifiedTests=false' --no-transfer-progress
```

From `frontend`, with `NX_DAEMON=false`:

```powershell
node node_modules/nx/dist/bin/nx.js run-many -t test --projects=cpq-orders,cpq-quotes --parallel=1 --maxWorkers=1 --fileParallelism=false
```

These are explicit local checks, not final-commit CI. The ordinary frontend CI allowlist skips CPQ,
shared-ui and new Nx projects; backend-unit CI covers only CRM and gateway. Other inherited Jev-branch
services and shared-ui were not covered by the selected regression commands. Browser/chaos coverage
is not implied by ordinary CI passing. No existing local CPQ service availability was used as evidence.

## Model-quality gate: open

No expert-labeled held-out set or recorded live Jev predictions were available. Therefore there is no
measured precision/recall, calibration, category baseline improvement, operational latency or provider
cost result. The proposed 200-case / 90%-precision entry criteria are not met or claimed. Do not show
recommendations to operators on the basis of scripted tests.

The next evaluation deliverable is a versioned, independently labeled, lineage-split corpus covering
every pilot category, with existing category rules as baseline and a frozen development-selected
confidence threshold. Record abstention, coverage, confusion matrix and confidence intervals on held-out
predictions. A synthetic round-trip success must never be included as a successful prediction.

The [evaluation protocol](jev-evaluation-protocol.md) and `scripts/oms/evaluate_jev.py` now provide the
local input format and scoring workflow. No independently labeled corpus was provided, so the checked-in
example is explicitly synthetic. The scorer always reports `releaseApproved: false`; independent
evidence review and operational gates remain necessary even when numerical thresholds are reached.

## Operational limits

The hooks are intentionally best-effort: saturation, crash or failure can lose an advisory. Once claimed,
the same subject/version/features/model/rubric/transport configuration is not automatically retried.
Expiry does not authorize a retry. Later material state/age-band changes may produce a new evaluation.
Existing reconciliation scopes and cadence determine observation; this is not a new polling service.
Shadow is limited to the deployment's single `oms.scope-id`; configuring a different recovery scope
disables advisory submission without preventing recovery. Queries have a five-second timeout.

No public advisory API, UI, outcome feedback or provider transport is enabled. The shared model metadata
does not yet prove a facade's actual evaluator version, and its call budget is per instance. Both are
explicit live-rollout gaps. No production OMS launch, provider-accuracy, availability, load, full accessibility,
retention or infrastructure-loss recovery evidence is added by these tests.
