# Requirements: Jev-driven OpenRouter LLM selection

**ID:** REQ-AI-014
**Status:** Backlog — implementation not started
**Created:** 2026-09-19
**Architecture:** [ADR-014](../adr/ADR-014-jev-openrouter-model-selection.md)
**Superseded direction:** fixed GLM-5 implementation tasks in ADR-002

## Outcome

For each supported application AI task, use Jev to determine which OpenRouter models meet the task's requirements and select the qualifying model with the lowest expected total cost per successful task. Make the selection explainable, reproducible, budget-limited, and reversible.

Current baseline: AI-service YAML uses Azure OpenAI `gpt-4o`; the existing Jev UAT client is reference material. No production catalog ingestion, model-selection policy, or OpenRouter execution adapter is assumed to exist.

## Functional requirements

| ID | Requirement |
|---|---|
| FR-01 | Fetch the OpenRouter catalog from `GET https://openrouter.ai/api/v1/models`; handle the current API's filtering/pagination behavior without silently truncating candidates. Resolve endpoint-specific metadata/prices for shortlisted models. |
| FR-02 | Store raw and normalized snapshots with retrieval time, source, hash, model ID, endpoint/provider identity, available capabilities, context limits, and prices/units. Missing prices are unknown, never implicitly zero; explicit zero prices remain subject to availability and capacity checks. |
| FR-03 | Define versioned task profiles: required modalities, tool/structured-output support, context/output limits, quality rubric and threshold, p95 latency limit, budget, provider restrictions, and fallback rules. Start with representative structured CPQ advice and tool-using workflows identified from the code. |
| FR-04 | Filter candidates deterministically for hard requirements before Jev selection. Verify required behavior with probes; advertised capabilities alone do not establish compatibility. Treat candidate metadata/output as untrusted evaluation data. |
| FR-05 | Run versioned fixtures with objective assertions for schemas, tool arguments, business constraints, and expected outcomes. Use Jev for rubric-based suitability judgments; blind model identity where practical when judging output quality. Maintain held-out cases and calibrate Jev confidence against observed results. |
| FR-06 | Give Jev explicit candidate IDs and evidence. Require typed decisions with validated distributions, configured minimum confidence, and evidence references. Reject invalid/unknown selections; abstain on insufficient evidence. Pin and record evaluator version, prompt/rubric version, and transport. |
| FR-07 | Compute costs in deterministic decimal arithmetic from normalized prices and measured/estimated usage. Include input/output, applicable cache/request/modality charges, billable reasoning without double-counting output, retries, fallbacks, and amortized Jev/evaluation costs. Record assumptions and reconcile against reported billed usage. |
| FR-08 | Among candidates that meet hard checks and Jev suitability gates, choose the minimum expected cost per successful task. Tie-break by measured quality, p95 latency, then stable model ID. Record rejected candidates and reasons. Do not claim global optimality outside the evaluated set. |
| FR-09 | Publish a versioned policy per task profile containing chosen model/provider constraints, ordered eligible fallbacks, cost estimate, confidence, benchmark/catalog versions, effective/expiry times, and rollback version. Runtime must validate eligibility again before dispatch. |
| FR-10 | Execute through a backend OpenRouter adapter with bounded timeouts/retries, compatible tool/stream/structured-output handling, and explicit provider restrictions. Prevent implicit provider fallback from bypassing policy requirements or budget ceilings. |
| FR-11 | Refresh on a configurable schedule and on price, capability, retirement, profile, or observed-quality changes. Cache policies; add minimum-benefit/hysteresis controls to avoid frequent switches. Expired or revoked eligibility must not be reused as a fallback. |
| FR-12 | On Jev/catalog failure, retain a still-valid policy. On execution failure, use only eligible budget-compliant fallbacks. If none remain, return a typed unavailable/escalation outcome. A separately configured Azure rollback may be used only under its explicit eligibility policy. |
| FR-13 | Emit selection/execution audit records: task/profile ID, policy version, model/provider, evaluator version, reason codes, latency, usage, estimated/actual cost, fallback/retry count, and outcome. Exclude credentials and raw customer payloads from default logs. |
| FR-14 | Provide a repeatable evaluation command/report with dry-run mode, candidate coverage, rejected candidates, benchmark results, spend, confidence, and proposed policy diff. Dry-run must not activate policies. |

## Selection and cost contract

Define an eligible set E from capability, provider, budget, measured-quality, and Jev-confidence gates. Choose `argmin(expected_total_cost_per_success(m))` for `m in E`. When E is empty, abstain.

For a measured evaluation batch, report `total attributable spend / successful tasks`; zero successful tasks means ineligible, not zero cost. Count failed attempts and evaluator spend in the numerator. For forecasts, version the token mix, retry/fallback assumptions, success estimates, and evaluation amortization window. Compare equivalent tasks under equivalent provider constraints. Use provider-specific pricing for the planned route; catalog headline prices are discovery inputs, not guaranteed execution costs.

