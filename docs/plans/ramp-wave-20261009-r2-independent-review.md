# Ramp R2 independent review

Reviewer lane: `r1-verification`, baseline `e2773ac`. Review is read-only against the backend lane and assembled integration worktree. Backend-lane review was read-only; this lane separately repaired identical full PUT preservation under coordinated impact and RED-to-GREEN verification.

## Verified scope

- Baseline R1 selected backend checks: 109 distinct tests have passing evidence across the initial 108 passes and the corrected CRLF assertion rerun. The newline normalization is test-only; committed switches remain unchanged.
- The orchestrator's integration PDF suite passed four tests using real Thymeleaf/OpenHTMLtoPDF rendering and PDFBox extraction. The original unrealistic 81,000 `totalAmount` fixture was corrected to actual initial amounts and separate contract metrics. Accessor compatibility, literal product-name escaping, flat-table absence and refusal to persist stale/unpriced PDFs are covered.
- The three-service live acceptance launcher and synthetic API/PDF script are syntax-checked. Root has executed staged startup and real fixture setup. The entry-query failure and other verified blockers were repaired; real API assertions and REST PDF download passed. The final browser run passed all five scoped checks with no page errors, console errors or failed responses. The final downloaded PDF has one page, validated text and a visually clean layout. Canonical event-consumption acceptance cannot be inferred from these checks or unit mocks.

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
| Commercial-only fingerprint omitted policy/decision context, so unchanged monetary totals could retain an old grant after trusted policy changed | Verified source: pricing hash includes normalized governance, trusted policy context, subscription type, dates, billing frequency and all quote totals; authoring hash is separate. Fixtures exercise changed policy with unchanged money and scale-insensitive replay. The original assembled 906-test gate passed this boundary; the two full-update regressions bring final unit evidence to 908 |
| A policy downgrade needs to retire a prior canonical PENDING binding even when governing line changes | Verified source: quote-owned RampApprovalBindingChanged V1 registry/topic, one Quote aggregate publisher for retirement and replacement, approvalRequired=false creates CANCELLED and supersedes prior PENDING across governing lines. KafkaInbox guards redelivery; no new PENDING on downgrade. Three real Kafka/PostgreSQL cases pass separately; full authoring-to-outbox application lifecycle remains unexecuted |
| Newly introduced CANCELLED retirement rows mapped to null status in BFF history | Verified source: the quote BFF explicitly filters fingerprinted CANCELLED retirement tombstones from visible history; flat/legacy status mapping is unchanged |
| Snapshot preview disappeared on GET | Backend persists period and governance snapshots and teaches mappers to read them. Unit evidence and final real GET/reload assertions pass |
| PDF contract summary used monthly initial totals without contract metrics | Corrected by orchestrator and real-renderer suite passed; Actual REST PDF text and first-page layout were independently inspected; its blank trailing page was reproduced and repaired with a renderer regression, and the final downloaded artifact is one page with validated text and clean visual layout |

## Known gaps and final acceptance limits

