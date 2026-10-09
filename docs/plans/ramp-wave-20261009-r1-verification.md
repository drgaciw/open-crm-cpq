# Ramp wave R1 verification

Assessment baseline: `e2773ac`, 2026-10-09. This report inventories evidence; no checks below have run unless explicitly recorded.

## Acceptance evidence and gaps

| Requirement | Existing executable evidence | Remaining evidence |
| --- | --- | --- |
| AC-01 / AC-02 arithmetic | `PricingRampEngineTest` with committed `fixtures/pricing/ramp` inputs and independently specified totals | Real quote HTTP calculate to real pricing-service, persisted schedule and totals read back |
| AC-03 validation | `RampScheduleValidatorTest`, `QuoteLineItemServiceRampTest`, pricing engine malformed-input cases | Real HTTP 422 reason and database no-write verification |
| AC-04 escalators | `RampEscalatorExpanderTest`, line-authoring tests, editor tests | Browser authoring and generated explicit steps persisted/read back |
| AC-07 flat compatibility / ACV | `PricingExecuteGoldenFixtureTest`, `MetricsCalculatorTest`, `AcvVersionFloorTest` | Targeted tests must run on this baseline; default switches remain off |
| AC-08 unsupported/missing periods | `PricingRampEngineTest`, `QuoteServiceRampTest`, client tests | Real transport flag-off 422 and rollback |
| AC-09 replay | `PricingRampEngineTest` golden replay | Cross-service persisted replay using fixed pricing context |
| Quote wiring | `QuoteServiceCalculateTest.calculateAc01ReturnsRampTotalsOnTheQuoteView`, client tests | That test mocks pricing; it does not prove full-stack AC-01 |
| Editor | `quote-ramp-editor.component.spec.ts`, detail component specs | Current `frontend/e2e` contains no ramp scenario; quote lifecycle alone is insufficient |

`scripts/ci/frontend.sh` already includes `cpq-quotes`. The original sprint document's statements that CPQ quotes is absent are historical, not current CI behavior. Other CPQ projects and shared-ui remain outside the allowlist.

## Resource and command plan

Java is Temurin 25.0.4.1. No global Maven command is available; `backend/mvnw.cmd` has a cached Maven 3.9.9 distribution. Docker is available, but CPQ service containers are absent from the initial inventory. Verification worktree has no frontend node_modules.

Use one orchestrator-assigned build slot at a time. Initial free RAM was about 3.4 GB; start with Maven heap 768 MB, one test fork, fork heap 512 MB, no reactor parallelism. Avoid service jar locks by building only this isolated worktree. Do not start services, containers or browsers without orchestration. Do not target the other checkout's :4200 UI.

Suggested backend reactor: common, pricing-service and quote-service plus their dependencies; `-Punit-tests`, explicitly selected ramp/golden/ACV/client/quote lifecycle tests, `-Dsurefire.failIfNoSpecifiedTests=false`. Use `verify` rather than only `test` because quote tests consume security's test-jar produced in the package phase. Preserve console logs and aggregate Surefire XML counts. Check that no Testcontainers container-start log appeared.

Suggested frontend: share an existing dependency tree through a worktree-local junction if approved by the orchestrator; run `cpq-quotes` Vitest with one worker and daemon disabled. A fresh install costs disk/network and should be coordinated with other agents.

Full-stack acceptance needs quote, pricing, pricebook and product services, a seeded recurring product/customer/book, PostgreSQL and gateway/auth; configuration-engine plus approval-routing are additionally needed for submit/approval. A browser scenario must use a separate explicitly assigned origin and mock data. Sample acceptance: list $50, quantity 50, 36 months, unit steps $40/$45/$50; assert monthly 2000/2250/2500, periods 24000/27000/30000, TCV81000, ACV27000, ARR24000, exitARR30000 after save/calculate/reload.

## Graph limitations

Initial local root index is stale at `4aa3173a` (2026-09-22). `query ramp --repo .` returned no results and warned missing FTS indexes. Named backend `QuoteService` context resolved but predates ramp implementation; it is not acceptance evidence or a safe edit clearance. Fresh integration-worktree graph and per-symbol impacts are required before product/test function edits. No product functions were edited during this inventory.

## Known gaps

- Docker-tagged integration tests are excluded from the unit plan; a Docker-free pass does not run `*IntegrationTest` classes.
- CPQ empty lists/503 fallbacks prove nothing about ramp behavior.
- Browser UAT/chaos scripts are not wired into CI and do not yet include ramp workflows.
- No live-stack or browser acceptance was performed in this inventory.
- A stale, partial, truncated or UNKNOWN graph result is unresolved.

## Execution results

