# OMS pilot evidence and release gates

Engineering baseline: integration commit `e96d3fa`, branch `feat/oms-integration`,
20 September 2026. Original authorized implementation base:
`8db288016c6da39bea8c29322613718f15faa459`. Final documentation follows this code baseline.
This inventory records locally verified engineering evidence; **production acceptance remains
closed** until the operational gates below pass. Independent specification and code-quality
reviews passed for the integrated pilot work and final deployment artifacts.

The [source requirements](../requirements/order-management-requirements.md) retain their proposed
research status. The [authorized wave](../forge/36-oms-pilot.md) and
[frozen pilot contract](../contracts/oms-pilot-v1.md) define the implemented scope: one deployment
scope, USD cents, integer quantities, approved simple mappings, controlled manual physical,
digital and service evidence, and one-time/monthly/annual charges. The
[requirement matrix](requirement-test-matrix.md) maps concrete tests without upgrading narrow tests
into broader operational claims.

## Available local evidence

| Evidence | What it establishes | Limits |
|---|---|---|
| OMS PostgreSQL suites: `OrderPostgresIntegrationTest`, `ExecutionIntegrationTest`, `FulfillmentIntegrationTest`, `FinanceIntegrationTest`, `ReplacementIntegrationTest`, `RecoveryIntegrationTest` | Durable state, concurrent keys/versions, quantities, leases, evidence, finance acknowledgments and recovery transactions | Synthetic authority evidence; many tests call services with mocked access. They do not exercise a real warehouse, finance system or provisioning provider. |
| `OrderHttpIntegrationTest`, `RecoveryHttpIntegrationTest` | Actual Spring HTTP, signed JWT/JWKS validation and account/scope denial against PostgreSQL | Local test issuer; not the production IdP or browser gateway callback. |
| `BrokerOutboxIntegrationTest` | Actual Kafka acknowledgment gated publication; identical identity/payload on expired-lease replay; stale acknowledgment fencing; failed consumer record retained beyond 15 attempts before ordered recovery | Single local broker, plaintext test boundary. No production quorum, SASL/ACL, broker outage/retention or end-to-end downstream exactly-once guarantee. |
| `RestoreDrillIntegrationTest` | Actual `pg_dump`/`pg_restore` into another database; fresh service instances; backup cutoff, immutable records, disabled intake and unreconciled UNKNOWN work preserved | Small local dataset; finance ledger empty in this scenario. Not infrastructure-loss RPO/RTO, encrypted offsite restore or external-effects reconciliation. See [drill scope](../../backend/services/order-management/restore-drill.md). |
| `RecoverySchedulerTest`, recovery/operations metric tests | Bounded daily batches, same-day catch-up, durable cursor/checkpoints, cached scraping, timeout and stale/failure signals | Scheduler capacity arithmetic is not a throughput benchmark or proof all production orders meet the daily deadline. |
| Angular `orders.components.spec.ts`, `orders.service.spec.ts`, `pending-operations.spec.ts`, `orders-adjudication.spec.ts` | Command bodies, version/permission UI, uncertain-command journal, errors, immutable dispositions | HttpClient mocks and component fixtures; not live backend acceptance. |
| `PublishedLanguageCompatibilityTest` | Additive fields accepted by supported Jackson 2/3 readers, including nested line records | Tested reader versions only; no universal schema-compatibility claim. |

## Final local verification

The final changed-service Maven `verify -Pintegration-tests` run passed: common 99,
security 13, OMS 189, quote 60, contract-asset 38 and gateway 58 tests (457 total), with zero
failures/errors/skips. The separately invoked `OrderLiveBrowserUat` then passed one test in
29.962 seconds against the final code: 458 tests across these scopes including live browser.
An earlier full backend reactor verification passed all 12 service suites. CRM and pricing
were separately rerun with JaCoCo enabled: 85.99% and 84.41% line coverage respectively,
passing both 80% gates. These are local runs, not a published remote CI result.

Additional verified evidence:

