# Margin wave, 2026-10-15

Base: `main` at `dd90501`, plus the shared cost basis port committed on `wave/margin-20261015`. Every lane branches from that commit and works in its own git worktree.

## Why this wave

Real per-period margin is the last open prerequisite of ADR-023 (P-2, AC-16). The owner's and Finance's decisions of 2026-10-15 settle its inputs: the cost model F-1, the export contract F-2, and mock cost basis v1 (F-3, approved by the project owner). Two engineering debts recorded in the ramp waves are paired with it, because they are independent and unblock CI trust:

- the frontend CI allowlist skips most CPQ projects (P-5);
- the gateway's other routes still carry the malformed CircuitBreaker shortcut that PR 581 fixed only for quote and pricing.

## Lanes

| Lane | Story | Owns (exclusive) | Must not touch |
|------|-------|------------------|----------------|
| **L1 cost basis** | DRG-526 BC-03-13 | `pricing-service/.../pricing/cost/**` except the four port files; new Flyway `V9__cost_basis.sql`; the ingestion API; `finance/cost-model/**` (generator plus the v1 CSV as fixture) | `execution/**`, `ramp/**`, rules, `application.yml` |
| **L2 margin** | DRG-438 BC-03-08, margin slice (guidance bands split out) | `MetricsCalculator`, `PeriodGovernance`, margin wiring in `PricingExecutionService`, `approval-margin-exception.drl`, `FinancialMetrics`, pricing `application.yml`, quote `PricingFinancialMetricsView` (tolerance only) | `cost/**` implementation, migrations |
| **L3 frontend CI gate** | new, P-5 | `scripts/ci/frontend.sh`, GHA workflow allowlists, `Jenkinsfile` frontend stage, failing or non-typechecking CPQ and shared-ui specs | product behaviour in any frontend lib |
| **L4 gateway breakers** | new, follow-up to PR 581 | `gateway-service` route definitions, fallback handlers, timeout properties and their tests | quote and pricing routes (already fixed) |
| **Root** | — | the port (`CostBasisLookup`, `UnitCost`, `CostComponent`, `CostBasisConfiguration`), this plan, integration, Linear and Notion, PRs | lane-owned files |

**Shared contract.** `CostBasisLookup.find(productExternalId, asOf)` returns the cost effective on `asOf`, or empty when there is no row or the newest row is stale (older than two quarters). Empty means fail closed. L1 provides the JPA-backed implementation as a `@Component`. Until then the `@ConditionalOnMissingBean` fallback returns empty. L2 consumes only the interface and tests against fakes. Signature changes go through root.

## Decisions recorded before coding

- **Default behaviour does not change.** L2 adds `opencrm.pricing.margin.source=placeholder|cost-basis`, defaulting to `placeholder`. Under `placeholder`, governance and responses are byte-for-byte what they are today: the 40.0 value remains non-authoritative, as ADR-023 D-06 requires.
- **Cost-basis mode.**
  - Margin is `(net − unit cost) / net`, computed per line and per ramp period, using the cost effective at each period's start.
  - A missing, stale or other-currency cost gives no margin, never 40.0, and the quote routes to `DEAL_DESK` with a reason that names the missing cost.
  - AC-16: a period under the 35% floor routes to `DEAL_DESK`, and the worst period governs.
  - The percent-versus-fraction mismatch between the 40.0 placeholder and the 0.35 rule is resolved explicitly and tested.
- **Ingestion.** It follows the F-2 export contract: per-row rejection (unknown product, negative cost, unit mismatch), idempotent by `version`, effective-dated, stale after two quarters. It is an authenticated `/api/v1` admin endpoint under the ADR-016 baseline. The role is `Admin`, unless the existing role table names a closer one.
- **Mock v1 is test and fixture data only.** Nothing seeds it into a running environment.
- **Held, not in this wave:**
  - guidance bands (rest of DRG-438);
  - a hardware or resale margin floor (a Deal Desk policy, undecided);
  - ADR-017 adoption;
  - PR 598 (DRG-509, reviewed separately);
  - any flag change in a committed environment profile;
  - every hold in AGENT.md.

## Gates for every lane

