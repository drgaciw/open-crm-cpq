-- ADR-018 D4 (expand step): event envelope columns on outbox_events. Additive only: every column is nullable and has
-- no default, so PostgreSQL adds them without rewriting the table, and code from before D4 keeps working.
-- topic:          the Kafka topic the relay sends to. event_type keeps holding the topic too until the D4 contract
--                 step switches it to the registry event type; the relay reads topic and falls back to event_type.
-- event_version:  the registry contract version (EventContractRegistry).
-- correlation_id: the chain the event belongs to; the event's own uuid when it starts one.
-- causation_id:   the id of the event that caused this one, when a consumer published it.
-- actor:          who caused it: user:<sub> or service:<name> (ADR-016).
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS topic VARCHAR(255);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS event_version INTEGER;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(100);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS causation_id VARCHAR(100);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS actor VARCHAR(255);
