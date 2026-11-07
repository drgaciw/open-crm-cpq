-- DRG-665 (S-T4). Canonical contract_lines origin for quote → contract line → subscription/asset.
-- Next free CAM Flyway slot at launch (main tip was V1_15). Not reserved.
-- tenant_id is stamped from contracts.tenant_id by trigger; never trusted from a caller body.
-- QuoteAccepted path is unchanged in this story; no writers wired here.
-- Rollback: db/rollback/V1_16__drop_contract_lines.sql

CREATE TABLE contract_lines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    contract_id uuid NOT NULL REFERENCES contracts (id),
    line_no integer NOT NULL,
    accepted_line_uuid varchar(64) NOT NULL,
    quote_id varchar(255),
    product_external_id varchar(255) NOT NULL,
    product_name varchar(255),
    quantity numeric(19, 4) NOT NULL DEFAULT 0,
    status varchar(40) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    charge_type varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_contract_lines_contract_line_no UNIQUE (contract_id, line_no),
    CONSTRAINT uq_contract_lines_tenant_accepted_line UNIQUE (tenant_id, accepted_line_uuid),
    CONSTRAINT chk_contract_lines_line_no CHECK (line_no >= 1),
    CONSTRAINT chk_contract_lines_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUPERSEDED', 'TERMINATED')),
    CONSTRAINT chk_contract_lines_charge CHECK (charge_type IN ('RECURRING', 'ONE_TIME', 'USAGE')),
    CONSTRAINT chk_contract_lines_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_contract_lines_quantity CHECK (quantity >= 0)
);

CREATE INDEX idx_contract_lines_contract ON contract_lines (contract_id);
CREATE INDEX idx_contract_lines_tenant ON contract_lines (tenant_id);
CREATE INDEX idx_contract_lines_accepted_line ON contract_lines (accepted_line_uuid);

CREATE OR REPLACE FUNCTION contract_lines_stamp_tenant_from_contract()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    contract_tenant uuid;
BEGIN
    SELECT tenant_id INTO contract_tenant
      FROM contracts
     WHERE id = NEW.contract_id;
    IF contract_tenant IS NULL THEN
        RAISE EXCEPTION 'contract_lines.contract_id % has no parent contract', NEW.contract_id
            USING ERRCODE = '23503';
    END IF;
    -- AC1: tenant_id is taken from the contract, never from a body / supplied value.
    NEW.tenant_id := contract_tenant;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_contract_lines_stamp_tenant
    BEFORE INSERT OR UPDATE OF contract_id, tenant_id ON contract_lines
    FOR EACH ROW
    EXECUTE FUNCTION contract_lines_stamp_tenant_from_contract();
