-- Service-local capture of one verified, receipt-backed asset history manifest.
-- Source outbox positions survive relay retention. The projection and outbox are separate tables.
CREATE TABLE asset_history_replay_events (
    event_id UUID PRIMARY KEY,
    activation_id UUID NOT NULL REFERENCES asset_activation_receipts (activation_id),
    source_outbox_id BIGINT NOT NULL UNIQUE CHECK (source_outbox_id > 0),
    topic VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    effective_date DATE NOT NULL,
    payload TEXT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_asset_history_replay_order
    ON asset_history_replay_events (activation_id, effective_date, source_outbox_id);
