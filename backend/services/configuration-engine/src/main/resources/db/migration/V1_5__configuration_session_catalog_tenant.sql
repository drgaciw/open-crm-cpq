-- =====================================================
-- Flyway Migration V1_5: Configuration session catalog version, rule set version, tenant, bundle
-- =====================================================
-- Description: DRG-424 (BC-02-01) StartSession records the catalog version, rule-set version, and
-- tenant, and may start a session against a bundle instead of a product.
-- =====================================================

-- A session may now start against a bundle instead of a product; exactly one subject is required.
ALTER TABLE configuration_sessions
    ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE configuration_sessions
    ADD COLUMN bundle_id VARCHAR(255),
    ADD COLUMN catalog_version VARCHAR(255),
    ADD COLUMN rule_set_version VARCHAR(255),
    ADD COLUMN tenant_id VARCHAR(255);

ALTER TABLE configuration_sessions
    ADD CONSTRAINT chk_config_session_subject CHECK (
        (product_id IS NOT NULL AND bundle_id IS NULL)
        OR (product_id IS NULL AND bundle_id IS NOT NULL)
    );

CREATE INDEX idx_config_session_bundle ON configuration_sessions(bundle_id);
CREATE INDEX idx_config_session_tenant ON configuration_sessions(tenant_id);
