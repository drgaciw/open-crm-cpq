# OMS Jev exception-triage extension

Authorized by the project owner on 2026-09-21 after the OMS Jev integration plan.
Scope: integrate OMS `6858be4` with Jev/quote advisory `4a29d31`; add the single ADR-015
use case `oms-exception-triage`, disabled by default, with offline tests and shadow-only storage.

Integration resolves duplicate Flyway versions between the unmerged branches: retain OMS quote
V1_5–V1_7 and approval-routing V1_3 lease migrations, allocate quote Jev log V1_8 and routing Jev
log V1_4. No existing database was migrated or repaired. A database previously initialized from the
Jev-only branch requires an explicit migration-history reconciliation plan before using this branch;
do not blindly repair Flyway checksums or deploy it over that history.

The delivery contains a finite feature/rubric contract, optional after-commit recovery and daily
reconciliation hooks, five-order per-scope reconciliation batch limit, five-exception per-trigger limit,
bounded shared runner, scoped/versioned claims, atomic decision-log binding, and migrations V9/V10.
Shadow covers only `oms.scope-id`. If any configured recovery scope differs, the optional adapter
suppresses all evaluation; recovery itself continues. Multiple-scope shadow is not authorized here.
The normal OMS state machine never depends on this work. Global and use-case flags both default off,
transport defaults to no-op, retries default to zero. No topics, HTTP endpoints or UI are added.

J0 scope integration and the J1 offline contract harness are delivered; J2 shadow persistence/hooks
are prepared disabled. J1's model-quality gate is NOT passed by scripted answers. A future J3 read/UI
must enforce scope, expiry, current order/exception versions, closed-state suppression and exact
decision feedback binding. The wider plan remains phased, not declared complete by this slice.

Evaluation evidence: [OMS Jev offline report](../oms/jev-offline-evaluation.md).
Live shadow/advisory/canary requires the ADR-015 data/provider gates, verified evaluator version,
deployment-wide budget, independent labeled predictions and flag-off drill. Baseline OMS release
gates and every unrelated project hold remain in force.
