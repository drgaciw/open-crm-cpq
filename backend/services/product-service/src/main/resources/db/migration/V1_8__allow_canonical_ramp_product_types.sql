-- The canonical recurring product types are accepted by the catalog API and
-- used by pricing/accepted subscription rates. Upgrade the shipped V1 domain
-- without changing its checksum or invalidating any existing catalog type.
ALTER TABLE products DROP CONSTRAINT chk_products_product_type;
ALTER TABLE products ADD CONSTRAINT chk_products_product_type CHECK (product_type IN (
    'SAAS_SEAT_NAMED', 'SAAS_SEAT_CONCURRENT', 'SAAS_USAGE_METERED', 'SAAS_FLAT_RATE',
    'ONPREM_PERPETUAL', 'ONPREM_CORE', 'ONPREM_SUBSCRIPTION', 'HYBRID_PORTABLE',
    'SUPPORT_BASIC', 'SUPPORT_PREMIUM', 'SUPPORT_ENTERPRISE',
    'PROFESSIONAL_SERVICES', 'TRAINING',
    'ADD_ON_API', 'ADD_ON_STORAGE', 'ADD_ON_ANALYTICS', 'ADD_ON_SECURITY', 'ADD_ON_INTEGRATION',
    'HARDWARE', 'CUSTOM',
    'ONPREM_SUPPORT', 'HYBRID_MIGRATION_RAMP'
));
