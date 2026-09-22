# Explicit refresh scheduling and hysteresis

`SelectionRefreshCoordinator` is a closeable integration API. No Spring bean or scheduler starts automatically. `Settings.defaults()` disables scheduling and selects `SHADOW`; even an explicit `runOnce()` only saves a valid proposal and publishes an outcome. It never activates or revokes a policy in shadow mode. Connect the outcome consumer to a durable report sink; `lastOutcome()` remains available if that observer fails.

An application owner can wire approved suppliers and a lifecycle explicitly:

```java
var refresh = new SelectionRefreshCoordinator(
    new SelectionRefreshCoordinator.Settings(
        approvedSchedulingEnabled,
        SelectionRefreshCoordinator.Mode.SHADOW,
        approvedInterval,
        approvedMinimumBenefitFraction,
        approvedMinimumSwitchInterval),
    policyRepository,
    currentProfileSupplier,
    currentCatalogSupplier,
    budgetedSyntheticEvaluationSupplier,
    Clock.systemUTC(),
    durableOutcomeConsumer);
refresh.start();                 // false when scheduling is disabled; otherwise one fixed-delay worker
refresh.runOnce();               // immediate price/capability/retirement/profile/quality event trigger
// Close at shutdown, including for a disabled coordinator.
refresh.close();
```

The evaluation supplier must return `RefreshEvaluation(profileId, profileVersion, catalogVersion, selectionReport)` with the exact profile/catalog versions used during evaluation and explicit candidate coverage. This binding is required even when no candidate qualifies and the report has no proposed policy. Never relabel a cached/stale report with newer versions. The supplier owns authorization for external calls, provider restrictions, synthetic/redacted fixtures, and evaluation budgets. Repeated scheduled runs must share an owner-defined rolling/lifetime spend and call ceiling; resetting an unconstrained run budget on every callback is not a scheduling budget. Supply an offline evaluator for local use. The coordinator makes no provider calls itself and has no embedded credentials.

The coordinator snapshots the active policy version before evaluation, rejects superseded results, and checks context bindings before applying negative findings. Repository compare/revoke/activate operations share the same repository-instance monitor; the integration must reuse that instance for administrative/canary operations and obey the repository's single-writer contract. A stale rejection report cannot revoke a newer valid policy. Failed evaluations re-read current catalog/profile state before claiming that a prior policy remains eligible.

Only explicit `CANARY` mode can activate a proposal. Every activation rechecks current profile/catalog eligibility, complete evidence, revocation, measured cost, and maximum per-request reservation cost. A valid predecessor is recorded for eligible rollback. Failed/incomplete evaluations retain an eligible current policy; a cached catalog is usable only while its original freshness and policy lifetime still pass. Expired/revoked evidence is never revived.

While the current policy remains eligible, a different primary needs a strictly positive cost improvement meeting `minimumBenefitFraction`, and the current policy's `effectiveAt + minimumSwitchInterval` must have elapsed. A matching primary is retained while valid. In canary mode, catalog/profile/budget invalidation or a complete evaluation explicitly rejecting an active route revokes that policy; safety replacement bypasses hysteresis. Shadow records such rejection without changing active state; its observations must feed an explicit canary/owner decision before activation. Runtime separately rechecks its current profile/catalog on every dispatch.

Catalog versions remain exact: each newly published catalog snapshot requires new compatible policy evidence. Hysteresis applies to still-eligible policies sharing the same current catalog; it never overrides price/capability changes or relaxes the runtime catalog-version check. An integration should retain its current catalog until a refresh and corresponding evaluation are complete, rather than publishing half a refresh. Interval settings should leave time to renew evidence before expiration. No forecast or quality calibration is implied by scheduling.

Tests use fake clocks and an injected scheduler to verify cadence, exception recovery, close, shadow behavior, hysteresis, cached-failure retention, expiry, revocation and safety replacement without sleeping or calling providers. Workload-owned confidence calibration, live-provider cost reconciliation, and rollout approval remain required before activation.
