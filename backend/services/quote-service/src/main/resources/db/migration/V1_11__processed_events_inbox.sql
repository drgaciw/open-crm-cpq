-- DRG-544 / DRG-540. quote-service inbox (ADR-018 D6) and the commercial-decision audit for a
-- system-created renewal draft. DRG-546 owns V1_12 (continuation operation column). DRG-564 owns the
-- general continuation pricing-decision trail; this table records only the decision to open the draft.
--
-- Rollback is manual. Flyway Community does not run undo scripts. Apply in the quote schema, in order:
--   DROP TABLE IF EXISTS commercial_decision_audit;
--   DROP INDEX IF EXISTS idx_processed_events_processed_at;
--   DROP TABLE IF EXISTS processed_events;
-- Quotes, lines and outbox rows created from a draft are not dropped: cancelling the draft is the
-- business reversal. The inbox rows are safe to drop; a later redelivery will claim again and the
-- live-renewal check will refuse a second quote for the same window.

-- ADR-018 D6: one row per (consumer group, event) processed in this schema, written in the same
-- transaction as the consumer's side effect. event_id is the record's eventId header (outbox row UUID),
-- or topic-partition@offset when a record has none. A stable publisher dedupe key is claimed as
-- dedupe:<key> in the same table.
CREATE TABLE IF NOT EXISTS processed_events (
    consumer_group VARCHAR(100) NOT NULL,
    event_id       VARCHAR(200) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_group, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events (processed_at);

-- Append-only. One row per system-created renewal draft. Sellers do not update it.
CREATE TABLE commercial_decision_audit (
    id              BIGSERIAL PRIMARY KEY,
    quote_id        BIGINT NOT NULL REFERENCES quotes (id),
    quote_number    VARCHAR(50) NOT NULL,
    decision_kind   VARCHAR(64) NOT NULL,
    decided_by      VARCHAR(100) NOT NULL,
    policy_id       VARCHAR(100) NOT NULL,
    operation       VARCHAR(32) NOT NULL,
    mode            VARCHAR(64) NOT NULL,
    subscription_id VARCHAR(200) NOT NULL,
    event_id        VARCHAR(200),
    inputs_json     TEXT NOT NULL,
    outputs_json    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commercial_decision_audit_quote ON commercial_decision_audit (quote_id);
CREATE INDEX idx_commercial_decision_audit_subscription
    ON commercial_decision_audit (subscription_id, decision_kind);