Selected Maven `verify -Punit-tests` completed on the baseline with 109 tests: 108 passed, one failure, zero errors/skips (common 4, pricing 33, quote 72). No Testcontainers startup appeared in the log. Log: `r1-targeted-unit.log` in the worktree (execution artifact, not a planned committed file).

The failure is the existing `RampPricingYamlTest.rampFlagDefaultsOff`: it searched LF-only strings against a Windows CRLF resource. The actual flag remains `false` and cap remains `36`. Fresh graph at `e2773ac` via `ramp-wave-20261009` returned exact LOW impact for both test class and method, zero callers/processes, no partial result. Applied test-only CRLF-to-LF normalization; selected test rerun via cached five-module Maven `verify` passed (1 test, zero failures/errors/skips; 8.754 seconds). Log: `r1-crlf-rerun.log`. No flags or product behavior changed. Across the original selection and the corrected-test rerun, all 109 selected tests have passing evidence; they were not all repeated together after the test-only fix.

Fresh named graph context resolves current backend QuoteService and its test callers, but FTS remains unavailable. Named context is usable; keyword zeros retain that limitation.

Smallest real acceptance environment is three services from this wave checkout: quote `18084`, pricing `18082`, pricebook `18087`, pointing at a dedicated `ramp_wave_20261009` database on the existing PostgreSQL and Redis. Product-service is unnecessary for line creation/calculate; it is used by submit's configuration preflight. Pricing always invokes pricebook resolution, so a real pricebook service is required. Seed pricing customer, regional default USD book/entry at list 50, and the three-step AC-01 quote through APIs. Set ramp and ACV switches only in local process arguments; leave committed defaults off/legacy and outbox relay off. No Kafka/gateway/browser/auth lifecycle required for this scoped calculate acceptance. Historical planning estimate was 1.8–2.4 GB aggregate RSS and 3.5GB free before launch. It is superseded by actual bounded three-JVM startup measurements around 1.15GB aggregate RSS. Current guards require 2.5GB initially, 1.5GB before each later JVM, 0.8GB after readiness and before API/PDF, and independently 1GB before the browser; memory-heavy builds remain serialized. Root has created the owned test database and started/stopped only these three wave processes for scoped live attempts.

Do not report R1 end-to-end acceptance or complete rollout from this inventory.

## Isolated live acceptance launch checklist

The orchestrator owns the three process PIDs and their shutdown. Starts are authorized only for isolated acceptance processes after the single heavy-build slot is released, with 2.5GB initial free RAM, 1.5GB before each later JVM, 0.8GB after readiness/before API and independently 1GB before browser. The historical 3.5GB estimate is superseded by measured approximately 1.15GB aggregate three-JVM RSS. Verify ports 18082/18084/18087 are unbound; create a new database named `ramp_wave_20261009` only if absent. Never seed a pre-existing database or restart existing development services.

Use assembled wave artifacts, not the other checkout's jars. Start pricing first (owns pricing-schema Flyway, including pricebook tables), then pricebook (Flyway disabled, validates that schema), then quote. For each: Java25 `-Xmx384m -XX:MaxMetaspaceSize=192m -XX:MaxDirectMemorySize=64m -XX:ReservedCodeCacheSize=64m`; `--server.address=127.0.0.1`; respective `--server.port`; `--spring.profiles.active=dev`; `--opencrm.security.mode=dev`; `--spring.datasource.hikari.maximum-pool-size=3`; `--spring.datasource.hikari.minimum-idle=1`; `--opencrm.outbox.relay.enabled=false`. Supply `DB_NAME` through process environment and DB credentials through the existing secure local environment; do not log them or commit them. Existing Redis can be used. Disable Kafka listener startup locally if a module contains listeners (`--spring.kafka.listener.auto-startup=false`); no lifecycle event is exercised.

Pricing arguments: `--opencrm.pricing.ramp.enabled=true --opencrm.pricing.metrics.acv-version=term-years --opencrm.pricebook.base-url=http://127.0.0.1:18087`. Quote arguments: `--opencrm.pricing.ramp.enabled=true --services.pricing.url=http://127.0.0.1:18082`. Do not override holds or Jev switches. These are process-only test switches, not committed flag changes or persistent rollout.

The committed service configurations already exclude these Azure auto-configurations; preserve that full list if any test launch override supplies `spring.autoconfigure.exclude`:

- `com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration`
- `com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration`
- `com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration`
- `com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration`
- `com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration`

Await health on each assigned loopback port. With this exact dedicated database verified, set `RAMP_ACCEPTANCE_DATABASE=ramp_wave_20261009` and run `node scripts/verification/ramp-r1-live-acceptance.mjs`. It generates only synthetic records, asserts AC-01 through real quote→pricing→pricebook HTTP, persisted priced-period preview on GET, invalid gap rejection/no extra line, and unchanged-input replay. It writes `ramp-r1-live-acceptance-result.json`. The sentinel is an operator acknowledgement, not remote database identity proof.

