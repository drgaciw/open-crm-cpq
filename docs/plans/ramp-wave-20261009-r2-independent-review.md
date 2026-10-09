# Ramp R2 independent review

Reviewer lane: `r1-verification`, baseline `e2773ac`. Review is read-only against the backend lane and assembled integration worktree. No product mutations were made by this reviewer.

## Verified scope

- Baseline R1 selected backend checks: 109 distinct tests have passing evidence across the initial 108 passes and the corrected CRLF assertion rerun. The newline normalization is test-only; committed switches remain unchanged.
- The orchestrator's integration PDF suite passed four tests using real Thymeleaf/OpenHTMLtoPDF rendering and PDFBox extraction. The original unrealistic 81,000 `totalAmount` fixture was corrected to actual initial amounts and separate contract metrics. Accessor compatibility, literal product-name escaping, flat-table absence and refusal to persist stale/unpriced PDFs are covered.
- The three-service live acceptance launcher and synthetic API/PDF script are syntax-checked. Their real run has not occurred in this reviewer lane. Browser and canonical event-consumption acceptance cannot be inferred from their static checks or from unit mocks.

## Approval propagation window and safety boundary

ADR-018 provides durable asynchronous outbox delivery, per-aggregate FIFO and idempotent consumption. ADR-013 makes quote-service the only frontend approval surface; frontend access to canonical `/api/v1/approvals/**` is prohibited. Neither ADR promises synchronous cross-service invalidation on a commercial edit.

The precise sequence requiring regression evidence is:

1. A ramp approval exists as PENDING in approval-routing.
2. A commercial edit commits in quote-service. Quote becomes stale and a retirement event is enqueued transactionally in its outbox.
3. Before relay/consumer delivery, approval-routing still holds the old PENDING row. A direct canonical approve can succeed during this propagation window because routing has no new binding yet.
4. Quote's BFF must immediately refuse the stale decision; send/accept must reject stale or mismatched commercial bindings. A direct canonical old grant cannot authorize the edited quote.
5. After consumption, an old still-PENDING request is superseded and a late canonical decision returns 409 `APPROVAL_SUPERSEDED`. If that request was already decided during the window, it remains decided history; the edited quote still needs its own current binding/grant.

This is an accepted architectural propagation window with a quote-owned safety gate. Describe the edit action as **enqueuing retirement**, not immediately retiring canonical state. The existing unit case named `changedFingerprintSupersedesEvenWithSameWorstDiscountAndLateCanonicalDecisionConflicts` calls the consumer before the canonical decision; it proves the post-consumption ordering, not immediate 409 following an edit.

Required paused-delivery regression: delay consumption, allow/record the direct canonical old decision where appropriate, assert quote BFF and send/accept remain fail-closed, then consume retirement/replacement and verify the current request and post-consumption 409. No synchronous cross-service invalidation API or hold change is proposed.

## Review findings and follow-up

| Finding | Resolution/evidence needed |
| --- | --- |
| Commercial-only fingerprint omitted policy/decision context, so unchanged monetary totals could retain an old grant after trusted policy changed | Verified source: pricing hash includes normalized governance, trusted policy context, subscription type, dates, billing frequency and all quote totals; authoring hash is separate. Fixtures exercise changed policy with unchanged money and scale-insensitive replay. Final expanded unit run is owned by backend |
| A policy downgrade needs to retire a prior canonical PENDING binding even when governing line changes | Verified source: quote-owned RampApprovalBindingChanged V1 registry/topic, one Quote aggregate publisher for retirement and replacement, approvalRequired=false creates CANCELLED and supersedes prior PENDING across governing lines. KafkaInbox guards redelivery; no new PENDING on downgrade. Broker delivery remains unexecuted |
| Newly introduced CANCELLED retirement rows mapped to null status in BFF history | Verified source: the quote BFF explicitly filters fingerprinted CANCELLED retirement tombstones from visible history; flat/legacy status mapping is unchanged |
| Snapshot preview disappeared on GET | Backend persists period and governance snapshots and teaches mappers to read them. Unit evidence plus real GET/reload assertion required |
| PDF contract summary used monthly initial totals without contract metrics | Corrected by orchestrator and real-renderer suite passed; real downloaded PDF text/layout inspection still pending |

