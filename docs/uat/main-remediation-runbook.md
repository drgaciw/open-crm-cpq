# CRM and CPQ UAT remediation

Replay the original case ledger against a clean worktree at the selected main revision. Record the old ID, the current assertion or release gate, the actual result, and the evidence location. A case that moved to a different suite needs an explicit mapping. Tag browser interactions, service HTTP, and database integration evidence separately.

## Released acceptance

1. Package the backend and install the exact frontend lockfile in the fresh worktree. Use a separate Postgres, Redis, and Kafka deployment with no real credentials. Start Pricing before Price Book and verify the proxied catalog, quote, CRM, configuration, pricing, and approval routes. Use only run-owned fixtures and stop only the test processes and containers. The production proxy target remains the gateway.
2. Try Browser Use on the supported isolated origin if credits are available. Local Playwright gives independent browser evidence if Browser Use is blocked. Run `npm run e2e:uat -- --no-jev --strict` with `UAT_BASE_URL` pointing at the isolated frontend. Set `UAT_ISOLATED_FIXTURES=1` only for the run-owned loopback deployment. Exercise the `released-regressions` suite for atomic quote removal, responsive dashboard, keyboard skip, and quote deal type display.
3. Run the CAM Docker integration cases `AssetWriteHoldIntegrationTest`, `DocumentApplyNullStartReplayIntegrationTest`, `AssetHistoryReplayIntegrationTest`, and `DocumentApplyIntegrationTest` under Maven's `integration-tests` profile. Verify authenticated populated reads and ordinary-seller denial with a local signed identity, separately from the browser's default access state. A check from an isolated test context proves only its stated scope.
4. Check a real Configuration Engine outage by stopping the run-owned instance, submitting a valid synthetic draft, restoring the instance, and retrying the same draft. Capture the first response, the persisted Draft state, and the successful retry. Restore this service before running other configuration or quote suites.
5. Rerun affected component, service, and HTTP tests, and all deterministic browser suites. Compare case IDs and the route and widget inventory to the old report. Keep duplicate and stale raw entries as evidence but close an old ID only when a current assertion covers the same behavior.

## Release gates

| Work | Owner prerequisite | Required evidence |
|---|---|---|
| Durable asset writes and S4 apply | Project and CAM owners approve reconciliation of old acceptances, line identity, and the effective-date lifecycle. Continue the S4 and legacy writer holds until that review. | Reconcile historical subscriptions and assets in nonproduction. Prove activation, future-effective apply, termination, ordered replay, deduplication, and rollback with production-shaped pins. Switch S4 readiness only after the verified implementation is deployed to all replicas. |
| Renewal from Assets and amendment UI | Project owner approves the nonproduction ramp and lifecycle release and domain owners approve tenant authority and role grants. Use the DRG-614 rollout decision for ramp and the documented CAM hold exception for browser scope. | Start from a real subscription and asset. Verify preview, draft, calculation, approval, apply, and read-back. Verify role and tenant isolation. Keep internal apply private and the existing legacy acceptance writer disabled. |
| CRM field registry publication | CRM and identity owners approve a forward migration and a verified tenant ownership and editor policy. | Verify fresh migration and upgrade, authorize all definition and value operations through verified tenants, and exercise typed values, archive behavior, and cross-tenant denial. The shipped guard remains in place until this release. |
| Browser Use and production-shaped access | Browser Use account owner provides credits and supported private test access. Identity owner supplies the authorized nonproduction Entra setup. | Run Browser Use against the isolated environment and verify real nonproduction login and session behavior separately from synthetic JWT tests. |
| ERP and customer delivery | Integration owners provide an authorized ERP endpoint and a test recipient or delivery sink. | Trace tenant-scoped activation and idempotent retry into ERP. Trace one quote send to actual delivery in the test sink. A local state transition or a simulated adapter is limited evidence. |

Do not count a gated positive workflow as passed based on the expected hold response. Track the negative hold check separately. Released acceptance requires zero unexplained failures and blockers, with named prerequisites for release-gated rows.

## Owner release approval

On 2026-11-23 at 09:23 CST, the project owner approved release of the nine cases listed as release-gated in the remediation ledger. Track these as approved and awaiting their technical prerequisites. This approval covers the named cases. Verify current implementation, tenant and actor authority, data reconciliation, and the target environment before enabling any path.

Current evidence still shows a hard-disabled S4 readiness policy, a simulated ERP adapter, and no deployed field-registry schema or verified tenant ownership for CRM records. The local signed runs establish specific access checks and serialization contracts. The integration releases need the designated nonproduction Entra, ERP, and delivery environment.
