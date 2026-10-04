-- R1-QUOTE: per-line ramp schedule. Flag stays off. Explicit periods only.
SET search_path TO quote;

ALTER TABLE quotes
    ADD COLUMN exit_arr DECIMAL(19,2),
    ADD COLUMN ramp_schedule_stale BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE quote_line_items
    ADD COLUMN ramp_escalator_start_price DECIMAL(19,4),
    ADD COLUMN ramp_escalator_mode VARCHAR(20),
    ADD COLUMN ramp_escalator_amount DECIMAL(19,4),
    ADD COLUMN ramp_escalator_every_months INT,
    ADD CONSTRAINT chk_quote_line_items_ramp_escalator_mode
        CHECK (ramp_escalator_mode IS NULL OR ramp_escalator_mode IN ('PERCENT', 'AMOUNT'));

CREATE TABLE quote_ramp_steps (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    line_item_id BIGINT NOT NULL REFERENCES quote_line_items(id) ON DELETE CASCADE,
    sequence INT NOT NULL,
    start_month INT NOT NULL,
    months INT NOT NULL,
    quantity INT NOT NULL,
    price_basis VARCHAR(30) NOT NULL,
    unit_price DECIMAL(19,4),
    discount_fraction DECIMAL(10,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_quote_ramp_steps_line_sequence UNIQUE (line_item_id, sequence),
    CONSTRAINT chk_quote_ramp_steps_months CHECK (months >= 1),
    CONSTRAINT chk_quote_ramp_steps_start CHECK (start_month >= 1),
    CONSTRAINT chk_quote_ramp_steps_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_quote_ramp_steps_basis CHECK (
        (price_basis = 'ABSOLUTE' AND unit_price IS NOT NULL AND discount_fraction IS NULL)
        OR (price_basis = 'DISCOUNT_FRACTION' AND discount_fraction IS NOT NULL AND unit_price IS NULL)
    )
);

CREATE INDEX idx_quote_ramp_steps_line_item_id ON quote_ramp_steps(line_item_id);
CREATE INDEX idx_quote_ramp_steps_is_deleted ON quote_ramp_steps(is_deleted);