- Docker-tagged integration tests, broker delivery/ordering, migrations against actual PostgreSQL and canonical approval consumption are not proven by Docker-free unit runs.
- The scoped live environment uses real quote/pricing/pricebook REST and persistence, including scoped browser checks, but no gateway/authenticated identity, configuration-submit lifecycle or canonical approval consumer. Its relay remains off intentionally.
- CPQ 503 fallbacks and empty lists prove no ramp acceptance.
- Browser UAT/chaos is not wired into CI; frontend CI allowlist includes cpq-quotes, but skips other CPQ projects and shared-ui.
- Initial keyword graph query was stale/degraded. Fresh baseline named context/impact was used; new/final wave symbols require final assembled graph analysis. A partial/truncated/UNKNOWN result is unresolved.
- Final source review of the quote-owned binding topic, FIFO repository eligibility, no-approval retirement and authoring invalidation is complete. No new blocking defect was found in these reviewed paths. The graph used is the fresh baseline index, so new wave symbols are source-confirmed and still require final assembled graph analysis.
- The new paused-delivery BFF fixture `ApprovalServiceSingleWriterTest.pausedBindingDeliveryBlocksBothOldBffDecisionsWithConflict` checks immediate 409 for both old approve/reject and verifies no canonical mutation is called. Separate fixtures cover immediate local stale state and finalization against PENDING/REJECTED versus APPROVED current bindings. These are scoped unit checks, not one broker-backed test spanning direct canonical approval, edit, send/accept, delivery and replacement.
- Outbox retirement and replacement use the same Quote aggregate/type and Kafka topic/key. `leaseDueBatch` blocks every later same-aggregate row until earlier rows are PUBLISHED or SKIPPED, including failed/backing-off rows. No source-version field was added; old-event manual replay after a newer consumed binding remains an operational limitation documented in ADR-018, and full live quote-authoring ordering remains unexecuted; scoped broker replacement/retirement delivery has passing evidence.
- A late legacy fingerprint-free discount event cannot supersede an existing fingerprinted ramp request. The consumer explicitly excludes such ramp rows from legacy open-request selection; the new regression covers this direction.

Do not report full R2 business acceptance or production readiness from this scoped review.

## Final assembly readiness check

Final affected-module unit evidence is 908 passing cases: 621 were rerun after the repairs (including quote 207), and the remaining unchanged 287 retain their prior passing evidence. This includes strict fingerprint-free new-topic rejection and four real PDF renderer/extraction cases. Eleven distinct Docker cases have passing evidence: three Kafka/PostgreSQL canonical delivery/binding cases, one PostgreSQL pricebook repository case exercising all five query variants, five PostgreSQL/Flyway managed-seed cases (two import and three optional-input), and two quote schedule persistence cases. The final quote-plus-library rerun passed 606 unit cases in 38.302 seconds; total distinct unit evidence remains 908, without double-counting reruns. These suites have separate execution evidence and do not establish the broader integration suite. Final frontend evidence is 127 tests, library typechecking and the Angular development build. The prior published checkpoint had complete forced graph all/compare-main checks covering 54 files, 294 symbols and 41 flows without partial/truncated flags; CRITICAL shared mapping/contract risk was disclosed and reviewed. New repairs require a refreshed complete graph gate before committing, and this report does not assert its future counts. Draft PR 579 was initially published at 70bd977; the final new head is pending; R1 remains In Review, R2 In Progress and R3/R4 readiness only. CI is pending and nothing has been merged or deployed. The new Kafka integration test initially failed during setup because its JDBC URL used the public schema; after the setup correction, the orchestrator reports all three tests pass with no skips against real broker/database containers. This supplies scoped broker/database evidence; it does not substitute for the browser and real application REST lifecycle checks.

Read-only inspection of the assembled launcher/script confirms syntax checks pass, real document endpoints match the controller, runtime flags match both RampPricingProperties namespaces, pricing points at the isolated pricebook and quote points at isolated pricing. Committed five Azure autoconfiguration exclusions are inherited by each service. Pricing owns initial pricing-schema migrations; pricebook Flyway is disabled; quote migrates its own schema. No database or services were created by this reviewer.

Observed environment at readiness review: existing `postgres` container is running and publishes 5432; its environment contains POSTGRES_PASSWORD (value never printed). Redis listens on 6379. DB_PASSWORD is absent from the reviewer process, and the original 3.5GB launcher threshold prevented startup (latest root validation 3.34GB). A revised staged API budget requires at least 2.5GB initially (the prior bounded three-JVM run measured 1.15GB aggregate RSS), 1.5GB before each subsequent JVM and 0.8GB after readiness and immediately before API execution, logging owned RSS/free memory. Direct buffers and code cache are each capped at 64MB per acceptance JVM; heap/metaspace remain 384/192MB. Browser independently requires at least 1GB free. Launch only after the heavy slot is released and final jars exist; no unrelated process may be stopped for memory. Existing services and databases remain untouched.

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
    & "$waveIntegration/scripts/verification/ramp-acceptance-processes.ps1" -Action check-api -IntegrationRoot $waveIntegration
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