- `PilotScenarioIntegrationTest`: 100 concurrent UUID/number-alias conversions produce one
  order/plan; mixed physical/service/monthly/annual lines complete only after their own
  evidence and full exact finance acknowledgments; shipment/cancellation race has one version
  winner and conserves quantities. Actual OMS/PostgreSQL, synthetic authority acknowledgments;
  not a complete deployed quote-to-contract journey.
- `ContractOmsBrokerRetentionIntegrationTest`: actual named OMS listener factory and source
  consumer retain a fatal record beyond ten failures and through consumer restart, even when
  the shared consumer factory enables auto-commit. Registry and trust are mocked; no SASL/ACL
  deployment claim. Quote and contract OMS factories remain isolated from legacy listeners.
- `DbRoleIntegrationTest`: actual PostgreSQL runtime-role allow/deny checks include recovery
  tables and operations, immutable records and published-outbox cleanup. `RecoveryWiringIntegrationTest`
  covers shared command auditing, abandoned operation lookup and adjudicated cancellation.
- Angular: 28 Vitest tests; production CRM build and orders feature lint passed. Nine fixture
  browser scenarios issued ten commands with zero page errors. Fixture responses are mocked.
- Final live browser: real OMS HTTP, signed local JWT/JWKS and PostgreSQL; eleven successful
  POST responses plus a deliberately lost real committed hold response, recovered after reload.
  Partial delivery and partial finance acknowledgment cannot complete the order. Final database
  assertions verify ten delivered, ten acknowledged, applied evidence and command-audit coverage.
  Zero page errors, zero axe violations on the final screen (WCAG 2 A/AA and 2.1 AA tags), and
  one Tab-navigation probe. Manual accessibility and production gateway login remain unverified.
- Actual local `pg_dump`/`pg_restore`: separate restored database, one snapshot, three receipts,
  six audit rows and five pending outbox rows retained; UNKNOWN redispatch blocked. Backup age
  at restore 157 ms and restore duration 295 ms are small functional-drill measurements, **not
  infrastructure-loss RPO/RTO evidence**.
- OMS Docker image built; non-root UID 100 and Java 25 runtime checked. Disabled pilot Compose
  overlay validates; four Promtool alert fixtures pass. No deployment or alert delivery claimed.
- CI contract drift check passes. Jenkins/manual GitHub Actions include explicit OMS/quote/contract,
  orders UI and mandatory fixture/live browser checks with fresh no-skip test-report assertions.
- Complete GitNexus branch comparison: 231 changed files, 1,485 changed symbols, 117 affected
  execution flows, CRITICAL aggregate risk; no partial/truncated results. This broad cross-service impact
  is why staged cutover and deployment gates remain mandatory. The final four artifact changes
  separately report LOW risk. FTS search extension was unavailable; graph analysis completed.

Reproduction commands (from `backend`, Java 25 and Docker available):

```powershell
$env:MAVEN_OPTS='-Xmx512m'
$env:JDK_JAVA_OPTIONS='-Xmx512m'
.\mvnw.cmd -pl services/order-management,services/quote-service,services/contract-asset-management,services/gateway-service -am verify -Pintegration-tests
.\mvnw.cmd -pl services/order-management -am test -Pintegration-tests '-Dtest=OrderLiveBrowserUat' '-Dsurefire.failIfNoSpecifiedTests=false'
```

The browser check additionally requires the pinned frontend dependencies and Playwright Chromium;
`scripts/ci/oms-browser.sh` installs these on the CI agent and requires Docker. The local frontend
uses an existing shared dependency junction; no dependency installation into that junction was done.

Relevant local artifacts: `backend/oms-final-integrated-verify.log`,
`backend/oms-final-live-browser.log`, `backend/oms-all-backend-verify.log`,
`backend/oms-coverage-verify.log`, service `target/surefire-reports/`,
`backend/services/order-management/target/restore-drill-report.json`, and that service's
`target/oms-browser-live.json`, PNG and accessibility JSON. These ignored artifacts remain local;
archive immutable CI links, SHA, environment and reports for the release candidate. Local results
and agent reports do not substitute for the final operational release record.

