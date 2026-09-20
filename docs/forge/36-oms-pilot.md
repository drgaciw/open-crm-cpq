# Authorized OMS pilot wave

User authorized implementation on 2026-09-20; base origin/main 8db288016c6da39bea8c29322613718f15faa459.

Authorize only order-management service/schema, order workspace, Kafka acknowledgment hardening,
immutable quote snapshots and backend line-level subscription handoff. Preserve all unrelated holds:
NgRx, subscription frontend, Activities, approvals dual-read flip, S2B-9 and agent Kafka expansion.

Pilot: one deployment scope, USD, integer quantities, physical/digital/manual services, fixed
one-time/monthly/annual charges. No native inventory, accounting or tax engine. Manual evidence
requires actor, time, quantity, external reference and reconciliation. Test adapters cannot establish
production success. Intake defaults disabled. Actual production launch requires named owners,
retention policy, cutover verification and operational acceptance.

## Dependency board / ownership

C contracts -> Q quote, R common relay, O OMS core, S OMS security, K contract ownership, U UI.
O+R+S -> E executor; E -> F fulfillment and B finance; F+B+K -> X recovery; all -> I acceptance.
Coordinator plus three workers maximum; reserve a slot for reviews when work is ready for review.
Each workstream has its own branch/worktree and sole writer. Coordinator owns shared contracts,
reactor/gateway/shell/CI and migration allocation; agents must request cross-boundary edits.

Q owns quote-service (migration next unused version); R owns common/event existing relay and tests;
K owns contract-asset (migration next unused version); O/E/F/B/X own distinct packages in
order-management, migrations V1 order, V2 execution, V3 operations; S owns OMS security/config;
U owns frontend/libs/cpq/orders exclusively, including recovery/evidence UI.

Before edits: worktree-bound GitNexus upstream impact. UNKNOWN requires source checks; HIGH/CRITICAL
requires warning. Before commits: complete detect-changes. TDD behavioral red/green and separate
specification then quality reviews. Indexes must be worktree-local; serialize if registry collisions occur.

## Cutover

Deploy contracts/schema disabled, then fail-closed legacy ownership fence on every consumer.
Only afterward enable quote cohort ownership, snapshot and outbox in one transaction. Then deploy
OMS consumer and per-line activation, shadow reconcile, and enable selected pilot cohort.
Rollback stops NEW intake; existing OMS orders retain ownership and drain/reconcile. Never replay
them into legacy subscription creation. Historical quotes are not automatically converted.

## Acceptance

OM-001..005 capture; OM-007..010 state/quantity/plan; OM-012 execution; OM-015..019 adapters and
handoff; OM-023..025 queue/UI/reconciliation. NFR-01..08 all apply. E2E-01..11,13..18 P0 portions;
manual allocation makes no atomic stock promise. Returns/refunds and non-USD cases are P1.
Required: 100-way conversion, aliases, snapshot immutability, version/body conflicts, replay,
lease/crash/ack failure, unknown outcomes, cancellation races, legacy overlap, scope denial,
partial finance acknowledgments, restore and disable-intake drain. Performance and recovery
budgets are targets requiring measured evidence, not claims about this implementation.
