# Manual fulfillment boundary

`EvidenceService.record(orderId, EvidenceCommand)` accepts authenticated operator facts. It joins an existing transaction, locks the order, checks the expected version, and persists evidence, quantities, executor changes, audit and outbox atomically. It does not reserve stock in an external inventory system.

The canonical `/evidence` multiplexer can delegate non-finance commands here. The optional `/fulfillment-evidence` endpoint and read-only ledger are provided by `FulfillmentController`. Receipts contain `orderId`, `evidenceId`, `status`, and current `version`. APPLIED returns 200; retained PENDING or CONFLICT facts return 202. Stale versions and conflicting operation-key bodies return 409 without applying the command.

SHIPMENT and RELEASE require `allocationReference`. DELIVERY requires `shipmentReference`. Allocation references are the allocation event's external reference. A shipment may consume only that lot's unreleased quantity, and its occurrence must precede an explicit expiry. Late receipt of a historical, pre-expiry shipment is distinct from authorizing a new shipment after expiry. Expiry detection opens an exception; only explicit operator release changes quantities.

Physical `fulfilled` means shipped; delivery is tracked separately. `ordered = cancelled + fulfilled + remaining`; allocated cannot exceed remaining. Duplicate external identities do not count twice. Contradictory references stay in the immutable evidence ledger with an exception. Pending deliveries are applied only once their exact shipment reference is verified. Any retained fact advances SUBMITTED to IN_PROGRESS and closes the source-replacement window.

Call `reconcilePending(orderId)` under an authorized evidence/recovery workflow; it requires `oms:evidence`. Allocation expiry monitoring is enabled only with `oms.execution.enabled` and uses `oms.execution.allowed-scopes`. Normal APIs require per-account `oms:evidence` or `oms:read`.

Finance acknowledgments remain the finance service's responsibility. Fulfillment evidence cannot complete an order without all executor obligations and finance acknowledgments.
