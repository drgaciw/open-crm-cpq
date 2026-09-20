# Manual finance pilot boundary

`FinanceService.prepare(orderId,lineId,Command)` requires `oms:finance`, optimistic version,
reason and an operation key. The line must have no remaining fulfillment quantity, all shipments
must be delivered, and the pinned FINANCE_ACK task dependencies must have succeeded. Holds prevent
new requests. Fully cancelled lines have no invoice obligation. There is no automatic scheduler.

POST `/api/v1/orders/{id}/finance-requests` takes the standard command plus `lineId`.
GET on the same path returns requestKey, lineId, quantity, acknowledgedQuantity, status and frozen
payload. POST `/finance-acknowledgments` takes the standard command plus lineId, positive quantity,
externalReference, ISO occurredAt, optional requestKey and required outcome:
ACKNOWLEDGED, REJECTED or UNKNOWN. The canonical FINANCE_ACK evidence adapter sets ACKNOWLEDGED.

A SHA-256 key of the encoded scope, canonical quote, accepted revision, source line and FINANCE
identifies one immutable request per line. A request and outbox event commit with audit and command
receipt. The order becomes finance REQUESTED; the request remains PENDING until external evidence.
Replaying or repeating preparation never emits another finance request. Unknown or rejected work
must reconcile the existing key, and cannot create a new dispatch identity.

All amounts are integer USD cents. Eligible quantity excludes cancellation. For line-level discount
and tax, the eligible amount is floor(source cents * eligible quantity / accepted quantity).
Acknowledgment slices use differences of cumulative floor(request cents * acknowledged quantity /
request quantity); the final slice receives the remainder. Gross minus discount plus tax gives total.
The frozen payload retains the original source line, billing frequency/dates, tax evidence, snapshot
hash and completed milestone references; it never substitutes an order total for a line charge.

Each immutable acknowledgment binds scoped external reference to request, quantity, outcome and
time. Altered reference reuse conflicts; command identity also binds the entire command body.
Only ACKNOWLEDGED quantity counts. Partial evidence retains PARTIAL; prior unknown/rejected balances
remain held until fully acknowledged. Rejection/unknown creates an exception. Full acknowledgment
calls the existing executor in the same transaction, so final completion requires every obligation.
Contradictory later outcomes remain in history and create an exception without erasing proven facts.

This is attributable manual recording of actual external finance evidence, not a production ERP
adapter or proof that a remote invoice was created. Broker ACLs, external finance permissions,
operator procedures and end-to-end browser/provider acceptance remain deployment prerequisites.
The PostgreSQL tests require Docker and carry the integration tag; they do not silently skip it.
