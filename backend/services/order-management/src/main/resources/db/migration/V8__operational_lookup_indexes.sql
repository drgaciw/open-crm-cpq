CREATE INDEX snapshot_inbox_payload_hash ON snapshot_inbox(payload_hash);
CREATE INDEX outbox_pending_age ON outbox_events(created_at,id) WHERE published=false AND is_deleted=false;
CREATE INDEX outbox_pending_order ON outbox_events(aggregate_id) WHERE published=false AND is_deleted=false;
CREATE INDEX fulfillment_evidence_reference ON fulfillment_evidence(external_reference,order_id);
