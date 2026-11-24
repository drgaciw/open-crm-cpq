# Verify an initial asset activation in CAM

CAM stores the per-line activation receipt in `asset_activation_receipts`. A trusted CAM producer must call
`EffectiveAssetActivation.stage` inside the transaction that establishes the line's activation. It supplies the
stable source activation UUID, canonical contract line UUID, subscription ID, and authoritative activation
date. CAM checks that the active subscription, line, and contract are linked and agree on their tenant,
customer-account, and originating quote evidence. Asset account IDs retain the existing CAM external customer
identifier used by AssetCreated v1.

The receipt snapshots the product, quantity, and overlapping commercial dates. Processing starts on or after
the latest of the source activation date and the line, contract, and subscription start dates. The receipt
keeps the original effective date if processing resumes late within the term. The internal worker uses the UTC
date and commits each due receipt, asset, history fact, AssetCreated outbox row, and audit outbox row in one
transaction. It checks the saved evidence a second time under row locks. An unchanged source activation
can be submitted again and resolves to the original receipt. An attempt to reuse an activation ID or contract
line for a different activation conflicts with the original receipt.

The worker is disabled by default. The legacy writer and S4 stay held. CAM currently has no trusted
producer for this receipt. Attaching one requires proof of authoritative line activation, account/tenant
provenance, and the agreed entitlement sequence. The existing quote acceptance, subscription HTTP
activation, and simulated ERP synchronization each lack that evidence.

## Inspect isolated nonproduction results

Use the CAM schema search path. Set the psql variable `activation_id` to a run-owned source activation ID.

```sql
select r.activation_id, r.contract_line_id, r.subscription_id, r.status, r.reconciliation_reason,
       r.effective_start, r.effective_end, r.asset_id, r.created_event_id,
       a.status as asset_status, h.event_type as history_type,
       created.topic as created_topic, audited.topic as audit_topic
from asset_activation_receipts r
left join assets a on a.uuid = r.asset_id
left join asset_history h on h.event_id = r.created_event_id
left join outbox_events created on created.uuid = r.created_event_id
left join outbox_events audited on audited.aggregate_id = r.asset_id
       and audited.causation_id = r.created_event_id and audited.topic = 'cpq.asset.audit'
where r.activation_id = :'activation_id'::uuid;
```

- `PENDING` before the effective date has no asset, history, or event. A due receipt that is still pending
  can be retried after checking the worker error log and correcting the underlying transaction failure.
- `APPLIED` has one registered AssetCreated identity and corresponding history and audit evidence.
  Check pending delivery or archived publication evidence if relay processing has moved the outbox record.
- `NEEDS_RECONCILIATION` means an asset already exists for the subscription or canonical line, the evidence
  changed, or the receipt's commercial window elapsed. Inventory the linked contract, subscription, and
  actual line alongside retained assets, history, and published events. Record the supporting identities and
  differences before an authorized correction. A historical QuoteAccepted replay does not create a receipt.

This slice covers initial activations with a finite overlapping subscription term. Amendment, replacement,
termination, full ordered lifecycle replay, and operator reconciliation of preexisting acceptances remain
prerequisites for S4. The rollout procedures cover these prerequisites. [Asset write hold](asset-write-hold.md),
[S4 hold](s4-asset-lifecycle-hold.md).
