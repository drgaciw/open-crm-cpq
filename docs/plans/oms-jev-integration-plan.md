# Jev integration into order management

Date: 2026-09-21. Status: proposed implementation plan; no runtime enablement.

## Outcome and first delivery

Introduce advisory exception triage into the OMS operator workflow. Begin with a suggested investigation step; measure whether it improves diagnosis before expanding to urgency or escalation advice. OMS rules and authenticated operators retain all business authority.

First deliverable: a flag-off, offline-tested `oms-exception-triage` use case with versioned input features, typed answers, shadow logging and a reproducible evaluation report. A subsequent delivery exposes qualified suggestions in the existing exception workspace. This work must not delay the baseline OMS release-readiness gates.

## Inspected baselines and prerequisites

- OMS: `feat/oms-integration`, `6858be45dc0bf083225379c9b290f732283eb9bc`, inspected in the separate `oms-integration` worktree. It contains orders, execution, fulfillment, finance, recovery and Angular orders UI, but not the shared Jev library or ADR-015.
- Jev: current checkout `feat/e2e-jev-browser-agent`, committed baseline `38cc6b0`, contains `backend/libs/jev-decision` and [ADR-015](../adr/ADR-015-jev-advisory-decision-support.md). Decision-store enhancements, quote suggestions and `jev-worker/` have concurrent uncommitted work; they are not stable dependencies for this plan.
- Select an integration branch containing reviewed versions of both baselines. Resolve reactor, common-library, migration, security and CI overlap before adding OMS hooks. Do not copy or overwrite concurrent work.
- Amend ADR-015's authorized-use-case table for each OMS use case and record the OMS wave extension. ADR-015 explicitly requires this amendment. This plan does not itself amend the accepted decision or authorize live provider calls.
- Preserve the OMS pilot scope and all unrelated holds: subscription frontend, Activities, NgRx, approval dual-read changes, agent Kafka expansion and ADR-014 runtime model selection.

## Workflow and authority

```text
Existing OMS command or reconciliation transaction commits
    -> capture a bounded, versioned exception feature snapshot
    -> optional after-commit Jev evaluation
    -> validate typed answer and record shadow result
    -> expose only a current, qualified advisory
    -> operator investigates and chooses an existing OMS command
    -> OMS rechecks permission, version, evidence and business rules
    -> record operator choice and eventual outcome for evaluation
```

No Jev call may run inside an order transaction, block a Kafka acknowledgment, or control retries. Disabled, saturated, timed-out, invalid or over-budget evaluation means no suggestion. Business processing and deterministic exception visibility continue unchanged.

Jev cannot release holds, waive evidence, assign owners, retry an UNKNOWN operation, adjudicate contradictions, change quantities or prices, create finance acknowledgments, complete orders, cancel fulfillment, or issue refunds. A model probability is never authoritative evidence of an external effect. Even in advisory mode, do not automatically reorder the queue or change severity, ownership, deadlines or notifications.

## Proposed use cases

| Sequence | Use case and typed question | Trigger | Authoritative outcome |
|---|---|---|---|
| 1 | `oms-exception-triage`: Choice of `CHECK_SOURCE`, `CHECK_FULFILLMENT_EVIDENCE`, `CHECK_ACTIVATION_ACK`, `CHECK_FINANCE_ACK`, `CHECK_DELIVERY_STATUS`, `ESCALATE_INTEGRATION`, `INSUFFICIENT_INFORMATION` | New exception or material change in its facts | Operator's selected investigation step, recorded separately from the eventual resolution command |
| 2 | `oms-exception-urgency`: Score on a fixed routine / soon / immediate-review rubric | Material exception change or bounded age-band crossing | Independently reviewed urgency and observed handling outcomes; existing severity stays authoritative |
| 3 | `oms-manual-escalation`: Noul for whether specialist review is warranted | Unresolved contradictions or repeated failures with adequate structured facts | Independent specialist assessment; no automatic escalation or assignment |

Deliver sequence 1 first. Add 2 or 3 only after an ADR amendment and evidence of incremental value. Avoid using Jev merely to restate an exception-category lookup; compare against that simple deterministic baseline.

## Implementation design

### Domain adapter and execution

Create a small `com.opencrm.order.advisory` package using the existing `JevClient`, `JevFeatureSchema`, `JevAdvisoryRunner` and transport abstraction directly. Do not route through `ai-service`, call agent-governance MCP tools from production, or import frontend E2E code.

Proposed classes: `OrderAdvisoryFeatures`, `OrderAdvisoryQuestions`, `OrderAdvisoryService`, `OrderAdvisoryRepository` and `OrderAdvisoryController`. Extend the shared library's explicit use-case configuration only for approved OMS cases. Keep domain rubrics and feature extraction in OMS.

