# Local functional restore drill

`RestoreDrillIntegrationTest` requires Docker and a real PostgreSQL 16 container. It is tagged
integration and does not skip when Docker is absent. Run from backend with:

    ./mvnw.cmd -pl services/order-management -am test -Dtest=RestoreDrillIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false

The test uses only fictional contract-fixture data. It creates an accepted snapshot/order, durable
conversion/submit/evidence receipts, a partial manual allocation, and a shipment operation whose
outcome is UNKNOWN. No broker, inventory provider, ERP, subscription writer or remote acknowledgment
is connected; the test never fabricates shipment, delivery or finance success.

It executes `pg_dump --format=custom` inside the ephemeral container, creates a DIFFERENT database,
and runs `pg_restore --exit-on-error` there. Original and restored databases coexist. A source-only
command deliberately committed AFTER the backup is absent from restore, proving the backup cutoff
and database isolation. Fresh datasource, transaction manager and service instances then read the
restored database, without reusing original database connections or in-memory service state.

Assertions compare backed-up snapshot bytes/hash, order and line quantities, all task/operation
state, immutable audits and command receipts, pending outbox records, allocation lots, evidence
identities/states/receipts, exceptions and empty finance ledgers. The additive command journal is
also compared when that schema has been integrated. Restored immutability triggers reject snapshot
edits and audit deletion. With intake disabled, matching conversion and submit receipts still replay,
a new conversion is rejected, and UNKNOWN work cannot be claimed, retried without authoritative
no-effect proof, or dispatched using its old lease. Pending outbox rows are never marked published.

A successful run writes `target/restore-drill-report.json` with backup start/end, backup age at restore,
restore elapsed milliseconds and retained-row counts. CI should archive this alongside Surefire
results. It is LOCAL FUNCTIONAL evidence, not a production RPO/RTO benchmark: one disposable local
server, tiny data set, local disk, no encrypted offsite backup, WAL/PITR, infrastructure failure,
external reconciliation, real identity provider, or production restore privileges are exercised.
Only transactions represented by the backup are promised; the deliberate later source-only command
is excluded. Named operators must still demonstrate agreed production restore/disable-intake drills.

## Follow-up real Kafka acknowledgment test (not implemented here)

Use a pinned Kafka Testcontainer plus PostgreSQL and the actual OutboxRelayPublisher,
OutboxDeliveryStore and KafkaTemplate (`acks=all`), with a unique test topic and consumer group.
Run no fulfillment/finance adapters. Test broker acknowledgment before asserting published=true;
consume the exact event UUID and payload. Use a controlled network proxy outage to make sends fail,
verify pending durable rows/backoff and replay after recovery. Do not replace Kafka futures with
completed mock futures in this test.

For the ambiguous crash window, allow a real broker acknowledgment then fault only the database
acknowledgment write. Reconstruct the relay, expire the abandoned lease, and replay the same event
identity; the receiver's actual durable inbox must apply it once even if Kafka delivers twice.
Verify stale lease tokens cannot publish/clear another worker's claim, and failed broker delivery
never becomes published. Capture the broker record and database receipt assertions as artifacts.
A single-broker container proves API acknowledgment handling, not production quorum durability,
SASL_SSL/ACL isolation, retention or disaster recovery; those remain separate deployment tests.
