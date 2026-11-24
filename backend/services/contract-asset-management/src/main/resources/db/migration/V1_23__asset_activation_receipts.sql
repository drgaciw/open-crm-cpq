-- Durable initial-activation evidence. Existing assets and acceptance history are left intact.
CREATE TABLE asset_activation_receipts (
    activation_id UUID PRIMARY KEY,
    contract_line_id UUID NOT NULL UNIQUE REFERENCES contract_lines (id),
    subscription_id BIGINT NOT NULL REFERENCES subscriptions (id),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    tenant_id UUID NOT NULL,
    customer_account_id UUID NOT NULL,
    external_customer_id VARCHAR(255) NOT NULL,
    quote_id VARCHAR(255) NOT NULL,
    accepted_line_uuid VARCHAR(64) NOT NULL,
    product_ref VARCHAR(255) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL CHECK (quantity > 0),
    activated_on DATE NOT NULL,
    effective_start DATE NOT NULL,
    effective_end DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reconciliation_reason VARCHAR(40),
    asset_id VARCHAR(36) REFERENCES assets (uuid),
    created_event_id VARCHAR(36) UNIQUE,
    staged_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT chk_asset_activation_term CHECK (
        effective_start >= activated_on AND effective_end >= effective_start
    ),
    CONSTRAINT chk_asset_activation_result CHECK (
        (status = 'PENDING' AND asset_id IS NULL AND created_event_id IS NULL
            AND processed_at IS NULL AND reconciliation_reason IS NULL)
        OR (status = 'APPLIED' AND asset_id IS NOT NULL AND created_event_id IS NOT NULL
            AND processed_at IS NOT NULL AND reconciliation_reason IS NULL)
        OR (status = 'NEEDS_RECONCILIATION' AND asset_id IS NULL AND created_event_id IS NULL
            AND processed_at IS NOT NULL AND reconciliation_reason IS NOT NULL)
    )
);

CREATE INDEX idx_asset_activation_due ON asset_activation_receipts (effective_start, activation_id)
    WHERE status = 'PENDING';