Locate exception creation/change points in recovery and daily reconciliation. Register evaluation against the actual transaction boundary: `submitAfterCommit` executes immediately if called without transaction synchronization. Capture safe immutable values, not live ORM objects, request security context or unbounded JSON. Limit submissions per batch; never evaluate every row on every reconciliation pass.

Reuse the runner's bounded concurrency and tolerate dropped work. Advisory delivery is best-effort. Do not add Kafka topics or use business outbox recovery for missed suggestions. If coverage is inadequate, propose a bounded, independently authorized advisory backfill; do not silently create a durable execution dependency.

### Features, privacy and freshness

Allow only validated categorical codes, booleans and bounded numbers: exception category, current deterministic severity, age band, line type, attempt band, deadline proximity, dependency state, UNKNOWN status, evidence pending/conflict flags, acknowledgment state and quantity/amount discrepancy bands. Missing facts stay explicitly unknown.

Do not send names, emails, addresses, account/scope identifiers, order IDs, evidence references, free-text reasons, raw quote snapshots, credentials or provider error bodies. IDs needed for joins remain local. Validate CODE values against finite domain enums; a generic string-type check is insufficient. Use deterministic UI templates for explanations instead of requesting prose.

Bind every evaluation locally to scope/account, order ID and version, exception ID and version, feature hash, feature-schema version, rubric version, evaluator version and timestamp. Define an expiry, initially proposed at 15 minutes, and invalidate immediately on relevant state change or exception closure. The TTL is configurable and must be validated in the pilot.

Deduplicate per scoped subject/version and evaluator configuration, including across replicas. An answer arriving late may be retained for audit but must not replace a newer suggestion. Never use a feature-hash-only lookup to expose another subject's advisory or attach a later operator outcome to every historical evaluation.

### Persistence and API

Allocate the next unused OMS Flyway migration at implementation time; V1–V8 exist in the inspected baseline. Add the canonical service-local `jev_decision_log` plus an OMS-specific binding/feedback structure for decision identity, scoped subject versions and expiry. Preserve the shared DDL contract and use reviewed extensions rather than changing every consumer implicitly.

The existing shared log does not contain all OMS scope/version fields. Define an atomic local persistence contract linking the decision and binding; expose no suggestion if persistence fails. Store operator feedback by exact decision and evaluated state. Keep unobserved outcomes null; operator disagreement alone is not proof the model was wrong.

Proposed endpoint: `GET /api/v1/order-exceptions/{id}/advisory`, returning an `ApiResponse` DTO with availability status, suggested investigation code, model confidence, evaluation time, expiry and evaluated versions. Enforce existing `oms:read` and account/scope authorization through the exception's order. GET never initiates a paid evaluation. Return bounded statuses such as unavailable, pending, abstained or stale without exposing provider diagnostics.

If feedback requires a new endpoint, restrict it to authorized operators, validate its finite codes and exact decision binding, and audit it separately. It must not invoke recovery commands. Verify gateway routing and CSRF behavior, service authorization and database runtime grants.

### Operator experience

Use Angular signals and HttpClient in `frontend/libs/cpq/orders`. Show a compact panel beside the current exception with the exact ADR-015 label, "Advisory — not a decision", the investigation suggestion, model confidence and evaluated time. Do not describe confidence as a measured success probability without calibration evidence.

Retain existing controls, queue ordering and permission checks. No preselected retry, waiver or resolution action. Stale/absent advice must not obstruct work. Operator navigation and investigation choice can be recorded for evaluation, but business commands continue to require their normal reason, evidence, operation key and current expected version. Distinguish exception-version checks from order-version checks.

## Delivery sequence and exit criteria

| Milestone | Owner role | Deliverable | Exit criterion |
|---|---|---|---|
| J0: scope and integration | Architecture + OMS owner | ADR-015 amendment, OMS wave extension, combined baseline, rubric and feature contract | Reviewed scope; no production switches enabled |
| J1: offline triage | OMS backend + QA | Adapter, fake/recorded transport fixtures and comparison report | Flag-off invariance; no network; evaluation beats or meaningfully complements category-based advice |
| J2: shadow plumbing | Backend + platform | Scoped persistence, version binding, deduplication, bounded hooks and metrics | Failures cannot alter OMS outcomes, transaction success or consumer offsets; scope and stale-result tests pass |
| J3: advisory UI | Frontend + QA | Read API, optional feedback and exception panel | Browser tests cover permissions, staleness, abstention and uninterrupted recovery; keyboard/screen-reader review |
| J4: approved canary | Security/privacy + SRE + operations | Selected upstream, budget, shadow evidence, rollback drill and trained cohort | All ADR-015 provider gates and independent OMS launch gates satisfied |

Dependency order: J0 -> J1 -> J2 -> J3 -> J4. Offline UI fixtures can be designed during J1, but live use cannot bypass the gates. Default both global and OMS-use-case flags to false; keep UI visibility separate from shadow evaluation.

