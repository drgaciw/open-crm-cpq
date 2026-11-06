-- DRG-665 AC3. Nullable subscriptions.contract_line_id in a separate migration from V1_16.
-- Expand-only: existing rows stay NULL. quote_id NOT NULL is not relaxed (AC4).
-- QuoteAccepted path does not populate this column in this story (AC5).
-- Rollback: db/rollback/V1_17__drop_subscriptions_contract_line_id.sql

ALTER TABLE subscriptions
    ADD COLUMN contract_line_id uuid REFERENCES contract_lines (id);

CREATE INDEX idx_subscriptions_contract_line_id ON subscriptions (contract_line_id);
