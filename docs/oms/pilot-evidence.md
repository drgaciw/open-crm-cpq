# OMS pilot evidence and release gates

Evidence baseline: integration commit `ad896d5`, documentation branch
`feat/oms-evidence-docs`, 20 September 2026. Original authorized implementation base:
`8db288016c6da39bea8c29322613718f15faa459`. This is an evidence inventory, **not production
acceptance or a claim that all engineering work is complete**. Later integration changes need their
own results. No tests were rerun for this documentation-only change.

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

Reviewed local reports from the workstream checkouts include 39 passing recovery tests (34
PostgreSQL, one real JWT/HTTP, four unit), two real-broker tests plus two retention-policy unit
tests, and a passing local restore drill. These are scoped workstream results, not a consolidated
release-candidate CI result. Test names in the matrix establish traceability; an entry alone does
not assert a fresh passing run at this exact baseline.

Archive immutable CI links, commit SHA, environment, command, test counts/failures/skips and
Surefire/Vitest reports for the release candidate. Relevant artifacts are service
`target/surefire-reports/`, `target/restore-drill-report.json`, and browser JSON/screenshots/logs.
Local ignored artifacts and agent reports are insufficient as the final release record.

At this baseline the separately reviewed DB runtime-role hardening and live-browser/CI follow-ups
are **not included**. A post-baseline, not-yet-committed `OrderLiveBrowserUat` run was inspected:
one test passed, zero failures/errors/skips, 36.61 seconds. Its `oms-browser-live.json` records 11
POST responses plus a separately faulted real committed hold response, recovered after reload;
zero page errors; zero axe violations on the final screen (WCAG 2 A/AA and 2.1 AA tags); and one
Tab-navigation probe. Actual OMS HTTP/JWT/PostgreSQL uses synthetic manual authority evidence.
The coordinator also reports intermediate partial-delivery/finance checks and final database
assertions for ten delivered, ten acknowledged, all evidence applied and command-audit coverage.
Archive these artifacts with the final commit before treating this as release-candidate evidence.
Fixture browser runs remain separate. The runtime-role follow-up must run against the complete
migration set, including recovery tables.

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
| Consolidated final CI, live browser acceptance and runtime least-privilege verification on the final SHA | **PENDING** release record. Post-baseline live browser passed locally; final commit/CI association still pending. Preserve Docker-required tests without silent skipping and include the new Nx project explicitly. |

P1/P2 remain roadmap work: non-quote intake, bundle designer, general cross-line orchestration,
in-flight amendments, distributed sourcing/procurement, payment controls, returns/refunds,
dashboards/notifications, customer portal, AI actions and standards adapters. An unexecuted
replacement request is not an in-flight amendment; evidence adjudication is not a refund or waiver
of fulfillment. Unrelated repository holds remain in force.