## Known gaps and final acceptance limits

- Docker-tagged integration tests, broker delivery/ordering, migrations against actual PostgreSQL and canonical approval consumption are not proven by Docker-free unit runs.
- The scoped live environment uses real quote/pricing/pricebook REST and persistence, but no gateway/browser/authenticated identity, configuration-submit lifecycle or canonical approval consumer. Its relay remains off intentionally.
- CPQ 503 fallbacks and empty lists prove no ramp acceptance.
- Browser UAT/chaos is not wired into CI; frontend CI allowlist includes cpq-quotes, but skips other CPQ projects and shared-ui.
- Initial keyword graph query was stale/degraded. Fresh baseline named context/impact was used; new/final wave symbols require final assembled graph analysis. A partial/truncated/UNKNOWN result is unresolved.
- Final source review of the quote-owned binding topic, FIFO repository eligibility, no-approval retirement and authoring invalidation is complete. No new blocking defect was found in these reviewed paths. The graph used is the fresh baseline index, so new wave symbols are source-confirmed and still require final assembled graph analysis.
- The new paused-delivery BFF fixture `ApprovalServiceSingleWriterTest.pausedBindingDeliveryBlocksBothOldBffDecisionsWithConflict` checks immediate 409 for both old approve/reject and verifies no canonical mutation is called. Separate fixtures cover immediate local stale state and finalization against PENDING/REJECTED versus APPROVED current bindings. These are scoped unit checks, not one broker-backed test spanning direct canonical approval, edit, send/accept, delivery and replacement.
- Outbox retirement and replacement use the same Quote aggregate/type and Kafka topic/key. `leaseDueBatch` blocks every later same-aggregate row until earlier rows are PUBLISHED or SKIPPED, including failed/backing-off rows. No source-version field was added; old-event manual replay after a newer consumed binding remains an operational limitation documented in ADR-018, and live ordering has not been exercised.
- A late legacy fingerprint-free discount event cannot supersede an existing fingerprinted ramp request. The consumer explicitly excludes such ramp rows from legacy open-request selection; the new regression covers this direction.

Do not report full R2 business acceptance or production readiness from this scoped review.

## Final assembly readiness check

The backend owner reports 165 selected tests green before the final strict new-topic boundary fixture was added. That final fixture rejects fingerprint-free payloads on the quote-owned topic before canonical query/write and awaits the combined rerun. The orchestrator reports four real PDF renderer/extraction checks green before final assembly. The new Kafka integration test initially failed during setup because its JDBC URL used the public schema; after the setup correction, the orchestrator reports all three tests pass with no skips against real broker/database containers. This supplies scoped broker/database evidence; it does not substitute for the browser and real application REST lifecycle checks.

Read-only inspection of the assembled launcher/script confirms syntax checks pass, real document endpoints match the controller, runtime flags match both RampPricingProperties namespaces, pricing points at the isolated pricebook and quote points at isolated pricing. Committed five Azure autoconfiguration exclusions are inherited by each service. Pricing owns initial pricing-schema migrations; pricebook Flyway is disabled; quote migrates its own schema. No database or services were created by this reviewer.

Observed environment at readiness review: existing `postgres` container is running and publishes 5432; its environment contains POSTGRES_PASSWORD (value never printed). Redis listens on 6379. DB_PASSWORD is absent from the reviewer process, and free RAM is 3.18GB, below the launcher's mandatory 3.5GB threshold. Do not launch until the heavy build slot is released, final jars exist and memory passes validation. Existing services and databases remain untouched.

The user has already authorized scoped real acceptance. Root schedules the resource handoff and launches when the final artifacts and runtime protections pass; no additional user approval is required. Execution preparation in a fresh PowerShell process without inherited Jev enablement or Spring autoconfiguration overrides:

