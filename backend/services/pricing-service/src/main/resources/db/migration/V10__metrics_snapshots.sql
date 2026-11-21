SET search_path TO pricing;

-- PD2: on-demand metrics history. Formulas stay in MetricsCalculator; this table stores outputs only.
-- IF NOT EXISTS keeps V9 replay tests (drop history, remigrate) from failing on a leftover table.
CREATE TABLE IF NOT EXISTS metrics_snapshots (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    customer_external_id VARCHAR(255) NOT NULL,
    quote_external_id VARCHAR(255),
    arr DECIMAL(19,2),
    tcv DECIMAL(19,2),
    acv DECIMAL(19,2),
    clv DECIMAL(19,2),
    churn_score INTEGER,
    discount_rate DECIMAL(19,4),
    total_discount DECIMAL(19,2),
    snapshot_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_metrics_snap_customer ON metrics_snapshots (customer_external_id, snapshot_at DESC);