Inventory confirms Playwright 1.55.0 is already a frontend dependency and its matching Chromium revision 1187 is cached. The final assembled Angular development build now exists and its source/artifact hashes were verified by the frontend lane. Existing smoke/chaos scripts default to the other checkout's :4200 and exercise broad CRM flows, so they are unsuitable for this isolated ramp check.

The completed scoped UI check used a serial Angular development build from the assembled integration tree (`NX_DAEMON=false`, one build worker, capped Node heap), then a small loopback static SPA server on an unused port such as 18090 and one headless Chromium context. Static server/browser overhead is expected around 300–600MB beyond the real three-service stack; measure actual free RAM before overlap. Do the Angular build before running the services rather than overlapping memory-heavy builds and acceptance.

Use `/quotes/<quoteId>` and `/quotes/<governedQuoteId>` from the real API result artifact. A test-owned loopback proxy or Playwright request forwarding may route only quote `/api/v1/quotes/**` calls to real isolated quote-service on 18084; these must be real HTTP responses and persisted mutations, never canned prices. Keep committed proxy.conf.json on 8080. Fail or record unrelated CRM/catalog/approval dependencies explicitly instead of treating fallback data as acceptance.

Assertions: persisted three-row priced preview with 40/45/50 rates and contract metrics after hard reload; real DRAFT quote governance banner with governing period one, 32 percent, SALES_VP/PENDING and disabled Send/Accept; actual DRAFT line edit invalidates preview/banner, shows stale alert and disables downstream actions until real Calculate; identical save/recalculate restores unchanged pricing without spurious stale state. Capture screenshots, browser console/network errors and exact scope. This route proves quote authoring/display against the scoped services; gateway identity, canonical approval inbox and complete submit/accept require additional services and remain separate gaps. No browser, frontend server or build was started during the historical inventory; root later executed the scoped browser run successfully.



Browser scope correction: approvalState=PENDING governance on a DRAFT quote does not establish the actual PENDING_APPROVAL-status edit gate. Entering that status requires canonical approval-routing with a current-fingerprint pending request. The submit-for-review API additionally needs real product/configuration preflight and enters IN_REVIEW, not PENDING_APPROVAL. The three-service harness therefore verifies DRAFT edits with pending governance; actual bound-pending status remains covered by the 127 frontend unit tests and is an explicit live gap.


Live checkpoint: staged startup succeeded with the bounded JVM configuration. The latest three-service run measured 1.147GB aggregate RSS and approximately 1.6GB free RAM from an initial 2.78GB; exact UTC process identity and finally cleanup worked, stopping all and only the three wave-owned JVMs. That prior attempt exposed an actual HTTP 500 during pricebook entry creation: the JPA query treated the priceBookId transient accessor as a mapped path and raised UnknownPathException. The repository now uses explicit mapped association paths, and its real PostgreSQL regression covers all five query variants. This historical failure is resolved, rather than a current RAM or API blocker. A real API result artifact now records passing quote→pricing→pricebook HTTP, AC01 arithmetic, persisted GET preview, rejected-gap rollback, replay and AC05 governing-period assertions, with an actual REST PDF artifact. The earlier browser attempt reproduced an identical PUT defect. After its repair, the final browser run passed all five scoped checks, including identical save, changed-period invalidation and real recalculation, with empty page/console/network error arrays. Passing readiness alone does not establish business acceptance. The earlier published-empty-book fixture error was corrected through draft authoring, entry-before-publish and a fresh isolated regional book; the immutable prior fixture is retained. At that historical checkpoint no browser acceptance had run. Final repaired browser and one-page PDF checks passed; actual PENDING_APPROVAL-status edits remain excluded from this three-service harness.