1. **Before editing:** GitNexus impact on each symbol the lane will change, run in the lane's worktree. Report HIGH or CRITICAL. Treat UNKNOWN as unresolved and confirm it by text search.
2. **Red first:** a failing test before each behaviour change.
3. **Before committing:**
   - the lane's module tests pass, under the `unit-tests` profile for backend;
   - frontend lanes run lint, test and typecheck for every project they touch;
   - `detect-changes --scope all` is complete, not partial or truncated.
4. **Serialise heavy stages.** Maven builds, `npm ci` and GitNexus analyze run through `flock /tmp/claude-0/wave-heavy.lock`. The container has 2 CPUs and 7 GB.
5. **Commits:**
   - Conventional Commits on the lane branch, with no pushes; root integrates.
   - Each lane reports its test counts, its graph results, and anything left unverified.
6. **Independent review.** After integration, a fresh agent reviews the combined diff against this plan and the ADRs before any PR is opened.

## Definition of done rubric

The owner's instruction of 2026-10-15: **no PR is opened until every row below is satisfied for every lane**, and Jev then scores the integrated implementation until it is complete. Each row is either met with evidence, or marked N/A with a reason that the independent reviewer accepts.

### D1 — Backlog Definition of Done

This is the Notion capability map's convention for every story.

| Item | L1 cost basis | L2 margin | L3 CI | L4 gateway |
|------|---------------|-----------|-------|------------|
| Exposed via Open Host Service: a versioned `/api/v1` DTO API under the ADR-016 baseline | Import and lookup API | Margin in the existing pricing response (additive field) | N/A: build tooling | N/A: edge-only; no domain API (ADR-009) |
| Events in the schema registry (`EventContract`) | N/A unless the lane adds an event. Any event it adds must be registered and fitness-tested | N/A: no new event | N/A | N/A |
| Written through the outbox | Same rule as above | N/A | N/A | N/A |
| Audit entry where a commercial decision is touched | **Required.** Every import records version, approver, actor, accepted and rejected counts | **Required.** The margin used, its source and any missing-cost reason are in the pricing audit trail and waterfall trace | N/A | N/A |
| Event-replay integration test | Replay or re-ingest of the same version is a no-op (PostgreSQL integration) | Replaying a priced quote returns the original cost and margin | N/A | N/A |

### D2 — Wave gates

These are the gates listed above, for every lane:

- GitNexus impact before edits, with HIGH, CRITICAL and UNKNOWN reported;
- red-first tests;
- module tests green under the unit profile;
- a complete `detect-changes`, not partial or truncated;
- no forge hold touched;
- defaults unchanged.

Integration tests that need Docker are run where Docker exists, either the owner's machine or the CI integration job. **Until they have run, the lane is not done.**

### D3 — Jev scoring

This follows the repository rubric, [jev-requirement-implementation-rubric.md](jev-requirement-implementation-rubric.md), R1–R4.

- **R1.** Each lane's acceptance criteria are passed to `jev_check_requirement`, using the GitNexus evidence packet. Every criterion must be `covered`, or explicitly deferred in this plan. `partial` means revise the smallest missing piece. `not_verifiable` means fill the evidence, never mark it covered.
- **R2.** `jev_review` on the integrated diff must return `safe_to_apply` true, with no blocking test gap. A GitNexus result of HIGH, CRITICAL or UNKNOWN still means human review before merge.
- **R3.** `jev_assess_change_risk` is mandatory for L1 (Flyway and a new API), L2 (pricing execute) and L4 (gateway routes). `escalate` stops the slice, and nobody edits code to chase a better score.
- **R4.** The local veto on holds always wins over any score.

The loop is: score, revise, re-score, until R1 and R2 pass. The PR opens only after that, as a draft for owner review. Jev's judgments are recorded in this plan's results section.

## Environment note

This cloud workspace cannot reach Maven Central, and the owner's computer was offline at planning time. Backend lanes L1, L2 and L4 start once Maven resolution works: either the host is allow-listed, or the owner's computer is back online. L3 needs only npm and starts immediately.

## Results

### Lanes (2026-10-15)

- **L3 frontend CI gate, DRG-527: complete and run.**
  - All 20 Nx projects are gated on lint, typecheck and test, with 701 tests passing. The gate fails when a spec, a type or a target is broken.
  - `frontend-gate-check.sh` fails CI when any project lacks one of the three targets.
  - The `eqeqeq allowNullOrUndefined` relaxation is a separate commit (`9dce2d7`), and the owner may revert it.
  - Not run end to end here: `crm:build:production` needs Google Fonts.
