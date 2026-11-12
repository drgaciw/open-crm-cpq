-- DRG-421 / BC-01-04: PublishCatalog creates an immutable catalog version.
--
-- catalog_versions is the published snapshot header (version id = uuid).
-- catalog_version_products freezes seller-visible product fields at publish time so later
-- draft/live edits do not change what sellers (or pinned Configuration/Pricing reads) see.
--
-- Rollback is manual. Flyway Community does not run undo scripts. Apply in the product schema:
--   DROP TABLE IF EXISTS catalog_version_products;
--   DROP TABLE IF EXISTS catalog_versions;

SET search_path TO product;

CREATE TABLE catalog_versions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    version_number INTEGER NOT NULL,
    tenant_id VARCHAR(128),
    published_at TIMESTAMPTZ NOT NULL,
    published_by VARCHAR(100),
    product_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Postgres treats NULL <> NULL in UNIQUE, so coalesce the null-tenant bucket (same interim as products).
CREATE UNIQUE INDEX uq_catalog_versions_tenant_number
    ON catalog_versions (COALESCE(tenant_id, ''), version_number)
    WHERE NOT is_deleted;

CREATE INDEX idx_catalog_versions_tenant_published
    ON catalog_versions (tenant_id, published_at DESC)
    WHERE NOT is_deleted;

CREATE TABLE catalog_version_products (
    id BIGSERIAL PRIMARY KEY,
    catalog_version_id BIGINT NOT NULL REFERENCES catalog_versions (id),
    product_id BIGINT NOT NULL,
    product_uuid VARCHAR(36) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    product_type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    list_price DECIMAL(19, 2),
    currency VARCHAR(3),
    billing_frequency VARCHAR(20),
    unit_of_measure VARCHAR(50),
    effective_from DATE,
    effective_to DATE,
    is_configurable BOOLEAN NOT NULL DEFAULT FALSE,
    is_bundle BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_catalog_version_products_version_product UNIQUE (catalog_version_id, product_id)
);

CREATE INDEX idx_catalog_version_products_version
    ON catalog_version_products (catalog_version_id);

CREATE INDEX idx_catalog_version_products_sku
    ON catalog_version_products (catalog_version_id, sku);

COMMENT ON TABLE catalog_versions IS
    'Immutable published catalog versions (DRG-421). uuid is the version id carried by CatalogPublished and used as the Configuration/Pricing pin.';
COMMENT ON TABLE catalog_version_products IS
    'Frozen product rows belonging to a catalog version. Seller-facing reads use these snapshots, not live draft products.';
