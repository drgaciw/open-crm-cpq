-- Submit-only idempotency. Keep receipts indefinitely until a separately designed retention policy exists.
-- A receipt, canonical approval and frozen first response share the routing transaction.
CREATE TABLE approval_submit_receipts (
    key_hash VARCHAR(64) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
