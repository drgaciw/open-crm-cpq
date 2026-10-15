-- DRG-509 / BC-13-05 — ADR-032 (Accepted 2026-10-15): multi-currency deals with a frozen base amount.
-- Pipeline totals must sum base_amount only; amounts in different currencies are never added together.

ALTER TABLE deals
    ADD COLUMN base_amount          DECIMAL(19,2),
    ADD COLUMN base_currency        VARCHAR(3),
    ADD COLUMN base_weighted_amount DECIMAL(19,2),
    ADD COLUMN fx_rate              DECIMAL(19,10),
    ADD COLUMN fx_rate_as_of        TIMESTAMPTZ,
    ADD COLUMN fx_source            VARCHAR(30);

COMMENT ON COLUMN deals.base_amount IS 'amount converted to base_currency at fx_rate; frozen on amount/currency change and at close (ADR-032 D-03). NULL = not converted.';
COMMENT ON COLUMN deals.fx_rate IS 'base_amount = amount * fx_rate';

-- Interim rate source (ADR-032 acceptance note 2). Operator-loaded; replaced by the BC-03-07 rate service later.
-- Convention: amount_in_to_currency = amount_in_from_currency * rate.
CREATE TABLE fx_rates (
    id            BIGSERIAL PRIMARY KEY,
    from_currency VARCHAR(3)     NOT NULL,
    to_currency   VARCHAR(3)     NOT NULL,
    rate          DECIMAL(19,10) NOT NULL,
    as_of         TIMESTAMPTZ    NOT NULL,
    source        VARCHAR(30)    NOT NULL DEFAULT 'MANUAL',
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fx_rates_positive CHECK (rate > 0),
    CONSTRAINT chk_fx_rates_distinct CHECK (from_currency <> to_currency),
    CONSTRAINT uq_fx_rates_pair_as_of UNIQUE (from_currency, to_currency, as_of)
);

CREATE INDEX idx_fx_rates_lookup ON fx_rates (from_currency, to_currency, as_of DESC);
CREATE INDEX idx_deal_base_currency ON deals (base_currency);

-- Backfill (ADR-032 acceptance notes): deals already in the base currency convert 1:1 with source IDENTITY.
-- Deals in any other currency stay unconverted (NULL) until a rate exists and the deal is next edited or closed.
UPDATE deals
SET base_amount          = amount,
    base_currency        = '${crm_base_currency}',
    base_weighted_amount = weighted_amount,
    fx_rate              = 1,
    fx_rate_as_of        = NOW(),
    fx_source            = 'IDENTITY'
WHERE amount IS NOT NULL
  AND UPPER(TRIM(currency)) = '${crm_base_currency}';
