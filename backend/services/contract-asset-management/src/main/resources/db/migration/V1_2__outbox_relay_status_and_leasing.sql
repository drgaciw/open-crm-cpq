-- ADR-018 D2/D3: relay status, retry backoff and leasing indexes for outbox_events. Additive only.
-- status:          PENDING until the broker acknowledges, then PUBLISHED; FAILED once the retry limit is reached.
-- next_attempt_at: earliest retry time after a failed attempt (NULL = due now).
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;

UPDATE outbox_events SET status = 'PUBLISHED' WHERE published = TRUE AND status <> 'PUBLISHED';

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));

-- Lease scan: due PENDING rows in id order (FOR UPDATE SKIP LOCKED).
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (id) WHERE status = 'PENDING';
-- Per-aggregate ordering check: is there an older unpublished row for the same aggregate?
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished_aggregate
    ON outbox_events (aggregate_type, aggregate_id, id) WHERE status <> 'PUBLISHED';
