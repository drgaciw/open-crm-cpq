-- DRG-526 / BC-03-13: the Finance cost basis (finance decisions F-1, F-2, F-3).
--
-- cost_basis_imports is the audit trail of every accepted export file: who ingested it (the verified principal,
-- ADR-016 step 4), which export version and approver it carried, and how many rows were accepted or rejected.
--
-- cost_basis_rows is append-only and effective-dated. A row is never updated or deleted:
--   * re-ingesting the same version of a key (product, component, effective date) is a no-op, enforced by uq_cost_basis_row;
--   * a higher version is a new row, and the lookup takes the highest version per key, so it replaces the earlier one
--     while the earlier one stays on record for audit and replay.
SET search_path TO pricing;

CREATE TABLE cost_basis_imports (
    id BIGSERIAL PRIMARY KEY,
    export_version BIGINT,
    approved_by VARCHAR(255),
    ingested_by VARCHAR(255) NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    rows_received INTEGER NOT NULL,
    rows_accepted INTEGER NOT NULL,
    rows_inserted INTEGER NOT NULL,
    rows_unchanged INTEGER NOT NULL,
    rows_superseded INTEGER NOT NULL,
    rows_rejected INTEGER NOT NULL,
    rows_unit_unchecked INTEGER NOT NULL,
    CONSTRAINT ck_cost_basis_import_counts CHECK (
        rows_received = rows_accepted + rows_rejected
        AND rows_accepted = rows_inserted + rows_unchanged + rows_superseded)
);

CREATE INDEX idx_cost_basis_import_ingested_at ON cost_basis_imports(ingested_at);
CREATE INDEX idx_cost_basis_import_version ON cost_basis_imports(export_version);

CREATE TABLE cost_basis_rows (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES cost_basis_imports(id),
    product_external_id VARCHAR(255) NOT NULL,
    component VARCHAR(30) NOT NULL,
    billing_unit VARCHAR(100) NOT NULL,
    unit_cost NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    effective_from DATE NOT NULL,
    version BIGINT NOT NULL,
    approved_by VARCHAR(255) NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL,
    ingested_by VARCHAR(255) NOT NULL,
    CONSTRAINT uq_cost_basis_row UNIQUE (product_external_id, component, effective_from, version),
    CONSTRAINT ck_cost_basis_row_component CHECK (
        component IN ('DIRECT', 'COST_TO_SERVE', 'USAGE_UNIT', 'LABOUR', 'PASS_THROUGH')),
    CONSTRAINT ck_cost_basis_row_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_cost_basis_row_version CHECK (version > 0)
);

CREATE INDEX idx_cost_basis_row_lookup ON cost_basis_rows(product_external_id, effective_from DESC);
CREATE INDEX idx_cost_basis_row_import ON cost_basis_rows(import_id);
