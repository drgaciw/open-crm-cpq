-- DRG-307 / BC-19-03 acceptance criteria: the initial role set. uuid is backfilled in SQL (not left to the
-- application's @PrePersist) because this is a data migration row, not an entity the application constructs.
INSERT INTO roles (uuid, name, description, created_at, version, is_deleted) VALUES
    (gen_random_uuid()::text, 'SalesRep', 'Sells and manages their own deals and quotes', NOW(), 0, FALSE),
    (gen_random_uuid()::text, 'DealDesk', 'Reviews and decides deal-desk-tier approvals', NOW(), 0, FALSE),
    (gen_random_uuid()::text, 'Approver', 'Decides approvals generally, independent of tier', NOW(), 0, FALSE),
    (gen_random_uuid()::text, 'CatalogAdmin', 'Maintains the product catalog', NOW(), 0, FALSE),
    (gen_random_uuid()::text, 'PricingAdmin', 'Maintains pricing rules and price books', NOW(), 0, FALSE),
    (gen_random_uuid()::text, 'Finance', 'Finance operations: billing, revenue, contracts', NOW(), 0, FALSE),
    (gen_random_uuid()::text, 'PartnerUser', 'External partner-portal user with restricted access', NOW(), 0, FALSE);