Benchmark candidate count, spend ceiling, repetition count, minimum sample size, acceptable uncertainty, and quality thresholds are mandatory configuration. Development may use fixture defaults; rollout requires explicit workload-owner values. No arbitrary dollar budget or confidence threshold is assumed by this document.

## Acceptance scenarios

| ID | Given / expected result |
|---|---|
| AC-01 | A cheaper candidate fails tool or quality requirements: Jev/policy rejects it and selects the lowest-cost qualifying candidate. |
| AC-02 | Two candidates qualify with different output lengths, retries, and evaluator costs: selection follows total cost per success, not input-token price alone. |
| AC-03 | Required price metadata is missing, malformed, negative, or stale: exclude or refresh the candidate; do not fabricate a free price. Explicit valid zero-price candidates are handled correctly. |
| AC-04 | Jev returns an unknown ID, malformed distribution, low confidence, timeout, or rate limit: no invalid policy is activated; valid prior policy or explicit abstention is used. |
| AC-05 | A catalog change removes a model or raises its cost above the ceiling: invalidate/re-evaluate its eligibility and use a qualifying fallback or abstain. |
| AC-06 | A provider lacks required parameters or violates data-handling restrictions: neither primary routing nor implicit fallback can send it the request. |
| AC-07 | Equal-cost candidates exist: quality, latency, and stable-ID tie-breaks produce a reproducible winner from the saved evidence. |
| AC-08 | A fixture batch contains failures and retries: decimal cost totals include all attempts and Jev overhead; zero successes cannot win. |
| AC-09 | Recorded Jev responses, catalog, profile, and measurements are replayed: policy validation/ranking yields the same result. Fresh evaluator calls may vary and are stored separately. |
| AC-10 | Canary quality or spend violates configured thresholds: rollback restores the previous eligible policy; in-flight tool actions are not blindly replayed. |
| AC-11 | Catalog/evaluator services are unavailable and the cache has expired: produce an explicit unavailable outcome rather than an unrestricted model switch. |
| AC-12 | Evaluation reaches its spend/call ceiling: stop cleanly, report partial candidate coverage, and do not publish an unsupported winner. |

## Implementation packages and parallel ownership

1. **Contracts/baseline:** establish task profiles, normalized catalog/cost schema, evaluation records, policy interface, and current Boot/Java dependency compatibility. Run fresh GitNexus impact analysis before code edits.
2. **Parallel A — catalog/cost:** discovery, endpoint normalization, freshness, deterministic cost engine and fixtures.
3. **Parallel B — Jev/evaluation:** backend decision adapter, benchmark harness, rubric/calibration, strict validation, evidence reports. Reference existing UAT client behavior without importing frontend test code into production.
4. **Parallel C — policy/execution:** policy store/cache, constrained OpenRouter execution adapter, fallback, audit, shadow/canary/rollback controls; use contract fixtures until A/B integrate.
5. **Integration:** objective acceptance suite, mocked provider failures, budget tests, bounded synthetic live validation, shadow/canary evidence, and documentation closeout.

Use one orchestrator and up to three worker worktrees from a common committed baseline. Assign separate files/packages and one owner for shared POM/config/schema changes. Require complete GitNexus change analysis against each edited worktree before commits and against the integration base before merging.

## Rollout and completion

- Keep selection/execution disabled until adapters, acceptance scenarios, and compatibility checks pass. No requirement to upgrade to the historical ADR-002 Spring AI version.
- Run deterministic fixtures without paid calls in normal CI. Live evaluation uses synthetic/redacted inputs, server-side secrets, and an explicitly configured spend cap.
- Establish quality/latency/cost baselines, confidence calibration, provider/data permissions for both evaluator and execution models, and fallback ownership before activation.
- Shadow recommendations first; canary approved workload profiles with automated regression thresholds, then expand after evidence passes. Refresh schedules here are implementation requirements, not an automation created by this task.
- Close the backlog item only with passing AC-01 through AC-12, replayable selection evidence, observed cost reconciliation, rollback proof, and updated runtime documentation. Mark unavailable infrastructure/live-provider validation as outstanding rather than passed.

## Out of scope

Changing Jenkins AI-assist routing (ADR-003), replacing browser UAT decisions, hard-coding GLM-5 or any other winner, enabling agent Kafka, approvals cutover, unrelated schema changes, and migrating customer data. This requirements change does not execute paid model comparisons or change the running AI provider.

## References

- [ADR-014 and API source references](../adr/ADR-014-jev-openrouter-model-selection.md)
- Existing integration patterns: `frontend/e2e/lib/decide.mjs`, `frontend/e2e/lib/decide.test.mjs`.
