-- ADR-018 D6/D8: consumer inbox for crm-service. Quote acceptance arrives on two channels during the D8 dual run
-- (Redis quote:accepted and Kafka cpq.quote.status.accepted); both claim the quote UUID here, in the same transaction
-- as the deal update, so a deal is marked WON once. Replaces the Redis SET NX keys crm:quote-accepted:processed:*.
CREATE TABLE IF NOT EXISTS processed_events (
    consumer_group VARCHAR(100) NOT NULL,
    event_id       VARCHAR(200) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_group, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events (processed_at);