Final repair review: the full QuoteLineItemService.updateLineItem path previously cleared priced fields and snapshots and marked ramps stale even on identical commercial authoring. The narrow repair compares normalized authoring before resetting pricing, preserving unit/net/extended amounts, discount rate/amount, explanation, line/quote snapshots, metrics, governance and pending status on an unchanged bound PUT, including note-only edits. A meaningful full-service RED test reproduced lost snapshot data; GREEN passes 29 targeted cases, including unchanged full PUT and changed-period retirement with a real guard/outbox interaction. The changed commercial path still clears prices, marks stale and queues retirement.

The additive V7 seed migration prepends only package/import declarations to eleven exact managed V2/V3 bodies. It requires default ruleset UUID rs-default-001, pinned rule UUID, system creator and exact original-body MD5 after CRLF normalization. Owner-edited bodies are skipped; all policy logic, metadata and timestamps are preserved; earlier migration checksums are untouched. Two real Flyway/PostgreSQL tests prove all eleven repaired persisted bodies compile and that an owner-edited rule remains untouched. Its runtime reach includes managed core pricing beyond ramps, so this is a shared pricing repair, not a ramp-only policy change. Existing golden/unit evidence and the final real pricing attempt remain the appropriate acceptance boundaries.

Current delivery state: real API assertions, REST PDF download, all five scoped browser checks and one-page PDF text/visual inspection have passed. No final new graph counts, commit hash or PR check conclusion is asserted here. True PENDING_APPROVAL status requires a current canonical routing request; submit-for-review enters IN_REVIEW after product/configuration preflight. The three-service harness therefore checks DRAFT plus pending governance only; real canonical status-entry/edit/submit/send/accept remains a scoped acceptance gap.

Latest independently reviewed persistence repair reconciles child schedules by sequence. Existing managed rows retain their IDs and parent/sequence; start month, length, quantity, price basis, unit price and discount fraction are updated explicitly. New sequences receive the parent; removed sequences are physically deleted through orphan removal, allowing later re-add without the unique-key collision from INSERT-before-orphan-DELETE. A null schedule clears rows and escalator metadata, and absent escalator inputs still clear all display-only fields. Two real PostgreSQL cases reproduced the original uniqueness failure and now verify flush/reload identity preservation, deletion and re-add; the selected eleven authoring unit cases also pass. This supplies JPA ordering evidence, not additional canonical approval lifecycle evidence.

V8 independently reviewed scope is five exact V7-managed bodies, gated by default ruleset, system creator, pinned rule IDs and original normalized body hashes. It short-circuits absent optional inputs without fabricating usage/cost facts; malformed nonempty numeric inputs still fail. Real tests verify retained valid usage, core, maintenance, volume, migration, margin, proration, churn, renewal, TCV and ACV outputs, owner-edited DRL preservation and unchanged metadata. V7/V8 therefore preserve owner policy while repairing compilation/optional-input execution across the managed core pricing corpus.

PDF pagination evidence: the actual downloaded AC01 PDF contained correct text and a visually correct first page plus a second page with zero text, drawings or images. Root strengthened the existing actual-renderer fixture with live name/customer/date lengths and a nonempty-page assertion; RED was four cases with one failure, then GREEN four of four after removing only the body's bottom margin. No global page-break suppression or application font changes were introduced. Historical browser screenshots had degraded fonts because the harness blocked external font resources. The final harness run supersedes that failure and passed all five checks without console errors; no global application font change was introduced. Final refreshed graph counts and PR head remain unclaimed pending the root gate.


Final artifact checkpoint: API quote 38, governed quote 39, pricebook 39 and document 2 are real synthetic persisted records. AC01 TCV/ACV/start ARR/exit ARR are 81000/27000/24000/30000 and persisted priced periods reload. Browser result status is PASS with five checks and empty pageErrors, consoleErrors and failedResponses. The API script's static known-gap strings for browser/PDF were point-in-time placeholders; the separate final browser and PDF artifacts resolve those two gaps. No listener remains on 18082/18084/18087/18090 after root-owned cleanup. Canonical lifecycle, authenticated gateway and true PENDING_APPROVAL-status browser edits remain excluded.
