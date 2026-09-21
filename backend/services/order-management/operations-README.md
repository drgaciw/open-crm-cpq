# OMS bounded admission and operational visibility

## Integration hooks (coordinator owned)

Inject `IntakeAdmission` into the order service. Its constructor takes JdbcTemplate, configured
`oms.scope-id`, `oms.admission.max-payload-bytes` (default 1048576, maximum 16777216), and
`oms.admission.max-pending-outbox` (default 10000, maximum 1000000).

For a NEW trusted accepted snapshot, run `checkPayload(payload)` before materializing its DTO and
`checkNewIntake()` before insertion. For NEW order conversion, run `checkNewIntake()` after exact
existing-receipt/order detection, inside the normal command transaction. Existing matching durable
receipts must still replay even if limits have subsequently tightened; existing-order fulfillment,
finance, recovery and outbox drain do not call admission. Never accept a client-provided replay flag.
The immutable snapshot validator enforces 1..200 lines.

`checkPayload` measures UTF-8 bytes, not characters; invalid/oversized payloads return a 422 semantic
failure. `checkNewIntake` performs a scope-bound pending-outbox count limited to its configured
threshold with a five-second statement timeout. At/above threshold it throws
`IntakeAdmission.Saturated` (OrderProblem status 503). This is a retryable high-water signal, not a
commercial validation failure. It changes no durable state. Broker error handling must retain and
retry a saturated delivery, never silently acknowledge/drop it. This is admission backpressure,
not a strict physical disk quota: concurrent admissions and existing-order work may cross the
threshold. Configure database capacity and broker retention separately; never discard accepted work.

## Cached metrics

`OperationalMetrics` uses the existing Micrometer core from Actuator. Add the Prometheus registry
and expose its endpoint in the deployment configuration. It polls every `oms.operations.poll-ms`
(default 30000), with a `oms.operations.query-timeout-seconds` statement bound (default 5, max 30).
Counts stop at `oms.operations.scan-limit` (default 100000, max 1000000); the limit gauge makes these
lower bounds explicit. No customer, actor, order, payload or external reference becomes a metric tag.
An empty deployment scope reads no database and reports configured=0.

Gauges: oms.outbox.pending; oms.outbox.exhausted; oms.outbox.oldest.pending.seconds;
oms.tasks.unknown; oms.tasks.overdue (expired claims/waiting acknowledgments);
oms.finance.exceptions.open; oms.operations.configured; oms.operations.sample.age.seconds;
oms.operations.refresh.failures; oms.operations.scan.limit. All values are replaced as one cached
sample. Scraping never executes SQL. Failed polls retain the last sample and advance the failure
counter; sample age exposes staleness (NaN until the first successful sample).

The rule sample in operations-alerts.yml is a deployment artifact to copy into the approved
Prometheus rules location, not an installed alarm. Alert ownership/routing and evidence of firing
remain required. These components do not establish the 99.5% SLO, 100000-order latency target,
RPO/RTO, backup restore or infrastructure recovery. Demonstrate these in the agreed environment.

Validation covers real PostgreSQL scope isolation, admission without deletion, UTF-8 boundary,
201-line intake rejection, metric caps, no scrape queries and retained samples on polling failure.
