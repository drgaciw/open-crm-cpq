# Ordered CAM asset history replay evidence

CAM can capture an immutable set of registered lifecycle events for one applied initial activation
receipt. This is an internal reconciliation prerequisite. It uses the retained CAM outbox identity and
row position, which the relay uses to preserve per-aggregate ordering. Capture requires an authorized,
independently reviewed manifest containing the exact lifecycle event UUIDs in producer order. Review
the full publication record and resolve retention gaps before relying on a manifest. [Event delivery
ADR](../adr/ADR-018-event-delivery-guarantees-idempotency.md), [Asset reconciliation inventory](asset-reconciliation-inventory.md).

The internal `OrderedAssetHistoryReplay.capture` service checks the manifest against retained
PUBLISHED lifecycle rows for the receipt's asset. It verifies the original creation against the
receipt's real line, account, subscription, product, quantity and term. Amendment and termination
must retain the asset and subscription and refer to a real line for the same account. Supersession
requires a distinct receipt-backed successor for the same account and tenant, with an earlier
published creation record and a receipt term covering the supersession date. A published or captured
closure effective on or before that date prevents the successor from qualifying. A manifest that
would reopen a closed asset when sorted by effective date and then producer position requires
reconciliation. This includes a future amendment published before a backdated closure. The exact
event bodies and original outbox positions are written in one
transaction to `asset_history_replay_events`. Audit and other non-lifecycle events stay in their
existing operational records.

Capture accepts an identical manifest again, including after outbox retention. Any different manifest
conflicts with the existing capture. This records one reconciliation snapshot per activation. Retain
the manifest source, approval, and comparison with the historical publication record with the
operator's reconciliation evidence. During a future authorized capture, settle source activity for the
asset so the review and capture can use the same historical boundary.

`OrderedAssetHistoryReplay.historyThrough` returns the verified events effective on or before the
requested date. Effective dates sort first and original producer positions break same-day ties. The
retained payloads can reconstruct the selected history after relay cleanup. This service writes the
journal only. The installed-base projection stays under the existing CAM write path.

The Docker integration tests use synthetic canonical CAM receipts and the registered v1 publishers.
They simulate the outbox's PUBLISHED status in the test database and exercise the ordering and
retention behavior. Production replay still requires an approved operator entry point, a source for
events already removed from the outbox, and verification against real historical records. Release
of S4 additionally needs a trusted effective-dated amendment and termination producer, decisions
on missing assets and replacement lineage, and validated reconciliation. [S4 asset lifecycle hold](s4-asset-lifecycle-hold.md),
[Asset write hold](asset-write-hold.md).