```powershell
$waveIntegration = 'C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-wave-20261009/integration'
if ($env:SPRING_APPLICATION_JSON -or $env:SPRING_AUTOCONFIGURE_EXCLUDE -or
    (Get-ChildItem Env: | Where-Object { $_.Name -match '^JEV.*ENABLED$' -and $_.Value -eq 'true' })) {
    throw 'Use a clean acceptance process environment without inherited Spring overrides or Jev enablement.'
}
$containerConfig = docker inspect postgres | ConvertFrom-Json
$credentialEntry = $containerConfig[0].Config.Env | Where-Object { $_.StartsWith('POSTGRES_PASSWORD=') }
if (@($credentialEntry).Count -ne 1) { throw 'Expected one local Postgres credential; do not guess or print it.' }
$priorDbPassword = $env:DB_PASSWORD
$env:DB_PASSWORD = ($credentialEntry -split '=', 2)[1]
try {
    & "$waveIntegration/scripts/verification/ramp-acceptance-processes.ps1" -Action validate -IntegrationRoot $waveIntegration
    & "$waveIntegration/scripts/verification/ramp-acceptance-processes.ps1" -Action start -IntegrationRoot $waveIntegration
    $env:RAMP_ACCEPTANCE_DATABASE = 'ramp_wave_20261009'
    Push-Location "$waveIntegration/.acceptance-artifacts"
    try { node "$waveIntegration/scripts/verification/ramp-r1-live-acceptance.mjs" }
    finally { Pop-Location }
} finally {
    $env:DB_PASSWORD = $priorDbPassword
    if (Test-Path -LiteralPath "$waveIntegration/.acceptance-artifacts/processes.json") {
        & "$waveIntegration/scripts/verification/ramp-acceptance-processes.ps1" -Action stop -IntegrationRoot $waveIntegration
    }
}
```

Credential discovery reads the existing local container configuration in memory, does not print/save the value, and does not reset a database password. If the configured password has since been rotated, startup must fail and be investigated; do not fall back to guessing. The launcher requires a genuinely new `ramp_wave_20261009` database or a prior same-worktree ownership manifest with no recorded process still live. Shutdown targets only recorded PID/start-time/jar/port matches and retains the isolated database as evidence. The finally stop should run only after a manifest was created; if validation fails before that point, no process cleanup is needed.

## Feasible isolated browser UAT

Inventory confirms Playwright 1.55.0 is already a frontend dependency and its matching Chromium revision 1187 is cached. The assembled application has no `frontend/dist/apps/crm/browser/index.html` yet. Existing smoke/chaos scripts default to the other checkout's :4200 and exercise broad CRM flows, so they are unsuitable for this isolated ramp check.

The smallest real UI check needs a serial Angular development build from the assembled integration tree (`NX_DAEMON=false`, one build worker, capped Node heap), then a small loopback static SPA server on an unused port such as 18090 and one headless Chromium context. Static server/browser overhead is expected around 300–600MB beyond the real three-service stack; measure actual free RAM before overlap. Do the Angular build before running the services rather than overlapping memory-heavy builds and acceptance.

Use `/quotes/<quoteId>` and `/quotes/<governedQuoteId>` from the real API result artifact. A test-owned loopback proxy or Playwright request forwarding may route only quote `/api/v1/quotes/**` calls to real isolated quote-service on 18084; these must be real HTTP responses and persisted mutations, never canned prices. Keep committed proxy.conf.json on 8080. Fail or record unrelated CRM/catalog/approval dependencies explicitly instead of treating fallback data as acceptance.

Assertions: persisted three-row priced preview with 40/45/50 rates and contract metrics after hard reload; real governance banner with governing period one, 32 percent, SALES_VP/PENDING and disabled Send/Accept; actual line edit invalidates preview/banner, shows stale alert and disables downstream actions until real Calculate; identical save/recalculate restores unchanged pricing without spurious stale state. Capture screenshots, browser console/network errors and exact scope. This route proves quote authoring/display against the scoped services; gateway identity, canonical approval inbox and complete submit/accept require additional services and remain separate gaps. No browser, frontend server or build was started during inventory.
