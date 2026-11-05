-- ADR-018 D2/D3/D4 (expand step) for order-management. The shared OutboxEvent entity (libs/common) maps status and the
-- D4 event envelope columns. V1 created outbox_events without them, so Hibernate schema validation fails at boot
-- ("missing column [actor] in table [outbox_events]"). Same additive pattern the other services use:
-- *_outbox_relay_status_and_leasing.sql + *_outbox_status_skipped.sql (status) and *_outbox_event_envelope_columns.sql.
-- Idempotent (IF NOT EXISTS); the other columns are nullable with no default, so PostgreSQL adds them without a rewrite.
-- status:          PENDING until the broker acknowledges, then PUBLISHED; FAILED at the retry limit; SKIPPED is set by an
--                  operator with the runbook SQL for a row that is deliberately not sent.
-- topic:           the Kafka topic the relay sends to (event_type keeps holding it too; the relay falls back to event_type).
-- event_version:   the registry contract version (EventContractRegistry).
-- correlation_id:  the chain the event belongs to; the event's own uuid when it starts one.
-- causation_id:    the id of the event that caused this one, when a consumer published it.
-- actor:           who caused it: user:<sub> or service:<name> (ADR-016).
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
UPDATE outbox_events SET status = 'PUBLISHED' WHERE published = TRUE AND status <> 'PUBLISHED';
-- An unpublished row whose retries are exhausted (exhausted_at set by OutboxDeliveryStore) is FAILED, not PENDING.
UPDATE outbox_events SET status = 'FAILED' WHERE published = FALSE AND exhausted_at IS NOT NULL AND status <> 'FAILED';
ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS ck_outbox_events_status;
ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'SKIPPED'));
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS topic VARCHAR(255);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS event_version INTEGER;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(100);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS causation_id VARCHAR(100);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS actor VARCHAR(255);
