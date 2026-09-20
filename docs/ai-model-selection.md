# AI model selection: evaluation and rollout

ADR-014 now has explicit catalog, evaluation, proposal, and execution components. The default application runtime remains Azure OpenAI. Evaluation and proposal creation do not enable OpenRouter execution. See [ADR-014](adr/ADR-014-jev-openrouter-model-selection.md), its [requirements](backlog/REQ-jev-openrouter-model-selection.md), and the [execution guide](../backend/services/ai-service/src/main/java/com/opencrm/ai/selection/execution/README.md).

## Build and run the offline example

Requirements: Java 25 and Maven dependency access if dependencies are not cached. These PowerShell commands start in the repository root:

```powershell
Set-Location backend
.\mvnw.cmd -pl services/ai-service -am package -DskipTests
java '-Dloader.main=com.opencrm.ai.selection.evaluation.SelectionEvaluationCli' -cp services/ai-service/target/ai-service-1.0.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher services/ai-service/src/test/resources/selection/example-evaluation-config.json
```

The packaged JAR/PropertiesLauncher command was executed against the checked-in template and returned `mode: dry-run`, `calls: 0`, `spend: 0`, and `selection: null`. It does not boot Spring or contact catalog, completion, or evaluator APIs. It writes `validated-config.json` and `outcome.json` in a new `backend/target/selection-dry-run/evaluation-<id>/` directory.

The [example configuration](../backend/services/ai-service/src/test/resources/selection/example-evaluation-config.json) is deliberately inert. Zero spending, placeholder model/provider identities, one allowed call, and strict illustrative thresholds make it unsuitable for live evaluation. Its numbers are schema examples, **not approved production defaults**.

The CLI accepts exactly `config.json [--live]`. Without `--live`, it checks configuration and serialized fixture request sizes without credentials or network calls.

## Explicit live evaluation

Before a paid run, a workload owner must replace the example values with an approved versioned task profile, actual catalog model IDs, provider restrictions, quality/confidence thresholds, latency/token/cost limits, sample sizes, repetitions, and evidence lifetime. The shortlist must contain 1–50 model IDs.

The evaluator needs its own pinned model ID matching the dated ID returned by the Decisions API, rubric version, approved routing tags, response provider labels, timeout/retry limit, and conservative maximum cost per attempt. Total spend and call ceilings must be explicit. Unknown failure billing retains the full reservation.

The CLI reads credentials only from `OPENROUTER_API_KEY`. Do not put keys in JSON, arguments, endpoint URLs, or artifacts. After reviewing those prerequisites, use a separate configuration file and append `--live` to the Java command. **No paid evaluation was performed to validate this document.**

Public metadata inspection does not qualify a live route. The current public catalog can omit request-fee pricing; missing rates remain unknown and are excluded, never assumed free. Live qualification may require a verified missing-price supplement with explicit tariff evidence. No completion or evaluator inference calls were made during this implementation; only public metadata reads were performed.

The live path fetches the complete model listing, resolves only the explicit shortlist's endpoints, and records its limited coverage. Hard capability/provider/freshness/cost checks run before samples. Catalog discovery and shortlisted endpoint requests consume the shared call allowance. Each completion is one bounded call; evaluator retries consume the same allowance and reserve cost before dispatch.

The `advice` suite requests `response_format: json_object`. The `tools` suite requires `tools` and `parallel_tool_calls` in both profile and catalog. It supplies a closed `getQuote` function schema and checks the returned function call and numeric quote ID. Plain JSON describing a tool is not accepted as a tool-capability probe. Proposed tools are never executed.

Serialized requests use the runtime's conservative UTF-8 byte allowance plus protocol overhead. Exact expected JSON checks cover fields, types, arithmetic, and safe arguments. Training and held-out synthetic cases are repeated. These are smoke fixtures, not a production qualification dataset or proof of calibrated confidence.

## Artifacts and replay

Every run uses a new output directory:

| Artifact | Contents |
| --- | --- |
| `catalog/<version>.json` | Normalized provider-specific catalog snapshot |
| `catalog/<version>.evidence.json` | Raw responses, exclusions, shortlist coverage, hash |
| `evaluation.json` | Full profile/catalog/evaluator configuration, fixtures, outputs, evaluator exchanges, costs, latency, objective results |
| `evidence.json` | Candidate evidence for deterministic selection |
| `selection-report.json` | Rejections and proposed policy, if any |
| `proposed-policy.json` | Written only when a supported winner exists; never activates it |
| `outcome.json` | Mode, reason, directory, call count, spend, selection summary |

Incomplete coverage, exhausted budgets, invalid evaluator answers, and failed probes cannot establish a winner. Zero successes are ineligible. Ranking divides total attributable spending by successful tasks, including failed attempts and evaluator overhead. The result is the cheapest qualifying route **within the evaluated set**, not a global optimality claim.

Raw artifacts contain synthetic prompt/output material and belong outside default application logs. Keys and authorization headers are not recorded. `confidenceSquaredError` is a diagnostic, not evidence of live calibration.

For offline reranking, use the same JAR launcher with `-Dloader.main=com.opencrm.ai.selection.SelectionReplayCli`. Exact arguments:

```text
profile.json catalog.json evidence.json output.json evaluated-at policy-version complete|partial
```

Extract the original `profile` and `catalog` objects from `evaluation.json`. Preserve evidence, ISO-8601 timestamp, and policy version for deterministic comparison; pass `partial` for an incomplete run. A later timestamp may correctly reject expired evidence. Replay never activates policies.

Activation, revocation, and rollback are separate explicit operations described in the execution guide. A rollback predecessor must remain eligible under current profile/catalog restrictions.

