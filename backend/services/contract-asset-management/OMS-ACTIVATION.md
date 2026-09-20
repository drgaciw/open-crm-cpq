# OMS source ownership and activation

Both Kafka consumers are disabled by default. Enable `oms.source-consumer-enabled` to ingest
`oms.quote-accepted.v1` into an immutable source registry. Enable `oms.activation-consumer-enabled`
only after source ownership is registered and legacy cutover reconciliation is complete.
Unknown sources never create subscriptions. Missing source events can be replayed from the quote outbox.

Before either consumer can start, configuration requires:
- `oms.trusted-producer-configured=true` (operator attestation of deployed broker ACLs).
- `spring.kafka.properties.security.protocol=SASL_SSL` with deployment-provided SASL credentials.
- `oms.quote-producer-principal` naming the principal exclusively allowed to WRITE `oms.quote-accepted.v1`.
- For activation, `oms.activation-producer-principal` naming the principal exclusively allowed to WRITE
  `oms.activation-requested.v1`.
- Explicit `oms.trusted-source-scopes` allowlist and consumer READ/group ACLs.

The application checks prerequisites; it cannot prove broker ACLs remotely. The attestation must
follow actual broker configuration/verification. Never enable it to bypass missing production auth.
Kafka payload scope alone is not producer authentication. No test success asserts deployed broker security.

Registered payload hash, canonical UUID, all observed quote-number aliases, revision, customer and
exact source line terms are immutable. Legacy consumer and source/activation consumers share database
advisory locks on quote identities. Existing subscriptions without a persisted OMS operation, including
those under any registered alias, require reconciliation before OMS activation. New commercial revisions
with prior OMS activations are held for explicit subscription reconciliation; no in-flight amendment path.

Recurring activation requires canonical quote UUID, integer positive quantity, nonnegative decimal USD
amount strings of at most two fractional digits, reconciled line economics and strictly ordered dates.
Monthly net charge annualizes by twelve; annual charge remains unchanged. Operation/inbox/subscription/
acknowledgment outbox persist in one database transaction. No external success identifiers are synthesized.

PostgreSQL tests exercise duplicate and concurrent deliveries, legacy alias overlap, immutable registry,
unregistered/different source lines and acknowledgment-insert rollback. They do not exercise real Kafka
broker ACLs or production operational acceptance.
