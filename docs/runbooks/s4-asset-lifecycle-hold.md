# Temporary S4 asset-lifecycle hold

New S4 apply attempts that could mutate an installed asset are temporarily refused
with HTTP 503 and `ASSET_LIFECYCLE_HOLD`. The refusal is audited. The document stays
EXECUTED and pending units are not marked BLOCKED, so the same attempt can be retried
after a verified lifecycle implementation is delivered. An identical, already
completed replay still works. Units containing only actions already outside the S4
slice retain their existing refusal behavior; missing line evidence is held.

The hold exists because acceptance-time asset creation is disabled, while document
apply can otherwise update or terminate existing asset rows immediately even when a
change is effective in the future. Silently skipping the asset hook would lose work
after the commercial ledger commits. This hold is not an implementation of effective
activation, termination, ordering, or reconciliation.

There is no runtime switch to release it. A later code change must deliver and verify
the durable effective-date lifecycle, ordering/replay and reconciliation path before
making the readiness policy return true. To establish the hold in a deployment,
quiesce S4 apply traffic and drain all old replicas before accepting new requests;
mixed-version replicas can otherwise still perform an apply. Existing assets, events,
completed applies and pending outbox rows are not repaired by this hold and require
operator inventory and verified reconciliation. See [asset write hold](asset-write-hold.md).