## Refresh coordination

The [refresh coordinator guide](../backend/services/ai-service/src/main/java/com/opencrm/ai/selection/refresh/README.md) describes the explicit scheduling/refresh API. Its defaults disable scheduling and use shadow proposals; it is not an automatically active Spring scheduler. Opt-in canary activation adds current eligibility, spending, minimum-benefit, and minimum-switch-interval checks. The supplied evaluator must retain a shared lifetime call/spend budget across refreshes. Do not obtain an unbounded budget by creating a fresh paid CLI process on every timer tick.

## Acceptance evidence

Run tests from `backend`:

```powershell
.\mvnw.cmd -pl services/ai-service -am test
```

These are deterministic unit/local-HTTP tests, not live provider certification.

| Scenario | Real test methods | Scope or remaining validation |
| --- | --- | --- |
| AC-01: cheaper unsuitable candidate loses | [SelectionEngineTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/SelectionEngineTest.java): `cheaperUnqualifiedCandidateCannotWin`; [SelectionEvaluationCliTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/evaluation/SelectionEvaluationCliTest.java): `genuineToolProbeRequiresReturnedFunctionCalls` | Synthetic quality/tool evidence |
| AC-02: full cost per success | [SelectionEngineTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/SelectionEngineTest.java): `measuredTotalIncludesFailuresAndOverheadAndReplaysDeterministically`; [CostCalculatorTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/catalog/CostCalculatorTest.java): `countsCacheReadWriteReasoningRequestRetriesAndEvaluatorOverheadExactly` | Real billing reconciliation outstanding |
| AC-03: invalid/stale prices and explicit zero | [SelectionEngineTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/SelectionEngineTest.java): `missingPricesAndStaleCatalogRejectWhileExplicitZeroIsValid`; [OpenRouterCatalogClientTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/catalog/OpenRouterCatalogClientTest.java): `missingNegativeExtraPricesAndUnknownCapabilitiesAreExcluded` | Mock catalogs |
| AC-04: invalid/uncertain evaluator | [EvaluationHarnessTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/evaluation/EvaluationHarnessTest.java): `rejectsUnknownCandidateMissingEvidenceMalformedAndLowConfidence`, `timeoutIsBoundedAndRetainsConservativeCharge`, `retryStopsAtSharedCallCeilingAndRetainsFailedCostAndRawResponse` | Live evaluator reliability/calibration outstanding |
| AC-05: catalog changes invalidate eligibility | [PolicyExecutionTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/execution/PolicyExecutionTest.java): `expiredPolicyAndRefreshedCatalogFailBeforeNetwork`, `policyAndCatalogExpiryAndRestrictionsAreRechecked` | Live retirement/price monitoring not exercised |
| AC-06: provider and capability restrictions | [SelectionEvaluationCliTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/evaluation/SelectionEvaluationCliTest.java): `hardProviderFilterStopsGenerationBeforePaidCalls`; [PolicyExecutionTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/execution/PolicyExecutionTest.java): `providerMismatchAndMalformedSuccessNeverTriggerFallback` | Local HTTP fixtures |
| AC-07: deterministic ties | [SelectionEngineTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/SelectionEngineTest.java): `tiesUseQualityLatencyThenStableModelId` | Directly asserts stable-ID tie; comparator also orders quality and latency |
| AC-08: failed attempts plus evaluator overhead | [EvaluationHarnessTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/evaluation/EvaluationHarnessTest.java): `failedGenerationRemainsBillableAndCountsAsObjectiveFailure`, `runsObjectiveFixturesIncludesEvaluatorOverheadAndReplays` | Unknown costs conservatively reserved |
| AC-09: replay | [EvaluationHarnessTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/evaluation/EvaluationHarnessTest.java): `runsObjectiveFixturesIncludesEvaluatorOverheadAndReplays`; [SelectionEngineTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/SelectionEngineTest.java): `measuredTotalIncludesFailuresAndOverheadAndReplaysDeterministically` | Fresh evaluator calls can vary |
| AC-10: rollback without replaying actions | [PolicyExecutionTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/execution/PolicyExecutionTest.java): `costViolationRevokesCurrentPolicyAndRestoresEligiblePredecessor`, `canaryQualityRegressionRollsBackWithoutReplayingAnyRequest`, `toolResultRequestsAreNeverReplayedOnFailure` | Mock rollback proof; no production canary |
| AC-11: outage with expired eligibility | [PolicyExecutionTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/execution/PolicyExecutionTest.java): `expiredPolicyAndRefreshedCatalogFailBeforeNetwork`, `expiredFallbackAfterFirstAttemptIsNotUsed`; harness timeout tests | Operational outage drill outstanding |
| AC-12: bounded evaluation | [SelectionEvaluationCliTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/evaluation/SelectionEvaluationCliTest.java): `partialBudgetRunWritesNoProposedWinner`; [EvaluationHarnessTest](../backend/services/ai-service/src/test/java/com/opencrm/ai/selection/evaluation/EvaluationHarnessTest.java): `refusesNetworkWhenSpendCannotCoverAttempt` | Bounds must be conservative; overbilling stops further calls |

Rollout remains disabled until workload owners approve thresholds, representative held-out fixtures, calibration, provider/data permissions, spending, shadow/canary observations, cost reconciliation, and fallback ownership. This matrix does not close those live requirements.

## API sources

The implementation contract was checked against the official [Decisions API](https://openrouter.ai/docs/api/api-reference/alphadecisions/submit-a-decisions-questions-and-answers-request), [models API](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties), and [provider routing](https://openrouter.ai/docs/guides/routing/provider-selection). Decisions is an alpha API; contract changes fail validation rather than silently changing model selection.