- **L1 cost basis, L2 margin, L4 gateway: written without Maven, so not compiled or run.** Each is checkpointed as an UNVERIFIED WIP commit on its own pushed branch, and all four lanes are merged on `wave/margin-20261015-integration`.

### Independent review of the integration branch

The review found 1 blocker, 6 major and 9 minor findings. It recomputed every figure in the tests and found them correct. No forge hold is touched (R4).

| Finding | Resolution |
|---------|------------|
| **B1** Staleness was judged against future ramp period starts, so every period six months or more after the pricing date failed closed | The port takes `effectiveOn`, which selects the cost row, and `pricedOn`, which judges freshness (root, `9b1ad09`). FA and FB implement it, with an AC-02 test across the real staleness semantics |
| **M1** Implicit replay pinned missing and stale costs | Replay is reused only when the input is identical, every cost is present, and every cost is still fresh on the pricing date (FB) |
| **M2** Period margin ignored the hybrid migration credit | Period net comes from `periodAmount` (FB) |
| **M3** No lookup API | `GET /api/v1/cost-basis/products/{id}` (FA) |
| **M4** Cost basis API unreachable through the gateway | `/api/v1/cost-basis/**` is added to the pricing route (root, `9b1ad09`) |
| **M5** Billing-unit check is practically inert | **Deferred, recorded as partial F-2 coverage.** The pricing catalog replica has no unit column; follow-up DRG-530 |
| **M6** No application-context test | Context test for bean selection and properties binding (FB) |
| m1 Zero-net lines always route | **Decision (owner's delegate):** net exactly 0 is margin-not-applicable, excluded and not routed. Negative net routes (FB) |
| m2, m3, m7, m8 | Fixed (FA, FB) |
| m4 Omitted component persists | Documented; a zero-cost row retires a component (FA) |
| m5 `/fallback/cpq` is GET-only | Accepted: no route uses it |
| m6 Retries of non-idempotent POSTs after a gateway timeout | Accepted for this wave. Idempotency keys are ADR-018 E3 work and stay out of scope |
| m9 `@ConditionalOnMissingBean` in a plain `@Configuration` | Covered by the M6 context test |

### Open points, decided

- **(a) L4 per-route deadlines against held ADR-017: accepted.** They are provisional values under ADR-017 D-03, following PR 581's precedent. No service-side Resilience4j or retries are added.
- **(b) Ramp period starts count from the pricing date: accepted as interim.** Follow-up DRG-531 carries the contract term start into the pricing context.
- **(c) The eslint relaxation: accepted.** It is template-only and kept as its own commit.
- **(d) File-level import refusals: not audited.** They are logged at WARN with the actor, the file SHA-256 and the error code.

### Review fixes merged (integration `c7eb395`)

- **FA** (`d59bc02`, `9b73fc3`, `1305b98`): B1 lookup, M3 `GET /api/v1/cost-basis/products/{id}`, m3, m8, m4, (d), and the M5 wording. Admin only. A product with only forward-dated cost rows uses that cost (it cannot be stale yet).
- **FB** (`03efc4a`, `aaacd66`): B1 evaluator, M1 replay pinning, M2 credit, M6 context tests, m1, m2, m7.
  - AC-02 on mock v1 gives 0.7380 / 0.7182 / 0.7182, and 0.7217 for the quote. A hybrid period with the migration credit gives 0.2222.
  - A zero-net ramp period has no margin, but its cost still counts in the line and quote totals.

### DoD rubric status

| Lane | D1 backlog DoD | D2 wave gates | D3 Jev |
|------|----------------|---------------|--------|
| L1 cost basis | Met on paper | **Open:** Maven compile and unit tests; Docker integration tests | **Open** |
| L2 margin | Met on paper | **Open:** Maven compile and unit tests; Docker integration and context tests | **Open** |
| L3 frontend CI | N/A (accepted) | **Met:** 20/20 projects, 701 tests. The production `crm` build needs Google Fonts | **Open** |
| L4 gateway | N/A (accepted) | **Open:** Maven compile and route-family tests | **Open** |

**Blocked.** `repo.maven.apache.org`, `repo.spring.io` and `api.typesafe.ai` are refused from this workspace, and no TypeSafe key is configured. The owner's computer is offline. **No PR opens until every cell reads Met.**
