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

## Environment note

This cloud workspace cannot reach Maven Central, and the owner's computer was offline at planning time. Backend lanes L1, L2 and L4 start once Maven resolution works: either the host is allow-listed, or the owner's computer is back online. L3 needs only npm and starts immediately.
