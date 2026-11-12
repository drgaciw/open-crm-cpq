-- ADR-018 D6: consumer inbox. One row per (consumer group, event) processed in this schema, written in the same
-- transaction as the consumer's side effect, so a redelivered or re-sent event is recognised and skipped.
-- event_id is the record's eventId header (outbox row UUID), or topic-partition@offset when a record has none.
-- No consumer exists yet in this service; the table is created for parity with every other service's baseline.
CREATE TABLE IF NOT EXISTS processed_events (
    consumer_group VARCHAR(100) NOT NULL,
    event_id       VARCHAR(200) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_group, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events (processed_at);