## Cutover and retention gates

Follow the [operations runbook](../runbooks/oms-pilot.md), with named accountable approvers:

1. Record versions, cohort, offsets and backup point. Apply additive migrations with intake and
   consumers disabled. All users of the common relay need the lease schema; stop every old relay
   binary before new lease claims, because old binaries ignore fences.
2. Deploy and verify the legacy subscription ownership fence on **every active consumer before
   enabling quote cohort ownership or new OMS intake**. Register canonical identities and aliases;
   test delayed legacy deliveries. Quote snapshot, ownership and outbox must commit atomically.
3. Establish authenticated source/activation topic boundaries, shadow-reconcile snapshots and
   terms, then enable selected source consumers, executor/ack consumers and relay independently.
   Enable intake last, only after release gates are approved. Attestation flags are not ACL proof.
4. Rollback disables **new intake**. Existing orders retain OMS ownership and continue visibility,
   drain and reconciliation. Never replay them into the legacy creator or automatically convert
   historical quotes.

Failed Kafka records, including poison envelopes and admission saturation, are retained at their
partition offset and retried at one-second intervals indefinitely. This deliberately stalls that
partition instead of silently skipping accepted work. Deployment must name the responder, alert
on lag/oldest-record age, size retention beyond the recovery window, and prove recovery before
retention expires. Do not reset offsets or drop poison records to manufacture progress. A repaired
producer/source and authorized reconciliation must preserve stable business identity.

Approve snapshot, evidence, audit, broker and backup retention and PII access/deletion policy; no
statutory retention duration is implied. Install and test alert routing, rather than treating sample
Prometheus rules as deployed alarms. Admission limits are high-water backpressure, not a disk quota;
existing accepted work may continue to add outbox records.

## Outstanding operational acceptance

| Gate | Evidence status |
|---|---|
| 100,000 orders, up to 200 lines/order, 25 concurrent UI users, 10 intake requests/second for 30 minutes; p95 reads <=500 ms and command acknowledgment <=1 s | **NOT RUN**. Unit line caps, 100-way conversion and scheduler arithmetic do not substitute. |
| Manual critical-task keyboard and screen-reader verification; WCAG 2.2 AA target | **NOT RUN**. Automated axe and a single keyboard probe do not establish conformance. |
| Real IdP login, gateway OAuth callback/token relay/refresh/logout, denied account claims under HTTPS | **NOT RUN**. Local signed-JWT HTTP tests are narrower. |
| Deployment SASL_SSL, producer/consumer principals, topic ACL positive and negative tests, authenticated activation callbacks | **NOT RUN**. Startup trust checks and local broker tests are narrower. |
| Infrastructure-loss RPO <=15 minutes, RTO <=4 hours; encrypted backup/PITR and authoritative downstream reconciliation | **NOT RUN**. Local functional restore only. |
| 99.5% monthly availability, daily reconciliation at production volume, exception visibility <=60 seconds, alert delivery/escalation and poison-lag recovery within retention | **NOT DEMONSTRATED** on an agreed deployment. |
| Named pilot/fulfillment/finance/security/SRE owners, policy approval, training and real external evidence procedures | **APPROVAL REQUIRED** before launch; test identities are not assigned operational owners. |
| Published release-candidate CI record | **PENDING** remote run and immutable archive. Final local service, live browser and runtime-role checks passed; required CI wiring is committed. Preserve Docker-required tests without silent skipping. |

P1/P2 remain roadmap work: non-quote intake, bundle designer, general cross-line orchestration,
in-flight amendments, distributed sourcing/procurement, payment controls, returns/refunds,
dashboards/notifications, customer portal, AI actions and standards adapters. An unexecuted
replacement request is not an in-flight amendment; evidence adjudication is not a refund or waiver
of fulfillment. Unrelated repository holds remain in force.