## Evaluation and tests

Build versioned synthetic cases covering source holds, expired allocations, delayed delivery facts, activation failure, partial finance acknowledgments, contradictory evidence, unknown effects, exhausted outbox rows and already-resolved exceptions. Include sparse facts and cases where abstention is correct.

Use a held-out expert-labeled set and report per-category precision/recall, confusion matrix, abstention and coverage, calibration, sample counts, latency and cost. Group splits by scenario/order lineage to avoid near-duplicate leakage. Compare against existing category/severity rules. Separate shadow expert labels from outcomes collected after operators see suggestions to measure automation bias.

Proposed advisory-entry thresholds, subject to owner review: at least 200 held-out cases with coverage of every pilot category; at least 90% precision among displayed investigation suggestions; zero forbidden action codes; zero safety-invariant regressions. Select confidence thresholds on development data, then lock them before held-out evaluation. Report uncertainty; these thresholds are proposed acceptance criteria, not achieved results.

Behavioral tests must prove:

- Rollback schedules no evaluation; flag off makes no calls or advisory behavior changes.
- Timeout, 429, malformed distributions, saturation, logging failure and budget exhaustion cannot fail commands or trigger Kafka redelivery.
- Model failure cannot approve UNKNOWN retries, accept evidence or complete finance/fulfillment.
- Concurrent refresh, changed order/exception versions, closure and late answers never expose stale advice.
- Cross-account lookup, equal feature hashes and feedback replay cannot leak or misattribute decisions.
- Runtime database grants allow advisory writes without weakening immutable business records.
- Real HTTP/gateway and browser workflows preserve CSRF, authorization, expectedVersion and operation-key behavior.

Explicitly run OMS, shared Jev, affected quote/contract/common/gateway tests and cpq-orders Vitest/browser checks. Docker-required integration tests must execute rather than skip. The current checkout's CI allowlists differ from OMS integration; confirm the combined branch includes every affected project. Normal CI uses no paid calls.

## Operating limits and rollout

Reuse no-op transport by default. For approved live evaluation, prefer the ADR-015 authenticated facade topology after reviewing its implementation; the current uncommitted worker is not a production dependency. Pin and verify the actual evaluator version returned by the facade, not only the configured alias. Do not select Jev recursively via ADR-014.

The existing daily call budget is in-memory and per instance. Before live rollout, enforce an aggregate deployment/use-case cap at an approved facade or shared limiter, including retries and replica restarts. Set explicit concurrency, timeout, request-size, retry and daily-spend limits from verified provider terms. Observe call attempts, drops, failures, abstentions, stale results, coverage, latency and budget use without IDs in metric labels.

Progress through synthetic offline -> approved non-production shadow -> approved limited advisory canary. Document provider pricing, rate limits, retention/data processing and credentials before outbound customer-derived features. Cloudflare currently links Jev pricing to its dashboard; no numerical price is assumed here. Keep raw evidence out of provider requests regardless of the upstream's examples.

Rollback switches off evaluations and UI advice while normal OMS continues. Recheck switches before dispatch and before publishing results; suppress in-flight results after disable. Preserve local audit history under the approved retention policy. Exercise provider outage, key revocation, budget exhaustion and kill-switch drills.

Jev readiness does not replace OMS final-SHA CI, deployed quote-to-contract acceptance, identity/ACL verification, cutover fencing, representative load, recovery/RPO/RTO, accessibility or named operational ownership. Ship those release-readiness items independently. Autonomous actions, promises of delivery dates, returns/refunds and P1/P2 workflow expansion are outside this plan.

## Evidence and implementation review

Inspected shared files: `JevAdvisoryRunner`, `JevProperties`, `DailyCallBudget`, `JevDecisionRecord`, `JevFeatureSchema` and canonical decision-log DDL. The inspected working-tree decision-store extensions are not assumed committed.

OMS references at the pinned branch: `docs/oms/pilot-evidence.md`, `docs/oms/requirement-test-matrix.md`, `docs/runbooks/oms-pilot.md`, recovery README/DTOs/service and `orders-exceptions.component.ts`.

Planning GitNexus impact for `RecoveryService`: LOW, one direct `RecoveryController` constructor dependency, zero indexed affected processes. This class-level result is not a change-wide all-clear. Before implementation, run upstream impact for each touched method and shared-library symbol; warn on HIGH/CRITICAL, resolve UNKNOWN with source checks, and complete branch graph-change analysis before commits. No runtime tests were run for this documentation task.

External schema reference, checked 2026-09-21: [Cloudflare Jev documentation](https://developers.cloudflare.com/ai/models/typesafe/jev/) describes typed Choice, Noul and Score answers and their probability fields. Domain accuracy and calibration must be established by the OMS evaluation; provider descriptions are not proof of those outcomes.
