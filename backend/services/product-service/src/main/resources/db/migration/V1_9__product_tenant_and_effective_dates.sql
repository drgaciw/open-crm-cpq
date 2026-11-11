-- DRG-418 / BC-01-01: Define and revise a Product with Sku, UnitOfMeasure, LifecycleState and
-- EffectiveDateRange.
--
-- tenant_id follows the quote-service V1_13 interim (DRG-632): the caller's Entra tid, immutable, null for
-- service tokens, anonymous callers, dev mode, and rows from before this column (ADR-036 "account-tenant
-- ownership" is Proposed, not accepted, and authorizes no verified per-customer tenant). SKU uniqueness moves
-- from global to per-tenant: two different tenants, or two rows that both have an unresolved (null) tenant, may
-- now share a SKU. Postgres treats NULL <> NULL in a unique index, so the null-tenant bucket is not
-- cross-checked for duplicates by the index alone; ProductRepository's tenant-scoped lookup queries handle the
-- null case explicitly.
--
-- effective_from / effective_to bound when a product definition is sellable. Both nullable and unenforced by a
-- CHECK beyond ordering: null effective_from means "sellable immediately", null effective_to means "no planned
-- end". No backfill; existing rows get both columns null.
--
-- Rollback is manual. Flyway Community does not run undo scripts. Apply in the product schema:
--   ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_effective_range;
--   DROP INDEX IF EXISTS uq_products_tenant_sku;
--   ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku);
--   ALTER TABLE products DROP COLUMN IF EXISTS tenant_id;
--   ALTER TABLE products DROP COLUMN IF EXISTS effective_from;
--   ALTER TABLE products DROP COLUMN IF EXISTS effective_to;
-- Do not drop tenant_id or uq_products_tenant_sku while code that reads/relies on them is deployed: dropping the
-- per-tenant unique index while restoring the old global one would also re-reject any SKU that two tenants
-- already share.

SET search_path TO product;

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_sku_key;

ALTER TABLE products ADD COLUMN tenant_id VARCHAR(128);
ALTER TABLE products ADD COLUMN effective_from DATE;
ALTER TABLE products ADD COLUMN effective_to DATE;

CREATE UNIQUE INDEX uq_products_tenant_sku ON products(tenant_id, sku) WHERE NOT is_deleted;

ALTER TABLE products ADD CONSTRAINT chk_products_effective_range
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from);

COMMENT ON COLUMN products.tenant_id IS
    'Entra tid of the caller who defined the product (DRG-632/DRG-418 interim). Null for service tokens, anonymous callers, dev mode, and rows from before this column. SKU uniqueness (uq_products_tenant_sku) is scoped by this value.';
COMMENT ON COLUMN products.effective_from IS 'Date this product definition becomes sellable. Null means immediately.';
COMMENT ON COLUMN products.effective_to IS 'Date this product definition stops being sellable. Null means no planned end.';