Keep process stdout/stderr and result JSON as execution artifacts. Stop only the three recorded acceptance PIDs after completion; no process-name-wide kills or existing container shutdown. Retain the isolated database for evidence unless the orchestrator requests its cleanup. Browser/gateway/identity, submit/approval/accept and fault-injection remain explicitly outside this scoped check.

The executable launcher is `scripts/verification/ramp-acceptance-processes.ps1` with `-Action validate|start|check-api|stop -IntegrationRoot <assembled-worktree>`. It validates memory/artifacts/free ports before launch, creates only the dedicated new database (or resumes one proven wave-created by the same-worktree manifest with all recorded PIDs gone), records process ownership and timestamped logs, and stops only matching PID/start-time/jar/port entries. It forces loopback datasource/listener addresses and inherits the committed Azure exclusion lists. The health preflight uses application readiness, not aggregate dependency health; actual calculate assertions supply cross-service evidence. The launcher and Node acceptance script passed static syntax checks. Root has executed real staged startup and fixture attempts; after startup, invoke check-api immediately before the API script to verify exact owned PID/start-time/jar/port identities and the 0.8GB reserve. Root finally cleanup stops only owned processes and retains the test database. Readiness/RSS samples are recorded in memory.jsonl; no secrets are logged.

## Independent R2 and PDF review

Review found the original PDF fixture's 81,000 `totalAmount` was unrealistic: actual AC-01 quote calculate stores 2,000 initial net amount and separate TCV 81,000. The orchestrator corrected the fixture and template to show initial amount separately from contract TCV/ACV/start ARR/exit ARR. The orchestrator reports four real-renderer tests passed in the integration quote reactor (20.8 seconds): actual Thymeleaf/OpenHTMLtoPDF rendering plus PDFBox extraction, persisted AC-01 rates/totals/contract metrics, literal malicious-name escaping, flat table absence, and stale/unpriced refusal before PDF persistence. This resolves accessor compatibility; it is scoped renderer evidence, not full-stack REST/browser acceptance.

The live script now generates and downloads the real synthetic quote PDF for later text/visual inspection. Its R2 pricing assertion checks period-one 32% discount routes to SALES_VP/PENDING and governance persists after GET. Canonical approval event consumption/supersession is explicitly untested by the three-service scoped environment.

Independent backend review raised approval fingerprints omitting policy/decision context (same monetary schedule with changed governance could retain an old grant), canonical retirement history mapping CANCELLED to null, and the edit-before-recalculate canonical invalidation window. Findings were sent to the backend owner; their final fixes/tests need review before declaring R2 accepted. No backend lane files were modified by this verification lane.

## Current live checkpoint

The owned ramp_wave_20261009 database exists and real quote, pricing and pricebook services have been launched under the bounded process-only configuration. Historical fixture publication and primitive sortOrder payload errors were corrected; actual pricebook JPQL association queries and managed seed DRL imports were repaired with real database regressions. The historical null optional-usage failure was repaired by V8 with real migration regressions. Final real API assertions passed for quote 38, governed quote 39, pricebook 39 and document 2. Persisted AC01 metrics are TCV 81000, ACV 27000, start ARR 24000 and exit ARR 30000, with priced periods on reload. The REST PDF is one page with validated rates/metrics and clean visual layout. All five scoped browser checks passed, with empty pageErrors, consoleErrors and failedResponses. No wave listener remains on 18082/18084/18087/18090 after root-owned cleanup. Preserve earlier baseline 109-test/CRLF evidence as historical; it is not rewritten by these launch-checklist corrections.

Current sequence is validate, start, check-api, synthetic API/PDF script, then browser only if its independent 1GB free-RAM guard passes. Always execute stop in root-owned finally cleanup, validating UTC ticks plus java executable/jar/port identity before targeting a PID. Do not stop any unrelated service, container or browser. Actual PENDING_APPROVAL-status lifecycle is excluded from the three-service fixture: its quote remains DRAFT with pending governance; canonical routing/current request and product/configuration preflight need separate acceptance.

Final affected verification evidence: 908 distinct unit cases have passing evidence, including the final 606-case quote-plus-library rerun (38.302 seconds). Eleven distinct Docker cases pass: three broker/database binding cases, one pricebook JPA case, five managed-seed migration cases and two ramp schedule persistence cases. Frontend evidence is 127 tests, library typechecking and the Angular development build. Historical baseline 109-test results above remain unchanged. This scoped acceptance does not prove gateway/authenticated identity, complete canonical approval delivery plus submit/send/accept, actual PENDING_APPROVAL-status browser edits, full integration suite or production rollout. Browser/chaos is outside CI. Final refreshed graph/head is owned by root and not asserted here.
